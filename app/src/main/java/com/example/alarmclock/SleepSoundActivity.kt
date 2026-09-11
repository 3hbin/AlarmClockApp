package com.example.alarmclock

import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SleepSoundActivity : AppCompatActivity() {
    private var player: MediaPlayer? = null
    private var timer: CountDownTimer? = null
    private var minutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val tv = TextView(this).apply {
            text = "Âm thanh ru ngủ"
            textSize = 20f
            id = android.view.View.generateViewId()
        }
        val tvTimer = TextView(this).apply { text = "Hẹn tắt: $minutes phút"; textSize = 16f }
        val btnPlay = MaterialButton(this).apply { text = "Phát mưa / chuông êm" }
        val btnStop = MaterialButton(this).apply { text = "Dừng" }
        val btnDur = MaterialButton(this).apply { text = "Đổi hẹn giờ tắt" }
        root.addView(tv); root.addView(tvTimer); root.addView(btnPlay); root.addView(btnStop); root.addView(btnDur)
        setContentView(root)
        title = "Âm thanh ru ngủ"

        btnPlay.setOnClickListener {
            stopPlay()
            try {
                player = MediaPlayer.create(this, R.raw.soft_chime)?.apply {
                    isLooping = true
                    start()
                }
            } catch (_: Exception) {}
            startTimer(tvTimer)
        }
        btnStop.setOnClickListener { stopPlay(); tvTimer.text = "Đã dừng" }
        btnDur.setOnClickListener {
            val items = arrayOf("15 phút", "30 phút", "60 phút")
            MaterialAlertDialogBuilder(this)
                .setItems(items) { _, w ->
                    minutes = listOf(15, 30, 60)[w]
                    tvTimer.text = "Hẹn tắt: $minutes phút"
                }.show()
        }
    }

    private fun startTimer(tv: TextView) {
        timer?.cancel()
        timer = object : CountDownTimer(minutes * 60_000L, 1000L) {
            override fun onTick(ms: Long) {
                tv.text = "Tắt sau ${ms / 60000} phút ${(ms / 1000) % 60} giây"
            }
            override fun onFinish() {
                stopPlay()
                tv.text = "Đã tắt"
            }
        }.start()
    }

    private fun stopPlay() {
        timer?.cancel(); timer = null
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    override fun onDestroy() {
        stopPlay()
        super.onDestroy()
    }
}
