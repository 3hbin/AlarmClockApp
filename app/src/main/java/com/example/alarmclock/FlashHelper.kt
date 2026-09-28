package com.example.alarmclock

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper

class FlashHelper(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null
    private var isFlashing = false
    private val handler = Handler(Looper.getMainLooper())
    private var flashRunnable: Runnable? = null

    init {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startFlashing() {
        if (cameraId == null || isFlashing) return
        isFlashing = true
        var on = true
        flashRunnable = object : Runnable {
            override fun run() {
                if (!isFlashing) return
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cameraManager.setTorchMode(cameraId!!, on)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                on = !on
                handler.postDelayed(this, 400) // nháy mỗi 0.4 giây
            }
        }
        handler.post(flashRunnable!!)
    }

    fun stopFlashing() {
        isFlashing = false
        flashRunnable?.let { handler.removeCallbacks(it) }
        try {
            if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId!!, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
