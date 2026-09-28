package com.example.alarmclock

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Màn hình khoá bấm icon báo thức sẽ thấy giờ kế tiếp. */
class NextAlarmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101D42.toInt())
            setPadding(48, 120, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Báo thức kế tiếp"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
        }
        val time = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 42f
            val alarms = AlarmRepository(this@NextAlarmActivity).getAlarms().filter { it.isEnabled }
            text = alarms.minByOrNull { it.hour * 60 + it.minute }?.let {
                "%02d:%02d".format(it.hour, it.minute)
            } ?: "Chưa có"
        }
        root.addView(title)
        root.addView(time)
        setContentView(root)
    }
}
