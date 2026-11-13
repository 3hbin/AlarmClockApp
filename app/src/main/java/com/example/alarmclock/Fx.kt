package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings

/**
 * Công tắc hiệu ứng cho CẢ app.
 * Tắt "Hiệu ứng chuyển động" trong Cài đặt → mọi ValueAnimator / ObjectAnimator / ViewPropertyAnimator
 * (kể cả của Material, RecyclerView, nút, thanh điều hướng…) nhảy thẳng đến kết quả, không vẽ từng khung → đỡ lag.
 */
object Fx {
    fun on(context: Context): Boolean = try {
        AppSettings.isAnimationEnabled(context)
    } catch (_: Throwable) { true }

    /** Gọi khi app khởi động và mỗi khi đổi công tắc. */
    fun applyGlobal(context: Context) {
        val app = context.applicationContext
        val scale = if (on(app)) systemScale(app) else 0f
        try {
            val m = ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            m.isAccessible = true
            m.invoke(null, scale)
        } catch (_: Throwable) {
            // Máy chặn API ẩn: các view vẫn tự kiểm tra Fx.on() ở những chỗ vẽ liên tục
        }
    }

    /** Giữ đúng tốc độ hoạt ảnh mà người dùng đặt trong Tùy chọn nhà phát triển (mặc định 1). */
    private fun systemScale(context: Context): Float = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Throwable) { 1f }
}
