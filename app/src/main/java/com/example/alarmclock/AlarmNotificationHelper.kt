package com.example.alarmclock

import android.app.AlarmManager
import android.app.Notification
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

    const val CHANNEL_RINGING = "alarm_ringing_v6"
    const val CHANNEL_RINGING_FGS = "alarm_ringing_fgs_v1"
    const val CHANNEL_SCHEDULED = "alarm_scheduled_v1"
    const val NOTIF_ID_SCHEDULED = 1002
    const val CHANNEL_CHRONO = "chrono_running"
    const val CHANNEL_TIMER_DONE = "timer_done_v1"
    const val NOTIF_ID_TIMER_DONE = 2010
    const val CHANNEL_GEMINI = "gemini_briefing_v1"
    const val NOTIF_ID_RINGING = 2001
    const val ALARM_NOTIFICATION_ID = 2001
    const val FOREGROUND_NOTIFICATION_ID = 2099
    // Notification bền vững, tách khỏi foreground-service notification.
    const val NOTIF_ID_RINGING_FGS = 2099
    const val NOTIF_ID_TIMER = 2002
    const val NOTIF_ID_STOPWATCH = 2003

    const val ACTION_DISMISS = "com.example.alarmclock.ACTION_DISMISS_ALARM"
    const val ACTION_SNOOZE = "com.example.alarmclock.ACTION_SNOOZE_ALARM"
    const val ACTION_OPEN_RING = "com.example.alarmclock.ACTION_OPEN_RING"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Xóa channel cũ (có tiếng hệ thống) — tránh kêu 2 chuông
        for (oldId in listOf("alarm_ringing", "alarm_ringing_v2", "alarm_ringing_v3", "alarm_ringing_v4", "alarm_ringing_v5")) {
            try { nm.deleteNotificationChannel(oldId) } catch (_: Exception) {}
        }

        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            "Báo thức đang kêu",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen khi báo thức reo — nhạc do AlarmRingService"
            setBypassDnd(true)
            enableVibration(false)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        nm.createNotificationChannel(ringing)

        // Notification bắt buộc của foreground service: tách riêng và để LOW
        // để không tạo thêm một notification báo thức nổi bật/trùng lặp.
        val fgs = NotificationChannel(
            CHANNEL_RINGING_FGS,
            "Dịch vụ báo thức",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Dịch vụ nền hỗ trợ báo thức đang kêu"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(fgs)

        val chrono = NotificationChannel(
            CHANNEL_CHRONO,
            "Bấm giờ / Đếm ngược",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Giữ chạy nền — có nút dừng"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(chrono)

        val timerDone = NotificationChannel(
            CHANNEL_TIMER_DONE,
            "Hết giờ đếm ngược",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hiện full-screen khi đếm ngược về 00:00"
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(timerDone)

        val gemini = NotificationChannel(
            CHANNEL_GEMINI,
            "Quy trình Gemini",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Đọc lời nhắc sau khi tắt báo thức — không phát chuông"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(gemini)
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


    /**
     * Chỉ khi app đã ẩn: báo "Đã đặt báo thức lúc HH:mm thành công!".
     * Đang mở Chat thì không bắn để tránh trùng với tin nhắn trên màn hình.
     */
    fun notifyChatAlarmSetIfBackground(
        context: Context,
        hour: Int,
        minute: Int,
        label: String? = null
    ) {
        if (AppVisibility.isForeground()) return
        try {
            ensureChannels(context)
            val timeStr = String.format("%02d:%02d", hour, minute)
            val title = "Đã đặt báo thức lúc $timeStr thành công!"
            val text = if (!label.isNullOrBlank()) label else "Báo thức AI"
            val open = Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val id = 3200 + hour * 60 + minute
            val pi = PendingIntent.getActivity(
                context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: Exception) {
        }
    }

    /**
     * Thông báo kiểu hệ thống khi tạo/bật báo thức — icon đồng hồ báo thức.
     * Hiện vài giây rồi tự ẩn (giống Clock app).
     */
    fun showAlarmSetNotification(
        context: Context,
        hour: Int,
        minute: Int,
        label: String? = null
    ) {
        try {
            ensureChannels(context)
            val timeStr = String.format("%02d:%02d", hour, minute)
            val title = "Báo thức đã đặt"
            val text = if (!label.isNullOrBlank()) "$timeStr · $label" else timeStr

            val open = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, NOTIF_ID_SCHEDULED, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Icon đồng hồ báo thức hệ thống
            val icon = android.R.drawable.ic_lock_idle_alarm

            val builder = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Báo thức")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(12_000L) // tự ẩn sau 12s
                .setColor(0xFF4F5BFF.toInt())

            NotificationManagerCompat.from(context).notify(NOTIF_ID_SCHEDULED, builder.build())
        } catch (_: Exception) {
        }
    }

    /** Full-screen khi đếm ngược hết — bắt buộc kênh HIGH. */
    fun postTimerDoneFullScreen(context: Context): Notification {
        ensureChannels(context)
        val open = Intent(context, TimerDoneActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }
        val fullPi = PendingIntent.getActivity(
            context, NOTIF_ID_TIMER_DONE, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            context, NOTIF_ID_TIMER_DONE + 1,
            Intent(context, TimerService::class.java).setAction(TimerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_TIMER_DONE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hết giờ!")
            .setContentText("Đếm ngược đã kết thúc")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tắt", stopPi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_TIMER_DONE, n)
        } catch (_: Exception) {}
        return n
    }

    fun cancelTimerDone(context: Context) {
        try { NotificationManagerCompat.from(context).cancel(NOTIF_ID_TIMER_DONE) } catch (_: Exception) {}
        try { NotificationManagerCompat.from(context).cancel(NOTIF_ID_TIMER) } catch (_: Exception) {}
    }

    fun cancelRinging(context: Context) {
        cancelRingingRefresh(context)
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RINGING)
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RINGING_FGS)
        clearRingingState(context)
    }

    /** Lưu trạng thái để notification có thể được khôi phục sau reboot/process death. */
    fun saveRingingState(
        context: Context,
        alarmId: Int,
        label: String,
        hour: Int,
        minute: Int,
        snoozeMinutes: Int,
        repeatMode: Int,
        ringtoneUri: String?,
        challengeType: Int,
        shakeTargetCount: Int,
        isStrict: Boolean,
        voiceNote: String?,
        useCrescendo: Boolean
    ) {
        context.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE).edit()
            .putBoolean("active", true)
            .putInt("alarmId", alarmId)
            .putString("label", label)
            .putInt("hour", hour)
            .putInt("minute", minute)
            .putInt("snooze", snoozeMinutes)
            .putInt("repeat", repeatMode)
            .putString("ringtone", ringtoneUri)
            .putInt("challenge", challengeType)
            .putInt("shake", shakeTargetCount)
            .putBoolean("strict", isStrict)
            .putString("voice", voiceNote)
            .putBoolean("crescendo", useCrescendo)
            .putLong("startedAt", System.currentTimeMillis())
            .apply()
        scheduleRingingRefresh(context)
    }

    fun ringingTimedOut(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return false
        val started = p.getLong("startedAt", 0L)
        if (started <= 0L) return true
        val cap = AppSettings.getRingDurationMinutes(context).coerceIn(1, 30) * 60_000L
        return System.currentTimeMillis() - started >= cap
    }

    fun clearRingingState(context: Context) {
        context.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Khôi phục notification độc lập với foreground service sau khi Android khởi động lại. */
    fun restoreRingingNotification(context: Context) {
        val p = context.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) return
        if (AppSettings.isPauseAlarmsAtHome(context) || ringingTimedOut(context)) {
            cancelRinging(context)
            try { AlarmRingService.stop(context) } catch (_: Exception) {}
            try { TonePlayer.stop() } catch (_: Exception) {}
            return
        }
        showRingingNotification(
            context = context,
            alarmId = p.getInt("alarmId", -1),
            label = p.getString("label", "Báo thức") ?: "Báo thức",
            allowDirectDismiss = p.getInt("challenge", Alarm.CHALLENGE_NONE) == Alarm.CHALLENGE_NONE &&
                !p.getBoolean("strict", false) && !AppSettings.isAntiTroll(context),
            hour = p.getInt("hour", -1),
            minute = p.getInt("minute", -1),
            snoozeMinutes = p.getInt("snooze", 5),
            repeatMode = p.getInt("repeat", Alarm.REPEAT_DAILY),
            ringtoneUri = p.getString("ringtone", null),
            challengeType = p.getInt("challenge", Alarm.CHALLENGE_NONE),
            shakeTargetCount = p.getInt("shake", 10),
            isStrict = p.getBoolean("strict", false),
            voiceNote = p.getString("voice", null),
            useCrescendo = p.getBoolean("crescendo", true)
        )
        scheduleRingingRefresh(context)
    }

    private const val PREFS_RINGING = "persistent_ringing_notification"

    fun scheduleRingingRefresh(context: Context) {
        val p = context.getSharedPreferences(PREFS_RINGING, Context.MODE_PRIVATE)
        if (!p.getBoolean("active", false)) {
            cancelRingingRefresh(context)
            return
        }
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + REFRESH_INTERVAL_MS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, refreshPending(context))
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, trigger, refreshPending(context))
            }
        } catch (_: Exception) {
        }
    }

    fun cancelRingingRefresh(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(refreshPending(context))
        } catch (_: Exception) {
        }
    }

    private fun refreshPending(context: Context): PendingIntent {
        val i = Intent(context, NotificationRefreshReceiver::class.java).setAction(ACTION_REFRESH_2001)
        return PendingIntent.getBroadcast(
            context, REQ_REFRESH,
            i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_REFRESH_2001 = "com.example.alarmclock.ACTION_REFRESH_2001"
    private const val REQ_REFRESH = 88001
    private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L

}

