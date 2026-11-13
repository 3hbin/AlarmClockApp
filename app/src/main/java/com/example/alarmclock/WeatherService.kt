package com.example.alarmclock

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Dữ liệu thời tiết: hiện tại + dự báo theo giờ + theo ngày. Đơn vị °C, km/h. */
object WeatherService {
    private const val PREFS = "weather_screen"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Now(
        val city: String, val temp: Double, val feels: Double, val humidity: Int,
        val pressure: Int, val windKmh: Double, val visibilityKm: Double, val clouds: Int,
        val id: Int, val sunrise: Long, val sunset: Long, val updated: Long, val fallback: Boolean
    )
    data class Slot(val time: Long, val temp: Double, val id: Int, val pop: Int)
    data class Day(val time: Long, val min: Double, val max: Double, val id: Int, val pop: Int)
    data class Bundle(val now: Now, val hours: List<Slot>, val days: List<Day>)

    fun fetch(lat: Double, lon: Double, city: String, fallback: Boolean, key: String): Bundle {
        val cur = get("weather", lat, lon, key)
        val fc = get("forecast", lat, lon, key)
        val bundle = parse(cur, fc, city, fallback, System.currentTimeMillis())
        save(cur, fc, city, fallback, bundle.now.updated)
        return bundle
    }

    private fun get(endpoint: String, lat: Double, lon: Double, key: String): String {
        val url = "https://api.openweathermap.org/data/2.5/$endpoint?lat=$lat&lon=$lon&appid=$key&units=metric&lang=vi"
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
            return r.body?.string() ?: throw IllegalStateException("empty")
        }
    }

    fun parse(curJson: String, fcJson: String, city: String, fallback: Boolean, updated: Long): Bundle {
        val c = JSONObject(curJson)
        val m = c.getJSONObject("main")
        val sys = c.optJSONObject("sys")
        val now = Now(
            city = city,
            temp = m.getDouble("temp"),
            feels = m.optDouble("feels_like", m.getDouble("temp")),
            humidity = m.optInt("humidity", 0),
            pressure = m.optInt("pressure", 0),
            windKmh = (c.optJSONObject("wind")?.optDouble("speed", 0.0) ?: 0.0) * 3.6,
            visibilityKm = c.optDouble("visibility", 0.0) / 1000.0,
            clouds = c.optJSONObject("clouds")?.optInt("all", 0) ?: 0,
            id = c.getJSONArray("weather").getJSONObject(0).optInt("id", 800),
            sunrise = (sys?.optLong("sunrise", 0L) ?: 0L) * 1000L,
            sunset = (sys?.optLong("sunset", 0L) ?: 0L) * 1000L,
            updated = updated,
            fallback = fallback
        )
        val list: JSONArray = JSONObject(fcJson).getJSONArray("list")
        val slots = ArrayList<Slot>()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            slots.add(Slot(
                time = o.getLong("dt") * 1000L,
                temp = o.getJSONObject("main").getDouble("temp"),
                id = o.getJSONArray("weather").getJSONObject(0).optInt("id", 800),
                pop = (o.optDouble("pop", 0.0) * 100).toInt()
            ))
        }
        val hours = slots.take(8)
        // Gom theo ngày (theo giờ máy)
        val groups = LinkedHashMap<Int, MutableList<Slot>>()
        val cal = Calendar.getInstance()
        for (s in slots) {
            cal.timeInMillis = s.time
            val key = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            groups.getOrPut(key) { ArrayList() }.add(s)
        }
        val days = groups.values.take(5).map { g ->
            val noon = g.minByOrNull { s ->
                cal.timeInMillis = s.time
                kotlin.math.abs(cal.get(Calendar.HOUR_OF_DAY) - 12)
            } ?: g.first()
            Day(
                time = g.first().time,
                min = g.minOf { it.temp },
                max = g.maxOf { it.temp },
                id = noon.id,
                pop = g.maxOf { it.pop }
            )
        }
        return Bundle(now, hours, days)
    }

    fun save(cur: String, fc: String, city: String, fallback: Boolean, updated: Long) {
        // Lưu tạm qua một chuỗi JSON gói cả hai phản hồi
        val o = JSONObject().put("cur", cur).put("fc", fc).put("city", city)
            .put("fallback", fallback).put("updated", updated)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("cache", o.toString())?.apply()
    }

    fun load(context: Context): Bundle? = try {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cache", null)
        if (s == null) null else {
            val o = JSONObject(s)
            parse(o.getString("cur"), o.getString("fc"), o.getString("city"), o.optBoolean("fallback"), o.getLong("updated"))
        }
    } catch (_: Exception) { null }

    @Volatile private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    fun iconFor(id: Int): String = when {
        id in 200..232 -> "⛈️"
        id in 300..321 || id in 500..531 -> "🌧️"
        id in 600..622 -> "❄️"
        id in 701..781 -> "🌫️"
        id == 800 -> "☀️"
        id == 801 || id == 802 -> "🌤️"
        id == 803 || id == 804 -> "☁️"
        else -> "🌡️"
    }

    fun descFor(context: Context, id: Int): String = when {
        id in 200..232 -> Lang.t(context, "Giông bão", "Thunderstorm")
        id in 502..504 -> Lang.t(context, "Mưa to", "Heavy rain")
        id in 300..531 -> Lang.t(context, "Mưa", "Rain")
        id in 600..622 -> Lang.t(context, "Tuyết", "Snow")
        id in 701..781 -> Lang.t(context, "Sương mù", "Fog")
        id == 800 -> Lang.t(context, "Trời nắng", "Sunny")
        id == 801 || id == 802 -> Lang.t(context, "Nắng nhẹ, ít mây", "Mostly sunny")
        id == 803 || id == 804 -> Lang.t(context, "Nhiều mây", "Cloudy")
        else -> Lang.t(context, "Thời tiết", "Weather")
    }

    /** Hai màu nền theo thời tiết (đổi mượt khi cập nhật). */
    fun gradientFor(id: Int): IntArray = when {
        id in 200..232 -> intArrayOf(0xFF141E30.toInt(), 0xFF243B55.toInt())
        id in 300..531 -> intArrayOf(0xFF2C3E50.toInt(), 0xFF4CA1AF.toInt())
        id in 600..622 -> intArrayOf(0xFF83A4D4.toInt(), 0xFFB6CFEF.toInt())
        id in 701..781 -> intArrayOf(0xFF606C88.toInt(), 0xFF3F4C6B.toInt())
        id == 800 -> intArrayOf(0xFFFF7A18.toInt(), 0xFFFFB347.toInt())
        id in 801..804 -> intArrayOf(0xFF4B6CB7.toInt(), 0xFF182848.toInt())
        else -> intArrayOf(0xFF3B5BDB.toInt(), 0xFF6C5CE7.toInt())
    }
}
