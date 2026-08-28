package com.example.alarmclock

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) tts?.setLanguage(Locale.getDefault())
            ready = true
        }
    }

    fun speak(text: String) {
        if (ready && !text.isBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alarm_tts")
        }
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
