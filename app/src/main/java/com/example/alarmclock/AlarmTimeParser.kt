package com.example.alarmclock

/**
 * Đọc giờ báo thức từ câu nói / tin nhắn tự do.
 * Ví dụ hợp lệ: 6:07, 6:7, 6h07, 6h7, 6.07, 6 giờ 7, 6 giờ 07 phút,
 * 8:30, 22:15, 7pm, 6 giờ sáng, 7 rưỡi, 22h.
 */
object AlarmTimeParser {

    data class AlarmTime(val hour: Int, val minute: Int)

    fun parseFirst(raw: String): Pair<Int, Int>? =
        parseAll(raw).firstOrNull()?.let { it.hour to it.minute }

    fun parseAll(raw: String): List<AlarmTime> {
        val text = normalize(raw)
        if (text.isBlank()) return emptyList()

        val found = LinkedHashSet<AlarmTime>()

        // 6:07 | 6h07 | 6.07 | 6 giờ 07 | 6gio7 | 6h 7 phút
        val clock = Regex(
            """(?<!\d)(\d{1,2})\s*(?:[:hHgG.]|giờ|gio|hours?|hrs?)\s*(\d{1,2})(?:\s*(?:phút|phut|p|min|mins|minutes?))?""",
            RegexOption.IGNORE_CASE
        )
        clock.findAll(text).forEach { m ->
            add(found, m.groupValues[1], m.groupValues[2], text, m.range)
        }

        // 7 rưỡi / 7 ruoi
        Regex("""(?<!\d)(\d{1,2})\s*(?:giờ|gio|h)?\s*(rưỡi|ruoi)""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                add(found, m.groupValues[1], "30", text, m.range)
            }

        // 7pm / 7 am / 7g tối
        Regex("""(?<!\d)(\d{1,2})\s*(a\.?m\.?|p\.?m\.?)""", RegexOption.IGNORE_CASE)
            .findAll(text).forEach { m ->
                val ampm = m.groupValues[2].lowercase()
                add(found, m.groupValues[1], "0", ampm, m.range)
            }

        // Chỉ giờ: "lúc 22h", "6 giờ", "đặt 7"
        if (found.isEmpty() || looksLikeSetAlarm(text)) {
            Regex("""(?<!\d)(\d{1,2})\s*(?:[:hHgG]|giờ|gio|hours?|hrs?)(?!\s*\d)""")
                .findAll(text).forEach { m ->
                    add(found, m.groupValues[1], "0", text, m.range)
                }
        }

        return found.toList()
    }

    fun looksLikeSetAlarm(raw: String): Boolean {
        val q = normalize(raw)
        val keys = listOf(
            "đặt", "dat", "báo", "bao", "thức", "thuc", "alarm", "wake",
            "nhắc", "nhac", "gọi dậy", "goi day", "thức dậy", "thuc day",
            "hẹn giờ", "hen gio"
        )
        return keys.any { it in q }
    }

    private fun normalize(raw: String): String {
        return raw.lowercase()
            .replace('：', ':')
            .replace('．', '.')
            .replace("小时", "h")
            .replace('\u00a0', ' ')
            .trim()
    }

    private fun add(
        out: MutableSet<AlarmTime>,
        hourRaw: String,
        minuteRaw: String,
        context: String,
        range: IntRange
    ) {
        var hour = hourRaw.toIntOrNull() ?: return
        val minute = minuteRaw.toIntOrNull() ?: return
        if (minute !in 0..59) return

        val around = context.substring(
            (range.first - 12).coerceAtLeast(0),
            (range.last + 13).coerceAtMost(context.length)
        )
        hour = applyMeridiem(hour, around)
        if (hour in 0..23) out.add(AlarmTime(hour, minute))
    }

    private fun applyMeridiem(hour0: Int, around: String): Int {
        var hour = hour0
        val pm = listOf("tối", "toi", "chiều", "chieu", "pm", "p.m", "evening", "afternoon", "trưa", "trua")
        val am = listOf("sáng", "sang", "am", "a.m", "morning")
        if (pm.any { it in around } && hour in 1..11) hour += 12
        if (am.any { it in around } && hour == 12) hour = 0
        return hour
    }
}
