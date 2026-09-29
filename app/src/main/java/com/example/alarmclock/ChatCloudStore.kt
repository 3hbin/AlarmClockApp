package com.example.alarmclock

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn

/** Prefs chat gắn theo email Google để đổi máy / đổi tài khoản không lẫn lịch sử. */
object ChatCloudStore {
    fun accountKey(context: Context): String {
        val email = (GoogleSignIn.getLastSignedInAccount(context)?.email
            ?: AppSettings.getRecoveryEmail(context)).trim().lowercase()
        return if (email.isBlank()) "guest" else email.replace(Regex("[^a-z0-9]+"), "_")
    }

    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("chat_ai_" + accountKey(context), Context.MODE_PRIVATE)
    }

    fun geminiKey(context: Context): String =
        prefs(context).getString("key", "").orEmpty()

    fun historyJson(context: Context): String =
        prefs(context).getString("history", "[]").orEmpty().ifBlank { "[]" }

    fun saveKey(context: Context, key: String) {
        prefs(context).edit().putString("key", key).apply()
    }

    fun saveHistory(context: Context, json: String) {
        prefs(context).edit().putString("history", json).apply()
    }
}
