package com.example.alarmclock

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Trợ lý gọi Gemini model mới, giao diện nhắn tin có ảnh hồ sơ. */
class ChatActivity : AppCompatActivity() {
    private val history = JSONArray()
    private val models = listOf("gemini-flash-latest", "gemini-3.6-flash", "gemini-2.5-flash")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val prefs = getSharedPreferences("chat_ai", MODE_PRIVATE)
        val d = resources.displayMetrics.density

        val log = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }
        val scroll = ScrollView(this).apply { addView(log) }
        val input = EditText(this).apply {
            hint = "Nhắn tin"
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(0xFFFFFFFF.toInt())
            }
        }
        val send = Button(this).apply {
            text = "Gửi"
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(0xFF1A73E8.toInt())
            }
        }
        val keyBtn = TextView(this).apply {
            text = "Khóa"
            setTextColor(0xFF1A73E8.toInt())
            setPadding((12 * d).toInt(), 0, (16 * d).toInt(), 0)
            gravity = Gravity.CENTER
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(avatar(true))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * d).toInt(), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "Gemini"
                    textSize = 18f
                    setTextColor(0xFF1A1C28.toInt())
                })
                addView(TextView(context).apply {
                    text = "Trợ lý trong app"
                    textSize = 12f
                    setTextColor(0xFF6B7085.toInt())
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(keyBtn)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            setBackgroundColor(0xFFF4F6FB.toInt())
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(send, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * d).toInt()
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F6FB.toInt())
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }

        fun bubble(text: String, mine: Boolean): TextView {
            val tv = TextView(this).apply {
                this.text = text
                textSize = 16f
                setTextColor(if (mine) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
                setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 18 * d
                    setColor(if (mine) 0xFF1A73E8.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or if (mine) Gravity.END else Gravity.START
                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                if (!mine) addView(avatar(true))
                addView(tv, LinearLayout.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.72f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = if (mine) 0 else (8 * d).toInt()
                    marginEnd = if (mine) (8 * d).toInt() else 0
                })
                if (mine) addView(avatar(false))
            }
            log.addView(row)
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            return tv
        }

        bubble("Chào bạn. Mình là Gemini. Hỏi gì cũng được, mình sẽ trả lời thật.", false)

        keyBtn.setOnClickListener {
            val box = EditText(this).apply {
                hint = "Dán khóa API Gemini"
                setText(prefs.getString("key", "").orEmpty())
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Khóa Gemini")
                .setMessage("Lấy khóa miễn phí tại aistudio.google.com rồi dán vào đây.")
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
                bubble("Chưa có khóa. Bấm Khóa và dán khóa API Gemini.", false)
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

    private fun avatar(gemini: Boolean): View {
        val size = (36 * resources.displayMetrics.density).toInt()
        return if (gemini) {
            ImageView(this).apply {
                setImageResource(R.drawable.ic_gemini_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF111111.toInt())
                }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        } else {
            TextView(this).apply {
                text = "Bạn"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF1A73E8.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        }
    }

    private fun askGemini(key: String, question: String): String {
        history.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question))))
        var lastError = "Không gọi được Gemini."
        for (model in models) {
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                        "text",
                        "Bạn là Gemini, trợ lý trong app đồng hồ báo thức cho học sinh. Trả lời tiếng Việt, rõ và tự nhiên. Giữ nội dung phù hợp với học sinh, không nói tục. Có thể trả lời câu hỏi thường ngày."
                    ))))
                    .put("contents", history)
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 20000
                    readTimeout = 40000
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                if (code == 404) {
                    lastError = "Model $model không còn. Đang thử model khác."
                    continue
                }
                if (code !in 200..299) {
                    history.remove(history.length() - 1)
                    return "Gemini không trả lời được (mã $code). Kiểm tra lại khóa API."
                }
                val parts = JSONObject(raw)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                val text = buildString {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) append(part.getString("text"))
                    }
                }.trim()
                if (text.isBlank()) continue
                history.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", text))))
                return text
            } catch (_: Exception) {
                lastError = "Không gọi được Gemini. Kiểm tra mạng hoặc khóa API."
            }
        }
        if (history.length() > 0 && history.getJSONObject(history.length() - 1).optString("role") == "user") {
            history.remove(history.length() - 1)
        }
        return lastError
    }
}
