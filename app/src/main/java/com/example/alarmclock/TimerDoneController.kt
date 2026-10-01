package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

object TimerDoneController {

    private const val REQ = 7721

    @Volatile
    var ringing = false

    fun scheduleExact(context: Context, durationMs: Long) {
        cancelExact(context)
        if (durationMs <= 0) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = System.currentTimeMillis() + durationMs
        val pi = alarmPi(context)
        val show = PendingIntent.getActivity(
            context, REQ + 1,
            Intent(context, TimerDoneActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun cancelExact(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmPi(context))
        } catch (_: Exception) {}
    }

    fun fire(context: Context) {
        val already = ringing
        ringing = true
        if (!already) {
            try { TonePlayer.playAppRaw(context, R.raw.ringtone_oz, loop = true) } catch (_: Exception) {}
            vibrate(context)
        }
        if (AppVisibility.isForeground()) {
            showInApp(context)
            if (!already) {
                try {
                    context.sendBroadcast(
                        Intent(TimerService.ACTION_FINISHED)
                            .setPackage(context.packageName)
                            .putExtra("in_app", true)
                    )
                } catch (_: Exception) {}
            }
            return
        }
        promoteToFullScreen(context)
        if (!already) {
            try {
                context.sendBroadcast(Intent(TimerService.ACTION_FINISHED).setPackage(context.packageName))
            } catch (_: Exception) {}
        }
    }

    /** App đã ẩn: full-screen intent + TimerDoneActivity. Không dùng khi đang mở app. */
    fun promoteToFullScreen(context: Context) {
        if (!ringing) return
        wake(context)
        AlarmNotificationHelper.postTimerDoneFullScreen(context)
        try {
            val app = context.applicationContext
            val i = Intent(app, TimerService::class.java).setAction(TimerService.ACTION_HOLD_DONE)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
        } catch (_: Exception) {}
        try {
            context.startActivity(
                Intent(context, TimerDoneActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                }
            )
        } catch (_: Exception) {}
    }

    private fun showInApp(context: Context) {
        val act = AppVisibility.resumedActivity() ?: return
        act.runOnUiThread {
            if (act.isFinishing || act.isDestroyed) return@runOnUiThread
            try {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                    .setTitle("⏰ Hết giờ!")
                    .setMessage("Đếm ngược đã kết thúc. Bấm Tắt để dừng chuông.")
                    .setCancelable(false)
                    .setPositiveButton("Tắt") { _, _ -> dismiss(act) }
                    .show()
            } catch (_: Exception) {}
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 250, 400), 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 400, 250, 400), 0)
            }
        } catch (_: Exception) {}
    }

    fun dismiss(context: Context) {
        ringing = false
        try { TonePlayer.stop() } catch (_: Exception) {}
        try {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vib.cancel()
        } catch (_: Exception) {}
        cancelExact(context)
        AlarmNotificationHelper.cancelTimerDone(context)
        try { context.stopService(Intent(context, TimerService::class.java)) } catch (_: Exception) {}
    }

    private fun alarmPi(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context, REQ,
            Intent(context, TimerDoneReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun wake(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "AlarmClock:TimerDone"
            )
            wl.acquire(15_000L)
        } catch (_: Exception) {}
    }
}
