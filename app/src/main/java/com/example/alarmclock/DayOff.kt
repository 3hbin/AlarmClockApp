package com.example.alarmclock

import android.content.Context
import java.util.Calendar

object DayOff {
    private fun prefs(context: Context) =
        context.getSharedPreferences("day_off", Context.MODE_PRIVATE)

    fun isPausedToday(context: Context): Boolean {
        val until = prefs(context).getLong("pause_until", 0L)
        return System.currentTimeMillis() < until
    }

    fun pauseToday(context: Context) {
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis
        prefs(context).edit().putLong("pause_until", end).apply()
    }

    fun clearPause(context: Context) {
        prefs(context).edit().remove("pause_until").apply()
    }

    fun skipped(context: Context): Set<String> =
        prefs(context).getStringSet("dates", emptySet()) ?: emptySet()

    fun toggle(context: Context, ymd: String) {
        val next = skipped(context).toMutableSet()
        if (!next.add(ymd)) next.remove(ymd)
        prefs(context).edit().putStringSet("dates", next).apply()
    }

    fun isSkipped(context: Context, cal: Calendar): Boolean {
        val ymd = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        return skipped(context).contains(ymd)
    }
}
