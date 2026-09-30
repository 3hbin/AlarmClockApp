package com.example.alarmclock

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TimerDoneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLock()
        super.onCreate(savedInstanceState)
        try { TonePlayer.playAppRaw(this, R.raw.ringtone_oz, loop = true) } catch (_: Exception) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1A237E.toInt())
            setPadding(48, 80, 48, 80)
        }
        root.addView(TextView(this).apply {
            text = "⏰  Hết giờ!"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 32f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        })
        root.addView(Button(this).apply {
            text = "Tắt"
            textSize = 18f
            setBackgroundColor(0xFFE53935.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { dismiss() }
        })
        setContentView(root)
    }

    private fun showOverLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        try {
            val km = getSystemService(KeyguardManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km?.requestDismissKeyguard(this, null)
            }
        } catch (_: Exception) {}
    }

    private fun dismiss() {
        TimerDoneController.dismiss(this)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showOverLock()
    }

    override fun onBackPressed() {
        dismiss()
    }
}
