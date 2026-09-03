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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import android.view.View
import android.provider.CalendarContract
import android.text.InputType
import android.widget.TextView
import android.widget.ScrollView
import android.widget.LinearLayout
import android.view.MenuItem
import android.view.Menu
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
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


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

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 1) Google Sign-In OAuth
            val account = GoogleSignInHelper.handleResult(this, result.data)
            if (account != null) {
                Toast.makeText(this, "Đã đăng nhập: ${account.email}", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            // 2) AccountPicker fallback
            val email = GoogleSignInHelper.handleAccountPicker(this, result.data)
            if (!email.isNullOrBlank()) {
                Toast.makeText(this, "Đã đăng nhập: $email", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Đã hủy chọn tài khoản", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Đăng nhập chưa thành công — thử Nhập email", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chuyển sang ViewPager2 host — hết nháy trắng khi đổi tab
        startActivity(
            android.content.Intent(this, MainTabActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(MainTabActivity.EXTRA_TAB, intent.getIntExtra(MainTabActivity.EXTRA_TAB, 0))
        )
        finish()
        try { overridePendingTransition(0, 0) } catch (_: Exception) {}
        return
        // (code cũ giữ để compile reference — unreachable)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            binding.toolbar.title = getString(R.string.app_name)
            binding.toolbar.setTitleTextColor(0xFFFFFFFF.toInt())
            // Overflow 3 chấm màu trắng trên nền xanh
            binding.toolbar.overflowIcon?.setTint(0xFFFFFFFF.toInt())
        } catch (_: Exception) {}

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
                updateNextAlarmBanner()
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

    private fun updateNextAlarmBanner() {
        try {
            val enabled = alarms.filter { it.isEnabled }
            if (enabled.isEmpty()) {
                binding.tvNextAlarm.text = "Chưa có báo thức — bấm +"
                return
            }
            val now = java.util.Calendar.getInstance()
            var bestMs = Long.MAX_VALUE
            var best: Alarm? = null
            for (a in enabled) {
                val c = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, a.hour)
                    set(java.util.Calendar.MINUTE, a.minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (timeInMillis <= now.timeInMillis) add(java.util.Calendar.DAY_OF_YEAR, 1)
                    if (a.repeatMode == Alarm.REPEAT_WEEKDAYS) {
                        while (get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY ||
                            get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY) {
                            add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                }
                if (c.timeInMillis < bestMs) {
                    bestMs = c.timeInMillis
                    best = a
                }
            }
            val diff = bestMs - now.timeInMillis
            val h = (diff / 3_600_000).toInt()
            val m = ((diff % 3_600_000) / 60_000).toInt()
            val timeStr = "%02d:%02d".format(best!!.hour, best.minute)
            val left = when {
                h > 24 -> "còn ${h / 24} ngày"
                h > 0 -> "còn ${h}giờ ${m}phút"
                else -> "còn ${m} phút"
            }
            binding.tvNextAlarm.text = "⏰ Tiếp theo $timeStr · $left"
        } catch (_: Exception) {
            try { binding.tvNextAlarm.text = "⏰ Báo thức" } catch (_: Exception) {}
        }
    }

    private fun showAlarmHistory() {
        val lines = AlarmHistory.formatLines(this)
        val msg = if (lines.isEmpty()) "Chưa có lịch sử tắt/báo lại."
        else lines.take(30).joinToString("\n")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Lịch sử báo thức")
            .setMessage(msg)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Xóa lịch sử") { _, _ ->
                AlarmHistory.clear(this)
                android.widget.Toast.makeText(this, "Đã xóa lịch sử", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateDailyTip() {
        val tips = listOf(
            "💡 Thử thách dễ giúp dậy đúng giờ mà không mất ngủ",
            "🌅 Đặt báo thức T2–T6 để cuối tuần ngủ thêm",
            "🎙 Hey Google: đặt báo thức bằng Báo thức Challenge",
            "🔋 Báo thức 1 lần tự tắt — tiết kiệm pin",
            "📱 Xoay ngang để xem danh sách rộng hơn",
            "🔤 Cài đặt → Cỡ chữ: bé / vừa / to",
            "📜 Bấm dòng báo thức tiếp theo để xem lịch sử"
        )
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        try { binding.tvDailyTip.text = tips[day % tips.size] } catch (_: Exception) {}
    }

    private fun updateCurrentTime() {
        val now = Calendar.getInstance()
        val timeFormat = SimpleDateFormat(if (AppSettings.isUse24h(this)) "HH:mm" else "hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault())
        binding.tvCurrentTime.text = timeFormat.format(now.time)
        updateNextAlarmBanner()
        binding.tvCurrentDate.text = dateFormat.format(now.time).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }

    
    override fun onResume() {
        super.onResume()
        try { binding.curvedNav.selectIndex(0, animate = false) } catch (_: Exception) {}
        DynamicIconHelper.applySafe(this)
        // Sau khi tắt báo thức / kêu xong → đồng bộ list + gỡ thông báo status
        try { reloadAlarmsFromDisk() } catch (_: Exception) {}
        maybeRequireAppLock()
    }

    override fun onStop() {
        super.onStop()
        // Rời app (Home / app khác) → khóa lại như ngân hàng
        if (AppSettings.isAppLockEnabled(this)) {
            AppSettings.appUnlockedThisSession = false
            AppSettings.settingsUnlockedThisSession = false
        }
    }

    private var appLockDialogShowing = false

    private fun maybeRequireAppLock() {
        if (!AppSettings.isAppLockEnabled(this)) return
        if (AppSettings.appUnlockedThisSession) return
        if (appLockDialogShowing) return
        appLockDialogShowing = true
        // Che nội dung khi đang khóa
        try { binding.root.alpha = 0.15f } catch (_: Exception) {}
        val input = EditText(this).apply {
            hint = "Nhập PIN mở app"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🔒 App đã khóa")
            .setMessage("Nhập PIN để mở Báo thức Challenge")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Mở") { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (AppSettings.checkSettingsPin(this, pin)) {
                    AppSettings.appUnlockedThisSession = true
                    AppSettings.settingsUnlockedThisSession = true
                    appLockDialogShowing = false
                    try { binding.root.alpha = 1f } catch (_: Exception) {}
                    Toast.makeText(this, "Đã mở khóa", Toast.LENGTH_SHORT).show()
                } else {
                    appLockDialogShowing = false
                    Toast.makeText(this, "Sai PIN", Toast.LENGTH_SHORT).show()
                    maybeRequireAppLock()
                }
            }
            .setNeutralButton("Quên PIN?") { _, _ ->
                appLockDialogShowing = false
                SettingsLockHelper.requireUnlock(this) {
                    AppSettings.appUnlockedThisSession = true
                    try { binding.root.alpha = 1f } catch (_: Exception) {}
                }
            }
            .setOnDismissListener {
                // Nếu vẫn chưa mở → hỏi lại
                if (!AppSettings.appUnlockedThisSession && AppSettings.isAppLockEnabled(this)) {
                    appLockDialogShowing = false
                    binding.root.post { maybeRequireAppLock() }
                }
            }
            .show()
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
        val nmCompat = NotificationManagerCompat.from(this)
        val nm = getSystemService(NotificationManager::class.java)

        if (enabledCount == 0) {
            // Tắt hẳn thông báo "Báo thức đang bật"
            try { nmCompat.cancel(1001) } catch (_: Exception) {}
            try { nm?.cancel(1001) } catch (_: Exception) {}
            try { nmCompat.cancel(AlarmNotificationHelper.NOTIF_ID_RINGING) } catch (_: Exception) {}
            try { nm?.cancel(AlarmNotificationHelper.NOTIF_ID_RINGING) } catch (_: Exception) {}
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
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        try {
            nmCompat.notify(1001, notification)
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    /** Đồng bộ lại list từ đĩa (sau khi báo thức kêu / tắt ở màn khác). */
    private fun reloadAlarmsFromDisk() {
        alarms.clear()
        alarms.addAll(repo.getAlarms().onEach {
            if (it.ringtoneUri.isNullOrEmpty()) it.ringtoneUri = "app:soft_chime"
        })
        try { adapter.notifyDataSetChanged() } catch (_: Exception) {}
        updateNextAlarmBanner()
        updateOngoingNotification()
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



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Cập nhật dòng phiên bản
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val ver = pInfo.versionName ?: "3.87"
            menu.findItem(R.id.menu_version)?.title = "ℹ️ Phiên bản v$ver"
        } catch (_: Exception) {}
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_app_lock -> {
                showAppLockMenu()
                return true
            }
            R.id.menu_calendar -> {
                showCalendarMenu()
                return true
            }
            R.id.menu_google -> {
                showGoogleLoginMenu()
                return true
            }
            R.id.menu_history -> {
                showAlarmHistoryDialog()
                return true
            }
            R.id.menu_version -> {
                showVersionDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAppLockMenu() {
        val hasPin = AppSettings.hasSettingsPin(this)
        val fullOn = AppSettings.isAppLockEnabled(this)
        val items = if (hasPin) {
            arrayOf(
                "Mở khóa / vào Cài đặt",
                "Đổi PIN",
                if (fullOn) "Tắt khóa cả app (chỉ còn khóa Cài đặt)" else "Bật khóa cả app khi mở (như ngân hàng)",
                "Tắt hết App lock (xóa PIN)",
                "Khóa lại ngay"
            )
        } else {
            arrayOf("Bật App lock (đặt PIN + khóa cả app)")
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("🔒 App lock")
            .setItems(items) { _, which ->
                when {
                    !hasPin && which == 0 -> promptSetPin(enableFull = true)
                    hasPin && which == 0 -> SettingsLockHelper.requireUnlock(this) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    hasPin && which == 1 -> promptSetPin(enableFull = fullOn)
                    hasPin && which == 2 -> {
                        if (fullOn) {
                            AppSettings.setAppLockEnabled(this, false)
                            Toast.makeText(this, "Chỉ còn khóa Cài đặt", Toast.LENGTH_SHORT).show()
                        } else {
                            AppSettings.setAppLockEnabled(this, true)
                            AppSettings.appUnlockedThisSession = true
                            Toast.makeText(this, "Đã bật khóa cả app — mỗi lần mở cần PIN", Toast.LENGTH_LONG).show()
                        }
                    }
                    hasPin && which == 3 -> {
                        AppSettings.clearSettingsPin(this)
                        AppSettings.setAppLockEnabled(this, false)
                        AppSettings.settingsUnlockedThisSession = true
                        AppSettings.appUnlockedThisSession = true
                        Toast.makeText(this, "Đã tắt App lock", Toast.LENGTH_SHORT).show()
                    }
                    hasPin && which == 4 -> {
                        AppSettings.appUnlockedThisSession = false
                        AppSettings.settingsUnlockedThisSession = false
                        Toast.makeText(this, "Đã khóa — cần PIN để tiếp tục", Toast.LENGTH_SHORT).show()
                        maybeRequireAppLock()
                    }
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun promptSetPin(enableFull: Boolean = true) {
        val input = EditText(this).apply {
            hint = "PIN ≥ 4 số"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Đặt PIN App lock")
            .setMessage(if (enableFull) "PIN này khóa cả app mỗi lần mở (và Cài đặt)." else "Đổi PIN hiện tại.")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN cần ≥ 4 số", Toast.LENGTH_SHORT).show()
                } else {
                    AppSettings.setSettingsPin(this, pin)
                    if (enableFull) AppSettings.setAppLockEnabled(this, true)
                    AppSettings.settingsUnlockedThisSession = true
                    AppSettings.appUnlockedThisSession = true
                    Toast.makeText(
                        this,
                        if (enableFull) "Đã bật khóa cả app + PIN" else "Đã đổi PIN",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showCalendarMenu() {
        startActivity(Intent(this, CalendarAgendaActivity::class.java))
    }

    private fun openNationalDayEvent() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.MONTH, java.util.Calendar.SEPTEMBER)
            set(java.util.Calendar.DAY_OF_MONTH, 2)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        // Nếu đã qua 2/9 năm nay → năm sau
        val now = java.util.Calendar.getInstance()
        if (cal.before(now) && !(cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
                && cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR))) {
            cal.add(java.util.Calendar.YEAR, 1)
        }
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Quốc khánh Việt Nam")
                putExtra(CalendarContract.Events.DESCRIPTION, "Ngày Quốc khánh 2/9 — nhắc từ Báo thức Challenge")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được lịch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAddCalendarEvent() {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "Sự kiện từ Báo thức Challenge")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được lịch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showGoogleLoginMenu() {
        val view = layoutInflater.inflate(R.layout.dialog_google_sign_in, null)
        val status = view.findViewById<TextView>(R.id.tvGoogleStatus)
        val current = GoogleSignInHelper.lastSignedInEmail(this)
            ?: AppSettings.getRecoveryEmail(this)
        val name = AppSettings.getGoogleDisplayName(this)
        status.text = when {
            current.isNotBlank() && name.isNotBlank() -> "Đã lưu: $name\n$current"
            current.isNotBlank() -> "Đã lưu: $current"
            else -> "Chưa đăng nhập"
        }
        view.findViewById<View>(R.id.btnGoogleSignIn).setOnClickListener {
            try {
                // OAuth chuẩn (đã có SHA-1 + oauth_client)
                googleSignInLauncher.launch(GoogleSignInHelper.signInIntent(this))
            } catch (e: Exception) {
                try {
                    googleSignInLauncher.launch(GoogleSignInHelper.accountPickerIntent())
                } catch (e2: Exception) {
                    Toast.makeText(this, "Không mở Google: ${e2.message}", Toast.LENGTH_LONG).show()
                    showGoogleEmailFallback()
                }
            }
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Nhập email") { _, _ -> showGoogleEmailFallback() }
            .setNegativeButton("Đăng xuất") { _, _ ->
                GoogleSignInHelper.signOut(this)
                Toast.makeText(this, "Đã đăng xuất Google trên máy", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showGoogleEmailFallback() {
        val current = AppSettings.getRecoveryEmail(this)
        val input = EditText(this).apply {
            hint = "you@gmail.com"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(current)
            setPadding(48, 32, 48, 32)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Email Google")
            .setMessage("Lưu Gmail để khôi phục PIN và gửi nhật ký.")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val email = input.text?.toString()?.trim().orEmpty()
                if (email.contains("@")) {
                    AppSettings.setRecoveryEmail(this, email)
                    Toast.makeText(this, "Đã lưu $email", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun sendHistoryToGmail(email: String) {
        val lines = AlarmHistory.formatLines(this)
        val body = if (lines.isEmpty()) {
            "Chưa có nhật ký báo thức.\n\n— Báo thức Challenge v3.87"
        } else {
            "Nhật ký báo thức:\n\n" + lines.joinToString("\n") + "\n\n— Báo thức Challenge v3.87"
        }
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "[Báo thức Challenge] Nhật ký ${System.currentTimeMillis() % 100000}")
                putExtra(Intent.EXTRA_TEXT, body)
            }
            startActivity(Intent.createChooser(intent, "Gửi qua Gmail"))
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được Gmail: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAlarmHistoryDialog() {
        val lines = AlarmHistory.formatLines(this)
        val msg = if (lines.isEmpty()) "Chưa có nhật ký.\nBáo thức tắt/báo lại sẽ được ghi tại đây."
        else lines.take(40).joinToString("\n")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📋 Nhật ký báo thức")
            .setMessage(msg)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Xóa nhật ký") { _, _ ->
                AlarmHistory.clear(this)
                Toast.makeText(this, "Đã xóa nhật ký", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Gửi Gmail") { _, _ ->
                val email = AppSettings.getRecoveryEmail(this)
                if (email.isBlank()) {
                    Toast.makeText(this, "Vào «Đăng nhập Google» để lưu email trước", Toast.LENGTH_LONG).show()
                    showGoogleLoginMenu()
                } else {
                    sendHistoryToGmail(email)
                }
            }
            .show()
    }

    private fun showVersionDialog() {
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "3.87"
        } catch (_: Exception) { "3.87" }
        val code = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode
        } catch (_: Exception) { 95L }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("ℹ️ Phiên bản")
            .setMessage("Báo thức Challenge\nPhiên bản: v$ver\nMã bản dựng: $code\n\nQuét mặt · Thử thách · App lock · Lịch lễ")
            .setPositiveButton("OK", null)
            .show()
    }

}
