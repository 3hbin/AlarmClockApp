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
        adoptGuest(context)
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

    // ------------------------------------------------------------------ gộp / khôi phục chat
    private fun parseArr(s: String?): org.json.JSONArray =
        try { org.json.JSONArray(if (s.isNullOrBlank()) "[]" else s) } catch (_: Exception) { org.json.JSONArray() }

    /** Máy mới / vừa xóa dữ liệu: chưa có chat nào. */
    fun isFresh(context: Context): Boolean {
        val p = prefs(context)
        return parseArr(p.getString("history", "[]")).length() == 0 &&
            parseArr(p.getString("sessions", "[]")).length() == 0
    }

    fun deletedIds(context: Context): MutableSet<String> {
        val a = parseArr(prefs(context).getString("deleted_sessions", "[]"))
        val out = linkedSetOf<String>()
        for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        return out
    }

    fun deletedJson(context: Context): String = org.json.JSONArray(deletedIds(context).toList()).toString()

    /**
     * GỘP danh sách chat (không ghi đè): mỗi chat theo id, bản nào mới hơn thì giữ.
     * Chat đã xóa (có trong danh sách xóa) không bị sống lại.
     */
    fun mergeSessions(context: Context, cloudSessions: String?, cloudDeleted: String?) {
        val p = prefs(context)
        val dead = deletedIds(context)
        val cd = parseArr(cloudDeleted)
        for (i in 0 until cd.length()) cd.optString(i).takeIf { it.isNotBlank() }?.let { dead.add(it) }
        val byId = LinkedHashMap<String, org.json.JSONObject>()
        fun take(arr: org.json.JSONArray) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank() || id in dead) continue
                val old = byId[id]
                if (old == null) {
                    byId[id] = o
                } else {
                    val ot = old.optLong("ts")
                    val nt = o.optLong("ts")
                    val oc = old.optJSONArray("messages")?.length() ?: 0
                    val nc = o.optJSONArray("messages")?.length() ?: 0
                    if (nt > ot || (nt == ot && nc > oc)) byId[id] = o
                }
            }
        }
        take(parseArr(p.getString("sessions", "[]")))
        take(parseArr(cloudSessions))
        val keep = byId.values
            .filter { (it.optJSONArray("messages")?.length() ?: 0) > 0 }
            .sortedBy { it.optLong("ts") }
            .takeLast(40)
        val out = org.json.JSONArray()
        keep.forEach { out.put(it) }
        p.edit()
            .putString("sessions", out.toString())
            .putString("deleted_sessions", org.json.JSONArray(dead.toList()).toString())
            .apply()
    }

    /** Máy mới: mở lại chat gần nhất đã sao lưu. */
    fun restoreCurrentIfEmpty(context: Context) {
        val p = prefs(context)
        if (parseArr(p.getString("history", "[]")).length() > 0) return
        val all = parseArr(p.getString("sessions", "[]"))
        var best: org.json.JSONObject? = null
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if ((o.optJSONArray("messages")?.length() ?: 0) == 0) continue
            if (best == null || o.optLong("ts") > best.optLong("ts")) best = o
        }
        val b = best ?: return
        p.edit()
            .putString("session_id", b.optString("id"))
            .putString("history", (b.optJSONArray("messages") ?: org.json.JSONArray()).toString())
            .apply()
    }

    /** Xóa hẳn một cuộc chat (có đánh dấu để cloud không đưa lại). */
    fun deleteSession(context: Context, id: String) {
        val p = prefs(context)
        val dead = deletedIds(context)
        dead.add(id)
        val all = parseArr(p.getString("sessions", "[]"))
        val out = org.json.JSONArray()
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("id") != id) out.put(o)
        }
        val e = p.edit()
            .putString("sessions", out.toString())
            .putString("deleted_sessions", org.json.JSONArray(dead.toList()).toString())
        if (currentSessionId(context) == id) {
            e.putString("session_id", "s" + System.currentTimeMillis()).putString("history", "[]")
        }
        e.apply()
    }

    /** Danh sách chat đưa lên cloud, giữ dưới ~700KB để không vượt giới hạn tài liệu. */
    fun sessionsForCloud(context: Context): String {
        val all = sessions(context)
        val list = (0 until all.length()).mapNotNull { all.optJSONObject(it) }
            .sortedByDescending { it.optLong("ts") }
        val out = org.json.JSONArray()
        var size = 2
        for (o in list) {
            val len = o.toString().length
            if (size + len > 700_000) break
            out.put(o)
            size += len + 1
        }
        return out.toString()
    }

    /** Chat lúc chưa đăng nhập (guest) được nhập vào tài khoản khi đã đăng nhập. */
    private fun adoptGuest(context: Context) {
        try {
            if (accountKey(context) == "guest") return
            val p = prefs(context)
            if (p.getBoolean("guest_adopted", false)) return
            val g = context.getSharedPreferences("chat_ai_guest", Context.MODE_PRIVATE)
            val gs = g.getString("sessions", "[]").orEmpty()
            p.edit().putBoolean("guest_adopted", true).apply()
            if (gs.isNotBlank() && gs != "[]") mergeSessions(context, gs, null)
        } catch (_: Exception) {}
    }
}
