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
    private var mathSolvedCount = 0
    private var mathNeed = 1
    private var readIndex = 0
    private val readUsed = mutableSetOf<String>()
    private var currentSentence = ""
    private var tapCount = 0
    private val tapTimes = ArrayDeque<Long>()
    private var lastTapAt = 0L
    private var autoClickStrikes = 0
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
        val h = intent.getIntExtra("ALARM_HOUR", -1)
        val m = intent.getIntExtra("ALARM_MINUTE", -1)
        if (h in 0..23 && m in 0..59) {
            binding.tvRingTime.text = String.format("%02d:%02d", h, m)
        } else {
            val now = java.util.Calendar.getInstance()
            binding.tvRingTime.text = String.format(
                "%02d:%02d",
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE)
            )
        }
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
        AlarmNotificationHelper.showRingingNotification(
            context = this,
            alarmId = alarmId,
            label = label,
            allowDirectDismiss = allowDirectDismiss,
            hour = intent.getIntExtra("ALARM_HOUR", -1),
            minute = intent.getIntExtra("ALARM_MINUTE", -1),
            snoozeMinutes = snoozeMinutes,
            repeatMode = repeatMode,
            ringtoneUri = ringtoneUri,
            challengeType = challengeType,
            shakeTargetCount = shakeTargetCount,
            isStrict = isStrictAntiSnooze,
            voiceNote = voiceNote,
            useCrescendo = intent.getBooleanExtra("USE_CRESCENDO", true)
        )

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
    private fun hideAllChallenges() {
        binding.layoutMathChallenge.visibility = View.GONE
        binding.layoutShakeChallenge.visibility = View.GONE
        try { binding.layoutReadChallenge.visibility = View.GONE } catch (_: Exception) {}
        try { binding.layoutTapChallenge.visibility = View.GONE } catch (_: Exception) {}
    }

    private fun setupChallengeUi() {
        hideAllChallenges()
        when (challengeType) {
            Alarm.CHALLENGE_MATH -> {
                binding.layoutMathChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.VISIBLE
                mathNeed = 1
                mathSolvedCount = 0
                initMathChallenge()
            }
            Alarm.CHALLENGE_MATH10 -> {
                binding.layoutMathChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                mathNeed = 10
                mathSolvedCount = 0
                initMathChallenge()
            }
            Alarm.CHALLENGE_SHAKE -> {
                if (shakeTargetCount < 10) shakeTargetCount = 10
                binding.layoutShakeChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.VISIBLE
                initShakeChallenge()
            }
            Alarm.CHALLENGE_SHAKE100 -> {
                shakeTargetCount = 100
                binding.layoutShakeChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initShakeChallenge()
            }
            Alarm.CHALLENGE_READ -> {
                binding.layoutReadChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initReadChallenge()
            }
            Alarm.CHALLENGE_TAP200 -> {
                binding.layoutTapChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initTapChallenge()
            }
            Alarm.CHALLENGE_FACE -> {
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                faceChallengeLauncher.launch(
                    android.content.Intent(this, FaceChallengeActivity::class.java).apply {
                        putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE)
                    }
                )
            }
            Alarm.CHALLENGE_FACE_EXPR -> {
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                faceChallengeLauncher.launch(
                    android.content.Intent(this, FaceChallengeActivity::class.java).apply {
                        putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR)
                    }
                )
            }
            Alarm.CHALLENGE_BIOMETRIC -> {
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                BiometricHelper.authenticate(
                    this,
                    onSuccess = { dismissAlarm(AlarmRepository(this)) },
                    onFail = { msg ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        if (AppSettings.isFaceCaptureOnFail(this)) {
                            faceChallengeLauncher.launch(
                                android.content.Intent(this, FaceChallengeActivity::class.java)
                            )
                        }
                    }
                )
            }
            else -> {
                binding.btnDismiss.visibility = View.VISIBLE
            }
        }
    }

    private fun initMathChallenge() {
        nextMathQuestion()
        binding.btnSubmitMath.setOnClickListener {
            val userAnswer = binding.edtMathAnswer.text.toString().toIntOrNull()
            if (userAnswer != null && userAnswer == mathAnswer) {
                mathSolvedCount++
                if (mathSolvedCount >= mathNeed) {
                    dismissAlarm(AlarmRepository(this))
                } else {
                    Toast.makeText(this, "Đúng! Còn ${mathNeed - mathSolvedCount} bài", Toast.LENGTH_SHORT).show()
                    binding.edtMathAnswer.setText("")
                    nextMathQuestion()
                }
            } else {
                Toast.makeText(this, getString(R.string.wrong_answer), Toast.LENGTH_SHORT).show()
                binding.edtMathAnswer.setText("")
                // Sai → đổi câu mới (khó hơn)
                nextMathQuestion(harder = mathNeed >= 10)
            }
        }
    }

    private fun nextMathQuestion(harder: Boolean = mathNeed >= 10) {
        val (q, a) = if (harder) {
            when ((0..4).random()) {
                0 -> {
                    val a1 = (12..40).random(); val b1 = (8..25).random()
                    "${a1} + ${b1} = ?" to (a1 + b1)
                }
                1 -> {
                    val a1 = (20..60).random(); val b1 = (5..18).random()
                    "${a1} − ${b1} = ?" to (a1 - b1)
                }
                2 -> {
                    val a1 = (6..14).random(); val b1 = (3..9).random()
                    "${a1} × ${b1} = ?" to (a1 * b1)
                }
                3 -> {
                    val b1 = (2..9).random(); val a1 = b1 * (3..12).random()
                    "${a1} ÷ ${b1} = ?" to (a1 / b1)
                }
                else -> {
                    val a1 = (5..15).random(); val b1 = (5..15).random(); val c1 = (2..9).random()
                    "${a1} + ${b1} × ${c1} = ?" to (a1 + b1 * c1)
                }
            }
        } else {
            val a1 = (10..50).random(); val b1 = (1..20).random()
            getString(R.string.math_question, a1, b1) to (a1 + b1)
        }
        mathAnswer = a
        binding.tvMathQuestion.text = if (mathNeed > 1)
            "($mathSolvedCount/$mathNeed) $q"
        else q
    }

    private val readPool = listOf(
        "Bình minh trên sông Hồng rất đẹp",
        "Hãy dậy và bắt đầu ngày mới",
        "Uống nước và tập thể dục buổi sáng",
        "Thành công đến từ sự kiên trì",
        "Mở cửa sổ cho không khí trong lành",
        "Hôm nay tôi sẽ làm việc hiệu quả",
        "Giấc ngủ đủ giúp tinh thần sảng khoái",
        "Cà phê sáng và bản tin thời sự",
        "Đừng trì hoãn những việc quan trọng",
        "Nụ cười là ngôn ngữ của trái tim",
        "Mỗi ngày là một cơ hội mới",
        "Học hỏi không ngừng để tiến bộ",
        "Gia đình là nơi bình yên nhất",
        "Thời gian quý hơn vàng bạc",
        "Lắng nghe cơ thể khi cần nghỉ ngơi"
    )

    private fun initReadChallenge() {
        pickNewSentence()
        binding.btnSubmitRead.setOnClickListener {
            val typed = normalizeText(binding.edtReadAnswer.text?.toString().orEmpty())
            val expect = normalizeText(currentSentence)
            if (typed == expect || typed.contains(expect.take(12)) || expect.contains(typed.take(12))) {
                readIndex++
                if (readIndex >= 3) {
                    dismissAlarm(AlarmRepository(this))
                } else {
                    Toast.makeText(this, "Đúng! Câu tiếp theo", Toast.LENGTH_SHORT).show()
                    binding.edtReadAnswer.setText("")
                    pickNewSentence()
                }
            } else {
                Toast.makeText(this, "Sai — gõ lại đúng câu (có thể bỏ dấu)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickNewSentence() {
        val candidates = readPool.filter { it !in readUsed }
        val s = (if (candidates.isEmpty()) readPool else candidates).random()
        readUsed.add(s)
        currentSentence = s
        binding.tvReadSentence.text = s
        binding.tvReadProgress.text = "${readIndex + 1} / 3"
    }

    private fun normalizeText(s: String): String {
        val nfd = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
        return nfd.replace(Regex("\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\s]"), "")
            .replace(Regex("\s+"), " ")
    }

    private fun initTapChallenge() {
        tapCount = 0
        tapTimes.clear()
        autoClickStrikes = 0
        binding.tvTapProgress.text = "0 / 200"
        binding.btnTapChallenge.setOnClickListener { onHumanTap() }
        // Chặn long-press spam từ auto-click một số app
        binding.btnTapChallenge.setOnLongClickListener { true }
    }

    private fun onHumanTap() {
        val now = System.currentTimeMillis()
        val dt = now - lastTapAt
        // Quá nhanh (<70ms) → nghi auto-click
        if (lastTapAt > 0 && dt < 70) {
            autoClickStrikes++
            binding.tvTapHint.text = "Phát hiện bấm quá nhanh — có thể auto-click ($autoClickStrikes)"
            if (autoClickStrikes >= 8) {
                Toast.makeText(this, "Auto-click bị chặn! Bấm chậm hơn bằng tay.", Toast.LENGTH_LONG).show()
                // Phạt: trừ 15 lần
                tapCount = (tapCount - 15).coerceAtLeast(0)
                autoClickStrikes = 0
                binding.tvTapProgress.text = "$tapCount / 200"
            }
            return
        }
        lastTapAt = now
        tapTimes.addLast(now)
        while (tapTimes.size > 12) tapTimes.removeFirst()
        // Pattern quá đều (độ lệch chuẩn interval < 8ms) trên 10 lần → auto
        if (tapTimes.size >= 10) {
            val intervals = tapTimes.zipWithNext { a, b -> (b - a).toDouble() }
            val mean = intervals.average()
            val variance = intervals.map { (it - mean) * (it - mean) }.average()
            val std = kotlin.math.sqrt(variance)
            if (mean < 90 && std < 8) {
                autoClickStrikes++
                binding.tvTapHint.text = "Nhịp quá máy móc — dùng tay bấm ($autoClickStrikes)"
                if (autoClickStrikes >= 5) {
                    tapCount = (tapCount - 20).coerceAtLeast(0)
                    autoClickStrikes = 0
                    Toast.makeText(this, "Phát hiện auto-click — trừ 20 lần", Toast.LENGTH_LONG).show()
                }
                binding.tvTapProgress.text = "$tapCount / 200"
                return
            }
        }
        tapCount++
        binding.tvTapProgress.text = "$tapCount / 200"
        binding.tvTapHint.text = if (tapCount % 50 == 0) "Còn ${200 - tapCount} lần — tiếp tục!" else "Chạm nút bằng tay"
        if (tapCount >= 200) {
            dismissAlarm(AlarmRepository(this))
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
                isFocusable = true
                isFocusableInTouchMode = true
            }
            val dialog = MaterialAlertDialogBuilder(this)
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
                        if (AppSettings.isFaceCaptureOnFail(this)) {
                            QuickIntruderCapture.snap(this, this)
                        }
                    }
                }
                .setNegativeButton("Hủy", null)
                .create()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            dialog.setOnShowListener {
                input.requestFocus()
                input.postDelayed({
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
                    imm?.toggleSoftInput(
                        android.view.inputmethod.InputMethodManager.SHOW_FORCED,
                        android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY
                    )
                }, 120)
            }
            dialog.show()
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
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        // Samsung / khóa màn: yêu cầu bỏ keyguard để hiện activity
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val km = getSystemService(android.app.KeyguardManager::class.java)
                km?.requestDismissKeyguard(this, null)
            }
        } catch (_: Exception) {
        }
        // Đánh thức màn hình
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                "AlarmClock:RingScreen"
            )
            wl.acquire(10_000L)
        } catch (_: Exception) {
        }
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
