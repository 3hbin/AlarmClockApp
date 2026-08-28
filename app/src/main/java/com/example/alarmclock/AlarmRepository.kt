package com.example.alarmclock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository(context: Context) {
    private val prefs = context.getSharedPreferences("alarms_prefs", Context.MODE_PRIVATE)

    fun getAlarms(): MutableList<Alarm> {
        val json = prefs.getString("alarms", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<Alarm>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Alarm(
                    id = obj.getInt("id"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    isEnabled = obj.getBoolean("isEnabled"),
                    label = obj.optString("label", "Báo thức"),
                    repeatMode = obj.optInt("repeatMode", Alarm.REPEAT_DAILY),
                    snoozeMinutes = obj.optInt("snoozeMinutes", 5),
                    ringtoneUri = if (obj.has("ringtoneUri") && !obj.isNull("ringtoneUri")) obj.getString("ringtoneUri") else null,
                    challengeType = obj.optInt("challengeType", Alarm.CHALLENGE_NONE),
                    shakeTargetCount = obj.optInt("shakeTargetCount", 10),
                    skipHolidays = obj.optBoolean("skipHolidays", false),
                    isStrictAntiSnooze = obj.optBoolean("isStrictAntiSnooze", false),
                    voiceNote = if (obj.has("voiceNote") && !obj.isNull("voiceNote")) obj.getString("voiceNote") else null,
                    useCrescendo = obj.optBoolean("useCrescendo", true)
                )
            )
        }
        return list
    }

    fun saveAlarms(alarms: List<Alarm>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("isEnabled", alarm.isEnabled)
                put("label", alarm.label)
                put("repeatMode", alarm.repeatMode)
                put("snoozeMinutes", alarm.snoozeMinutes)
                put("ringtoneUri", alarm.ringtoneUri)
                put("challengeType", alarm.challengeType)
                put("shakeTargetCount", alarm.shakeTargetCount)
                put("skipHolidays", alarm.skipHolidays)
                put("isStrictAntiSnooze", alarm.isStrictAntiSnooze)
                put("voiceNote", alarm.voiceNote)
                put("useCrescendo", alarm.useCrescendo)
            }
            array.put(obj)
        }
        prefs.edit().putString("alarms", array.toString()).apply()
    }

    fun getNextId(): Int {
        return prefs.getInt("next_id", 1).also {
            prefs.edit().putInt("next_id", it + 1).apply()
        }
    }

    fun getGlobalRingtone(): String? = prefs.getString("global_ringtone", null)
    fun setGlobalRingtone(uri: String?) = prefs.edit().putString("global_ringtone", uri).apply()

    fun isFlashEnabled(): Boolean = prefs.getBoolean("flash_enabled", false)
    fun setFlashEnabled(enabled: Boolean) = prefs.edit().putBoolean("flash_enabled", enabled).apply()

    // Sleep Habits Tracker (simple)
    fun logSleep(startMs: Long, endMs: Long) {
        val key = "sleep_log"
        val arr = JSONArray(prefs.getString(key, "[]"))
        arr.put(JSONObject().apply {
            put("start", startMs)
            put("end", endMs)
            put("durationMin", (endMs - startMs) / 60000)
        })
        // Keep last 30 entries
        while (arr.length() > 30) arr.remove(0)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun getSleepLogs(): List<Triple<Long, Long, Long>> {
        val arr = JSONArray(prefs.getString("sleep_log", "[]"))
        val list = mutableListOf<Triple<Long, Long, Long>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(Triple(o.getLong("start"), o.getLong("end"), o.getLong("durationMin")))
        }
        return list
    }
}
