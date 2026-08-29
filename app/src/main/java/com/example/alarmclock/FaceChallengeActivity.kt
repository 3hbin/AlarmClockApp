package com.example.alarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.alarmclock.databinding.ActivityFaceChallengeBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executors

class FaceChallengeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "FACE_MODE"
        const val MODE_FACE = 0
        const val MODE_EXPR = 1
    }

    private enum class Expr(val label: String, val emoji: String) {
        SMILE("CUOI tuoi", "😊"),
        ANGRY("TUC GIAN (cau may, khong cuoi)", "😠"),
        TONGUE("LE LUOI (ha mieng to)", "👅")
    }

    private lateinit var binding: ActivityFaceChallengeBinding
    private var imageCapture: ImageCapture? = null
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var facePassed = false
    private var failCount = 0
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var mode = MODE_FACE
    private var exprIndex = 0
    private val exprSteps = listOf(Expr.SMILE, Expr.ANGRY, Expr.TONGUE)
    private var matchHoldMs = 0L
    private var lastMatchTs = 0L
    private val holdNeedMs = 700L

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(opts)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        binding = ActivityFaceChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_FACE)
        boostBrightness()
        updateExprUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        } else {
            startCamera()
        }

        binding.btnConfirmFace.setOnClickListener {
            if (facePassed) {
                setResult(RESULT_OK)
                finishRestore()
            } else {
                onFaceFail("Chua hoan thanh thu thach mat")
            }
        }
        binding.btnCancelFace.setOnClickListener {
            onFaceFail("Huy")
            setResult(RESULT_CANCELED)
            finishRestore()
        }
    }

    private fun updateExprUi() {
        if (mode == MODE_EXPR) {
            val step = exprSteps[exprIndex]
            binding.tvExprTitle.text = "Bieu cam ${exprIndex + 1}/${exprSteps.size}"
            binding.tvFaceStatus.text = "${step.emoji} ${step.label}"
            binding.tvExprProgress.text = "Giu dung ~0.7s — khung xanh = dat"
            binding.btnConfirmFace.isEnabled = false
            binding.btnConfirmFace.text = "Hoan thanh het bieu cam de tat"
        } else {
            binding.tvExprTitle.text = "Quet khuon mat"
            binding.tvFaceStatus.text = "Dua mat vao khung — dang quet…"
            binding.tvExprProgress.text = ""
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
        // TextureView ổn định hơn trên Huawei/EMUI (tránh preview đen)
        try {
            binding.previewView.implementationMode =
                androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
            binding.previewView.scaleType =
                androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        } catch (_: Exception) {}

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
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
                                runOnUiThread {
                                    handleFaces(faces, proxy.width, proxy.height, proxy.imageInfo.rotationDegrees)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else {
                        proxy.close()
                    }
                }

                provider.unbindAll()
                val selectors = listOf(
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                var bound = false
                for (sel in selectors) {
                    try {
                        provider.bindToLifecycle(this, sel, preview, imageCapture, analysis)
                        bound = true
                        binding.tvFaceStatus.append("") // no-op keep UI
                        if (sel == CameraSelector.DEFAULT_BACK_CAMERA) {
                            runOnUiThread {
                                Toast.makeText(this, "Dùng camera sau (không có camera trước)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        break
                    } catch (e: Exception) {
                        Log.e("FaceChallenge", "bind fail $sel", e)
                        provider.unbindAll()
                    }
                }
                if (!bound) {
                    runOnUiThread {
                        binding.tvFaceStatus.text = "Không mở được camera — cấp quyền Camera trong Cài đặt"
                        Toast.makeText(this, "Không mở được camera", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FaceChallenge", "startCamera error", e)
                runOnUiThread {
                    Toast.makeText(this, "Lỗi camera: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleFaces(faces: List<Face>, imgW: Int, imgH: Int, rotation: Int) {
        if (faces.isEmpty()) {
            binding.faceBox.clear()
            matchHoldMs = 0
            lastMatchTs = 0
            if (mode == MODE_FACE) {
                binding.tvFaceStatus.text = "Khong thay mat — dua mat vao khung"
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
                binding.tvFaceStatus.text = "Da thay khuon mat — bam Xac nhan de tat"
                binding.btnConfirmFace.isEnabled = true
            }
            return
        }

        val now = System.currentTimeMillis()
        if (matched) {
            if (lastMatchTs == 0L) lastMatchTs = now
            matchHoldMs = now - lastMatchTs
            if (matchHoldMs >= holdNeedMs) {
                exprIndex++
                matchHoldMs = 0
                lastMatchTs = 0
                if (exprIndex >= exprSteps.size) {
                    facePassed = true
                    binding.tvFaceStatus.text = "Du 3 bieu cam! Bam xac nhan de tat"
                    binding.tvExprProgress.text = "3 / 3"
                    binding.btnConfirmFace.isEnabled = true
                    binding.btnConfirmFace.text = "Tat bao thuc"
                } else {
                    updateExprUi()
                    Toast.makeText(this, "Dat! Tiep theo…", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvExprProgress.text =
                    "Giu… ${(matchHoldMs * 100 / holdNeedMs).toInt()}% — khung XANH"
            }
        } else {
            lastMatchTs = 0
            matchHoldMs = 0
            val step = exprSteps.getOrNull(exprIndex)
            if (step != null) {
                binding.tvExprProgress.text = "Khung DO — lam dung: ${step.label}"
            }
        }
    }

    private fun matchExpression(face: Face): Boolean {
        val smile = face.smilingProbability ?: -1f
        val leftEye = face.leftEyeOpenProbability ?: 1f
        val rightEye = face.rightEyeOpenProbability ?: 1f
        val mouthOpen = estimateMouthOpen(face)
        return when (exprSteps.getOrNull(exprIndex)) {
            Expr.SMILE -> smile >= 0.55f
            Expr.ANGRY -> smile in 0f..0.25f && leftEye > 0.4f && rightEye > 0.4f && mouthOpen < 0.35f
            Expr.TONGUE -> mouthOpen >= 0.45f
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

    private fun onFaceFail(reason: String) {
        failCount++
        binding.tvFaceStatus.text = "$reason (lan $failCount)"
        if (AppSettings.isFaceCaptureOnFail(this)) {
            captureIntruderPhoto()
        }
    }

    private fun captureIntruderPhoto() {
        val capture = imageCapture ?: return
        val dir = File(filesDir, "intruder_photos").apply { mkdirs() }
        val file = File(dir, "face_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(opts, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@FaceChallengeActivity, "Da chup: ${file.name}", Toast.LENGTH_LONG).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("FaceChallenge", "capture error", exception)
                }
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Cần quyền Camera — vào Cài đặt ứng dụng để bật", Toast.LENGTH_LONG).show()
            binding.tvFaceStatus.text = "Chưa cấp quyền Camera"
            setResult(RESULT_CANCELED)
            finishRestore()
        }
    }

    override fun onDestroy() {
        restoreBrightness()
        cameraExecutor.shutdown()
        detector.close()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        onFaceFail("Back")
        super.onBackPressed()
    }
}
