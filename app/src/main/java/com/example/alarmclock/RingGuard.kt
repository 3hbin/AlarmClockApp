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
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val nowBucket = now / 60_000L
        // Phút mà lần kêu này đã bắt đầu (nếu biết), để chỉ chặn đúng phút đó.
        val ringBucket = if (p.getInt("fired_id", Int.MIN_VALUE) == alarmId)
            p.getLong("fired_bucket", nowBucket) else nowBucket
        p.edit()
            .putInt("dismiss_id", alarmId)
            .putLong("dismiss_until", now + 3 * 60_000L)
            .putLong("dismiss_b1", ringBucket)
            .putLong("dismiss_b2", nowBucket)
            .putLong("fired_bucket", nowBucket)
            .putInt("fired_id", alarmId)
            .apply()
    }

    /**
     * Đã tắt báo thức này và vẫn đang ở đúng phút của lần kêu đó -> không kêu lại.
     * Báo thức mới / sửa giờ (cùng id nhưng phút khác) KHÔNG bị chặn nữa. Trước đây chặn cả 3 phút
     * nên đặt báo thức ngay sau khi tắt thì không kêu hoặc kêu trễ.
     */
    fun isDismissed(context: Context, alarmId: Int): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now > p.getLong("dismiss_until", 0L)) return false
        val id = p.getInt("dismiss_id", Int.MIN_VALUE)
        if (!(id == alarmId || alarmId < 0)) return false
        val bucket = now / 60_000L
        return bucket == p.getLong("dismiss_b1", bucket) || bucket == p.getLong("dismiss_b2", bucket)
    }

    fun alreadyFiredThisMinute(context: Context, alarmId: Int): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bucket = System.currentTimeMillis() / 60_000L
        return p.getInt("fired_id", Int.MIN_VALUE) == alarmId &&
            p.getLong("fired_bucket", -1L) == bucket
    }
}
