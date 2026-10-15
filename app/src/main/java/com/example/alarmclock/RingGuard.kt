package com.example.alarmclock

import android.content.Context

/** Chặn báo thức bật lại màn hình sau khi người dùng đã tắt. */
object RingGuard {
    private const val PREFS = "ring_guard"

    fun markFired(context: Context, alarmId: Int) {
        val bucket = System.currentTimeMillis() / 60_000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("fired_id", alarmId)
            .putLong("fired_bucket", bucket)
            .apply()
    }

    fun markDismissed(context: Context, alarmId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("dismiss_id", alarmId)
            .putLong("dismiss_until", System.currentTimeMillis() + 3 * 60_000L)
            .putLong("fired_bucket", System.currentTimeMillis() / 60_000L)
            .putInt("fired_id", alarmId)
            .apply()
    }

    fun isDismissed(context: Context, alarmId: Int): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() > p.getLong("dismiss_until", 0L)) return false
        val id = p.getInt("dismiss_id", Int.MIN_VALUE)
        return id == alarmId || alarmId < 0
    }

    fun alreadyFiredThisMinute(context: Context, alarmId: Int): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bucket = System.currentTimeMillis() / 60_000L
        return p.getInt("fired_id", Int.MIN_VALUE) == alarmId &&
            p.getLong("fired_bucket", -1L) == bucket
    }
}
