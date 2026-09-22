package com.example.alarmclock

import android.content.Context
import android.os.Handler
import android.os.Looper
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
        val on = alarm?.routineOn == true || AppSettings.isMorningBriefing(app)
        if (!on) return

        val wantWeather = alarm?.routineWeather ?: true
        val wantCal = alarm?.routineCalendar ?: true
        val wantTasks = alarm?.routineTasks ?: true
        val wantTomorrow = alarm?.routineTomorrow ?: true
        val en = AppSettings.isEnglishUi(app)

        thread {
            val now = Calendar.getInstance()
            val timeStr = if (en)
                String.format(Locale.US, "%d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            else
                String.format(Locale.getDefault(), "%02d giờ %02d phút", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            val day = if (en) SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date())
            else SimpleDateFormat("EEEE, 'ngày' dd 'tháng' MM", Locale("vi", "VN")).format(Date())
            val place = try { LocationPlaceHelper.resolve(app) } catch (_: Exception) { null }
            val label = alarm?.label?.trim().orEmpty()

            val text = buildString {
                if (en) {
                    append("Hello. Time to wake up. ")
                    append("It is $timeStr, $day. ")
                    if (place != null) append(place.speakLine(true))
                    else append("Location is off. Open the app and allow location. ")
                } else {
                    append("Xin chào. Đã đến giờ dậy. ")
                    append("Bây giờ $timeStr, $day. ")
                    if (place != null) append(place.speakLine(false))
                    else append("Chưa có vị trí. Hãy mở app và cấp quyền vị trí. ")
                }
                if (wantWeather) {
                    val w = try {
                        if (place != null) WeatherHelper.fetchWeatherAt(place.lat, place.lon, place.shortCity(), en)
                        else WeatherHelper.fetchWeatherSummary("Hanoi", en)
                    } catch (_: Exception) { "" }
                    if (w.isNotBlank()) append(w)
                    else append(if (en) "Weather is unavailable. " else "Chưa lấy được thời tiết. ")
                }
                if (wantCal) {
                    val ev = eventsOn(app, 0)
                    append(
                        if (en) {
                            if (ev.isNotBlank()) "Today's events: $ev. " else "No calendar events today. "
                        } else {
                            if (ev.isNotBlank()) "Lịch hôm nay: $ev. " else "Hôm nay không có sự kiện trên lịch. "
                        }
                    )
                }
                if (wantTomorrow) {
                    val ev = eventsOn(app, 1)
                    append(
                        if (en) {
                            if (ev.isNotBlank()) "Tomorrow you have: $ev. " else "No calendar events tomorrow. "
                        } else {
                            if (ev.isNotBlank()) "Ngày mai có sự kiện: $ev. " else "Ngày mai không có sự kiện trên lịch. "
                        }
                    )
                }
                if (wantTasks) {
                    val tasks = AppSettings.getRoutineTasksText(app).trim()
                    val note = alarm?.voiceNote?.trim().orEmpty()
                    when {
                        tasks.isNotBlank() -> append(if (en) "Tasks: $tasks. " else "Việc cần làm: $tasks. ")
                        note.isNotBlank() -> append(note).append(" ")
                        else -> append(if (en) "No tasks set. " else "Chưa đặt việc cần làm. ")
                    }
                }
                if (label.isNotBlank() && !label.equals("Báo thức", true) && !label.equals("Alarm", true)) {
                    append(if (en) "It is time for $label. " else "Đến giờ $label rồi. ")
                }
                append(if (en) "Have a good day. " else "Cố lên, một ngày mới bắt đầu. ")
                append(nextAlarmText(app, en))
            }
            Handler(Looper.getMainLooper()).post {
                GeminiSpeakService.start(app, text)
            }
        }
    }

    private fun eventsOn(context: Context, daysAhead: Int): String {
        return try {
            val start = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis
            val end = start + 24L * 60 * 60 * 1000
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
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
        } catch (_: Exception) { "" }
    }

    private fun nextAlarmText(context: Context, en: Boolean = false): String {
        val enabled = AlarmRepository(context).getAlarms().filter { it.isEnabled }
        if (enabled.isEmpty()) return if (en) "No more alarms are on." else "Không còn báo thức nào đang bật."
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
        return if (en) "Next alarm ${a.label} at ${"%02d:%02d".format(a.hour, a.minute)}." else "Báo thức tiếp theo ${a.label} lúc ${"%02d:%02d".format(a.hour, a.minute)}."
    }
}
