package com.example.alarmclock

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.alarmclock.databinding.ActivityFeaturesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

class FeaturesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeaturesBinding
    private var dreamHelper: DreamRecorderHelper? = null
    private var isRecording = false
    private lateinit var repo: AlarmRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeaturesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.features_menu)
        repo = AlarmRepository(this)

        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        binding.btnGallery.setOnClickListener {
            startActivity(android.content.Intent(this, GalleryActivity::class.java))
        }
        binding.btnPureAlarm.setOnClickListener {
            val next = !AppSettings.isPureAlarmOnly(this)
            AppSettings.setPureAlarmOnly(this, next)
            updatePureAlarmButton()
            Toast.makeText(
                this,
                if (next) "Chỉ chuông thuần — TTS/Spotify khi báo thức đã tắt"
                else "TTS / ghi chú giọng nói khi báo thức: BẬT",
                Toast.LENGTH_LONG
            ).show()
        }
        updatePureAlarmButton()
        binding.btnSleepCycle.setOnClickListener { showSleepCycleDialog() }
        binding.btnDreamJournal.setOnClickListener { toggleDreamRecord() }
        binding.btnSleepStats.setOnClickListener { showSleepStats() }
        binding.btnWeather.setOnClickListener { speakWeather() }
        binding.btnSpotify.setOnClickListener {
            SpotifyHelper.play(this)
            Toast.makeText(this, "Đang mở Spotify…", Toast.LENGTH_SHORT).show()
        }
        binding.btnYoutube.setOnClickListener {
            YouTubeMusicHelper.playSearch(this, "nhạc báo thức buổi sáng")
        }
        binding.btnCloudPush.setOnClickListener {
            CloudSyncHelper.pushAlarms(this, repo.getAlarms())
        }
        binding.btnCloudPull.setOnClickListener {
            CloudSyncHelper.pullAlarms(this) { list ->
                if (list.isEmpty()) {
                    Toast.makeText(this, "Cloud trống hoặc lỗi", Toast.LENGTH_SHORT).show()
                } else {
                    repo.saveAlarms(list)
                    list.filter { it.isEnabled }.forEach { AlarmScheduler.schedule(this, it) }
                    Toast.makeText(this, "Đã kéo ${list.size} báo thức từ Cloud", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnEmergencySetup.setOnClickListener { setupEmergency() }
        binding.btnEmergencySend.setOnClickListener { sendEmergency() }
        binding.btnSaveGpsHome.setOnClickListener { saveCurrentAsHome() }
        binding.btnCheckGps.setOnClickListener { checkNearHome() }
    }

    private fun setupEmergency() {
        val input = EditText(this).apply {
            setText(EmergencySmsHelper.getPhone(this@FeaturesActivity))
            hint = "Số điện thoại (vd: 0901234567)"
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Số cứu viện (Emergency SMS)")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                EmergencySmsHelper.setPhone(this, input.text.toString())
                Toast.makeText(this, "Đã lưu số cứu viện", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun sendEmergency() {
        requestSmsAndLocation {
            LocationHelper.getLastLocation(this) { locText ->
                EmergencySmsHelper.sendAlert(this, locText)
            }
        }
    }

    private fun saveCurrentAsHome() {
        requestLocation {
            LocationHelper.getLastLocation(this) { text ->
                // parse "lat, lng | maps"
                val parts = text.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].split("|").first().trim().toDoubleOrNull()
                    if (lat != null && lng != null) {
                        LocationHelper.setTargetLocation(this, lat, lng, 300f)
                        Toast.makeText(this, "Đã lưu vị trí nhà/cơ quan:\n$text", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkNearHome() {
        requestLocation {
            val target = LocationHelper.getTarget(this)
            if (target == null) {
                Toast.makeText(this, "Chưa lưu vị trí nhà. Bấm \"Lưu vị trí hiện tại\" trước.", Toast.LENGTH_LONG).show()
                return@requestLocation
            }
            LocationHelper.getLastLocation(this) { text ->
                val parts = text.split(",").map { it.trim() }
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lng = parts.getOrNull(1)?.split("|")?.first()?.trim()?.toDoubleOrNull()
                if (lat == null || lng == null) {
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                    return@getLastLocation
                }
                val (tLat, tLng, radius) = target
                val dist = LocationHelper.distanceMeters(lat, lng, tLat, tLng)
                val msg = if (dist <= radius) {
                    "Bạn đang GẦN vị trí đã lưu (cách ${dist.toInt()} m, bán kính ${radius.toInt()} m)"
                } else {
                    "Bạn đang XA vị trí đã lưu (cách ${dist.toInt()} m, bán kính ${radius.toInt()} m)"
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle("GPS Location Alarm")
                    .setMessage("$msg\n\nHiện tại: $text")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun requestSmsAndLocation(then: () -> Unit) {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.SEND_SMS)
        if (!LocationHelper.hasPermission(this)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (need.isEmpty()) then()
        else ActivityCompat.requestPermissions(this, need.toTypedArray(), 201)
    }

    private fun requestLocation(then: () -> Unit) {
        if (LocationHelper.hasPermission(this)) then()
        else ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            202
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            when (requestCode) {
                201 -> sendEmergency()
                202 -> { /* user can press button again */ Toast.makeText(this, "Đã cấp quyền vị trí, bấm lại nút", Toast.LENGTH_SHORT).show() }
            }
        } else {
            Toast.makeText(this, "Cần cấp quyền để dùng tính năng này", Toast.LENGTH_LONG).show()
        }
    }

    private fun speakWeather() {
        Toast.makeText(this, "Đang lấy thời tiết…", Toast.LENGTH_SHORT).show()
        thread {
            val text = WeatherHelper.fetchWeatherSummary("Hanoi")
            Handler(Looper.getMainLooper()).post {
                val tts = TtsHelper(this)
                tts.speak(text)
                MaterialAlertDialogBuilder(this)
                    .setTitle("Thời tiết")
                    .setMessage(text)
                    .setPositiveButton("OK") { _, _ -> tts.shutdown() }
                    .show()
            }
        }
    }

    private fun showSleepCycleDialog() {
        TimePickerDialog(this, { _, hour, minute ->
            val suggestions = SleepCycleCalculator.suggestBedtimes(hour, minute)
            val lines = suggestions.mapIndexed { i, (h, m) ->
                "${i + 1} chu kỳ → đi ngủ lúc ${SleepCycleCalculator.formatTime(h, m)}"
            }.joinToString("\n")
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sleep_cycle_title))
                .setMessage("Thức dậy ${SleepCycleCalculator.formatTime(hour, minute)}\n\n$lines")
                .setPositiveButton("OK", null)
                .show()
        }, 6, 30, true).show()
    }

    private fun toggleDreamRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }
        if (!isRecording) {
            dreamHelper = DreamRecorderHelper(this)
            val file = dreamHelper?.start()
            if (file != null) {
                isRecording = true
                binding.btnDreamJournal.text = getString(R.string.stop_record)
                Toast.makeText(this, "Đang ghi âm giấc mơ…", Toast.LENGTH_SHORT).show()
            }
        } else {
            val file = dreamHelper?.stop()
            isRecording = false
            binding.btnDreamJournal.text = getString(R.string.start_record)
            Toast.makeText(this, "Đã lưu: ${file?.name}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSleepStats() {
        val logs = repo.getSleepLogs()
        if (logs.isEmpty()) {
            Toast.makeText(this, "Chưa có dữ liệu ngủ", Toast.LENGTH_SHORT).show()
            return
        }
        val msg = logs.takeLast(7).joinToString("\n") { (_, _, dur) -> "• $dur phút" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Thống kê ngủ (gần đây)")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updatePureAlarmButton() {
        val on = AppSettings.isPureAlarmOnly(this)
        binding.btnPureAlarm.text = if (on) "Chuông thuần: BẬT (bấm để tắt)" else "Chuông thuần: TẮT (bấm để bật)"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
