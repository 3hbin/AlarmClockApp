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

    fun currentSessionId(context: Context): String {
        val id = prefs(context).getString("session_id", "").orEmpty()
        if (id.isNotBlank()) return id
        val fresh = "s" + System.currentTimeMillis()
        prefs(context).edit().putString("session_id", fresh).apply()
        return fresh
    }

    fun sessions(context: Context): org.json.JSONArray {
        migrate(context)
        return try {
            org.json.JSONArray(prefs(context).getString("sessions", "[]"))
        } catch (_: Exception) {
            org.json.JSONArray()
        }
    }

    private fun migrate(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("sess_migrated", false)) return
        val hist = p.getString("history", "[]").orEmpty()
        val arr = try { org.json.JSONArray(hist) } catch (_: Exception) { org.json.JSONArray() }
        if (arr.length() > 0) {
            val id = currentSessionId(context)
            val one = org.json.JSONObject()
                .put("id", id)
                .put("title", titleOf(arr))
                .put("ts", System.currentTimeMillis())
                .put("messages", arr)
            p.edit().putString("sessions", org.json.JSONArray().put(one).toString())
                .putBoolean("sess_migrated", true).apply()
        } else {
            p.edit().putBoolean("sess_migrated", true).apply()
        }
    }

    private fun titleOf(arr: org.json.JSONArray): String {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optInt("m") == 1) {
                val t = o.optString("t").trim()
                if (t.isNotBlank()) return t.take(36)
            }
        }
        return "Chat " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
    }

    fun snapshotCurrent(context: Context, history: org.json.JSONArray) {
        val id = currentSessionId(context)
        val all = sessions(context)
        var found = false
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                o.put("messages", history)
                o.put("title", titleOf(history))
                o.put("ts", System.currentTimeMillis())
                found = true
                break
            }
        }
        if (!found && history.length() > 0) {
            all.put(
                org.json.JSONObject()
                    .put("id", id)
                    .put("title", titleOf(history))
                    .put("ts", System.currentTimeMillis())
                    .put("messages", history)
            )
        }
        prefs(context).edit()
            .putString("sessions", all.toString())
            .putString("history", history.toString())
            .apply()
    }

    fun startNewChat(context: Context, history: org.json.JSONArray) {
        snapshotCurrent(context, history)
        val id = "s" + System.currentTimeMillis()
        prefs(context).edit()
            .putString("session_id", id)
            .putString("history", "[]")
            .apply()
    }

    fun openSession(context: Context, id: String, current: org.json.JSONArray): org.json.JSONArray {
        snapshotCurrent(context, current)
        val all = sessions(context)
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("id") == id) {
                val msgs = o.optJSONArray("messages") ?: org.json.JSONArray()
                prefs(context).edit()
                    .putString("session_id", id)
                    .putString("history", msgs.toString())
                    .apply()
                return msgs
            }
        }
        return org.json.JSONArray()
    }

    fun pins(context: Context): org.json.JSONArray {
        return try { org.json.JSONArray(prefs(context).getString("pins", "[]")) } catch (_: Exception) { org.json.JSONArray() }
    }

    fun isPinned(context: Context, text: String): Boolean {
        val arr = pins(context)
        for (i in 0 until arr.length()) if (arr.optString(i) == text) return true
        return false
    }

    fun togglePin(context: Context, text: String): Boolean {
        val arr = pins(context)
        for (i in 0 until arr.length()) {
            if (arr.optString(i) == text) {
                arr.remove(i)
                prefs(context).edit().putString("pins", arr.toString()).apply()
                return false
            }
        }
        arr.put(text)
        prefs(context).edit().putString("pins", arr.toString()).apply()
        return true
    }
}
