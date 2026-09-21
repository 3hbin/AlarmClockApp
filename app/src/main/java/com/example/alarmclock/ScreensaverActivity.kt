package com.example.alarmclock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Màn hình ngủ: nền đen, chữ trắng, ẩn thanh hệ thống,
 * giảm sáng, bật Không làm phiền.
 */
class ScreensaverActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private var prevDnd = NotificationManager.INTERRUPTION_FILTER_ALL
    private var changedDnd = false

    private val tick = object : Runnable {
        override fun run() {
            val loc = if (AppSettings.isEnglishUi(this@ScreensaverActivity)) Locale.US else Locale("vi", "VN")
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            date.text = SimpleDateFormat("EEEE, dd/MM", loc).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()
        dimScreen()
        hideSystemBars()
        enableFocusMode()
        hintBatterySaver()

        clock = TextView(this).apply {
            textSize = 84f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
        }
        date = TextView(this).apply {
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 0)
        }
        val hint = TextView(this).apply {
            text = if (AppSettings.isEnglishUi(this@ScreensaverActivity))
                "Tap to exit"
            else
                "Chạm để thoát"
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
        }
        setContentView(root)
        handler.post(tick)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun dimScreen() {
        val lp = window.attributes
        lp.screenBrightness = 0.04f
        window.attributes = lp
    }

    private fun enableFocusMode() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!nm.isNotificationPolicyAccessGranted) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    return
                }
                prevDnd = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                changedDnd = true
            }
        } catch (_: Exception) {}
    }

    private fun hintBatterySaver() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !pm.isPowerSaveMode) {
                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                if (!prefs.getBoolean("asked_battery_saver", false)) {
                    prefs.edit().putBoolean("asked_battery_saver", true).apply()
                    try {
                        startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                    } catch (_: Exception) {}
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (changedDnd) {
            try {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(prevDnd)
                }
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
