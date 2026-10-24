package com.example.alarmclock

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Danh sách nhạc người dùng thêm từ máy (content:// URI). */
object CustomRingtones {
    data class Item(val uri: String, val name: String)

    private const val PREF = "custom_ringtones"
    private const val KEY = "items"

    fun list(ctx: Context): MutableList<Item> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Item(o.getString("uri"), o.optString("name", "Nhạc của bạn")))
        }
        return out
    }

    fun add(ctx: Context, uri: Uri, name: String) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        val items = list(ctx)
        items.removeAll { it.uri == uri.toString() }
        items.add(0, Item(uri.toString(), name.ifBlank { "Nhạc của bạn" }))
        save(ctx, items)
    }

    fun remove(ctx: Context, uri: String) {
        val items = list(ctx).filterNot { it.uri == uri }
        save(ctx, items)
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name))
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
