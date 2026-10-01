package com.example.alarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Nói chuyện giọng nói với Gemini trong app. */
class GeminiLiveActivity : AppCompatActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TtsHelper? = null
    private var listening = false
    private var busy = false
    private var lastHeard = ""
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var mic: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        status = TextView(this).apply {
            text = "Bấm mic để nói"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF1A1C28.toInt())
        }
        transcript = TextView(this).apply {
            text = "Nói xong bấm mic lần nữa để gửi.\nGemini sẽ trả lời bằng giọng nói."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF3C4043.toInt())
            setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), (16 * d).toInt())
        }
        mic = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_live)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1A73E8.toInt())
            }
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setOnClickListener { onMic() }
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_back)
            background = null
            contentDescription = "Quay lại"
            setOnClickListener { finish() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F3F4.toInt())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding((4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
                addView(back, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
                addView(TextView(context).apply {
                    text = "Gemini Live"
                    textSize = 18f
                    setTextColor(0xFF1A1C28.toInt())
                    paint.isFakeBoldText = true
                })
            })
            addView(status, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (36 * d).toInt() })
            addView(transcript, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(mic, LinearLayout.LayoutParams((84 * d).toInt(), (84 * d).toInt()).apply {
                gravity = Gravity.CENTER
                bottomMargin = (36 * d).toInt()
            })
        }
        setContentView(root)
        tts = TtsHelper(this).also { it.useMediaStream = true }
    }

    private fun onMic() {
        if (busy) {
            Toast.makeText(this, "Đang trả lời, đợi chút", Toast.LENGTH_SHORT).show()
            return
        }
        if (listening) {
            status.text = "Đang gửi…"
            try { recognizer?.stopListening() } catch (_: Exception) {}
            val heard = lastHeard.trim()
            if (heard.length >= 2) {
                listening = false
                paintMic(false)
                ask(heard)
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 71)
            return
        }
        startListen()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 71 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListen()
        else Toast.makeText(this, "Cần quyền micro để Live", Toast.LENGTH_SHORT).show()
    }

    private fun startListen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Máy không có nhận giọng nói"
            Toast.makeText(this, "Cần Google app / nhận giọng nói", Toast.LENGTH_LONG).show()
            return
        }
        try { tts?.stop() } catch (_: Exception) {}
        lastHeard = ""
        try { recognizer?.destroy() } catch (_: Exception) {}
        val rec = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            status.text = "Không mở được micro"
            return
        }
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Đang nghe… nói đi"
            }
            override fun onBeginningOfSpeech() {
                status.text = "Đã nghe thấy"
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                status.text = "Đang gửi…"
            }
            override fun onError(error: Int) {
                listening = false
                paintMic(false)
                val heard = lastHeard.trim()
                if (heard.length >= 2 && error != SpeechRecognizer.ERROR_CLIENT) {
                    ask(heard)
                    return
                }
                status.text = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Chưa nghe rõ. Bấm mic nói lại."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Im quá lâu. Bấm mic nói lại."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Mạng nhận giọng yếu. Thử lại."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền micro"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Micro đang bận. Đợi 1 giây rồi bấm."
                    else -> "Lỗi nghe ($error). Bấm mic nói lại."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                paintMic(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim().ifBlank { lastHeard.trim() }
                if (text.length < 2) {
                    status.text = "Chưa nghe rõ. Bấm mic nói lại."
                    return
                }
                ask(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    lastHeard = text
                    transcript.text = text
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }
        listening = true
        paintMic(true)
        status.text = "Đang nghe…"
        try {
            rec.startListening(intent)
        } catch (_: Exception) {
            listening = false
            paintMic(false)
            status.text = "Không bật được nghe"
        }
    }

    private fun paintMic(on: Boolean) {
        (mic.background as? GradientDrawable)?.setColor(if (on) 0xFFD93025.toInt() else 0xFF1A73E8.toInt())
    }

    private fun ask(question: String) {
        lastHeard = ""
        val key = ChatCloudStore.geminiKey(this)
        if (key.isBlank()) {
            status.text = "Chưa có khóa Gemini. Mở Chat bấm Khóa."
            return
        }
        busy = true
        status.text = "Gemini đang trả lời…"
        transcript.text = "Bạn: $question"
        Thread {
            val answer = callGemini(key, question)
            runOnUiThread {
                busy = false
                status.text = "Đang đọc…"
                val clean = answer.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("\\s+"), " ").trim()
                val parts = clean.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
                if (parts.size <= 1) {
                    transcript.text = "Bạn: $question\n\nGemini: $answer"
                    tts?.onDone = { status.text = "Bấm mic để nói tiếp" }
                    tts?.speak(clean)
                } else {
                    var i = 0
                    fun next() {
                        if (i >= parts.size) {
                            status.text = "Bấm mic để nói tiếp"
                            transcript.text = "Bạn: $question\n\nGemini: $answer"
                            return
                        }
                        val line = parts[i]
                        i++
                        transcript.text = "Bạn: $question\n\nGemini: $line"
                        tts?.onDone = { next() }
                        tts?.speak(line)
                    }
                    next()
                }
            }
        }.start()
    }

    private fun callGemini(key: String, question: String): String {
        return try {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", GeminiChatPolicy.SYSTEM))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))))
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 22_000
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val raw = conn.inputStream.bufferedReader().readText()
            val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (p.has("text")) append(p.getString("text"))
                }
            }.trim().ifBlank { "Mình chưa nghe rõ." }
        } catch (_: Exception) {
            "Mạng bận. Thử nói lại."
        }
    }

    override fun onDestroy() {
        listening = false
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}
