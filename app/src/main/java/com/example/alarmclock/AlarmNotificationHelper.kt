package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Thông báo khi báo thức đang kêu — full-screen intent + nút Tắt.
 * Samsung/OEM: full-screen intent là cách chính để hiện màn reo khi khóa.
 */
object AlarmNotificationHelper {

    const val CHANNEL_RINGING = "alarm_ringing_v3"
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

        // Xóa channel cũ nếu importance thấp
        try {
            nm.deleteNotificationChannel("alarm_ringing")
        } catch (_: Exception) {
        }

        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            "Báo thức đang kêu",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen khi báo thức reo — cần bật thông báo + full-screen intent"
            setBypassDnd(true)
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            // Âm thanh từ AlarmRingActivity (STREAM_ALARM); channel không mute
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(ringing)

        val chrono = NotificationChannel(
            CHANNEL_CHRONO,
            "Bấm giờ / Đếm ngược",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Giữ chạy nền — có nút dừng"
            setShowBadge(false)
        }
        nm.createNotificationChannel(chrono)
    }

    fun showRingingNotification(
        context: Context,
        alarmId: Int,
        label: String,
        allowDirectDismiss: Boolean,
        hour: Int = -1,
        minute: Int = -1,
        snoozeMinutes: Int = 5,
        repeatMode: Int = Alarm.REPEAT_DAILY,
        ringtoneUri: String? = null,
        challengeType: Int = Alarm.CHALLENGE_NONE,
        shakeTargetCount: Int = 10,
        isStrict: Boolean = false,
        voiceNote: String? = null,
        useCrescendo: Boolean = true
    ) {
        ensureChannels(context)

        val openIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("REPEAT_MODE", repeatMode)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("CHALLENGE_TYPE", challengeType)
            putExtra("SHAKE_TARGET_COUNT", shakeTargetCount)
            putExtra("STRICT_ANTI_SNOOZE", isStrict)
            putExtra("VOICE_NOTE", voiceNote)
            putExtra("USE_CRESCENDO", useCrescendo)
            action = ACTION_OPEN_RING + "_$alarmId"
        }
        val openPi = PendingIntent.getActivity(
            context, alarmId + 70000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $label")
            .setContentText(
                if (allowDirectDismiss)
                    "Báo thức đang kêu — bấm Tắt bên dưới (tránh lỡ tay)"
                else
                    "Báo thức đang kêu — mở màn hình để PIN / thử thách"
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openPi, true)
            .setTimeoutAfter(0)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

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
