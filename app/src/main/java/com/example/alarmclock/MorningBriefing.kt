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

        thread {
            val now = Calendar.getInstance()
            val timeStr = String.format(Locale.getDefault(), "%02d giờ %02d phút", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            val day = SimpleDateFormat("EEEE, 'ngày' dd 'tháng' MM", Locale("vi", "VN")).format(Date())
            val place = try { LocationPlaceHelper.resolve(app) } catch (_: Exception) { null }
            val label = alarm?.label?.trim().orEmpty()

            val text = buildString {
                // 1 chào ngắn
                append("Xin chào. Đã đến giờ dậy. ")
                // 2 giờ + ngày
                append("Bây giờ $timeStr, $day. ")
                // vị trí xã / quận / thành phố
                if (place != null) append(place.speakLine())
                else append("Chưa có vị trí. Hãy mở app và cấp quyền vị trí. ")
                // 3 thời tiết đúng chỗ
                if (wantWeather) {
                    val w = try {
                        if (place != null) WeatherHelper.fetchWeatherAt(place.lat, place.lon, place.shortCity())
                        else WeatherHelper.fetchWeatherSummary("Hanoi")
                    } catch (_: Exception) { "" }
                    if (w.isNotBlank()) append(w)
                    else append("Chưa lấy được thời tiết. ")
                }
                // 4 lịch
                if (wantCal) {
                    val ev = todayEvents(app)
                    append(if (ev.isNotBlank()) "Lịch hôm nay: $ev. " else "Hôm nay không có sự kiện lịch. ")
                }
                // 5 việc cần làm
                if (wantTasks) {
                    val tasks = AppSettings.getRoutineTasksText(app).trim()
                    val note = alarm?.voiceNote?.trim().orEmpty()
                    when {
                        tasks.isNotBlank() -> append("Việc cần làm: $tasks. ")
                        note.isNotBlank() -> append(note).append(" ")
                        else -> append("Chưa đặt việc cần làm. ")
                    }
                }
                // 7 nhắc theo tên chuông
                if (label.isNotBlank() && !label.equals("Báo thức", true)) {
                    append("Đến giờ $label rồi. ")
                }
                // 8 động viên
                append("Cố lên, một ngày mới bắt đầu. ")
                // 10 gói đầy đủ: thêm chuông kế
                append(nextAlarmText(app))
            }
            Handler(Looper.getMainLooper()).post {
                GeminiSpeakService.start(app, text)
            }
        }
    }

    private fun todayEvents(context: Context): String {
        return try {
            val start = Calendar.getInstance().apply {
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
