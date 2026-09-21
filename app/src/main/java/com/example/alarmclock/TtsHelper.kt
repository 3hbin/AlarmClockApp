package com.example.alarmclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()
    var onDone: (() -> Unit)? = null

    init {
        main.post {
            tts = TextToSpeech(app, this)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        val lang = engine.setLanguage(Locale("vi", "VN"))
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        applySavedVoice(engine)
        engine.setSpeechRate(0.92f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) { main.post { onDone?.invoke() } }
            override fun onDone(utteranceId: String?) { main.post { onDone?.invoke() } }
        })
        ready = true
        flush()
    }

    private fun applySavedVoice(engine: TextToSpeech) {
        val name = AppSettings.getTtsVoiceName(app)
        val pitch = AppSettings.getTtsPitch(app)
        engine.setPitch(pitch.coerceIn(0.6f, 1.4f))
        if (name.isBlank()) {
            pickFallback(engine, preferMale = true)
            return
        }
        val voices = try { engine.voices } catch (_: Exception) { emptySet<Voice>() }
        val match = voices?.firstOrNull { it.name == name }
        if (match != null) {
            try { engine.voice = match } catch (_: Exception) { pickFallback(engine, true) }
        } else pickFallback(engine, true)
    }

    private fun pickFallback(engine: TextToSpeech, preferMale: Boolean) {
        val voices = try { engine.voices } catch (_: Exception) { emptySet<Voice>() }
        if (voices.isNullOrEmpty()) return
        val vi = voices.filter { it.locale.language.equals("vi", true) }
        val pool = if (vi.isNotEmpty()) vi else voices.toList()
        val chosen = if (preferMale) {
            pool.firstOrNull { v ->
                val n = v.name.lowercase()
                n.contains("male") || n.contains("nam") || n.contains("-m-") || n.contains("vif")
            } ?: pool.firstOrNull()
        } else {
            pool.firstOrNull { v ->
                val n = v.name.lowercase()
                n.contains("female") || n.contains("nu") || n.contains("-f-") || n.contains("vid")
            } ?: pool.firstOrNull()
        }
        if (chosen != null) try { engine.voice = chosen } catch (_: Exception) {}
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        main.post {
            pending.addLast(text)
            flush()
        }
    }

    private fun flush() {
        if (!ready) return
        val engine = tts ?: return
        while (pending.isNotEmpty()) {
            val text = pending.removeFirst()
            try {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (max > 0) {
                    val now = am.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (now < (max * 3) / 4) am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                }
            } catch (_: Exception) {}
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            }
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, "alarm_tts_${System.currentTimeMillis()}")
        }
    }

    fun speakVoiceNote(note: String?) {
        if (!note.isNullOrBlank()) speak(note)
    }

    fun shutdown() {
        main.post {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }
}
