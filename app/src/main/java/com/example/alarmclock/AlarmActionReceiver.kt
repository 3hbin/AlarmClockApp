package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Xử lý nút Tắt / Hoãn trên thông báo báo thức (tránh lỡ tay trên full-screen).
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmNotificationHelper.ACTION_DISMISS -> {
                // Chỉ tắt được qua notification khi không anti-troll / challenge
                // (helper đã ẩn nút Tắt trong trường hợp đó)
                AlarmNotificationHelper.cancelRinging(context)
                try { AlarmRingService.stop(context) } catch (_: Exception) {}
                // Gửi lệnh dừng cho activity nếu đang mở
                context.sendBroadcast(Intent(ACTION_FORCE_STOP_RING).setPackage(context.packageName))
                Toast.makeText(context, "Đã tắt báo thức từ thông báo", Toast.LENGTH_SHORT).show()
            }
            AlarmNotificationHelper.ACTION_SNOOZE -> {
                // Có thể mở rộng sau
            }
        }
    }

    companion object {
        const val ACTION_FORCE_STOP_RING = "com.example.alarmclock.ACTION_FORCE_STOP_RING"
    }
}
