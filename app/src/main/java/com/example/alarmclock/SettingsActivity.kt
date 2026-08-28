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
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { BottomNavHelper.bind(this, binding.curvedNav, 5) } catch (_: Exception) {}
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        // Ngôn ngữ (~195) + hiệu ứng
        refreshLanguageLabel()
        binding.cardLanguage.setOnClickListener { v ->
            Motion.press(v) { showLanguagePicker() }
        }
        Motion.fadeScaleIn(binding.cardLanguage, delay = 40)

        // Volume
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

        // Kiểu thanh dưới: cong Android / Liquid Glass
        when (AppSettings.getBottomNavStyle(this)) {
            AppSettings.NAV_LIQUID_GLASS -> binding.rbNavGlass.isChecked = true
            else -> binding.rbNavCurved.isChecked = true
        }
        binding.rgNavStyle.setOnCheckedChangeListener { _, id ->
            val style = if (id == R.id.rbNavGlass) AppSettings.NAV_LIQUID_GLASS else AppSettings.NAV_CURVED
            AppSettings.setBottomNavStyle(this, style)
            // Áp dụng ngay trên màn Cài đặt
            try {
                binding.curvedNav.navStyle = if (style == AppSettings.NAV_LIQUID_GLASS)
                    CurvedBottomNavView.Style.LIQUID_GLASS
                else
                    CurvedBottomNavView.Style.CURVED
                binding.curvedNav.setItems(BottomNavHelper.items(), 5)
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
            AppSettings.setRecoveryEmail(this, binding.etRecoveryEmail.text?.toString() ?: "")
            Toast.makeText(this, getString(R.string.recovery_email_saved), Toast.LENGTH_SHORT).show()
        }
        binding.btnSetGalleryPw.setOnClickListener {
            val pw = binding.etGalleryPw.text?.toString() ?: ""
            if (pw.length < 4) {
                Toast.makeText(this, getString(R.string.gallery_pw_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setGalleryPassword(this, pw)
            binding.etGalleryPw.text = null
            Toast.makeText(this, getString(R.string.gallery_pw_saved), Toast.LENGTH_SHORT).show()
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
                binding.root.postDelayed({ recreate() }, 220)
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
}
