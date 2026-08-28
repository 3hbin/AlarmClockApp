package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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

        // Thông báo có nút Tắt (chỉ khi không challenge / không chống troll)
        val allowDirectDismiss =
            challengeType == Alarm.CHALLENGE_NONE &&
                !AppSettings.isAntiTroll(context) &&
                !isStrict
        AlarmNotificationHelper.showRingingNotification(
            context, alarmId, label, allowDirectDismiss
        )

        val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_HOUR", intent.getIntExtra("ALARM_HOUR", -1))
            putExtra("ALARM_MINUTE", intent.getIntExtra("ALARM_MINUTE", -1))
            putExtra("SNOOZE_MINUTES", snoozeMinutes)
            putExtra("REPEAT_MODE", repeatMode)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("CHALLENGE_TYPE", challengeType)
            putExtra("SHAKE_TARGET_COUNT", shakeTargetCount)
            putExtra("STRICT_ANTI_SNOOZE", isStrict)
            putExtra("VOICE_NOTE", voiceNote)
            putExtra("USE_CRESCENDO", useCrescendo)
        }
        context.startActivity(ringIntent)
    }
}
