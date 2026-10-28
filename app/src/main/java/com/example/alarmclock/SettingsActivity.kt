package com.example.alarmclock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.facebook.FacebookSdk
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivitySettingsBinding

    private val accountPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val account = GoogleSignInHelper.handleResult(this, result.data)
            val email = account?.email?.trim().orEmpty().ifBlank {
                GoogleSignInHelper.handleAccountPicker(this, result.data).orEmpty()
            }
            if (email.contains("@")) {
                bindGoogleProfileRow()
                Toast.makeText(this, Lang.t(this, "Đã chuyển tài khoản: $email", "Switched account: $email"), Toast.LENGTH_LONG).show()
                try { CloudSyncHelper.syncOnLogin(this) } catch (_: Exception) {}
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        FacebookLoginHelper.callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loading = Md3Loading.show(this, "Đang mở cài đặt…")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ try { loading.dismiss() } catch (_: Exception) {} }, 700)
        try {
            binding = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            findViewById<android.view.View>(R.id.btnOpenHistory)?.setOnClickListener {
                startActivity(android.content.Intent(this, MainActivity::class.java).putExtra("open", "history"))
            }
            findViewById<android.view.View>(R.id.btnOpenScreensaver)?.setOnClickListener {
                startActivity(android.content.Intent(this, ScreensaverActivity::class.java))
            }
            findViewById<android.view.View>(R.id.btnOpenPrivacy)?.setOnClickListener {
                startActivity(android.content.Intent(this, PrivacyPolicyActivity::class.java))
            }
            findViewById<android.view.View>(R.id.btnOpenGuide)?.setOnClickListener {
                FirstLaunchDialog.show(this, force = true)
            }
            findViewById<android.view.View>(R.id.btnOpenCloud)?.setOnClickListener {
                startActivity(android.content.Intent(this, MainActivity::class.java).putExtra("open", "cloud"))
            }
            try { setupDismissStyle() } catch (e: Exception) {
                android.util.Log.e("Settings", "dismiss style setup failed", e)
            }
        try { ThemeFix.apply(this, binding.root) } catch (_: Exception) {}
        try { EventManager.applyChrome(this) } catch (_: Exception) {}
        } catch (e: Exception) {
            val tv = android.widget.TextView(this)
            tv.text = UiTranslate.tr("Cài đặt")
            tv.textSize = 20f
            tv.setPadding(48, 48, 48, 48)
            setContentView(tv)
            android.widget.Toast.makeText(this, "Lỗi giao diện cài đặt: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        bindGoogleProfileRow()
        try {
            binding.swEventTheme.isChecked = EventManager.isThemeEnabled(this)
            binding.swEventTheme.setOnCheckedChangeListener { _, on ->
                EventManager.setThemeEnabled(this, on)
                try { EventManager.applyChrome(this) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        try {
            val sw = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swWallpaperTheme)
            sw?.isChecked = WallpaperTheme.isEnabled(this)
            sw?.setOnCheckedChangeListener { _, on ->
                WallpaperTheme.setEnabled(this, on)
                if (on && WallpaperTheme.palette(this) == null) {
                    // Máy không cho đọc màu hình nền: xin quyền đọc ảnh/bộ nhớ (Android ≤ 12)
                    if (android.os.Build.VERSION.SDK_INT < 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            this, android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 93)
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            Lang.t(this, "Máy không cho app đọc màu hình nền", "This phone doesn't let the app read wallpaper colors"),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                WallpaperTheme.markApplied(this)
                recreate()
            }
        } catch (_: Exception) {}
        try {
            findViewById<android.view.View>(R.id.tvQuickDismiss)?.setOnClickListener { showQuickDismissPicker() }
            refreshQuickDismissValue()
            findViewById<android.view.View>(R.id.tvRingTheme)?.setOnClickListener {
                startActivity(android.content.Intent(this, RingThemeActivity::class.java))
            }
        } catch (_: Exception) {}
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        // try { binding.curvedNav.visibility = android.view.View.GONE } catch (_: Exception) {}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        // Bắt buộc PIN nếu đã đặt
        if (AppSettings.hasSettingsPin(this) && !AppSettings.settingsUnlockedThisSession) {
            SettingsLockHelper.requireUnlock(this) {
                // đã mở — tiếp tục; UI đã inflate sẵn
                refreshSettingsPinStatus()
            }
        }

        // Test quét mặt / biểu cảm
        try {
            binding.btnTestFace.setOnClickListener {
                startActivity(android.content.Intent(this, FaceChallengeActivity::class.java).apply {
                    putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE)
                })
            }
            binding.btnTestExpr.setOnClickListener {
                startActivity(android.content.Intent(this, FaceChallengeActivity::class.java).apply {
                    putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR)
                })
            }
            // Giữ lâu tiêu đề / vùng test → menu "Bạn muốn làm gì?"
            binding.btnTestFace.setOnLongClickListener {
                BottomNavHelper.showFaceTestMenu(this); true
            }
            binding.btnTestExpr.setOnLongClickListener {
                BottomNavHelper.showFaceTestMenu(this); true
            }
        } catch (_: Exception) {}


        // PIN khóa Cài đặt
        try {
            refreshSettingsPinStatus()
            binding.btnSaveSettingsPin.setOnClickListener {
                val pin = binding.edtSettingsPin.text?.toString()?.trim().orEmpty()
                if (pin.length < 4 || !pin.all { it.isDigit() }) {
                    Toast.makeText(this, UiTranslate.tr("PIN tối thiểu 4 chữ số"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val email = AppSettings.getRecoveryEmail(this)
                if (email.isBlank()) {
                    Toast.makeText(this, UiTranslate.tr("Nên lưu Gmail khôi phục trước (phía dưới)"), Toast.LENGTH_LONG).show()
                }
                AppSettings.setSettingsPin(this, pin)
                AppSettings.settingsUnlockedThisSession = true
                binding.edtSettingsPin.setText("")
                refreshSettingsPinStatus()
                Toast.makeText(this, UiTranslate.tr("Đã khóa Cài đặt bằng PIN"), Toast.LENGTH_SHORT).show()
            }
            binding.btnClearSettingsPin.setOnClickListener {
                if (!AppSettings.hasSettingsPin(this)) {
                    Toast.makeText(this, UiTranslate.tr("Chưa đặt PIN"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Xác nhận bằng PIN hiện tại
                val input = android.widget.EditText(this).apply {
                    hint = UiTranslate.tr("Nhập PIN hiện tại để xóa")
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    setPadding(48, 32, 48, 32)
                }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(UiTranslate.tr("Xóa PIN Cài đặt?"))
                    .setView(input)
                    .setPositiveButton(UiTranslate.tr("Xóa")) { _, _ ->
                        val pin = input.text?.toString()?.trim().orEmpty()
                        if (AppSettings.checkSettingsPin(this, pin)) {
                            AppSettings.clearSettingsPin(this)
                            refreshSettingsPinStatus()
                            Toast.makeText(this, UiTranslate.tr("Đã xóa PIN Cài đặt"), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Sai PIN", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton(UiTranslate.tr("Quên PIN?")) { _, _ ->
                        SettingsLockHelper.showForgotFlow(this) {
                            refreshSettingsPinStatus()
                        }
                    }
                    .setNegativeButton(UiTranslate.tr("Hủy"), null)
                    .show()
            }
        } catch (_: Exception) {}

        // Ngôn ngữ (~195) + hiệu ứng
        refreshLanguageLabel()
        binding.cardLanguage.setOnClickListener { v ->
            Motion.press(v) { showLanguagePicker() }
        }
        Motion.fadeScaleIn(binding.cardLanguage, delay = 40)

        // Volume
        
        // Cỡ chữ bé / vừa / to
        try {
            when (AppSettings.getFontScaleMode(this)) {
                0 -> binding.rbFontSmall.isChecked = true
                2 -> binding.rbFontLarge.isChecked = true
                else -> binding.rbFontNormal.isChecked = true
            }
            binding.rgFontScale.setOnCheckedChangeListener { _, checkedId ->
                val mode = when (checkedId) {
                    R.id.rbFontSmall -> 0
                    R.id.rbFontLarge -> 2
                    else -> 1
                }
                AppSettings.setFontScaleMode(this, mode)
                Toast.makeText(this, UiTranslate.tr("Đã đổi cỡ chữ — mở lại màn hình để áp dụng"), Toast.LENGTH_SHORT).show()
                recreate()
            }
        } catch (_: Exception) {}

        binding.seekVolume.progress = AppSettings.getAlarmVolume(this)
        binding.tvVolumeValue.text = "${binding.seekVolume.progress}%"
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvVolumeValue.text = "$progress%"
                if (fromUser) AppSettings.setAlarmVolume(this@SettingsActivity, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Vibrate — LoadSwitch (animation đẹp, giống nhau mọi hãng)
        binding.switchVibrate.setCheckedSilent(AppSettings.isVibrate(this))
        binding.switchVibrate.setOnCheckedChangeListener { sw, on ->
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setVibrate(this, on)
                sw.setLoading(false)
            }, 280)
        }

        // Snooze
        when (AppSettings.getDefaultSnooze(this)) {
            10 -> binding.rbSnooze10.isChecked = true
            15 -> binding.rbSnooze15.isChecked = true
            else -> binding.rbSnooze5.isChecked = true
        }
        try {
            binding.tvRingDurationTitle.text = Lang.t(this, "Thời lượng đổ chuông", "Ring duration")
            binding.tvRingDurationHint.text = Lang.t(this, "Hết giờ tự tắt, không kêu suốt ngày", "Stops automatically — will not ring all day")
            binding.tvPauseHome.text = Lang.t(this, "Đang ở nhà — tạm dừng báo thức", "I'm home — pause alarms")
            when (AppSettings.getRingDurationMinutes(this)) {
                1 -> binding.rbRing1.isChecked = true
                5 -> binding.rbRing5.isChecked = true
                15 -> binding.rbRing15.isChecked = true
                30 -> binding.rbRing30.isChecked = true
                else -> binding.rbRing10.isChecked = true
            }
            binding.rgRingDuration.setOnCheckedChangeListener { _, id ->
                val min = when (id) {
                    R.id.rbRing1 -> 1
                    R.id.rbRing5 -> 5
                    R.id.rbRing15 -> 15
                    R.id.rbRing30 -> 30
                    else -> 10
                }
                AppSettings.setRingDurationMinutes(this, min)
            }
            binding.switchPauseHome.setCheckedSilent(AppSettings.isPauseAlarmsAtHome(this))
            binding.switchPauseHome.setOnCheckedChangeListener { _, on ->
                AppSettings.setPauseAlarmsAtHome(this, on)
                try {
                    if (on) {
                        AlarmRepository(this).getAlarms().forEach { AlarmScheduler.cancel(this, it.id) }
                        AlarmNotificationHelper.cancelRinging(this)
                        AlarmRingService.stop(this)
                        TonePlayer.stop()
                    } else {
                        AlarmScheduler.rescheduleAll(this)
                    }
                } catch (_: Exception) {}
                Toast.makeText(
                    this,
                    if (on) Lang.t(this, "Đã tạm dừng báo thức khi ở nhà", "Alarms paused while at home")
                    else Lang.t(this, "Đã bật lại lịch báo thức", "Alarms scheduled again"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            binding.btnAllowBackground.text = Lang.t(
                this,
                "Cho phép chạy nền / Tắt tối ưu pin",
                "Allow background / Ignore battery opt."
            )
            binding.btnAllowBackground.setOnClickListener {
                BatteryOptHelper.promptAllowBackground(this, force = true)
            }
        } catch (_: Exception) {}
        binding.rgSnoozeDefault.setOnCheckedChangeListener { _, id ->
            val m = when (id) {
                R.id.rbSnooze10 -> 10
                R.id.rbSnooze15 -> 15
                else -> 5
            }
            AppSettings.setDefaultSnooze(this, m)
        }

        // Dark mode
        when (AppSettings.getDarkMode(this)) {
            1 -> binding.rbDarkOn.isChecked = true
            2 -> binding.rbDarkOff.isChecked = true
            else -> binding.rbDarkSystem.isChecked = true
        }
        binding.rgDarkMode.setOnCheckedChangeListener { _, id ->
            if (id == View.NO_ID) return@setOnCheckedChangeListener
            val mode = when (id) {
                R.id.rbDarkOn -> 1
                R.id.rbDarkOff -> 2
                else -> 0
            }
            AppSettings.setDarkMode(this, mode)
            try {
                Toast.makeText(this, getString(R.string.dark_mode_changed), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            // Huawei: recreate() → màn đen. Restart sạch task.
            binding.root.postDelayed({
                try {
                    val i = android.content.Intent(this, MainActivity::class.java)
                    i.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    startActivity(i)
                    finishAffinity()
                } catch (_: Exception) {
                    try { finish() } catch (_: Exception) {}
                }
            }, 200)
        }

        // Kiểu thanh dưới: Curved / Persistent / Google Nav
        // KHÔNG recreate — recreate gây màn đen / văng app (Huawei)
        binding.rgNavStyle.setOnCheckedChangeListener(null)
        when (AppSettings.getBottomNavStyle(this)) {
            AppSettings.NAV_PERSISTENT -> binding.rbNavPersistent.isChecked = true
            AppSettings.NAV_GOOGLE -> binding.rbNavGoogle.isChecked = true
            else -> binding.rbNavCurved.isChecked = true
        }
        binding.rgNavStyle.setOnCheckedChangeListener { _, id ->
            if (id == View.NO_ID) return@setOnCheckedChangeListener
            val style = when (id) {
                R.id.rbNavPersistent -> AppSettings.NAV_PERSISTENT
                R.id.rbNavGoogle -> AppSettings.NAV_GOOGLE
                else -> AppSettings.NAV_CURVED
            }
            try {
                AppSettings.setBottomNavStyle(this, style)
            } catch (_: Exception) {}
            // Đổi style trên UI hiện tại — post để tránh re-entrant layout crash
            binding.root.post {
                try {
                    val s = when (style) {
                        AppSettings.NAV_CURVED -> CurvedBottomNavView.Style.CURVED
                        AppSettings.NAV_PERSISTENT, AppSettings.NAV_GLASS -> CurvedBottomNavView.Style.PERSISTENT
                        else -> CurvedBottomNavView.Style.GOOGLE
                    }
        // binding.curvedNav.navStyle = s
                    /* settings is not a tab */
        // binding.curvedNav.requestLayout()
        // binding.curvedNav.invalidate()
                } catch (e: Exception) {
                    try {
                        Toast.makeText(this, "Lỗi style: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {}
                }
            }
            val name = when (style) {
                AppSettings.NAV_GOOGLE -> "Thanh nổi"
                AppSettings.NAV_PERSISTENT, AppSettings.NAV_GLASS -> "Kính lỏng"
                else -> "Cong"
            }
            try {
                Toast.makeText(this, "Thanh dưới: $name (áp dụng mọi tab)", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }

        binding.switchAnimation.setCheckedSilent(AppSettings.isAnimationEnabled(this))
        binding.switchAnimation.setOnCheckedChangeListener { sw, on ->
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setAnimationEnabled(this, on)
                try { Fx.applyGlobal(this) } catch (_: Throwable) {}
                sw.setLoading(false)
            }, 220)
        }

        // 12/24h
        binding.switch24h.setCheckedSilent(AppSettings.isUse24h(this))
        binding.switch24h.setOnCheckedChangeListener { sw, on ->
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setUse24h(this, on)
                sw.setLoading(false)
            }, 280)
        }

        binding.switchAutoIcon.setCheckedSilent(DynamicIconHelper.isAutoIcon(this))
        binding.switchAutoIcon.setOnCheckedChangeListener { sw, on ->
            if (on) {
                DynamicIconHelper.setAutoIcon(this, true)
                android.widget.Toast.makeText(this, UiTranslate.tr("Đã bật đổi icon theo buổi"), android.widget.Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            sw.setCheckedSilent(true)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(UiTranslate.tr("Tắt đổi icon"))
                .setMessage(UiTranslate.tr("Chắc chắn muốn tắt nó sẽ bị văng ra ngoài"))
                .setPositiveButton(UiTranslate.tr("Có")) { _, _ ->
                    val loading = android.app.ProgressDialog(this).apply {
                        setMessage("Đang đặt icon buổi tối…")
                        setCancelable(false)
                        show()
                    }
                    window.decorView.postDelayed({
                        try { loading.dismiss() } catch (_: Exception) {}
                        DynamicIconHelper.lockEveningIcon(this)
                        val home = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
                        home.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        try { startActivity(home) } catch (_: Exception) {}
                        finishAffinity()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }, 3000)
                }
                .setNegativeButton(UiTranslate.tr("Không"), null)
                .show()
        }

        // Pure alarm
        binding.switchPure.setCheckedSilent(AppSettings.isPureAlarmOnly(this))
        binding.switchPure.setOnCheckedChangeListener { sw, on ->
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setPureAlarmOnly(this, on)
                sw.setLoading(false)
            }, 280)
        }

        
        try {
            binding.switchFocusMode.setCheckedSilent(AppSettings.isFocusModeOnAlarm(this))
            binding.switchFocusMode.setOnCheckedChangeListener { sw, on ->
                sw.setLoading(true)
                sw.postDelayed({
                    AppSettings.setFocusModeOnAlarm(this, on)
                    sw.setLoading(false)
                    if (on) {
                        // Gợi ý cấp quyền DND nếu chưa có
                        try {
                            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                                && !nm.isNotificationPolicyAccessGranted) {
                                Toast.makeText(this, UiTranslate.tr("Cần cấp quyền Không làm phiền (nút bên dưới)"), Toast.LENGTH_LONG).show()
                            }
                        } catch (_: Exception) {}
                    }
                }, 200)
            }
            binding.btnGrantDnd.setOnClickListener {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Không mở được cài đặt: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            refreshDndGrantButton()
        } catch (_: Exception) {}

binding.switchAntiTroll.setCheckedSilent(AppSettings.isAntiTroll(this))
        binding.switchAntiTroll.setOnCheckedChangeListener { sw, on ->
            if (on && !AppSettings.hasAntiTrollPin(this)) {
                Toast.makeText(this, getString(R.string.anti_troll_pin_need), Toast.LENGTH_LONG).show()
                sw.setChecked(false, animate = true, notify = false)
                return@setOnCheckedChangeListener
            }
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setAntiTroll(this, on)
                sw.setLoading(false)
                Toast.makeText(
                    this,
                    getString(if (on) R.string.anti_troll_on else R.string.anti_troll_off),
                    Toast.LENGTH_SHORT
                ).show()
            }, 320)
        }
        binding.btnSaveAntiTrollPin.setOnClickListener {
            val pin = binding.etAntiTrollPin.text?.toString().orEmpty()
            if (pin.length < 4) {
                Toast.makeText(this, getString(R.string.anti_troll_pin_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setAntiTrollPin(this, pin)
            binding.etAntiTrollPin.text = null
            Toast.makeText(this, getString(R.string.anti_troll_pin_saved), Toast.LENGTH_SHORT).show()
        }

        // Gallery password + recovery email
        binding.etRecoveryEmail.setText(AppSettings.getRecoveryEmail(this))
        binding.btnSaveRecovery.setOnClickListener {
            val email = binding.etRecoveryEmail.text?.toString()?.trim().orEmpty()
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, UiTranslate.tr("Nhập email hợp lệ (vd: you@gmail.com)"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setRecoveryEmail(this, email)
            Toast.makeText(this, getString(R.string.recovery_email_saved), Toast.LENGTH_SHORT).show()
        }

        fun refreshBirthday() {
            val shown = try { BirthdayHelper.formatStored(this) } catch (_: Exception) { "" }
            binding.tvBirthdayStatus.text = if (shown.isBlank()) {
                getString(R.string.birthday_empty)
            } else {
                val src = if (AppSettings.getBirthdaySource(this) == "google")
                    getString(R.string.birthday_source_google) else getString(R.string.birthday_source_manual)
                getString(R.string.birthday_status, shown, src)
            }
        }
        try { refreshBirthday() } catch (_: Exception) {}
        try { AccountSecurity.attach(this, binding.root) } catch (_: Exception) {}
        binding.btnSyncBirthday.setOnClickListener {
            try { BirthdayHelper.syncNow(this) { refreshBirthday() } } catch (_: Exception) {}
        }
        binding.btnSetBirthday.setOnClickListener {
            try { BirthdayHelper.pickManual(this) { refreshBirthday() } } catch (_: Exception) {}
        }
        binding.btnSetGalleryPw.setOnClickListener {
            val pw = binding.etGalleryPw.text?.toString() ?: ""
            if (pw.length < 4) {
                Toast.makeText(this, getString(R.string.gallery_pw_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Tự lưu email khôi phục nếu đang điền sẵn
            val email = binding.etRecoveryEmail.text?.toString()?.trim().orEmpty()
            if (email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                AppSettings.setRecoveryEmail(this, email)
            }
            AppSettings.setGalleryPassword(this, pw)
            binding.etGalleryPw.text = null
            val hasEmail = AppSettings.getRecoveryEmail(this).isNotBlank()
            Toast.makeText(
                this,
                if (hasEmail) getString(R.string.gallery_pw_saved) + " (+ email khôi phục)"
                else getString(R.string.gallery_pw_saved) + " — nhớ Lưu email khôi phục!",
                Toast.LENGTH_LONG
            ).show()
        }
        binding.btnClearGalleryPw.setOnClickListener {
            AppSettings.clearGalleryPassword(this)
            Toast.makeText(this, getString(R.string.gallery_pw_cleared), Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshLanguageLabel() {
        val code = AppSettings.getLanguage(this)
        binding.tvLanguageValue.text = if (code == LanguageCatalog.SYSTEM || code.isBlank()) {
            getString(R.string.lang_system)
        } else {
            LanguageCatalog.displayName(code)
        }
    }

    private fun showLanguagePicker() {
        val systemLabel = getString(R.string.lang_system)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        val etSearch = EditText(this).apply {
            hint = getString(R.string.lang_search_hint)
            setSingleLine()
            setPadding(32, 24, 32, 24)
        }
        val tvCount = TextView(this).apply {
            text = getString(R.string.lang_count, LanguageCatalog.languages.size)
            setPadding(8, 8, 8, 8)
            textSize = 12f
        }
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 420).toInt()
            )
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 180
                removeDuration = 120
                changeDuration = 120
                moveDuration = 160
            }
        }
        container.addView(etSearch)
        container.addView(tvCount)
        container.addView(recycler)

        val current = AppSettings.getLanguage(this)
        val items = mutableListOf<LangRow>()
        items.add(LangRow(LanguageCatalog.SYSTEM, systemLabel, current == LanguageCatalog.SYSTEM))
        LanguageCatalog.languages.forEach {
            items.add(LangRow(it.code, it.displayLabel, it.code.equals(current, true)))
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null
        val adapter = LangAdapter(items) { code, view ->
            Motion.bounce(view) {
                AppSettings.setLanguage(this, code)
                LocaleHelper.applyLocale(code)
                refreshLanguageLabel()
                Motion.pulse(binding.cardLanguage)
                val label = if (code == LanguageCatalog.SYSTEM) systemLabel else LanguageCatalog.displayName(code)
                Toast.makeText(this, getString(R.string.lang_selected, label), Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
                binding.root.postDelayed({
                    // Tránh màn đen: recreate sau khi dialog đóng hẳn
                    window.setWindowAnimations(0)
                    recreate()
                }, 350)
            }
        }
        recycler.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                val filtered = mutableListOf<LangRow>()
                filtered.add(LangRow(LanguageCatalog.SYSTEM, systemLabel, current == LanguageCatalog.SYSTEM))
                LanguageCatalog.search(q).forEach {
                    filtered.add(LangRow(it.code, it.displayLabel, it.code.equals(current, true)))
                }
                tvCount.text = getString(R.string.lang_count, filtered.size - 1)
                adapter.replace(filtered)
            }
        })

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.lang_pick_title, LanguageCatalog.languages.size))
            .setView(container)
            .setNegativeButton(getString(R.string.close), null)
            .create()
        dialog.show()
        Motion.fadeScaleIn(container, delay = 30)
        Motion.slideFadeIn(etSearch, delay = 40)
    }

    private data class LangRow(val code: String, val label: String, val selected: Boolean)

    private class LangAdapter(
        private var rows: MutableList<LangRow>,
        private val onPick: (String, View) -> Unit
    ) : RecyclerView.Adapter<LangAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            holder.tv.text = if (row.selected) "✓  ${row.label}" else row.label
            holder.tv.setOnClickListener { v ->
                Motion.press(v) { onPick(row.code, v) }
            }
            // Stagger entrance — chỉ vài item đầu để tránh lag
            if (position < 12) {
                Motion.slideFadeIn(holder.itemView, delay = position * 28L)
            }
        }

        override fun getItemCount() = rows.size

        fun replace(newRows: List<LangRow>) {
            rows = newRows.toMutableList()
            notifyDataSetChanged()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        Motion.finishFade(this)
        return true
    }

    private fun refreshSettingsPinStatus() {
        try {
            val has = AppSettings.hasSettingsPin(this)
            val email = AppSettings.getRecoveryEmail(this)
            binding.tvSettingsPinStatus.text = when {
                has && email.isNotBlank() -> "✅ Đang khóa PIN · Gmail khôi phục: $email"
                has -> "⚠️ Đang khóa PIN · Chưa có Gmail khôi phục — nên lưu email!"
                else -> getString(R.string.pin_unlocked)
            }
        } catch (_: Exception) {}
    }
    override fun onResume() {
        super.onResume()
        try { binding.root.alpha = 1f } catch (_: Exception) {}
        refreshDndGrantButton()
        bindGoogleProfileRow()
    }

    /** Nút cấp quyền biến mất khi hệ thống đã cho phép Không làm phiền. Công tắc bật không được tính. */
    private fun refreshDndGrantButton() {
        try {
            val granted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.isNotificationPolicyAccessGranted
            } else true
            binding.btnGrantDnd.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        } catch (_: Exception) {}
    }


    private fun signedInNow(): Boolean {
        val name = AppSettings.getGoogleDisplayName(this)
        val email = AppSettings.getRecoveryEmail(this)
        val fb = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null } catch (_: Exception) { false }
        return name.isNotBlank() || email.isNotBlank() || fb
    }

    private fun signOutAccount() {
        try { com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut() } catch (_: Exception) {}
        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        AppSettings.setGoogleDisplayName(this, "")
        AppSettings.setRecoveryEmail(this, "")
        AppSettings.setGooglePhotoUrl(this, "")
        Toast.makeText(this, Lang.t(this, "Đã đăng xuất", "Signed out"), Toast.LENGTH_SHORT).show()
        bindGoogleProfileRow()
    }

    @Deprecated("permissions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 93) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                WallpaperTheme.invalidate()
                Thread {
                    try { RingTheme.snapshotWallpaper(applicationContext) } catch (_: Throwable) {}
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            WallpaperTheme.markApplied(this)
                            recreate()
                        }
                    }
                }.start()
            } else {
                android.widget.Toast.makeText(
                    this,
                    Lang.t(this, "Chưa cấp quyền đọc hình nền", "Wallpaper permission not granted"),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bindGoogleProfileRow() {
        try {
            val signed = signedInNow()
            val rawName = AppSettings.getGoogleDisplayName(this)
            val name = if (signed) rawName.ifBlank { "Đã đăng nhập" } else "Chưa đăng nhập"
            val email = AppSettings.getRecoveryEmail(this)
            findViewById<android.widget.TextView>(R.id.tvGoogleName)?.text = name
            findViewById<android.widget.TextView>(R.id.tvGoogleEmail)?.text = if (signed && email.isBlank()) "Facebook" else email
                        val url = AppSettings.getGooglePhotoUrl(this)
            val img = findViewById<android.widget.ImageView>(R.id.imgGoogleAvatar) ?: return
            if (url.isBlank()) return
            Thread {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = true
                    val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()
                    if (bmp != null) runOnUiThread { img.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }.start()
        } catch (_: Exception) {}
    }


    /** Chọn kiểu tắt báo thức, có mẫu chạy thử ngay trong thẻ để xem trước. */
    private fun setupDismissStyle() {
        val primary = try {
            com.google.android.material.color.MaterialColors.getColor(
                binding.root, androidx.appcompat.R.attr.colorPrimary
            )
        } catch (_: Exception) {
            0xFF4351B7.toInt()
        }
        val density = resources.displayMetrics.density
        binding.previewSwipe.setDemo(true)

        fun render() {
            val sel = AppSettings.getDismissStyle(this)
            val rows = listOf(
                Triple(binding.cardStyleButton, binding.tvStyleButtonState, 0),
                Triple(binding.cardStyleSwipe, binding.tvStyleSwipeState, 1)
            )
            for ((card, state, idx) in rows) {
                val on = sel == idx
                card.strokeColor = if (on) primary else 0x26808080
                card.strokeWidth = ((if (on) 2.5f else 1f) * density).toInt()
                card.setCardBackgroundColor(
                    if (on) androidx.core.graphics.ColorUtils.setAlphaComponent(primary, 0x1A)
                    else 0x00000000
                )
                state.text = if (on) "✓ Đang dùng"
                else if (idx == 0) "Chạm nút Tắt để dừng chuông"
                else "Kéo núm sang phải để dừng chuông"
                state.setTextColor(if (on) primary else 0xFF80868B.toInt())
            }
        }

        fun pick(style: Int, card: android.view.View) {
            if (AppSettings.getDismissStyle(this) == style) return
            AppSettings.setDismissStyle(this, style)
            render()
            card.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
        }
        binding.cardStyleButton.setOnClickListener { pick(0, binding.cardStyleButton) }
        binding.cardStyleSwipe.setOnClickListener { pick(1, binding.cardStyleSwipe) }
        render()
    }

    private fun refreshQuickDismissValue() {
        try {
            findViewById<android.widget.TextView>(R.id.tvQuickDismissValue)?.text =
                QuickDismiss.label(AppSettings.getQuickDismiss(this))
        } catch (_: Exception) {}
    }

    private fun showQuickDismissPicker() {
        val modes = QuickDismiss.all
        val labels = modes.map { QuickDismiss.label(it) }.toTypedArray()
        val cur = modes.indexOf(AppSettings.getQuickDismiss(this)).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(Lang.t(this, "Cách tắt báo thức nhanh", "Quick stop gesture"))
            .setSingleChoiceItems(labels, cur) { d, which ->
                AppSettings.setQuickDismiss(this, modes[which])
                refreshQuickDismissValue()
                d.dismiss()
            }
            .setNegativeButton(Lang.t(this, "Đóng", "Close"), null)
            .show()
    }
}
