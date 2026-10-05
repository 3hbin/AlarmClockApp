package com.example.alarmclock

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

class BedtimeActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bedtime", MODE_PRIVATE) }
    private var soundRaw = R.raw.sleep_ocean
    private val sounds = listOf(
        Triple(R.raw.sleep_ocean, "Tiếng sóng", "Ocean"),
        Triple(R.raw.sleep_rain, "Mưa đêm", "Night rain"),
        Triple(R.raw.sleep_stream, "Suối", "Stream"),
        Triple(R.raw.sleep_wind, "Gió đêm", "Night wind"),
        Triple(R.raw.sleep_forest, "Rừng", "Forest")
    )

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bedtime)
        try { ThemeFix.apply(this, findViewById(android.R.id.content)) } catch (_: Exception) {}
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        findViewById<MaterialToolbar>(R.id.toolbarBedtime).title = getString(R.string.title_bedtime)
        try { BottomNavHelper.bind(this, findViewById(R.id.curvedNav), 5) } catch (_: Exception) {}

        val bed = findViewById<TextView>(R.id.tvBedTime)
        val wake = findViewById<TextView>(R.id.tvWakeTime)
        val status = findViewById<TextView>(R.id.tvBedStatus)
        val switch = findViewById<MaterialSwitch>(R.id.switchBedtime)
        fun paint() {
            val bh = prefs.getInt("bed_h", 23)
            val bm = prefs.getInt("bed_m", 0)
            val wh = prefs.getInt("wake_h", 7)
            val wm = prefs.getInt("wake_m", 0)
            bed.text = "%02d:%02d".format(bh, bm)
            wake.text = "%02d:%02d".format(wh, wm)
            val on = prefs.getBoolean("bed_on", false)
            switch.isChecked = on
            status.text = if (on) "Đang bật" else "Đã tắt"
        }
        paint()
        bed.setOnClickListener { pick(true, ::paint) }
        wake.setOnClickListener { pick(false, ::paint) }
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("bed_on", checked).apply()
            paint()
        }
        findViewById<android.view.View>(R.id.rowSchedule).setOnClickListener { pick(true, ::paint) }

        soundRaw = prefs.getInt("sound_raw", R.raw.sleep_ocean)
        val name = findViewById<TextView>(R.id.tvSoundName)
        name.text = sounds.firstOrNull { it.first == soundRaw }?.second ?: "Tiếng sóng"
        findViewById<ImageButton>(R.id.btnPlaySleep).setOnClickListener {
            startActivity(Intent(this, SleepSoundActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowSleepSound).setOnClickListener {
            startActivity(Intent(this, SleepSoundActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnPickSound).setOnClickListener {
            startActivity(Intent(this, SleepSoundActivity::class.java))
        }

        val cal = Calendar.getInstance()
        findViewById<TextView>(R.id.tvEventMonth).text = "THÁNG ${cal.get(Calendar.MONTH) + 1}"
        findViewById<TextView>(R.id.tvEventDay).text = cal.get(Calendar.DAY_OF_MONTH).toString()
        findViewById<android.view.View>(R.id.btnOpenCalendar).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowEvents).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
    }

    private fun pick(bed: Boolean, after: () -> Unit) {
        val h = prefs.getInt(if (bed) "bed_h" else "wake_h", if (bed) 23 else 7)
        val m = prefs.getInt(if (bed) "bed_m" else "wake_m", 0)
        TimePickerDialog(this, { _, hour, minute ->
            prefs.edit()
                .putInt(if (bed) "bed_h" else "wake_h", hour)
                .putInt(if (bed) "bed_m" else "wake_m", minute)
                .apply()
            after()
        }, h, m, true).show()
    }

    override fun onResume() {
        super.onResume()
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        try { Motion.playTabEnter(this) } catch (_: Exception) {}
    }
}
