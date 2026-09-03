package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Handler
import android.os.Looper

/**
 * Nhận báo thức — Samsung/OEM thường chặn startActivity từ background.
 * Chiến lược: full-screen notification + wake lock + thử mở activity.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "AlarmClock:AlarmReceiver"
        ).apply {
            setReferenceCounted(false)
            acquire(60_000L)
        }

        try {
            val alarmId = intent.getIntExtra("ALARM_ID", -1)
            val label = intent.getStringExtra("ALARM_LABEL") ?: "Báo thức"
            val snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 5)
            val repeatMode = intent.getIntExtra("REPEAT_MODE", Alarm.REPEAT_DAILY)
            val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
            val challengeType = intent.getIntExtra("CHALLENGE_TYPE", Alarm.CHALLENGE_NONE)
            val shakeTargetCount = intent.getIntExtra("SHAKE_TARGET_COUNT", 10)
            val isStrict = intent.getBooleanExtra("STRICT_ANTI_SNOOZE", false)
            val voiceNote = intent.getStringExtra("VOICE_NOTE")
            val useCrescendo = intent.getBooleanExtra("USE_CRESCENDO", true)
            val hour = intent.getIntExtra("ALARM_HOUR", -1)
            val minute = intent.getIntExtra("ALARM_MINUTE", -1)

            val allowDirectDismiss =
                challengeType == Alarm.CHALLENGE_NONE &&
                    !AppSettings.isAntiTroll(context) &&
                    !isStrict

            // 1) Full-screen intent notification (quan trọng trên Samsung khi khóa màn)
            AlarmNotificationHelper.showRingingNotification(
                context = context,
                alarmId = alarmId,
                label = label,
                allowDirectDismiss = allowDirectDismiss,
                hour = hour,
                minute = minute,
                snoozeMinutes = snoozeMinutes,
                repeatMode = repeatMode,
                ringtoneUri = ringtoneUri,
                challengeType = challengeType,
                shakeTargetCount = shakeTargetCount,
                isStrict = isStrict,
                voiceNote = voiceNote,
                useCrescendo = useCrescendo
            )

            // 1b) Foreground service phát chuông (Samsung A12: Activity bị chặn khi khóa)
            try {
                val svc = Intent(context, AlarmRingService::class.java).apply {
                    putExtra("ALARM_ID", alarmId)
                    putExtra("ALARM_LABEL", label)
                    putExtra("ALARM_HOUR", hour)
                    putExtra("ALARM_MINUTE", minute)
                    putExtra("SNOOZE_MINUTES", snoozeMinutes)
                    putExtra("REPEAT_MODE", repeatMode)
                    putExtra("RINGTONE_URI", ringtoneUri)
                    putExtra("CHALLENGE_TYPE", challengeType)
                    putExtra("SHAKE_TARGET_COUNT", shakeTargetCount)
                    putExtra("STRICT_ANTI_SNOOZE", isStrict)
                    putExtra("VOICE_NOTE", voiceNote)
                    putExtra("USE_CRESCENDO", useCrescendo)
                }
                AlarmRingService.start(context, svc)
            } catch (_: Exception) {}

            // 2) Thử mở activity trực tiếp (một số máy vẫn cho phép)
            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_LABEL", label)
                putExtra("ALARM_HOUR", hour)
                putExtra("ALARM_MINUTE", minute)
                putExtra("SNOOZE_MINUTES", snoozeMinutes)
                putExtra("REPEAT_MODE", repeatMode)
                putExtra("RINGTONE_URI", ringtoneUri)
                putExtra("CHALLENGE_TYPE", challengeType)
                putExtra("SHAKE_TARGET_COUNT", shakeTargetCount)
                putExtra("STRICT_ANTI_SNOOZE", isStrict)
                putExtra("VOICE_NOTE", voiceNote)
                putExtra("USE_CRESCENDO", useCrescendo)
            }
            try {
                context.startActivity(ringIntent)
            } catch (_: Exception) {
                // FSI notification sẽ mở màn khi hệ thống cho phép
            }

            // 3) Thử lại sau 800ms (Samsung đôi khi cần delay)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    context.startActivity(ringIntent)
                } catch (_: Exception) {
                } finally {
                    try {
                        if (wakeLock.isHeld) wakeLock.release()
                    } catch (_: Exception) {
                    }
                    pending.finish()
                }
            }, 800L)
        } catch (e: Exception) {
            try {
                if (wakeLock.isHeld) wakeLock.release()
            } catch (_: Exception) {
            }
            pending.finish()
        }
    }
}
