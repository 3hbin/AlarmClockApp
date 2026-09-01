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
 * Thử thách khuôn mặt / biểu cảm.
 * Trên Huawei/EMUI camera hay đen khi mở từ full-screen alarm → mở trễ + fallback.
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
    }

    private enum class Expr(val label: String, val emoji: String) {
        // 10 biểu cảm dễ — ngưỡng thấp
        SMILE("1/10 CƯỜI nhẹ", "😊"),
        NEUTRAL("2/10 MẶT THƯỜNG (đừng cười)", "😐"),
        BLINK("3/10 NHẮM 2 MẮT", "😌"),
        LOOK("4/10 NHÌN CAMERA", "👀"),
        BIG_SMILE("5/10 CƯỜI TƯƠI hơn", "😄"),
        SOFT("6/10 CƯỜI MỈM", "🙂"),
        MOUTH("7/10 HÁ MIỆNG nhẹ", "😮"),
        WINK_L("8/10 NHẮM MẮT TRÁI (mắt phải mở)", "😉"),
        WINK_R("9/10 NHẮM MẮT PHẢI (mắt trái mở)", "😜"),
        CENTER("10/10 GIỮ MẶT GIỮA KHUNG", "🎯")
    }

    private lateinit var binding: ActivityFaceChallengeBinding
    private var imageCapture: ImageCapture? = null
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var facePassed = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var mode = MODE_FACE
    private var exprIndex = 0
    private val exprStepsFull = listOf(
        Expr.SMILE, Expr.NEUTRAL, Expr.BLINK, Expr.LOOK,
        Expr.BIG_SMILE, Expr.SOFT, Expr.MOUTH,
        Expr.WINK_L, Expr.WINK_R, Expr.CENTER
    )
    private val exprStepsEasy = listOf(
        Expr.SMILE, Expr.MOUTH, Expr.BLINK
    )
    private var exprSteps = exprStepsFull
    private var matchHoldMs = 0L
    private var lastMatchTs = 0L
    private val holdNeedMs = 300L
    private val cameraStarted = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.1f)
            .build()
        FaceDetection.getClient(opts)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            binding.tvFaceStatus.text = "Đã cấp quyền — đang mở camera…"
            // Đợi activity có focus thật sự (Huawei cần)
            mainHandler.postDelayed({ tryStartCamera() }, 400)
        } else {
            binding.tvFaceStatus.text = "Chưa cấp quyền Camera"
            showCameraFallback("Cần quyền Camera trong Cài đặt ứng dụng")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        binding = ActivityFaceChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_FACE)
        val easy = intent.getBooleanExtra(EXTRA_EASY, false)
        exprSteps = if (easy) exprStepsEasy else exprStepsFull
        boostBrightness()
        updateExprUi()

        try {
            binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        } catch (_: Exception) {}

        binding.btnConfirmFace.setOnClickListener {
            if (facePassed) {
                setResult(RESULT_OK)
                finishRestore()
            } else {
                Toast.makeText(this, "Chưa hoàn thành thử thách", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnCancelFace.setOnClickListener {
            setResult(RESULT_CANCELED)
            finishRestore()
        }

        // Nút fallback ẩn mặc định — hiện khi camera lỗi
        binding.btnConfirmFace.isEnabled = false

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                binding.tvFaceStatus.text = "Đang mở camera…"
                mainHandler.postDelayed({ tryStartCamera() }, 350)
            }
            else -> {
                binding.tvFaceStatus.text = "Xin quyền Camera…"
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Hiện nút dự phòng sớm — user không bị kẹt màn đen
        mainHandler.postDelayed({
            if (!facePassed && !isFinishing) {
                // Dù camera đã start hay chưa, nếu chưa detect được mặt thì vẫn cho fallback
                if (!cameraStarted.get()) {
                    showCameraFallback("Camera không mở được — dùng chế độ dự phòng")
                }
            }
        }, 2500)
        // Luôn cho Hủy rõ ràng
        binding.btnCancelFace.visibility = android.view.View.VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mainHandler.postDelayed({ tryStartCamera() }, 200)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mainHandler.postDelayed({ tryStartCamera() }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        // Giữ camera khi pause ngắn; unbind khi destroy
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun tryStartCamera() {
        if (isFinishing || cameraStarted.get()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) return
        startCamera()
    }

    private fun updateExprUi() {
        if (mode == MODE_EXPR) {
            val step = exprSteps[exprIndex]
            binding.tvExprTitle.text = "Biểu cảm ${exprIndex + 1}/${exprSteps.size}"
            binding.tvFaceStatus.text = "${step.emoji} ${step.label}"
            binding.tvExprProgress.text = "Giữ đúng ~0.7s — khung xanh = đạt"
            binding.btnConfirmFace.isEnabled = false
            binding.btnConfirmFace.text = "Hoàn thành hết biểu cảm để tắt"
        } else {
            binding.tvExprTitle.text = "Quét khuôn mặt"
            binding.tvFaceStatus.text = "Đưa mặt vào khung — đang quét…"
            binding.tvExprProgress.text = ""
            binding.btnConfirmFace.text = "Xác nhận tắt báo thức"
        }
    }

    private fun showOnLockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    private fun boostBrightness() {
        try {
            val lp = window.attributes
            originalBrightness = lp.screenBrightness
            lp.screenBrightness = 1.0f
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
        restoreBrightness()
        finish()
    }

    private fun startCamera() {
        if (cameraStarted.get()) return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (isFinishing) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                // Unbind trước
                provider.unbindAll()

                val preview = Preview.Builder().build()
                // Gắn surface SAU khi PreviewView đã layout
                binding.previewView.post {
                    try {
                        preview.setSurfaceProvider(binding.previewView.surfaceProvider)
                    } catch (e: Exception) {
                        Log.e("FaceChallenge", "surfaceProvider", e)
                    }
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    val media = proxy.image
                    if (media != null && !facePassed) {
                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                if (!isFinishing) {
                                    runOnUiThread {
                                        handleFaces(
                                            faces, proxy.width, proxy.height,
                                            proxy.imageInfo.rotationDegrees
                                        )
                                    }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else {
                        proxy.close()
                    }
                }

                val selectors = listOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                var bound = false
                for (sel in selectors) {
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(this, sel, preview, imageCapture, analysis)
                        // Gắn lại surface sau bind (một số máy Huawei cần)
                        binding.previewView.post {
                            try {
                                preview.setSurfaceProvider(binding.previewView.surfaceProvider)
                            } catch (_: Exception) {}
                        }
                        bound = true
                        cameraStarted.set(true)
                        runOnUiThread {
                            if (mode == MODE_FACE) {
                                binding.tvFaceStatus.text = "Camera OK — đưa mặt vào khung"
                            }
                            if (sel == CameraSelector.DEFAULT_BACK_CAMERA) {
                                Toast.makeText(
                                    this,
                                    "Đang dùng camera sau",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        break
                    } catch (e: Exception) {
                        Log.e("FaceChallenge", "bind fail $sel", e)
                        try { provider.unbindAll() } catch (_: Exception) {}
                    }
                }
                if (!bound) {
                    runOnUiThread {
                        showCameraFallback("Không mở được camera trước/sau")
                    }
                }
            } catch (e: Exception) {
                Log.e("FaceChallenge", "startCamera error", e)
                runOnUiThread {
                    showCameraFallback("Lỗi camera: ${e.message}")
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Khi camera đen / lỗi — cho tắt bằng cách xác nhận thủ công từng bước */
    private fun showCameraFallback(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        binding.tvFaceStatus.text = "$msg\nChạm nút bên dưới theo hướng dẫn"
        binding.tvExprProgress.text = "Chế độ dự phòng (không cần camera)"
        binding.btnConfirmFace.visibility = View.VISIBLE
        binding.btnConfirmFace.isEnabled = true
        if (mode == MODE_EXPR) {
            binding.btnConfirmFace.text = "Tôi đã: ${exprSteps[exprIndex].label}"
            binding.btnConfirmFace.setOnClickListener {
                exprIndex++
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    setResult(RESULT_OK)
                    finishRestore()
                } else {
                    binding.tvFaceStatus.text = "${exprSteps[exprIndex].emoji} ${exprSteps[exprIndex].label}"
                    binding.tvExprTitle.text = "Biểu cảm ${exprIndex + 1}/${exprSteps.size}"
                    binding.btnConfirmFace.text = "Tôi đã: ${exprSteps[exprIndex].label}"
                }
            }
        } else {
            binding.btnConfirmFace.text = "Tôi đã đưa mặt vào khung — Tắt báo thức"
            binding.btnConfirmFace.setOnClickListener {
                facePassed = true
                setResult(RESULT_OK)
                finishRestore()
            }
        }
    }

    private fun handleFaces(faces: List<Face>, imgW: Int, imgH: Int, rotation: Int) {
        if (faces.isEmpty()) {
            binding.faceBox.clear()
            matchHoldMs = 0
            lastMatchTs = 0
            if (mode == MODE_FACE && !facePassed) {
                binding.tvFaceStatus.text = "Không thấy mặt — đưa mặt vào khung"
                binding.btnConfirmFace.isEnabled = false
            }
            return
        }
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return
        val mapped = mapBox(face, imgW, imgH, rotation)
        val matched = if (mode == MODE_EXPR) matchExpression(face) else true
        binding.faceBox.update(mapped[0], mapped[1], mapped[2], mapped[3], matched)

        if (mode == MODE_FACE) {
            if (!facePassed) {
                facePassed = true
                binding.tvFaceStatus.text = "Đã thấy khuôn mặt — bấm Xác nhận để tắt"
                binding.btnConfirmFace.isEnabled = true
            }
            return
        }

        val now = System.currentTimeMillis()
        if (matched) {
            // Cộng dồn thời gian khớp (không reset nếu frame lệch nhẹ)
            if (lastMatchTs == 0L) lastMatchTs = now
            matchHoldMs += (now - lastMatchTs).coerceIn(0L, 80L)
            lastMatchTs = now
            if (matchHoldMs >= holdNeedMs) {
                exprIndex++
                matchHoldMs = 0
                lastMatchTs = 0
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    binding.tvFaceStatus.text = "Đủ tất cả biểu cảm!"
                    binding.tvExprProgress.text = "${exprSteps.size} / ${exprSteps.size}"
                    // Tự hoàn thành — không bắt bấm thêm (tránh kẹt bước 5/5)
                    setResult(RESULT_OK)
                    finishRestore()
                    return
                } else {
                    updateExprUi()
                    Toast.makeText(this, "Đạt! Tiếp theo…", Toast.LENGTH_SHORT).show()
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
                binding.tvExprProgress.text = "Khung ĐỎ — làm đúng: ${step.label}"
            }
        }
    }

    private fun matchExpression(face: Face): Boolean {
        val smile = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        val mouthOpen = estimateMouthOpen(face)
        val yaw = try { face.headEulerAngleY } catch (_: Exception) { 0f }
        // Ngưỡng dễ — máy yếu vẫn qua được
        return when (exprSteps.getOrNull(exprIndex)) {
            Expr.SMILE -> smile >= 0.18f
            Expr.NEUTRAL -> smile < 0.5f
            Expr.BLINK -> leftEye < 0.55f && rightEye < 0.55f
            Expr.LOOK -> true
            Expr.BIG_SMILE -> smile >= 0.28f
            Expr.SOFT -> smile >= 0.12f
            Expr.MOUTH -> mouthOpen >= 0.12f
            // Camera trước hay đảo trái/phải — chấp nhận nháy 1 bên
            Expr.WINK_L -> (leftEye < 0.45f && rightEye > 0.2f) || (rightEye < 0.45f && leftEye > 0.2f)
            Expr.WINK_R -> (rightEye < 0.45f && leftEye > 0.2f) || (leftEye < 0.45f && rightEye > 0.2f)
            Expr.CENTER -> kotlin.math.abs(yaw) < 25f || true // luôn dễ nếu có mặt
            else -> false
        }
    }

    private fun estimateMouthOpen(face: Face): Float {
        val upper = face.getContour(FaceContour.UPPER_LIP_TOP)?.points
        val lower = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points
        if (upper.isNullOrEmpty() || lower.isNullOrEmpty()) return 0f
        val uy = upper.map { it.y }.average()
        val ly = lower.map { it.y }.average()
        val faceH = face.boundingBox.height().toFloat().coerceAtLeast(1f)
        return ((ly - uy) / faceH).toFloat().coerceIn(0f, 1f)
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
