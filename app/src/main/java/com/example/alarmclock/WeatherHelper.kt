package com.example.alarmclock

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WeatherHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchWeatherAt(lat: Double, lon: Double, placeLabel: String): String {
        return try {
            val key = ApiConfig.weatherApiKey
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$key&units=metric&lang=vi"
            speakFromUrl(url, placeLabel.ifBlank { "vị trí hiện tại" })
        } catch (_: Exception) {
            fetchWeatherSummary(placeLabel.ifBlank { "Hanoi" })
        }
    }

    fun fetchWeatherSummary(city: String = "Hanoi"): String {
        return try {
            val key = ApiConfig.weatherApiKey
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$key&units=metric&lang=vi"
            speakFromUrl(url, city)
        } catch (e: Exception) {
            Log.e("WeatherHelper", "Error", e)
            "Không thể lấy thông tin thời tiết."
        }
    }

    private fun speakFromUrl(url: String, where: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "Không lấy được thời tiết tại $where."
            val body = response.body?.string() ?: return "Không có dữ liệu thời tiết."
            return describe(JSONObject(body), where)
        }
    }

    fun describe(json: JSONObject, where: String): String {
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val id = weather.optInt("id", 800)
        val desc = weather.optString("description", "")
        val main = json.getJSONObject("main")
        val temp = main.getDouble("temp").toInt()
        val feels = main.optDouble("feels_like", temp.toDouble()).toInt()
        val wind = json.optJSONObject("wind")?.optDouble("speed", 0.0) ?: 0.0
        val rain1h = json.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0
        val clouds = json.optJSONObject("clouds")?.optInt("all", 0) ?: 0

        val kind = when {
            id in 200..232 -> "Có dông bão và sấm sét."
            id in 502..504 || rain1h >= 7.5 -> "Trời mưa to."
            id == 501 || (rain1h >= 2.5 && rain1h < 7.5) -> "Trời mưa vừa."
            id in 300..321 || id == 500 || (rain1h > 0 && rain1h < 2.5) -> "Trời mưa nhỏ."
            id in 520..531 -> "Có mưa rào."
            id in 600..622 -> "Có tuyết."
            id == 800 -> "Trời nắng, quang đãng."
            id == 801 || id == 802 -> "Trời nắng nhẹ, ít mây."
            id == 803 || id == 804 -> "Trời nhiều mây."
            id in 701..781 -> "Trời mù hoặc sương."
            else -> if (desc.isNotBlank()) "Hiện $desc." else "Thời tiết bình thường."
        }
        val stormExtra = if (id in 200..232 || wind >= 17) " Có gió mạnh, nên hạn chế ra ngoài." else ""
        val rainQ = when {
            id in 200..232 -> " Có mưa bão."
            rain1h >= 7.5 || id in 502..504 -> " Mưa lớn."
            rain1h > 0 || id in 300..531 -> " Có mưa."
            else -> " Không mưa."
        }
        val sunQ = if (id == 800 || id == 801) " Có nắng." else " Không nắng rõ."
        return "Thời tiết tại $where: $kind$stormExtra$rainQ$sunQ Nhiệt độ $temp độ C, cảm giác như $feels độ. Mây $clouds phần trăm. "
    }
}
