package com.example.alarmclock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lịch sử tắt / báo lại báo thức — xem trong Cài đặt hoặc bấm dòng "Báo thức tiếp theo". */
object AlarmHistory {
    private const val PREF = "alarm_history"
    private const val KEY = "events"
    private const val MAX = 50

    data class Event(
        val timeMs: Long,
        val alarmLabel: String,
        val hour: Int,
        val minute: Int,
        val action: String // "dismiss" | "snooze"
    )

    fun add(context: Context, label: String, hour: Int, minute: Int, action: String) {
        val list = load(context).toMutableList()
        list.add(0, Event(System.currentTimeMillis(), label, hour, minute, action))
        while (list.size > MAX) list.removeAt(list.lastIndex)
        save(context, list)
    }

    fun load(context: Context): List<Event> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Event(
                    o.optLong("t"),
                    o.optString("l"),
                    o.optInt("h"),
                    o.optInt("m"),
                    o.optString("a", "dismiss")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply()
    }

    fun formatLines(context: Context): List<String> {
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        return load(context).map { e ->
            val act = if (e.action == "snooze") "Báo lại" else "Tắt"
            "${fmt.format(Date(e.timeMs))} · ${"%02d:%02d".format(e.hour, e.minute)} · $act · ${e.alarmLabel.ifBlank { "Báo thức" }}"
        }
    }

    private fun save(context: Context, list: List<Event>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("t", e.timeMs)
                put("l", e.alarmLabel)
                put("h", e.hour)
                put("m", e.minute)
                put("a", e.action)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
