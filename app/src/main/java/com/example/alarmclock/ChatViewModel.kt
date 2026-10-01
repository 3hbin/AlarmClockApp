package com.example.alarmclock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Một lượt chat: Gemini + tool set_alarm → lưu báo thức bằng setAlarmClock.
 * Notification hệ thống chỉ khi app đang background.
 */
class ChatViewModel(private val app: Context) {

    data class Turn(
        val answer: String,
        val createdNote: String?,
        val times: List<String>,
        val error: ChatApiError? = null
    )

    @Volatile
    var lastFailedPrompt: String? = null
        private set

    fun runTurn(
        apiKey: String,
        question: String,
        historyJson: String,
        cancelled: () -> Boolean
    ): Turn {
        if (cancelled()) return Turn("Đã dừng trả lời.", null, emptyList())

        val api = askGemini(apiKey, question, historyJson, cancelled)
        val gemini = api.out
        val merged = LinkedHashMap<String, GeminiTools.AlarmIntent>()
        (gemini.intents + localIntents(question)).forEach { i ->
            if (i.hour in 0..23 && i.minute in 0..59) {
                merged["%02d:%02d".format(i.hour, i.minute)] = i
            }
        }

        val created = applyAlarms(merged.values.toList())
        if (created.times.isNotEmpty() && AppVisibility.isBackground()) {
            created.times.forEach { hhmm ->
                val p = hhmm.split(":")
                AlarmNotificationHelper.notifyChatAlarmSetIfBackground(
                    app, p[0].toInt(), p[1].toInt(), created.label
                )
            }
        }

        if (api.error != null && created.note == null) {
            lastFailedPrompt = question
            return Turn(
                answer = api.error.errorMessage,
                createdNote = null,
                times = emptyList(),
                error = api.error
            )
        }
        lastFailedPrompt = null

        val answer = when {
            cancelled() && gemini.text.isBlank() -> created.note ?: "Đã dừng trả lời."
            created.note == null && gemini.text.isBlank() && api.error != null ->
                "${api.error.errorMessage}\n\n${created.note ?: ""}".trim()
            created.note == null -> gemini.text.ifBlank { "Gemini đang bận. Đợi một lát rồi gửi lại." }
            gemini.text.isBlank() -> created.note
            else -> "${created.note}\n\n${gemini.text}"
        }
        return Turn(answer, created.note, created.times, error = null)
    }

    private fun localIntents(raw: String): List<GeminiTools.AlarmIntent> {
        if (!AlarmTimeParser.looksLikeSetAlarm(raw)) return emptyList()
        val daily = raw.contains("mỗi ngày") || raw.contains("hàng ngày") || raw.contains("hang ngay")
        val label = labelFrom(raw)
        return AlarmTimeParser.parseAll(raw).map {
            GeminiTools.AlarmIntent(it.hour, it.minute, label, daily)
        }
    }

    private data class Created(val note: String?, val times: List<String>, val label: String)

    private fun applyAlarms(intents: List<GeminiTools.AlarmIntent>): Created {
        if (intents.isEmpty()) return Created(null, emptyList(), "Báo thức")
        val repo = AlarmRepository(app)
        val list = repo.getAlarms().toMutableList()
        val added = mutableListOf<String>()
        var lastLabel = "Báo thức"
        for (i in intents) {
            lastLabel = i.label
            val alarm = Alarm(
                id = repo.getNextId(),
                hour = i.hour,
                minute = i.minute,
                isEnabled = true,
                label = i.label,
                repeatMode = if (i.daily) Alarm.REPEAT_DAILY else Alarm.REPEAT_ONCE
            )
            list.add(alarm)
            AlarmScheduler.schedule(app, alarm)
            added.add("%02d:%02d".format(i.hour, i.minute))
        }
        repo.saveAlarms(list)
        val note = if (added.size == 1) {
            "Đã thêm báo thức mới ${added[0]} vào tab Báo thức."
        } else {
            "Đã thêm ${added.size} báo thức mới: ${added.joinToString(", ")} vào tab Báo thức."
        }
        return Created(note, added, lastLabel)
    }

    private data class AskOut(
        val out: GeminiTools.ModelOut,
        val error: ChatApiError? = null
    )

    private fun askGemini(
        key: String,
        question: String,
        historyJson: String,
        cancelled: () -> Boolean
    ): AskOut {
        val contents = JSONArray()
        val old = try { JSONArray(historyJson) } catch (_: Exception) { JSONArray() }
        val start = (old.length() - 8).coerceAtLeast(0)
        for (i in start until old.length()) {
            val item = old.getJSONObject(i)
            contents.put(
                JSONObject()
                    .put("role", if (item.optInt("m") == 1) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", item.optString("t"))))
            )
        }
        contents.put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))
        )
        var lastError: ChatApiError? = null
        repeat(2) { attempt ->
            if (cancelled()) return AskOut(GeminiTools.ModelOut("", emptyList()))
            try {
                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", GeminiChatPolicy.SYSTEM))
                        )
                    )
                    .put("contents", contents)
                if (AlarmTimeParser.looksLikeSetAlarm(question)) {
                    body.put("tools", GeminiTools.declarations())
                }
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$key"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 12_000
                    readTimeout = 25_000
                }
                lastConn = conn
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (cancelled()) {
                    try { conn.disconnect() } catch (_: Exception) {}
                    return AskOut(GeminiTools.ModelOut("", emptyList()))
                }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText().orEmpty()
                if (code in 200..299) {
                    return AskOut(GeminiTools.parseResponse(raw))
                }
                lastError = ChatApiExceptionMapper.fromHttp(code, raw, question)
                if (lastError?.kind == ChatErrorKind.RATE_LIMIT && attempt == 0) {
                    try { Thread.sleep(1200) } catch (_: Exception) {}
                }
            } catch (t: Throwable) {
                if (cancelled()) return AskOut(GeminiTools.ModelOut("", emptyList()))
                lastError = ChatApiExceptionMapper.fromThrowable(t, question)
                if (lastError?.kind == ChatErrorKind.NETWORK && attempt == 0) {
                    try { Thread.sleep(800) } catch (_: Exception) {}
                }
            }
        }
        return AskOut(GeminiTools.ModelOut("", emptyList()), lastError)
    }

    fun disconnect() {
        try { lastConn?.disconnect() } catch (_: Exception) {}
        lastConn = null
    }

    companion object {
        private const val MODEL = "gemini-3.6-flash"
        @Volatile
        var lastConn: HttpURLConnection? = null
    }

    private fun labelFrom(raw: String): String {
        val m = Regex("(?i)(?:nhãn|tên|gọi là)\\s+([^,.!?]{2,24})").find(raw)
        val name = m?.groupValues?.get(1)?.trim().orEmpty()
        return if (name.isNotBlank()) name else "Báo thức AI"
    }
}
