package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Nhận lệnh từ Google Assistant / Gemini / Hey Google (SET_ALARM).
 * Phải setResult(RESULT_OK) để Gemini không báo "Đã xảy ra lỗi".
 */
class VoiceAlarmActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
            setResult(Activity.RESULT_OK)
        } catch (e: Exception) {
            e.printStackTrace()
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            handle(intent)
            setResult(Activity.RESULT_OK)
        } catch (e: Exception) {
            setResult(Activity.RESULT_CANCELED)
        }
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
                if (intent.hasExtra(AlarmClock.EXTRA_HOUR) ||
                    intent.hasExtra("hour") ||
                    intent.getStringExtra(AlarmClock.EXTRA_MESSAGE) != null
                ) {
                    createFromAssistant(intent)
                }
            }
        }
    }

    private fun createFromAssistant(intent: Intent) {
        val message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE)
            ?: intent.getStringExtra("message")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: "Báo thức giọng nói"

        val parsed = parseTimeFromMessage(message)
        var hour = when {
            intent.hasExtra(AlarmClock.EXTRA_HOUR) ->
                intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
            intent.hasExtra("hour") -> intent.getIntExtra("hour", -1)
            else -> -1
        }
        var minute = when {
            intent.hasExtra(AlarmClock.EXTRA_MINUTES) ->
                intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
            intent.hasExtra("minutes") -> intent.getIntExtra("minutes", 0)
            intent.hasExtra("minute") -> intent.getIntExtra("minute", 0)
            else -> 0
        }

        // Assistant đôi khi không gửi EXTRA_HOUR → lấy từ câu nói
        if (hour < 0 || hour > 23) {
            hour = parsed?.first ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (parsed != null) minute = parsed.second
        } else if (parsed != null && !intent.hasExtra(AlarmClock.EXTRA_MINUTES)) {
            // có giờ từ extra nhưng phút trong câu
            minute = parsed.second
        }

        // EXTRA_IS_PM (nếu có)
        if (intent.getBooleanExtra(AlarmClock.EXTRA_IS_PM, false) && hour in 1..11) {
            hour += 12
        }
        // "7 giờ sáng" → 7; "7 giờ tối/chiều" → 19
        val lowerMsg = message.lowercase()
        if (("sáng" in lowerMsg || "morning" in lowerMsg) && hour in 13..23) {
            hour -= 12
        }
        if (("tối" in lowerMsg || "chiều" in lowerMsg || "evening" in lowerMsg || "pm" in lowerMsg) && hour in 1..11) {
            hour += 12
        }

        val lower = lowerMsg
        val challenge = when {
            "all easy" in lower || ("dễ" in lower && "thử thách" in lower) -> Alarm.CHALLENGE_ALL_EASY
            "all" in lower || "tất cả" in lower -> Alarm.CHALLENGE_ALL
            "math" in lower || "toán" in lower -> Alarm.CHALLENGE_MATH10
            "shake" in lower || "lắc" in lower -> Alarm.CHALLENGE_SHAKE100
            "face" in lower || "mặt" in lower || "cười" in lower -> Alarm.CHALLENGE_FACE_EXPR
            "read" in lower || "đọc" in lower || "chữ" in lower -> Alarm.CHALLENGE_READ
            "tap" in lower || "bấm" in lower -> Alarm.CHALLENGE_TAP200
            "challenge" in lower || "thử thách" in lower -> Alarm.CHALLENGE_ALL_EASY
            else -> Alarm.CHALLENGE_ALL_EASY
        }

        // Lặp lại
        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)
        val repeat = when {
            "hàng ngày" in lower || "every day" in lower || "hằng ngày" in lower -> Alarm.REPEAT_DAILY
            "thứ 2" in lower || "thứ hai" in lower || "weekdays" in lower ||
                "ngày thường" in lower || "t2" in lower -> Alarm.REPEAT_WEEKDAYS
            days != null && days.isNotEmpty() -> {
                val weekdayOnly = days.all {
                    it in Calendar.MONDAY..Calendar.FRIDAY
                }
                if (weekdayOnly) Alarm.REPEAT_WEEKDAYS else Alarm.REPEAT_DAILY
            }
            else -> Alarm.REPEAT_ONCE // 1 lần — tự tắt sau khi kêu (tiết kiệm pin)
        }

        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true)

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

        val repeatText = when (repeat) {
            Alarm.REPEAT_ONCE -> "1 lần (tự tắt)"
            Alarm.REPEAT_WEEKDAYS -> "T2–T6"
            else -> "Hàng ngày"
        }
        val labelChallenge = Alarm.challengeLabel(challenge)
        Toast.makeText(
            this,
            "Đã đặt ${"%02d:%02d".format(alarm.hour, alarm.minute)} · $repeatText · $labelChallenge",
            Toast.LENGTH_LONG
        ).show()

        if (!skipUi) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    /** "5:30", "7 giờ", "7h30", "05 giờ 30" */
    private fun parseTimeFromMessage(msg: String): Pair<Int, Int>? {
        val m1 = Pattern.compile(
            """(\d{1,2})\s*[:hHgiờ]\s*(\d{1,2})""",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        ).matcher(msg)
        if (m1.find()) {
            val h = m1.group(1)!!.toIntOrNull() ?: return null
            val min = m1.group(2)!!.toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return h to min
        }
        val m2 = Pattern.compile(
            """(\d{1,2})\s*(giờ|h|hour)""",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        ).matcher(msg)
        if (m2.find()) {
            val h = m2.group(1)!!.toIntOrNull() ?: return null
            if (h in 0..23) return h to 0
        }
        return null
    }
}
