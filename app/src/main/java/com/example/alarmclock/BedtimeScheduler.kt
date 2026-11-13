package com.example.alarmclock

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

/**
 * "Lịch biểu" giờ đi ngủ. Khi bật:
 *  1) mỗi ngày đến giờ đi ngủ app gửi thông báo nhắc,
 *  2) tự tạo một báo thức hằng ngày tên "Giờ dậy" ở giờ dậy đã chọn.
 * Tắt thì xóa nhắc nhở và báo thức "Giờ dậy" đó.
 */
object BedtimeScheduler {
    private const val PREFS = "bedtime"
    private const val REQ = 7701
    private const val NOTIF_ID = 7701
    private const val CHANNEL = "bedtime_reminder_v1"
    const val ACTION = "com.example.alarmclock.BEDTIME_REMINDER"
    const val WAKE_LABEL = "Giờ dậy"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(c: Context) = prefs(c).getBoolean("bed_on", false)

    /** Gọi sau khi bật / tắt hoặc đổi giờ. */
    fun apply(c: Context) {
        if (isOn(c)) {
            syncWakeAlarm(c, true)
            armReminder(c)
        } else {
            syncWakeAlarm(c, false)
            cancelReminder(c)
        }
    }

    /** Gọi sau khởi động lại máy. Báo thức "Giờ dậy" nằm trong danh sách báo thức nên đã được đặt lại. */
    fun armIfOn(c: Context) {
        if (isOn(c)) armReminder(c)
    }

    fun durationText(bh: Int, bm: Int, wh: Int, wm: Int): String {
        var mins = ((wh * 60 + wm) - (bh * 60 + bm) + 1440) % 1440
        if (mins == 0) mins = 1440
        val h = mins / 60
        val m = mins % 60
        return if (m == 0) "$h giờ" else "$h giờ $m phút"
    }

    // ------------------------------------------------------------ báo thức giờ dậy
    private fun syncWakeAlarm(c: Context, on: Boolean) {
        val p = prefs(c)
        val repo = AlarmRepository(c)
        val list = repo.getAlarms().toMutableList()
        val id = p.getInt("wake_alarm_id", -1)
        val existing = list.firstOrNull { it.id == id }
        if (on) {
            val h = p.getInt("wake_h", 7)
            val m = p.getInt("wake_m", 0)
            if (existing != null) {
                existing.hour = h
                existing.minute = m
                existing.isEnabled = true
                AlarmScheduler.schedule(c, existing)
            } else {
                val a = Alarm(
                    id = repo.getNextId(),
                    hour = h,
                    minute = m,
                    isEnabled = true,
                    label = WAKE_LABEL,
                    repeatMode = Alarm.REPEAT_DAILY
                )
                list.add(a)
                AlarmScheduler.schedule(c, a)
                p.edit().putInt("wake_alarm_id", a.id).apply()
            }
            repo.saveAlarms(list)
        } else {
            if (existing != null) {
                AlarmScheduler.cancel(c, existing.id)
                list.removeAll { it.id == existing.id }
                repo.saveAlarms(list)
            }
            p.edit().remove("wake_alarm_id").apply()
        }
    }

    // ------------------------------------------------------------ nhắc giờ đi ngủ
    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, REQ,
        Intent(c, BedtimeReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextBedMillis(c: Context): Long {
        val p = prefs(c)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getInt("bed_h", 23))
            set(Calendar.MINUTE, p.getInt("bed_m", 0))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun armReminder(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c)
        val at = nextBedMillis(c)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun cancelReminder(c: Context) {
        try {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(c))
        } catch (_: Exception) {}
    }

    fun notifyBedtime(c: Context) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Nhắc giờ đi ngủ", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val p = prefs(c)
            val bh = p.getInt("bed_h", 23)
            val bm = p.getInt("bed_m", 0)
            val wh = p.getInt("wake_h", 7)
            val wm = p.getInt("wake_m", 0)
            val open = PendingIntent.getActivity(
                c, REQ, Intent(c, BedtimeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = "Đi ngủ bây giờ để ngủ đủ " + durationText(bh, bm, wh, wm) +
                " trước báo thức %02d:%02d. Bấm để mở tiếng ru ngủ.".format(wh, wm)
            val b = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🌙 Đến giờ đi ngủ rồi")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
            NotificationManagerCompat.from(c).notify(NOTIF_ID, b.build())
        } catch (_: Exception) {}
    }
}

class BedtimeReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BedtimeScheduler.isOn(context)) return
        BedtimeScheduler.notifyBedtime(context)
        BedtimeScheduler.armReminder(context) // đặt cho ngày mai
    }
}
