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
        val startTomorrow = alarm?.routineTomorrow ?: intent.getBooleanExtra("routineTomorrow", true)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val bar = MaterialToolbar(this).apply {
            title = Lang.t("Quy trình Gemini", "Gemini routine")
            setNavigationIcon(R.drawable.ic_close)
            setNavigationOnClickListener { finish() }
        }
        root.addView(bar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        body.addView(TextView(this).apply {
            text = Lang.t("Khi tôi tắt chuông báo, quy trình này sẽ", "When I dismiss the alarm, this routine will")
            textSize = 16f
            setPadding(0, 0, 0, pad)
        })

        val cbOn = MaterialCheckBox(this).apply {
            text = Lang.t("Bật quy trình khi tắt chuông", "Run routine when alarm is dismissed")
            isChecked = startOn
        }
        val cbWeather = MaterialCheckBox(this).apply {
            text = Lang.t("Cho tôi biết thông tin thời tiết", "Tell me the weather")
            isChecked = startWeather
        }
        val cbCal = MaterialCheckBox(this).apply {
            text = Lang.t("Cho tôi biết sự kiện trên lịch hôm nay", "Tell me today's calendar events")
            isChecked = startCal
        }
        val cbTask = MaterialCheckBox(this).apply {
            text = Lang.t("Cho tôi biết những việc cần làm hôm nay", "Tell me today's tasks")
            isChecked = startTasks
        }
        val cbTomorrow = MaterialCheckBox(this).apply {
            text = Lang.t("Cho tôi biết mai có sự kiện hay không", "Tell me if I have events tomorrow")
            isChecked = startTomorrow
        }
        listOf(cbOn, cbWeather, cbCal, cbTask, cbTomorrow).forEach { cb ->
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
            text = Lang.t("Việc cần làm: ", "Tasks: ") + AppSettings.getRoutineTasksText(this@GeminiRoutineActivity).ifBlank { Lang.t("Chưa đặt", "Not set") }
            setPadding(0, pad / 2, 0, pad)
        }
        body.addView(tvTasks)
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = Lang.t("+ Thêm việc cần làm", "+ Add tasks")
            setOnClickListener {
                val et = EditText(this@GeminiRoutineActivity).apply {
                    setText(AppSettings.getRoutineTasksText(this@GeminiRoutineActivity))
                    hint = Lang.t("Ví dụ: mang vở, tập 15 phút", "Example: pack bag, stretch 15 minutes")
                }
                MaterialAlertDialogBuilder(this@GeminiRoutineActivity)
                    .setTitle(Lang.t("Việc cần làm hôm nay", "Today's tasks"))
                    .setView(et)
                    .setPositiveButton(Lang.t("Lưu", "Save")) { _, _ ->
                        AppSettings.setRoutineTasksText(this@GeminiRoutineActivity, et.text.toString().trim())
                        tvTasks.text = Lang.t("Việc cần làm: ", "Tasks: ") + et.text.toString().trim().ifBlank { Lang.t("Chưa đặt", "Not set") }
                    }
                    .setNegativeButton(Lang.t("Hủy", "Cancel"), null)
                    .show()
            }
        })

        val tvVoice = TextView(this).apply {
            text = "Giọng AI: " + AppSettings.getTtsVoiceLabel(this@GeminiRoutineActivity)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        body.addView(MaterialButton(this).apply {
            text = Lang.t("Chọn giọng nam / nữ", "Choose male / female voice")
            setOnClickListener { pickVoice(tvVoice) }
        })
        body.addView(tvVoice)

        val btnSave = MaterialButton(this).apply {
            text = Lang.t("Lưu", "Save")
            setOnClickListener {
                if (alarm != null) {
                    val list = repo.getAlarms()
                    list.find { it.id == alarmId }?.let { a ->
                        a.routineOn = cbOn.isChecked
                        a.routineWeather = cbWeather.isChecked
                        a.routineCalendar = cbCal.isChecked
                        a.routineTasks = cbTask.isChecked
                        a.routineTomorrow = cbTomorrow.isChecked
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
                        .putExtra("routineTomorrow", cbTomorrow.isChecked)
                )
                finish()
            }
        }
        body.addView(btnSave)
        root.addView(body)
        setContentView(root)
    }

    private fun pickVoice(labelView: TextView) {
        val engine = android.speech.tts.TextToSpeech(this) { }
        labelView.postDelayed({
            val voices = try { engine.voices } catch (_: Exception) { emptySet() }
            val options = TtsVoiceCatalog.fromEngine(voices)
            engine.shutdown()
            val names = options.map { it.label }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle(Lang.t("Chọn giọng AI", "Choose AI voice"))
                .setItems(names) { _, which ->
                    val opt = options[which]
                    TtsVoiceCatalog.apply(this, opt)
                    labelView.text = "Giọng AI: " + opt.label
                    val preview = TtsHelper(this)
                    preview.onDone = { preview.shutdown() }
                    preview.speak("Xin chào. Đây là giọng " + opt.label)
                }
                .show()
        }, 700)
    }
}
