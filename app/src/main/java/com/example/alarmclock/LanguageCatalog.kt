package com.example.alarmclock

/**
 * Các ngôn ngữ được hỗ trợ (+ theo hệ thống). Ngôn ngữ khác dùng tiếng Anh.
 */
data class AppLanguage(
    val code: String,
    val native: String,
    val english: String,
    val flag: String
) {
    val displayLabel: String
        get() = "$flag $native — $english ($code)"
}

object LanguageCatalog {
    const val SYSTEM = "system"

    val languages: List<AppLanguage> = listOf(
        AppLanguage("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
        AppLanguage("en", "English", "English", "🇬🇧"),
        AppLanguage("hi", "हिन्दी", "Hindi", "🇮🇳"),
        AppLanguage("ru", "Русский", "Russian", "🇷🇺"),
        AppLanguage("zh", "中文", "Chinese", "🇨🇳"),
        AppLanguage("ko", "한국어", "Korean", "🇰🇷"),
        AppLanguage("ja", "日本語", "Japanese", "🇯🇵"),
        AppLanguage("fr", "Français", "French", "🇫🇷")
    )

    fun displayName(code: String): String {
        if (code == SYSTEM || code.isBlank()) return "🌐 System default"
        val lang = languages.find { it.code.equals(code, ignoreCase = true) }
            ?: return "🇬🇧 English (en)"
        return "${lang.flag} ${lang.native} — ${lang.english} (${lang.code})"
    }

    fun search(query: String): List<AppLanguage> {
        if (query.isBlank()) return languages
        val q = query.trim().lowercase()
        return languages.filter {
            it.code.contains(q) ||
                it.native.lowercase().contains(q) ||
                it.english.lowercase().contains(q)
        }
    }
}
