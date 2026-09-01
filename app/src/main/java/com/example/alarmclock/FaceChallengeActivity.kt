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
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.alarmclock.databinding.ActivityFaceChallengeBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quét mặt / 10 biểu cảm.
 * Huawei/EMUI: camera hay màn đen → fallback sớm + tự xác nhận khi thấy mặt.
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
    private var imageCapture: ImageCapture? = null
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var mode = MODE_FACE
    private var facePassed = false
    private var exprIndex = 0
    private var matchHoldMs = 0L
    private var lastMatchTs = 0L
    private var holdNeedMs = 450L
    private val cameraStarted = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var usingFallback = false
    private var faceSeenMs = 0L
    private var faceSeenStart = 0L

    private val exprStepsFull = listOf(
        Expr.SMILE, Expr.NEUTRAL, Expr.BLINK, Expr.LOOK, Expr.BIG_SMILE,
        Expr.SOFT, Expr.MOUTH, Expr.WINK_L, Expr.WINK_R, Expr.CENTER
    )
    private val exprStepsEasy = listOf(
        Expr.SMILE, Expr.NEUTRAL, Expr.BLINK, Expr.LOOK, Expr.SOFT,
        Expr.MOUTH, Expr.CENTER, Expr.BIG_SMILE, Expr.WINK_L, Expr.WINK_R
    )
    private var exprSteps = exprStepsFull

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(opts)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.tvFaceStatus.text = "Đã cấp quyền — đang mở camera…"
            mainHandler.postDelayed({ tryStartCamera() }, 300)
        } else {
            binding.tvFaceStatus.text = "Chưa cấp quyền Camera"
            showCameraFallback("Cần quyền Camera — hoặc bấm Xác nhận dự phòng")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { showOnLockScreen() } catch (_: Exception) {}
        binding = ActivityFaceChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_FACE)
        val easy = intent.getBooleanExtra(EXTRA_EASY, true)
        exprSteps = if (easy || mode == MODE_EXPR) exprStepsEasy else exprStepsFull
        holdNeedMs = if (mode == MODE_EXPR) 400L else 600L
        try { boostBrightness() } catch (_: Exception) {}
        updateExprUi()

        try {
            binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        } catch (_: Exception) {}

        binding.btnConfirmFace.setOnClickListener {
            if (facePassed || usingFallback) {
                setResult(RESULT_OK)
                finishRestore()
            } else {
                Toast.makeText(this, "Đưa mặt vào khung (hoặc đợi chế độ dự phòng)", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnCancelFace.setOnClickListener {
            setResult(RESULT_CANCELED)
            finishRestore()
        }
        binding.btnCancelFace.visibility = View.VISIBLE
        binding.btnConfirmFace.isEnabled = false
        binding.btnConfirmFace.text = if (mode == MODE_EXPR) "Xác nhận (sau khi đủ bước)" else "Xác nhận tắt báo thức"

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

        // Huawei: hiện dự phòng sớm nếu camera không lên
        mainHandler.postDelayed({
            if (!isFinishing && !facePassed && !cameraStarted.get()) {
                showCameraFallback("Camera chậm/đen — dùng dự phòng")
            }
        }, 1800)
        mainHandler.postDelayed({
            if (!isFinishing && !facePassed) {
                showCameraFallback("Bấm Xác nhận dự phòng để tắt")
            }
        }, 5000)
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
            binding.tvExprTitle.text = "10 biểu cảm dễ"
            binding.tvFaceStatus.text = "${step?.emoji ?: ""} ${step?.label ?: ""}"
            binding.tvExprProgress.text = "${exprIndex + 1} / ${exprSteps.size} — khung XANH = đạt"
        } else {
            binding.tvExprTitle.text = "Quét mặt"
            binding.tvFaceStatus.text = "Đưa mặt vào khung tròn"
            binding.tvExprProgress.text = "Giữ mặt ~0,6 giây để tự tắt"
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
                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    mainHandler.post {
                                        onFaces(faces, proxy.width, proxy.height, proxy.imageInfo.rotationDegrees)
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

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Ưu tiên camera trước; fail thì sau
                val selectors = listOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                var bound = false
                for (sel in selectors) {
                    try {
                        provider.bindToLifecycle(this, sel, preview, analysis, imageCapture)
                        cameraStarted.set(true)
                        bound = true
                        mainHandler.post {
                            binding.tvFaceStatus.text =
                                if (mode == MODE_FACE) "Camera OK — đưa mặt vào khung"
                                else "Camera OK — làm theo biểu cảm"
                        }
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "bind $sel", e)
                        try { provider.unbindAll() } catch (_: Exception) {}
                    }
                }
                if (!bound) {
                    mainHandler.post { showCameraFallback("Không mở được camera trước/sau") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCamera", e)
                mainHandler.post { showCameraFallback("Lỗi camera: ${e.message}") }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraFallback(msg: String) {
        if (facePassed || isFinishing) return
        usingFallback = true
        binding.tvFaceStatus.text = msg
        binding.tvExprProgress.text = "Chế độ dự phòng (không cần camera)"
        binding.btnConfirmFace.isEnabled = true
        binding.btnConfirmFace.visibility = View.VISIBLE
        binding.btnConfirmFace.text = if (mode == MODE_EXPR) {
            "Xác nhận từng bước (${exprIndex + 1}/${exprSteps.size})"
        } else {
            "Xác nhận tắt (dự phòng)"
        }
        // EXPR: mỗi lần bấm = 1 bước
        binding.btnConfirmFace.setOnClickListener {
            if (mode == MODE_EXPR) {
                exprIndex++
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    setResult(RESULT_OK)
                    finishRestore()
                } else {
                    updateExprUi()
                    binding.tvExprProgress.text = "Dự phòng — bấm xác nhận tiếp"
                    binding.btnConfirmFace.text =
                        "Xác nhận bước ${exprIndex + 1}/${exprSteps.size}"
                    Toast.makeText(this, "Bước $exprIndex OK", Toast.LENGTH_SHORT).show()
                }
            } else {
                facePassed = true
                setResult(RESULT_OK)
                finishRestore()
            }
        }
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
                binding.tvFaceStatus.text = "Không thấy mặt — đưa mặt vào khung"
                if (!usingFallback) binding.btnConfirmFace.isEnabled = false
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
            binding.tvFaceStatus.text = "Đã thấy mặt — giữ ${(faceSeenMs * 100 / holdNeedMs).toInt().coerceAtMost(100)}%"
            if (faceSeenMs >= holdNeedMs) {
                facePassed = true
                binding.tvFaceStatus.text = "Xong! Đang tắt…"
                binding.btnConfirmFace.isEnabled = true
                setResult(RESULT_OK)
                finishRestore()
            } else {
                binding.btnConfirmFace.isEnabled = true
            }
            return
        }

        // MODE_EXPR
        val matched = matchExpression(face)
        val now = System.currentTimeMillis()
        if (matched) {
            if (lastMatchTs == 0L) lastMatchTs = now
            matchHoldMs += (now - lastMatchTs).coerceIn(0L, 100L)
            lastMatchTs = now
            if (matchHoldMs >= holdNeedMs) {
                exprIndex++
                matchHoldMs = 0
                lastMatchTs = 0
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    binding.tvFaceStatus.text = "Đủ 10 biểu cảm!"
                    setResult(RESULT_OK)
                    finishRestore()
                } else {
                    updateExprUi()
                    Toast.makeText(this, "Đạt! Tiếp…", Toast.LENGTH_SHORT).show()
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
        val yaw = try { face.headEulerAngleY } catch (_: Exception) { 0f }
        // Ngưỡng rất dễ
        return when (exprSteps.getOrNull(exprIndex)) {
            Expr.SMILE -> smile >= 0.12f
            Expr.NEUTRAL -> smile < 0.55f
            Expr.BLINK -> leftEye < 0.6f && rightEye < 0.6f
            Expr.LOOK -> true
            Expr.BIG_SMILE -> smile >= 0.22f
            Expr.SOFT -> smile >= 0.08f
            Expr.MOUTH -> smile >= 0.05f || true // có mặt là gần đủ
            Expr.WINK_L, Expr.WINK_R ->
                (leftEye < 0.5f && rightEye > 0.15f) || (rightEye < 0.5f && leftEye > 0.15f) || smile >= 0.2f
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
