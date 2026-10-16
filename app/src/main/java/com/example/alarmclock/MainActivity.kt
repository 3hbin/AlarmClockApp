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
import android.widget.NumberPicker
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
            try { AlarmDueWatcher.fireIfDue(this@MainActivity) } catch (_: Exception) {}
            try { updateNextAlarmBanner() } catch (_: Exception) {}
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

    private var googleLoginDialog: androidx.appcompat.app.AlertDialog? = null

    private fun applySignedIn(email: String, name: String?) {
        val shown = name?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        AppSettings.setRecoveryEmail(this, email)
        AppSettings.setGoogleDisplayName(this, shown)
        googleLoginDialog?.dismiss()
        googleLoginDialog = null
        Toast.makeText(this, "Đã đăng nhập: $shown\n$email", Toast.LENGTH_LONG).show()
        try { CloudSyncHelper.syncOnLogin(this) } catch (_: Exception) {}
    }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = GoogleSignInHelper.handleResult(this, result.data)
                ?: try { com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this) } catch (_: Exception) { null }
            val email = account?.email?.trim().orEmpty()
            if (email.contains("@")) {
                applySignedIn(email, account?.displayName)
                try { BirthdayHelper.syncFromSignedInAccount(this, account) } catch (_: Exception) {}
                return@registerForActivityResult
            }
            val picked = GoogleSignInHelper.handleAccountPicker(this, result.data)
            if (!picked.isNullOrBlank()) {
                applySignedIn(picked, null)
                return@registerForActivityResult
            }
            if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Đã hủy chọn tài khoản", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Đăng nhập chưa thành công — thử Nhập email", Toast.LENGTH_LONG).show()
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        FacebookLoginHelper.callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // first-launch guide after layout
        if (WelcomeActivity.launchIfNeeded(this)) return
        if (DynamicIconHelper.applyOnUserOpen(this)) return
        try { TamperGuard.verifyInActivity(this) } catch (_: Throwable) {}
        binding = ActivityMainBinding.inflate(layoutInflater)
        try {
            binding.btnQuick5.text = "+${Lang.minLabel(5).replace("5 ","")}" 
        } catch (_: Exception) {}
        try { Lang.sync(this) } catch (_: Exception) {}
        setContentView(binding.root)
        try { CloudSyncHelper.restoreSilently(this) } catch (_: Exception) {}
        try {
            binding.btnQuick5.text = Lang.t(this, "+5 phút", "+5 min")
            binding.btnQuick10.text = Lang.t(this, "+10 phút", "+10 min")
            binding.btnQuick15.text = Lang.t(this, "+15 phút", "+15 min")
            binding.btnQuick30.text = Lang.t(this, "+30 phút", "+30 min")
            binding.btnQuick60.text = Lang.t(this, "+1 giờ", "+1 hour")
            binding.btnQuick120.text = Lang.t(this, "+2 giờ", "+2 hours")
            binding.btnQuickCustom.text = Lang.t(this, "Chỉnh", "Custom")
        } catch (_: Exception) {}
        try { EventManager.bind(findViewById(R.id.eventBanner), this) } catch (_: Exception) {}
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        binding.root.post { try { FirstLaunchDialog.show(this) } catch (_: Exception) {} }
        binding.root.post {
            when (intent?.getStringExtra("open")) {
                "history" -> showAlarmHistoryDialog()
                "cloud" -> showCloudCheck()
            }
            intent?.removeExtra("open")
        }
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        try { DynamicIconHelper.cancelBackgroundIconAlarms(this) } catch (_: Exception) {}
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(true)
            binding.toolbar.title = getString(R.string.app_name)
            val onPrimary = androidx.core.content.ContextCompat.getColor(this, R.color.on_primary)
            binding.toolbar.setTitleTextColor(onPrimary)
            // Overflow 3 chấm cùng màu chữ trên header (Material You)
            binding.toolbar.overflowIcon?.setTint(onPrimary)
        } catch (_: Exception) {}

        repo = AlarmRepository(this)
        selectedRingtoneUri = AppRingtones.systemAlarm(this)
        alarms.addAll(repo.getAlarms().onEach {
            if (it.ringtoneUri.isNullOrEmpty()) it.ringtoneUri = AppRingtones.systemAlarm(this)
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
            },
            onMore = { alarm -> showAlarmMore(alarm) }
        )
        alarms.sortBy { it.hour * 60 + it.minute }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        try { StretchOverscroll.attach(binding.recyclerView) } catch (_: Exception) {}
        binding.recyclerView.itemAnimator?.apply {
            addDuration = 180
            removeDuration = 160
            moveDuration = 180
            changeDuration = 120
        }

        binding.swipeRefresh.setColorSchemeColors(0xFF6750A4.toInt())
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(0x00FFFFFF)
        binding.swipeRefresh.setOnRefreshListener {
            val wavy = Md3Loading.showWavy(this, "Đang tải lại…")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { wavy.dismiss() } catch (_: Exception) {}
                binding.swipeRefresh.isRefreshing = false
            }, 1200)
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
        binding.btnQuick60.setOnClickListener { addQuickAlarm(60) }
        binding.btnQuick120.setOnClickListener { addQuickAlarm(120) }
        binding.btnQuickCustom.setOnClickListener { pickCustomNap() }


        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }

        binding.fabAdd.setOnLongClickListener { true }

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
            binding.emptyAlarms?.visibility = if (alarms.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvEmptyAlarms?.text = Lang.t(this, "Không có báo thức", "No alarms")
            if (enabled.isEmpty()) {
                binding.tvNextAlarm.text = Lang.t(this, "Chưa có báo thức — bấm +", "No alarm — tap +")
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
            val timeStr = "%02d:%02d".format(best!!.hour, best.minute)
            // Chỉ hiện "Đang kêu" khi báo thức thật sự đang đổ chuông phút này.
            // Đặt đúng phút hiện tại được hẹn sang ngày mai, không được coi là đang kêu.
            val actuallyRinging = RingGuard.alreadyFiredThisMinute(this, best.id) &&
                !RingGuard.isDismissed(this, best.id) &&
                !AlarmNotificationHelper.ringingTimedOut(this)
            if (actuallyRinging) {
                binding.tvNextAlarm.text = Lang.t(this, "Đang kêu • $timeStr", "Ringing • $timeStr")
                return
            }
            val diff = (bestMs - now.timeInMillis).coerceAtLeast(0)
            val nextDay = java.util.Calendar.getInstance().apply { timeInMillis = bestMs }
            val tomorrow = now.get(java.util.Calendar.DAY_OF_YEAR) != nextDay.get(java.util.Calendar.DAY_OF_YEAR) ||
                now.get(java.util.Calendar.YEAR) != nextDay.get(java.util.Calendar.YEAR)
            if (tomorrow) {
                binding.tvNextAlarm.text = Lang.t(this, "Ngày mai • $timeStr", "Tomorrow • $timeStr")
                return
            }
            val h = (diff / 3_600_000).toInt()
            val m = ((diff % 3_600_000) / 60_000).toInt()
            val s = ((diff % 60_000) / 1000).toInt()
            binding.tvNextAlarm.text = Lang.t(
                this,
                "Còn $h giờ $m phút $s giây • $timeStr",
                "$h h $m m $s s left • $timeStr"
            )
        } catch (_: Exception) {
            try { binding.tvNextAlarm.text = getString(R.string.alarm_default_label) } catch (_: Exception) {}
        }
    }

    private fun showAlarmHistory() {
        val lines = AlarmHistory.formatLines(this)
        val msg = if (lines.isEmpty()) Lang.t(this, "Chưa có lịch sử tắt/báo lại.", "No dismiss/snooze history yet.")
        else lines.take(30).joinToString("\n")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(Lang.t(this, "Lịch sử báo thức", "Alarm history"))
            .setMessage(msg)
            .setPositiveButton(Lang.t(this, "Đóng", "Close"), null)
            .setNeutralButton(Lang.t(this, "Xóa lịch sử", "Clear history")) { _, _ ->
                AlarmHistory.clear(this)
                android.widget.Toast.makeText(this, Lang.t(this, "Đã xóa lịch sử", "History cleared"), android.widget.Toast.LENGTH_SHORT).show()
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

    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try { forceShowUi() } catch (_: Exception) {}
    }

    override fun onResume() {
        if (LocationPlaceHelper.hasPermission(this)) {
            Thread { try { LocationPlaceHelper.resolve(this) } catch (_: Exception) {} }.start()
        }
        super.onResume()
        forceShowUi()
        try { binding.root.alpha = 1f } catch (_: Exception) {}
        try { Motion.playTabEnter(this) } catch (_: Exception) {}
        try { BottomNavHelper.bind(this, binding.curvedNav, 0) } catch (_: Exception) {}
        try { binding.curvedNav.selectIndex(0, animate = false) } catch (_: Exception) {}
        try { reloadAlarmsFromDisk() } catch (_: Exception) {}
        try { maybeRequireAppLock() } catch (_: Exception) {
            try { forceShowUi() } catch (_: Exception) {}
        }
    }

    private fun forceShowUi() {
        try {
            val ev = try { EventManager.palette().takeIf { EventManager.isThemeEnabled(this) } } catch (_: Exception) { null }
            val surface = ev?.surface ?: androidx.core.content.ContextCompat.getColor(this, R.color.surface)
            val primary = ev?.primary ?: androidx.core.content.ContextCompat.getColor(this, R.color.primary)
            window.decorView.setBackgroundColor(surface)
            window.statusBarColor = primary
            val night = androidx.core.graphics.ColorUtils.calculateLuminance(primary) <= 0.5
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                var flags = window.decorView.systemUiVisibility
                flags = if (night) flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                else flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                window.decorView.systemUiVisibility = flags
            }
            try { binding.tvNextAlarm.setTextColor(primary) } catch (_: Exception) {}
            try { EventManager.applyChrome(this) } catch (_: Exception) {}
        } catch (_: Exception) {}
        if (!::binding.isInitialized) return
        try {
            val surface = androidx.core.content.ContextCompat.getColor(this, R.color.surface)
            binding.root.alpha = 1f
            binding.root.visibility = android.view.View.VISIBLE
            binding.root.setBackgroundColor(surface)
            binding.root.post {
                try {
                    binding.root.requestLayout()
                    binding.root.invalidate()
                    binding.recyclerView.requestLayout()
                    binding.recyclerView.invalidate()
                } catch (_: Exception) {}
            }
            binding.root.postDelayed({
                try {
                    binding.root.requestLayout()
                    binding.root.invalidate()
                } catch (_: Exception) {}
            }, 50)
        } catch (_: Exception) {}
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
        try { binding.root.alpha = 1f } catch (_: Exception) {}
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
            .setPositiveButton(Lang.t(this, "Đóng", "Close")) { d, _ ->
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

    private fun showAddDialog() {
        startActivity(android.content.Intent(this, AddEditAlarmActivity::class.java))
    }

    private fun showEditDialog(alarm: Alarm) {
        startActivity(
            android.content.Intent(this, AddEditAlarmActivity::class.java)
                .putExtra("ALARM_ID", alarm.id)
        )
    }

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
            val rgG = dialogView.findViewById<RadioGroup>(R.id.rgGroup)
            when (existing.group) {
                "Học" -> rgG.check(R.id.rbGroupHoc)
                "Tập" -> rgG.check(R.id.rbGroupTap)
                "Làm việc" -> rgG.check(R.id.rbGroupWork)
                else -> rgG.check(R.id.rbGroupChung)
            }
            dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbWeekend).isChecked = existing.useWeekendSchedule
            val wh = if (existing.weekendHour >= 0) existing.weekendHour else existing.hour
            val wm = if (existing.weekendMinute >= 0) existing.weekendMinute else existing.minute
            dialogView.findViewById<android.widget.TextView>(R.id.tvWeekendTime).text = "%02d:%02d".format(wh, wm)
            dialogView.findViewById<android.widget.TextView>(R.id.tvWeekendTime).tag = intArrayOf(wh, wm)
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

        val tvWe = dialogView.findViewById<android.widget.TextView>(R.id.tvWeekendTime)
        if (tvWe.tag == null) tvWe.tag = intArrayOf(8, 0)
        dialogView.findViewById<android.view.View>(R.id.rowWeekendTime).setOnClickListener {
            val cur = tvWe.tag as? IntArray ?: intArrayOf(8, 0)
            TimePickerDialog(this, { _, h, m ->
                tvWe.tag = intArrayOf(h, m)
                tvWe.text = "%02d:%02d".format(h, m)
            }, cur[0], cur[1], true).show()
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
                val groupName = when (dialogView.findViewById<RadioGroup>(R.id.rgGroup).checkedRadioButtonId) {
                    R.id.rbGroupHoc -> "Học"
                    R.id.rbGroupTap -> "Tập"
                    R.id.rbGroupWork -> "Làm việc"
                    else -> "Chung"
                }
                val useWeekend = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbWeekend).isChecked
                val weArr = (dialogView.findViewById<android.widget.TextView>(R.id.tvWeekendTime).tag as? IntArray) ?: intArrayOf(8, 0)

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
                            existing.ringtoneUri = selectedRingtoneUri ?: AppRingtones.systemAlarm(this)
                            existing.challengeType = challengeType
                            existing.shakeTargetCount = shakeTarget
                            existing.skipHolidays = skipHolidays
                            existing.isStrictAntiSnooze = isStrict
                            existing.voiceNote = voiceNote
                            existing.useCrescendo = useCrescendo
                            existing.group = groupName
                            existing.useWeekendSchedule = useWeekend
                            existing.weekendHour = if (useWeekend) weArr[0] else -1
                            existing.weekendMinute = if (useWeekend) weArr[1] else -1
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
                                ringtoneUri = selectedRingtoneUri ?: AppRingtones.systemAlarm(this),
                                challengeType = challengeType,
                                shakeTargetCount = shakeTarget,
                                skipHolidays = skipHolidays,
                                isStrictAntiSnooze = isStrict,
                                voiceNote = voiceNote,
                                useCrescendo = useCrescendo,
                                group = groupName,
                                useWeekendSchedule = useWeekend,
                                weekendHour = if (useWeekend) weArr[0] else -1,
                                weekendMinute = if (useWeekend) weArr[1] else -1
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
                RingGuard.markDismissed(this, alarm.id)
                try { AlarmRingService.stop(this) } catch (_: Exception) {}
                try { TonePlayer.stop() } catch (_: Exception) {}
                try { AlarmNotificationHelper.cancelRinging(this) } catch (_: Exception) {}
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
        val builtIn = AppRingtones.alarms
        val choices = builtIn.map { it.label }.toMutableList().also { it.add("Chọn nhạc hệ thống…") }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Nhạc báo thức")
            .setItems(choices) { _, which ->
                when {
                    which in builtIn.indices -> {
                        selectedRingtoneUri = AppRingtones.uriOf(builtIn[which].id)
                        Toast.makeText(this, builtIn[which].label, Toast.LENGTH_SHORT).show()
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
        try { AlarmKeepAliveService.sync(this) } catch (_: Exception) {}
    }

    /** Đồng bộ lại list từ đĩa (sau khi báo thức kêu / tắt ở màn khác). */
    private fun reloadAlarmsFromDisk() {
        alarms.clear()
        alarms.addAll(repo.getAlarms().onEach {
            if (it.ringtoneUri.isNullOrEmpty()) it.ringtoneUri = AppRingtones.systemAlarm(this)
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
        // Không hỏi camera / vị trí lúc mở app. Chỉ xin khi dùng tính năng cần quyền đó.
        val hasPlace = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPlace) {
            Thread { try { LocationPlaceHelper.resolve(this) } catch (_: Exception) {} }.start()
        }
        try {
            window.decorView.postDelayed({
                if (!isFinishing) BatteryOptHelper.promptAllowBackground(this)
            }, 900)
        } catch (_: Exception) {}
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
            label = Lang.t(this, "Ngủ gật", "Nap") + " " + napText(minutes),
            repeatMode = Alarm.REPEAT_ONCE,
            challengeType = Alarm.CHALLENGE_NONE,
            ringtoneUri = AppRingtones.systemAlarm(this),
            useCrescendo = true
        )
        alarms.add(alarm)
        repo.saveAlarms(alarms)
        AlarmScheduler.schedule(this, alarm)
        adapter.notifyDataSetChanged()
        updateOngoingNotification()
        val timeStr = String.format("%02d:%02d", hour, minute)
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            "Báo thức $timeStr (sau ${napText(minutes)})",
            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showAlarmMore(alarm: Alarm) {
        val colors = intArrayOf(0xFF1A73E8.toInt(), 0xFF188038.toInt(), 0xFFE37400.toInt(), 0xFFD93025.toInt(), 0xFF9334E6.toInt())
        MaterialAlertDialogBuilder(this)
            .setTitle(alarm.label)
            .setItems(arrayOf("Sao chép", "Ghi chú", "Đổi màu")) { _, which ->
                when (which) {
                    0 -> {
                        val copy = alarm.copy(id = repo.getNextId(), label = alarm.label + " copy")
                        alarms.add(copy)
                        alarms.sortBy { it.hour * 60 + it.minute }
                        repo.saveAlarms(alarms)
                        AlarmScheduler.schedule(this, copy)
                        adapter.notifyDataSetChanged()
                    }
                    1 -> {
                        val et = EditText(this).apply { setText(alarm.note); hint = "Ghi chú ngắn" }
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Ghi chú")
                            .setView(et)
                            .setPositiveButton("Lưu") { _, _ ->
                                alarm.note = et.text.toString().trim()
                                repo.saveAlarms(alarms)
                                adapter.notifyDataSetChanged()
                            }
                            .setNegativeButton("Hủy", null)
                            .show()
                    }
                    2 -> {
                        alarm.color = colors[(colors.indexOf(alarm.color) + 1).coerceAtLeast(0) % colors.size]
                        repo.saveAlarms(alarms)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .show()
    }

    private fun pickSkipDate() {
        val now = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(this, { _, y, m, d ->
            val ymd = "%04d-%02d-%02d".format(y, m + 1, d)
            DayOff.toggle(this, ymd)
            AlarmScheduler.rescheduleAll(this)
            Toast.makeText(this, "Ngày nghỉ: $ymd", Toast.LENGTH_SHORT).show()
        }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }

    private fun napText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> Lang.t(this, "${h} giờ ${m} phút", "${h}h ${m}m")
            h > 0 -> Lang.t(this, "${h} giờ", "${h}h")
            else -> Lang.t(this, "${m} phút", "${m} min")
        }
    }

    private fun pickCustomNap() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 16, 32, 8)
        }
        val hours = NumberPicker(this).apply {
            minValue = 0
            maxValue = 12
            value = 1
        }
        val mins = NumberPicker(this).apply {
            minValue = 0
            maxValue = 180
            value = 0
        }
        row.addView(hours)
        row.addView(TextView(this).apply { text = "  giờ   " })
        row.addView(mins)
        row.addView(TextView(this).apply { text = "  phút" })
        MaterialAlertDialogBuilder(this)
            .setTitle(Lang.t(this, "Chỉnh ngủ gật", "Custom nap"))
            .setView(row)
            .setPositiveButton(Lang.t(this, "Đặt", "Set")) { _, _ ->
                val total = hours.value * 60 + mins.value
                if (total <= 0) {
                    Toast.makeText(this, Lang.t(this, "Chọn giờ hoặc phút", "Pick hours or minutes"), Toast.LENGTH_SHORT).show()
                } else {
                    addQuickAlarm(total)
                }
            }
            .setNegativeButton(Lang.t(this, "Hủy", "Cancel"), null)
            .show()
    }



    private var pendingUpdateTag: String? = null

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        try {
            if (menu is androidx.appcompat.view.menu.MenuBuilder) {
                @Suppress("RestrictedApi")
                menu.setOptionalIconsVisible(true)
            }
        } catch (_: Exception) {}
        val updateItem = menu.findItem(R.id.menu_version)
        val current = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        updateItem?.isVisible = true
        updateItem?.title = if (current.isNotBlank()) "v$current" else "Phiên bản"
        UpdateCheckHelper.check(current) { info ->
            if (info != null && info.newer) {
                pendingUpdateTag = info.tag
                updateItem?.isVisible = true
                updateItem?.title = "v$current → ${info.tag} • Cập nhật"
                try { updateItem?.setIcon(R.drawable.ic_system_update) } catch (_: Exception) {}
                // Hiện full-screen cập nhật
                try {
                    startActivity(android.content.Intent(this, UpdateFullScreenActivity::class.java).apply {
                        putExtra("tag", info.tag)
                        putExtra("current", current)
                    })
                } catch (_: Exception) {}
            } else {
                pendingUpdateTag = null
                updateItem?.isVisible = true
                updateItem?.title = if (current.isNotBlank()) "v$current" else "Phiên bản"
            }
        }
        return true
    }


    /** Chọn ảnh hồ sơ từ thư viện (không cần xin quyền) */
    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) saveProfilePhoto(uri)
    }

    private fun saveProfilePhoto(uri: android.net.Uri) {
        Toast.makeText(this, "Đang lưu ảnh…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val src = contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                } ?: throw IllegalStateException("decode")
                val side = minOf(src.width, src.height)
                val square = android.graphics.Bitmap.createBitmap(
                    src, (src.width - side) / 2, (src.height - side) / 2, side, side
                )
                val out = android.graphics.Bitmap.createScaledBitmap(square, 512, 512, true)
                val f = java.io.File(filesDir, "profile_photo.jpg")
                f.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                AppSettings.setProfilePhotoPath(this, f.absolutePath)
                runOnUiThread {
                    Toast.makeText(this, "Đã đổi ảnh hồ sơ", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Không đổi được ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showProfileEditDialog() {
        val items = arrayOf(
            "Đổi ảnh hồ sơ",
            "Sửa tên",
            "Sửa ngày sinh",
            "Đổi mật khẩu",
            "Đổi email"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Thông tin hồ sơ")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> try { profilePhotoPicker.launch("image/*") } catch (e: Exception) {
                        Toast.makeText(this, "Không mở được thư viện ảnh", Toast.LENGTH_SHORT).show()
                    }
                    1 -> showEditNameDialog()
                    2 -> try { BirthdayHelper.pickManual(this) } catch (e: Exception) {
                        Toast.makeText(this, "Không mở được bảng chọn ngày sinh", Toast.LENGTH_SHORT).show()
                    }
                    3 -> showChangePasswordDialog()
                    4 -> showChangeEmailDialog()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun requireOtpThen(email: String, onOk: () -> Unit) {
        val code = "%06d".format(kotlin.random.Random.nextInt(0, 1_000_000))
        AppSettings.setRecoveryCode(this, code)
        Thread { GmailBot.sendOtp(email, code) }.start()
        Toast.makeText(this, "Đang gửi mã tới $email", Toast.LENGTH_SHORT).show()
        val input = android.widget.EditText(this).apply {
            hint = "Nhập mã 6 số"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận OTP")
            .setMessage("Mã đã gửi tới $email")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                val loading = Md3Loading.show(this, "Đang kiểm tra mã…")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loading.dismiss()
                    if (input.text.toString() == code) onOk()
                    else Toast.makeText(this, "Sai mã", Toast.LENGTH_SHORT).show()
                }, 900)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showEditNameDialog() {
        val email = AppSettings.getRecoveryEmail(this)
        if (email.isBlank()) { Toast.makeText(this, "Chưa có email", Toast.LENGTH_SHORT).show(); return }
        val input = android.widget.EditText(this).apply {
            setText(AppSettings.getGoogleDisplayName(this@MainActivity))
            hint = "Tên mới"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sửa tên")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                requireOtpThen(email) {
                    AppSettings.setGoogleDisplayName(this, name)
                    // Cập nhật lên Firebase để lần đăng nhập sau không bị ghi đè
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        user.updateProfile(req)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Đã đổi tên", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Đã lưu local, Firebase lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        Toast.makeText(this, "Đã đổi tên", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val email = AppSettings.getRecoveryEmail(this)
        if (email.isBlank()) { Toast.makeText(this, "Chưa có email", Toast.LENGTH_SHORT).show(); return }
        val input = android.widget.EditText(this).apply {
            hint = "Mật khẩu mới"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Đổi mật khẩu")
            .setView(input)
            .setPositiveButton("Đổi") { _, _ ->
                val pass = input.text.toString()
                if (pass.length < 6) { Toast.makeText(this, "Mật khẩu quá ngắn", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                requireOtpThen(email) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        ?.updatePassword(pass)
                        ?.addOnSuccessListener { Toast.makeText(this, "Đã đổi mật khẩu", Toast.LENGTH_SHORT).show() }
                        ?.addOnFailureListener { e -> Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showChangeEmailDialog() {
        val email = AppSettings.getRecoveryEmail(this)
        if (email.isBlank()) { Toast.makeText(this, "Chưa có email", Toast.LENGTH_SHORT).show(); return }
        val input = android.widget.EditText(this).apply {
            hint = "Email mới"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Đổi email")
            .setView(input)
            .setPositiveButton("Đổi") { _, _ ->
                val newEmail = input.text.toString().trim()
                if (!newEmail.contains("@")) { Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                requireOtpThen(email) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                        ?.updateEmail(newEmail)
                        ?.addOnSuccessListener {
                            AppSettings.setRecoveryEmail(this, newEmail)
                            Toast.makeText(this, "Đã đổi email", Toast.LENGTH_SHORT).show()
                        }
                        ?.addOnFailureListener { e -> Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showCloudCheck() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(Lang.t(this, "Chọn cloud", "Choose cloud"))
            .setItems(arrayOf(
                Lang.t(this, "Cloud Google", "Google cloud"),
                Lang.t(this, "Cloud Facebook", "Facebook cloud"),
                Lang.t(this, "Cloud Email", "Email cloud")
            )) { _, which ->
                when (which) {
                    0 -> runCloudCheck("Google")
                    1 -> runCloudCheck("Facebook")
                    else -> runCloudCheck("Email")
                }
            }
            .setNegativeButton(Lang.t(this, "Đóng", "Close"), null)
            .show()
    }

    private fun runCloudCheck(provider: String) {
        Toast.makeText(this, Lang.t(this, "Đang kiểm tra cloud $provider…", "Checking $provider cloud…"), Toast.LENGTH_SHORT).show()
        CloudSyncHelper.diagnose(this) { text ->
            if (isFinishing || isDestroyed) return@diagnose
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val signed = when (provider) {
                "Facebook" -> user?.providerData?.any { it.providerId.contains("facebook") } == true
                "Email" -> user?.providerData?.any { it.providerId.contains("password") } == true
                else -> user?.providerData?.any { it.providerId.contains("google") } == true
            }
            val head = if (signed) {
                Lang.t(this, "Đã đăng nhập $provider", "Signed in with $provider")
            } else {
                Lang.t(this, "Chưa đăng nhập $provider", "Not signed in with $provider")
            }
            val tv = android.widget.TextView(this).apply {
                this.text = "$head\n\n$text"
                setTextIsSelectable(true)
                setPadding(48, 32, 48, 0)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(Lang.t(this, "Kiểm tra cloud $provider", "Check $provider cloud"))
                .setView(tv)
                .setPositiveButton(Lang.t(this, "Đóng", "Close"), null)
                .show()
        }
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
                showLoginChooser()
                return true
            }
            R.id.menu_settings -> {
                openInternal(SettingsActivity::class.java, "Cài đặt")
                return true
            }
            R.id.menu_day_off -> {
                pickSkipDate()
                return true
            }
            R.id.menu_version -> {
                showUpdateDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun openInternal(cls: Class<*>, label: String) {
        try {
            startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Không mở được $label: ${e.javaClass.simpleName}", android.widget.Toast.LENGTH_LONG).show()
        }
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

    private fun showLoginChooser() {
        showGoogleLoginMenu()
    }

    private fun showGoogleLoginMenu() {
        val view = layoutInflater.inflate(R.layout.dialog_google_sign_in, null)
        fun walkG(v: android.view.View) {
            if (v is android.widget.TextView) {
                val cur = v.text?.toString().orEmpty()
                if (cur.contains("Đăng nhập để lưu")) v.text = Lang.t(this, cur, "Sign in to save alarm history")
                if (cur == "Đóng") v.text = Lang.t(this, "Đóng", "Close")
                if (cur == "Đăng xuất") v.text = Lang.t(this, "Đăng xuất", "Sign out")
                if (cur == "Nhập email") v.text = Lang.t(this, "Nhập email", "Enter email")
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walkG(v.getChildAt(i))
        }
        walkG(view)
        val status = view.findViewById<TextView>(R.id.tvGoogleStatus)
        val current = GoogleSignInHelper.lastSignedInEmail(this)
            ?: AppSettings.getRecoveryEmail(this)
        val name = AppSettings.getGoogleDisplayName(this)
        val signed = name.isNotBlank() || current.isNotBlank()
        val profile = view.findViewById<android.view.View>(R.id.layoutProfile)
        val buttons = view.findViewById<android.view.View>(R.id.layoutLoginButtons)
        if (signed) {
            profile?.visibility = android.view.View.VISIBLE
            buttons?.visibility = android.view.View.GONE
            view.findViewById<TextView>(R.id.tvProfileName)?.text = name.ifBlank { "Đã đăng nhập" }
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val isEmail = user?.providerData?.any { it.providerId == "password" } == true
            view.findViewById<View>(R.id.tvViewProfile)?.visibility =
                if (isEmail) android.view.View.VISIBLE else android.view.View.GONE
            status.text = "Đã đăng nhập"
            val url = AppSettings.getGooglePhotoUrl(this)
            val img = view.findViewById<android.widget.ImageView>(R.id.imgAvatar)
            val localPhoto = AppSettings.getProfilePhotoPath(this)
            if (img != null && localPhoto.isNotBlank() && java.io.File(localPhoto).exists()) {
                try {
                    img.setImageBitmap(android.graphics.BitmapFactory.decodeFile(localPhoto))
                } catch (_: Exception) {}
            } else if (url.isNotBlank() && img != null) {
                Thread {
                    try {
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bmp != null) runOnUiThread { img.setImageBitmap(bmp) }
                    } catch (_: Exception) {}
                }.start()
            }
        } else {
            profile?.visibility = android.view.View.GONE
            buttons?.visibility = android.view.View.VISIBLE
            status.text = "Chưa đăng nhập"
        }
        view.findViewById<View>(R.id.btnGoogleSignIn).setOnClickListener {
            try {
                googleSignInLauncher.launch(GoogleSignInHelper.signInIntent(this))
            } catch (e: Exception) {
                try {
                    googleSignInLauncher.launch(GoogleSignInHelper.accountPickerIntent())
                } catch (e2: Exception) {
                    Toast.makeText(this, "Không mở Google: ${e2.message}", Toast.LENGTH_LONG).show()
                    showGoogleEmailFallback()
                }
            }
            googleLoginDialog?.dismiss()
        }
        view.findViewById<View>(R.id.btnFacebookSignIn)?.setOnClickListener {
            Toast.makeText(this, Lang.t(this, "Đang mở Facebook…", "Opening Facebook…"), Toast.LENGTH_SHORT).show()
            FacebookLoginHelper.start(this)
            googleLoginDialog?.dismiss()
        }
        view.findViewById<View>(R.id.btnEmailSignIn)?.setOnClickListener {
            googleLoginDialog?.dismiss()
            showEmailLoginDialog()
        }
        view.findViewById<View>(R.id.btnGuest)?.setOnClickListener {
            AppSettings.setGoogleDisplayName(this, "Khách")
            AppSettings.setRecoveryEmail(this, "")
            Toast.makeText(this, Lang.t(this, "Đang dùng chế độ khách", "Using guest mode"), Toast.LENGTH_SHORT).show()
            googleLoginDialog?.dismiss()
        }
        view.findViewById<View>(R.id.btnForgotPin)?.setOnClickListener {
            googleLoginDialog?.dismiss()
            SettingsLockHelper.showForgotFlow(this) {
                Toast.makeText(this, "Đã mở khóa", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.tvViewProfile)?.setOnClickListener {
            googleLoginDialog?.dismiss()
            showProfileEditDialog()
        }
        view.findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            val loading = Md3Loading.show(this, null)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { GoogleSignInHelper.signOut(this) } catch (_: Exception) {}
                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
                AppSettings.setGoogleDisplayName(this, "")
                AppSettings.setRecoveryEmail(this, "")
                AppSettings.setGooglePhotoUrl(this, "")
                status.text = "Chưa đăng nhập"
                loading.dismiss()
                Toast.makeText(this, Lang.t(this, "Đã đăng xuất", "Signed out"), Toast.LENGTH_SHORT).show()
                googleLoginDialog?.dismiss()
            }, 3000)
        }
        googleLoginDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()
        googleLoginDialog?.show()
    }


    private fun saveEmailSession(email: String, name: String) {
        AppSettings.setRecoveryEmail(this, email)
        AppSettings.setGoogleDisplayName(this, name.ifBlank { email.substringBefore("@") })
        Toast.makeText(this, "Đã lưu: $name\n$email", Toast.LENGTH_LONG).show()
        try { CloudSyncHelper.syncOnLogin(this) } catch (_: Exception) {}
    }

    private fun showEmailLoginDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_email_login, null)
        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btnLogin).setOnClickListener {
            val email = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail).text?.toString()?.trim().orEmpty()
            val pass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword).text?.toString().orEmpty()
            if (!email.contains("@") || pass.length < 6) {
                Toast.makeText(this, "Email hoặc mật khẩu không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val loading = Md3Loading.show(this, "Đang đăng nhập…")
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { res ->
                    loading.dismiss()
                    val localName = AppSettings.getGoogleDisplayName(this)
                    val fbName = res.user?.displayName.orEmpty()
                    val name = when {
                        localName.isNotBlank() && localName != email.substringBefore("@") -> localName
                        fbName.isNotBlank() -> fbName
                        else -> email.substringBefore("@")
                    }
                    saveEmailSession(email, name)
                    // Khôi phục ảnh hồ sơ local nếu còn file
                    val photoPath = AppSettings.getProfilePhotoPath(this)
                    if (photoPath.isNotBlank() && java.io.File(photoPath).exists()) {
                        AppSettings.setProfilePhotoPath(this, photoPath)
                    }
                    dlg.dismiss()
                }
                .addOnFailureListener { e ->
                    try { loading.dismiss() } catch (_: Exception) {}
                    Toast.makeText(this, e.message ?: "Đăng nhập thất bại", Toast.LENGTH_LONG).show()
                }
        }
        view.findViewById<View>(R.id.tvGoRegister).setOnClickListener {
            dlg.dismiss()
            showEmailRegisterDialog()
        }
        dlg.show()
    }

    private fun showEmailRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_email_register, null)
        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btnRegister).setOnClickListener {
            val email = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail).text?.toString()?.trim().orEmpty()
            val pass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword).text?.toString().orEmpty()
            val confirm = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etConfirm).text?.toString().orEmpty()
            val name = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName).text?.toString()?.trim().orEmpty()
            val terms = view.findViewById<android.widget.CheckBox>(R.id.cbTerms).isChecked
            if (!email.contains("@") || pass.length < 6 || pass != confirm || name.isBlank() || !terms) {
                Toast.makeText(this, "Kiểm tra lại thông tin và đồng ý điều khoản", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { res ->
                    val user = res.user
                    val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    user?.updateProfile(profile)
                    saveEmailSession(email, name)
                    dlg.dismiss()
                    showOtpDialog(email)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, e.message ?: "Đăng ký thất bại", Toast.LENGTH_LONG).show()
                }
        }
        view.findViewById<View>(R.id.tvGoLogin).setOnClickListener {
            dlg.dismiss()
            showEmailLoginDialog()
        }
        dlg.show()
    }

    private fun showOtpDialog(email: String) {
        val code = "%06d".format(kotlin.random.Random.nextInt(0, 1_000_000))
        AppSettings.setRecoveryCode(this, code)
        Thread { GmailBot.sendOtp(email, code) }.start()
        Toast.makeText(this, "Đang gửi mã OTP tới $email", Toast.LENGTH_SHORT).show()

        val view = layoutInflater.inflate(R.layout.dialog_otp, null)
        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        val boxes = listOf(
            view.findViewById<android.widget.EditText>(R.id.otp1),
            view.findViewById<android.widget.EditText>(R.id.otp2),
            view.findViewById<android.widget.EditText>(R.id.otp3),
            view.findViewById<android.widget.EditText>(R.id.otp4),
            view.findViewById<android.widget.EditText>(R.id.otp5),
            view.findViewById<android.widget.EditText>(R.id.otp6)
        )
        val tvResend = view.findViewById<android.widget.TextView>(R.id.tvResend)
        var seconds = 59
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (seconds > 0) {
                    tvResend.text = "Gửi lại mã sau ${seconds}s"
                    tvResend.setTextColor(0xFF5F6368.toInt())
                    tvResend.isClickable = false
                    seconds--
                    handler.postDelayed(this, 1000)
                } else {
                    tvResend.text = "Gửi lại mã"
                    tvResend.setTextColor(0xFF1A73E8.toInt())
                    tvResend.isClickable = true
                }
            }
        }
        handler.post(tick)
        tvResend.setOnClickListener {
            if (seconds <= 0) {
                val newCode = "%06d".format(kotlin.random.Random.nextInt(0, 1_000_000))
                AppSettings.setRecoveryCode(this, newCode)
                Thread { GmailBot.sendOtp(email, newCode) }.start()
                Toast.makeText(this, "Đã gửi lại mã", Toast.LENGTH_SHORT).show()
                seconds = 59
                handler.post(tick)
            }
        }

        view.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            val entered = boxes.joinToString("") { it.text?.toString().orEmpty() }
            if (entered == code) {
                Toast.makeText(this, "Xác minh thành công", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
            } else {
                Toast.makeText(this, "Sai mã OTP", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<View>(R.id.tvBackLogin).setOnClickListener {
            dlg.dismiss()
            showEmailLoginDialog()
        }
        view.findViewById<View>(R.id.tvEditEmail).setOnClickListener {
            dlg.dismiss()
            showEmailRegisterDialog()
        }
        dlg.show()
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
        val summary = AlarmHistory.weekSummary(this)
        val msg = if (lines.isEmpty()) "$summary\n\nChưa có nhật ký.\nBáo thức tắt/báo lại sẽ được ghi tại đây."
        else "$summary\n\n" + lines.take(40).joinToString("\n")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Nhật ký báo thức")
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


    private fun showUpdateDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(Lang.t(this, "Cập nhật ứng dụng", "Update app"))
            .setMessage(Lang.t(this, "Bạn có muốn mở trang APKPure để tải bản cập nhật mới nhất không?", "Open APKPure to download the latest version?"))
            .setPositiveButton(Lang.t(this, "Mở APKPure", "Open APKPure")) { _, _ ->
                try {
                    val uri = android.net.Uri.parse("https://apkpure.com/p/com.alarmclock.dongho")
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                } catch (_: Exception) {
                    android.widget.Toast.makeText(this, "Không mở được trình duyệt", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

}
