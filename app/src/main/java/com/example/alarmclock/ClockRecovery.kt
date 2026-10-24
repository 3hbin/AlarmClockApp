package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Khôi phục đếm ngược và bấm giờ sau khi máy tắt / sập nguồn / app bị kill.
 * Mọi mốc thời gian lưu theo GIỜ HỆ THỐNG (không dùng elapsedRealtime vì bị đặt lại khi khởi động máy).
 * Được gọi từ BootReceiver, từ AlarmApp (mỗi lần app khởi động) và khi mở khóa màn hình.
 */
object ClockRecovery {
    private const val SW = "stopwatch_state"
    private const val TIMER = "timer_state"
    private const val NOTIF_ID = 7790
    private const val CHANNEL = "clock_recovery_v1"

    fun run(context: Context) {
        val app = context.applicationContext
        try { restoreTimer(app) } catch (_: Throwable) {}
        try { restoreStopwatch(app) } catch (_: Throwable) {}
    }

    // ------------------------------------------------------------ bấm giờ
    fun swSave(context: Context, running: Boolean, accumulatedMs: Long) {
        try {
            val now = System.currentTimeMillis()
            context.getSharedPreferences(SW, Context.MODE_PRIVATE).edit()
                .putBoolean("has", true)
                .putBoolean("running", running)
                .putLong("wall_start", if (running) now - accumulatedMs else 0L)
                .putLong("accum", accumulatedMs)
                .apply()
        } catch (_: Throwable) {}
    }

    fun swClear(context: Context) {
        try { context.getSharedPreferences(SW, Context.MODE_PRIVATE).edit().clear().apply() } catch (_: Throwable) {}
    }

    /** Chạy lại bấm giờ đã lưu (nếu service chưa chạy). Trả về true nếu đã khôi phục. */
    fun restoreStopwatch(context: Context): Boolean {
        if (StopwatchService.isActive) return false
        val sp = context.getSharedPreferences(SW, Context.MODE_PRIVATE)
        if (!sp.getBoolean("has", false)) return false
        val running = sp.getBoolean("running", false)
        val now = System.currentTimeMillis()
        val elapsed = if (running) (now - sp.getLong("wall_start", now)).coerceAtLeast(0L)
        else sp.getLong("accum", 0L)
        if (elapsed <= 0L) {
            swClear(context)
            return false
        }
        // Đặt trước các biến tĩnh để màn hình Bấm giờ vẽ đúng ngay, không chờ service khởi động
        StopwatchService.elapsedMs = elapsed
        StopwatchService.isActive = true
        StopwatchService.isRunningFlag = running
        StopwatchService.baseStartElapsed = if (running) SystemClock.elapsedRealtime() - elapsed else 0L
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StopwatchService::class.java)
                    .setAction(StopwatchService.ACTION_START)
                    .putExtra(StopwatchService.EXTRA_MS, elapsed)
            )
            if (!running) {
                context.startService(
                    Intent(context, StopwatchService::class.java).setAction(StopwatchService.ACTION_PAUSE)
                )
            }
            true
        } catch (_: Throwable) {
            StopwatchService.isActive = false
            StopwatchService.isRunningFlag = false
            StopwatchService.baseStartElapsed = 0L
            false
        }
    }

    // ------------------------------------------------------------ đếm ngược
    /** Chạy lại đếm ngược đã lưu; nếu đã hết giờ trong lúc máy tắt thì báo kết thúc. */
    fun restoreTimer(context: Context): Boolean {
        if (TimerService.isActive) return false
        val sp = context.getSharedPreferences(TIMER, Context.MODE_PRIVATE)
        if (!sp.getBoolean("running", false)) return false
        val endWall = sp.getLong("end_wall", 0L)
        if (endWall <= 0L) return false
        val left = endWall - System.currentTimeMillis()
        if (left > 1500L) {
            TimerService.isActive = true
            TimerService.remainingMs = left
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TimerService::class.java)
                        .setAction(TimerService.ACTION_RESUME)
                        .putExtra(TimerService.EXTRA_MS, left)
                )
                true
            } catch (_: Throwable) {
                TimerService.isActive = false
                TimerService.remainingMs = 0L
                false
            }
        }
        // Đã hết giờ
        sp.edit().clear().apply()
        val overdue = -left
        if (overdue <= 5 * 60_000L) {
            try { TimerDoneController.fire(context) } catch (_: Throwable) {}
        } else {
            postTimerEnded(context, endWall)
        }
        return true
    }

    private fun postTimerEnded(context: Context, endWall: Long) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Đếm ngược đã kết thúc", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val at = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endWall))
            val b = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏲ Đếm ngược đã kết thúc")
                .setContentText("Hết giờ lúc $at (trong lúc máy tắt hoặc app bị đóng).")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            NotificationManagerCompat.from(context).notify(NOTIF_ID, b.build())
        } catch (_: Throwable) {}
    }
}
