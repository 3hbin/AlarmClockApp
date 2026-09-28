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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Chat Gemini: cần đăng nhập Google, có lịch sử, và đặt báo thức thật. */
class ChatActivity : AppCompatActivity() {
    private val history = JSONArray()
    private val model = "gemini-3.6-flash"
    private val spinHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val spinning = HashMap<ImageView, Runnable>()
    private val frames by lazy {
        IntArray(39) { resources.getIdentifier("gemini_loop_%02d".format(it), "drawable", packageName) }
    }
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        GoogleSignInHelper.handleResult(this, result.data)
        if (signedIn()) showChat() else showGate("Chưa đăng nhập được. Thử lại.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (signedIn()) showChat() else showGate("Chat cần đăng nhập Google để lưu lịch sử.")
    }

    private fun signedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val email = account?.email ?: AppSettings.getRecoveryEmail(this)
        return !email.isNullOrBlank()
    }

    private fun showGate(message: String) {
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            setBackgroundColor(0xFFF4F6FB.toInt())
            addView(TextView(context).apply {
                text = "Đăng nhập Google"
                textSize = 22f
                setTextColor(0xFF1A1C28.toInt())
            })
            addView(TextView(context).apply {
                text = message
                textSize = 16f
                setPadding(0, (12 * d).toInt(), 0, (20 * d).toInt())
                setTextColor(0xFF444444.toInt())
            })
            addView(Button(context).apply {
                text = "Đăng nhập"
                isAllCaps = false
                setOnClickListener { signInLauncher.launch(GoogleSignInHelper.signInIntent(this@ChatActivity)) }
            })
        }
        setContentView(root)
    }

    private fun showChat() {
        val prefs = getSharedPreferences("chat_ai", MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val email = GoogleSignIn.getLastSignedInAccount(this)?.email
            ?: AppSettings.getRecoveryEmail(this)

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
        val headerAvatar = avatar(true) as ImageView
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * d).toInt(), (10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            addView(headerAvatar)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * d).toInt(), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "Gemini"
                    textSize = 18f
                    setTextColor(0xFF1A1C28.toInt())
                })
                addView(TextView(context).apply {
                    text = email.ifBlank { "Đã đăng nhập" }
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

        fun bubble(text: String, mine: Boolean, save: Boolean): TextView {
            val clean = text.replace("**", "").trim()
            val tv = TextView(this).apply {
                this.text = clean
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
                val geminiView = if (!mine) (avatar(true) as ImageView).also { addView(it) } else null
                tv.tag = geminiView
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
            if (save) {
                history.put(JSONObject().put("m", if (mine) 1 else 0).put("t", clean))
                while (history.length() > 40) history.remove(0)
                prefs.edit().putString("history", history.toString()).apply()
            }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            return tv
        }

        val saved = prefs.getString("history", "[]").orEmpty()
        val old = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
        if (old.length() == 0) {
            bubble("Chào bạn. Nhắn “đặt báo thức 7:00” để mình lưu báo thức thật.", false, true)
        } else {
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                bubble(item.optString("t"), item.optInt("m") == 1, false)
                history.put(item)
            }
        }

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
                    bubble("Đã lưu khóa.", false, true)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            val key = prefs.getString("key", "").orEmpty()
            if (key.isBlank()) {
                bubble("Chưa có khóa. Bấm Khóa và dán khóa API Gemini.", false, true)
                return@setOnClickListener
            }
            bubble(q, true, true)
            input.setText("")
            val waiting = bubble("Gemini đang trả lời...", false, false)
            (waiting.tag as? ImageView)?.let { startSpin(it) }
            startSpin(headerAvatar)
            send.isEnabled = false
            Thread {
                val created = createAlarmIfAsked(q)
                val answer = askGemini(key, q, created)
                val shown = if (created == null) answer else "$created\n\n$answer"
                runOnUiThread {
                    waiting.text = shown
                    (waiting.tag as? ImageView)?.let { stopSpin(it) }
                    stopSpin(headerAvatar)
                    history.put(JSONObject().put("m", 0).put("t", shown))
                    prefs.edit().putString("history", history.toString()).apply()
                    send.isEnabled = true
                    scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }.start()
        }
        setContentView(root)
    }

    private fun createAlarmIfAsked(raw: String): String? {
        val time = parseAlarmTime(raw) ?: return null
        val repo = AlarmRepository(this)
        val alarm = Alarm(
            id = repo.getNextId(),
            hour = time.first,
            minute = time.second,
            isEnabled = true,
            label = "Báo thức",
            repeatMode = if (raw.contains("mỗi ngày") || raw.contains("hang ngay")) Alarm.REPEAT_DAILY else Alarm.REPEAT_ONCE
        )
        val list = repo.getAlarms().toMutableList()
        list.add(alarm)
        repo.saveAlarms(list)
        AlarmScheduler.schedule(this, alarm)
        return "Đã lưu báo thức %02d:%02d. Quay lại tab Báo để xem.".format(time.first, time.second)
    }

    private fun parseAlarmTime(raw: String): Pair<Int, Int>? {
        val q = raw.lowercase()
        if (!q.contains("đặt") && !q.contains("dat") && !q.contains("báo") && !q.contains("bao")) return null
        val clock = Regex("(\\d{1,2})\\s*[:h.]\\s*(\\d{2})").find(q)
        val words = Regex("(\\d{1,2})\\s*(giờ|gio)(?:\\s*(\\d{1,2}))?").find(q)
        val hour0 = clock?.groupValues?.get(1)?.toIntOrNull() ?: words?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val minute = clock?.groupValues?.get(2)?.toIntOrNull()
            ?: words?.groupValues?.get(3)?.toIntOrNull()
            ?: 0
        var hour = hour0
        if ((q.contains("tối") || q.contains("chiều") || q.contains("pm")) && hour in 1..11) hour += 12
        if ((q.contains("sáng") || q.contains("am")) && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun startSpin(view: ImageView) {
        stopSpin(view)
        var i = 0
        val run = object : Runnable {
            override fun run() {
                if (!spinning.containsKey(view)) return
                val id = frames[i % frames.size]
                if (id != 0) view.setImageResource(id)
                i++
                spinHandler.postDelayed(this, 90)
            }
        }
        spinning[view] = run
        spinHandler.post(run)
    }

    private fun stopSpin(view: ImageView) {
        spinning.remove(view)?.let { spinHandler.removeCallbacks(it) }
        view.setImageResource(R.drawable.ic_gemini_avatar)
    }

    override fun onDestroy() {
        spinning.values.forEach { spinHandler.removeCallbacks(it) }
        spinning.clear()
        super.onDestroy()
    }

    private fun avatar(gemini: Boolean): View {
        val size = (36 * resources.displayMetrics.density).toInt()
        return if (gemini) {
            ImageView(this).apply {
                setImageResource(R.drawable.ic_gemini_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFFFFFFF.toInt())
                    setStroke((1 * resources.displayMetrics.density).toInt(), 0xFFE0E3EA.toInt())
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

    private fun askGemini(key: String, question: String, created: String?): String {
        val prompt = if (created == null) question else "$question\n$created. Hãy xác nhận ngắn, đừng nói là chưa đặt."
        val contents = JSONArray()
        val saved = getSharedPreferences("chat_ai", MODE_PRIVATE).getString("history", "[]").orEmpty()
        val old = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
        val start = (old.length() - 8).coerceAtLeast(0)
        for (i in start until old.length()) {
            val item = old.getJSONObject(i)
            contents.put(JSONObject()
                .put("role", if (item.optInt("m") == 1) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", item.optString("t")))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        repeat(2) {
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                        "text",
                        "Bạn là Gemini trong app đồng hồ báo thức cho học sinh. Trả lời tiếng Việt, ngắn, dễ hiểu. Không nói tục. Nếu app đã lưu báo thức, xác nhận đúng giờ đó."
                    ))))
                    .put("contents", contents)
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
                if (code in 200..299) {
                    val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                    val text = buildString {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) append(part.getString("text"))
                        }
                    }.trim()
                    if (text.isNotBlank()) return text
                }
            } catch (_: Exception) {
            }
        }
        return created ?: "Gemini đang bận. Đợi một lát rồi gửi lại."
    }
}
