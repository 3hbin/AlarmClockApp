package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Thông báo khi báo thức đang kêu — có nút Tắt để tránh lỡ tay bấm full-screen.
 */
object AlarmNotificationHelper {

    const val CHANNEL_RINGING = "alarm_ringing"
    const val CHANNEL_CHRONO = "chrono_running"
    const val NOTIF_ID_RINGING = 2001
    const val NOTIF_ID_TIMER = 2002
    const val NOTIF_ID_STOPWATCH = 2003

    const val ACTION_DISMISS = "com.example.alarmclock.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE = "com.example.alarmclock.ACTION_SNOOZE_ALARM"
    const val ACTION_OPEN_RING = "com.example.alarmclock.ACTION_OPEN_RING"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            "Báo thức đang kêu",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo khi báo thức reo — có nút Tắt"
            setBypassDnd(true)
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ringing)

        val chrono = NotificationChannel(
            CHANNEL_CHRONO,
            "Bấm giờ / Đếm ngược",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Giữ chạy nền, tránh nóng máy — có nút dừng"
            setShowBadge(false)
        }
        nm.createNotificationChannel(chrono)
    }

    fun showRingingNotification(
        context: Context,
        alarmId: Int,
        label: String,
        allowDirectDismiss: Boolean
    ) {
        ensureChannels(context)

        val openIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            action = ACTION_OPEN_RING
        }
        val openPi = PendingIntent.getActivity(
            context, alarmId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $label")
            .setContentText(
                if (allowDirectDismiss)
                    "Báo thức đang kêu — bấm Tắt bên dưới (tránh lỡ tay trên màn hình)"
                else
                    "Báo thức đang kêu — mở màn hình để hoàn thành thử thách / PIN"
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openPi, true)

        if (allowDirectDismiss) {
            val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra("ALARM_ID", alarmId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context, alarmId + 50000, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Tắt",
                dismissPi
            )
        } else {
            // Chỉ mở activity — chống troll / challenge
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Mở màn hình",
                openPi
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_RINGING, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelRinging(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RINGING)
    }
}
