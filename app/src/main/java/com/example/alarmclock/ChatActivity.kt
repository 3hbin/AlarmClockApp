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
    /** Bật/tắt cho AI tìm Google + đọc web (lưu lại, mặc định bật). */
    private var webSearchOn: Boolean
        get() = AppSettings.isChatWebSearch(this)
        set(v) = AppSettings.setChatWebSearch(this, v)
    private var pendingImageNote = ""
    private var pendingImageFile: java.io.File? = null
    private var previewRow: LinearLayout? = null
    private var previewThumb: ImageView? = null
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

        // MD3 Loading khi mở chat
        val loading = Md3Loading.show(this, "Đang mở chat AI")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { loading.dismiss() } catch (_: Exception) {}
            if (!isNetworkAvailable()) {
                Md3Loading.showNoNetwork(this, "Không có mạng — không mở được chat AI")
            }
        }, 1200)

        val hold = TextView(this).apply {
            text = UiTranslate.tr("Đang mở chat…")
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF202124.toInt())
        }
        setContentView(hold)
        try { ThemeFix.apply(this, findViewById(android.R.id.content)) } catch (_: Exception) {}
        window.decorView.post {
            try {
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                if (signedIn()) showChat() else showGate(Lang.t(this, "Chat cần đăng nhập để lưu lịch sử.", "Sign in to save chat history."))
            } catch (e: Throwable) {
                hold.text = "Chat chưa mở được.\n" + (e.message ?: "Lỗi")
            }
        }
    }

    private fun signedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val email = account?.email ?: AppSettings.getRecoveryEmail(this)
        val name = AppSettings.getGoogleDisplayName(this)
        val fb = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        return !email.isNullOrBlank() || name.isNotBlank() || fb
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
            setBackgroundColor(if (ThemeFix.isNight(this@ChatActivity)) 0xFF000000.toInt() else 0xFFF1F3F4.toInt())
            addView(TextView(context).apply {
                text = Lang.t(this@ChatActivity, "Đăng nhập", "Sign in")
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
                text = Lang.t(this@ChatActivity, "Đăng nhập Google", "Sign in with Google")
                isAllCaps = false
                setOnClickListener { signInLauncher.launch(GoogleSignInHelper.signInIntent(this@ChatActivity)) }
            })
            addView(android.widget.Button(context).apply {
                text = Lang.t(this@ChatActivity, "Đăng nhập Facebook", "Sign in with Facebook")
                isAllCaps = false
                setOnClickListener { FacebookLoginHelper.start(this@ChatActivity) }
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
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                // hist chỉ khác null khi máy vừa được khôi phục chat từ tài khoản
                if (hist != null && hist != "[]" && !generating) {
                    reloadHistoryBubbles()
                    android.widget.Toast.makeText(this, UiTranslate.tr("Đã khôi phục lịch sử chat"), android.widget.Toast.LENGTH_SHORT).show()
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
            hint = UiTranslate.tr("Nhắn tin với Gemini")
            setPadding((16 * d).toInt(), (12 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
            val night = ThemeFix.isNight(this@ChatActivity)
            background = GradientDrawable().apply {
                cornerRadius = 26 * d
                setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), if (night) 0xFF333333.toInt() else 0xFFE1E6F2.toInt())
            }
            elevation = 3 * d
            if (night) {
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFFB0B0B0.toInt())
            }
            maxLines = 5
        }
        inputBox = input
        plusBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_gemini_sparkle)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val night = ThemeFix.isNight(this@ChatActivity)
                setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), if (night) 0xFF3A3A3A.toInt() else 0xFFDADCE0.toInt())
            }
            imageTintList = null
            contentDescription = UiTranslate.tr("Thêm")
            setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            setOnClickListener { showPlusMenu(this) }
        }
        val micBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_mic)
            background = null
            contentDescription = UiTranslate.tr("Nói")
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
            setOnClickListener { toggleChatMic() }
        }
        sendBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_send)
            background = sendBg(false)
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            contentDescription = UiTranslate.tr("Gửi")
        }
        val keyBtn = TextView(this).apply {
            text = UiTranslate.tr("Khóa")
            setTextColor(0xFF1A73E8.toInt())
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
            gravity = Gravity.CENTER
        }
        headerAvatar = avatar(true) as ImageView
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_back)
            background = null
            contentDescription = UiTranslate.tr("Quay lại")
            if (ThemeFix.isNight(this@ChatActivity)) {
                imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            }
            setOnClickListener { finish() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            val night = ThemeFix.isNight(this@ChatActivity)
            setBackgroundColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            elevation = if (night) 0f else 3 * d
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
                    setTextColor(if (ThemeFix.isNight(this@ChatActivity)) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
                    paint.isFakeBoldText = true
                })
                addView(TextView(context).apply {
                    text = email.ifBlank { "Đã đăng nhập" }
                    textSize = 12f
                    setTextColor(if (ThemeFix.isNight(this@ChatActivity)) 0xFFE8E8E8.toInt() else 0xFF5F6368.toInt())
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_chat_menu)
                background = null
                contentDescription = "Menu chat"
                if (ThemeFix.isNight(this@ChatActivity)) {
                    imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                }
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
        val galleryBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_gallery)
            background = null
            contentDescription = UiTranslate.tr("Chọn ảnh từ thư viện")
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
            setOnClickListener { openGallery() }
        }
        val cameraBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_camera)
            background = null
            contentDescription = UiTranslate.tr("Chụp ảnh")
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
            setOnClickListener { openCamera() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (2 * d).toInt())
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(galleryBtn, LinearLayout.LayoutParams((36 * d).toInt(), (40 * d).toInt()))
            addView(cameraBtn, LinearLayout.LayoutParams((36 * d).toInt(), (40 * d).toInt()))
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
        // Khung xem trước ảnh sắp gửi (có nút X để bỏ ảnh)
        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, 12 * d)
                }
            }
        }
        previewThumb = thumb
        val removeBtn = ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_close)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            contentDescription = UiTranslate.tr("Bỏ ảnh")
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF5F6368.toInt())
            }
            setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            setOnClickListener { clearPendingImage() }
        }
        val preview = android.widget.FrameLayout(this).apply {
            addView(thumb, android.widget.FrameLayout.LayoutParams((72 * d).toInt(), (72 * d).toInt()).apply {
                topMargin = (8 * d).toInt()
                marginEnd = (8 * d).toInt()
            })
            addView(removeBtn, android.widget.FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt(), Gravity.TOP or Gravity.END))
        }
        val previewBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((14 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), 0)
            visibility = View.GONE
            addView(preview)
        }
        previewRow = previewBox

        val night0 = ThemeFix.isNight(this@ChatActivity)
        val disclaimer = TextView(this).apply {
            text = UiTranslate.tr("Gemini có thể sai, hãy kiểm tra thông tin.")
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(if (night0) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt())
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding((12 * d).toInt(), (2 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (ThemeFix.isNight(this@ChatActivity)) intArrayOf(0xFF000000.toInt(), 0xFF0B0B12.toInt())
                else intArrayOf(0xFFF7F9FF.toInt(), 0xFFE9EEFB.toInt())
            )
            addView(header)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(chipScroll)
            addView(previewBox)
            addView(bar)
            addView(disclaimer)
        }

        reloadHistoryBubbles()

        keyBtn.setOnClickListener {
            val box = EditText(this).apply {
                hint = UiTranslate.tr("Dán khóa API Gemini")
                setText(prefs.getString("key", "").orEmpty())
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(UiTranslate.tr("Khóa Gemini"))
                .setMessage(UiTranslate.tr("Lấy khóa miễn phí tại aistudio.google.com rồi dán vào đây. Khóa được lưu theo tài khoản Google."))
                .setView(box)
                .setPositiveButton(UiTranslate.tr("Lưu")) { _, _ ->
                    val key = box.text.toString().trim()
                    prefs.edit().putString("key", key).apply()
                    CloudSyncHelper.pushChatBackup(this)
                    addBubble("Đã lưu khóa vào tài khoản Google.", mine = false, save = true, actions = false)
                }
                .setNegativeButton(UiTranslate.tr("Hủy"), null)
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
            addWelcomeCard()
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
                addBubble(item.optString("t"), mine, save = false, actions = !mine, ts = item.optLong("ts", 0L))
                val img = item.optString("img")
                if (mine && img.isNotBlank()) {
                    val f = java.io.File(chatImageDir(), img)
                    if (f.exists()) showImage(f, mine = true)
                }
                history.put(item)
            }
        }
    }

    /** Màn chào khi chưa có tin nhắn: logo, lời chào và 4 thẻ gợi ý bấm là gửi. */
    private fun addWelcomeCard() {
        val log = logRef ?: return
        val d = resources.displayMetrics.density
        val night = ThemeFix.isNight(this)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_gemini_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), 0xFFE0E3EA.toInt())
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = 4 * d
        }
        val title = TextView(this).apply {
            text = UiTranslate.tr("Xin chào 👋")
            textSize = 24f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
            setPadding(0, (14 * d).toInt(), 0, (4 * d).toInt())
        }
        val sub = TextView(this).apply {
            text = UiTranslate.tr("Mình có thể đặt báo thức, giải đáp câu hỏi và nhìn ảnh bạn gửi.")
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(if (night) 0xFFB0B0B0.toInt() else 0xFF5F6368.toInt())
            setPadding((24 * d).toInt(), 0, (24 * d).toInt(), (18 * d).toInt())
        }
        fun card(emoji: String, label: String, action: () -> Unit): TextView = TextView(this).apply {
            text = "$emoji  $label"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
            setPadding((14 * d).toInt(), (16 * d).toInt(), (14 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), if (night) 0xFF333333.toInt() else 0xFFE1E6F2.toInt())
            }
            setOnClickListener { action() }
        }
        fun row(a: TextView, b: TextView) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            }
            val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            }
            addView(a, lp1)
            addView(b, lp2)
        }
        val c1 = card("⏰", "Đặt báo thức 6:30") { submitPrompt("Đặt báo thức 6:30 nhãn Dậy học", addUserBubble = true) }
        val c2 = card("📷", "Gửi ảnh để hỏi") { openGallery() }
        val c3 = card("📚", "Ôn tập") { submitPrompt("Đặt 5 câu ôn tập giúp mình", addUserBubble = true) }
        val c4 = card("✍️", "Viết prompt học") { submitPrompt("Viết prompt ôn bài, bọc trong ```prompt", addUserBubble = true) }
        log.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((8 * d).toInt(), (36 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
            addView(logo, LinearLayout.LayoutParams((72 * d).toInt(), (72 * d).toInt()))
            addView(title)
            addView(sub)
            addView(row(c1, c2), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(row(c3, c4), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
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
        val night = ThemeFix.isNight(this)
        val pill = TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(if (night) 0xFFC4C7CC.toInt() else 0xFF5F6368.toInt())
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(if (night) 0xFF1C1C1C.toInt() else 0x14000000)
            }
        }
        log.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, (6 * d).toInt())
            addView(pill)
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
            text = UiTranslate.tr("Đã ghim")
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
            val night = ThemeFix.isNight(this@ChatActivity)
            setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1A73E8.toInt())
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * d).toInt(), if (night) 0xFF333333.toInt() else 0xFFD6E2FB.toInt())
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
        if (stop) {
            setColor(0xFFD93025.toInt())
        } else {
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(0xFF2F7DF6.toInt(), 0xFF6C5CE7.toInt())
        }
    }

    private fun beginGeneration() {
        generating = true
        cancelled.set(false)
        sendBtn.setImageResource(R.drawable.ic_chat_stop)
        sendBtn.background = sendBg(true)
        sendBtn.contentDescription = UiTranslate.tr("Dừng")
    }

    private fun endGeneration() {
        generating = false
        cancelled.set(false)
        activeConn = null
        sendBtn.setImageResource(R.drawable.ic_chat_send)
        sendBtn.background = sendBg(false)
        sendBtn.contentDescription = UiTranslate.tr("Gửi")
    }

    private fun stopGeneration() {
        cancelled.set(true)
        try { activeConn?.disconnect() } catch (_: Exception) {}
        try { chatVm.disconnect() } catch (_: Exception) {}
        typeHandler.removeCallbacksAndMessages(null)
        endGeneration()
    }

    private fun submitPrompt(q: String, addUserBubble: Boolean) {
        if (q.isEmpty() || generating) return
        val shot = pendingImageFile
        pendingImageFile = null
        previewRow?.visibility = View.GONE
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
            if (shot != null) {
                val name = saveChatImage(shot)
                if (name != null) {
                    try {
                        history.optJSONObject(history.length() - 1)?.put("img", name)
                        ChatCloudStore.snapshotCurrent(this, history)
                    } catch (_: Exception) {}
                    showImage(java.io.File(chatImageDir(), name), mine = true)
                } else {
                    showImage(shot, mine = true)
                }
            }
            inputBox.setText("")
        }
        val needSearch = (webSearchOn && needsLive(q)) || (!webSearchOn && looksCurrent(q))
        val first = if (needSearch) "Đang tìm kiếm…" else "Đang suy nghĩ…"
        val waiting = addBubble(first, mine = false, save = false, actions = false)
        startStatusShine(waiting, first)
        // Avatar Gemini xoay (gemini_loop_00..38) trong lúc đang suy nghĩ / trả lời.
        (waiting.tag as? ImageView)?.let { startSpin(it) }
        beginGeneration()
        Thread {
            // Bật: Gemini tự tìm Google/đọc link (trong ChatViewModel). Tắt mà câu hỏi cần tin mới: tra nhanh bằng DuckDuckGo.
            val searched = if (!webSearchOn && looksCurrent(q)) searchWeb(q) else ""
            runOnUiThread { setStatus(waiting, "Đang suy nghĩ…") }
            val asked = if (searched.isBlank()) q2 else q2 + "\n\nKết quả tìm web thật (chỉ dựa vào đoạn này, nếu tin nói đã ngừng hoặc khai tử thì phải trả lời đã ngừng, không nói còn hoạt động. Nếu không đủ thì nói chưa đủ tin):\n" + searched
            val jpeg = shot?.takeIf { it.exists() }?.let { prepareJpeg(it) }
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

    // ---------------------------------------------------------------- ảnh trong chat + sửa tin nhắn
    private fun chatImageDir(): java.io.File = java.io.File(filesDir, "chat_images").also { it.mkdirs() }

    /** Lưu bản ảnh đã gửi vào máy để thoát chat vào lại vẫn còn. Trả về tên file. */
    private fun saveChatImage(src: java.io.File): String? {
        return try {
            val bytes = prepareJpeg(src) ?: return null
            val name = "img_" + System.currentTimeMillis() + ".jpg"
            java.io.File(chatImageDir(), name).writeBytes(bytes)
            name
        } catch (_: Throwable) { null }
    }

    private fun editRow(ts: Long): LinearLayout {
        val d = resources.displayMetrics.density
        val time = TextView(this).apply {
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
            textSize = 11f
            setTextColor(0xFF8A8F98.toInt())
            setPadding(0, (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        val edit = TextView(this).apply {
            text = UiTranslate.tr("✏️ Sửa")
            textSize = 12f
            setTextColor(0xFF5F6368.toInt())
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
            contentDescription = UiTranslate.tr("Sửa tin nhắn")
            setOnClickListener { editMessage(ts) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, (44 * d).toInt(), 0)
            addView(time)
            addView(edit)
        }
    }

    /** Sửa tin nhắn của mình: bỏ tin này và mọi câu trả lời sau nó, đưa chữ (và ảnh) về ô nhập để gửi lại. */
    private fun editMessage(ts: Long) {
        if (generating) {
            Toast.makeText(this, UiTranslate.tr("Đợi AI trả lời xong rồi sửa nhé"), Toast.LENGTH_SHORT).show()
            return
        }
        var idx = -1
        for (i in 0 until history.length()) {
            val o = history.optJSONObject(i) ?: continue
            if (o.optInt("m") == 1 && o.optLong("ts") == ts) { idx = i; break }
        }
        if (idx < 0) {
            Toast.makeText(this, UiTranslate.tr("Không tìm thấy tin nhắn này"), Toast.LENGTH_SHORT).show()
            return
        }
        val item = history.getJSONObject(idx)
        val text = item.optString("t")
        val img = item.optString("img")
        while (history.length() > idx) history.remove(history.length() - 1)
        ChatCloudStore.snapshotCurrent(this, history)
        try { CloudSyncHelper.pushChatBackup(this) } catch (_: Exception) {}
        removeErrorCard()
        clearPendingImage()
        reloadHistoryBubbles()
        inputBox.setText(text)
        inputBox.setSelection(text.length)
        inputBox.requestFocus()
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(inputBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {}
        if (img.isNotBlank()) {
            val f = java.io.File(chatImageDir(), img)
            if (f.exists()) setPendingImage(f, "Người dùng gửi kèm một ảnh. Hãy nhìn ảnh và trả lời.", text)
        }
        Toast.makeText(this, UiTranslate.tr("Sửa xong bấm gửi để hỏi lại"), Toast.LENGTH_SHORT).show()
    }

    private fun addBubble(text: String, mine: Boolean, save: Boolean, actions: Boolean, ts: Long = 0L): TextView {
        val stamp = if (ts > 0L) ts else System.currentTimeMillis()
        val d = resources.displayMetrics.density
        val log = logRef ?: return TextView(this)
        val formatted = if (mine) text.trim() else ChatMarkdown.format(text)
        val tv = TextView(this).apply {
            this.text = formatted
            textSize = 16f
            setTextColor(if (mine || ThemeFix.isNight(this@ChatActivity)) 0xFFFFFFFF.toInt() else 0xFF202124.toInt())
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = bubbleBg(mine, d)
            maxWidth = (resources.displayMetrics.widthPixels - (88 * d).toInt()).coerceAtLeast((180 * d).toInt())
            setTag(R.id.chat_full_text, text)
            setLineSpacing(0f, 1.15f)
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
        if (mine && (save || ts > 0L) && text.isNotBlank()) {
            col.addView(editRow(stamp))
        }
        log.addView(col)
        if (save) {
            history.put(JSONObject().put("m", if (mine) 1 else 0).put("t", text).put("ts", stamp))
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
        val r = 20 * d
        if (mine) {
            cornerRadii = floatArrayOf(r, r, 6 * d, 6 * d, r, r, r, r)
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(0xFF2F7DF6.toInt(), 0xFF5B63F0.toInt())
        } else {
            val night = ThemeFix.isNight(this@ChatActivity)
            cornerRadii = floatArrayOf(6 * d, 6 * d, r, r, r, r, r, r)
            setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
            setStroke((1 * d).toInt(), if (night) 0xFF2E2E2E.toInt() else 0xFFE4E8F2.toInt())
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
                text = UiTranslate.tr("Sao chép prompt")
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
        Toast.makeText(this, UiTranslate.tr("Chat mới"), Toast.LENGTH_SHORT).show()
    }

    private fun showOldChats() {
        ChatCloudStore.snapshotCurrent(this, history)
        val all = ChatCloudStore.sessions(this)
        val list = ArrayList<JSONObject>()
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if ((o.optJSONArray("messages")?.length() ?: 0) > 0) list.add(o)
        }
        if (list.isEmpty()) {
            Toast.makeText(this, UiTranslate.tr("Chưa có chat cũ"), Toast.LENGTH_SHORT).show()
            return
        }
        list.sortByDescending { it.optLong("ts") }
        val d = resources.displayMetrics.density
        val night = ThemeFix.isNight(this)
        val curId = ChatCloudStore.currentSessionId(this)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (18 * d).toInt(), (16 * d).toInt(), (24 * d).toInt())
            setBackgroundColor(if (night) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())
        }
        col.addView(TextView(this).apply {
            text = UiTranslate.tr("Chat cũ")
            textSize = 20f
            paint.isFakeBoldText = true
            setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
        })
        col.addView(TextView(this).apply {
            text = "${list.size} cuộc trò chuyện · bấm để mở, bấm 🗑 để xóa"
            textSize = 12.5f
            setTextColor(if (night) 0xFFB0B0B0.toInt() else 0xFF5F6368.toInt())
            setPadding(0, (2 * d).toInt(), 0, (12 * d).toInt())
        })
        for (o in list) {
            val id = o.optString("id")
            val msgs = o.optJSONArray("messages") ?: continue
            var preview = ""
            for (j in msgs.length() - 1 downTo 0) {
                val t = msgs.optJSONObject(j)?.optString("t").orEmpty().trim()
                if (t.isNotBlank()) { preview = t.replace('\n', ' ').take(70); break }
            }
            val isCur = id == curId
            val title = o.optString("title").ifBlank { "Chat" }
            val meta = dayLabel(o.optLong("ts")) + " · " + msgs.length() + " tin" + (if (isCur) " · Đang mở" else "")
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 15f
                    paint.isFakeBoldText = true
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(if (night) 0xFFFFFFFF.toInt() else 0xFF1A1C28.toInt())
                })
                addView(TextView(context).apply {
                    text = preview
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(if (night) 0xFFB0B0B0.toInt() else 0xFF5F6368.toInt())
                })
                addView(TextView(context).apply {
                    text = meta
                    textSize = 11f
                    setTextColor(0xFF8A8F98.toInt())
                    setPadding(0, (2 * d).toInt(), 0, 0)
                })
            }
            val del = TextView(this).apply {
                text = "🗑"
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding((12 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
                contentDescription = UiTranslate.tr("Xóa chat")
                setOnClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@ChatActivity)
                        .setTitle(UiTranslate.tr("Xóa cuộc chat này?"))
                        .setMessage(title)
                        .setPositiveButton(UiTranslate.tr("Xóa")) { _, _ ->
                            ChatCloudStore.deleteSession(this@ChatActivity, id)
                            if (id == curId) {
                                while (history.length() > 0) history.remove(0)
                                reloadHistoryBubbles()
                            }
                            try { CloudSyncHelper.pushChatBackup(this@ChatActivity) } catch (_: Exception) {}
                            sheet.dismiss()
                            showOldChats()
                        }
                        .setNegativeButton(UiTranslate.tr("Hủy"), null)
                        .show()
                }
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * d).toInt(), (12 * d).toInt(), (6 * d).toInt(), (12 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 16 * d
                    setColor(if (night) 0xFF1E1E1E.toInt() else 0xFFF3F6FC.toInt())
                    if (isCur) setStroke((2 * d).toInt(), 0xFF1A73E8.toInt())
                }
                addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(del)
                setOnClickListener {
                    sheet.dismiss()
                    if (!isCur) {
                        ChatCloudStore.openSession(this@ChatActivity, id, history)
                        reloadHistoryBubbles()
                    }
                }
            }
            col.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * d).toInt() })
        }
        sheet.setContentView(ScrollView(this).apply { addView(col) })
        sheet.show()
    }

    private fun clearThisChat() {
        while (history.length() > 0) history.remove(0)
        ChatCloudStore.snapshotCurrent(this, history)
        reloadHistoryBubbles()
    }

    private fun showPins() {
        val pins = ChatCloudStore.pins(this)
        if (pins.length() == 0) {
            Toast.makeText(this, UiTranslate.tr("Chưa ghim tin nào"), Toast.LENGTH_SHORT).show()
            return
        }
        val items = Array(pins.length()) { pins.optString(it).take(60) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(UiTranslate.tr("Tin đã ghim"))
            .setItems(items) { _, which -> copyText(pins.optString(which)) }
            .setNegativeButton(UiTranslate.tr("Đóng"), null)
            .show()
    }

    private fun reloadTts() {
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = TtsHelper(this).also { it.useMediaStream = true }
    }

    private fun voiceDialog() {
        val rate = AppSettings.getChatTtsRate(this)
        val items = arrayOf(
            Lang.t("Chọn giọng (theo hệ thống…)", "Choose voice (system…)"),
            "Đọc chậm", "Đọc vừa", "Đọc nhanh"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Giọng đọc: ${TtsVoiceCatalog.currentLabel(this)} · tốc độ ${"%.1f".format(rate)}")
            .setItems(items) { _, which ->
                if (which == 0) {
                    TtsVoicePicker.show(this) { reloadTts() }
                } else {
                    when (which) {
                        1 -> AppSettings.setChatTtsRate(this, 0.8f)
                        2 -> AppSettings.setChatTtsRate(this, 0.95f)
                        3 -> AppSettings.setChatTtsRate(this, 1.15f)
                    }
                    reloadTts()
                    Toast.makeText(this, UiTranslate.tr("Đã lưu giọng đọc"), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun summarizeChat() {
        val key = chatPrefs().getString("key", "").orEmpty()
        if (key.isBlank()) {
            Toast.makeText(this, UiTranslate.tr("Chưa có khóa Gemini"), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, UiTranslate.tr("Chưa có gì để tóm tắt"), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, UiTranslate.tr("Máy không nhận giọng nói"), Toast.LENGTH_SHORT).show()
            return
        }
        lastHeard = ""
        try { speech?.destroy() } catch (_: Exception) {}
        val rec = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        speech = rec
        rec.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                inputBox.hint = UiTranslate.tr("Đang nghe…")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listeningMic = false }
            override fun onError(error: Int) {
                listeningMic = false
                inputBox.hint = UiTranslate.tr("Nhắn tin với Gemini")
                if (lastHeard.length >= 2) inputBox.setText(lastHeard)
            }
            override fun onResults(results: android.os.Bundle?) {
                listeningMic = false
                inputBox.hint = UiTranslate.tr("Nhắn tin với Gemini")
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
            openGallery()
        })
        box.addView(row("Camera") { openCamera() })
        val web = TextView(this).apply {
            text = if (webSearchOn) "Tìm kiếm trang web: Bật (tự tìm khi cần)" else "Tìm kiếm trang web: Tắt"
            textSize = 16f
            setTextColor(0xFF1A73E8.toInt())
            setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
            setOnClickListener {
                webSearchOn = !webSearchOn
                text = if (webSearchOn) "Tìm kiếm trang web: Bật (tự tìm khi cần)" else "Tìm kiếm trang web: Tắt"
                Toast.makeText(this@ChatActivity, if (webSearchOn) UiTranslate.tr("Đã bật tìm web") else UiTranslate.tr("Đã tắt tìm web"), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, UiTranslate.tr("Hãy cho phép camera rồi bấm lại"), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = java.io.File(cacheDir, "chat_images").apply { mkdirs() }
            val file = java.io.File(dir, "cam_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri), 72)
        } catch (e: Exception) {
            Toast.makeText(this, UiTranslate.tr("Không mở được camera"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageCard() {
        val topic = inputBox.text?.toString()?.trim().orEmpty().ifBlank { "Minh họa bài học hôm nay" }
        val key = chatPrefs().getString("key", "").orEmpty()
        val ref = pendingImageFile
        if (key.isBlank()) {
            Toast.makeText(this, UiTranslate.tr("Cần khóa Gemini để AI vẽ ảnh"), Toast.LENGTH_SHORT).show()
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
                    waiting.text = UiTranslate.tr("Chưa tạo được ảnh. Thử lại hoặc viết rõ hơn muốn vẽ gì.")
                } else {
                    waiting.text = UiTranslate.tr("Ảnh đã tạo")
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
            setOnClickListener { shareFile(file, if (file.name.endsWith(".png")) "image/png" else "image/jpeg") }
        }
        val wrap = LinearLayout(this).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, (4 * d).toInt(), 0, (8 * d).toInt())
            addView(iv, LinearLayout.LayoutParams((260 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        log.addView(wrap)
        scrollRef?.post { scrollRef?.fullScroll(android.widget.ScrollView.FOCUS_DOWN) }
    }

    /** Chỉ để hiện chữ "Đang tìm kiếm…" cho hợp lý. */
    private fun needsLive(q: String): Boolean {
        val s = q.lowercase()
        return looksCurrent(q) || listOf(
            "mấy giờ", "hôm nay", "tin tức", "thời tiết", "giá ", "http", "www.", "tìm", "kết quả", "mới nhất"
        ).any { s.contains(it) }
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
            Toast.makeText(this, UiTranslate.tr("Không tạo được file"), Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 71 && resultCode == RESULT_OK && data?.data != null) {
            val f = copyPicked(data.data!!)
            if (f == null) {
                Toast.makeText(this, UiTranslate.tr("Không đọc được ảnh này"), Toast.LENGTH_SHORT).show()
            } else {
                setPendingImage(f, "Người dùng gửi kèm một ảnh. Hãy nhìn ảnh và trả lời.", "Nhìn ảnh này và giải thích giúp mình.")
            }
        }
        if (requestCode == 72 && resultCode == RESULT_OK) {
            val f = cameraFile?.takeIf { it.exists() && it.length() > 0 }
            if (f == null) {
                Toast.makeText(this, UiTranslate.tr("Không lấy được ảnh vừa chụp"), Toast.LENGTH_SHORT).show()
            } else {
                setPendingImage(f, "Người dùng gửi kèm một ảnh vừa chụp. Hãy nhìn ảnh và trả lời.", "Nhìn ảnh vừa chụp và giải thích giúp mình.")
            }
        }
    }

    @Deprecated("permissions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, UiTranslate.tr("Cần cho phép camera để chụp ảnh"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openGallery() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 71)
        } catch (_: Exception) {
            Toast.makeText(this, UiTranslate.tr("Không mở được thư viện ảnh"), Toast.LENGTH_SHORT).show()
        }
    }

    /** Gắn ảnh vào tin nhắn sắp gửi và hiện ảnh nhỏ phía trên ô nhập. */
    private fun setPendingImage(file: java.io.File, note: String, defaultText: String) {
        pendingImageFile = file
        pendingImageNote = note
        if (::inputBox.isInitialized && inputBox.text.toString().isBlank()) inputBox.setText(defaultText)
        val bmp = decodeSampled(file, 240)
        if (bmp != null) previewThumb?.setImageBitmap(bmp)
        previewRow?.visibility = View.VISIBLE
    }

    private fun clearPendingImage() {
        pendingImageFile = null
        pendingImageNote = ""
        previewThumb?.setImageDrawable(null)
        previewRow?.visibility = View.GONE
    }

    private fun readRotation(file: java.io.File): Int = try {
        when (android.media.ExifInterface(file.absolutePath)
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

    /** Đọc ảnh đã thu nhỏ (cạnh dài ≤ maxSide) và xoay đúng chiều. */
    private fun decodeSampled(file: java.io.File, maxSide: Int): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val longest = maxOf(bmp.width, bmp.height)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                bmp = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            }
            val rot = readRotation(file)
            if (rot != 0) {
                val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            bmp
        } catch (_: Throwable) { null }
    }

    /** Ảnh gửi cho AI: JPEG thật, cạnh dài ≤ 1280px, đúng chiều (ảnh gốc từ camera rất nặng). */
    private fun prepareJpeg(file: java.io.File): ByteArray? {
        val bmp = decodeSampled(file, 1280) ?: return try { file.readBytes() } catch (_: Exception) { null }
        return java.io.ByteArrayOutputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
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


    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.activeNetwork != null
        } catch (_: Exception) { false }
    }

}