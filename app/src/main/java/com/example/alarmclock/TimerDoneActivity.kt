package com.example.alarmclock

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Chỉ hiện khi đếm ngược hết lúc app đang thoát. */
class TimerDoneActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildShow.turnOn(this)) {
            // flags applied
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            setPadding(48, 160, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Hết giờ"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 36f
        })
        root.addView(Button(this).apply {
            text = "Tắt"
            setOnClickListener {
                stopService(android.content.Intent(this@TimerDoneActivity, TimerService::class.java))
                finish()
            }
        })
        setContentView(root)
    }
}

private object BuildShow {
    fun turnOn(activity: AppCompatActivity): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        return true
    }
}
