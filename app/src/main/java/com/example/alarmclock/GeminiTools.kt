package com.example.alarmclock

import org.json.JSONArray
import org.json.JSONObject

object GeminiTools {

    data class AlarmIntent(
        val hour: Int,
        val minute: Int,
        val label: String,
        val daily: Boolean
    )

    data class ModelOut(
        val text: String,
        val intents: List<AlarmIntent>
    )

    fun declarations(): JSONArray {
        val setAlarm = JSONObject()
            .put("name", "set_alarm")
            .put("description", "Đặt báo thức trong app khi người dùng muốn được đánh thức hoặc hẹn giờ.")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject()
                            .put("hour", JSONObject().put("type", "INTEGER").put("description", "Giờ 0-23"))
                            .put("minute", JSONObject().put("type", "INTEGER").put("description", "Phút 0-59"))
                            .put("label", JSONObject().put("type", "STRING").put("description", "Nhãn ngắn"))
                            .put("repeat", JSONObject().put("type", "STRING").put("description", "once hoặc daily"))
                    )
                    .put("required", JSONArray().put("hour").put("minute"))
            )
        return JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(setAlarm)))
    }

    fun parseResponse(raw: String): ModelOut {
        val intents = mutableListOf<AlarmIntent>()
        val text = StringBuilder()
        try {
            val parts = JSONObject(raw)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) text.append(part.optString("text"))
                val call = part.optJSONObject("functionCall") ?: continue
                if (call.optString("name") != "set_alarm") continue
                val args = when {
                    call.optJSONObject("args") != null -> call.getJSONObject("args")
                    else -> try { JSONObject(call.optString("args", "{}")) } catch (_: Exception) { JSONObject() }
                }
                val h = args.optInt("hour", -1)
                val m = args.optInt("minute", -1)
                if (h !in 0..23 || m !in 0..59) continue
                val label = args.optString("label").ifBlank { "Báo thức AI" }
                val daily = args.optString("repeat").equals("daily", true)
                intents.add(AlarmIntent(h, m, label, daily))
            }
        } catch (_: Exception) {}
        return ModelOut(text.toString().trim(), intents)
    }
}
