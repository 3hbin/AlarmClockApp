package com.example.alarmclock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreensaverActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        clock = TextView(this).apply {
            textSize = 72f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }
        val date = TextView(this).apply {
            text = SimpleDateFormat("EEEE, dd/MM", Locale("vi")).format(Date())
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFBBBBBB.toInt())
        }
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF101D42.toInt())
            addView(clock)
            addView(date)
            setOnClickListener { finish() }
        }
        setContentView(root)
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
