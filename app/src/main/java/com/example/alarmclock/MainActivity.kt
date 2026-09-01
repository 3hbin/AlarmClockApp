package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarmclock.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: AlarmRepository
    private lateinit var adapter: AlarmAdapter
    private val alarms = mutableListOf<Alarm>()
    private var selectedRingtoneUri: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeUpdater = object : Runnable {
        override fun run() {
            updateCurrentTime()
            handler.postDelayed(this, 1000)
        }
    }

    private val ringtonePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri?.toString()
            repo.setGlobalRingtone(selectedRingtoneUri)
            Toast.makeText(this, getString(R.string.choose_ringtone) + " OK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AlarmRepository(this)
        selectedRingtoneUri = repo.getGlobalRingtone() ?: "app:soft_chime"
        alarms.addAll(repo.getAlarms().onEach {
            if (it.ringtoneUri.isNullOrEmpty()) it.ringtoneUri = "app:soft_chime"
        })

        createNotificationChannel()
        checkPermissions()

        adapter = AlarmAdapter(
            alarms,
            onToggle = { alarm ->
                repo.saveAlarms(alarms)
                if (alarm.isEnabled) {
                    AlarmScheduler.schedule(this, alarm)
                } else {
                    AlarmScheduler.cancel(this, alarm.id)
                }
                updateOngoingNotification()
            },
            onDelete = { alarm ->
                confirmDelete(alarm)
            },
            onEdit = { alarm ->
                showEditDialog(alarm)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.itemAnimator?.apply {
            addDuration = 180
            removeDuration = 160
            moveDuration = 180
            changeDuration = 120
        }

        binding.swipeRefresh.setColorSchemeColors(0xFF3F51B5.toInt(), 0xFF7E57C2.toInt())
        binding.swipeRefresh.setOnRefreshListener {
            alarms.clear()
            alarms.addAll(repo.getAlarms())
            adapter.notifyDataSetChanged()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.shimmer.hide()
        binding.loadingAnim.applyBrandDefault()
        binding.loadingAnim.visibility = android.view.View.GONE

        binding.loadingAnim.stop()

        // Báo thức nhanh (ngủ gật)
        binding.btnQuick5.setOnClickListener { addQuickAlarm(5) }
        binding.btnQuick10.setOnClickListener { addQuickAlarm(10) }
        binding.btnQuick15.setOnClickListener { addQuickAlarm(15) }
        binding.btnQuick30.setOnClickListener { addQuickAlarm(30) }


        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }

        binding.fabAdd.setOnLongClickListener {
            startActivity(Intent(this, FeaturesActivity::class.java))
            true
        }

        setupCurvedNav()

        updateCurrentTime()
        handler.post(timeUpdater)
        updateOngoingNotification()
        Motion.fadeScaleIn(binding.tvCurrentTime)

        // Tap time → analog clock
        binding.tvCurrentTime.setOnClickListener { showAnalogClock() }
        binding.tvCurrentTime.isClickable = true

        // Long press clock to toggle flash
        binding.tvCurrentTime.setOnLongClickListener {
            val enabled = !repo.isFlashEnabled()
            repo.setFlashEnabled(enabled)
            Toast.makeText(
                this,
                if (enabled) "Đã bật nháy đèn flash khi báo thức" else "Đã tắt nháy đèn flash",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeUpdater)
    }

    private fun updateCurrentTime() {
        val now = Calendar.getInstance()
        val timeFormat = SimpleDateFormat(if (AppSettings.isUse24h(this)) "HH:mm" else "hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault())
        binding.tvCurrentTime.text = timeFormat.format(now.time)
        binding.tvCurrentDate.text = dateFormat.format(now.time).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    
    override fun onResume() {
        super.onResume()
        try { binding.curvedNav.selectIndex(0, animate = false) } catch (_: Exception) {}
        // Cập nhật icon launcher theo buổi hiện tại
        DynamicIconHelper.applySafe(this)
    }

    private fun setupCurvedNav() {
        BottomNavHelper.bind(this, binding.curvedNav, 0)
    }

    
    private fun showAnalogClock() {
        val view = layoutInflater.inflate(R.layout.dialog_analog_clock, null)
        val tvDigital = view.findViewById<android.widget.TextView>(R.id.tvAnalogDigital)
        val tvDate = view.findViewById<android.widget.TextView>(R.id.tvAnalogDate)
        val timeFormat = java.text.SimpleDateFormat(
            if (AppSettings.isUse24h(this)) "HH:mm:ss" else "hh:mm:ss a",
            java.util.Locale.getDefault()
        )
        val dateFormat = java.text.SimpleDateFormat("EEEE, dd/MM/yyyy", java.util.Locale.getDefault())
        val update = object : Runnable {
            override fun run() {
                val now = java.util.Calendar.getInstance().time
                tvDigital.text = timeFormat.format(now)
                tvDate.text = dateFormat.format(now).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
                }
                view.postDelayed(this, 1000)
            }
        }
        view.post(update)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Đóng") { d, _ ->
                view.removeCallbacks(update)
                d.dismiss()
            }
            .show()
    }


    private fun showFabMenu() {
        val items = arrayOf(
            "➕ Thêm báo thức",
            "📷 Test quét mặt (camera)",
            "😊 Test 10 biểu cảm dễ"
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Bạn muốn làm gì?")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddDialog()
                    1 -> startActivity(
                        Intent(this, FaceChallengeActivity::class.java).apply {
                            putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE)
                        }
                    )
                    2 -> startActivity(
                        Intent(this, FaceChallengeActivity::class.java).apply {
                            putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR)
                        }
                    )
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showAddDialog() = showAlarmEditor(null)

    private fun showEditDialog(alarm: Alarm) = showAlarmEditor(alarm)

    /** null = thêm mới; có alarm = sửa. */
    private fun showAlarmEditor(existing: Alarm?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_alarm, null)
        // Giới hạn chiều cao để ScrollView cuộn được (tránh cắt thử thách)
        val maxH = (resources.displayMetrics.heightPixels * 0.65f).toInt()
        dialogView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH
        )
        dialogView.minimumHeight = (resources.displayMetrics.heightPixels * 0.35f).toInt()
        val etLabel = dialogView.findViewById<EditText>(R.id.etLabel)
        val rgRepeat = dialogView.findViewById<RadioGroup>(R.id.rgRepeat)
        val rgSnooze = dialogView.findViewById<RadioGroup>(R.id.rgSnooze)
        val rgChallenge = dialogView.findViewById<RadioGroup>(R.id.rgChallenge)
        val btnChooseRingtone = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChooseRingtone)
        val tvRingtoneStatus = dialogView.findViewById<android.widget.TextView>(R.id.tvRingtoneStatus)

        if (existing != null) {
            etLabel.setText(existing.label)
            when (existing.repeatMode) {
                Alarm.REPEAT_ONCE -> rgRepeat.check(R.id.rbOnce)
                Alarm.REPEAT_WEEKDAYS -> rgRepeat.check(R.id.rbWeekdays)
                else -> rgRepeat.check(R.id.rbDaily)
            }
            when (existing.snoozeMinutes) {
                10 -> rgSnooze.check(R.id.rbSnooze10)
                15 -> rgSnooze.check(R.id.rbSnooze15)
                else -> rgSnooze.check(R.id.rbSnooze5)
            }
            when (existing.challengeType) {
                Alarm.CHALLENGE_MATH -> rgChallenge.check(R.id.rbChallengeMath)
                Alarm.CHALLENGE_SHAKE -> rgChallenge.check(R.id.rbChallengeShake)
                Alarm.CHALLENGE_FACE -> rgChallenge.check(R.id.rbChallengeFace)
                Alarm.CHALLENGE_READ -> rgChallenge.check(R.id.rbChallengeRead)
                Alarm.CHALLENGE_MATH10 -> rgChallenge.check(R.id.rbChallengeMath10)
                Alarm.CHALLENGE_SHAKE100 -> rgChallenge.check(R.id.rbChallengeShake100)
                Alarm.CHALLENGE_TAP200 -> rgChallenge.check(R.id.rbChallengeTap200)
                Alarm.CHALLENGE_FACE_EXPR -> rgChallenge.check(R.id.rbChallengeFaceExpr)
                Alarm.CHALLENGE_BIOMETRIC -> rgChallenge.check(R.id.rbChallengeBio)
                Alarm.CHALLENGE_ALL -> rgChallenge.check(R.id.rbChallengeAll)
                Alarm.CHALLENGE_ALL_EASY -> rgChallenge.check(R.id.rbChallengeAllEasy)
                else -> rgChallenge.check(R.id.rbChallengeNone)
            }
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbSkipHolidays).isChecked = existing.skipHolidays
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbStrictAntiSnooze).isChecked = existing.isStrictAntiSnooze
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbCrescendo).isChecked = existing.useCrescendo
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVoiceNote)?.setText(existing.voiceNote.orEmpty())
            selectedRingtoneUri = existing.ringtoneUri
        }

        tvRingtoneStatus.text = if (selectedRingtoneUri != null) {
            getString(R.string.choose_ringtone) + " ✓"
        } else {
            getString(R.string.default_ringtone)
        }

        btnChooseRingtone.setOnClickListener {
            pickRingtone()
            tvRingtoneStatus.postDelayed({
                tvRingtoneStatus.text = if (selectedRingtoneUri != null) {
                    getString(R.string.choose_ringtone) + " ✓"
                } else {
                    getString(R.string.default_ringtone)
                }
            }, 500)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing != null) "Sửa báo thức" else getString(R.string.add_alarm))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.choose_time)) { _, _ ->
                val label = etLabel.text.toString().ifBlank { getString(R.string.app_name) }
                val repeatMode = when (rgRepeat.checkedRadioButtonId) {
                    R.id.rbOnce -> Alarm.REPEAT_ONCE
                    R.id.rbWeekdays -> Alarm.REPEAT_WEEKDAYS
                    else -> Alarm.REPEAT_DAILY
                }
                val snoozeMinutes = when (rgSnooze.checkedRadioButtonId) {
                    R.id.rbSnooze10 -> 10
                    R.id.rbSnooze15 -> 15
                    else -> 5
                }
                val bioGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgChallenge2)
                val challengeType = when (rgChallenge.checkedRadioButtonId) {
                    R.id.rbChallengeBio -> Alarm.CHALLENGE_BIOMETRIC
                    R.id.rbChallengeAll -> Alarm.CHALLENGE_ALL
                    R.id.rbChallengeAllEasy -> Alarm.CHALLENGE_ALL_EASY
                    R.id.rbChallengeMath -> Alarm.CHALLENGE_MATH
                    R.id.rbChallengeShake -> Alarm.CHALLENGE_SHAKE
                    R.id.rbChallengeFace -> Alarm.CHALLENGE_FACE
                    R.id.rbChallengeRead -> Alarm.CHALLENGE_READ
                    R.id.rbChallengeMath10 -> Alarm.CHALLENGE_MATH10
                    R.id.rbChallengeShake100 -> Alarm.CHALLENGE_SHAKE100
                    R.id.rbChallengeTap200 -> Alarm.CHALLENGE_TAP200
                    R.id.rbChallengeFaceExpr -> Alarm.CHALLENGE_FACE_EXPR
                    else -> Alarm.CHALLENGE_NONE
                }
                val shakeTarget = when (challengeType) {
                    Alarm.CHALLENGE_SHAKE100 -> 100
                    Alarm.CHALLENGE_SHAKE -> 10
                    else -> 10
                }
                val skipHolidays = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbSkipHolidays).isChecked
                val isStrict = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbStrictAntiSnooze).isChecked
                    || challengeType == Alarm.CHALLENGE_ALL
                    || challengeType == Alarm.CHALLENGE_ALL_EASY
                val useCrescendo = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbCrescendo).isChecked
                val voiceNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVoiceNote).text?.toString()?.takeIf { it.isNotBlank() }

                val initH = existing?.hour ?: 7
                val initM = existing?.minute ?: 0
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        if (existing != null) {
                            AlarmScheduler.cancel(this, existing.id)
                            existing.hour = hour
                            existing.minute = minute
                            existing.label = label
                            existing.repeatMode = repeatMode
                            existing.snoozeMinutes = snoozeMinutes
                            existing.ringtoneUri = selectedRingtoneUri ?: "app:soft_chime"
                            existing.challengeType = challengeType
                            existing.shakeTargetCount = shakeTarget
                            existing.skipHolidays = skipHolidays
                            existing.isStrictAntiSnooze = isStrict
                            existing.voiceNote = voiceNote
                            existing.useCrescendo = useCrescendo
                            if (existing.isEnabled) AlarmScheduler.schedule(this, existing)
                            repo.saveAlarms(alarms)
                            adapter.notifyDataSetChanged()
                            updateOngoingNotification()
                            Toast.makeText(this, "Đã cập nhật: $label $hour:%02d".format(minute), Toast.LENGTH_SHORT).show()
                        } else {
                            val newAlarm = Alarm(
                                id = repo.getNextId(),
                                hour = hour,
                                minute = minute,
                                isEnabled = true,
                                label = label,
                                repeatMode = repeatMode,
                                snoozeMinutes = snoozeMinutes,
                                ringtoneUri = selectedRingtoneUri ?: "app:soft_chime",
                                challengeType = challengeType,
                                shakeTargetCount = shakeTarget,
                                skipHolidays = skipHolidays,
                                isStrictAntiSnooze = isStrict,
                                voiceNote = voiceNote,
                                useCrescendo = useCrescendo
                            )
                            alarms.add(newAlarm)
                            repo.saveAlarms(alarms)
                            AlarmScheduler.schedule(this, newAlarm)
                            adapter.notifyDataSetChanged()
                            updateOngoingNotification()
                            Toast.makeText(
                                this,
                                getString(R.string.added, label, hour, minute),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    initH, initM, true
                ).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete(alarm: Alarm) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_title))
            .setMessage(getString(R.string.delete_message, alarm.label, alarm.hour, alarm.minute))
            .setPositiveButton(getString(R.string.dismiss)) { _, _ ->
                AlarmScheduler.cancel(this, alarm.id)
                alarms.remove(alarm)
                repo.saveAlarms(alarms)
                adapter.notifyDataSetChanged()
                updateOngoingNotification()
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickRingtone() {
        val choices = arrayOf(
            "Chuông êm (trong app)",
            "Chuông nhẹ chuông (trong app)",
            "Chọn nhạc hệ thống…"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.choose_ringtone))
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        selectedRingtoneUri = "app:soft_chime"
                        Toast.makeText(this, "Chuông êm", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        selectedRingtoneUri = "app:soft_bell"
                        Toast.makeText(this, "Chuông nhẹ", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.choose_ringtone))
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (selectedRingtoneUri != null && selectedRingtoneUri!!.startsWith("content")) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(selectedRingtoneUri))
                            }
                        }
                        ringtonePicker.launch(intent)
                    }
                }
            }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_status",
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when alarms are active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateOngoingNotification() {
        val enabledCount = alarms.count { it.isEnabled }
        val manager = NotificationManagerCompat.from(this)

        if (enabledCount == 0) {
            manager.cancel(1001)
            return
        }

        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "alarm_status")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, enabledCount))
            .setOngoing(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            manager.notify(1001, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.permission_needed))
                    .setMessage(getString(R.string.permission_needed))
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            })
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
        // Camera for flash
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
        // Samsung/OEM: bỏ tối ưu pin để báo thức kêu khi khóa màn
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !pm.isIgnoringBatteryOptimizations(packageName)
            ) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } catch (_: Exception) {
        }
        // Android 14+: full-screen intent permission
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Tạo báo thức một lần sau [minutes] phút (ngủ gật / power nap). */
    private fun addQuickAlarm(minutes: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, minutes)
        }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        val alarm = Alarm(
            id = repo.getNextId(),
            hour = hour,
            minute = minute,
            isEnabled = true,
            label = "Ngủ gật +$minutes phút",
            repeatMode = Alarm.REPEAT_ONCE,
            challengeType = Alarm.CHALLENGE_NONE,
            ringtoneUri = "app:soft_chime",
            useCrescendo = true
        )
        alarms.add(alarm)
        repo.saveAlarms(alarms)
        AlarmScheduler.schedule(this, alarm)
        adapter.notifyDataSetChanged()
        val timeStr = String.format("%02d:%02d", hour, minute)
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Báo thức $timeStr (sau $minutes phút)",
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }


}
