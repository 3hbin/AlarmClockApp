package com.example.alarmclock

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

data class TtsVoiceOption(
    val id: String,
    val label: String,
    val voiceName: String,
    val pitch: Float
)

object TtsVoiceCatalog {
    fun builtInFallbacks(): List<TtsVoiceOption> = listOf(
        TtsVoiceOption("m1", "Nam trầm", "", 0.70f),
        TtsVoiceOption("m2", "Nam vừa", "", 0.85f),
        TtsVoiceOption("m3", "Nam rõ", "", 0.95f),
        TtsVoiceOption("m4", "Nam cao", "", 1.08f),
        TtsVoiceOption("f1", "Nữ trầm", "", 0.92f),
        TtsVoiceOption("f2", "Nữ vừa", "", 1.05f),
        TtsVoiceOption("f3", "Nữ nhẹ", "", 1.12f),
        TtsVoiceOption("f4", "Nữ cao", "", 1.22f),
        TtsVoiceOption("k1", "Giọng chậm", "", 0.80f),
        TtsVoiceOption("k2", "Giọng nhanh", "", 1.18f),
        TtsVoiceOption("k3", "Giọng kể chuyện", "", 0.88f),
        TtsVoiceOption("k4", "Giọng thông báo", "", 1.00f)
    )

    fun fromEngine(voices: Set<Voice>?): List<TtsVoiceOption> {
        val list = mutableListOf<TtsVoiceOption>()
        val all = voices?.toList().orEmpty()
        val vi = all.filter { it.locale.language.equals("vi", true) }
        val rest = all.filter { !it.locale.language.equals("vi", true) }
        fun addVoice(v: Voice, extra: String, pitch: Float) {
            val gender = guessGender(v)
            val loc = v.locale.displayLanguage
            list.add(
                TtsVoiceOption(
                    id = v.name + "_" + pitch,
                    label = "$gender · $loc$extra",
                    voiceName = v.name,
                    pitch = pitch
                )
            )
        }
        vi.forEach { addVoice(it, "", 1.0f) }
        vi.take(4).forEach { v ->
            addVoice(v, " · trầm", 0.75f)
            addVoice(v, " · cao", 1.15f)
        }
        rest.take(8).forEach { addVoice(it, "", 1.0f) }
        if (list.size < 12) list.addAll(builtInFallbacks())
        return list.distinctBy { it.label }.take(24)
    }

    private fun guessGender(v: Voice): String {
        val n = v.name.lowercase()
        return when {
            listOf("female", "nu", "-f-", "vid", "woman").any { n.contains(it) } -> "Nữ"
            listOf("male", "nam", "-m-", "vif", "man").any { n.contains(it) } -> "Nam"
            else -> "Khác"
        }
    }

    fun apply(context: Context, option: TtsVoiceOption) {
        AppSettings.setTtsVoiceName(context, option.voiceName)
        AppSettings.setTtsPitch(context, option.pitch)
        AppSettings.setTtsVoiceLabel(context, option.label)
    }
}
