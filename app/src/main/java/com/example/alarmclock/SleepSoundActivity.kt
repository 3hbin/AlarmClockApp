package com.example.alarmclock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SleepSoundActivity : AppCompatActivity() {
    private val sounds = listOf(
        Triple(R.raw.sleep_ocean, "Tiếng sóng", "Ocean"),
        Triple(R.raw.sleep_rain, "Mưa đêm", "Night rain"),
        Triple(R.raw.sleep_stream, "Suối", "Stream"),
        Triple(R.raw.sleep_wind, "Gió đêm", "Night wind"),
        Triple(R.raw.sleep_forest, "Rừng", "Forest")
    )
    private var raw = R.raw.sleep_ocean
    private var title = "Tiếng sóng"
    private var minutes = 30

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_sound)
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        val prefs = getSharedPreferences("bedtime", MODE_PRIVATE)
        raw = prefs.getInt("sound_raw", R.raw.sleep_ocean)
        title = sounds.firstOrNull { it.first == raw }?.second ?: "Tiếng sóng"
        minutes = prefs.getInt("sleep_minutes", 30)

        val tv = findViewById<TextView>(R.id.tvSleepTitle)
        val play = findViewById<ImageButton>(R.id.btnSleepPlay)
        val timer = findViewById<MaterialButton>(R.id.btnSleepTimer)
        val fx = findViewById<SleepWhiteFxView>(R.id.sleepFx)
        fun paint() {
            tv.text = title
            fx.playing = SleepSoundService.playing
            timer.text = if (minutes >= 60) "Dừng sau 1 giờ" else "Dừng sau $minutes phút"
            play.setImageResource(
                if (SleepSoundService.playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
        }
        paint()

        findViewById<ImageButton>(R.id.btnCloseSleep).setOnClickListener { finish() }
        play.setOnClickListener {
            if (SleepSoundService.playing) {
                stopService(Intent(this, SleepSoundService::class.java))
                SleepSoundService.playing = false
            } else {
                startSleep()
            }
            paint()
        }
        timer.setOnClickListener {
            val opts = arrayOf("10 phút", "20 phút", "30 phút", "40 phút", "50 phút", "1 giờ", "Tự chọn")
            val values = intArrayOf(10, 20, 30, 40, 50, 60, -1)
            MaterialAlertDialogBuilder(this)
                .setTitle("Dừng sau")
                .setSingleChoiceItems(opts, values.indexOf(minutes).coerceAtLeast(0)) { d, which ->
                    if (values[which] < 0) {
                        d.dismiss()
                        pickCustomSleepMinutes(prefs)
                        return@setSingleChoiceItems
                    }
                    minutes = values[which]
                    prefs.edit().putInt("sleep_minutes", minutes).apply()
                    if (SleepSoundService.playing) startSleep()
                    paint()
                    d.dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
        findViewById<TextView>(R.id.btnPickOther).setOnClickListener {
            val labels = sounds.map { Lang.t(this, it.second, it.third) }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sleep_sounds))
                .setItems(labels) { _, which ->
                    raw = sounds[which].first
                    title = sounds[which].second
                    prefs.edit().putInt("sound_raw", raw).apply()
                    if (SleepSoundService.playing) startSleep()
                    paint()
                }
                .show()
        }
    }

    private fun startSleep() {
        val i = Intent(this, SleepSoundService::class.java)
            .setAction(SleepSoundService.ACTION_PLAY)
            .putExtra(SleepSoundService.EXTRA_RAW, raw)
            .putExtra(SleepSoundService.EXTRA_TITLE, title)
            .putExtra(SleepSoundService.EXTRA_MINUTES, minutes)
        if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, i)
        else startService(i)
        SleepSoundService.playing = true
    }

    private fun pickCustomSleepMinutes(prefs: android.content.SharedPreferences) {
        val picker = android.widget.NumberPicker(this).apply {
            minValue = 1
            maxValue = 180
            value = minutes.coerceIn(1, 180)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Dừng sau bao nhiêu phút")
            .setView(picker)
            .setPositiveButton("Đặt") { _, _ ->
                minutes = picker.value
                prefs.edit().putInt("sleep_minutes", minutes).apply()
                if (SleepSoundService.playing) startSleep()
                findViewById<MaterialButton>(R.id.btnSleepTimer).text =
                    if (minutes >= 60) "Dừng sau ${minutes / 60} giờ ${minutes % 60} phút" else "Dừng sau $minutes phút"
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
