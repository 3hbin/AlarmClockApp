package com.example.alarmclock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Bấm giờ chạy nền. Activity tự vẽ mượt 10ms; service chỉ cập nhật notification ~1s (đỡ nóng máy).
 */
class StopwatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var startElapsed = 0L
    private var accumulated = 0L
    private var running = false

    /** Cập nhật notification thưa (1s) — không gửi broadcast mỗi frame. */
    private val notifTick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime() - startElapsed
            elapsedMs = now
            baseStartElapsed = startElapsed
            updateNotification(now, true)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmNotificationHelper.ensureChannels(this)
        when (intent?.action) {
            ACTION_START -> {
                accumulated = intent.getLongExtra(EXTRA_MS, 0L)
                startElapsed = SystemClock.elapsedRealtime() - accumulated
                running = true
                isActive = true
                isRunningFlag = true
                baseStartElapsed = startElapsed
                elapsedMs = accumulated
                startForeground(
                    AlarmNotificationHelper.NOTIF_ID_STOPWATCH,
                    buildNotification(accumulated, true)
                )
                handler.removeCallbacks(notifTick)
                handler.post(notifTick)
                broadcastState(accumulated, true)
            }
            ACTION_PAUSE -> {
                if (running) {
                    accumulated = SystemClock.elapsedRealtime() - startElapsed
                    running = false
                    isRunningFlag = false
                    elapsedMs = accumulated
                    handler.removeCallbacks(notifTick)
                    updateNotification(accumulated, false)
                    startForeground(
                        AlarmNotificationHelper.NOTIF_ID_STOPWATCH,
                        buildNotification(accumulated, false)
                    )
                    broadcastState(accumulated, false)
                }
            }
            ACTION_RESUME -> {
                accumulated = intent.getLongExtra(EXTRA_MS, accumulated)
                startElapsed = SystemClock.elapsedRealtime() - accumulated
                running = true
                isActive = true
                isRunningFlag = true
                baseStartElapsed = startElapsed
                elapsedMs = accumulated
                startForeground(
                    AlarmNotificationHelper.NOTIF_ID_STOPWATCH,
                    buildNotification(accumulated, true)
                )
                handler.removeCallbacks(notifTick)
                handler.post(notifTick)
                broadcastState(accumulated, true)
            }
            ACTION_STOP, ACTION_RESET -> {
                handler.removeCallbacks(notifTick)
                running = false
                isActive = false
                isRunningFlag = false
                accumulated = 0L
                elapsedMs = 0L
                baseStartElapsed = 0L
                stopForeground(STOP_FOREGROUND_REMOVE)
                NotificationManagerCompat.from(this)
                    .cancel(AlarmNotificationHelper.NOTIF_ID_STOPWATCH)
                broadcastState(0L, false, reset = true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun broadcastState(ms: Long, running: Boolean, reset: Boolean = false) {
        sendBroadcast(Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_MS, ms)
            putExtra(EXTRA_RUNNING, running)
            putExtra(EXTRA_RESET, reset)
            putExtra(EXTRA_BASE_START, baseStartElapsed)
        })
    }

    private fun buildNotification(ms: Long, running: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, StopwatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 11,
            Intent(this, StopwatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = PendingIntent.getService(
            this, 12,
            Intent(this, StopwatchService::class.java).setAction(
                if (running) ACTION_PAUSE else ACTION_RESUME
            ).putExtra(EXTRA_MS, ms),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AlarmNotificationHelper.CHANNEL_CHRONO)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(if (running) "⏱ Bấm giờ đang chạy" else "⏸ Bấm giờ tạm dừng")
            .setContentText(formatTime(ms))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (running) "Tạm dừng" else "Tiếp tục",
                togglePi
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dừng",
                stopPi
            )
            .build()
    }

    private fun updateNotification(ms: Long, running: Boolean) {
        try {
            NotificationManagerCompat.from(this)
                .notify(AlarmNotificationHelper.NOTIF_ID_STOPWATCH, buildNotification(ms, running))
        } catch (_: SecurityException) {
        }
    }

    private fun formatTime(ms: Long): String {
        val minutes = (ms / 60000) % 60
        val seconds = (ms / 1000) % 60
        val centis = (ms / 10) % 100
        return String.format("%02d:%02d.%02d", minutes, seconds, centis)
    }

    override fun onDestroy() {
        handler.removeCallbacks(notifTick)
        isActive = false
        isRunningFlag = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "sw.START"
        const val ACTION_PAUSE = "sw.PAUSE"
        const val ACTION_RESUME = "sw.RESUME"
        const val ACTION_STOP = "sw.STOP"
        const val ACTION_RESET = "sw.RESET"
        const val ACTION_UPDATE = "com.example.alarmclock.STOPWATCH_UPDATE"
        const val EXTRA_MS = "ms"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_RESET = "reset"
        const val EXTRA_BASE_START = "baseStart"

        @Volatile var isActive = false
        /** true khi đang chạy (chưa pause) */
        @Volatile var isRunningFlag = false
        /** SystemClock.elapsedRealtime() lúc start − accumulated */
        @Volatile var baseStartElapsed = 0L
        @Volatile var elapsedMs = 0L

        /** Thời gian hiện tại chính xác (ms). */
        fun currentElapsed(): Long {
            return if (isRunningFlag && baseStartElapsed > 0) {
                SystemClock.elapsedRealtime() - baseStartElapsed
            } else {
                elapsedMs
            }
        }
    }
}
