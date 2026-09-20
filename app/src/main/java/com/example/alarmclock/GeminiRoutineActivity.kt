package com.example.alarmclock

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Quy trình sau khi tắt chuông — giao diện gần Đồng hồ Google.
 * Đọc bằng giọng nói trong app (không mở Assistant hệ thống).
 */
class GeminiRoutineActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val repo = AlarmRepository(this)
        val alarm = if (alarmId >= 0) repo.getAlarms().find { it.id == alarmId } else null
        val startOn = alarm?.routineOn ?: intent.getBooleanExtra("routineOn", false)
        val startWeather = alarm?.routineWeather ?: intent.getBooleanExtra("routineWeather", true)
        val startCal = alarm?.routineCalendar ?: intent.getBooleanExtra("routineCalendar", true)
        val startTasks = alarm?.routineTasks ?: intent.getBooleanExtra("routineTasks", true)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val bar = MaterialToolbar(this).apply {
            title = "Quy trình Gemini"
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener { finish() }
        }
        root.addView(bar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        body.addView(TextView(this).apply {
            text = "Khi tôi tắt chuông báo, quy trình này sẽ"
            textSize = 16f
            setPadding(0, 0, 0, pad)
        })

        val cbOn = MaterialCheckBox(this).apply {
            text = "Bật quy trình khi tắt chuông"
            isChecked = startOn
        }
        val cbWeather = MaterialCheckBox(this).apply {
            text = "Cho tôi biết thông tin thời tiết"
            isChecked = startWeather
        }
        val cbCal = MaterialCheckBox(this).apply {
            text = "Cho tôi biết sự kiện trên lịch hôm nay"
            isChecked = startCal
        }
        val cbTask = MaterialCheckBox(this).apply {
            text = "Cho tôi biết những việc cần làm hôm nay"
            isChecked = startTasks
        }
        listOf(cbOn, cbWeather, cbCal, cbTask).forEach { cb ->
            body.addView(MaterialCardView(this).apply {
                radius = 16f * resources.displayMetrics.density
                cardElevation = 0f
                setContentPadding(pad / 2, pad / 3, pad / 2, pad / 3)
                addView(cb)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = pad / 2
                layoutParams = lp
            })
        }

        val tvTasks = TextView(this).apply {
            text = "Việc cần làm: " + AppSettings.getRoutineTasksText(this@GeminiRoutineActivity).ifBlank { "Chưa đặt" }
            setPadding(0, pad / 2, 0, pad)
        }
        body.addView(tvTasks)
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "+ Thêm việc cần làm"
            setOnClickListener {
                val et = EditText(this@GeminiRoutineActivity).apply {
                    setText(AppSettings.getRoutineTasksText(this@GeminiRoutineActivity))
                    hint = "Ví dụ: mang vở, tập 15 phút"
                }
                MaterialAlertDialogBuilder(this@GeminiRoutineActivity)
                    .setTitle("Việc cần làm hôm nay")
                    .setView(et)
                    .setPositiveButton("Lưu") { _, _ ->
                        AppSettings.setRoutineTasksText(this@GeminiRoutineActivity, et.text.toString().trim())
                        tvTasks.text = "Việc cần làm: " + et.text.toString().trim().ifBlank { "Chưa đặt" }
                    }
                    .setNegativeButton("Hủy", null)
                    .show()
            }
        })

        val btnSave = MaterialButton(this).apply {
            text = "Lưu"
            setOnClickListener {
                if (alarm != null) {
                    val list = repo.getAlarms()
                    list.find { it.id == alarmId }?.let { a ->
                        a.routineOn = cbOn.isChecked
                        a.routineWeather = cbWeather.isChecked
                        a.routineCalendar = cbCal.isChecked
                        a.routineTasks = cbTask.isChecked
                    }
                    repo.saveAlarms(list)
                }
                setResult(
                    Activity.RESULT_OK,
                    android.content.Intent()
                        .putExtra("routineOn", cbOn.isChecked)
                        .putExtra("routineWeather", cbWeather.isChecked)
                        .putExtra("routineCalendar", cbCal.isChecked)
                        .putExtra("routineTasks", cbTask.isChecked)
                )
                finish()
            }
        }
        body.addView(btnSave)
        root.addView(body)
        setContentView(root)
    }
}
