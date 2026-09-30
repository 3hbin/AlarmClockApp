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

/** Nói chuyện giọng nói với Gemini trong app (không mở trợ lý hệ thống). */
class GeminiLiveActivity : AppCompatActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TtsHelper? = null
    private var listening = false
    private var busy = false
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var mic: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        status = TextView(this).apply {
            text = "Gemini Live"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFF1A1C28.toInt())
        }
        transcript = TextView(this).apply {
            text = "Bấm mic rồi nói. Gemini sẽ trả lời bằng giọng nói.\nKhông hỗ trợ lập trình."
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
            val s = (84 * d).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
            setOnClickListener { onMic() }
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_back)
            background = null
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
        if (busy) return
        if (listening) {
            stopListen()
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
            Toast.makeText(this, "Máy không có nhận giọng nói", Toast.LENGTH_LONG).show()
            return
        }
        try { tts?.stop() } catch (_: Exception) {}
        stopListen()
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Đang nghe…"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Đang nghĩ…" }
            override fun onError(error: Int) {
                listening = false
                status.text = "Gemini Live"
                paintMic(false)
            }
            override fun onResults(results: Bundle?) {
                listening = false
                paintMic(false)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (text.isBlank()) {
                    status.text = "Chưa nghe rõ. Bấm mic nói lại."
                    return
                }
                transcript.text = "Bạn: $text"
                ask(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) transcript.text = text
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listening = true
        paintMic(true)
        rec.startListening(intent)
    }

    private fun stopListen() {
        listening = false
        paintMic(false)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun paintMic(on: Boolean) {
        (mic.background as? GradientDrawable)?.setColor(if (on) 0xFFD93025.toInt() else 0xFF1A73E8.toInt())
    }

    private fun ask(question: String) {
        val key = ChatCloudStore.geminiKey(this)
        if (key.isBlank()) {
            status.text = "Chưa có khóa Gemini. Mở Chat bấm Khóa."
            return
        }
        busy = true
        status.text = "Gemini đang trả lời…"
        Thread {
            val answer = callGemini(key, question)
            runOnUiThread {
                busy = false
                status.text = "Gemini Live"
                transcript.text = "Bạn: $question\n\nGemini: $answer"
                tts?.speak(answer.replace(Regex("```[\\s\\S]*?```"), " "))
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
                connectTimeout = 20000
                readTimeout = 40000
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
        stopListen()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }
}
