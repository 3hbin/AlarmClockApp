package com.example.alarmclock

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Cài đặt"

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

        // Vibrate
        binding.switchVibrate.isChecked = AppSettings.isVibrate(this)
        binding.switchVibrate.setOnCheckedChangeListener { _, on ->
            AppSettings.setVibrate(this, on)
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
            Toast.makeText(this, "Đã đổi chế độ tối", Toast.LENGTH_SHORT).show()
        }

        // 12/24h
        binding.switch24h.isChecked = AppSettings.isUse24h(this)
        binding.switch24h.setOnCheckedChangeListener { _, on ->
            AppSettings.setUse24h(this, on)
        }

        // Pure alarm
        binding.switchPure.isChecked = AppSettings.isPureAlarmOnly(this)
        binding.switchPure.setOnCheckedChangeListener { _, on ->
            AppSettings.setPureAlarmOnly(this, on)
        }

        // Gallery password + recovery email
        binding.etRecoveryEmail.setText(AppSettings.getRecoveryEmail(this))
        binding.btnSaveRecovery.setOnClickListener {
            AppSettings.setRecoveryEmail(this, binding.etRecoveryEmail.text?.toString() ?: "")
            Toast.makeText(this, "Đã lưu email khôi phục", Toast.LENGTH_SHORT).show()
        }
        binding.btnSetGalleryPw.setOnClickListener {
            val pw = binding.etGalleryPw.text?.toString() ?: ""
            if (pw.length < 4) {
                Toast.makeText(this, "Mật khẩu ít nhất 4 ký tự", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setGalleryPassword(this, pw)
            binding.etGalleryPw.text = null
            Toast.makeText(this, "Đã đặt mật khẩu bộ sưu tập", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
