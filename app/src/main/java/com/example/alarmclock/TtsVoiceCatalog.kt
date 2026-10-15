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
        TtsVoiceOption("google_f", "Nữ Google", "", 1.05f),
        TtsVoiceOption("ms_m", "Nam Microsoft", "", 0.90f),
        TtsVoiceOption("f1", "Nữ trầm", "", 0.92f),
        TtsVoiceOption("f2", "Nữ vừa", "", 1.05f),
        TtsVoiceOption("m1", "Nam trầm", "", 0.75f),
        TtsVoiceOption("m2", "Nam vừa", "", 0.90f),
        TtsVoiceOption("k1", "Giọng khác nữ", "", 1.10f),
        TtsVoiceOption("k2", "Giọng khác nam", "", 0.85f)
    )

    fun fromEngine(voices: Set<Voice>?): List<TtsVoiceOption> {
        val list = mutableListOf<TtsVoiceOption>()
        val all = voices?.toList().orEmpty()
        val vi = all.filter { it.locale.language.equals("vi", true) }

        fun labelFor(v: Voice): String {
            val n = v.name.lowercase()
            val gender = when {
                listOf("female", "nu", "-f-", "woman").any { n.contains(it) } -> "Nữ"
                listOf("male", "nam", "-m-", "man").any { n.contains(it) } -> "Nam"
                else -> "Khác"
            }
            val engine = when {
                n.contains("google") -> "Google"
                n.contains("microsoft") || n.contains("ms") -> "Microsoft"
                else -> ""
            }
            return if (engine.isNotEmpty()) "$gender $engine" else gender
        }

        vi.forEach { v ->
            list.add(TtsVoiceOption(v.name, labelFor(v), v.name, 1.0f))
        }
        if (list.isEmpty()) list.addAll(builtInFallbacks())
        else {
            // Ensure preferred labels exist
            if (list.none { it.label.contains("Google") }) {
                list.add(0, TtsVoiceOption("google_f", "Nữ Google", "", 1.05f))
            }
            if (list.none { it.label.contains("Microsoft") }) {
                list.add(1, TtsVoiceOption("ms_m", "Nam Microsoft", "", 0.90f))
            }
        }
        return list.distinctBy { it.label }.take(12)
    }

    fun apply(context: Context, option: TtsVoiceOption) {
        AppSettings.setTtsVoiceName(context, option.voiceName)
        AppSettings.setTtsPitch(context, option.pitch)
        AppSettings.setTtsVoiceLabel(context, option.label)
    }
}
