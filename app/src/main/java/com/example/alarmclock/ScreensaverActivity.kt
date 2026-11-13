package com.example.alarmclock

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình đen xì, chữ trắng, ẩn thanh hệ thống, giữ máy không ngủ, giảm sáng.
 */
class ScreensaverActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private var wakeLock: PowerManager.WakeLock? = null
    private var oldBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    private val tick = object : Runnable {
        override fun run() {
            val loc = if (Lang.isEn(this@ScreensaverActivity)) Locale.US else Locale("vi", "VN")
            clock.text = SimpleDateFormat("HH:mm", loc).format(Date())
            date.text = SimpleDateFormat(
                if (Lang.isEn(this@ScreensaverActivity)) "EEEE, MMM d" else "EEEE, dd/MM",
                loc
            ).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        oldBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 0.04f }

        hideSystemBars()

        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "dongho:screensaver"
            ).also { it.setReferenceCounted(false); it.acquire(6 * 60 * 60 * 1000L) }
        } catch (_: Exception) {}

        clock = TextView(this).apply {
            textSize = 84f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.SANS_SERIF
        }
        date = TextView(this).apply {
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 0)
        }
        val hint = TextView(this).apply {
            text = Lang.t(this@ScreensaverActivity, "Chạm để thoát", "Tap to exit")
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
            setPadding(0, 48, 0, 0)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            addView(clock)
            addView(date)
            addView(hint)
            setOnClickListener { finish() }
            keepScreenOn = true
        }
        setContentView(root)
        handler.post(tick)
        tryEnableBatterySaver()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun tryEnableBatterySaver() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isPowerSaveMode) {
                val i = android.content.Intent("android.settings.BATTERY_SAVER_SETTINGS")
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                // không nhảy settings mỗi lần — chỉ ghi nhận; giữ sáng thấp để tiết kiệm
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        try {
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
