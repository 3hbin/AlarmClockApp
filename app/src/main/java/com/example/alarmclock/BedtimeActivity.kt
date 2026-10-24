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
            status.text = if (on) {
                "Đang bật · ngủ " + BedtimeScheduler.durationText(bh, bm, wh, wm) +
                    "\nNhắc lúc %02d:%02d · báo thức dậy %02d:%02d mỗi ngày".format(bh, bm, wh, wm)
            } else {
                "Đã tắt · bật để nhắc giờ đi ngủ và tự đặt báo thức giờ dậy"
            }
        }
        paint()
        bed.setOnClickListener { pick(true, ::paint) }
        wake.setOnClickListener { pick(false, ::paint) }
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("bed_on", checked).apply()
            try { BedtimeScheduler.apply(this) } catch (_: Exception) {}
            paint()
            val msg = if (checked) {
                if (!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled())
                    "Đã bật lịch ngủ. Hãy bật thông báo cho app để nhận nhắc giờ đi ngủ."
                else "Đã bật lịch ngủ: nhắc đi ngủ và đặt báo thức giờ dậy"
            } else "Đã tắt lịch ngủ và xóa báo thức giờ dậy"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
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

        refreshEvents()
        findViewById<android.view.View>(R.id.btnOpenCalendar).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowEvents).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
    }

    /** Thẻ "Sự kiện sắp diễn ra": ngày lễ hôm nay hoặc ngày lễ gần nhất trong danh sách ngày lễ Việt Nam. */
    private fun refreshEvents() {
        val monthTv = findViewById<TextView>(R.id.tvEventMonth)
        val dayTv = findViewById<TextView>(R.id.tvEventDay)
        val body = findViewById<TextView>(R.id.tvEventBody)
        val now = Calendar.getInstance()
        val startOfToday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val items = try {
            VietnamHolidays.agendaItems(now, 8).filter { it.timeMs >= startOfToday }
        } catch (_: Exception) { emptyList() }
        val first = items.firstOrNull()
        if (first == null) {
            monthTv.text = "THÁNG ${now.get(Calendar.MONTH) + 1}"
            dayTv.text = now.get(Calendar.DAY_OF_MONTH).toString()
            return
        }
        val c = Calendar.getInstance().apply { timeInMillis = first.timeMs }
        monthTv.text = "THÁNG ${c.get(Calendar.MONTH) + 1}"
        dayTv.text = c.get(Calendar.DAY_OF_MONTH).toString()
        val en = Lang.isEn()
        val sb = StringBuilder()
        if (first.isToday) {
            sb.append(if (en) "🎉 Today: " else "🎉 Hôm nay: ").append(first.title)
        } else {
            val days = ((first.timeMs - startOfToday) / 86_400_000L).toInt()
            sb.append(first.title).append("\n")
                .append(if (en) "In $days days" else "Còn $days ngày")
        }
        for (it in items.drop(1).take(2)) {
            val cc = Calendar.getInstance().apply { timeInMillis = it.timeMs }
            sb.append("\n• ").append(it.title)
                .append(" (%02d/%02d)".format(cc.get(Calendar.DAY_OF_MONTH), cc.get(Calendar.MONTH) + 1))
        }
        body.text = sb.toString()
    }

    private fun pick(bed: Boolean, after: () -> Unit) {
        val h = prefs.getInt(if (bed) "bed_h" else "wake_h", if (bed) 23 else 7)
        val m = prefs.getInt(if (bed) "bed_m" else "wake_m", 0)
        TimePickerDialog(this, { _, hour, minute ->
            prefs.edit()
                .putInt(if (bed) "bed_h" else "wake_h", hour)
                .putInt(if (bed) "bed_m" else "wake_m", minute)
                .apply()
            try { BedtimeScheduler.apply(this) } catch (_: Exception) {}
            after()
        }, h, m, true).show()
    }

    override fun onResume() {
        super.onResume()
        refreshEvents()
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        try { Motion.playTabEnter(this) } catch (_: Exception) {}
    }
}
