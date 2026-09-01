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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { BottomNavHelper.bind(this, binding.curvedNav, 5) } catch (_: Exception) {}
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
                    Toast.makeText(this, "PIN tối thiểu 4 chữ số", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val email = AppSettings.getRecoveryEmail(this)
                if (email.isBlank()) {
                    Toast.makeText(this, "Nên lưu Gmail khôi phục trước (phía dưới)", Toast.LENGTH_LONG).show()
                }
                AppSettings.setSettingsPin(this, pin)
                AppSettings.settingsUnlockedThisSession = true
                binding.edtSettingsPin.setText("")
                refreshSettingsPinStatus()
                Toast.makeText(this, "Đã khóa Cài đặt bằng PIN", Toast.LENGTH_SHORT).show()
            }
            binding.btnClearSettingsPin.setOnClickListener {
                if (!AppSettings.hasSettingsPin(this)) {
                    Toast.makeText(this, "Chưa đặt PIN", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Xác nhận bằng PIN hiện tại
                val input = android.widget.EditText(this).apply {
                    hint = "Nhập PIN hiện tại để xóa"
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    setPadding(48, 32, 48, 32)
                }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Xóa PIN Cài đặt?")
                    .setView(input)
                    .setPositiveButton("Xóa") { _, _ ->
                        val pin = input.text?.toString()?.trim().orEmpty()
                        if (AppSettings.checkSettingsPin(this, pin)) {
                            AppSettings.clearSettingsPin(this)
                            refreshSettingsPinStatus()
                            Toast.makeText(this, "Đã xóa PIN Cài đặt", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Sai PIN", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton("Quên PIN?") { _, _ ->
                        SettingsLockHelper.showForgotFlow(this) {
                            refreshSettingsPinStatus()
                        }
                    }
                    .setNegativeButton("Hủy", null)
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
                Toast.makeText(this, "Đã đổi cỡ chữ — mở lại màn hình để áp dụng", Toast.LENGTH_SHORT).show()
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
            val mode = when (id) {
                R.id.rbDarkOn -> 1
                R.id.rbDarkOff -> 2
                else -> 0
            }
            AppSettings.setDarkMode(this, mode)
            Toast.makeText(this, getString(R.string.dark_mode_changed), Toast.LENGTH_SHORT).show()
        }

        // Kiểu thanh dưới: Curved / Persistent / Google Nav
        when (AppSettings.getBottomNavStyle(this)) {
            AppSettings.NAV_PERSISTENT -> binding.rbNavPersistent.isChecked = true
            AppSettings.NAV_GOOGLE -> binding.rbNavGoogle.isChecked = true
            else -> binding.rbNavCurved.isChecked = true
        }
        binding.rgNavStyle.setOnCheckedChangeListener { _, id ->
            val style = when (id) {
                R.id.rbNavPersistent -> AppSettings.NAV_PERSISTENT
                R.id.rbNavGoogle -> AppSettings.NAV_GOOGLE
                else -> AppSettings.NAV_CURVED
            }
            AppSettings.setBottomNavStyle(this, style)
            try {
                binding.curvedNav.navStyle = when (style) {
                    AppSettings.NAV_PERSISTENT -> CurvedBottomNavView.Style.PERSISTENT
                    AppSettings.NAV_GOOGLE -> CurvedBottomNavView.Style.GOOGLE
                    else -> CurvedBottomNavView.Style.CURVED
                }
                binding.curvedNav.setItems(BottomNavHelper.items(this), 5)
            } catch (_: Exception) {}
            Toast.makeText(this, getString(R.string.nav_style_changed), Toast.LENGTH_SHORT).show()
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

        // Pure alarm
        binding.switchPure.setCheckedSilent(AppSettings.isPureAlarmOnly(this))
        binding.switchPure.setOnCheckedChangeListener { sw, on ->
            sw.setLoading(true)
            sw.postDelayed({
                AppSettings.setPureAlarmOnly(this, on)
                sw.setLoading(false)
            }, 280)
        }

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
                Toast.makeText(this, "Nhập email hợp lệ (vd: you@gmail.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setRecoveryEmail(this, email)
            Toast.makeText(this, getString(R.string.recovery_email_saved), Toast.LENGTH_SHORT).show()
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
                else -> "Chưa khóa — ai cũng vào được Cài đặt"
            }
        } catch (_: Exception) {}
    }
}
