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
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            fun applyHour() {
                val (h, m) = alarm.hourFor(this)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
            }
            applyHour()
            if (alarm.repeatMode == Alarm.REPEAT_YEARLY || alarm.id == BirthdayHelper.ALARM_ID) {
                val month = AppSettings.getBirthdayMonth(context)
                val day = AppSettings.getBirthdayDay(context)
                if (month in 1..12 && day in 1..31) {
                    timeInMillis = BirthdayHelper.nextOccurrenceMillis(
                        month, day, alarm.hour, alarm.minute
                    )
                } else if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.YEAR, 1)
                    applyHour()
                }
            } else if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
                applyHour()
            }
            if (alarm.repeatMode == Alarm.REPEAT_WEEKDAYS) {
                while (get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) {
                    add(Calendar.DAY_OF_YEAR, 1)
                    applyHour()
                }
            }
            if (alarm.skipHolidays) {
                while (VietnamHolidays.isHoliday(this)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                    applyHour()
                }
            }
        }

        try {
            val show = PendingIntent.getActivity(
                context, alarm.id + 30000,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, show),
                pendingIntent
            )
        } catch (se: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }
        try { AlarmKeepAliveService.sync(context) } catch (_: Exception) {}
    }

    fun cancel(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        try { AlarmKeepAliveService.sync(context) } catch (_: Exception) {}
    }

    fun rescheduleAll(context: Context) {
        val repo = AlarmRepository(context)
        repo.getAlarms().filter { it.isEnabled }.forEach { schedule(context, it) }
        try { AlarmKeepAliveService.sync(context) } catch (_: Exception) {}
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
        try {
            val show = PendingIntent.getActivity(
                context, alarmId + 40000,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), pendingIntent)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
            }
        }
    }
}
