package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
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
            putExtra("SKIP_HOLIDAYS", alarm.skipHolidays)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }

            if (alarm.repeatMode == Alarm.REPEAT_WEEKDAYS) {
                while (get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Vietnam Holiday Auto-Skip
            if (alarm.skipHolidays) {
                while (VietnamHolidays.isHoliday(this)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                    // also avoid weekend if weekdays mode already handled, but safe
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context) {
        val repo = AlarmRepository(context)
        repo.getAlarms().filter { it.isEnabled }.forEach { schedule(context, it) }
    }

    fun scheduleSnooze(context: Context, alarmId: Int, minutes: Int, extras: Intent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtras(extras)
            putExtra("ALARM_ID", alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId + 10000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val trigger = System.currentTimeMillis() + minutes * 60_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
        }
    }
}
