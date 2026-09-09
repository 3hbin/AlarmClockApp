package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object WidgetUpdateHelper {

    const val ACTION_TOGGLE = "com.alarmclock.dongho.widget.TOGGLE"
    const val ACTION_TICK = "com.alarmclock.dongho.widget.TICK"
    const val EXTRA_ALARM_ID = "ALARM_ID"

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        listOf(
            AlarmWidget1x1Provider::class.java,
            AlarmWidget2x2Provider::class.java,
            AlarmWidget4x2Provider::class.java
        ).forEach { cls ->
            val ids = mgr.getAppWidgetIds(ComponentName(app, cls))
            if (ids.isNotEmpty()) {
                val i = Intent(app, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                app.sendBroadcast(i)
            }
        }
        scheduleTick(app)
    }

    fun nextEnabledAlarms(context: Context, limit: Int = 3): List<Pair<Alarm, Long>> {
        val now = System.currentTimeMillis()
        return AlarmRepository(context).getAlarms()
            .map { it to nextTrigger(it) }
            .filter { it.second > now || it.first.isEnabled }
            .sortedWith(compareBy({ !it.first.isEnabled }, { it.second }))
            .take(limit)
    }

    fun nearestAlarm(context: Context): Pair<Alarm, Long>? {
        val enabled = AlarmRepository(context).getAlarms().filter { it.isEnabled }
        if (enabled.isEmpty()) return null
        return enabled.map { it to nextTrigger(it) }.minByOrNull { it.second }
    }

    fun nextTrigger(alarm: Alarm): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            if (alarm.repeatMode == Alarm.REPEAT_WEEKDAYS) {
                while (get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    fun formatCountdown(ms: Long): String {
        val left = (ms - System.currentTimeMillis()).coerceAtLeast(0)
        val h = left / 3_600_000
        val m = (left % 3_600_000) / 60_000
        return if (h > 0) "${h}g ${m}p" else "${m} phút"
    }

    fun formatHm(alarm: Alarm) = "%02d:%02d".format(alarm.hour, alarm.minute)

    private fun scheduleTick(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 7711,
                Intent(context, WidgetTickReceiver::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC, cal.timeInMillis, pi)
            }
        } catch (_: Exception) {}
    }
}
