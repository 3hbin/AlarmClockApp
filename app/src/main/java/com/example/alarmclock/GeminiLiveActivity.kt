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
    private lateinit var glow: LiveGlowView
    private lateinit var captionBox: android.view.View
    private var captionsOn = true
    private lateinit var scroll: android.widget.ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val bg = 0xFF101114.toInt()
        try {
            window.statusBarColor = bg
            window.navigationBarColor = bg
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = (flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv())
        } catch (_: Exception) {}

        glow = LiveGlowView(this).apply {
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    val r = 44 * d
                    outline.setRoundRect(0, (-r).toInt(), view.width, view.height, r)
                }
            }
            clipToOutline = true
        }
        status = TextView(this).apply {
            text = "Bấm mic để nói"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFB9BCC2.toInt())
            setPadding((24 * d).toInt(), 0, (24 * d).toInt(), (14 * d).toInt())
        }
        transcript = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.START
            setLineSpacing(0f, 1.18f)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((18 * d).toInt(), (14 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
        }
        scroll = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.30f).toInt()).apply {
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            background = GradientDrawable().apply {
                cornerRadius = 22 * d
                setColor(0xFF08090C.toInt())
            }
            addView(transcript)
        }
        captionBox = scroll
        captionBox.visibility = android.view.View.GONE

        fun pill(res: Int, color: Int, desc: String, click: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            contentDescription = desc
            background = GradientDrawable().apply {
                cornerRadius = 30 * d
                setColor(color)
            }
            setOnClickListener { click() }
        }
        val stopBtn = pill(R.drawable.ic_live_stop, 0xFF2C3139.toInt(), "Ngắt lời") { interrupt() }
        mic = pill(R.drawable.ic_chat_mic, 0xFF2C3139.toInt(), "Nói") { onMic() }
        val endBtn = pill(R.drawable.ic_chat_close, 0xFFE5362F.toInt(), "Kết thúc") { finish() }

        val live = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_live_bars)
            }, LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
            addView(TextView(context).apply {
                text = "Live"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                paint.isFakeBoldText = true
                setPadding((6 * d).toInt(), 0, 0, 0)
            })
        }
        val cc = ImageButton(this).apply {
            setImageResource(R.drawable.ic_live_cc)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            contentDescription = "Bật/tắt phụ đề"
            setOnClickListener {
                captionsOn = !captionsOn
                alpha = if (captionsOn) 1f else 0.45f
                captionBox.visibility =
                    if (captionsOn && transcript.text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        val topBar = android.widget.FrameLayout(this).apply {
            setPadding((8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            addView(live, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
            addView(cc, android.widget.FrameLayout.LayoutParams(
                (44 * d).toInt(), (44 * d).toInt(), Gravity.END or Gravity.CENTER_VERTICAL
            ))
        }

        // Giữa màn: dòng gợi ý + khung phụ đề, nằm trên vùng sáng
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(status, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (24 * d).toInt()
                marginEnd = (24 * d).toInt()
            })
        }
        val stage = android.widget.FrameLayout(this).apply {
            addView(glow, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(center, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((14 * d).toInt(), (14 * d).toInt(), (14 * d).toInt(), (22 * d).toInt())
            for (v in listOf(stopBtn, mic, endBtn)) {
                addView(v, LinearLayout.LayoutParams(0, (58 * d).toInt(), 1f).apply {
                    marginStart = (6 * d).toInt()
                    marginEnd = (6 * d).toInt()
                })
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(topBar)
            addView(stage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        setContentView(root)
        tts = TtsHelper(this).also { it.useMediaStream = true }
    }

    /** Ngắt lời Gemini / dừng nghe. */
    private fun interrupt() {
        try { tts?.stop() } catch (_: Exception) {}
        try { recognizer?.cancel() } catch (_: Exception) {}
        listening = false
        paintMic(false)
        status.text = "Bấm mic để nói"
    }

    /** Chữ phụ đề: "Tôi:" và "Gemini:" in đậm. */
    private fun styled(user: String, gemini: String?): CharSequence {
        val sb = android.text.SpannableStringBuilder()
        fun line(label: String, text: String) {
            if (sb.isNotEmpty()) sb.append("\n")
            val st = sb.length
            sb.append(label)
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length, 0)
            sb.append(" ").append(text)
        }
        line("Tôi:", user)
        if (gemini != null) line("Gemini:", gemini)
        return sb
    }

    private fun show(cs: CharSequence) {
        transcript.text = cs
        captionBox.visibility =
            if (captionsOn && cs.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
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
            override fun onRmsChanged(rmsdB: Float) {
                glow.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
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
                    show(styled(text, null))
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
        mic.background = GradientDrawable().apply {
            cornerRadius = 30 * resources.displayMetrics.density
            setColor(if (on) 0xFFFFFFFF.toInt() else 0xFF2C3139.toInt())
        }
        mic.imageTintList = android.content.res.ColorStateList.valueOf(if (on) 0xFF101114.toInt() else 0xFFFFFFFF.toInt())
        glow.state = if (on) LiveGlowView.State.LISTENING else LiveGlowView.State.IDLE
        if (!on) glow.level = 0f
    }

    private fun ask(question: String) {
        lastHeard = ""
        val local = try { ChatViewModel(this).applySpokenCommand(question) } catch (_: Exception) { null }
        val key = ChatCloudStore.geminiKey(this)
        if (key.isBlank() && local?.createdNote == null) {
            status.text = "Chưa có khóa Gemini. Mở Chat bấm Khóa."
            return
        }
        val localNote = local?.createdNote
        busy = true
        glow.state = LiveGlowView.State.THINKING
        status.text = if (localNote != null) "Đã đặt báo thức" else "Gemini đang trả lời…"
        show(styled(question, null))
        Thread {
            val answer = if (localNote != null) localNote else callGemini(key, question)
            runOnUiThread {
                busy = false
                status.text = "Đang đọc…"
                val clean = TtsHelper.forSpeech(answer).ifBlank { "Mình chưa nghe rõ." }
                val parts = clean.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
                if (parts.size <= 1) {
                    show(styled(question, clean))
                    glow.state = LiveGlowView.State.SPEAKING
                    tts?.onDone = { status.text = "Bấm mic để nói tiếp"; glow.state = LiveGlowView.State.IDLE }
                    tts?.speak(clean)
                } else {
                    var i = 0
                    fun next() {
                        if (i >= parts.size) {
                            status.text = "Bấm mic để nói tiếp"
                            show(styled(question, clean))
                            glow.state = LiveGlowView.State.IDLE
                            return
                        }
                        val line = parts[i]
                        i++
                        show(styled(question, line))
                        glow.state = LiveGlowView.State.SPEAKING
                        tts?.onDone = { next() }
                        tts?.speak(TtsHelper.forSpeech(line))
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

/** ScrollView có chiều cao tối đa, để khung phụ đề không che hết màn hình. */
private class MaxHeightScrollView(context: android.content.Context, private val maxH: Int) :
    android.widget.ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(maxH, android.view.View.MeasureSpec.AT_MOST))
    }
}
