package com.example.alarmclock

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class SleepSoundActivity : AppCompatActivity() {
    private var timer: CountDownTimer? = null
    private var leftMs = 0L
    private var playing = false
    private var soundRaw = R.raw.sleep_rain

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.sleep_sounds)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val green = 0xFF2E7D32.toInt()
        val greenLight = 0xFF81C784.toInt()
        val bg = 0xFF0A0A0A.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(pad, pad, pad, pad)
        }
        val tvStatus = TextView(this).apply {
            text = getString(R.string.sleep_sounds)
            textSize = 18f
            setTextColor(Color.WHITE)
        }
        val tvLeft = TextView(this).apply {
            text = "00:00"
            textSize = 56f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val grpSound = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        fun addSound(id: Int, name: String, raw: Int) {
            grpSound.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.id = id
                text = name
                setTextColor(Color.WHITE)
                strokeColor = android.content.res.ColorStateList.valueOf(green)
                tag = raw
            })
        }
        addSound(1001, getString(R.string.sleep_rain), R.raw.sleep_rain)
        addSound(1002, getString(R.string.sleep_forest), R.raw.sleep_forest)
        addSound(1003, getString(R.string.sleep_ocean), R.raw.sleep_ocean)
        grpSound.check(1001)
        grpSound.addOnButtonCheckedListener { _, cid, checked ->
            if (!checked) return@addOnButtonCheckedListener
            soundRaw = when (cid) {
                1002 -> R.raw.sleep_forest
                1003 -> R.raw.sleep_ocean
                else -> R.raw.sleep_rain
            }
            if (playing) TonePlayer.playAppRaw(this, soundRaw, loop = true)
        }

        val grpTime = MaterialButtonToggleGroup(this).apply { isSingleSelection = true }
        listOf(15 to 2001, 30 to 2002, 45 to 2003, 60 to 2004).forEach { (min, id) ->
            grpTime.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.id = id
                text = "${min}p"
                setTextColor(Color.WHITE)
                strokeColor = android.content.res.ColorStateList.valueOf(green)
            })
        }
        grpTime.check(2002)

        val btnPlay = MaterialButton(this).apply {
            text = getString(R.string.play)
            setBackgroundColor(green)
            setTextColor(Color.WHITE)
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

        val labelColor = 0xFFCCCCCC.toInt()
        root.addView(tvStatus)
        root.addView(tvLeft)
        root.addView(TextView(this).apply {
            text = getString(R.string.sound); setTextColor(labelColor); setPadding(0, pad, 0, 8)
        })
        root.addView(grpSound)
        root.addView(TextView(this).apply {
            text = getString(R.string.auto_off_after); setTextColor(labelColor); setPadding(0, pad, 0, 8)
        })
        root.addView(grpTime)
        root.addView(btnPlay)
        window.statusBarColor = bg
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
