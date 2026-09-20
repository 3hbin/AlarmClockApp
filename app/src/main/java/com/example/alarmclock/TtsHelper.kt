package com.example.alarmclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(app, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        val lang = engine.setLanguage(Locale("vi", "VN"))
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        pickMaleVoice(engine)
        engine.setPitch(0.78f)
        engine.setSpeechRate(0.92f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
    }

    private fun pickMaleVoice(engine: TextToSpeech) {
        val voices = try { engine.voices } catch (_: Exception) { emptySet<Voice>() }
        if (voices.isNullOrEmpty()) return
        val vi = voices.filter { it.locale.language.equals("vi", true) }
        val pool = if (vi.isNotEmpty()) vi else voices.toList()
        val male = pool.firstOrNull { v ->
            val n = v.name.lowercase()
            n.contains("male") || n.contains("nam") || n.contains("-m-") ||
                n.contains("vif") || n.contains("x-vim")
        } ?: pool.firstOrNull { v ->
            val n = v.name.lowercase()
            !n.contains("female") && !n.contains("nu") && !n.contains("vid")
        }
        if (male != null) {
            try { engine.voice = male } catch (_: Exception) {}
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (max > 0) {
                val now = am.getStreamVolume(AudioManager.STREAM_ALARM)
                if (now < (max * 3) / 4) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                }
            }
        } catch (_: Exception) {}
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_tts")
    }

    fun speakVoiceNote(note: String?) {
        if (!note.isNullOrBlank()) speak(note)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
