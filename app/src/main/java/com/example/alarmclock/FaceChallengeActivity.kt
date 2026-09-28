package com.example.alarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.alarmclock.databinding.ActivityFaceChallengeBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quét mặt / 10 biểu cảm — BẮT BUỘC nhận diện (không tắt bằng 1 lần bấm như nút Dismiss).
 * - MODE_FACE: giữ mặt trong khung ≥ 2 giây → mới tắt
 * - MODE_EXPR: lần lượt 10 biểu cảm, mỗi bước giữ ≥ 0,8s
 * - Dự phòng (camera hỏng): sau 12s, phải bấm Xác nhận 3 lần (cách nhau 1s)
 */
class FaceChallengeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    companion object {
        const val EXTRA_MODE = "FACE_MODE"
        const val MODE_FACE = 0
        const val MODE_EXPR = 1
        const val EXTRA_EASY = "easy_expr"
        private const val TAG = "FaceChallenge"

        /** AlarmRing singleInstance hay mất ActivityResult → dùng cờ này */
        @JvmStatic @Volatile var pendingResultOk: Boolean? = null
        fun consumePendingResult(): Boolean? {
            val v = pendingResultOk
            pendingResultOk = null
            return v
        }
    }

    private enum class Expr(val label: String, val emoji: String) {
        SMILE("1/10 CƯỜI nhẹ", "😊"),
        NEUTRAL("2/10 MẶT THƯỜNG", "😐"),
        BLINK("3/10 NHẮM 2 MẮT", "😌"),
        LOOK("4/10 NHÌN CAMERA", "👀"),
        BIG_SMILE("5/10 CƯỜI TƯƠI", "😄"),
        SOFT("6/10 CƯỜI MỈM", "🙂"),
        MOUTH("7/10 HÁ MIỆNG nhẹ", "😮"),
        WINK_L("8/10 NHẮM 1 MẮT", "😉"),
        WINK_R("9/10 NHẮM MẮT kia", "😜"),
        CENTER("10/10 GIỮ MẶT GIỮA", "🎯")
    }

    private lateinit var binding: ActivityFaceChallengeBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val cameraStarted = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var mode = MODE_FACE
    private var facePassed = false
    private var exprIndex = 0
    private var matchHoldMs = 0L
    private var lastMatchTs = 0L
    private var holdNeedMs = 2000L
    private var faceSeenMs = 0L
    private var faceSeenStart = 0L
    private var usingFallback = false
    private var fallbackTaps = 0
    private var lastFallbackTap = 0L
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
        )
    }
    private val exprStepsFull = Expr.values().toList()
    private var exprSteps = exprStepsFull

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                binding.tvFaceStatus.text = "Đang mở camera…"
                mainHandler.postDelayed({ tryStartCamera() }, 200)
            } else {
                Toast.makeText(this, "Cần quyền Camera để quét mặt", Toast.LENGTH_LONG).show()
                scheduleFallback(8000L)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        boostBrightness()
        binding = ActivityFaceChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            window.statusBarColor = 0xFF0F172A.toInt()
            window.navigationBarColor = 0xFF0F172A.toInt()
        } catch (_: Exception) {}

        mode = intent.getIntExtra(EXTRA_MODE, MODE_FACE)
        val easy = intent.getBooleanExtra(EXTRA_EASY, true)
        // Biểu cảm: luôn 10 bước; hold ngắn hơn một chút khi easy
        holdNeedMs = when {
            mode == MODE_EXPR && easy -> 700L
            mode == MODE_EXPR -> 900L
            else -> 2000L // quét mặt: giữ 2 giây
        }
        exprSteps = exprStepsFull
        exprIndex = 0
        facePassed = false
        pendingResultOk = null

        try {
            binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        } catch (_: Exception) {}

        updateExprUi()
        // Nút xác nhận tắt = CHỈ bật khi đã đạt (hoặc dự phòng 3 lần bấm)
        binding.btnConfirmFace.isEnabled = false
        binding.btnConfirmFace.text =
            if (mode == MODE_EXPR) "Chưa đủ biểu cảm" else "Chưa nhận diện mặt"
        binding.btnConfirmFace.setOnClickListener {
            if (facePassed) {
                completeOk()
            } else if (usingFallback) {
                onFallbackTap()
            } else {
                Toast.makeText(
                    this,
                    if (mode == MODE_EXPR) "Làm theo biểu cảm trên màn hình"
                    else "Đưa mặt vào vòng tròn và giữ 2 giây",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.btnCancelFace.visibility = View.VISIBLE
        binding.btnCancelFace.setOnClickListener {
            pendingResultOk = false
            setResult(RESULT_CANCELED)
            finishRestore()
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                binding.tvFaceStatus.text = "Đang mở camera…"
                mainHandler.postDelayed({ tryStartCamera() }, 250)
            }
            else -> {
                binding.tvFaceStatus.text = "Xin quyền Camera…"
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Dự phòng chỉ sau 12s nếu camera không lên / không thấy mặt
        scheduleFallback(12_000L)
        Toast.makeText(this, "Màn quét mặt đã mở — đưa mặt vào khung", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleFallback(delayMs: Long) {
        mainHandler.postDelayed({
            if (!isFinishing && !facePassed && !usingFallback) {
                enableFallbackMode(
                    if (cameraStarted.get()) "Chưa nhận diện ổn định — dự phòng (bấm 3 lần)"
                    else "Camera không lên — dự phòng (bấm Xác nhận 3 lần)"
                )
            }
        }, delayMs)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !cameraStarted.get() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mainHandler.postDelayed({ tryStartCamera() }, 200)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!cameraStarted.get() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mainHandler.postDelayed({ tryStartCamera() }, 300)
        }
    }

    override fun onDestroy() {
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        try { cameraExecutor.shutdown() } catch (_: Exception) {}
        try { detector.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun tryStartCamera() {
        if (isFinishing || cameraStarted.get()) return
        startCamera()
    }

    private fun updateExprUi() {
        if (mode == MODE_EXPR) {
            val step = exprSteps.getOrNull(exprIndex)
            binding.tvExprTitle.text = "10 biểu cảm — làm lần lượt"
            binding.tvFaceStatus.text = "${step?.emoji ?: ""} ${step?.label ?: ""}"
            binding.tvExprProgress.text = "${exprIndex + 1} / ${exprSteps.size} — khung XANH = đạt"
        } else {
            binding.tvExprTitle.text = "Quét mặt để tắt báo thức"
            binding.tvFaceStatus.text = "Đưa mặt vào vòng tròn giữa màn hình"
            binding.tvExprProgress.text = "Giữ mặt liên tục 2 giây"
        }
    }

    private fun showOnLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        try {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } catch (_: Exception) {}
    }

    private fun boostBrightness() {
        try {
            val lp = window.attributes
            originalBrightness = lp.screenBrightness
            lp.screenBrightness = 1f
            window.attributes = lp
        } catch (_: Exception) {}
    }

    private fun restoreBrightness() {
        try {
            val lp = window.attributes
            lp.screenBrightness = originalBrightness
            window.attributes = lp
        } catch (_: Exception) {}
    }

    private fun finishRestore() {
        try { restoreBrightness() } catch (_: Exception) {}
        finish()
    }

    private fun completeOk() {
        if (facePassed) {
            pendingResultOk = true
            setResult(RESULT_OK)
            finishRestore()
        }
    }

    private fun enableFallbackMode(msg: String) {
        if (facePassed || isFinishing) return
        usingFallback = true
        fallbackTaps = 0
        binding.tvFaceStatus.text = msg
        binding.tvExprProgress.text = "Bấm «Xác nhận» 3 lần (cách nhau ≥1s) — không tắt 1 phát"
        binding.btnConfirmFace.isEnabled = true
        binding.btnConfirmFace.visibility = View.VISIBLE
        binding.btnConfirmFace.text = "Xác nhận dự phòng (0/3)"
    }

    private fun onFallbackTap() {
        val now = System.currentTimeMillis()
        if (now - lastFallbackTap < 900) {
            Toast.makeText(this, "Chờ 1 giây rồi bấm tiếp", Toast.LENGTH_SHORT).show()
            return
        }
        lastFallbackTap = now
        fallbackTaps++
        binding.btnConfirmFace.text = "Xác nhận dự phòng ($fallbackTaps/3)"
        binding.tvExprProgress.text = "Đã bấm $fallbackTaps/3"
        if (fallbackTaps >= 3) {
            facePassed = true
            Toast.makeText(this, "Đủ 3 lần — tắt báo thức", Toast.LENGTH_SHORT).show()
            completeOk()
        } else {
            Toast.makeText(this, "Còn ${3 - fallbackTaps} lần", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        if (cameraStarted.get()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    try {
                        val media = proxy.image
                        if (media != null && !facePassed) {
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            val w = proxy.width
                            val h = proxy.height
                            val rot = proxy.imageInfo.rotationDegrees
                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    mainHandler.post {
                                        onFaces(faces, w, h, rot)
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        } else {
                            proxy.close()
                        }
                    } catch (e: Exception) {
                        try { proxy.close() } catch (_: Exception) {}
                        Log.w(TAG, "analyze", e)
                    }
                }

                var bound = false
                val selectors = listOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                for (sel in selectors) {
                    try {
                        provider.bindToLifecycle(this, sel, preview, analysis)
                        cameraStarted.set(true)
                        bound = true
                        mainHandler.post {
                            binding.tvFaceStatus.text =
                                if (mode == MODE_FACE) "Camera OK — đưa mặt vào vòng tròn, giữ 2 giây"
                                else "Camera OK — làm theo biểu cảm trên màn hình"
                        }
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "bind $sel", e)
                        try { provider.unbindAll() } catch (_: Exception) {}
                    }
                }
                if (!bound) {
                    mainHandler.post {
                        enableFallbackMode("Không mở được camera — bấm Xác nhận 3 lần")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCamera", e)
                mainHandler.post {
                    enableFallbackMode("Lỗi camera: ${e.message} — bấm 3 lần")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFaces(faces: List<Face>, imgW: Int, imgH: Int, rotation: Int) {
        if (facePassed || isFinishing) return
        if (faces.isEmpty()) {
            try { binding.faceBox.clear() } catch (_: Exception) {}
            matchHoldMs = 0
            lastMatchTs = 0
            faceSeenStart = 0
            faceSeenMs = 0
            if (mode == MODE_FACE) {
                binding.tvFaceStatus.text = "Không thấy mặt — đưa mặt vào vòng tròn"
                if (!usingFallback) {
                    binding.btnConfirmFace.isEnabled = false
                    binding.btnConfirmFace.text = "Chưa nhận diện mặt"
                }
            }
            return
        }
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return
        try {
            val mapped = mapBox(face, imgW, imgH, rotation)
            val matched = if (mode == MODE_EXPR) matchExpression(face) else true
            binding.faceBox.update(mapped[0], mapped[1], mapped[2], mapped[3], matched)
        } catch (_: Exception) {}

        if (mode == MODE_FACE) {
            val now = System.currentTimeMillis()
            if (faceSeenStart == 0L) faceSeenStart = now
            faceSeenMs = now - faceSeenStart
            val pct = (faceSeenMs * 100 / holdNeedMs).toInt().coerceAtMost(100)
            binding.tvFaceStatus.text = "Đã thấy mặt — giữ $pct%"
            binding.tvExprProgress.text = "Còn ${((holdNeedMs - faceSeenMs).coerceAtLeast(0) / 100) / 10.0}s"
            if (faceSeenMs >= holdNeedMs) {
                facePassed = true
                binding.tvFaceStatus.text = "Nhận diện OK — đang tắt…"
                binding.btnConfirmFace.isEnabled = true
                binding.btnConfirmFace.text = "Xong"
                completeOk()
            } else {
                binding.btnConfirmFace.isEnabled = false
                binding.btnConfirmFace.text = "Đang quét… $pct%"
            }
            return
        }

        // MODE_EXPR
        val matched = matchExpression(face)
        val now = System.currentTimeMillis()
        if (matched) {
            if (lastMatchTs == 0L) lastMatchTs = now
            matchHoldMs += (now - lastMatchTs).coerceIn(0L, 120L)
            lastMatchTs = now
            if (matchHoldMs >= holdNeedMs) {
                exprIndex++
                matchHoldMs = 0
                lastMatchTs = 0
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    binding.tvFaceStatus.text = "Đủ 10 biểu cảm!"
                    completeOk()
                } else {
                    updateExprUi()
                    Toast.makeText(this, "Đạt bước ${exprIndex}! Tiếp…", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvExprProgress.text =
                    "Giữ… ${(matchHoldMs * 100 / holdNeedMs).toInt()}% — khung XANH"
            }
        } else {
            lastMatchTs = 0
            matchHoldMs = 0
            val step = exprSteps.getOrNull(exprIndex)
            if (step != null) {
                binding.tvExprProgress.text = "Khung ĐỎ — ${step.emoji} ${step.label}"
            }
        }
    }

    private fun matchExpression(face: Face): Boolean {
        val smile = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        return when (exprSteps.getOrNull(exprIndex)) {
            Expr.SMILE -> smile >= 0.15f
            Expr.NEUTRAL -> smile < 0.45f
            Expr.BLINK -> leftEye < 0.55f && rightEye < 0.55f
            Expr.LOOK -> true
            Expr.BIG_SMILE -> smile >= 0.28f
            Expr.SOFT -> smile >= 0.10f
            Expr.MOUTH -> smile >= 0.08f
            Expr.WINK_L, Expr.WINK_R ->
                (leftEye < 0.45f && rightEye > 0.2f) ||
                    (rightEye < 0.45f && leftEye > 0.2f) ||
                    smile >= 0.25f
            Expr.CENTER -> true
            else -> true
        }
    }

    private fun mapBox(face: Face, imgW: Int, imgH: Int, rotation: Int): FloatArray {
        val bb = face.boundingBox
        val vw = binding.faceBox.width.toFloat().coerceAtLeast(1f)
        val vh = binding.faceBox.height.toFloat().coerceAtLeast(1f)
        val rw = if (rotation == 90 || rotation == 270) imgH else imgW
        val rh = if (rotation == 90 || rotation == 270) imgW else imgH
        val scale = maxOf(vw / rw.toFloat(), vh / rh.toFloat())
        val dx = (vw - rw * scale) / 2f
        val dy = (vh - rh * scale) / 2f
        var l = vw - (bb.right * scale + dx)
        var r = vw - (bb.left * scale + dx)
        val t = bb.top * scale + dy
        val b = bb.bottom * scale + dy
        if (l > r) {
            val tmp = l; l = r; r = tmp
        }
        val pad = 12f
        return floatArrayOf(l - pad, t - pad, r + pad, b + pad)
    }
}
