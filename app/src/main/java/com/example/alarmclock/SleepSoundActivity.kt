package com.example.alarmclock

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Nhạc ru ngủ + hẹn giờ tắt. Dùng raw êm trong app, một nguồn TonePlayer.
 */
class SleepSoundActivity : AppCompatActivity() {
    private var timer: CountDownTimer? = null
    private var leftMs = 0L
    private var playing = false
    private var soundRaw = R.raw.soft_chime

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Nhạc ru ngủ"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val tvStatus = TextView(this).apply {
            textSize = 20f
            text = "Chọn tiếng êm và thời gian tắt"
        }
        val tvLeft = TextView(this).apply {
            textSize = 32f
            text = "00:00"
        }
        val grpSound = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        fun addSound(id: Int, label: String, raw: Int) {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                this.id = id
            }
            grpSound.addView(b)
            if (raw == soundRaw) grpSound.check(id)
        }
        addSound(1001, "Êm 1", R.raw.soft_chime)
        addSound(1002, "Êm 2", R.raw.soft_bell)
        addSound(1003, "Oz nhẹ", R.raw.ringtone_oz)
        grpSound.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            soundRaw = when (checkedId) {
                1002 -> R.raw.soft_bell
                1003 -> R.raw.ringtone_oz
                else -> R.raw.soft_chime
            }
            if (playing) TonePlayer.playAppRaw(this, soundRaw, loop = true)
        }

        val grpTime = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        val times = listOf(15 to 2001, 30 to 2002, 45 to 2003, 60 to 2004)
        times.forEach { (min, id) ->
            grpTime.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "${min}p"
                this.id = id
            })
        }
        grpTime.check(2002)

        val btnPlay = MaterialButton(this).apply { text = "Phát" }
        btnPlay.setOnClickListener {
            if (playing) {
                stopAll()
                tvStatus.text = "Đã dừng"
                btnPlay.text = "Phát"
                tvLeft.text = "00:00"
            } else {
                val min = when (grpTime.checkedButtonId) {
                    2001 -> 15; 2003 -> 45; 2004 -> 60; else -> 30
                }
                TonePlayer.playAppRaw(this, soundRaw, loop = true)
                playing = true
                btnPlay.text = "Dừng"
                leftMs = min * 60_000L
                timer?.cancel()
                timer = object : CountDownTimer(leftMs, 1000) {
                    override fun onTick(ms: Long) {
                        leftMs = ms
                        val s = ms / 1000
                        tvLeft.text = "%02d:%02d".format(s / 60, s % 60)
                        tvStatus.text = "Đang phát · tự tắt sau"
                    }
                    override fun onFinish() {
                        stopAll()
                        tvLeft.text = "00:00"
                        tvStatus.text = "Hết giờ — đã tắt nhạc"
                        btnPlay.text = "Phát"
                    }
                }.start()
            }
        }

        root.addView(tvStatus)
        root.addView(tvLeft)
        root.addView(TextView(this).apply { text = "Âm thanh"; setPadding(0, pad, 0, 8) })
        root.addView(grpSound)
        root.addView(TextView(this).apply { text = "Tự tắt sau"; setPadding(0, pad, 0, 8) })
        root.addView(grpTime)
        root.addView(btnPlay)
        setContentView(root)
    }

    private fun stopAll() {
        playing = false
        timer?.cancel()
        timer = null
        TonePlayer.stop()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { stopAll(); super.onDestroy() }
}
