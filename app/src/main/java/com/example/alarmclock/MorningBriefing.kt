package com.example.alarmclock

import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object MorningBriefing {
    fun speakAfterDismiss(context: Context, alarmId: Int = -1) {
        val app = context.applicationContext
        val alarm = if (alarmId >= 0) {
            try { AlarmRepository(app).getAlarms().find { it.id == alarmId } } catch (_: Exception) { null }
        } else null
        val on = alarm?.routineOn == true || (alarm == null && AppSettings.isMorningBriefing(app))
        if (!on) return
        val wantWeather = alarm?.routineWeather ?: true
        val wantCal = alarm?.routineCalendar ?: true
        val wantTasks = alarm?.routineTasks ?: true
        thread {
            try {
                val day = SimpleDateFormat("EEEE, dd/MM", Locale("vi", "VN")).format(Date())
                val text = buildString {
                    append("Xin chào. Hôm nay $day. ")
                    if (wantWeather) {
                        val w = try { WeatherHelper.fetchWeatherSummary("Hanoi") } catch (_: Exception) { "" }
                        if (w.isNotBlank()) append(w).append(" ")
                    }
                    if (wantCal) {
                        val ev = todayEvents(app)
                        append(if (ev.isNotBlank()) "Lịch hôm nay: $ev. " else "Hôm nay không có sự kiện lịch. ")
                    }
                    if (wantTasks) {
                        val tasks = AppSettings.getRoutineTasksText(app).trim()
                        val note = alarm?.voiceNote?.trim().orEmpty()
                        when {
                            tasks.isNotBlank() -> append("Việc cần làm: $tasks. ")
                            note.isNotBlank() -> append(note).append(" ")
                            else -> append("Chưa đặt việc cần làm. ")
                        }
                    }
                    append(nextAlarmText(app))
                }
                val tts = TtsHelper(app)
                Thread.sleep(700)
                tts.speak(text)
                Thread.sleep(22_000)
                tts.shutdown()
            } catch (_: Exception) {}
        }
    }

    private fun todayEvents(context: Context): String {
        return try {
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val end = start + 24L * 60 * 60 * 1000
            val uri = CalendarContract.Events.CONTENT_URI
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.DTSTART}>=? AND ${CalendarContract.Events.DTSTART}<? AND ${CalendarContract.Events.DELETED}=0",
                arrayOf(start.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                val titles = mutableListOf<String>()
                while (c.moveToNext() && titles.size < 3) {
                    val title = c.getString(0)?.trim().orEmpty()
                    if (title.isNotBlank()) titles.add(title)
                }
                titles.joinToString(", ")
            } ?: ""
        } catch (_: Exception) {
            ""
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
