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
    private var rawId = R.raw.sleep_delta

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val tvTitle = TextView(this).apply { text = "Nhạc ru ngủ (không dùng lúc báo thức)"; textSize = 18f }
        val tvTrack = TextView(this).apply { text = AppRingtones.sleep.first().label; textSize = 16f }
        val tvTimer = TextView(this).apply { text = "Hẹn tắt: $minutes phút" }
        val btnPick = MaterialButton(this).apply { text = "Chọn nhạc ru ngủ" }
        val btnPlay = MaterialButton(this).apply { text = "Phát" }
        val btnStop = MaterialButton(this).apply { text = "Dừng" }
        val btnDur = MaterialButton(this).apply { text = "Hẹn giờ tắt" }
        listOf(tvTitle, tvTrack, tvTimer, btnPick, btnPlay, btnStop, btnDur).forEach { root.addView(it) }
        setContentView(root)
        title = "Nhạc ru ngủ"

        btnPick.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Nhạc ru ngủ")
                .setItems(AppRingtones.sleep.map { it.label }.toTypedArray()) { _, w ->
                    rawId = AppRingtones.sleep[w].raw
                    tvTrack.text = AppRingtones.sleep[w].label
                }.show()
        }
        btnPlay.setOnClickListener {
            stopPlay()
            try { player = MediaPlayer.create(this, rawId)?.apply { isLooping = true; start() } } catch (_: Exception) {}
            timer?.cancel()
            timer = object : CountDownTimer(minutes * 60_000L, 1000L) {
                override fun onTick(ms: Long) { tvTimer.text = "Tắt sau ${ms / 60000} phút" }
                override fun onFinish() { stopPlay(); tvTimer.text = "Đã tắt" }
            }.start()
        }
        btnStop.setOnClickListener { stopPlay(); tvTimer.text = "Đã dừng" }
        btnDur.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setItems(arrayOf("15 phút", "30 phút", "60 phút", "90 phút")) { _, w ->
                    minutes = listOf(15, 30, 60, 90)[w]
                    tvTimer.text = "Hẹn tắt: $minutes phút"
                }.show()
        }
    }

    private fun stopPlay() {
        timer?.cancel(); timer = null
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    override fun onDestroy() { stopPlay(); super.onDestroy() }
}
