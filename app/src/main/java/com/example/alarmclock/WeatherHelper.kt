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

    fun fetchWeatherAt(lat: Double, lon: Double, placeLabel: String, english: Boolean = false): String {
        return try {
            val key = ApiConfig.weatherApiKey
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$key&units=metric&lang=vi"
            speakFromUrl(url, placeLabel.ifBlank { if (english) "your location" else "vị trí hiện tại" }, english)
        } catch (_: Exception) {
            fetchWeatherSummary(placeLabel.ifBlank { "Hanoi" })
        }
    }

    fun fetchWeatherSummary(city: String = "Hanoi", english: Boolean = false): String {
        return try {
            val key = ApiConfig.weatherApiKey
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$key&units=metric&lang=${if (english) "en" else "vi"}"
            speakFromUrl(url, city, english)
        } catch (e: Exception) {
            Log.e("WeatherHelper", "Error", e)
            "Không thể lấy thông tin thời tiết."
        }
    }

    private fun speakFromUrl(url: String, where: String, english: Boolean = false): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "Không lấy được thời tiết tại $where."
            val body = response.body?.string() ?: return "Không có dữ liệu thời tiết."
            return describe(JSONObject(body), where, english)
        }
    }

    fun describe(json: JSONObject, where: String, english: Boolean = false): String {
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
            id in 200..232 -> if (english) "Thunderstorm with lightning." else "Có dông bão và sấm sét."
            id in 502..504 || rain1h >= 7.5 -> if (english) "Heavy rain." else "Trời mưa to."
            id == 501 || (rain1h >= 2.5 && rain1h < 7.5) -> if (english) "Moderate rain." else "Trời mưa vừa."
            id in 300..321 || id == 500 || (rain1h > 0 && rain1h < 2.5) -> if (english) "Light rain." else "Trời mưa nhỏ."
            id in 520..531 -> if (english) "Rain showers." else "Có mưa rào."
            id in 600..622 -> if (english) "Snow." else "Có tuyết."
            id == 800 -> if (english) "Sunny and clear." else "Trời nắng, quang đãng."
            id == 801 || id == 802 -> if (english) "Mostly sunny." else "Trời nắng nhẹ, ít mây."
            id == 803 || id == 804 -> if (english) "Cloudy." else "Trời nhiều mây."
            id in 701..781 -> if (english) "Fog or mist." else "Trời mù hoặc sương."
            else -> if (desc.isNotBlank()) (if (english) "Currently $desc." else "Hiện $desc.") else (if (english) "Normal weather." else "Thời tiết bình thường.")
        }
        val stormExtra = if (id in 200..232 || wind >= 17)
            if (english) " Strong wind. Stay inside if you can." else " Có gió mạnh, nên hạn chế ra ngoài."
        else ""
        val rainQ = when {
            id in 200..232 -> if (english) " Storm rain." else " Có mưa bão."
            rain1h >= 7.5 || id in 502..504 -> if (english) " Heavy rain." else " Mưa lớn."
            rain1h > 0 || id in 300..531 -> if (english) " Rain." else " Có mưa."
            else -> if (english) " No rain." else " Không mưa."
        }
        val sunQ = if (id == 800 || id == 801)
            if (english) " Sunny." else " Có nắng."
        else if (english) " Not sunny." else " Không nắng rõ."
        return if (english)
            "Weather in $where: $kind$stormExtra$rainQ$sunQ Temperature $temp C, feels like $feels. Clouds $clouds percent. "
        else
            "Thời tiết tại $where: $kind$stormExtra$rainQ$sunQ Nhiệt độ $temp độ C, cảm giác như $feels độ. Mây $clouds phần trăm. "
    }
}
