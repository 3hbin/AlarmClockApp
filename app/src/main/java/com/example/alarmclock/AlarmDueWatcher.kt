package com.example.alarmclock

import android.content.Context
import android.content.Intent
import java.util.Calendar

/** App đang mở mà AlarmManager không bắn broadcast thì vẫn kêu đúng phút. */
object AlarmDueWatcher {
    private const val GRACE_MS = 90_000L

    fun fireIfDue(context: Context) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("due_fired", Context.MODE_PRIVATE)
        val alarms = try { AlarmRepository(context).getAlarms() } catch (_: Exception) { return }
        // Giống AlarmScheduler: tạm dừng ở nhà / nghỉ hôm nay thì không kêu.
        if (AppSettings.isPauseAlarmsAtHome(context)) return
        if (DayOff.isPausedToday(context)) return
        for (alarm in alarms) {
            if (!alarm.isEnabled) continue
            // Sinh nhật / hàng năm do AlarmReceiver xử lý (cần kiểm tra ngày tháng).
            if (alarm.repeatMode == Alarm.REPEAT_YEARLY || alarm.id == BirthdayHelper.ALARM_ID) continue
            val today = Calendar.getInstance()
            // Giờ kêu của hôm nay (có tính lịch riêng cuối tuần).
            val (h, m) = alarm.hourFor(today)
            val dow = today.get(Calendar.DAY_OF_WEEK)
            // T2–T6 không kêu T7/CN.
            if (alarm.repeatMode == Alarm.REPEAT_WEEKDAYS &&
                (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)) continue
            if (alarm.skipHolidays && VietnamHolidays.isHoliday(today)) continue
            if (DayOff.isSkipped(context, today)) continue
            val trigger = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
            }.timeInMillis
            val late = now - trigger
            if (late < 0 || late > GRACE_MS) continue
            // Báo thức vừa đặt đúng phút hiện tại đã được hẹn sang ngày mai -> không kêu ngay.
            val skip = context.getSharedPreferences("alarm_skip", Context.MODE_PRIVATE)
                .getLong("s_" + alarm.id, -1L)
            if (skip == trigger) continue
            if (RingGuard.isDismissed(context, alarm.id)) continue
            if (RingGuard.alreadyFiredThisMinute(context, alarm.id)) continue
            val key = "${alarm.id}_$trigger"
            if (prefs.getBoolean(key, false)) continue
            prefs.edit().putBoolean(key, true).apply()
            RingGuard.markFired(context, alarm.id)
            ring(context, alarm)
        }
    }

    private fun ring(context: Context, alarm: Alarm) {
        val extras = Intent().apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_LABEL", alarm.label)
            putExtra("ALARM_HOUR", alarm.hour)
            putExtra("ALARM_MINUTE", alarm.minute)
            putExtra("SNOOZE_MINUTES", alarm.snoozeMinutes)
            putExtra("REPEAT_MODE", alarm.repeatMode)
            putExtra("RINGTONE_URI", alarm.ringtoneUri)
            putExtra("CHALLENGE_TYPE", alarm.challengeType)
            putExtra("SHAKE_TARGET_COUNT", alarm.shakeTargetCount)
            putExtra("STRICT_ANTI_SNOOZE", alarm.isStrictAntiSnooze)
            putExtra("VOICE_NOTE", alarm.voiceNote)
            putExtra("USE_CRESCENDO", alarm.useCrescendo)
        }
        try {
            AlarmNotificationHelper.saveRingingState(
                context, alarm.id, alarm.label, alarm.hour, alarm.minute,
                alarm.snoozeMinutes, alarm.repeatMode, alarm.ringtoneUri,
                alarm.challengeType, alarm.shakeTargetCount, alarm.isStrictAntiSnooze,
                alarm.voiceNote, alarm.useCrescendo
            )
        } catch (_: Exception) {}
        try { AlarmRingService.start(context, extras) } catch (_: Exception) {}
        try {
            context.startActivity(Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                extras.extras?.let { putExtras(it) }
            })
        } catch (_: Exception) {}
    }
}
