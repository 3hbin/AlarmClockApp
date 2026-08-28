package com.example.alarmclock

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivityTimerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var isRunning = false
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var flashHelper: FlashHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
            }
            override fun onFinish() {
                timeLeftInMillis = 0
                updateCountDownText()
                isRunning = false
                binding.btnStartPause.text = "Start"
                onTimerFinished()
            }
        }.start()
        isRunning = true
        binding.btnStartPause.text = "Pause"
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        binding.btnStartPause.text = "Start"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
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
        } catch (e: Exception) { e.printStackTrace() }
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
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
        super.onDestroy()
        countDownTimer?.cancel()
        stopRinging()
    }
}
