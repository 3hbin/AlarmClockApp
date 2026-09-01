package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

/**
 * Nhận lệnh từ Google Assistant / Gemini / Hey Google:
 *  - "Đặt báo thức 7 giờ sáng"
 *  - "Set an alarm for 6:30 with challenge"
 * Intent: android.intent.action.SET_ALARM
 */
class VoiceAlarmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM, "android.intent.action.SET_ALARM" -> createFromAssistant(intent)
            AlarmClock.ACTION_SHOW_ALARMS, "android.intent.action.SHOW_ALARMS" -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            }
            AlarmClock.ACTION_SET_TIMER, "android.intent.action.SET_TIMER" -> {
                val secs = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 60)
                startActivity(Intent(this, TimerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("VOICE_TIMER_SECONDS", secs)
                })
            }
            else -> {
                // Deep link / Gemini có thể gửi extras tương tự
                if (intent.hasExtra(AlarmClock.EXTRA_HOUR) || intent.hasExtra("hour")) {
                    createFromAssistant(intent)
                }
            }
        }
    }

    private fun createFromAssistant(intent: Intent) {
        val cal = Calendar.getInstance()
        val hour = intent.getIntExtra(
            AlarmClock.EXTRA_HOUR,
            intent.getIntExtra("hour", cal.get(Calendar.HOUR_OF_DAY))
        )
        val minute = intent.getIntExtra(
            AlarmClock.EXTRA_MINUTES,
            intent.getIntExtra("minutes", intent.getIntExtra("minute", 0))
        )
        val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE)
            ?: intent.getStringExtra("message")
            ?: getString(R.string.app_name)

        // Gemini / Assistant: nếu message có từ khóa thử thách → gắn challenge
        val lower = message.lowercase()
        val challenge = when {
            "all easy" in lower || "dễ" in lower && "thử thách" in lower -> Alarm.CHALLENGE_ALL_EASY
            "all" in lower || "tất cả" in lower -> Alarm.CHALLENGE_ALL
            "math" in lower || "toán" in lower -> Alarm.CHALLENGE_MATH10
            "shake" in lower || "lắc" in lower -> Alarm.CHALLENGE_SHAKE100
            "face" in lower || "mặt" in lower || "cười" in lower -> Alarm.CHALLENGE_FACE_EXPR
            "read" in lower || "đọc" in lower || "chữ" in lower -> Alarm.CHALLENGE_READ
            "tap" in lower || "bấm" in lower -> Alarm.CHALLENGE_TAP200
            "challenge" in lower || "thử thách" in lower || "gemini" in lower -> Alarm.CHALLENGE_ALL_EASY
            else -> Alarm.CHALLENGE_ALL_EASY // mặc định: thử thách dễ khi đặt bằng giọng nói
        }

        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)
        val repeat = if (days != null && days.isNotEmpty()) Alarm.REPEAT_DAILY else Alarm.REPEAT_ONCE

        val repo = AlarmRepository(this)
        val alarms = repo.getAlarms().toMutableList()
        val alarm = Alarm(
            id = repo.getNextId(),
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            isEnabled = true,
            label = "🎙 $message",
            repeatMode = repeat,
            snoozeMinutes = 5,
            ringtoneUri = "app:soft_chime",
            challengeType = challenge,
            shakeTargetCount = if (challenge == Alarm.CHALLENGE_SHAKE100) 100 else 30,
            useCrescendo = true
        )
        alarms.add(alarm)
        repo.saveAlarms(alarms)
        AlarmScheduler.schedule(this, alarm)

        val labelChallenge = Alarm.challengeName(challenge)
        Toast.makeText(
            this,
            "Đã đặt ${"%02d:%02d".format(alarm.hour, alarm.minute)} · $labelChallenge",
            Toast.LENGTH_LONG
        ).show()

        if (!skipUi) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }
}
