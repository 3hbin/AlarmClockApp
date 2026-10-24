package com.example.alarmclock

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(val tag: String, val newer: Boolean)

object UpdateCheckHelper {
    private const val URL = "https://api.github.com/repos/3hbin/AlarmClockApp/releases/latest"
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun check(currentName: String, onResult: (AppUpdateInfo?) -> Unit) {
        Thread {
            val info = try {
                val req = Request.Builder().url(URL).header("Accept", "application/vnd.github+json").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val tag = JSONObject(resp.body?.string().orEmpty()).optString("tag_name").ifBlank { return@use null }
                    AppUpdateInfo(tag, isNewer(tag, currentName))
                }
            } catch (_: Exception) { null }
            Handler(Looper.getMainLooper()).post { onResult(info) }
        }.start()
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
