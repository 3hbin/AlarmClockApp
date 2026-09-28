package com.example.alarmclock

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Rung tự động kiểu cuộc gọi đến — không cần chạm nút.
 * Pattern lặp: rung – nghỉ – rung – nghỉ… đến khi tắt báo thức.
 * Kèm pulse nhẹ trên nút để dễ nhìn.
 */
object RippleRingsEffect {

    /** Giống chuông gọi: 0.4s rung, 0.25s nghỉ, lặp */
    private val CALL_PATTERN = longArrayOf(0, 450, 280, 450, 280, 650, 400)

    private var vibrator: Vibrator? = null
    private var pulseAnim: ObjectAnimator? = null

    /**
     * Bắt đầu rung + pulse nút (gọi khi màn reo hiện).
     * [ringColor] bỏ qua — giữ chữ ký cũ.
     */
    fun attach(target: View, @Suppress("UNUSED_PARAMETER") ringColor: Int = 0) {
        startCallVibration(target.context)
        startButtonPulse(target)
    }

    /** Dừng rung + pulse (gọi khi tắt báo / destroy). */
    fun stop(context: Context) {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
        try {
            pulseAnim?.cancel()
            pulseAnim = null
        } catch (_: Exception) {}
    }

    fun startCallVibration(context: Context) {
        try {
            val vib = getVibrator(context) ?: return
            vibrator = vib
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // index 0 = lặp từ đầu pattern
                val effect = VibrationEffect.createWaveform(CALL_PATTERN, 0)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(CALL_PATTERN, 0)
            }
        } catch (_: Exception) {}
    }

    private fun startButtonPulse(target: View) {
        try {
            pulseAnim?.cancel()
            pulseAnim = ObjectAnimator.ofPropertyValuesHolder(
                target,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f, 1f)
            ).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
