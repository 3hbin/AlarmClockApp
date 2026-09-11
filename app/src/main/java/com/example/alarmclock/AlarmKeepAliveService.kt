package com.example.alarmclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Thông báo "đang bật" chỉ khi người dùng bật trong Cài đặt.
 * Mặc định TẮT — học Đồng hồ Google: lịch setAlarmClock mới là nguồn sự thật.
 */
class AlarmKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundSafe(buildNotification(enabledCount(this)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !isWanted(this) || enabledCount(this) <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForegroundSafe(buildNotification(enabledCount(this)))
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Báo thức đang bật", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        } catch (_: Exception) {}
    }

    private fun startForegroundSafe(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, 1073741824)
            else startForeground(NOTIF_ID, n)
        } catch (_: Exception) {
            try { startForeground(NOTIF_ID, n) } catch (_: Exception) {}
        }
    }

    private fun buildNotification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, count))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL = "alarm_status_v2"
        const val ACTION_STOP = "keepalive_stop"
        private const val PREF_KEY = "status_notif"

        fun isWanted(context: Context) =
            AppSettings.prefs(context).getBoolean(PREF_KEY, false)

        fun setWanted(context: Context, on: Boolean) {
            AppSettings.prefs(context).edit().putBoolean(PREF_KEY, on).apply()
            sync(context)
        }

        fun enabledCount(context: Context): Int =
            try { AlarmRepository(context).getAlarms().count { it.isEnabled } } catch (_: Exception) { 0 }

        fun sync(context: Context) {
            val ctx = context.applicationContext
            val i = Intent(ctx, AlarmKeepAliveService::class.java)
            if (!isWanted(ctx) || enabledCount(ctx) <= 0) {
                try { ctx.startService(i.setAction(ACTION_STOP)) } catch (_: Exception) {}
                try {
                    val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
                    nm?.cancel(NOTIF_ID)
                } catch (_: Exception) {}
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {}
        }
    }
}
