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
        cancelled: () -> Boolean,
        imageJpeg: ByteArray? = null
    ): Turn {
        if (cancelled()) return Turn("Đã dừng trả lời.", null, emptyList())

        val api = askGemini(apiKey, question, historyJson, cancelled, imageJpeg)
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

        val geminiText = if (gemini.text.isNotBlank() && gemini.sources.isNotEmpty()) {
            gemini.text + "\n\n🌐 Nguồn: " + gemini.sources.joinToString(", ")
        } else gemini.text
        val answer = when {
            cancelled() && geminiText.isBlank() -> created.note ?: "Đã dừng trả lời."
            created.note == null && geminiText.isBlank() && api.error != null ->
                "${api.error.errorMessage}\n\n${created.note ?: ""}".trim()
            created.note == null -> geminiText.ifBlank { "Gemini đang bận. Đợi một lát rồi gửi lại." }
            geminiText.isBlank() -> created.note
            else -> "${created.note}\n\n$geminiText"
        }
        return Turn(answer, created.note, created.times, error = null)
    }

    /** Đặt báo thức từ câu nói, không gọi API. Dùng cho Gemini Live khi mạng bận. */
    fun applySpokenCommand(question: String): Turn {
        val created = applyAlarms(localIntents(question))
        return Turn(created.note ?: "", created.note, created.times)
    }

    private fun localIntents(raw: String): List<GeminiTools.AlarmIntent> {
        val daily = raw.contains("mỗi ngày") || raw.contains("hàng ngày") || raw.contains("hang ngay")
        val label = labelFrom(raw)
        val times = AlarmTimeParser.parseAll(raw)
        val bareTime = times.isNotEmpty() && raw.trim().length <= 12
        if (times.isNotEmpty() && (AlarmTimeParser.looksLikeSetAlarm(raw) || bareTime)) {
            return times.map { GeminiTools.AlarmIntent(it.hour, it.minute, label, daily) }
        }
        val offset = AlarmTimeParser.parseOffset(raw)
        if (offset != null && (AlarmTimeParser.looksLikeSetAlarm(raw) || raw.trim().length < 24)) {
            return listOf(GeminiTools.AlarmIntent(offset.hour, offset.minute, label, false))
        }
        return emptyList()
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
            "⏰ Đã thêm báo thức mới ${added[0]} vào tab Báo thức."
        } else {
            "⏰ Đã thêm ${added.size} báo thức mới: ${added.joinToString(", ")} vào tab Báo thức."
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
        cancelled: () -> Boolean,
        imageJpeg: ByteArray? = null
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
        val userParts = JSONArray().put(JSONObject().put("text", question))
        if (imageJpeg != null && imageJpeg.isNotEmpty()) {
            val b64 = android.util.Base64.encodeToString(imageJpeg, android.util.Base64.NO_WRAP)
            userParts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg").put("data", b64)
                )
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", userParts))
        var lastError: ChatApiError? = null
        // Công cụ web không dùng chung được với set_alarm trong một yêu cầu
        val isAlarmAsk = AlarmTimeParser.looksLikeSetAlarm(question)
        // Chỉ gắn công cụ web khi câu hỏi thật sự cần thông tin mới ("hello" thì không cần)
        val webAllowed = AppSettings.isChatWebSearch(app) && !isAlarmAsk && wantsWeb(question)
        // 0 = tìm Google + đọc link · 1 = chỉ tìm Google · 2 = không công cụ (nếu model từ chối)
        var webStage = 0
        repeat(3) { attempt ->
            if (cancelled()) return AskOut(GeminiTools.ModelOut("", emptyList()))
            val useWeb = webAllowed && webStage < 2
            try {
                val sys = buildString {
                    append(GeminiChatPolicy.SYSTEM)
                    append("\n")
                    append(nowLine())
                    if (useWeb) {
                        append("\n")
                        append(WEB_HINT)
                    }
                }
                val body = JSONObject()
                    .put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", sys))
                        )
                    )
                    .put("contents", contents)
                if (isAlarmAsk) {
                    body.put("tools", GeminiTools.declarations())
                } else if (useWeb) {
                    body.put("tools", GeminiTools.webTools(withUrlContext = webStage == 0))
                }
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$key"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 12_000
                    readTimeout = if (useWeb) 45_000 else 25_000
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
                // Model không nhận công cụ web -> hạ cấp dần: bỏ đọc link, rồi bỏ luôn tìm kiếm
                // Bất kỳ lỗi nào khi có công cụ web (400/403/429/5xx…) -> thử lại KHÔNG công cụ,
                // để chat thường không bao giờ bị kẹt vì phần tìm kiếm.
                if (useWeb) {
                    webStage = 2
                    return@repeat
                }
                if (lastError?.kind == ChatErrorKind.RATE_LIMIT && attempt == 0) {
                    try { Thread.sleep(1200) } catch (_: Exception) {}
                }
            } catch (t: Throwable) {
                if (cancelled()) return AskOut(GeminiTools.ModelOut("", emptyList()))
                if (useWeb) {
                    // Tìm kiếm quá chậm / lỗi mạng khi có công cụ -> lần sau bỏ công cụ
                    webStage = 2
                    lastError = ChatApiExceptionMapper.fromThrowable(t, question)
                    return@repeat
                }
                lastError = ChatApiExceptionMapper.fromThrowable(t, question)
                if (lastError?.kind == ChatErrorKind.NETWORK && attempt == 0) {
                    try { Thread.sleep(800) } catch (_: Exception) {}
                }
            }
        }
        return AskOut(GeminiTools.ModelOut("", emptyList()), lastError)
    }

    /** Câu hỏi có vẻ cần thông tin mới / liên kết -> mới bật công cụ tìm Google và đọc web. */
    private fun wantsWeb(q: String): Boolean {
        val s = q.lowercase()
        return listOf(
            "http", "www.", ".com", ".vn", ".net", ".org",
            "tin tức", "thời tiết", "tỷ giá", "giá vàng", "giá ", "kết quả", "mới nhất", "hiện tại",
            "còn hoạt động", "khai tử", "đã ngừng", "tìm ", "tra cứu", "google", "search",
            "hôm nay", "đang diễn ra", "trực tiếp", "lịch thi đấu", "điểm chuẩn", "tuyển sinh",
            "năm 2025", "năm 2026", "ai là", "bao nhiêu tiền"
        ).any { s.contains(it) }
    }

    /** Cho AI biết bây giờ là mấy giờ, ngày nào (model không tự có đồng hồ). */
    private fun nowLine(): String {
        val tz = java.util.TimeZone.getDefault()
        val f = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", java.util.Locale("vi", "VN"))
        f.timeZone = tz
        val now = java.util.Date()
        return "Thời gian hiện tại của người dùng: ${f.format(now)} (múi giờ ${tz.id}). " +
            "Khi được hỏi mấy giờ, hôm nay thứ mấy hoặc ngày bao nhiêu, hãy trả lời đúng theo thông tin này, " +
            "không bảo người dùng tự xem đồng hồ."
    }

    fun disconnect() {
        try { lastConn?.disconnect() } catch (_: Exception) {}
        lastConn = null
    }

    companion object {
        private const val MODEL = "gemini-3.6-flash"
        private const val WEB_HINT =
            "Bạn CÓ công cụ tìm kiếm Google và đọc trang web. Với thông tin mới, tin tức, giá cả, " +
            "thời tiết, kết quả, hoặc khi người dùng gửi liên kết, hãy dùng công cụ rồi trả lời ngắn gọn. " +
            "Không nói là bạn không có quyền truy cập internet hay trình duyệt."
        @Volatile
        var lastConn: HttpURLConnection? = null
    }

    private fun labelFrom(raw: String): String {
        val m = Regex("(?i)(?:nhãn|tên|gọi là|nhắn|nhan)\\s+([^,.!?]{2,24})").find(raw)
        val name = m?.groupValues?.get(1)?.trim().orEmpty()
        return if (name.isNotBlank()) name else "Báo thức AI"
    }
}
