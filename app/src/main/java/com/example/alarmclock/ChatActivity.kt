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
    private val chatVm by lazy { ChatViewModel(applicationContext) }
    private lateinit var sendBtn: ImageButton
    private var headerAvatar: ImageView? = null
    private var scrollRef: ScrollView? = null
    private var logRef: LinearLayout? = null
    private var tts: TtsHelper? = null
    private var ttsOn = false
    private lateinit var inputBox: EditText
    private lateinit var plusBtn: ImageButton
    private var webSearchOn = false
    private var pendingImageNote = ""
    private var pendingImageFile: java.io.File? = null
    private var speech: android.speech.SpeechRecognizer? = null
    private var lastHeard = ""
    private var listeningMic = false
    private var menuBtn: ImageButton? = null
    private var menuPopup: android.widget.PopupWindow? = null
    private var errorCard: android.view.View? = null
    private var statusAnim: android.animation.ValueAnimator? = null
    private var statusView: TextView? = null

    private val frames by lazy {
        IntArray(39) { resources.getIdentifier("gemini_loop_%02d".format(it), "drawable", packageName) }
    }
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        GoogleSignInHelper.handleResult(this, result.data)
        if (signedIn()) {
            CloudSyncHelper.syncOnLogin(this)
            showChat()
        } else showGate("Chưa đăng nhập được. Thử lại.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hold = TextView(this).apply {
            text = "Đang mở chat…"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF202124.toInt())
        }
        setContentView(hold)
        window.decorView.post {
            try {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                if (signedIn()) showChat() else showGate("Chat cần đăng nhập Google để lưu lịch sử.")
            } catch (e: Throwable) {
                hold.text = "Chat chưa mở được.\n" + (e.message ?: "Lỗi")
            }
        }
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
        try { showChatBody() } catch (e: Exception) {
            showGate("Chat lỗi khi mở: " + (e.message ?: "không rõ"))
        }
    }

    private fun showChatBody() {
        val prefs = chatPrefs()
        val d = resources.displayMetrics.density
        val email = accountEmail()
        val displayName = "Gemini"

        CloudSyncHelper.pullChatBackup(this) { key, hist ->
            if (!key.isNullOrBlank() && prefs.getString("key", "").isNullOrBlank()) {
                prefs.edit().putString("key", key).apply()
            }
            if (!hist.isNullOrBlank() && hist != "[]") {
                val local = prefs.getString("history", "[]").orEmpty()
                if (local == "[]" || local.length < hist.length) prefs.edit().putString("history", hist).apply()
            }
            runOnUiThread {
                reloadHistoryBubbles()
                if (!key.isNullOrBlank() || (hist != null && hist != "[]")) {
                    android.widget.Toast.makeText(this, "Đã kéo lại khóa và lịch sử chat", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        val log = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (28 * d).toInt())
        }
        logRef = log
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(log)
        }
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
        inputBox = input
        plusBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_gemini_sparkle)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), 0xFFDADCE0.toInt())
            }
            imageTintList = null
            contentDescription = "Thêm"
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setOnClickListener { showPlusMenu(this) }
        }
        val micBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_mic)
            background = null
            contentDescription = "Nói"
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
            setOnClickListener { toggleChatMic() }
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
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_chat_menu)
                background = null
                contentDescription = "Menu chat"
                setOnClickListener { showChatMenu() }
                menuBtn = this
            }, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            addView(TextView(context).apply {
                text = "Live"
                setTextColor(0xFF1A73E8.toInt())
                setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
                gravity = Gravity.CENTER
                setOnClickListener {
                    startActivity(Intent(this@ChatActivity, GeminiLiveActivity::class.java))
                }
            })
            addView(keyBtn)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
            setBackgroundColor(0xFFF1F3F4.toInt())
            addView(plusBtn, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()).apply {
                marginEnd = (8 * d).toInt()
            })
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(micBtn, LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt()))
            addView(sendBtn, LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt()).apply {
                marginStart = (8 * d).toInt()
            })
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), 0)
            addView(chip("Đặt báo thức", "Đặt báo thức 6:07 nhãn Toán"))
            addView(chip("Viết prompt học", "Viết prompt ôn bài, bọc trong ```prompt"))
            addView(chip("Tóm tắt bài", "Tóm tắt bài học ngắn, dễ nhớ"))
            addView(chip("Ôn tập", "Đặt 5 câu ôn tập giúp mình"))
        }
        val chipScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF1F3F4.toInt())
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(chipScroll)
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
            submitPrompt(input.text.toString().trim(), addUserBubble = true)
        }
        setContentView(root)
    }

    private fun reloadHistoryBubbles() {
        val log = logRef ?: return
        log.removeAllViews()
        while (history.length() > 0) history.remove(0)
        val saved = chatPrefs().getString("history", "[]").orEmpty()
        val old = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
        addPinnedBanner()
        if (old.length() == 0) {
            addBubble("Chào bạn. Nhắn “đặt báo thức 6:07 nhãn Toán” hoặc bấm gợi ý phía dưới.", mine = false, save = false, actions = false)
        } else {
            var lastDay = ""
            for (i in 0 until old.length()) {
                val item = old.getJSONObject(i)
                val day = dayLabel(item.optLong("ts", 0L))
                if (day != lastDay) {
                    lastDay = day
                    addDayHeader(day)
                }
                val mine = item.optInt("m") == 1
                addBubble(item.optString("t"), mine, save = false, actions = !mine)
                history.put(item)
            }
        }
    }

    private fun dayLabel(ts: Long): String {
        if (ts <= 0L) return "Trước đây"
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        val today = fmt.format(java.util.Date())
        val that = fmt.format(java.util.Date(ts))
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yest = fmt.format(cal.time)
        return when (that) {
            today -> "Hôm nay"
            yest -> "Hôm qua"
            else -> java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }
    }

    private fun addDayHeader(label: String) {
        val log = logRef ?: return
        val d = resources.displayMetrics.density
        log.addView(TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF5F6368.toInt())
            setPadding(0, (10 * d).toInt(), 0, (6 * d).toInt())
        })
    }

    private fun addPinnedBanner() {
        val log = logRef ?: return
        val pins = ChatCloudStore.pins(this)
        if (pins.length() == 0) return
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * d
                setColor(0xFFFFF8E1.toInt())
            }
        }
        box.addView(TextView(this).apply {
            text = "Đã ghim"
            textSize = 13f
            paint.isFakeBoldText = true
            setTextColor(0xFFB06000.toInt())
        })
        for (i in 0 until pins.length().coerceAtMost(3)) {
            box.addView(TextView(this).apply {
                text = pins.optString(i).take(90)
                textSize = 13f
                setTextColor(0xFF3C4043.toInt())
                setPadding(0, (4 * d).toInt(), 0, 0)
            })
        }
        log.addView(box)
    }

    private fun chip(label: String, fill: String): TextView {
        val d = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF1A73E8.toInt())
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(0xFFE8F0FE.toInt())
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (8 * d).toInt()
            layoutParams = lp
            setOnClickListener { inputBox.setText(fill) }
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
        try { chatVm.disconnect() } catch (_: Exception) {}
        typeHandler.removeCallbacksAndMessages(null)
        headerAvatar?.let { stopSpin(it) }
        endGeneration()
    }

    private fun submitPrompt(q: String, addUserBubble: Boolean) {
        if (q.isEmpty() || generating) return
        val shot = pendingImageFile
        pendingImageFile = null
        val extra = buildString {
            if (pendingImageNote.isNotBlank()) append(pendingImageNote).append(' ')
        }
        pendingImageNote = ""
        val q2 = (extra + q).trim()
        val key = chatPrefs().getString("key", "").orEmpty()
        if (key.isBlank()) {
            addBubble("Chưa có khóa. Bấm Khóa và dán khóa API Gemini.", mine = false, save = true, actions = false)
            return
        }
        removeErrorCard()
        if (addUserBubble) {
            addBubble(q, mine = true, save = true, actions = false)
            if (shot != null) showImage(shot, mine = true)
            inputBox.setText("")
        }
        val needSearch = webSearchOn || looksCurrent(q)
        val first = if (needSearch) "Đang tìm kiếm…" else "Đang suy nghĩ…"
        val waiting = addBubble(first, mine = false, save = false, actions = false)
        startStatusShine(waiting, first)
        (waiting.tag as? ImageView)?.let { startSpin(it) }
        headerAvatar?.let { startSpin(it) }
        beginGeneration()
        Thread {
            val searched = if (webSearchOn || looksCurrent(q)) searchWeb(q) else ""
            runOnUiThread { setStatus(waiting, "Đang suy nghĩ…") }
            val asked = if (searched.isBlank()) q2 else q2 + "\n\nKết quả tìm web thật (chỉ dựa vào đoạn này, nếu tin nói đã ngừng hoặc khai tử thì phải trả lời đã ngừng, không nói còn hoạt động. Nếu không đủ thì nói chưa đủ tin):\n" + searched
            val jpeg = shot?.takeIf { it.exists() }?.readBytes()
            val turn = chatVm.runTurn(key, asked, history.toString(), { cancelled.get() }, jpeg)
            runOnUiThread { setStatus(waiting, "Đang trả lời…") }
            val shown = turn.answer
            val err = turn.error
            if (err == null) {
                try {
                    history.put(JSONObject().put("m", 0).put("t", shown).put("ts", System.currentTimeMillis()))
                    while (history.length() > 80) history.remove(0)
                    ChatCloudStore.snapshotCurrent(applicationContext, history)
                } catch (_: Exception) {}
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                (waiting.tag as? ImageView)?.let { stopSpin(it) }
                headerAvatar?.let { stopSpin(it) }
                if (err != null && err.canRetry) {
                    hideWaitingRow(waiting)
                    showErrorCard(err)
                    endGeneration()
                    return@runOnUiThread
                }
                if (cancelled.get()) {
                    waiting.text = ChatMarkdown.format(shown)
                    attachActions(waiting, shown)
                    endGeneration()
                } else {
                    typeWords(waiting, shown) {
                        attachActions(waiting, shown)
                        endGeneration()
                    }
                }
            }
        }.start()
    }

    private fun hideWaitingRow(waiting: TextView) {
        val row = waiting.parent as? LinearLayout
        val col = row?.parent as? LinearLayout
        (col?.parent as? LinearLayout)?.removeView(col)
    }

    private fun removeErrorCard() {
        errorCard?.let { card ->
            (card.parent as? LinearLayout)?.removeView(card)
        }
        errorCard = null
    }

    private fun showErrorCard(err: ChatApiError) {
        val log = logRef ?: return
        removeErrorCard()
        val card = layoutInflater.inflate(R.layout.item_chat_error, log, false)
        card.findViewById<TextView>(R.id.txtError).text = err.errorMessage
        card.findViewById<android.widget.Button>(R.id.btnRetry).setOnClickListener {
            val prompt = chatVm.lastFailedPrompt ?: err.rawPrompt
            submitPrompt(prompt, addUserBubble = false)
        }
        log.addView(card)
        errorCard = card
        scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun persistAi(text: String) {
        history.put(JSONObject().put("m", 0).put("t", text).put("ts", System.currentTimeMillis()))
        while (history.length() > 80) history.remove(0)
        ChatCloudStore.snapshotCurrent(this, history)
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
            maxWidth = (resources.displayMetrics.widthPixels - (88 * d).toInt()).coerceAtLeast((180 * d).toInt())
            setTag(R.id.chat_full_text, text)
            enablePartialCopy()
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, (4 * d).toInt(), 0, (10 * d).toInt())
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or if (mine) Gravity.END else Gravity.START
            val geminiView = if (!mine) (avatar(true) as ImageView).also { addView(it) } else null
            tv.tag = geminiView
            addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
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
            addPromptCopyChips(col, text)
        }
        log.addView(col)
        if (save) {
            history.put(JSONObject().put("m", if (mine) 1 else 0).put("t", text).put("ts", System.currentTimeMillis()))
            while (history.length() > 80) history.remove(0)
            ChatCloudStore.snapshotCurrent(this, history)
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
        col.addView(fileRow(raw))
        addPromptCopyChips(col, raw)
        scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private val idleTint = 0xFF5F6368.toInt()
    private val likeTint = 0xFF1A73E8.toInt()
    private val dislikeTint = 0xFFD93025.toInt()

    private fun actionRow(raw: String): LinearLayout {
        val d = resources.displayMetrics.density
        val ripple = try {
            android.util.TypedValue().also {
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId
        } catch (_: Exception) { 0 }
        fun iconBtn(icon: Int, desc: String): ImageButton {
            return ImageButton(this).apply {
                setImageResource(icon)
                if (ripple != 0) setBackgroundResource(ripple) else background = null
                contentDescription = desc
                imageTintList = android.content.res.ColorStateList.valueOf(idleTint)
                setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
                layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
            }
        }
        val hint = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF5F6368.toInt())
            setPadding((8 * d).toInt(), 0, 0, 0)
        }
        val like = iconBtn(R.drawable.ic_chat_like, "Thích")
        val dislike = iconBtn(R.drawable.ic_chat_dislike, "Không thích")
        var vote = 0
        fun paint() {
            like.imageTintList = android.content.res.ColorStateList.valueOf(if (vote == 1) likeTint else idleTint)
            dislike.imageTintList = android.content.res.ColorStateList.valueOf(if (vote == -1) dislikeTint else idleTint)
        }
        fun flash(msg: String) {
            hint.text = msg
            hint.animate().cancel()
            hint.alpha = 1f
            hint.postDelayed({
                hint.animate().alpha(0f).setDuration(250).start()
            }, 1200)
        }
        like.setOnClickListener {
            vote = if (vote == 1) 0 else 1
            paint()
            flash(if (vote == 1) "Đã thích" else "Đã bỏ thích")
        }
        dislike.setOnClickListener {
            vote = if (vote == -1) 0 else -1
            paint()
            flash(if (vote == -1) "Không thích" else "Đã bỏ đánh giá")
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding((44 * d).toInt(), (2 * d).toInt(), 0, (6 * d).toInt())
            addView(iconBtn(R.drawable.ic_chat_copy, "Sao chép toàn bộ").also {
                it.setOnClickListener { copyText(raw) }
            })
            addView(iconBtn(R.drawable.ic_chat_speak, "Đọc văn bản").also { btn ->
                btn.setOnClickListener { toggleSpeak(raw, btn) }
            })
            addView(like)
            addView(dislike)
            addView(iconBtn(R.drawable.ic_chat_share, "Chia sẻ").also {
                it.setOnClickListener { shareText(raw) }
            })
            addView(iconBtn(R.drawable.ic_chat_pin, "Ghim").also { btn ->
                if (ChatCloudStore.isPinned(this@ChatActivity, raw)) {
                    btn.imageTintList = android.content.res.ColorStateList.valueOf(0xFFF9AB00.toInt())
                }
                btn.setOnClickListener {
                    val on = ChatCloudStore.togglePin(this@ChatActivity, raw)
                    btn.imageTintList = android.content.res.ColorStateList.valueOf(
                        if (on) 0xFFF9AB00.toInt() else idleTint
                    )
                    flash(if (on) "Đã ghim" else "Bỏ ghim")
                }
            })
            addView(hint)
        }
    }

    /** Giữ chữ → thanh Sao chép / Chọn tất cả. */
    private fun TextView.enablePartialCopy() {
        setTextIsSelectable(true)
        val mode = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.clear()
                menu.add(0, android.R.id.copy, 0, "Sao chép")
                menu.add(0, android.R.id.selectAll, 1, "Chọn tất cả")
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.clear()
                menu.add(0, android.R.id.copy, 0, "Sao chép")
                menu.add(0, android.R.id.selectAll, 1, "Chọn tất cả")
                return true
            }
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                val full = (getTag(R.id.chat_full_text) as? String) ?: text.toString()
                when (item.itemId) {
                    android.R.id.copy -> {
                        copyText(full)
                        mode.finish()
                        return true
                    }
                    android.R.id.selectAll -> {
                        val span = text
                        if (span is android.text.Spannable) {
                            android.text.Selection.setSelection(span, 0, span.length)
                        }
                        return true
                    }
                }
                return false
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        }
        customSelectionActionModeCallback = mode
        customInsertionActionModeCallback = mode
        setOnLongClickListener {
            val span = text
            if (span is android.text.Spannable && selectionEnd <= selectionStart) {
                android.text.Selection.setSelection(span, 0, span.length)
            }
            false
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

    private fun addPromptCopyChips(col: LinearLayout, raw: String) {
        val blocks = GeminiChatPolicy.extractCopyBlocks(raw)
        if (blocks.isEmpty()) return
        val d = resources.displayMetrics.density
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((44 * d).toInt(), 0, (8 * d).toInt(), (6 * d).toInt())
        }
        blocks.forEach { (title, body) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12 * d
                    setColor(0xFFE8F0FE.toInt())
                    setStroke((1 * d).toInt(), 0xFFAECBFA.toInt())
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * d).toInt()
                layoutParams = lp
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 13f
                paint.isFakeBoldText = true
                setTextColor(0xFF174EA6.toInt())
            })
            card.addView(TextView(this).apply {
                text = body.take(280)
                textSize = 14f
                setTextColor(0xFF202124.toInt())
                setPadding(0, (6 * d).toInt(), 0, (8 * d).toInt())
            })
            card.addView(TextView(this).apply {
                text = "Sao chép prompt"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 16 * d
                    setColor(0xFF1A73E8.toInt())
                }
                setOnClickListener { copyText(body) }
            })
            wrap.addView(card)
        }
        col.addView(wrap)
    }

    private fun toggleSpeak(text: String, btn: ImageButton) {
        if (ttsOn) {
            tts?.stop()
            ttsOn = false
            btn.imageTintList = android.content.res.ColorStateList.valueOf(idleTint)
            return
        }
        if (tts == null) {
            tts = TtsHelper(this).also { it.useMediaStream = true }
        }
        ttsOn = true
        btn.imageTintList = android.content.res.ColorStateList.valueOf(likeTint)
        tts?.onDone = {
            ttsOn = false
            btn.imageTintList = android.content.res.ColorStateList.valueOf(idleTint)
        }
        val clean = text.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("\\s+"), " ").trim()
        tts?.speak(clean)
    }

    private fun copyText(text: String) {
        val full = text.trim()
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("chat", full))
        Toast.makeText(this, "Đã sao chép cả bài (${full.length} chữ)", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Chia sẻ"))
    }


    private var statusBars: LinearLayout? = null

    private fun setStatus(tv: TextView, label: String) {
        if (statusView !== tv) return
        tv.text = label
    }

    private fun startStatusShine(tv: TextView, label: String) {
        stopStatusShine(tv)
        statusView = tv
        tv.setTextColor(0xFFE0E0E0.toInt())
        tv.text = label
        val d = tv.resources.displayMetrics.density
        val bars = LinearLayout(tv.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * d).toInt(), 0, 0)
            listOf(168, 112).forEach { wDp ->
                addView(android.view.View(tv.context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8 * d
                        setColor(0xFFE0E0E0.toInt())
                    }
                }, LinearLayout.LayoutParams((wDp * d).toInt(), (12 * d).toInt()).apply {
                    topMargin = (6 * d).toInt()
                })
            }
        }
        (tv.parent as? LinearLayout)?.addView(bars)
        statusBars = bars
        statusAnim = android.animation.ValueAnimator.ofFloat(-0.4f, 1.4f).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { anim ->
                val w = tv.width.toFloat().coerceAtLeast(1f)
                val x = (anim.animatedValue as Float) * w
                val shader = android.graphics.LinearGradient(
                    x, 0f, x + w * 0.45f, 0f,
                    intArrayOf(0xFFE0E0E0.toInt(), 0xFFF5F5F5.toInt(), 0xFFE0E0E0.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                tv.paint.shader = shader
                tv.invalidate()
            }
            start()
        }
    }

    private fun stopStatusShine(tv: TextView?) {
        statusView?.removeCallbacks(null)
        statusView = null
        statusAnim?.cancel()
        statusAnim = null
        statusBars?.let { (it.parent as? LinearLayout)?.removeView(it) }
        statusBars = null
        tv?.paint?.shader = null
        tv?.setTextColor(0xFF202124.toInt())
        tv?.invalidate()
    }

    private fun typeWords(target: TextView, full: String, done: () -> Unit) {
        stopStatusShine(target)
        typeHandler.removeCallbacksAndMessages(null)
        val words = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 8) {
            target.text = ChatMarkdown.format(full)
            done(); return
        }
        val built = StringBuilder()
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (cancelled.get() || isFinishing || i >= words.size) {
                    target.text = ChatMarkdown.format(full)
                    done(); return
                }
                val end = (i + 5).coerceAtMost(words.size)
                while (i < end) {
                    if (built.isNotEmpty()) built.append(' ')
                    built.append(words[i])
                    i++
                }
                target.text = built
                if (i % 15 == 0 || i >= words.size) {
                    scrollRef?.post { scrollRef?.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                typeHandler.postDelayed(this, 70)
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
                label = alarmLabelFrom(raw),
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

    private fun alarmLabelFrom(raw: String): String {
        val m = Regex("(?i)(?:nhãn|tên|gọi là)\\s+([^,.!?]{2,24})").find(raw)
        val name = m?.groupValues?.get(1)?.trim().orEmpty()
        return if (name.isNotBlank()) name else "Báo thức AI"
    }

    private fun showChatMenu() {
        val anchor = menuBtn ?: return
        if (menuPopup?.isShowing == true) {
            menuPopup?.dismiss()
            return
        }
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 12 * d
                setColor(0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), 0xFFE0E0E0.toInt())
            }
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            elevation = 10 * d
        }
        fun row(label: String, action: () -> Unit) {
            box.addView(TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(0xFF202124.toInt())
                setPadding((20 * d).toInt(), (12 * d).toInt(), (28 * d).toInt(), (12 * d).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    menuPopup?.dismiss()
                    action()
                }
            })
        }
        row("Chat mới") { newChat() }
        row("Chat cũ") { showOldChats() }
        row("Tóm tắt") { summarizeChat() }
        row("Giọng đọc") { voiceDialog() }
        row("Tin đã ghim") { showPins() }
        row("Xóa đoạn chat này") { clearThisChat() }
        val pop = android.widget.PopupWindow(
            box,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12 * d
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        menuPopup = pop
        val xOff = -(120 * d).toInt()
        pop.showAsDropDown(anchor, xOff, (4 * d).toInt())
    }

    private fun newChat() {
        ChatCloudStore.startNewChat(this, history)
        reloadHistoryBubbles()
        Toast.makeText(this, "Chat mới", Toast.LENGTH_SHORT).show()
    }

    private fun showOldChats() {
        ChatCloudStore.snapshotCurrent(this, history)
        val all = ChatCloudStore.sessions(this)
        if (all.length() == 0) {
            Toast.makeText(this, "Chưa có chat cũ", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = Array(all.length()) { i ->
            all.optJSONObject(i)?.optString("title").orEmpty().ifBlank { "Chat ${i + 1}" }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chat cũ")
            .setItems(titles) { _, which ->
                val id = all.optJSONObject(which)?.optString("id").orEmpty()
                if (id.isNotBlank()) {
                    ChatCloudStore.openSession(this, id, history)
                    reloadHistoryBubbles()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun clearThisChat() {
        while (history.length() > 0) history.remove(0)
        ChatCloudStore.snapshotCurrent(this, history)
        reloadHistoryBubbles()
    }

    private fun showPins() {
        val pins = ChatCloudStore.pins(this)
        if (pins.length() == 0) {
            Toast.makeText(this, "Chưa ghim tin nào", Toast.LENGTH_SHORT).show()
            return
        }
        val items = Array(pins.length()) { pins.optString(it).take(60) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tin đã ghim")
            .setItems(items) { _, which -> copyText(pins.optString(which)) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun voiceDialog() {
        val male = AppSettings.isChatTtsMale(this)
        val rate = AppSettings.getChatTtsRate(this)
        val items = arrayOf("Giọng nam", "Giọng nữ", "Đọc chậm", "Đọc vừa", "Đọc nhanh")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Giọng đọc (đang: ${if (male) "nam" else "nữ"}, tốc độ ${"%.1f".format(rate)})")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> AppSettings.setChatTtsMale(this, true)
                    1 -> AppSettings.setChatTtsMale(this, false)
                    2 -> AppSettings.setChatTtsRate(this, 0.8f)
                    3 -> AppSettings.setChatTtsRate(this, 0.95f)
                    4 -> AppSettings.setChatTtsRate(this, 1.15f)
                }
                try { tts?.shutdown() } catch (_: Exception) {}
                tts = TtsHelper(this).also { it.useMediaStream = true }
                Toast.makeText(this, "Đã lưu giọng đọc", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun summarizeChat() {
        val key = chatPrefs().getString("key", "").orEmpty()
        if (key.isBlank()) {
            Toast.makeText(this, "Chưa có khóa Gemini", Toast.LENGTH_SHORT).show()
            return
        }
        val clip = StringBuilder()
        val start = (history.length() - 12).coerceAtLeast(0)
        for (i in start until history.length()) {
            val o = history.optJSONObject(i) ?: continue
            clip.append(if (o.optInt("m") == 1) "Bạn: " else "AI: ")
            clip.append(o.optString("t")).append('\n')
        }
        if (clip.isBlank()) {
            Toast.makeText(this, "Chưa có gì để tóm tắt", Toast.LENGTH_SHORT).show()
            return
        }
        val waiting = addBubble("Đang tóm tắt cuộc chat…", mine = false, save = false, actions = false)
        beginGeneration()
        Thread {
            val answer = askGemini(key, "Tóm tắt cuộc chat sau thành 5 ý ngắn, dễ nhớ:\n$clip", null)
            runOnUiThread {
                typeWords(waiting, answer.ifBlank { "Chưa tóm tắt được." }) {
                    persistAi(waiting.text.toString())
                    attachActions(waiting, answer)
                    endGeneration()
                }
            }
        }.start()
    }

    private fun toggleChatMic() {
        if (listeningMic) {
            try { speech?.stopListening() } catch (_: Exception) {}
            if (lastHeard.length >= 2) {
                inputBox.setText(lastHeard)
                listeningMic = false
            }
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 72)
            return
        }
        startChatListen()
    }

    private fun startChatListen() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Máy không nhận giọng nói", Toast.LENGTH_SHORT).show()
            return
        }
        lastHeard = ""
        try { speech?.destroy() } catch (_: Exception) {}
        val rec = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        speech = rec
        rec.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                inputBox.hint = "Đang nghe…"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listeningMic = false }
            override fun onError(error: Int) {
                listeningMic = false
                inputBox.hint = "Nhắn tin với Gemini"
                if (lastHeard.length >= 2) inputBox.setText(lastHeard)
            }
            override fun onResults(results: android.os.Bundle?) {
                listeningMic = false
                inputBox.hint = "Nhắn tin với Gemini"
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().ifBlank { lastHeard }
                if (text.isNotBlank()) inputBox.setText(text)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val text = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    lastHeard = text
                    inputBox.setText(text)
                }
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        listeningMic = true
        rec.startListening(intent)
    }

    override fun onDestroy() {
        cancelled.set(true)
        spinning.values.forEach { spinHandler.removeCallbacks(it) }
        spinning.clear()
        typeHandler.removeCallbacksAndMessages(null)
        try { activeConn?.disconnect() } catch (_: Exception) {}
        try { speech?.destroy() } catch (_: Exception) {}
        try { menuPopup?.dismiss() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
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
                        GeminiChatPolicy.SYSTEM
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

    private fun fileRow(raw: String): LinearLayout {
        val d = resources.displayMetrics.density
        fun chip(label: String, click: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF1A73E8.toInt())
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * d
                setColor(0xFFE8F0FE.toInt())
            }
            setOnClickListener { click() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * d).toInt(), 0, 0)
            addView(chip("TXT") { exportDoc(raw, "txt") })
            addView(chip("PDF") { exportDoc(raw, "pdf") }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() })
            addView(chip("Tài liệu") { exportDoc(raw, "doc") }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * d).toInt() })
        }
    }

    private fun showPlusMenu(anchor: View) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                setColor(0xFFFFFFFF.toInt())
            }
        }
        fun row(label: String, click: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(0xFF202124.toInt())
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                menuPopup?.dismiss()
                click()
            }
        }
        box.addView(row("Thư viện ảnh") {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 71)
        })
        box.addView(row("Camera") { openCamera() })
        val web = TextView(this).apply {
            text = if (webSearchOn) "Tìm kiếm trang web: Bật" else "Tìm kiếm trang web: Tắt"
            textSize = 16f
            setTextColor(0xFF1A73E8.toInt())
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                webSearchOn = !webSearchOn
                text = if (webSearchOn) "Tìm kiếm trang web: Bật" else "Tìm kiếm trang web: Tắt"
                Toast.makeText(this@ChatActivity, if (webSearchOn) "Đã bật tìm web" else "Đã tắt tìm web", Toast.LENGTH_SHORT).show()
            }
        }
        box.addView(web)
        box.addView(row("Tạo ảnh") { createImageCard() })
        val pop = android.widget.PopupWindow(box, (240 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT, true)
        pop.elevation = 8 * d
        pop.showAsDropDown(anchor, 0, (-220 * d).toInt())
        menuPopup = pop
    }

    private var cameraFile: java.io.File? = null

    private fun openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 41)
            Toast.makeText(this, "Hãy cho phép camera rồi bấm lại", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = java.io.File(cacheDir, "chat_images").apply { mkdirs() }
            val file = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri), 72)
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageCard() {
        val topic = inputBox.text?.toString()?.trim().orEmpty().ifBlank { "Minh họa bài học hôm nay" }
        val key = chatPrefs().getString("key", "").orEmpty()
        val ref = pendingImageFile
        if (key.isBlank()) {
            Toast.makeText(this, "Cần khóa Gemini để AI vẽ ảnh", Toast.LENGTH_SHORT).show()
            return
        }
        addBubble("Đang vẽ: $topic", mine = true, save = false, actions = false)
        val waiting = addBubble("Đang tạo…", mine = false, save = false, actions = false)
        startStatusShine(waiting, "Đang tạo…")
        Thread {
            val file = drawWithGemini(key, topic, ref)
            runOnUiThread {
                stopStatusShine(waiting)
                if (file == null) {
                    waiting.text = "Chưa tạo được ảnh. Thử lại hoặc viết rõ hơn muốn vẽ gì."
                } else {
                    waiting.text = "Ảnh đã tạo"
                    showImage(file, mine = false)
                    pendingImageFile = null
                    inputBox.setText("")
                }
            }
        }.start()
    }

    private fun drawWithGemini(key: String, topic: String, ref: java.io.File?): java.io.File? {
        return try {
            val parts = org.json.JSONArray().put(org.json.JSONObject().put(
                "text",
                "Vẽ một ảnh minh họa phù hợp học sinh, rõ ràng, không chữ bậy. Chủ đề: $topic"
            ))
            if (ref != null && ref.exists()) {
                val b64 = android.util.Base64.encodeToString(ref.readBytes(), android.util.Base64.NO_WRAP)
                parts.put(org.json.JSONObject().put("inline_data", org.json.JSONObject()
                    .put("mime_type", "image/jpeg").put("data", b64)))
            }
            val body = org.json.JSONObject()
                .put("contents", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", org.json.JSONObject().put("responseModalities", org.json.JSONArray().put("TEXT").put("IMAGE")))
            val models = arrayOf("gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation")
            var raw = ""
            for (modelName in models) {
                val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$key")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 20000
                    readTimeout = 60000
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
                if (code in 200..299 && raw.contains("inlineData")) break
            }
            val json = org.json.JSONObject(raw)
            val partsOut = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            var b64 = ""
            for (i in 0 until partsOut.length()) {
                val p = partsOut.getJSONObject(i)
                val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
                if (inline != null) b64 = inline.optString("data")
            }
            if (b64.isBlank()) return null
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val dir = java.io.File(getExternalFilesDir(null), "chat_docs").apply { mkdirs() }
            val file = java.io.File(dir, "anh_${System.currentTimeMillis()}.png")
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun showImage(file: java.io.File, mine: Boolean) {
        val log = logRef ?: return
        val d = resources.displayMetrics.density
        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
        val iv = android.widget.ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            background = bubbleBg(mine, d)
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
            setOnClickListener { shareFile(file, "image/png") }
        }
        val wrap = LinearLayout(this).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt())
            addView(iv, LinearLayout.LayoutParams((260 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        log.addView(wrap)
        scrollRef?.post { scrollRef?.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    private fun looksCurrent(q: String): Boolean {
        val s = q.lowercase()
        return listOf("còn hoạt động", "khai tử", "đã ngừng", "hiện tại", "mới nhất", "còn không", "shutdown", "discontinued").any { s.contains(it) }
    }

    private fun searchWeb(query: String): String {
        val bits = mutableListOf<String>()
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val conn = (java.net.URL("https://html.duckduckgo.com/html/?q=$q").openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val html = conn.inputStream.bufferedReader().readText()
            Regex("result__snippet[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach {
                val snip = it.groupValues[1].replace(Regex("<[^>]+>"), " ").replace("&", "&").replace("&#x27;", "'").trim()
                if (snip.length > 20) bits.add(snip)
            }
        } catch (_: Exception) {}
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val conn = (java.net.URL("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&srlimit=2").openConnection() as java.net.HttpURLConnection).apply {
                setRequestProperty("User-Agent", "AlarmClock/1.0")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val raw = conn.inputStream.bufferedReader().readText()
            Regex("\"snippet\":\"(.*?)\"").findAll(raw).forEach {
                bits.add(it.groupValues[1].replace("\\n", " ").replace("\"", "\""))
            }
        } catch (_: Exception) {}
        return bits.distinct().take(5).joinToString("\n") { "• $it" }
    }

    private fun exportDoc(raw: String, kind: String) {
        try {
            val dir = java.io.File(getExternalFilesDir(null), "chat_docs").apply { mkdirs() }
            val stamp = System.currentTimeMillis()
            val file = when (kind) {
                "pdf" -> {
                    val f = java.io.File(dir, "bai_$stamp.pdf")
                    val doc = android.graphics.pdf.PdfDocument()
                    val paint = android.graphics.Paint().apply { textSize = 14f; color = 0xFF202124.toInt() }
                    val lines = raw.replace("\r", "").split('\n').flatMap { line ->
                        if (line.length <= 70) listOf(line) else line.chunked(70)
                    }
                    var pageNo = 1
                    var y = 48f
                    var page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                    for (line in lines) {
                        if (y > 800f) {
                            doc.finishPage(page)
                            pageNo++
                            page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                            y = 48f
                        }
                        page.canvas.drawText(line, 40f, y, paint)
                        y += 20f
                    }
                    doc.finishPage(page)
                    java.io.FileOutputStream(f).use { doc.writeTo(it) }
                    doc.close()
                    f
                }
                "doc" -> {
                    val f = java.io.File(dir, "bai_$stamp.doc")
                    val html = "<html><body><pre>" + raw.replace("&", "&").replace("<", "<") + "</pre></body></html>"
                    f.writeText(html)
                    f
                }
                else -> {
                    val f = java.io.File(dir, "bai_$stamp.txt")
                    f.writeText(raw)
                    f
                }
            }
            val mime = when (kind) {
                "pdf" -> "application/pdf"
                "doc" -> "application/msword"
                else -> "text/plain"
            }
            shareFile(file, mime)
        } catch (_: Exception) {
            Toast.makeText(this, "Không tạo được file", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 71 && resultCode == RESULT_OK && data?.data != null) {
            pendingImageFile = copyPicked(data.data!!)
            pendingImageNote = "Người dùng gửi kèm một ảnh. Hãy nhìn ảnh và trả lời."
            if (::inputBox.isInitialized) {
                inputBox.setText(inputBox.text.toString().ifBlank { "Nhìn ảnh này và giải thích giúp mình." })
            }
            Toast.makeText(this, "Đã thêm ảnh, bấm gửi để AI xem", Toast.LENGTH_SHORT).show()
        }
        if (requestCode == 72 && resultCode == RESULT_OK) {
            pendingImageFile = cameraFile
            pendingImageNote = "Người dùng gửi kèm một ảnh vừa chụp. Hãy nhìn ảnh và trả lời."
            if (::inputBox.isInitialized) {
                inputBox.setText(inputBox.text.toString().ifBlank { "Nhìn ảnh vừa chụp và giải thích giúp mình." })
            }
            Toast.makeText(this, "Đã thêm ảnh camera, bấm gửi để AI xem", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyPicked(uri: android.net.Uri): java.io.File? {
        return try {
            val dir = java.io.File(cacheDir, "chat_images").apply { mkdirs() }
            val file = java.io.File(dir, "lib_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.takeIf { it.exists() && it.length() > 0 }
        } catch (_: Exception) { null }
    }

    private fun shareFile(file: java.io.File, mime: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Mở file"))
    }

}
