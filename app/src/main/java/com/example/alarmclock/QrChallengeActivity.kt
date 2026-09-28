package com.example.alarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrChallengeActivity : AppCompatActivity() {
    private var expected = ""
    private var done = false
    private val exec = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_challenge)
        expected = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (expected.isBlank()) {
            val id = intent.getIntExtra("ALARM_ID", -1)
            expected = try {
                AlarmRepository(this).getAlarms().find { it.id == id }?.qrToken.orEmpty()
            } catch (_: Exception) { "" }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 71)
        } else startCam()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCam()
        else {
            Toast.makeText(this, "Cần quyền camera để quét QR", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCam() {
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(exec) { proxy ->
                val media = proxy.image
                if (media != null && !done) {
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { codes -> handle(codes) }
                        .addOnCompleteListener { proxy.close() }
                } else proxy.close()
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                try {
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (_: Exception) {}
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handle(codes: List<Barcode>) {
        if (done) return
        val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue ?: return
        runOnUiThread {
            findViewById<TextView>(R.id.tvQrStatus).text = "Đã thấy mã…"
            if (expected.isBlank() || value.trim() == expected.trim()) {
                done = true
                setResult(RESULT_OK)
                finish()
            } else {
                findViewById<TextView>(R.id.tvQrStatus).text = "Sai mã. Quét đúng mã đã lưu."
            }
        }
    }

    override fun onDestroy() {
        try { scanner.close() } catch (_: Exception) {}
        try { exec.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TOKEN = "QR_TOKEN"
    }
}
