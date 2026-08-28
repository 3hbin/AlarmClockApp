package com.example.alarmclock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Đếm ngược chạy nền — thoát app vẫn chạy, hiện thông báo (tránh nóng máy / bị hệ thống kill).
 */
class TimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var timeLeftMs: Long = 0
    private var endAtElapsed: Long = 0
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmNotificationHelper.ensureChannels(this)
        when (intent?.action) {
            ACTION_START -> {
                timeLeftMs = intent.getLongExtra(EXTRA_MS, 0L)
                if (timeLeftMs <= 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCountdown()
            }
            ACTION_PAUSE -> pauseCountdown()
            ACTION_RESUME -> {
                timeLeftMs = intent.getLongExtra(EXTRA_MS, timeLeftMs)
                if (timeLeftMs > 0) startCountdown()
            }
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
            }
            ACTION_TICK_QUERY -> {
                // no-op, notification already updated
            }
            else -> {
                if (!isRunning && timeLeftMs > 0) startCountdown()
            }
        }
        return START_STICKY
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        endAtElapsed = SystemClock.elapsedRealtime() + timeLeftMs
        isRunning = true
        isActive = true
        remainingMs = timeLeftMs

        startForeground(AlarmNotificationHelper.NOTIF_ID_TIMER, buildNotification(timeLeftMs, true))

        countDownTimer = object : CountDownTimer(timeLeftMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                remainingMs = millisUntilFinished
                updateNotification(millisUntilFinished, true)
                sendBroadcast(Intent(ACTION_UPDATE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_MS, millisUntilFinished)
                    putExtra(EXTRA_RUNNING, true)
                })
            }

            override fun onFinish() {
                timeLeftMs = 0
                remainingMs = 0
                isRunning = false
                isActive = false
                updateNotification(0, false)
                sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
                // Không stopSelf ngay — để activity mở và reo; user bấm dừng sẽ stop
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }.start()
    }

    private fun pauseCountdown() {
        countDownTimer?.cancel()
        isRunning = false
        // timeLeftMs already current
        remainingMs = timeLeftMs
        updateNotification(timeLeftMs, false)
        sendBroadcast(Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_MS, timeLeftMs)
            putExtra(EXTRA_RUNNING, false)
        })
        // Vẫn foreground nhưng paused — user có thể Resume từ notification
        startForeground(AlarmNotificationHelper.NOTIF_ID_TIMER, buildNotification(timeLeftMs, false))
    }

    private fun stopEverything() {
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        isActive = false
        remainingMs = 0
        timeLeftMs = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(AlarmNotificationHelper.NOTIF_ID_TIMER)
    }

    private fun buildNotification(ms: Long, running: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, TimerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val togglePi = PendingIntent.getService(
            this, 2,
            Intent(this, TimerService::class.java).setAction(
                if (running) ACTION_PAUSE else ACTION_RESUME
            ).putExtra(EXTRA_MS, ms),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (ms <= 0) "Hết giờ!" else formatTime(ms)
        val title = if (running) "⏱ Đếm ngược đang chạy" else if (ms <= 0) "⏱ Hết giờ" else "⏸ Đếm ngược tạm dừng"

        return NotificationCompat.Builder(this, AlarmNotificationHelper.CHANNEL_CHRONO)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ms > 0)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (running) "Tạm dừng" else if (ms > 0) "Tiếp tục" else "OK",
                if (ms > 0) togglePi else stopPi
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
                .notify(AlarmNotificationHelper.NOTIF_ID_TIMER, buildNotification(ms, running))
        } catch (_: SecurityException) {
        }
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        isActive = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "timer.START"
        const val ACTION_PAUSE = "timer.PAUSE"
        const val ACTION_RESUME = "timer.RESUME"
        const val ACTION_STOP = "timer.STOP"
        const val ACTION_TICK_QUERY = "timer.QUERY"
        const val ACTION_UPDATE = "com.example.alarmclock.TIMER_UPDATE"
        const val ACTION_FINISHED = "com.example.alarmclock.TIMER_FINISHED"
        const val EXTRA_MS = "ms"
        const val EXTRA_RUNNING = "running"

        @Volatile var isActive = false
        @Volatile var remainingMs = 0L
    }
}
