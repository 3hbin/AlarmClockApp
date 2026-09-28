package com.example.alarmclock

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Trợ lý gọi Gemini. Không có khóa thì chưa trả lời được như AI thật. */
class ChatActivity : AppCompatActivity() {
    private val history = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val prefs = getSharedPreferences("chat_ai", MODE_PRIVATE)

        val log = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }
        val scroll = ScrollView(this).apply { addView(log) }
        val input = EditText(this).apply {
            hint = "Nhắn cho trợ lý"
            setPadding(24, 16, 24, 16)
        }
        val send = Button(this).apply { text = "Gửi" }
        val keyBtn = Button(this).apply { text = "Khóa Gemini" }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 12)
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(send)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F6FB.toInt())
            addView(keyBtn)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }

        fun bubble(text: String, mine: Boolean): TextView {
            val tv = TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(if (mine) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
                setPadding(28, 18, 28, 18)
                background = GradientDrawable().apply {
                    cornerRadius = 28f * resources.displayMetrics.density
                    setColor(if (mine) 0xFF1A73E8.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            val row = LinearLayout(this).apply {
                gravity = if (mine) Gravity.END else Gravity.START
                setPadding(0, 8, 0, 8)
                addView(tv, LinearLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.78f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            log.addView(row)
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            return tv
        }

        bubble(
            "Mình trả lời bằng Gemini. Bấm Khóa Gemini và dán khóa API một lần. Không có khóa thì chưa trả lời thật được.",
            false
        )

        keyBtn.setOnClickListener {
            val box = EditText(this).apply {
                hint = "Dán khóa API Gemini"
                setText(prefs.getString("key", "").orEmpty())
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Khóa Gemini")
                .setMessage("Lấy khóa miễn phí tại aistudio.google.com, rồi dán vào đây.")
                .setView(box)
                .setPositiveButton("Lưu") { _, _ ->
                    prefs.edit().putString("key", box.text.toString().trim()).apply()
                    bubble("Đã lưu khóa. Nhắn một câu để thử.", false)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            val key = prefs.getString("key", "").orEmpty()
            if (key.isBlank()) {
                bubble("Chưa có khóa Gemini. Bấm Khóa Gemini và dán khóa API trước.", false)
                return@setOnClickListener
            }
            bubble(q, true)
            input.setText("")
            val waiting = bubble("Đang trả lời...", false)
            send.isEnabled = false
            Thread {
                val answer = askGemini(key, q)
                runOnUiThread {
                    waiting.text = answer
                    send.isEnabled = true
                    scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }.start()
        }
        setContentView(root)
    }

    private fun askGemini(key: String, question: String): String {
        return try {
            history.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question))))
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                    "text",
                    "Bạn là trợ lý trong app đồng hồ báo thức cho học sinh. Trả lời tiếng Việt, ngắn, dễ hiểu. Giữ nội dung phù hợp với học sinh, không nói tục. Có thể trả lời câu hỏi thường ngày. Nếu hỏi cách dùng app thì chỉ báo thức, ngủ, nhạc ru, đếm ngược và nhắc nước."
                ))))
                .put("contents", history)
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 30000
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream.bufferedReader().readText()
            if (code !in 200..299) {
                history.remove(history.length() - 1)
                return "Gemini không trả lời được (mã $code). Kiểm tra lại khóa API."
            }
            val text = JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            history.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", text))))
            text.ifBlank { "Gemini trả về trống. Thử hỏi lại." }
        } catch (e: Exception) {
            if (history.length() > 0) history.remove(history.length() - 1)
            "Không gọi được Gemini. Kiểm tra mạng hoặc khóa API."
        }
    }
}
