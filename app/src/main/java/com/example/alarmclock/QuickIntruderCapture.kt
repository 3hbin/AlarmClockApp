package com.example.alarmclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Chụp nhanh camera trước (~0.1s sau khi camera sẵn sàng) khi nhập PIN sai.
 * Ảnh lưu vào filesDir/intruder_photos → hiện trong Bộ sưu tập.
 */
object QuickIntruderCapture {
    private const val TAG = "QuickIntruder"

    fun snap(context: Context, lifecycleOwner: LifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "No camera permission")
            Toast.makeText(context, "Chưa có quyền camera — không chụp được", Toast.LENGTH_SHORT).show()
            return
        }

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Preview bắt buộc trên nhiều máy, dùng surface ảo không UI
                val preview = Preview.Builder().build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture
                )

                // Chụp sau ~100ms khi pipeline sẵn sàng
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    takePicture(context, imageCapture) {
                        try {
                            provider.unbindAll()
                        } catch (_: Exception) {}
                    }
                }, 100L)
            } catch (e: Exception) {
                Log.e(TAG, "bind failed", e)
                // Thử camera sau nếu trước lỗi
                tryFallbackBack(context, lifecycleOwner)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun tryFallbackBack(context: Context, lifecycleOwner: LifecycleOwner) {
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val provider = future.get()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val preview = Preview.Builder().build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        takePicture(context, imageCapture) {
                            try { provider.unbindAll() } catch (_: Exception) {}
                        }
                    }, 100L)
                } catch (e: Exception) {
                    Log.e(TAG, "fallback failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "fallback init failed", e)
        }
    }

    private fun takePicture(context: Context, capture: ImageCapture, onDone: () -> Unit) {
        val dir = File(context.filesDir, "intruder_photos").apply { mkdirs() }
        val file = File(dir, "troll_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            opts,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "Saved ${file.absolutePath}")
                    Toast.makeText(
                        context,
                        "📸 Đã chụp người nhập sai PIN → Bộ sưu tập",
                        Toast.LENGTH_LONG
                    ).show()
                    onDone()
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "capture error", exception)
                    onDone()
                }
            }
        )
    }
}
