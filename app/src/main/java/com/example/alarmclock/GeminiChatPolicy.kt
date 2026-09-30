package com.example.alarmclock

object GeminiChatPolicy {
    const val SYSTEM = "Bạn là Gemini trong app đồng hồ báo thức cho học sinh. " +
        "Trả lời tiếng Việt, ngắn, dễ hiểu, lịch sự. Không nói tục. " +
        "Không hỗ trợ lập trình: không viết code, không sửa lỗi code, không hướng dẫn ngôn ngữ lập trình, " +
        "không giải bài tập code. Nếu bị hỏi lập trình, từ chối nhẹ và gợi ý học trên lớp hoặc hỏi giáo viên. " +
        "Có thể dùng **in đậm**, *nghiêng*, công thức toán \$E=mc^2\$ hoặc hóa chất H2O. " +
        "Nếu cần đưa lời nhắc (prompt) để học sinh copy, viết rõ khối đó trong ```prompt ... ```. " +
        "Nếu app đã lưu báo thức, xác nhận đúng giờ đó."

    fun extractCopyBlocks(raw: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        Regex("```([a-zA-Z0-9_+-]*)\\s*\\n([\\s\\S]*?)```").findAll(raw).forEach { m ->
            val label = m.groupValues[1].ifBlank { "đoạn" }
            val body = m.groupValues[2].trim()
            if (body.length >= 4) {
                val title = if (label.equals("prompt", true) || "prompt" in label.lowercase())
                    "Sao chép prompt" else "Sao chép $label"
                out.add(title to body)
            }
        }
        Regex("(?im)^(?:prompt|lời nhắc)\\s*[:：]\\s*([^\\n].+(?:\\n(?!\\n).+)*)")
            .findAll(raw).forEach { m ->
                val body = m.groupValues[1].trim()
                if (body.length >= 8 && out.none { it.second == body }) {
                    out.add("Sao chép prompt" to body)
                }
            }
        return out
    }
}
