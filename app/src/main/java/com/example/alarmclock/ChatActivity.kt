package com.example.alarmclock

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Chat Gemini: đăng nhập Google, gõ chữ, định dạng, lưu cloud theo tài khoản. */
class ChatActivity : AppCompatActivity() {
    private val history = JSONArray()
    private val model = "gemini-3.6-flash"
    private val spinHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val typeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val spinning = HashMap<ImageView, Runnable>()
    private val cancelled = AtomicBoolean(false)
    private var generating = false
    private var activeConn: HttpURLConnection? = null
    private lateinit var sendBtn: ImageButton
    private var headerAvatar: ImageView? = null
    private var scrollRef: ScrollView? = null
    private var logRef: LinearLayout? = null

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

    private fun accountEmail(): String {
        return GoogleSignIn.getLastSignedInAccount(this)?.email
            ?: AppSettings.getRecoveryEmail(this)
    }

    private fun accountName(): String {
        val n = GoogleSignIn.getLastSignedInAccount(this)?.displayName
            ?: AppSettings.getGoogleDisplayName(this)
        return n.ifBlank { "Gemini" }
    }

    private fun chatPrefs() = ChatCloudStore.prefs(this)

    private fun showGate(message: String) {
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
            setBackgroundColor(0xFFF1F3F4.toInt())
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
            addView(android.widget.Button(context).apply {
                text = "Đăng nhập"
                isAllCaps = false
                setOnClickListener { signInLauncher.launch(GoogleSignInHelper.signInIntent(this@ChatActivity)) }
            })
        }
        setContentView(root)
    }

    private fun showChat() {
        val prefs = chatPrefs()
        val d = resources.displayMetrics.density
        val email = accountEmail()
        val displayName = "Gemini"

        CloudSyncHelper.pullChatBackup(this) { key, hist ->
            if (!key.isNullOrBlank() && prefs.getString("key", "").isNullOrBlank()) {
                prefs.edit().putString("key", key).apply()
            }
            if (!hist.isNullOrBlank() && (prefs.getString("history", "[]") == "[]" || prefs.getString("history", "[]").isNullOrBlank())) {
                prefs.edit().putString("history", hist).apply()
                if (logRef?.childCount == 0 || history.length() == 0) {
                    runOnUiThread { reloadHistoryBubbles() }
                }
            }
        }

        val log = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        }
        logRef = log
        val scroll = ScrollView(this).apply { addView(log) }
        scrollRef = scroll
        val input = EditText(this).apply {
            hint = "Nhắn tin với Gemini"
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * d
                setColor(0xFFFFFFFF.toInt())
            }
            maxLines = 5
        }
        sendBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_send)
            background = sendBg(false)
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            contentDescription = "Gửi"
        }
        val keyBtn = TextView(this).apply {
            text = "Khóa"
            setTextColor(0xFF1A73E8.toInt())
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
            gravity = Gravity.CENTER
        }
        headerAvatar = avatar(true) as ImageView
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_back)
            background = null
            contentDescription = "Quay lại"
            setOnClickListener { finish() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 3 * d
            addView(back, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            addView(headerAvatar, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()).apply {
                marginStart = (4 * d).toInt()
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((10 * d).toInt(), 0, 0, 0)
                addView(TextView(context).apply {
                    text = displayName
                    textSize = 17f
                    setTextColor(0xFF1A1C28.toInt())
                    paint.isFakeBoldText = true
                })
                addView(TextView(context).apply {
                    text = email.ifBlank { "Đã đăng nhập" }
                    textSize = 12f
                    setTextColor(0xFF5F6368.toInt())
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(keyBtn)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            setBackgroundColor(0xFFF1F3F4.toInt())
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendBtn, LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F3F4.toInt())
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }

        reloadHistoryBubbles()

        keyBtn.setOnClickListener {
            val box = EditText(this).apply {
                hint = "Dán khóa API Gemini"
                setText(prefs.getString("key", "").orEmpty())
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Khóa Gemini")
                .setMessage("Lấy khóa miễn phí tại aistudio.google.com rồi dán vào đây. Khóa được lưu theo tài khoản Google.")
                .setView(box)
                .setPositiveButton("Lưu") { _, _ ->
                    val key = box.text.toString().trim()
                    prefs.edit().putString("key", key).apply()
                    CloudSyncHelper.pushChatBackup(this)
                    addBubble("Đã lưu khóa vào tài khoản Google.", mine = false, save = true, actions = false)
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        sendBtn.setOnClickListener {
            if (generating) {
                stopGeneration()
                return@setOnClickListener
            }
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            val key = prefs.getString("key", "").orEmpty()
            if (key.isBlank()) {
                addBubble("Chưa có khóa. Bấm Khóa và dán khóa API Gemini.", mine = false, save = true, actions = false)
                return@setOnClickListener
            }
            addBubble(q, mine = true, save = true, actions = false)
            input.setText("")
            val waiting = addBubble("Gemini đang trả lời…", mine = false, save = false, actions = false)
            (waiting.tag as? ImageView)?.let { startSpin(it) }
            headerAvatar?.let { startSpin(it) }
            beginGeneration()
            Thread {
                val created = createAlarmIfAsked(q)
                val answer = if (cancelled.get()) "" else askGemini(key, q, created)
                val shown = when {
                    cancelled.get() && answer.isBlank() -> created ?: "Đã dừng trả lời."
                    created == null -> answer
                    else -> "$created\n\n$answer"
                }
                runOnUiThread {
                    (waiting.tag as? ImageView)?.let { stopSpin(it) }
                    headerAvatar?.let { stopSpin(it) }
                    if (cancelled.get()) {
                        waiting.text = ChatMarkdown.format(shown)
                        persistAi(shown)
                        attachActions(waiting, shown)
                        endGeneration()
                    } else {
                        typeWords(waiting, shown) {
                            persistAi(shown)
                            attachActions(waiting, shown)
                            endGeneration()
                        }
                    }
                }
            }.start()
        }
        setContentView(root)
    }

    private fun reloadHistoryBubbles() {
        val log = logRef ?: return
        log.removeAllViews()
        while (history.length() > 0) history.remove(0)
        val saved = chatPrefs().getString("history", "[]").orEmpty()
        val old = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
        if (old.length() == 0) {
            addBubble("Chào bạn. Nhắn “đặt báo thức 6:07” hoặc “6 giờ 7” để mình lưu báo thức mới.", mine = false, save = true, actions = false)
        } else {
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                val mine = item.optInt("m") == 1
                addBubble(item.optString("t"), mine, save = false, actions = !mine)
                history.put(item)
            }
        }
    }

    private fun sendBg(stop: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (stop) 0xFFD93025.toInt() else 0xFF1A73E8.toInt())
    }

    private fun beginGeneration() {
        generating = true
        cancelled.set(false)
        sendBtn.setImageResource(R.drawable.ic_chat_stop)
        sendBtn.background = sendBg(true)
        sendBtn.contentDescription = "Dừng"
    }

    private fun endGeneration() {
        generating = false
        cancelled.set(false)
        activeConn = null
        sendBtn.setImageResource(R.drawable.ic_chat_send)
        sendBtn.background = sendBg(false)
        sendBtn.contentDescription = "Gửi"
    }

    private fun stopGeneration() {
        cancelled.set(true)
        try { activeConn?.disconnect() } catch (_: Exception) {}
        typeHandler.removeCallbacksAndMessages(null)
        headerAvatar?.let { stopSpin(it) }
        endGeneration()
    }

    private fun persistAi(text: String) {
        history.put(JSONObject().put("m", 0).put("t", text))
        while (history.length() > 60) history.remove(0)
        chatPrefs().edit().putString("history", history.toString()).apply()
        CloudSyncHelper.pushChatBackup(this@ChatActivity)
    }

    private fun addBubble(text: String, mine: Boolean, save: Boolean, actions: Boolean): TextView {
        val d = resources.displayMetrics.density
        val log = logRef ?: return TextView(this)
        val formatted = if (mine) text.trim() else ChatMarkdown.format(text)
        val tv = TextView(this).apply {
            this.text = formatted
            textSize = 16f
            setTextColor(if (mine) 0xFFFFFFFF.toInt() else 0xFF202124.toInt())
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = bubbleBg(mine, d)
            setTextIsSelectable(false)
            setOnLongClickListener {
                copyText(text)
                true
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or if (mine) Gravity.END else Gravity.START
            setPadding(0, (6 * d).toInt(), 0, (2 * d).toInt())
            val geminiView = if (!mine) (avatar(true) as ImageView).also { addView(it) } else null
            tv.tag = geminiView
            addView(tv, LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.74f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (mine) 0 else (8 * d).toInt()
                marginEnd = if (mine) (8 * d).toInt() else 0
            })
            if (mine) addView(avatar(false))
        }
        col.addView(row)
        if (actions && !mine) {
            col.addView(actionRow(text))
        }
        log.addView(col)
        if (save) {
            history.put(JSONObject().put("m", if (mine) 1 else 0).put("t", text))
            while (history.length() > 60) history.remove(0)
            chatPrefs().edit().putString("history", history.toString()).apply()
            CloudSyncHelper.pushChatBackup(this)
        }
        scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
        return tv
    }

    private fun attachActions(tv: TextView, raw: String) {
        val parent = tv.parent as? LinearLayout ?: return
        val col = parent.parent as? LinearLayout ?: return
        if (col.childCount > 1) return
        col.addView(actionRow(raw))
        scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun actionRow(raw: String): LinearLayout {
        val d = resources.displayMetrics.density
        fun iconBtn(icon: Int, desc: String, tint: Int = 0xFF5F6368.toInt(), click: (ImageButton) -> Unit): ImageButton {
            return ImageButton(this).apply {
                setImageResource(icon)
                background = null
                contentDescription = desc
                imageTintList = android.content.res.ColorStateList.valueOf(tint)
                setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
                layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt())
                setOnClickListener { click(this) }
            }
        }
        val like = iconBtn(R.drawable.ic_chat_like, "Thích") { btn ->
            btn.imageTintList = android.content.res.ColorStateList.valueOf(0xFF1A73E8.toInt())
            Toast.makeText(this, "Đã thích", Toast.LENGTH_SHORT).show()
        }
        val dislike = iconBtn(R.drawable.ic_chat_dislike, "Không thích") { btn ->
            btn.imageTintList = android.content.res.ColorStateList.valueOf(0xFFD93025.toInt())
            Toast.makeText(this, "Đã ghi nhận", Toast.LENGTH_SHORT).show()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding((44 * d).toInt(), 0, 0, (4 * d).toInt())
            addView(iconBtn(R.drawable.ic_chat_copy, "Sao chép") { copyText(raw) })
            addView(like)
            addView(dislike)
            addView(iconBtn(R.drawable.ic_chat_share, "Chia sẻ") { shareText(raw) })
        }
    }

    private fun bubbleBg(mine: Boolean, d: Float) = GradientDrawable().apply {
        val r = 18 * d
        if (mine) {
            cornerRadii = floatArrayOf(r, r, 6 * d, 6 * d, r, r, r, r)
            setColor(0xFF1A73E8.toInt())
        } else {
            cornerRadii = floatArrayOf(6 * d, 6 * d, r, r, r, r, r, r)
            setColor(0xFFFFFFFF.toInt())
        }
    }

    private fun copyText(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("chat", text))
        Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Chia sẻ"))
    }

    private fun typeWords(target: TextView, full: String, done: () -> Unit) {
        typeHandler.removeCallbacksAndMessages(null)
        val words = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            target.text = ChatMarkdown.format(full)
            done(); return
        }
        val built = StringBuilder()
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (cancelled.get() || isFinishing) {
                    target.text = ChatMarkdown.format(full)
                    done(); return
                }
                if (i >= words.size) {
                    target.text = ChatMarkdown.format(full)
                    done(); return
                }
                if (built.isNotEmpty()) built.append(' ')
                built.append(words[i])
                i++
                target.text = ChatMarkdown.format(built.toString())
                scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
                typeHandler.postDelayed(this, 45)
            }
        }
        target.text = ""
        typeHandler.post(step)
    }

    /** Luôn thêm báo thức mới — không đè báo cũ. */
    private fun createAlarmIfAsked(raw: String): String? {
        if (!AlarmTimeParser.looksLikeSetAlarm(raw)) return null
        val times = AlarmTimeParser.parseAll(raw)
        if (times.isEmpty()) return null
        val repo = AlarmRepository(this)
        val list = repo.getAlarms().toMutableList()
        val added = mutableListOf<String>()
        val daily = raw.contains("mỗi ngày") || raw.contains("hang ngay") || raw.contains("hàng ngày")
        for (t in times) {
            val alarm = Alarm(
                id = repo.getNextId(),
                hour = t.hour,
                minute = t.minute,
                isEnabled = true,
                label = "Báo thức AI",
                repeatMode = if (daily) Alarm.REPEAT_DAILY else Alarm.REPEAT_ONCE
            )
            list.add(alarm)
            AlarmScheduler.schedule(this, alarm)
            added.add("%02d:%02d".format(t.hour, t.minute))
        }
        repo.saveAlarms(list)
        return if (added.size == 1) {
            "Đã thêm báo thức mới ${added[0]} vào tab Báo thức."
        } else {
            "Đã thêm ${added.size} báo thức mới: ${added.joinToString(", ")} vào tab Báo thức."
        }
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
        cancelled.set(true)
        spinning.values.forEach { spinHandler.removeCallbacks(it) }
        spinning.clear()
        typeHandler.removeCallbacksAndMessages(null)
        try { activeConn?.disconnect() } catch (_: Exception) {}
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
                text = accountName().take(1).uppercase()
                textSize = 13f
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
        val saved = chatPrefs().getString("history", "[]").orEmpty()
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
            if (cancelled.get()) return ""
            try {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put(
                        "text",
                        "Bạn là Gemini trong app đồng hồ báo thức cho học sinh. Trả lời tiếng Việt, ngắn, dễ hiểu. Không nói tục. " +
                            "Có thể dùng **in đậm**, *nghiêng*, công thức \$E=mc^2\$ hoặc H2O khi cần. " +
                            "Nếu app đã lưu báo thức, xác nhận đúng giờ đó."
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
                activeConn = conn
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (cancelled.get()) {
                    try { conn.disconnect() } catch (_: Exception) {}
                    return ""
                }
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
                if (cancelled.get()) return ""
            }
        }
        return created ?: "Gemini đang bận. Đợi một lát rồi gửi lại."
    }
}
