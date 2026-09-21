package com.example.alarmclock

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class SleepSoundActivity : AppCompatActivity() {
    private var timer: CountDownTimer? = null
    private var playing = false
    private var leftMs = 0L
    private var soundRaw = R.raw.sleep_rain

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val green = 0xFF2E7D32.toInt()
        val greenLight = 0xFF43A047.toInt()
        val bg = 0xFF0A0A0A.toInt()
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val bar = MaterialToolbar(this).apply {
            title = Lang.t(this@SleepSoundActivity, "Nhạc ru ngủ", "Sleep sounds")
            setBackgroundColor(bg)
            setTitleTextColor(0xFFFFFFFF.toInt())
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener { finish() }
        }
        root.addView(bar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val tvStatus = TextView(this).apply {
            text = Lang.t(this@SleepSoundActivity, "Chọn tiếng mưa / suối / gió", "Pick rain, stream or wind")
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val tvLeft = TextView(this).apply {
            text = "00:00"
            textSize = 42f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, pad, 0, pad)
        }

        val grpSound = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        data class Snd(val id: Int, val raw: Int, val vi: String, val en: String)
        val sounds = listOf(
            Snd(1001, R.raw.sleep_rain, "Mưa đêm", "Night rain"),
            Snd(1002, R.raw.sleep_stream, "Suối", "Stream"),
            Snd(1003, R.raw.sleep_wind, "Gió đêm", "Night wind")
        )
        sounds.forEach { s ->
            grpSound.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = Lang.t(this@SleepSoundActivity, s.vi, s.en)
                this.id = s.id
                setTextColor(0xFFFFFFFF.toInt())
                strokeColor = android.content.res.ColorStateList.valueOf(green)
            })
        }
        grpSound.check(1001)
        grpSound.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            soundRaw = sounds.first { it.id == id }.raw
            if (playing) TonePlayer.playAppRaw(this, soundRaw, loop = true)
        }

        val grpTime = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        listOf(15 to 2001, 30 to 2002, 45 to 2003, 60 to 2004).forEach { (min, id) ->
            grpTime.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "${min}m"
                this.id = id
                setTextColor(0xFFFFFFFF.toInt())
                strokeColor = android.content.res.ColorStateList.valueOf(green)
            })
        }
        grpTime.check(2002)

        val btnPlay = MaterialButton(this).apply {
            text = getString(R.string.play)
            setBackgroundColor(greenLight)
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnPlay.setOnClickListener {
            if (playing) {
                stopAll()
                tvStatus.text = getString(R.string.sleep_stopped)
                btnPlay.text = getString(R.string.play)
                tvLeft.text = "00:00"
            } else {
                val min = when (grpTime.checkedButtonId) {
                    2001 -> 15; 2003 -> 45; 2004 -> 60; else -> 30
                }
                TonePlayer.playAppRaw(this, soundRaw, loop = true)
                playing = true
                btnPlay.text = getString(R.string.stop)
                leftMs = min * 60_000L
                timer?.cancel()
                timer = object : CountDownTimer(leftMs, 1000) {
                    override fun onTick(ms: Long) {
                        leftMs = ms
                        val s = ms / 1000
                        tvLeft.text = "%02d:%02d".format(s / 60, s % 60)
                        tvStatus.text = getString(R.string.sleep_playing)
                    }
                    override fun onFinish() {
                        stopAll()
                        tvLeft.text = "00:00"
                        tvStatus.text = getString(R.string.sleep_done)
                        btnPlay.text = getString(R.string.play)
                    }
                }.start()
            }
        }

        body.addView(tvStatus)
        body.addView(tvLeft)
        body.addView(TextView(this).apply {
            text = getString(R.string.sound)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, pad, 0, 8)
        })
        body.addView(grpSound)
        body.addView(TextView(this).apply {
            text = getString(R.string.auto_off_after)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, pad, 0, 8)
        })
        body.addView(grpTime)
        body.addView(btnPlay)
        root.addView(body)
        setContentView(root)
        window.statusBarColor = bg
        window.navigationBarColor = bg
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
