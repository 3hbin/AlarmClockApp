package com.example.alarmclock

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenWeatherMap (hoặc API tương thích).
 * Key: BuildConfig.WEATHER_API_KEY
 */
object WeatherHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Lấy mô tả thời tiết ngắn cho TTS.
     * @param city ví dụ "Hanoi" hoặc "Ho Chi Minh"
     */
    fun fetchWeatherSummary(city: String = "Hanoi"): String {
        return try {
            val key = ApiConfig.weatherApiKey
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$key&units=metric&lang=vi"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return "Không lấy được thời tiết (mã ${response.code})"
                }
                val body = response.body?.string() ?: return "Không có dữ liệu thời tiết"
                val json = JSONObject(body)
                val weather = json.getJSONArray("weather").getJSONObject(0)
                val main = json.getJSONObject("main")
                val desc = weather.getString("description")
                val temp = main.getDouble("temp").toInt()
                val feels = main.optDouble("feels_like", temp.toDouble()).toInt()
                "Hôm nay tại $city: $desc, nhiệt độ $temp độ C, cảm giác như $feels độ."
            }
        } catch (e: Exception) {
            Log.e("WeatherHelper", "Error", e)
            "Không thể lấy thông tin thời tiết."
        }
    }
}
