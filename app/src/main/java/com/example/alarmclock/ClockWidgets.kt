package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale

/** Phần thông tin dùng chung của 2 widget đồng hồ: thứ, ngày tháng năm và báo thức sắp tới. */
object ClockWidgetInfo {
    private val VI_DAYS = arrayOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
    private val EN_DAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val EN_MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun bind(context: Context, views: RemoteViews) {
        val en = try { Lang.isEn(context) } catch (_: Exception) { false }
        val now = Calendar.getInstance()
        val dow = now.get(Calendar.DAY_OF_WEEK) - 1
        val d = now.get(Calendar.DAY_OF_MONTH)
        val m = now.get(Calendar.MONTH)
        val y = now.get(Calendar.YEAR)
        views.setTextViewText(R.id.tvClkWeekday, if (en) EN_DAYS[dow] else VI_DAYS[dow])
        views.setTextViewText(
            R.id.tvClkDate,
            if (en) "${EN_MONTHS[m]} $d, $y" else "$d tháng ${m + 1}, $y"
        )

        val paused = try { AppSettings.isPauseAlarmsAtHome(context) } catch (_: Exception) { false }
        val next = try { WidgetUpdateHelper.nearestAlarm(context) } catch (_: Exception) { null }
        val text: String
        if (paused) {
            text = if (en) "⏸ Paused at home — alarms won't ring" else "⏸ Đang tạm dừng — báo thức không kêu"
            views.setInt(R.id.tvClkAlarm, "setBackgroundResource", R.drawable.bg_widget_chip_yellow)
            views.setTextColor(R.id.tvClkAlarm, 0xFF5C3B00.toInt())
        } else {
            views.setInt(R.id.tvClkAlarm, "setBackgroundResource", R.drawable.bg_widget_chip)
            views.setTextColor(R.id.tvClkAlarm, 0xFFE8EEFF.toInt())
            text = if (next == null) {
                if (en) "⏰ No alarm set" else "⏰ Chưa có báo thức"
            } else {
                val (alarm, at) = next
                val label = alarm.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                val left = WidgetUpdateHelper.formatCountdown(at)
                if (en) "⏰ ${WidgetUpdateHelper.formatHm(alarm)}$label · in $left"
                else "⏰ ${WidgetUpdateHelper.formatHm(alarm)}$label · còn $left"
            }
        }
        views.setTextViewText(R.id.tvClkAlarm, text)
        views.setTextViewText(R.id.tvClkWeather, weatherLineFor(context, en))

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.clkRoot,
            PendingIntent.getActivity(
                context, 61, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}

class AlarmWidgetClockAnalogProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetWeather.refreshIfStale(context) { ClockWidgetInfo.updateAll(context) }
        ids.forEach { id -> mgr.updateAppWidget(id, build(context)) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try { WidgetUpdateHelper.refreshAll(context) } catch (_: Exception) {}
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_clock_analog)
            ClockWidgetInfo.bind(context, v)
            return v
        }
    }
}

class AlarmWidgetClockDigitalProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetWeather.refreshIfStale(context) { ClockWidgetInfo.updateAll(context) }
        ids.forEach { id -> mgr.updateAppWidget(id, build(context)) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try { WidgetUpdateHelper.refreshAll(context) } catch (_: Exception) {}
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_clock_digital)
            ClockWidgetInfo.bind(context, v)
            return v
        }
    }
}

/** Dòng thời tiết cho widget: lấy từ cache của màn Thời tiết (dữ liệu thật, không tạo giả). */
private fun weatherLineFor(context: Context, en: Boolean): String {
    val b = try { WeatherService.load(context) } catch (_: Exception) { null }
        ?: return if (en) "Weather: no data yet" else "Thời tiết: chưa có dữ liệu"
    val n = b.now
    val icon = WeatherService.iconFor(n.id)
    val age = (System.currentTimeMillis() - n.updated) / 60_000L
    val stale = if (age > 60) (if (en) " · old" else " · cũ") else ""
    val place = if (n.fallback) (if (en) "Hanoi" else "Hà Nội") else n.city
    return "$icon ${n.temp.toInt()}°C · $place$stale"
}

/** Cập nhật mọi widget đồng hồ (kim và số). */
fun ClockWidgetInfo.updateAll(context: Context) {
    try {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.getAppWidgetIds(android.content.ComponentName(context, AlarmWidgetClockAnalogProvider::class.java))
            .forEach { mgr.updateAppWidget(it, AlarmWidgetClockAnalogProvider.build(context)) }
        mgr.getAppWidgetIds(android.content.ComponentName(context, AlarmWidgetClockDigitalProvider::class.java))
            .forEach { mgr.updateAppWidget(it, AlarmWidgetClockDigitalProvider.build(context)) }
    } catch (_: Exception) {}
}

/** Tự làm mới thời tiết khi cache cũ hơn 30 phút; luôn gọi onDone trên luồng chính. */
object WidgetWeather {
    private const val STALE_MS = 30 * 60_000L

    fun refreshIfStale(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        try {
            WeatherService.init(app)
            val cached = WeatherService.load(app)
            val fresh = cached != null && System.currentTimeMillis() - cached.now.updated < STALE_MS
            val key = ApiConfig.weatherApiKey
            if (fresh || key.isBlank()) { main.post(onDone); return }
            Thread {
                try {
                    val place = LocationPlaceHelper.lastKnown(app)
                    val lat = place?.latitude ?: 21.0278
                    val lon = place?.longitude ?: 105.8342
                    val fallback = place == null
                    val name = if (fallback) "Hà Nội" else "Vị trí hiện tại"
                    WeatherService.fetch(lat, lon, name, fallback, key)
                } catch (_: Exception) {
                    // Giữ cache cũ nếu lỗi mạng
                } finally {
                    main.post(onDone)
                }
            }.start()
        } catch (_: Throwable) {
            main.post(onDone)
        }
    }
}
