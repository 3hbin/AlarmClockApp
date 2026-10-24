package com.example.alarmclock

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/** Trạng thái từng quyền (dùng chung cho màn chào mừng và kiểm tra khi mở app). */
object OnboardingPerms {
    fun has(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun notifications(c: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33 && !has(c, Manifest.permission.POST_NOTIFICATIONS)) false
        else NotificationManagerCompat.from(c).areNotificationsEnabled()
    } catch (_: Exception) { true }

    fun exactAlarm(c: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 31)
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        else true
    } catch (_: Exception) { true }

    fun fullScreen(c: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34)
            c.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true
        else true
    } catch (_: Exception) { true }

    fun background(c: Context) = BatteryOptHelper.isIgnoring(c)
    fun microphone(c: Context) = has(c, Manifest.permission.RECORD_AUDIO)
    fun camera(c: Context) = has(c, Manifest.permission.CAMERA)
    fun audioFiles(c: Context) =
        if (Build.VERSION.SDK_INT >= 33) has(c, Manifest.permission.READ_MEDIA_AUDIO)
        else has(c, Manifest.permission.READ_EXTERNAL_STORAGE)
    fun location(c: Context) =
        has(c, Manifest.permission.ACCESS_COARSE_LOCATION) || has(c, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Quyền bắt buộc để báo thức kêu đúng giờ. */
    fun coreMissing(c: Context) =
        !notifications(c) || !exactAlarm(c) || !fullScreen(c) || !background(c)
}

class WelcomeActivity : AppCompatActivity() {

    private class Perm(
        val key: String,
        val emoji: String,
        val title: String,
        val desc: String,
        val core: Boolean,
        val runtime: Array<String>?,
        val code: Int,
        val granted: () -> Boolean,
        val special: () -> Unit
    )

    private val handler = Handler(Looper.getMainLooper())
    private var countdown = LOADING_SECONDS
    private var page = 1
    private var flowActive = false
    private var awaiting = false
    private val attempted = HashSet<String>()

    private lateinit var perms: List<Perm>
    private val chips = HashMap<String, TextView>()
    private val shown = HashMap<String, Boolean>()

    private lateinit var page1: LinearLayout
    private lateinit var page2: LinearLayout
    private lateinit var primaryBtn: TextView
    private lateinit var secondaryBtn: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var doneBanner: View

    private val tick = object : Runnable {
        override fun run() {
            if (page != 1) return
            countdown--
            updateConfirmButton()
            if (countdown > 0) handler.postDelayed(this, 1000L)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(vi: String, en: String) = Lang.t(this, vi, en)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accepted = AppSettings.hasAcceptedWelcome(this)
        if (accepted && (AppSettings.isPermStepDone(this) || !OnboardingPerms.coreMissing(this))) {
            goHome()
            return
        }
        buildPerms()
        buildUi()
        if (accepted) showPermissions() else showWelcome()
    }

    override fun onResume() {
        super.onResume()
        if (!::perms.isInitialized || !::page2.isInitialized) return
        if (page == 2) {
            refresh()
            if (awaiting) {
                handler.postDelayed({ afterRequest() }, 350L)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Người mới phải đi hết bước chào mừng trước khi vào app
        moveTaskToBack(true)
    }

    @Deprecated("permissions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handler.postDelayed({ afterRequest() }, 350L)
    }

    // ------------------------------------------------------------------ dữ liệu quyền
    private fun buildPerms() {
        perms = listOf(
            Perm(
                "notif", "🔔", t("Thông báo", "Notifications"),
                t("Hiện báo thức và nút Tắt / Hoãn trên màn hình khóa.", "Show alarms and Stop / Snooze on the lock screen."),
                true,
                if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else null,
                101,
                { OnboardingPerms.notifications(this) },
                { openNotificationSettings() }
            ),
            Perm(
                "exact", "⏰", t("Báo thức chính xác", "Exact alarms"),
                t("Để chuông kêu đúng từng giây đã hẹn.", "So the alarm rings exactly on time."),
                true, null, 0,
                { OnboardingPerms.exactAlarm(this) },
                {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                        )
                    } catch (_: Exception) { openAppSettings() }
                }
            ),
            Perm(
                "fullscreen", "📱", t("Hiện toàn màn hình", "Full-screen alarm"),
                t("Cho màn hình báo thức bật lên khi máy đang khóa.", "Lets the alarm screen pop up while the phone is locked."),
                true, null, 0,
                { OnboardingPerms.fullScreen(this) },
                {
                    try {
                        startActivity(
                            Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:$packageName"))
                        )
                    } catch (_: Exception) { openAppSettings() }
                }
            ),
            Perm(
                "background", "🔋",
                t("Chạy nền, không tối ưu pin", "Run in background"),
                t(
                    "Để hệ thống không tắt app làm mất báo thức." +
                        (if (BatteryOptHelper.isHuaweiLike()) " Huawei: nên bật thêm “Khởi động tự động”." else ""),
                    "Stops the system from killing the app and losing alarms." +
                        (if (BatteryOptHelper.isHuaweiLike()) " Huawei: also enable Auto-launch." else "")
                ),
                true, null, 0,
                { OnboardingPerms.background(this) },
                {
                    AppSettings.prefs(this).edit().putBoolean("battery_opt_prompted_v2", true).apply()
                    BatteryOptHelper.requestIgnore(this)
                }
            ),
            Perm(
                "mic", "🎙️", t("Micro", "Microphone"),
                t("Ra lệnh giọng nói và trò chuyện trực tiếp với Gemini.", "Voice commands and live chat with Gemini."),
                false, arrayOf(Manifest.permission.RECORD_AUDIO), 102,
                { OnboardingPerms.microphone(this) }, {}
            ),
            Perm(
                "camera", "📷", t("Camera", "Camera"),
                t("Quét QR để tắt báo thức và chụp ảnh gửi cho AI.", "Scan QR to stop the alarm and snap photos for the AI."),
                false, arrayOf(Manifest.permission.CAMERA), 103,
                { OnboardingPerms.camera(this) }, {}
            ),
            Perm(
                "audio", "🎵", t("Nhạc và âm thanh", "Music and audio"),
                t("Chọn bài hát của bạn làm nhạc chuông.", "Pick your own songs as ringtones."),
                false,
                if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                104,
                { OnboardingPerms.audioFiles(this) }, {}
            ),
            Perm(
                "location", "📍", t("Vị trí", "Location"),
                t("Gợi ý theo nơi bạn ở (thời tiết, địa điểm).", "Suggestions for where you are (weather, places)."),
                false,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 105,
                { OnboardingPerms.location(this) }, {}
            )
        )
    }

    private fun askedPrefs() = getSharedPreferences("onboarding_perm_asked", Context.MODE_PRIVATE)

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {}
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) { openAppSettings() }
    }

    /** Xin một quyền. auto = đang chạy chuỗi tự động (không đẩy người dùng vào Cài đặt). */
    private fun requestOne(p: Perm, auto: Boolean): Boolean {
        val rt = p.runtime
        if (rt != null) {
            val asked = askedPrefs().getBoolean("asked_" + p.key, false)
            val rationale = rt.any { shouldShowRequestPermissionRationale(it) }
            if (asked && !rationale) {
                // Đã từ chối hẳn: hệ thống không hiện hộp thoại nữa.
                if (auto) return false
                awaiting = true
                if (p.key == "notif") openNotificationSettings() else openAppSettings()
                return true
            }
            askedPrefs().edit().putBoolean("asked_" + p.key, true).apply()
            awaiting = true
            requestPermissions(rt, p.code)
            return true
        }
        awaiting = true
        p.special()
        return true
    }

    private fun startFlow() {
        flowActive = true
        attempted.clear()
        stepNext()
    }

    private fun stepNext() {
        if (isFinishing) return
        while (true) {
            val p = perms.firstOrNull { !it.granted() && it.key !in attempted }
            if (p == null) {
                flowActive = false
                refresh()
                return
            }
            attempted.add(p.key)
            if (requestOne(p, true)) return
        }
    }

    private fun afterRequest() {
        if (!awaiting || isFinishing) return
        awaiting = false
        refresh()
        if (flowActive) handler.postDelayed({ stepNext() }, 250L)
    }

    // ------------------------------------------------------------------ giao diện
    private fun gradient(colors: IntArray, radius: Int = 0, stroke: Int = 0, strokeColor: Int = 0) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            if (radius > 0) cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    private fun cardBg() = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(0x1AFFFFFF)
        setStroke(dp(1), 0x26FFFFFF)
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val match = ViewGroup.LayoutParams.MATCH_PARENT

    private fun emojiCircle(emoji: String, size: Int, textSp: Float): TextView = TextView(this).apply {
        text = emoji
        textSize = textSp
        gravity = Gravity.CENTER
        background = circle(0x26FFFFFF)
        minimumWidth = dp(size)
        minimumHeight = dp(size)
    }

    private fun buildUi() {
        val bg = gradient(intArrayOf(0xFF0B1240.toInt(), 0xFF2A1A78.toInt(), 0xFF5B2BC9.toInt()))
        val root = FrameLayout(this).apply { background = bg }

        // họa tiết tròn trang trí
        val blobTop = View(this).apply { background = circle(0x1F8B7CFF) }
        val blobBottom = View(this).apply { background = circle(0x1AFF8FD0.toInt()) }
        root.addView(blobTop, FrameLayout.LayoutParams(dp(260), dp(260), Gravity.TOP or Gravity.END).apply {
            topMargin = -dp(70)
            marginEnd = -dp(90)
        })
        root.addView(blobBottom, FrameLayout.LayoutParams(dp(220), dp(220), Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(60)
            marginStart = -dp(100)
        })

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        page1 = buildWelcomePage()
        page2 = buildPermissionPage()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(12))
            addView(page1, lp(match, wrap))
            addView(page2, lp(match, wrap))
        }
        column.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }, lp(match, 0).apply { weight = 1f })

        primaryBtn = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF1B1464.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.WHITE)
            }
            setOnClickListener { onPrimary() }
        }
        secondaryBtn = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(4))
            setOnClickListener { finishFlow() }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(16))
            addView(primaryBtn, lp(match, dp(56)))
            addView(secondaryBtn, lp(match, wrap))
        }
        column.addView(bottom, lp(match, wrap))
        root.addView(column, FrameLayout.LayoutParams(match, match))
        setContentView(root)

        try {
            window.statusBarColor = 0xFF0B1240.toInt()
            window.navigationBarColor = 0xFF2A1A78.toInt()
            val ic = WindowCompat.getInsetsController(window, window.decorView)
            ic.isAppearanceLightStatusBars = false
            ic.isAppearanceLightNavigationBars = false
        } catch (_: Exception) {}
    }

    private fun title(text: String, sp: Float = 26f) = TextView(this).apply {
        this.text = text
        textSize = sp
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
    }

    private fun body(text: String, sp: Float = 15f) = TextView(this).apply {
        this.text = text
        textSize = sp
        gravity = Gravity.CENTER
        setTextColor(0xCCFFFFFF.toInt())
        setLineSpacing(0f, 1.15f)
    }

    private fun featureCard(emoji: String, head: String, desc: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = cardBg()
        }
        card.addView(emojiCircle(emoji, 48, 22f), lp(dp(48), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = head
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 13f
            setTextColor(0xB3FFFFFF.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(texts, lp(0, wrap).apply {
            weight = 1f
            marginStart = dp(14)
        })
        return card
    }

    private fun buildWelcomePage(): LinearLayout {
        val pg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logo = TextView(this).apply {
            text = "⏰"
            textSize = 46f
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x40FFFFFF, 0x1AFFFFFF)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), 0x40FFFFFF)
            }
        }
        pg.addView(logo, lp(dp(104), dp(104)).apply { topMargin = dp(20) })
        pg.addView(title(t("Chào mừng đến với\nBáo thức Challenge", "Welcome to\nChallenge Alarm"), 27f),
            lp(match, wrap).apply { topMargin = dp(22) })
        pg.addView(body(
            t("Báo thức thông minh: tắt bằng thử thách, đánh thức bằng AI.",
                "A smarter alarm: stop it with a challenge, wake up with AI.")
        ), lp(match, wrap).apply { topMargin = dp(10); bottomMargin = dp(22) })

        pg.addView(featureCard("🧠", t("Thử thách tỉnh ngủ", "Wake-up challenges"),
            t("Giải toán, lắc điện thoại, quét QR… để tắt báo thức thật sự.",
                "Solve math, shake the phone, scan a QR… to truly stop the alarm.")),
            lp(match, wrap).apply { bottomMargin = dp(10) })
        pg.addView(featureCard("✨", t("Trợ lý Gemini", "Gemini assistant"),
            t("Nhắn “đặt báo thức 6:07”, gửi ảnh, hỏi mọi thứ ngay trong app.",
                "Say “set an alarm for 6:07”, send photos, ask anything in the app.")),
            lp(match, wrap).apply { bottomMargin = dp(10) })
        pg.addView(featureCard("🎨", t("Giao diện của riêng bạn", "Make it yours"),
            t("Đổi theme, dùng hình nền hoặc ảnh riêng cho màn hình báo thức.",
                "Change themes, use your wallpaper or a photo for the alarm screen.")),
            lp(match, wrap).apply { bottomMargin = dp(18) })

        val privacy = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13.5f
            setTextColor(0xB3FFFFFF.toInt())
        }
        bindPrivacyLink(privacy)
        pg.addView(privacy, lp(match, wrap).apply { bottomMargin = dp(8) })
        return pg
    }

    private fun bindPrivacyLink(tv: TextView) {
        val lead = t("Bằng việc xác nhận, bạn đồng ý với", "By confirming, you agree to the")
        val label = getString(R.string.welcome_privacy_label)
        val url = getString(R.string.privacy_policy_url)
        val full = "$lead $label\n$url"
        val span = SpannableString(full)
        val start = full.indexOf(url)
        val end = start + url.length
        val link = 0xFF9DB8FF.toInt()
        span.setSpan(ForegroundColorSpan(link), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
            }
            override fun updateDrawState(ds: android.text.TextPaint) {
                ds.color = link
                ds.isUnderlineText = true
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = span
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 11.5f
        letterSpacing = 0.1f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0x99FFFFFF.toInt())
        setPadding(dp(4), 0, 0, 0)
    }

    private fun permRow(p: Perm): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = cardBg()
            setOnClickListener {
                if (!p.granted()) {
                    flowActive = false
                    requestOne(p, false)
                }
            }
        }
        card.addView(emojiCircle(p.emoji, 44, 20f), lp(dp(44), dp(44)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = p.title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        texts.addView(TextView(this).apply {
            text = p.desc
            textSize = 12.5f
            setTextColor(0xB3FFFFFF.toInt())
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(texts, lp(0, wrap).apply {
            weight = 1f
            marginStart = dp(12)
            marginEnd = dp(8)
        })
        val chip = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7))
        }
        chips[p.key] = chip
        card.addView(chip, lp(wrap, wrap))
        return card
    }

    private fun buildPermissionPage(): LinearLayout {
        val pg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val icon = TextView(this).apply {
            text = "🛡️"
            textSize = 38f
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x40FFFFFF, 0x1AFFFFFF)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), 0x40FFFFFF)
            }
        }
        pg.addView(icon, lp(dp(84), dp(84)).apply { topMargin = dp(8) })
        pg.addView(title(t("Cấp quyền để báo thức\nchạy thật chuẩn", "Grant permissions so\nalarms run perfectly"), 23f),
            lp(match, wrap).apply { topMargin = dp(16) })
        pg.addView(body(
            t("Bật đủ các quyền bên dưới để chuông luôn kêu đúng giờ, kể cả khi khóa màn hình.",
                "Turn on the permissions below so alarms always ring on time, even when the screen is locked."),
            14f
        ), lp(match, wrap).apply { topMargin = dp(8); bottomMargin = dp(16) })

        progressText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        pg.addView(progressText, lp(match, wrap))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = perms.size
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF4ADE80.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        }
        pg.addView(progressBar, lp(match, dp(8)).apply { topMargin = dp(8); bottomMargin = dp(16) })

        doneBanner = TextView(this).apply {
            text = t("🎉 Đã bật đầy đủ quyền. Báo thức sẵn sàng!", "🎉 All permissions are on. Your alarms are ready!")
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFBBF7D0.toInt())
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0x264ADE80)
                setStroke(dp(1), 0x664ADE80)
            }
            visibility = View.GONE
        }
        pg.addView(doneBanner, lp(match, wrap).apply { bottomMargin = dp(14) })

        val lblWrap = lp(match, wrap).apply { bottomMargin = dp(8) }
        pg.addView(sectionLabel(t("Bắt buộc để báo thức hoạt động", "Required for alarms")), lblWrap)
        for (p in perms.filter { it.core }) {
            pg.addView(permRow(p), lp(match, wrap).apply { bottomMargin = dp(8) })
        }
        pg.addView(sectionLabel(t("Tùy chọn cho tính năng mở rộng", "Optional extras")),
            lp(match, wrap).apply { topMargin = dp(10); bottomMargin = dp(8) })
        for (p in perms.filter { !it.core }) {
            pg.addView(permRow(p), lp(match, wrap).apply { bottomMargin = dp(8) })
        }
        return pg
    }

    private fun stagger(vg: ViewGroup) {
        for (i in 0 until vg.childCount) {
            val v = vg.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(18).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(55L * i).setDuration(360L).start()
        }
    }

    // ------------------------------------------------------------------ trạng thái
    private fun showWelcome() {
        page = 1
        page1.visibility = View.VISIBLE
        page2.visibility = View.GONE
        secondaryBtn.visibility = View.GONE
        countdown = LOADING_SECONDS
        updateConfirmButton()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000L)
        stagger(page1)
    }

    private fun updateConfirmButton() {
        val ready = countdown <= 0
        primaryBtn.isEnabled = ready
        primaryBtn.alpha = if (ready) 1f else 0.55f
        primaryBtn.text = if (ready) t("Xác nhận", "Confirm")
        else t("Xác nhận (${countdown})", "Confirm (${countdown})")
    }

    private fun showPermissions() {
        page = 2
        page1.visibility = View.GONE
        page2.visibility = View.VISIBLE
        refresh()
        stagger(page2)
    }

    private fun refresh() {
        if (page != 2) return
        var on = 0
        for (p in perms) {
            val chip = chips[p.key] ?: continue
            val g = p.granted()
            if (g) {
                on++
                chip.text = t("Đã bật ✓", "On ✓")
                chip.setTextColor(0xFF86EFAC.toInt())
                chip.background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(0x334ADE80)
                    setStroke(dp(1), 0x664ADE80)
                }
            } else {
                chip.text = t("Bật", "Enable")
                chip.setTextColor(0xFF1B1464.toInt())
                chip.background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.WHITE)
                }
            }
            // Hiệu ứng "bật" nảy lên khi quyền vừa được cấp
            if (g && shown[p.key] == false) {
                chip.scaleX = 0.6f
                chip.scaleY = 0.6f
                chip.animate().scaleX(1f).scaleY(1f).setDuration(320L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            shown[p.key] = g
        }
        val all = on == perms.size
        progressBar.progress = on
        progressText.text = t("Đã bật $on/${perms.size} quyền", "$on/${perms.size} permissions on")
        doneBanner.visibility = if (all) View.VISIBLE else View.GONE
        primaryBtn.isEnabled = true
        primaryBtn.alpha = 1f
        if (all) {
            primaryBtn.text = t("Vào ứng dụng", "Open the app")
            secondaryBtn.visibility = View.GONE
        } else {
            primaryBtn.text = t("Cấp quyền đầy đủ", "Grant all permissions")
            secondaryBtn.visibility = View.VISIBLE
            secondaryBtn.text = if (!OnboardingPerms.coreMissing(this))
                t("Vào ứng dụng", "Open the app") else t("Để sau", "Later")
        }
    }

    private fun onPrimary() {
        if (page == 1) {
            if (countdown > 0) return
            AppSettings.setWelcomeAccepted(this, true)
            showPermissions()
        } else {
            if (perms.all { it.granted() }) finishFlow() else startFlow()
        }
    }

    private fun finishFlow() {
        AppSettings.setPermStepDone(this, true)
        if (perms.all { it.granted() }) {
            // Đã bật đủ nên không cần hộp "Hướng dẫn bắt đầu" xin quyền lần nữa.
            try { FirstLaunchDialog.markDone(this) } catch (_: Exception) {}
        }
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        private const val LOADING_SECONDS = 5

        fun launchIfNeeded(activity: Activity): Boolean {
            val accepted = AppSettings.hasAcceptedWelcome(activity)
            val needPerms = accepted &&
                !AppSettings.isPermStepDone(activity) && OnboardingPerms.coreMissing(activity)
            if (accepted && !needPerms) return false
            activity.startActivity(Intent(activity, WelcomeActivity::class.java))
            activity.finish()
            return true
        }
    }
}
