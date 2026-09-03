package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.alarmclock.databinding.ActivityTimerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TimerActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    private lateinit var binding: ActivityTimerBinding
    private var timeLeftInMillis: Long = 0
    private var isRunning = false
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var flashHelper: FlashHelper? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerService.ACTION_UPDATE -> {
                    timeLeftInMillis = intent.getLongExtra(TimerService.EXTRA_MS, 0L)
                    isRunning = intent.getBooleanExtra(TimerService.EXTRA_RUNNING, false)
                    updateCountDownText()
                    binding.btnStartPause.text = if (isRunning) "Pause" else "Start"
                }
                TimerService.ACTION_FINISHED -> {
                    timeLeftInMillis = 0
                    isRunning = false
                    updateCountDownText()
                    binding.btnStartPause.text = "Start"
                    onTimerFinished()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        try { BottomNavHelper.bind(this, binding.curvedNav, 3) } catch (_: Exception) {}

        AlarmNotificationHelper.ensureChannels(this)

        // Đồng bộ nếu service đang chạy
        if (TimerService.isActive && TimerService.remainingMs > 0) {
            timeLeftInMillis = TimerService.remainingMs
            isRunning = true
            binding.btnStartPause.text = "Pause"
            updateCountDownText()
        }

        binding.btnStartPause.setOnClickListener {
            SoundHelper.animatePress(it)
            if (isRunning) {
                SoundHelper.playPause(this)
                pauseTimer()
            } else {
                SoundHelper.playStart(this)
                startTimer()
            }
        }

        binding.btnReset.setOnClickListener {
            SoundHelper.animatePress(it)
            SoundHelper.playClick(this)
            resetTimer()
        }
        binding.btn1min.setOnClickListener { SoundHelper.animatePress(it); SoundHelper.playClick(this); setTime(1) }
        binding.btn5min.setOnClickListener { SoundHelper.animatePress(it); SoundHelper.playClick(this); setTime(5) }
        binding.btn10min.setOnClickListener { SoundHelper.animatePress(it); SoundHelper.playClick(this); setTime(10) }
        binding.btn15min.setOnClickListener { SoundHelper.animatePress(it); SoundHelper.playClick(this); setTime(15) }

        binding.btnCustom.setOnClickListener {
            if (isRunning) {
                Toast.makeText(this, "Hãy Pause trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showCustomTimeDialog()
        }

        binding.btnStopRing.setOnClickListener {
            stopRinging()
            binding.ringLayout.visibility = android.view.View.GONE
            resetTimer()
        }

        updateCountDownText()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_UPDATE)
            addAction(TimerService.ACTION_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }
    }

    override fun onStop() {
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
        // Không hủy service — giữ chạy nền + thông báo
    }

    private fun showCustomTimeDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(40, 20, 40, 20)
            gravity = android.view.Gravity.CENTER
        }
        val hourPicker = NumberPicker(this).apply { minValue = 0; maxValue = 23; value = 0 }
        val minPicker = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = 5 }
        val secPicker = NumberPicker(this).apply { minValue = 0; maxValue = 59; value = 0 }
        layout.addView(hourPicker)
        layout.addView(android.widget.TextView(this).apply { text = " giờ " })
        layout.addView(minPicker)
        layout.addView(android.widget.TextView(this).apply { text = " phút " })
        layout.addView(secPicker)
        layout.addView(android.widget.TextView(this).apply { text = " giây" })

        MaterialAlertDialogBuilder(this)
            .setTitle("Chọn thời gian")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val total = (hourPicker.value * 3600L + minPicker.value * 60L + secPicker.value) * 1000L
                if (total <= 0) Toast.makeText(this, "Thời gian phải > 0", Toast.LENGTH_SHORT).show()
                else {
                    timeLeftInMillis = total
                    updateCountDownText()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun setTime(minutes: Int) {
        if (isRunning) return
        timeLeftInMillis = minutes * 60 * 1000L
        updateCountDownText()
    }

    private fun startTimer() {
        if (timeLeftInMillis <= 0) {
            Toast.makeText(this, "Chọn thời gian trước", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra(TimerService.EXTRA_MS, timeLeftInMillis)
        }
        ContextCompat.startForegroundService(this, intent)
        isRunning = true
        binding.btnStartPause.text = "Pause"
        Toast.makeText(this, "Đếm ngược chạy nền — thoát app vẫn chạy (xem thông báo)", Toast.LENGTH_SHORT).show()
    }

    private fun pauseTimer() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_PAUSE
        }
        startService(intent)
        isRunning = false
        binding.btnStartPause.text = "Start"
    }

    private fun resetTimer() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP
        }
        startService(intent)
        timeLeftInMillis = 0
        isRunning = false
        binding.btnStartPause.text = "Start"
        binding.ringLayout.visibility = android.view.View.GONE
        stopRinging()
        updateCountDownText()
    }

    private fun updateCountDownText() {
        val totalSeconds = timeLeftInMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        binding.tvTimer.text = if (hours > 0)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        else
            String.format("%02d:%02d", minutes, seconds)
    }

    private fun onTimerFinished() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.ringLayout.visibility = android.view.View.VISIBLE
        startRinging()
        val repo = AlarmRepository(this)
        if (repo.isFlashEnabled()) {
            flashHelper = FlashHelper(this)
            flashHelper?.startFlashing()
        }
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@TimerActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
        }
    }

    private fun stopRinging() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        flashHelper?.stopFlashing()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
        // Service tiếp tục chạy nếu đang đếm
    }
    override fun onResume() {
        super.onResume()
        try { binding.root.alpha = 1f } catch (_: Exception) {}
        try { binding.curvedNav.selectIndex(3, animate = false) } catch (_: Exception) {}
    }

}
