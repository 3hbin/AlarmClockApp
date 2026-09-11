package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class BedtimeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bedtime)
        findViewById<MaterialToolbar>(R.id.toolbarBedtime).title = "Giờ đi ngủ"
        try { BottomNavHelper.bind(this, findViewById(R.id.curvedNav), 5) } catch (_: Exception) {}

        findViewById<android.view.View>(R.id.rowSchedule).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowSleepSound).setOnClickListener {
            startActivity(Intent(this, SleepSoundActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowEvents).setOnClickListener {
            startActivity(Intent(this, CalendarAgendaActivity::class.java))
        }
    }
}
