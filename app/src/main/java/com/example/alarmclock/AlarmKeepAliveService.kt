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

class AlarmKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundSafe(buildNotification(enabledCount(this)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val count = enabledCount(this)
        if (count <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForegroundSafe(buildNotification(count))
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (enabledCount(this) > 0) {
            try {
                val restart = Intent(applicationContext, AlarmKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart) else startService(restart)
            } catch (_: Exception) {}
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            try { nm.deleteNotificationChannel("alarm_status") } catch (_: Exception) {}
            if (nm.getNotificationChannel(CHANNEL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Báo thức đang bật",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Giữ báo thức chạy nền"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                }
            )
        } catch (_: Exception) {}
    }

    private fun startForegroundSafe(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, 1073741824)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (_: Exception) {
            try { startForeground(NOTIF_ID, n) } catch (_: Exception) {}
        }
    }

    private fun buildNotification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = try {
            R.drawable.ic_notification_alarm
        } catch (_: Exception) {
            android.R.drawable.ic_lock_idle_alarm
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, count))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL = "alarm_status_v2"
        const val ACTION_STOP = "keepalive_stop"

        fun enabledCount(context: Context): Int =
            try { AlarmRepository(context).getAlarms().count { it.isEnabled } } catch (_: Exception) { 0 }

        fun sync(context: Context) {
            val ctx = context.applicationContext
            val count = enabledCount(ctx)
            val i = Intent(ctx, AlarmKeepAliveService::class.java)
            if (count <= 0) {
                try { ctx.startService(i.setAction(ACTION_STOP)) } catch (_: Exception) {}
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {}
        }
    }
}
