package com.example.alarmclock

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GmailBot {
    private const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzNdnUa4ARQKbwjhqgpu1Df-wnir8ya4LMutZbHt6O1Ac6gZdifImuNkQhBojxpeIDr/exec"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun sendOtp(toEmail: String, code: String): Boolean {
        return try {
            val json = JSONObject().apply {
                put("to", toEmail)
                put("code", code)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(SCRIPT_URL).post(body).build()
            client.newCall(req).execute().use { resp ->
                val ok = resp.isSuccessful
                Log.i("GmailBot", "send $toEmail -> ${resp.code}")
                ok
            }
        } catch (e: Exception) {
            Log.e("GmailBot", "send fail", e)
            false
        }
    }
}
