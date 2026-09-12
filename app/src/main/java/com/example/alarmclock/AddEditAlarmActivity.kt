package com.example.alarmclock

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class AddEditAlarmActivity : AppCompatActivity() {

    private var alarmId = -1
    private var hour = 7
    private var minute = 0
    private var repeatMode = Alarm.REPEAT_DAILY
    private var snoozeMinutes = 5
    private var ringtoneUri: String? = AppRingtones.DEFAULT_ALARM
    private var label = "Báo thức"
    private var challengeType = Alarm.CHALLENGE_NONE
    private var skipHolidays = false
    private var strictAnti = false
    private var useCrescendo = true
    private var voiceNote: String? = null
    private var isEdit = false

    private val pickTone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != RESULT_OK) return@registerForActivityResult
        ringtoneUri = res.data?.getStringExtra(RingtonePickerActivity.EXTRA_URI) ?: ringtoneUri
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_alarm)

        alarmId = intent.getIntExtra("ALARM_ID", -1)
        val existing = if (alarmId >= 0) AlarmRepository(this).getAlarms().find { it.id == alarmId } else null
        if (existing != null) {
            isEdit = true
            hour = existing.hour
            minute = existing.minute
            repeatMode = existing.repeatMode
            snoozeMinutes = existing.snoozeMinutes
            ringtoneUri = existing.ringtoneUri ?: AppRingtones.DEFAULT_ALARM
            label = existing.label
            challengeType = existing.challengeType
            skipHolidays = existing.skipHolidays
            strictAnti = existing.isStrictAntiSnooze
            useCrescendo = existing.useCrescendo
            voiceNote = existing.voiceNote
        } else {
            val now = Calendar.getInstance()
            hour = now.get(Calendar.HOUR_OF_DAY)
            minute = now.get(Calendar.MINUTE)
        }

        findViewById<android.view.View>(R.id.btnEditTime).setOnClickListener { showTimePicker() }
        findViewById<android.view.View>(R.id.tvBigTime).setOnClickListener { showTimePicker() }

        bindChips()
        findViewById<android.view.View>(R.id.rowSound).setOnClickListener {
            pickTone.launch(
                android.content.Intent(this, RingtonePickerActivity::class.java)
                    .putExtra(RingtonePickerActivity.EXTRA_CURRENT, ringtoneUri)
            )
        }
        findViewById<SwitchMaterial>(R.id.swVibrate).setOnCheckedChangeListener { _, on ->
            AppSettings.setVibrate(this, on)
        }
        findViewById<android.view.View>(R.id.rowChallenge).setOnClickListener { pickChallenge() }
        findViewById<android.view.View>(R.id.rowSnooze).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Thời gian báo lại")
                .setItems(arrayOf("5 phút", "10 phút", "15 phút")) { _, which ->
                    snoozeMinutes = listOf(5, 10, 15)[which]
                    refreshUi()
                }.show()
        }
        findViewById<android.view.View>(R.id.rowVoice).setOnClickListener {
            val et = EditText(this).apply { setText(voiceNote ?: ""); hint = "Lời chào khi thức" }
            MaterialAlertDialogBuilder(this)
                .setTitle("Ghi chú giọng nói (TTS)")
                .setView(et)
                .setPositiveButton("Lưu") { _, _ ->
                    voiceNote = et.text.toString().ifBlank { null }
                    refreshUi()
                }
                .setNegativeButton("Xóa") { _, _ ->
                    voiceNote = null
                    refreshUi()
                }
                .show()
        }

        findViewById<MaterialCheckBox>(R.id.cbAntiSnooze).setOnCheckedChangeListener { _, on -> strictAnti = on }
        findViewById<MaterialCheckBox>(R.id.cbSkipHoliday).setOnCheckedChangeListener { _, on -> skipHolidays = on }
        findViewById<MaterialCheckBox>(R.id.cbCrescendo).setOnCheckedChangeListener { _, on -> useCrescendo = on }

        findViewById<EditText>(R.id.etLabelInline).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    // Giữ nguyên chữ đang gõ, kể cả khi đang xóa trắng.
                    label = s?.toString() ?: ""
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            }
        )

        findViewById<MaterialButton>(R.id.btnSchedule).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnDelete).apply {
            visibility = if (isEdit) android.view.View.VISIBLE else android.view.View.INVISIBLE
            setOnClickListener { deleteAlarm() }
        }
        findViewById<android.view.View>(R.id.btnCloseSheet).setOnClickListener { finish() }

        refreshUi()
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, h, m ->
            hour = h; minute = m; refreshUi()
        }, hour, minute, true).show()
    }

    private fun bindChips() {
        val ids = listOf(
            R.id.chipT2 to Calendar.MONDAY,
            R.id.chipT3 to Calendar.TUESDAY,
            R.id.chipT4 to Calendar.WEDNESDAY,
            R.id.chipT5 to Calendar.THURSDAY,
            R.id.chipT6 to Calendar.FRIDAY,
            R.id.chipT7 to Calendar.SATURDAY,
            R.id.chipCN to Calendar.SUNDAY
        )
        ids.forEach { (id, _) ->
            findViewById<Chip>(id).setOnCheckedChangeListener { _, _ ->
                repeatMode = modeFromChips()
                refreshUi()
            }
        }
        applyChipsFromMode()
    }

    private fun applyChipsFromMode() {
        val weekdays = setOf(R.id.chipT2, R.id.chipT3, R.id.chipT4, R.id.chipT5, R.id.chipT6)
        val all = weekdays + R.id.chipT7 + R.id.chipCN
        val on = when (repeatMode) {
            Alarm.REPEAT_ONCE -> emptySet()
            Alarm.REPEAT_WEEKDAYS -> weekdays
            else -> all
        }
        all.forEach { findViewById<Chip>(it).isChecked = it in on }
    }

    private fun modeFromChips(): Int {
        val t2 = findViewById<Chip>(R.id.chipT2).isChecked
        val t3 = findViewById<Chip>(R.id.chipT3).isChecked
        val t4 = findViewById<Chip>(R.id.chipT4).isChecked
        val t5 = findViewById<Chip>(R.id.chipT5).isChecked
        val t6 = findViewById<Chip>(R.id.chipT6).isChecked
        val t7 = findViewById<Chip>(R.id.chipT7).isChecked
        val cn = findViewById<Chip>(R.id.chipCN).isChecked
        val weekdays = t2 && t3 && t4 && t5 && t6 && !t7 && !cn
        val daily = t2 && t3 && t4 && t5 && t6 && t7 && cn
        return when {
            daily -> Alarm.REPEAT_DAILY
            weekdays -> Alarm.REPEAT_WEEKDAYS
            else -> Alarm.REPEAT_ONCE
        }
    }

    private fun pickChallenge() {
        val types = listOf(
            Alarm.CHALLENGE_NONE, Alarm.CHALLENGE_MATH, Alarm.CHALLENGE_SHAKE,
            Alarm.CHALLENGE_READ, Alarm.CHALLENGE_FACE, Alarm.CHALLENGE_BIOMETRIC,
            Alarm.CHALLENGE_TAP200, Alarm.CHALLENGE_MATH10, Alarm.CHALLENGE_ALL_EASY
        )
        val labels = types.map { Alarm.challengeLabel(it) }.toTypedArray()
        val cur = types.indexOf(challengeType).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Thử thách khi tắt báo thức")
            .setSingleChoiceItems(labels, cur) { d, which ->
                challengeType = types[which]
                refreshUi(); d.dismiss()
            }.show()
    }

    private fun nextAlarmText(): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val sameDay = now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) "Chuông báo sắp tới: Hôm nay" else "Chuông báo sắp tới: Ngày mai"
    }

    private fun refreshUi() {
        findViewById<TextView>(R.id.tvBigTime).text = "%02d:%02d".format(hour, minute)
        findViewById<TextView>(R.id.tvNextHint).text = nextAlarmText()
        val etLabel = findViewById<EditText>(R.id.etLabelInline)
        if (etLabel.text?.toString() != label) {
            etLabel.setText(label)
            etLabel.setSelection(etLabel.text?.length ?: 0)
        }
        findViewById<TextView>(R.id.tvSoundValue).text = when {
            ringtoneUri == "silent:" -> "Im lặng"
            ringtoneUri?.startsWith("app:") == true -> AppRingtones.labelOf(ringtoneUri)
            else -> CustomRingtones.list(this).find { it.uri == ringtoneUri }?.name
                ?: "Nhạc chuông tùy chọn"
        }
        findViewById<TextView>(R.id.tvChallengeValue).text = Alarm.challengeLabel(challengeType)
        findViewById<TextView>(R.id.tvSnoozeValue).text = "$snoozeMinutes phút"
        findViewById<SwitchMaterial>(R.id.swVibrate).isChecked = AppSettings.isVibrate(this)
        findViewById<MaterialCheckBox>(R.id.cbAntiSnooze).isChecked = strictAnti
        findViewById<MaterialCheckBox>(R.id.cbSkipHoliday).isChecked = skipHolidays
        findViewById<MaterialCheckBox>(R.id.cbCrescendo).isChecked = useCrescendo
        findViewById<TextView>(R.id.tvVoiceValue).text = voiceNote ?: "Không"
    }

    private fun collectLabel(): String {
        val typed = findViewById<EditText>(R.id.etLabelInline).text?.toString()?.trim()
        return if (typed.isNullOrBlank()) label else typed
    }

    private fun save() {
        label = collectLabel()
        repeatMode = modeFromChips()
        val repo = AlarmRepository(this)
        val list = repo.getAlarms()
        if (isEdit) {
            val existing = list.find { it.id == alarmId } ?: return finish()
            AlarmScheduler.cancel(this, existing.id)
            existing.hour = hour
            existing.minute = minute
            existing.label = label
            existing.repeatMode = repeatMode
            existing.snoozeMinutes = snoozeMinutes
            existing.ringtoneUri = ringtoneUri
            existing.challengeType = challengeType
            existing.skipHolidays = skipHolidays
            existing.isStrictAntiSnooze = strictAnti
            existing.useCrescendo = useCrescendo
            existing.voiceNote = voiceNote
            existing.isEnabled = true
            AlarmScheduler.schedule(this, existing)
        } else {
            val created = Alarm(
                id = repo.getNextId(),
                hour = hour,
                minute = minute,
                isEnabled = true,
                label = label,
                repeatMode = repeatMode,
                snoozeMinutes = snoozeMinutes,
                ringtoneUri = ringtoneUri,
                challengeType = challengeType,
                skipHolidays = skipHolidays,
                isStrictAntiSnooze = strictAnti,
                useCrescendo = useCrescendo,
                voiceNote = voiceNote
            )
            list.add(created)
            AlarmScheduler.schedule(this, created)
        }
        repo.saveAlarms(list)
        try { CloudSyncHelper.pushAlarms(this, list) {} } catch (_: Exception) {}
        finish()
    }

    private fun deleteAlarm() {
        if (!isEdit) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Xoá báo thức?")
            .setPositiveButton("Xoá") { _, _ ->
                val repo = AlarmRepository(this)
                val list = repo.getAlarms()
                AlarmScheduler.cancel(this, alarmId)
                repo.saveAlarms(list.filter { it.id != alarmId })
                finish()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
