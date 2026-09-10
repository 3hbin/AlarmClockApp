package com.example.alarmclock

import android.os.Bundle
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class AddEditAlarmActivity : AppCompatActivity() {

    private var alarmId = -1
    private var repeatMode = Alarm.REPEAT_DAILY
    private var snoozeMinutes = 5
    private var ringtoneUri: String? = "app:soft_chime"
    private var label = "Báo thức"
    private var ringMinutes = 5
    private var vibrate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_alarm)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarEdit)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_save_alarm) {
                save(); true
            } else false
        }

        val npH = findViewById<NumberPicker>(R.id.npHour)
        val npM = findViewById<NumberPicker>(R.id.npMinute)
        npH.minValue = 0; npH.maxValue = 23
        npM.minValue = 0; npM.maxValue = 59
        npH.setFormatter { "%02d".format(it) }
        npM.setFormatter { "%02d".format(it) }

        alarmId = intent.getIntExtra("ALARM_ID", -1)
        val existing = if (alarmId >= 0) AlarmRepository(this).getAlarms().find { it.id == alarmId } else null
        if (existing != null) {
            toolbar.title = "Sửa báo thức"
            npH.value = existing.hour
            npM.value = existing.minute
            repeatMode = existing.repeatMode
            snoozeMinutes = existing.snoozeMinutes
            ringtoneUri = existing.ringtoneUri ?: "app:soft_chime"
            label = existing.label
        } else {
            toolbar.title = "Thêm báo thức"
            val now = java.util.Calendar.getInstance()
            npH.value = now.get(java.util.Calendar.HOUR_OF_DAY)
            npM.value = now.get(java.util.Calendar.MINUTE)
        }
        refreshRows()

        findViewById<android.view.View>(R.id.rowRepeat).setOnClickListener {
            val items = arrayOf("Chỉ đổ chuông một lần", "Hàng ngày", "Thứ 2 - Thứ 6")
            val cur = when (repeatMode) {
                Alarm.REPEAT_ONCE -> 0
                Alarm.REPEAT_WEEKDAYS -> 2
                else -> 1
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Lặp lại")
                .setSingleChoiceItems(items, cur) { d, which ->
                    repeatMode = when (which) {
                        0 -> Alarm.REPEAT_ONCE
                        2 -> Alarm.REPEAT_WEEKDAYS
                        else -> Alarm.REPEAT_DAILY
                    }
                    refreshRows(); d.dismiss()
                }.show()
        }
        findViewById<android.view.View>(R.id.rowSound).setOnClickListener {
            val items = arrayOf("Chuông êm", "Chuông nhẹ")
            MaterialAlertDialogBuilder(this)
                .setTitle("Âm thanh")
                .setItems(items) { _, which ->
                    ringtoneUri = if (which == 1) "app:soft_bell" else "app:soft_chime"
                    refreshRows()
                }.show()
        }
        findViewById<SwitchMaterial>(R.id.swVibrate).setOnCheckedChangeListener { _, on -> vibrate = on }
        findViewById<android.view.View>(R.id.rowLabel).setOnClickListener {
            val et = EditText(this).apply { setText(label) }
            MaterialAlertDialogBuilder(this)
                .setTitle("Nhãn")
                .setView(et)
                .setPositiveButton("Lưu") { _, _ ->
                    label = et.text.toString().ifBlank { "Báo thức" }
                    refreshRows()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
        findViewById<android.view.View>(R.id.rowDuration).setOnClickListener {
            val items = arrayOf("1 phút", "5 phút", "10 phút", "30 phút")
            MaterialAlertDialogBuilder(this)
                .setTitle("Thời lượng đổ chuông")
                .setItems(items) { _, which ->
                    ringMinutes = listOf(1, 5, 10, 30)[which]
                    refreshRows()
                }.show()
        }
        findViewById<android.view.View>(R.id.rowSnooze).setOnClickListener {
            val items = arrayOf("5 phút", "10 phút", "15 phút")
            MaterialAlertDialogBuilder(this)
                .setTitle("Báo lại")
                .setItems(items) { _, which ->
                    snoozeMinutes = listOf(5, 10, 15)[which]
                    refreshRows()
                }.show()
        }
    }

    private fun refreshRows() {
        findViewById<TextView>(R.id.tvRepeatValue).text = when (repeatMode) {
            Alarm.REPEAT_ONCE -> "Chỉ một lần"
            Alarm.REPEAT_WEEKDAYS -> "T2–T6"
            else -> "Hàng ngày"
        }
        findViewById<TextView>(R.id.tvSoundValue).text =
            if (ringtoneUri == "app:soft_bell") "Chuông nhẹ" else "Chuông êm"
        findViewById<TextView>(R.id.tvLabelValue).text = label
        findViewById<TextView>(R.id.tvDurationValue).text = "$ringMinutes phút"
        findViewById<TextView>(R.id.tvSnoozeValue).text = "$snoozeMinutes phút"
        findViewById<SwitchMaterial>(R.id.swVibrate).isChecked = vibrate
    }

    private fun save() {
        val hour = findViewById<NumberPicker>(R.id.npHour).value
        val minute = findViewById<NumberPicker>(R.id.npMinute).value
        val repo = AlarmRepository(this)
        val list = repo.getAlarms()
        if (alarmId >= 0) {
            val existing = list.find { it.id == alarmId }
            if (existing != null) {
                AlarmScheduler.cancel(this, existing.id)
                existing.hour = hour
                existing.minute = minute
                existing.label = label
                existing.repeatMode = repeatMode
                existing.snoozeMinutes = snoozeMinutes
                existing.ringtoneUri = ringtoneUri
                if (existing.isEnabled) AlarmScheduler.schedule(this, existing)
            }
        } else {
            val created = Alarm(
                id = repo.getNextId(),
                hour = hour,
                minute = minute,
                isEnabled = true,
                label = label,
                repeatMode = repeatMode,
                snoozeMinutes = snoozeMinutes,
                ringtoneUri = ringtoneUri
            )
            list.add(created)
            AlarmScheduler.schedule(this, created)
        }
        repo.saveAlarms(list)
        try { CloudSyncHelper.pushAlarms(this, list) {} } catch (_: Exception) {}
        finish()
    }
}
