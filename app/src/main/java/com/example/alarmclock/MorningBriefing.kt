package com.example.alarmclock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object MorningBriefing {
    fun speakAfterDismiss(context: Context) {
        if (!AppSettings.isMorningBriefing(context)) return
        val app = context.applicationContext
        thread {
            try {
                val day = SimpleDateFormat("EEEE, dd/MM", Locale("vi", "VN")).format(Date())
                val next = nextAlarmText(app)
                val weather = try { WeatherHelper.fetchWeatherSummary("Hanoi") } catch (_: Exception) { "" }
                val text = buildString {
                    append("Chào buổi sáng. Hôm nay $day. ")
                    if (weather.isNotBlank()) append(weather).append(" ")
                    append(next)
                }
                val tts = TtsHelper(app)
                Thread.sleep(600)
                tts.speak(text)
                Thread.sleep(20_000)
                tts.shutdown()
            } catch (_: Exception) {}
        }
    }

    private fun nextAlarmText(context: Context): String {
        val enabled = AlarmRepository(context).getAlarms().filter { it.isEnabled }
        if (enabled.isEmpty()) return "Không còn báo thức nào đang bật."
        var best: Pair<Alarm, Long>? = null
        val now = System.currentTimeMillis()
        enabled.forEach { a ->
            val cal = Calendar.getInstance()
            val (h, m) = a.hourFor(cal)
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            cal.set(Calendar.SECOND, 0)
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
            val t = cal.timeInMillis
            if (best == null || t < best!!.second) best = a to t
        }
        val a = best!!.first
        return "Báo thức tiếp theo ${a.label} lúc ${"%02d:%02d".format(a.hour, a.minute)}."
    }
}
