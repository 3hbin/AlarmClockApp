package com.example.alarmclock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
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
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executors

/**
 * Quét khuôn mặt bằng camera trước (không dùng Biometric hệ thống).
 * - Phát hiện có khuôn mặt → cho phép tắt báo thức
 * - Sai / không có mặt / bấm hủy nhiều lần → chụp ảnh người đang cầm máy
 * - Tối: tự tăng độ sáng màn hình 100%, xong trả lại
 *
 * Vân tay: Android không cho app tự đọc cảm biến vân tay ngoài BiometricPrompt hệ thống.
 */
class FaceChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaceChallengeBinding
    private var imageCapture: ImageCapture? = null
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var facePassed = false
    private var failCount = 0
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(opts)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
        binding = ActivityFaceChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        boostBrightness()

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
                onFaceFail("Chưa nhận diện được khuôn mặt")
            }
        }
        binding.btnCancelFace.setOnClickListener {
            onFaceFail("Hủy / không xác minh")
            setResult(RESULT_CANCELED)
            finishRestore()
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
            lp.screenBrightness = 1.0f // 100%
            window.attributes = lp
            // Cũng thử tăng độ sáng hệ thống (cần WRITE_SETTINGS trên một số máy — bỏ qua nếu fail)
        } catch (_: Exception) { }
    }

    private fun restoreBrightness() {
        try {
            val lp = window.attributes
            lp.screenBrightness = originalBrightness
            window.attributes = lp
        } catch (_: Exception) { }
    }

    private fun finishRestore() {
        restoreBrightness()
        finish()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
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
                            if (faces.isNotEmpty()) {
                                facePassed = true
                                runOnUiThread {
                                    binding.tvFaceStatus.text = "✓ Đã thấy khuôn mặt — bấm Xác nhận để tắt"
                                    binding.btnConfirmFace.isEnabled = true
                                }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                } else {
                    proxy.close()
                }
            }

            try {
                provider.unbindAll()
                // Camera trước để quét mặt
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture, analysis
                )
            } catch (e: Exception) {
                Log.e("FaceChallenge", "bind failed", e)
                Toast.makeText(this, "Không mở được camera trước", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFaceFail(reason: String) {
        failCount++
        binding.tvFaceStatus.text = "$reason (lần $failCount)"
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
                    Toast.makeText(
                        this@FaceChallengeActivity,
                        "Đã chụp người tắt báo thức: ${file.name}",
                        Toast.LENGTH_LONG
                    ).show()
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
            Toast.makeText(this, "Cần quyền Camera để quét mặt", Toast.LENGTH_LONG).show()
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
