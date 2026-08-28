package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivityAlarmRingBinding
import java.util.Calendar
import kotlin.math.sqrt

class AlarmRingActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityAlarmRingBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: Int = -1
    private var snoozeMinutes: Int = 5
    private var repeatMode: Int = Alarm.REPEAT_DAILY
    private var ringtoneUri: String? = null
    private var flashHelper: FlashHelper? = null

    // Thử thách báo thức
    private var challengeType: Int = Alarm.CHALLENGE_NONE
    private var isStrictAntiSnooze: Boolean = false
    private var voiceNote: String? = null
    private var ttsHelper: TtsHelper? = null
    private var shakeTargetCount: Int = 10
    private var currentShakeCount = 0
    private var lastShakeTime: Long = 0
    private var mathAnswer: Int = 0
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var currentLabel: String = ""
    private val faceChallengeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            dismissAlarm(AlarmRepository(this))
        } else {
            Toast.makeText(this, "Chưa xác minh khuôn mặt — báo thức vẫn kêu", Toast.LENGTH_LONG).show()
        }
    }

    /** Nhận lệnh Tắt từ nút trên thông báo (tránh lỡ tay full-screen). */
    private val forceStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmActionReceiver.ACTION_FORCE_STOP_RING) {
                // Chỉ chấp nhận khi không chống troll / không challenge
                if (challengeType == Alarm.CHALLENGE_NONE &&
                    !AppSettings.isAntiTroll(this@AlarmRingActivity) &&
                    !isStrictAntiSnooze
                ) {
                    dismissAlarm(AlarmRepository(this@AlarmRingActivity))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreenAndTurnScreenOn()

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getIntExtra("ALARM_ID", -1)
        val label = intent.getStringExtra("ALARM_LABEL") ?: getString(R.string.app_name)
        currentLabel = label
        snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 5)
        repeatMode = intent.getIntExtra("REPEAT_MODE", Alarm.REPEAT_DAILY)
        ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        challengeType = intent.getIntExtra("CHALLENGE_TYPE", Alarm.CHALLENGE_NONE)
        isStrictAntiSnooze = intent.getBooleanExtra("STRICT_ANTI_SNOOZE", false)
        voiceNote = intent.getStringExtra("VOICE_NOTE")
        if (!AppSettings.isPureAlarmOnly(this)) {
            ttsHelper = TtsHelper(this)
            voiceNote?.let { ttsHelper?.speakVoiceNote(it) }
        }
        shakeTargetCount = intent.getIntExtra("SHAKE_TARGET_COUNT", 10)

        binding.tvLabel.text = label
        binding.btnSnooze.text = "${getString(R.string.snooze)} ($snoozeMinutes phút)"
        if (isStrictAntiSnooze) {
            binding.btnSnooze.visibility = View.GONE
        }

        // Đăng ký nhận Tắt từ notification
        val filter = IntentFilter(AlarmActionReceiver.ACTION_FORCE_STOP_RING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(forceStopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(forceStopReceiver, filter)
        }

        // Hiện thông báo có nút Tắt (tránh lỡ tay)
        val allowDirectDismiss =
            challengeType == Alarm.CHALLENGE_NONE &&
                !AppSettings.isAntiTroll(this) &&
                !isStrictAntiSnooze
        AlarmNotificationHelper.showRingingNotification(this, alarmId, label, allowDirectDismiss)

        startRinging()
        enforceAntiTroll()
        setupChallengeUi()

        val repo = AlarmRepository(this)
        if (repo.isFlashEnabled()) {
            flashHelper = FlashHelper(this)
            flashHelper?.startFlashing()
        }

        binding.btnDismiss.setOnClickListener {
            requestDismiss(repo)
        }

        binding.btnSnooze.setOnClickListener {
            if (AppSettings.isAntiTroll(this) || isStrictAntiSnooze) {
                Toast.makeText(this, "Chế độ chống troll: không được hoãn!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            snoozeAlarm(label)
        }
    }

    /**
     * Hiển thị đúng loại thử thách (Toán / Lắc máy / Không có) và ẩn nút Tắt
     * mặc định cho tới khi thử thách được hoàn thành.
     */
    private fun setupChallengeUi() {
        when (challengeType) {
            Alarm.CHALLENGE_MATH -> {
                binding.layoutMathChallenge.visibility = View.VISIBLE
                binding.layoutShakeChallenge.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.VISIBLE
                initMathChallenge()
            }
            Alarm.CHALLENGE_SHAKE -> {
                binding.layoutMathChallenge.visibility = View.GONE
                binding.layoutShakeChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.VISIBLE
                initShakeChallenge()
            }
            Alarm.CHALLENGE_FACE -> {
                binding.layoutMathChallenge.visibility = View.GONE
                binding.layoutShakeChallenge.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                faceChallengeLauncher.launch(android.content.Intent(this, FaceChallengeActivity::class.java))
            }
            Alarm.CHALLENGE_BIOMETRIC -> {
                binding.layoutMathChallenge.visibility = View.GONE
                binding.layoutShakeChallenge.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                BiometricHelper.authenticate(
                    this,
                    onSuccess = { dismissAlarm(AlarmRepository(this)) },
                    onFail = { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        // Chụp mặt nếu thất bại
                        if (AppSettings.isFaceCaptureOnFail(this)) {
                            faceChallengeLauncher.launch(
                                android.content.Intent(this, FaceChallengeActivity::class.java)
                            )
                        }
                    }
                )
            }
            else -> {
                binding.layoutMathChallenge.visibility = View.GONE
                binding.layoutShakeChallenge.visibility = View.GONE
                binding.btnDismiss.visibility = View.VISIBLE
            }
        }
    }

    private fun initMathChallenge() {
        val num1 = (10..50).random()
        val num2 = (1..20).random()
        mathAnswer = num1 + num2

        binding.tvMathQuestion.text = getString(R.string.math_question, num1, num2)

        binding.btnSubmitMath.setOnClickListener {
            val userAnswer = binding.edtMathAnswer.text.toString().toIntOrNull()
            if (userAnswer != null && userAnswer == mathAnswer) {
                dismissAlarm(AlarmRepository(this))
            } else {
                Toast.makeText(this, getString(R.string.wrong_answer), Toast.LENGTH_SHORT).show()
                binding.edtMathAnswer.setText("")
            }
        }
    }

    private fun initShakeChallenge() {
        binding.tvShakeProgress.text = getString(R.string.shake_progress, currentShakeCount, shakeTargetCount)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            // Máy không có cảm biến gia tốc: cho phép tắt bằng nút thường để tránh kẹt màn hình
            Toast.makeText(this, getString(R.string.no_accelerometer), Toast.LENGTH_LONG).show()
            binding.layoutShakeChallenge.visibility = View.GONE
            binding.btnDismiss.visibility = View.VISIBLE
            return
        }

        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (challengeType != Alarm.CHALLENGE_SHAKE || event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        val currentTime = System.currentTimeMillis()

        if (gForce > 2.0 && currentTime - lastShakeTime > 300) {
            lastShakeTime = currentTime
            currentShakeCount++
            binding.tvShakeProgress.text =
                getString(R.string.shake_progress, currentShakeCount, shakeTargetCount)

            if (currentShakeCount >= shakeTargetCount) {
                dismissAlarm(AlarmRepository(this))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    private fun enforceAntiTroll() {
        if (!AppSettings.isAntiTroll(this)) return
        // Ẩn snooze, ép volume lớn
        binding.btnSnooze.visibility = View.GONE
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) {}
        // Giữ volume: mỗi 2s set lại max
        binding.root.post(object : Runnable {
            override fun run() {
                if (isFinishing) return
                try {
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                } catch (_: Exception) {}
                binding.root.postDelayed(this, 2000)
            }
        })
        Toast.makeText(this, "🛡️ Chống troll: cần mã PIN / thử thách để tắt", Toast.LENGTH_LONG).show()
    }

    private fun requestDismiss(repo: AlarmRepository) {
        // Nếu đang có challenge (math/shake/face) thì không cho bấm tắt trực tiếp
        if (challengeType != Alarm.CHALLENGE_NONE && binding.btnDismiss.visibility != View.VISIBLE) {
            Toast.makeText(this, "Hãy hoàn thành thử thách trước!", Toast.LENGTH_SHORT).show()
            return
        }
        if (AppSettings.isAntiTroll(this) && AppSettings.hasAntiTrollPin(this)) {
            val input = EditText(this).apply {
                hint = "Nhập mã PIN chống troll"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setPadding(48, 32, 48, 32)
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Xác minh tắt báo thức")
                .setMessage("Nhập PIN để tắt — chống người khác troll")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Tắt") { _, _ ->
                    val pin = input.text?.toString().orEmpty()
                    if (AppSettings.checkAntiTrollPin(this, pin)) {
                        dismissAlarm(repo)
                    } else {
                        Toast.makeText(this, "Sai PIN! Báo thức vẫn kêu.", Toast.LENGTH_LONG).show()
                        // Chụp nhanh ~0.1s → lưu Bộ sưu tập (chống troll)
                        if (AppSettings.isFaceCaptureOnFail(this)) {
                            QuickIntruderCapture.snap(this, this)
                        }
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        } else {
            dismissAlarm(repo)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (AppSettings.isAntiTroll(this) || isStrictAntiSnooze || challengeType != Alarm.CHALLENGE_NONE) {
            Toast.makeText(this, "Không thể thoát — hãy tắt đúng cách!", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    private fun dismissAlarm(repo: AlarmRepository) {
        stopRinging()
        AlarmNotificationHelper.cancelRinging(this)
        if (repeatMode != Alarm.REPEAT_ONCE) {
            val alarms = repo.getAlarms()
            val alarm = alarms.find { it.id == alarmId }
            if (alarm != null) {
                AlarmScheduler.schedule(this, alarm)
            }
        }
        finish()
    }

    private fun snoozeAlarm(label: String) {
        stopRinging()
        AlarmNotificationHelper.cancelRinging(this)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, snoozeMinutes)
        val snoozeAlarm = Alarm(
            id = alarmId + 10000,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isEnabled = true,
            label = label,
            snoozeMinutes = snoozeMinutes,
            ringtoneUri = ringtoneUri,
            challengeType = challengeType,
            shakeTargetCount = shakeTargetCount
        )
        AlarmScheduler.schedule(this, snoozeAlarm)
        finish()
    }

    private fun startRinging() {
        try {
            val uri = if (!ringtoneUri.isNullOrEmpty()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmRingActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                val vol = AppSettings.getAlarmVolume(this@AlarmRingActivity) / 100f
                setVolume(vol, vol)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmRingActivity, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        if (AppSettings.isVibrate(this)) {
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }
        }
    }

    private fun stopRinging() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        flashHelper?.stopFlashing()
        sensorManager?.unregisterListener(this)
    }

    private fun showOnLockScreenAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(forceStopReceiver)
        } catch (_: Exception) {
        }
        ttsHelper?.shutdown()
        super.onDestroy()
        stopRinging()
        // Không cancel notification ở đây nếu activity bị destroy ngoài ý muốn;
        // chỉ cancel khi dismiss/snooze thành công.
    }
}
