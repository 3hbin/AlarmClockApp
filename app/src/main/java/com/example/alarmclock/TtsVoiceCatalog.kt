package com.example.alarmclock

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice

data class TtsVoiceOption(
    val id: String,
    val label: String,
    val voiceName: String,
    val pitch: Float
)

object TtsVoiceCatalog {
    /** Theo hệ thống: dùng đúng giọng mặc định của ứng dụng giọng đọc, không ép gì thêm. */
    fun system(): TtsVoiceOption =
        TtsVoiceOption("system", Lang.t("Theo hệ thống", "System default"), "", 1.0f)

    /** Nhãn đang dùng (giọng rỗng = theo hệ thống, kể cả cài đặt cũ). */
    fun currentLabel(context: Context): String =
        if (AppSettings.getTtsVoiceName(context).isBlank()) system().label
        else AppSettings.getTtsVoiceLabel(context)

    private fun shortId(name: String): String {
        val parts = name.split('-')
        return if (parts.size >= 4 && parts[2].length == 1) parts[3] else name.takeLast(14)
    }

    /**
     * Chỉ liệt kê các giọng THẬT mà ứng dụng giọng đọc đang cài, đúng ngôn ngữ của app.
     * Android không cho biết nam/nữ nên không gán nhãn nam/nữ — bấm chọn sẽ đọc thử để nghe.
     */
    fun fromEngine(voices: Set<Voice>?, lang: String): List<TtsVoiceOption> {
        val real = voices.orEmpty()
            .filter { it.locale.language.equals(lang, true) }
            .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .distinctBy { it.name }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
            .take(20)
        return real.mapIndexed { i, v ->
            val where = if (v.isNetworkConnectionRequired) Lang.t("cần mạng", "online")
            else Lang.t("trên máy", "on device")
            TtsVoiceOption(
                v.name,
                "${Lang.t("Giọng", "Voice")} ${i + 1} · $where · ${shortId(v.name)}",
                v.name,
                1.0f
            )
        }
    }

    fun apply(context: Context, option: TtsVoiceOption) {
        AppSettings.setTtsVoiceName(context, option.voiceName)
        AppSettings.setTtsPitch(context, option.pitch)
        AppSettings.setTtsVoiceLabel(context, option.label)
    }
}
