package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.view.MotionEvent
import androidx.core.content.ContextCompat
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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.alarmclock.databinding.ActivityAlarmRingBinding
import java.util.Calendar
import kotlin.math.sqrt

class AlarmRingActivity : AppCompatActivity(), SensorEventListener {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    private lateinit var binding: ActivityAlarmRingBinding
    private var mediaPlayer: MediaPlayer? = null
    private var prevInterruptionFilter: Int = -1
    private var dndEnabledByUs: Boolean = false
    private var vibrator: Vibrator? = null
    private var alarmId: Int = -1
    private var snoozeMinutes: Int = 5
    private var repeatMode: Int = Alarm.REPEAT_DAILY
    private var ringtoneUri: String? = null
    private var flashHelper: FlashHelper? = null

    // Thử thách báo thức
    private var challengeType: Int = Alarm.CHALLENGE_NONE
    /** Chuỗi thử thách khi chọn TẤT CẢ (khó / dễ) */
    private val allChainHard = listOf(
        Alarm.CHALLENGE_MATH10,
        Alarm.CHALLENGE_READ,
        Alarm.CHALLENGE_SHAKE100,
        Alarm.CHALLENGE_TAP200,
        Alarm.CHALLENGE_FACE_EXPR
    )
    /** TẤT CẢ dễ — ít bài, ít lắc/bấm, ít biểu cảm */
    private val allChainEasy = listOf(
        Alarm.CHALLENGE_MATH,      // sẽ set mathNeed=3
        Alarm.CHALLENGE_READ,      // 1 câu, 15s
        Alarm.CHALLENGE_SHAKE,     // 20 lần
        Alarm.CHALLENGE_TAP200,    // sẽ set tapNeed=30
        Alarm.CHALLENGE_FACE_EXPR  // 3 biểu cảm
    )
    private var allChain: List<Int> = allChainHard
    private var allStepIndex = 0
    private var runningAll = false
    private var allEasyMode = false
    private var tapNeed = 200
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
    private var readNeed = 3
    private var readWordIndex = 0
    private var readWords: List<String> = emptyList()
    private var readTimerLeft = 10
    private val ringTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var ringTimeoutRunnable: Runnable? = null
    private val readTimerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var readTimerRunnable: Runnable? = null
    private val readUsed = mutableSetOf<String>()
    private var currentSentence = ""
    private var tapCount = 0
    private val tapTimes = ArrayDeque<Long>()
    private var lastTapAt = 0L
    private var autoClickStrikes = 0
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var currentLabel: String = ""
    private val faceChallengeLauncher:
        androidx.activity.result.ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            android.util.Log.i("AlarmRing", "FaceChallenge result=${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                onChallengeStepComplete()
            } else {
                onFaceChallengeCanceled()
            }
        }

    
    private val qrLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) dismissAlarm(AlarmRepository(this))
        else android.widget.Toast.makeText(this, UiTranslate.tr("Chưa quét đúng mã QR"), android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- vân tay / khuôn mặt
    private var biometricBusy = false
    private var biometricRetried = false

    /** Xác minh bằng mã khóa / mẫu hình của điện thoại (dùng khi vân tay-khuôn mặt chưa dùng được). */
    private val credLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) dismissAlarm(AlarmRepository(this))
        else android.widget.Toast.makeText(this, UiTranslate.tr("Chưa xác minh — báo thức vẫn kêu"), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showBiometricButton() {
        binding.btnDismiss.visibility = View.VISIBLE
        binding.btnDismiss.text = UiTranslate.tr("Xác thực vân tay / khuôn mặt để tắt")
        binding.btnDismiss.setOnClickListener { startBiometric(false) }
    }

    private fun startBiometric(auto: Boolean) {
        if (biometricBusy || isFinishing) return
        if (!BiometricHelper.canAuthenticate(this)) {
            // Chưa đăng ký vân tay / khuôn mặt, hoặc vừa khởi động lại máy nên bị khóa
            confirmDeviceCredential()
            return
        }
        biometricBusy = true
        try {
            BiometricHelper.authenticate(
                this,
                onSuccess = {
                    biometricBusy = false
                    dismissAlarm(AlarmRepository(this))
                },
                onMismatch = {
                    Toast.makeText(this, UiTranslate.tr("Không khớp — thử lại"), Toast.LENGTH_SHORT).show()
                    if (AppSettings.isFaceCaptureOnFail(this)) {
                        try { QuickIntruderCapture.snap(this, this) } catch (_: Exception) {}
                    }
                },
                onError = { code, msg ->
                    biometricBusy = false
                    when (code) {
                        // 13 nút "Dùng mã khóa", 7/9 khóa do quét sai nhiều, 11 chưa đăng ký,
                        // 12/1 không có/ không dùng được cảm biến, 14 chưa có khóa màn hình, 15 cần cập nhật bảo mật
                        13, 7, 9, 11, 12, 1, 14, 15 -> confirmDeviceCredential()
                        // 5: bị hủy do màn hình chưa sẵn sàng → thử lại một lần
                        5 -> if (auto && !biometricRetried) {
                            biometricRetried = true
                            binding.root.postDelayed({ if (!isFinishing) startBiometric(false) }, 900)
                        }
                        // 10 người dùng đóng hộp, 3 hết giờ…: giữ nút để thử lại
                        else -> Toast.makeText(this, "$msg — bấm nút để thử lại", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            biometricBusy = false
            confirmDeviceCredential()
        }
    }

    private fun confirmDeviceCredential() {
        try {
            val km = getSystemService(android.app.KeyguardManager::class.java)
            if (km != null && km.isDeviceSecure) {
                @Suppress("DEPRECATION")
                val i = km.createConfirmDeviceCredentialIntent(
                    "Xác minh để tắt báo thức",
                    "Nhập mã khóa hoặc mẫu hình của điện thoại"
                )
                if (i != null) {
                    credLauncher.launch(i)
                    return
                }
            }
        } catch (_: Exception) {}
        // Máy không có khóa màn hình lẫn vân tay: không thể xác minh → cho tắt trực tiếp để không bị kẹt
        Toast.makeText(this, UiTranslate.tr("Máy chưa đặt khóa màn hình / vân tay — tắt trực tiếp"), Toast.LENGTH_LONG).show()
        binding.btnDismiss.visibility = View.VISIBLE
        binding.btnDismiss.text = UiTranslate.tr("Tắt báo thức")
        binding.btnDismiss.setOnClickListener { dismissAlarm(AlarmRepository(this)) }
    }

    private fun launchQrChallenge() {
        val token = try { AlarmRepository(this).getAlarms().find { it.id == alarmId }?.qrToken.orEmpty() } catch (_: Exception) { "" }
        val i = android.content.Intent(this, QrChallengeActivity::class.java)
            .putExtra(QrChallengeActivity.EXTRA_TOKEN, token)
            .putExtra("ALARM_ID", alarmId)
        qrLauncher.launch(i)
    }

private fun launchFaceChallenge(mode: Int = FaceChallengeActivity.MODE_EXPR) {
        try {
            FaceChallengeActivity.pendingResultOk = null
            val intent = android.content.Intent(this, FaceChallengeActivity::class.java).apply {
                putExtra(FaceChallengeActivity.EXTRA_MODE, mode)
                putExtra(
                    FaceChallengeActivity.EXTRA_EASY,
                    allEasyMode || challengeType == Alarm.CHALLENGE_FACE_EXPR
                )
            }
            startActivity(intent)
            Toast.makeText(this, UiTranslate.tr("Đang mở quét mặt… đưa mặt vào khung"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được quét mặt: ${e.message}", Toast.LENGTH_LONG).show()
            binding.btnDismiss.visibility = View.VISIBLE
            binding.btnDismiss.text = UiTranslate.tr("Lỗi mở quét — bấm thử lại")
            binding.btnDismiss.setOnClickListener { launchFaceChallenge(mode) }
        }
    }

    private fun launchFaceExpr() {
        launchFaceChallenge(FaceChallengeActivity.MODE_EXPR)
    }

    private fun onFaceChallengeCanceled() {
        val isExpr = challengeType == Alarm.CHALLENGE_FACE_EXPR ||
            (runningAll && allChain.getOrNull(allStepIndex) == Alarm.CHALLENGE_FACE_EXPR)
        Toast.makeText(
            this,
            if (isExpr) "Chưa xong biểu cảm — bấm nút đỏ thử lại"
            else "Chưa quét mặt — bấm nút đỏ thử lại",
            Toast.LENGTH_LONG
        ).show()
        try {
            binding.btnDismiss.visibility = View.VISIBLE
            binding.btnSnooze.visibility = View.GONE
            if (runningAll) {
                binding.btnDismiss.text =
                    "Thử lại (${allStepIndex + 1}/${allChain.size})"
            } else if (isExpr) {
                binding.btnDismiss.text = UiTranslate.tr("Bắt đầu: 10 biểu cảm dễ")
            } else {
                binding.btnDismiss.text = UiTranslate.tr("Bắt đầu quét mặt lại")
            }
            if (!isStrictAntiSnooze && !AppSettings.isAntiTroll(this) && !runningAll) {
                binding.btnSnooze.visibility = View.VISIBLE
            }
            val mode = if (isExpr) FaceChallengeActivity.MODE_EXPR else FaceChallengeActivity.MODE_FACE
            binding.btnDismiss.setOnClickListener { launchFaceChallenge(mode) }
        } catch (_: Exception) {}
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
        try { showOnLockScreenAndTurnScreenOn() } catch (_: Exception) {}

        try {
            binding = ActivityAlarmRingBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            android.util.Log.e("AlarmRing", "inflate failed", e)
            showFallbackRingUi()
            return
        }
        try {
            RingTheme.applyToRing(
                this, binding.root,
                listOf(binding.viewRingBlobTop, binding.viewRingBlobBottom),
                binding.tvRingTitle, binding.tvLabel
            )
        } catch (e: Exception) {
            android.util.Log.e("AlarmRing", "ring theme failed", e)
        }
        try {
            binding.root.setOnLongClickListener {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val km = getSystemService(android.app.KeyguardManager::class.java)
                        km?.requestDismissKeyguard(this, null)
                    }
                } catch (_: Exception) {}
                true
            }
        } catch (_: Exception) {}

        // Chế độ tập trung khi báo thức (DND + ẩn thanh hệ thống)
        try {
            if (AppSettings.isFocusModeOnAlarm(this)) enableFocusMode()
        } catch (_: Exception) {}

        alarmId = intent.getIntExtra("ALARM_ID", -1)
        val label = intent.getStringExtra("ALARM_LABEL") ?: getString(R.string.app_name)
        currentLabel = label
        snoozeMinutes = intent.getIntExtra("SNOOZE_MINUTES", 5)
        repeatMode = intent.getIntExtra("REPEAT_MODE", Alarm.REPEAT_DAILY)
        ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        challengeType = intent.getIntExtra("CHALLENGE_TYPE", Alarm.CHALLENGE_NONE)
        if (challengeType == 14 || challengeType == 15) challengeType = Alarm.CHALLENGE_NONE
        isStrictAntiSnooze = intent.getBooleanExtra("STRICT_ANTI_SNOOZE", false)
        voiceNote = intent.getStringExtra("VOICE_NOTE")
        try {
            if (!AppSettings.isPureAlarmOnly(this)) {
                ttsHelper = TtsHelper(this)
                voiceNote?.let { ttsHelper?.speakVoiceNote(it) }
            }
        } catch (_: Exception) {}
        shakeTargetCount = intent.getIntExtra("SHAKE_TARGET_COUNT", 10)

        binding.tvLabel.text = label
        try {
            val tf = resources.getFont(R.font.noto_sans_regular)
            val tfBold = resources.getFont(R.font.noto_sans_bold)
            binding.tvLabel.typeface = tf
            binding.tvRingTime.typeface = tfBold
            try { binding.tvMathQuestion.typeface = tfBold } catch (_: Exception) {}
            try { binding.btnMathA.typeface = tf } catch (_: Exception) {}
            try { binding.btnMathB.typeface = tf } catch (_: Exception) {}
            try { binding.btnMathC.typeface = tf } catch (_: Exception) {}
            try { binding.btnMathD.typeface = tf } catch (_: Exception) {}
            try { binding.btnDismiss.typeface = tfBold } catch (_: Exception) {}
            try { binding.btnSnooze.typeface = tf } catch (_: Exception) {}
        } catch (_: Exception) {}
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

        try {
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
        scheduleRingTimeout()
        enforceAntiTroll()
        setupChallengeUi()
        setupQuickDismiss()

        val repo = AlarmRepository(this)
        if (repo.isFlashEnabled()) {
            flashHelper = FlashHelper(this)
            flashHelper?.startFlashing()
        }

        // CHỈ gắn "Tắt" khi KHÔNG có thử thách.
        // Có FACE/EXPR/… thì setupChallengeUi() đã gắn đúng listener — không được ghi đè!
        if (challengeType == Alarm.CHALLENGE_NONE) {
            binding.btnDismiss.setOnClickListener {
                requestDismiss(repo)
            }
        }
        // Hiệu ứng Ripple Rings khi chạm nút (Tắt hoặc Bắt đầu quét)
        try {
            RippleRingsEffect.attach(binding.btnDismiss, 0xFF67E8F9.toInt())
        } catch (_: Exception) {}
        try { setupSwipeDismiss() } catch (e: Exception) {
            android.util.Log.e("AlarmRing", "swipe dismiss setup failed", e)
        }

        binding.btnSnooze.setOnClickListener {
            if (AppSettings.isAntiTroll(this) || isStrictAntiSnooze) {
                Toast.makeText(this, UiTranslate.tr("Chế độ chống troll: không được hoãn!"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            snoozeAlarm(label)
        }
        } catch (e: Exception) {
            android.util.Log.e("AlarmRing", "onCreate setup failed", e)
            try {
                binding.btnDismiss.setOnClickListener { finish() }
                binding.btnSnooze.setOnClickListener { finish() }
            } catch (_: Exception) {
                showFallbackRingUi()
            }
        }
    }

    /**
     * Kiểu "Vuốt để tắt" (chọn trong Cài đặt). Nút Tắt vẫn là nơi giữ toàn bộ logic
     * (thử thách, chống troll…) nên chỉ thu nó về kích thước 0 và để thanh vuốt "bấm" thay nó.
     */
    private fun setupSwipeDismiss() {
        if (AppSettings.getDismissStyle(this) != 1) return
        val sw = binding.swipeDismiss
        val btn = binding.btnDismiss
        val lp = btn.layoutParams
        lp.height = 0
        btn.layoutParams = lp
        sw.onComplete = { btn.performClick() }
        val dismissText = getString(R.string.dismiss)
        fun sync() {
            val vis = if (btn.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            if (sw.visibility != vis) sw.visibility = vis
            val t = btn.text?.toString().orEmpty()
            sw.setLabel(
                when {
                    t.isBlank() || t == dismissText -> Lang.t(this, "Vuốt để tắt", "Swipe to dismiss")
                    t.startsWith("Bắt đầu") -> "Vuốt để " + t.replaceFirstChar { it.lowercase() }
                    else -> t
                }
            )
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener { sync() }
        sync()
    }

    /**
     * Hiển thị đúng loại thử thách (Toán / Lắc máy / Không có) và ẩn nút Tắt
     * mặc định cho tới khi thử thách được hoàn thành.
     */
    // ---- chạm 2 ngón & cắm sạc
    private var chargeReceiver: BroadcastReceiver? = null

    private var quickGesture: android.view.GestureDetector? = null

    /** Cách tắt nhanh từ Cài đặt — chỉ áp dụng khi báo thức KHÔNG có thử thách. */
    private fun setupQuickDismiss() {
        if (challengeType != Alarm.CHALLENGE_NONE) return
        val repo = AlarmRepository(this)
        when (AppSettings.getQuickDismiss(this)) {
            QuickDismiss.SHAKE -> {
                quickShake = true
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
            }
            QuickDismiss.DOUBLE_TAP -> {
                quickGesture = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        requestDismiss(repo)
                        return true
                    }
                })
            }
            QuickDismiss.CHARGE -> {
                if (chargeReceiver == null) {
                    val r = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: android.content.Intent?) {
                            if (intent?.action == android.content.Intent.ACTION_POWER_CONNECTED) requestDismiss(repo)
                        }
                    }
                    chargeReceiver = r
                    try {
                        ContextCompat.registerReceiver(this, r,
                            IntentFilter(android.content.Intent.ACTION_POWER_CONNECTED),
                            ContextCompat.RECEIVER_EXPORTED)
                    } catch (_: Exception) {}
                }
            }
            QuickDismiss.FINGERPRINT -> {
                binding.root.postDelayed({
                    if (isFinishing || !BiometricHelper.canAuthenticate(this)) return@postDelayed
                    BiometricHelper.authenticate(
                        this,
                        title = Lang.t(this, "Vân tay để tắt báo thức", "Fingerprint to stop the alarm"),
                        onSuccess = { requestDismiss(repo) },
                        onError = { _, _ -> }
                    )
                }, 700)
            }
        }
    }

    private var quickShake = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        quickGesture?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun stopChargeChallenge() {
        chargeReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        chargeReceiver = null
    }

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
                tapNeed = 200
                binding.layoutTapChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initTapChallenge()
            }
            Alarm.CHALLENGE_QR -> {
                binding.btnSnooze.visibility = View.GONE
                binding.btnDismiss.visibility = View.VISIBLE
                binding.btnDismiss.text = UiTranslate.tr("Quét mã QR để tắt")
                binding.btnDismiss.setOnClickListener { launchQrChallenge() }
                if (!isStrictAntiSnooze && !AppSettings.isAntiTroll(this)) {
                    binding.btnSnooze.visibility = View.VISIBLE
                }
            }
            Alarm.CHALLENGE_FACE, Alarm.CHALLENGE_FACE_EXPR -> {
                // Không auto-mở camera (Huawei hay đen). Hiện nút bắt đầu.
                binding.btnSnooze.visibility = View.GONE
                binding.btnDismiss.visibility = View.VISIBLE
                binding.btnDismiss.text = if (challengeType == Alarm.CHALLENGE_FACE_EXPR)
                    "Bắt đầu: 10 biểu cảm dễ"
                else
                    "Bắt đầu quét mặt để tắt"
                binding.btnDismiss.setOnClickListener {
                    val mode = if (challengeType == Alarm.CHALLENGE_FACE_EXPR)
                        FaceChallengeActivity.MODE_EXPR
                    else
                        FaceChallengeActivity.MODE_FACE
                    launchFaceChallenge(mode)
                }
                if (!isStrictAntiSnooze && !AppSettings.isAntiTroll(this)) {
                    binding.btnSnooze.visibility = View.VISIBLE
                }
            }
            Alarm.CHALLENGE_ALL -> {
                runningAll = true
                allEasyMode = false
                allChain = allChainHard
                allStepIndex = 0
                isStrictAntiSnooze = true
                binding.btnSnooze.visibility = View.GONE
                binding.btnDismiss.visibility = View.GONE
                Toast.makeText(this, "Thử thách TẤT CẢ (khó) — bước 1/${allChain.size}", Toast.LENGTH_LONG).show()
                startAllChainStep()
            }
            Alarm.CHALLENGE_ALL_EASY -> {
                runningAll = true
                allEasyMode = true
                allChain = allChainEasy
                allStepIndex = 0
                isStrictAntiSnooze = false
                binding.btnSnooze.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                Toast.makeText(this, "TẤT CẢ dễ — không mất ngủ · bước 1/${allChain.size}", Toast.LENGTH_LONG).show()
                startAllChainStep()
            }
            Alarm.CHALLENGE_BIOMETRIC -> {
                binding.btnSnooze.visibility = View.GONE
                showBiometricButton()
                // Chờ màn hình báo thức sáng và có focus rồi mới mở hộp vân tay / khuôn mặt
                binding.root.postDelayed({
                    if (!isFinishing && !biometricBusy) startBiometric(true)
                }, 700)
            }
            else -> {
                binding.btnDismiss.visibility = View.VISIBLE
                if (!isStrictAntiSnooze) {
                    binding.btnSnooze.visibility = View.VISIBLE
                }
            }
        }
        // Đảm bảo luôn có cách tắt nếu không có challenge UI
        if (challengeType == Alarm.CHALLENGE_NONE) {
            binding.btnDismiss.visibility = View.VISIBLE
        }
    }

    private fun initMathChallenge() {
        binding.btnSnooze.visibility = View.GONE
        binding.btnDismiss.visibility = View.GONE
        nextMathQuestion()
        val onPick: (Int) -> Unit = { chosen ->
            if (chosen == mathAnswer) {
                mathSolvedCount++
                if (mathSolvedCount >= mathNeed) {
                    onChallengeStepComplete()
                } else {
                    Toast.makeText(this, "Đúng! Còn ${mathNeed - mathSolvedCount} bài", Toast.LENGTH_SHORT).show()
                    nextMathQuestion()
                }
            } else {
                Toast.makeText(this, getString(R.string.wrong_answer), Toast.LENGTH_SHORT).show()
                nextMathQuestion(harder = mathNeed >= 10)
            }
        }
        try {
            binding.btnMathA.setOnClickListener { onPick(mathChoices.getOrElse(0) { 0 }) }
            binding.btnMathB.setOnClickListener { onPick(mathChoices.getOrElse(1) { 0 }) }
            binding.btnMathC.setOnClickListener { onPick(mathChoices.getOrElse(2) { 0 }) }
            binding.btnMathD.setOnClickListener { onPick(mathChoices.getOrElse(3) { 0 }) }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi nút đáp án: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** 4 đáp án A–D (đã xáo), đúng nằm trong list */
    private var mathChoices: List<Int> = emptyList()

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
        // 3 đáp án nhiễu gần đúng + đáp án đúng, xáo trộn
        val wrong = mutableSetOf<Int>()
        var guard = 0
        while (wrong.size < 3 && guard < 40) {
            guard++
            val delta = listOf(-12, -8, -5, -3, -2, -1, 1, 2, 3, 5, 7, 9, 11, 15).random()
            val w = a + delta
            if (w != a) wrong.add(w)
        }
        while (wrong.size < 3) wrong.add(a + wrong.size + 17)
        mathChoices = (wrong.toList() + a).shuffled()
        val labels = listOf("A", "B", "C", "D")
        binding.tvMathQuestion.text = if (mathNeed > 1)
            "(${mathSolvedCount + 1}/$mathNeed) $q"
        else q
        try {
            binding.btnMathA.text = "A. ${mathChoices[0]}"
            binding.btnMathB.text = "B. ${mathChoices[1]}"
            binding.btnMathC.text = "C. ${mathChoices[2]}"
            binding.btnMathD.text = "D. ${mathChoices[3]}"
        } catch (_: Exception) {}
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
        binding.btnSnooze.visibility = View.GONE
        binding.btnDismiss.visibility = View.GONE
        readIndex = 0
        readNeed = if (allEasyMode) 1 else 3
        pickNewSentence()
    }

    private fun stopReadTimer() {
        readTimerRunnable?.let { readTimerHandler.removeCallbacks(it) }
        readTimerRunnable = null
    }

    private fun startReadTimer() {
        stopReadTimer()
        readTimerLeft = if (allEasyMode) 15 else 10
        try { binding.tvReadTimer.text = "⏱ $readTimerLeft giây" } catch (_: Exception) {}
        val tick = object : Runnable {
            override fun run() {
                if (isFinishing) return
                readTimerLeft--
                try {
                    binding.tvReadTimer.text = if (readTimerLeft > 0)
                        "⏱ $readTimerLeft giây"
                    else
                        "⏱ Hết giờ!"
                    binding.tvReadTimer.setTextColor(
                        if (readTimerLeft <= 3) 0xFFEF4444.toInt() else 0xFFFCA5A5.toInt()
                    )
                } catch (_: Exception) {}
                if (readTimerLeft <= 0) {
                    Toast.makeText(this@AlarmRingActivity, UiTranslate.tr("Hết 10 giây — câu mới!"), Toast.LENGTH_SHORT).show()
                    // Không cộng readIndex — làm lại câu khác
                    pickNewSentence()
                    return
                }
                readTimerHandler.postDelayed(this, 1000L)
            }
        }
        readTimerRunnable = tick
        readTimerHandler.postDelayed(tick, 1000L)
    }

    private fun pickNewSentence() {
        stopReadTimer()
        val candidates = readPool.filter { it !in readUsed }
        val s = (if (candidates.isEmpty()) {
            readUsed.clear()
            readPool
        } else candidates).random()
        readUsed.add(s)
        currentSentence = s
        readWords = s.split(" ").map { it.trim() }.filter { it.isNotBlank() }
        readWordIndex = 0
        try {
            binding.tvReadSentence.text = s
            binding.tvReadProgress.text = "${readIndex + 1} / $readNeed"
            binding.tvReadBuilt.text = "…"
        } catch (_: Exception) {}
        buildReadWordChips()
        startReadTimer()
    }

    private fun buildReadWordChips() {
        try {
            val group = binding.chipGroupReadWords
            group.removeAllViews()
            val shuffled = readWords.mapIndexed { i, w -> i to w }.shuffled()
            for ((origIndex, word) in shuffled) {
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = word
                    isCheckable = false
                    isClickable = true
                    isFocusable = true
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(0xFF4338CA.toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 15f
                    setOnClickListener { onReadWordTapped(origIndex, word, this) }
                }
                group.addView(chip)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi chip từ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun onReadWordTapped(origIndex: Int, word: String, chip: com.google.android.material.chip.Chip) {
        if (origIndex == readWordIndex) {
            // Đúng thứ tự
            chip.isEnabled = false
            chip.alpha = 0.35f
            readWordIndex++
            val built = readWords.take(readWordIndex).joinToString(" ")
            try { binding.tvReadBuilt.text = built } catch (_: Exception) {}
            if (readWordIndex >= readWords.size) {
                stopReadTimer()
                readIndex++
                if (readIndex >= readNeed) {
                    Toast.makeText(this, UiTranslate.tr("Xong chọn từ!"), Toast.LENGTH_SHORT).show()
                    onChallengeStepComplete()
                } else {
                    Toast.makeText(this, "Đúng! Câu tiếp (${readIndex}/$readNeed)", Toast.LENGTH_SHORT).show()
                    pickNewSentence()
                }
            }
        } else {
            // Sai — rung nhẹ / toast
            Toast.makeText(this, UiTranslate.tr("Sai thứ tự — chọn từ đúng tiếp theo"), Toast.LENGTH_SHORT).show()
            chip.animate().translationX(12f).setDuration(40)
                .withEndAction { chip.animate().translationX(-12f).setDuration(40)
                    .withEndAction { chip.animate().translationX(0f).setDuration(40).start() }
                    .start() }
                .start()
        }
    }

    private fun normalizeText(s: String): String {
        val nfd = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
        val noMarks = buildString {
            for (c in nfd) {
                val t = Character.getType(c)
                if (t != Character.NON_SPACING_MARK.toInt() && t != Character.COMBINING_SPACING_MARK.toInt()) {
                    append(c)
                }
            }
        }
        return noMarks.replace(Regex("[^a-z0-9 ]"), "").replace(Regex(" +"), " ").trim()
    }

    private fun initTapChallenge() {
        tapCount = 0
        tapTimes.clear()
        autoClickStrikes = 0
        binding.tvTapProgress.text = "0 / $tapNeed"
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
                Toast.makeText(this, UiTranslate.tr("Auto-click bị chặn! Bấm chậm hơn bằng tay."), Toast.LENGTH_LONG).show()
                // Phạt: trừ 15 lần
                tapCount = (tapCount - 15).coerceAtLeast(0)
                autoClickStrikes = 0
                binding.tvTapProgress.text = "$tapCount / $tapNeed"
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
                    Toast.makeText(this, UiTranslate.tr("Phát hiện auto-click — trừ 20 lần"), Toast.LENGTH_LONG).show()
                }
                binding.tvTapProgress.text = "$tapCount / $tapNeed"
                return
            }
        }
        tapCount++
        binding.tvTapProgress.text = "$tapCount / $tapNeed"
        binding.tvTapHint.text = if (tapCount % 10 == 0) "Còn ${tapNeed - tapCount} lần — tiếp tục!" else "Chạm nút bằng tay"
        if (tapCount >= tapNeed) {
            onChallengeStepComplete()
        }
    }

    private fun initShakeChallenge() {
        currentShakeCount = 0
        lastShakeTime = 0L
        binding.tvShakeProgress.text = getString(R.string.shake_progress, currentShakeCount, shakeTargetCount)
        Toast.makeText(this, UiTranslate.tr("Lắc mạnh điện thoại để đếm!"), Toast.LENGTH_SHORT).show()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        // Ưu tiên LINEAR (không trọng lực) nếu có — nhạy hơn trên một số máy Huawei
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.no_accelerometer), Toast.LENGTH_LONG).show()
            binding.layoutShakeChallenge.visibility = View.GONE
            binding.btnDismiss.visibility = View.VISIBLE
            binding.btnDismiss.text = UiTranslate.tr("Tắt (không có cảm biến)")
            binding.btnDismiss.setOnClickListener { onChallengeStepComplete() }
            return
        }

        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)

        // Fallback: nếu 12 giây vẫn 0 lần → hiện nút "Tôi đang lắc" cộng từng lần bấm
        binding.btnSnooze.visibility = View.GONE
        binding.btnDismiss.visibility = View.GONE
        binding.root.postDelayed({
            if (isFinishing) return@postDelayed
            if (currentShakeCount == 0 &&
                (challengeType == Alarm.CHALLENGE_SHAKE || challengeType == Alarm.CHALLENGE_SHAKE100)
            ) {
                binding.btnDismiss.visibility = View.VISIBLE
                binding.btnDismiss.text = UiTranslate.tr("Cảm biến chậm — bấm để +1 lắc")
                binding.btnDismiss.setOnClickListener {
                    currentShakeCount++
                    binding.tvShakeProgress.text =
                        getString(R.string.shake_progress, currentShakeCount, shakeTargetCount)
                    if (currentShakeCount >= shakeTargetCount) onChallengeStepComplete()
                }
                Toast.makeText(this, UiTranslate.tr("Cảm biến không nhận — dùng nút bên dưới"), Toast.LENGTH_LONG).show()
            }
        }, 12_000L)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // CHALLENGE_SHAKE và CHALLENGE_SHAKE100 (chuỗi TẤT CẢ) đều cần đếm lắc
        val isShake = challengeType == Alarm.CHALLENGE_SHAKE ||
            challengeType == Alarm.CHALLENGE_SHAKE100 || quickShake
        if (!isShake || event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val type = event.sensor?.type ?: return

        val intensity = if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
            // m/s² không trọng lực — lắc mạnh ~8+
            sqrt((x * x + y * y + z * z).toDouble())
        } else {
            // gia tốc / g
            sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        }
        val threshold = if (type == Sensor.TYPE_LINEAR_ACCELERATION) 4.5 else 1.25
        val currentTime = System.currentTimeMillis()

        if (intensity > threshold && currentTime - lastShakeTime > 120) {
            lastShakeTime = currentTime
            currentShakeCount++
            runOnUiThread {
                binding.tvShakeProgress.text =
                    getString(R.string.shake_progress, currentShakeCount, shakeTargetCount)
            }
            if (quickShake) {
                runOnUiThread { requestDismiss(AlarmRepository(this)) }
                return
            }
            if (currentShakeCount >= shakeTargetCount) {
                runOnUiThread { onChallengeStepComplete() }
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
        Toast.makeText(this, UiTranslate.tr("🛡️ Chống troll: cần mã PIN / thử thách để tắt"), Toast.LENGTH_LONG).show()
    }

    private fun requestDismiss(repo: AlarmRepository) {
        try { RippleRingsEffect.stop(this) } catch (_: Exception) {}
        if (challengeType == Alarm.CHALLENGE_BIOMETRIC) {
            startBiometric(false)
            return
        }
        // Có thử thách → không tắt trực tiếp (kể cả khi nút đỏ đang hiện = "Bắt đầu quét…")
        if (challengeType != Alarm.CHALLENGE_NONE) {
            Toast.makeText(this, UiTranslate.tr("Hãy hoàn thành thử thách trước!"), Toast.LENGTH_SHORT).show()
            return
        }
        if (AppSettings.isAntiTroll(this) && AppSettings.hasAntiTrollPin(this)) {
            showPinPad(repo)
        } else {
            dismissAlarm(repo)
        }
    }

    /**
     * Bàn phím số ngay trong hộp thoại: không phụ thuộc bàn phím hệ thống nên trên màn khóa
     * lúc nào cũng bấm được. Nhập đủ số PIN thì tự kiểm tra.
     */
    private fun showPinPad(repo: AlarmRepository) {
        val d = resources.displayMetrics.density
        val len = AppSettings.getAntiTrollPin(this).length.coerceIn(4, 12)
        val entered = StringBuilder()
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val tv = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        val textColor = if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, tv.resourceId) else tv.data

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (14 * d).toInt())
        }
        val dots = ArrayList<android.view.View>()
        for (i in 0 until len) {
            val v = android.view.View(this)
            dots.add(v)
            dotsRow.addView(v, LinearLayout.LayoutParams((14 * d).toInt(), (14 * d).toInt()).apply {
                marginStart = (6 * d).toInt()
                marginEnd = (6 * d).toInt()
            })
        }
        fun refreshDots() {
            for (i in dots.indices) {
                dots[i].background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    if (i < entered.length) setColor(0xFF1A73E8.toInt())
                    else setStroke((2 * d).toInt(), 0x66808080)
                }
            }
        }
        refreshDots()

        fun submit() {
            val pin = entered.toString()
            if (AppSettings.checkAntiTrollPin(this, pin)) {
                dialog?.dismiss()
                dismissAlarm(repo)
            } else {
                Toast.makeText(this, UiTranslate.tr("Sai PIN! Báo thức vẫn kêu."), Toast.LENGTH_LONG).show()
                entered.clear()
                refreshDots()
                dotsRow.animate().translationX(18f * d).setDuration(60).withEndAction {
                    dotsRow.animate().translationX(-14f * d).setDuration(60).withEndAction {
                        dotsRow.animate().translationX(0f).setDuration(60).start()
                    }.start()
                }.start()
                if (AppSettings.isFaceCaptureOnFail(this)) {
                    QuickIntruderCapture.snap(this, this)
                }
            }
        }

        fun key(label: String, onTap: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(0x1F808080)
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onTap()
            }
        }
        fun digit(n: Int) = key(n.toString()) {
            if (entered.length < len) {
                entered.append(n)
                refreshDots()
                if (entered.length == len) submit()
            }
        }
        val back = key("⌫") {
            if (entered.isNotEmpty()) {
                entered.deleteCharAt(entered.length - 1)
                refreshDots()
            }
        }
        val ok = key("✓") { if (entered.length >= 4) submit() }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf(digit(1), digit(2), digit(3)),
            listOf(digit(4), digit(5), digit(6)),
            listOf(digit(7), digit(8), digit(9)),
            listOf(back, digit(0), ok)
        )
        for (r in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                row.addView(k, LinearLayout.LayoutParams(0, (60 * d).toInt(), 1f).apply {
                    setMargins((5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt(), (5 * d).toInt())
                })
            }
            grid.addView(row)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (4 * d).toInt(), (20 * d).toInt(), 0)
            addView(dotsRow)
            addView(grid)
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(UiTranslate.tr("Xác minh tắt báo thức"))
            .setMessage(UiTranslate.tr("Nhập PIN để tắt — chống người khác troll"))
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(UiTranslate.tr("Hủy"), null)
            .create()
        dialog?.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (AppSettings.isAntiTroll(this) || isStrictAntiSnooze || challengeType != Alarm.CHALLENGE_NONE) {
            Toast.makeText(this, UiTranslate.tr("Không thể thoát — hãy tắt đúng cách!"), Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }


    /** Chạy một bước trong chuỗi TẤT CẢ */
    private fun startAllChainStep() {
        if (allStepIndex >= allChain.size) {
            dismissAlarm(AlarmRepository(this))
            return
        }
        val step = allChain[allStepIndex]
        Toast.makeText(
            this,
            "Bước ${allStepIndex + 1}/${allChain.size}: ${Alarm.challengeLabel(step)}",
            Toast.LENGTH_SHORT
        ).show()
        // Tạm gán challengeType = bước hiện tại để UI/init dùng đúng
        challengeType = step
        hideAllChallenges()
        when (step) {
            Alarm.CHALLENGE_MATH10 -> {
                binding.layoutMathChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                mathNeed = 10
                mathSolvedCount = 0
                initMathChallenge()
            }
            Alarm.CHALLENGE_MATH -> {
                binding.layoutMathChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                mathNeed = if (allEasyMode) 3 else 1
                mathSolvedCount = 0
                initMathChallenge()
            }
            Alarm.CHALLENGE_READ -> {
                binding.layoutReadChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initReadChallenge()
            }
            Alarm.CHALLENGE_SHAKE100 -> {
                shakeTargetCount = 100
                binding.layoutShakeChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initShakeChallenge()
            }
            Alarm.CHALLENGE_SHAKE -> {
                shakeTargetCount = if (allEasyMode) 20 else 10
                binding.layoutShakeChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initShakeChallenge()
            }
            Alarm.CHALLENGE_TAP200 -> {
                tapNeed = if (allEasyMode) 30 else 200
                binding.layoutTapChallenge.visibility = View.VISIBLE
                binding.btnDismiss.visibility = View.GONE
                binding.btnSnooze.visibility = View.GONE
                initTapChallenge()
            }
            Alarm.CHALLENGE_FACE_EXPR -> {
                binding.btnDismiss.visibility = View.VISIBLE
                binding.btnSnooze.visibility = View.GONE
                binding.btnDismiss.text =
                    if (allEasyMode) "Bắt đầu 3 biểu cảm dễ (${allStepIndex + 1}/${allChain.size})"
                    else "Bắt đầu biểu cảm (${allStepIndex + 1}/${allChain.size})"
                binding.btnDismiss.setOnClickListener { launchFaceExpr() }
            }
            else -> onChallengeStepComplete()
        }
    }

    /** Xong 1 bước thử thách (đơn hoặc trong chuỗi TẤT CẢ) */
    private fun onChallengeStepComplete() {
        if (runningAll) {
            allStepIndex++
            if (allStepIndex >= allChain.size) {
                Toast.makeText(this, UiTranslate.tr("Hoàn thành TẤT CẢ thử thách!"), Toast.LENGTH_LONG).show()
                runningAll = false
                challengeType = Alarm.CHALLENGE_ALL
                dismissAlarm(AlarmRepository(this))
            } else {
                Toast.makeText(this, "Xong bước ${allStepIndex}/${allChain.size} — tiếp tục!", Toast.LENGTH_SHORT).show()
                startAllChainStep()
            }
        } else {
            dismissAlarm(AlarmRepository(this))
        }
    }

    private fun scheduleRingTimeout() {
        ringTimeoutRunnable?.let { ringTimeoutHandler.removeCallbacks(it) }
        val cap = AppSettings.getRingDurationMinutes(this).coerceIn(1, 30) * 60_000L
        val run = Runnable { autoStopAfterDuration() }
        ringTimeoutRunnable = run
        ringTimeoutHandler.postDelayed(run, cap)
    }

    /** Hết thời lượng đổ chuông: tắt tiếng, không đọc Gemini, không kêu thêm. */
    private fun autoStopAfterDuration() {
        RingGuard.markDismissed(this, alarmId)
        try { AlarmNotificationHelper.clearRingingState(this) } catch (_: Exception) {}
        try { restoreFocusMode() } catch (_: Exception) {}
        try {
            AlarmHistory.add(
                this,
                intent.getStringExtra("ALARM_LABEL") ?: "",
                intent.getIntExtra("ALARM_HOUR", 0),
                intent.getIntExtra("ALARM_MINUTE", 0),
                "timeout"
            )
        } catch (_: Exception) {}
        try { RippleRingsEffect.stop(this) } catch (_: Exception) {}
        stopRinging()
        AlarmNotificationHelper.cancelRinging(this)
        try { AlarmRingService.stop(this) } catch (_: Exception) {}
        try { TonePlayer.stop() } catch (_: Exception) {}
        try {
            val repo = AlarmRepository(this)
            val alarms = repo.getAlarms().toMutableList()
            val alarm = alarms.find { it.id == alarmId }
            if (alarm != null) {
                if (repeatMode == Alarm.REPEAT_ONCE) {
                    alarm.isEnabled = false
                    repo.saveAlarms(alarms)
                    AlarmScheduler.cancel(this, alarmId)
                } else {
                    AlarmScheduler.schedule(this, alarm)
                }
            }
        } catch (_: Exception) {}
        finish()
    }

    private fun dismissAlarm(repo: AlarmRepository) {
        RingGuard.markDismissed(this, alarmId)
        try { AlarmNotificationHelper.clearRingingState(this) } catch (_: Exception) {}
        try { restoreFocusMode() } catch (_: Exception) {}
        try {
            AlarmHistory.add(this, intent.getStringExtra("ALARM_LABEL") ?: "",
                intent.getIntExtra("ALARM_HOUR", 0),
                intent.getIntExtra("ALARM_MINUTE", 0), "dismiss")
        } catch (_: Exception) {}
        try { RippleRingsEffect.stop(this) } catch (_: Exception) {}
        stopRinging()
        AlarmNotificationHelper.cancelRinging(this)
        try { AlarmRingService.stop(this) } catch (_: Exception) {}
        try { TonePlayer.stop() } catch (_: Exception) {}
        try { MorningBriefing.speakAfterDismiss(this, alarmId) } catch (_: Exception) {}
        val alarms = repo.getAlarms().toMutableList()
        val alarm = alarms.find { it.id == alarmId }
        if (alarm != null) {
            if (repeatMode == Alarm.REPEAT_ONCE) {
                // 1 lần → tắt hẳn, không lên lịch lại (tiết kiệm pin)
                alarm.isEnabled = false
                repo.saveAlarms(alarms)
                AlarmScheduler.cancel(this, alarmId)
            } else {
                // Hàng ngày / T2–T6 → lên lịch lần kế (scheduler bỏ qua T7 CN)
                AlarmScheduler.schedule(this, alarm)
            }
        }
        finish()
    }

    private fun snoozeAlarm(label: String) {
        RingGuard.markDismissed(this, alarmId)
        try { AlarmNotificationHelper.clearRingingState(this) } catch (_: Exception) {}
        try { restoreFocusMode() } catch (_: Exception) {}
        try {
            AlarmHistory.add(this, label,
                intent.getIntExtra("ALARM_HOUR", 0),
                intent.getIntExtra("ALARM_MINUTE", 0), "snooze")
        } catch (_: Exception) {}
        stopRinging()
        AlarmNotificationHelper.cancelRinging(this)
        try { AlarmRingService.stop(this) } catch (_: Exception) {}
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


    /** URI nhạc: resource app (êm) hoặc URI hệ thống. Mặc định soft_chime. */
    private fun resolveAlarmUri(uriStr: String?): android.net.Uri {
        if (!uriStr.isNullOrEmpty()) {
            when {
                uriStr == "app:soft_chime" || uriStr.endsWith("/soft_chime") ->
                    return android.net.Uri.parse("android.resource://${packageName}/${R.raw.soft_chime}")
                uriStr == "app:soft_bell" || uriStr.endsWith("/soft_bell") ->
                    return android.net.Uri.parse("android.resource://${packageName}/${R.raw.soft_bell}")
                else -> return android.net.Uri.parse(uriStr)
            }
        }
        return android.net.Uri.parse("android.resource://${packageName}/${R.raw.soft_chime}")
    }


    /** Bật chế độ tập trung (DND) + ẩn thanh hệ thống — khó vuốt thoát khi khoá màn hình */
    private fun enableFocusMode() {
        try {
            // Immersive sticky — ẩn nav bar / status
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.let { c ->
                    c.hide(android.view.WindowInsets.Type.navigationBars() or android.view.WindowInsets.Type.statusBars())
                    c.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } catch (_: Exception) {}

        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (nm.isNotificationPolicyAccessGranted) {
                    prevInterruptionFilter = nm.currentInterruptionFilter
                    nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    dndEnabledByUs = true
                }
            }
        } catch (_: Exception) {}
    }

    private fun restoreFocusMode() {
        try {
            if (dndEnabledByUs && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (nm.isNotificationPolicyAccessGranted && prevInterruptionFilter >= 0) {
                    nm.setInterruptionFilter(prevInterruptionFilter)
                }
                dndEnabledByUs = false
            }
        } catch (_: Exception) {}
    }

    private fun startRinging() {
        // Nhạc chỉ phát từ AlarmRingService (tránh 2 chuông).
        try {
            if (AppSettings.isVibrate(this)) {
                vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180, 120, 420, 280), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 400), 0)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopRinging() {
        try { TonePlayer.stop() } catch (_: Exception) {}
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        flashHelper?.stopFlashing()
        sensorManager?.unregisterListener(this)
    }


    private fun showFallbackRingUi() {
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF0D1B4A.toInt())
            setPadding(48, 48, 48, 48)
        }
        val time = android.widget.TextView(this).apply {
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            textSize = 56f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
        }
        val msg = android.widget.TextView(this).apply {
            text = intent.getStringExtra("ALARM_LABEL") ?: "Báo thức"
            textSize = 18f
            setTextColor(0xFFA5B4FC.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 48)
        }
        val dismiss = com.google.android.material.button.MaterialButton(this).apply {
            text = UiTranslate.tr("Tắt")
            setOnClickListener {
                try { AlarmNotificationHelper.cancelRinging(this@AlarmRingActivity) } catch (_: Exception) {}
                try { stopService(android.content.Intent(this@AlarmRingActivity, AlarmRingService::class.java)) } catch (_: Exception) {}
                finish()
            }
        }
        val snooze = com.google.android.material.button.MaterialButton(this).apply {
            text = UiTranslate.tr("Báo lại 5 phút")
            setOnClickListener {
                try { snoozeAlarm(intent.getStringExtra("ALARM_LABEL") ?: "Báo thức") } catch (_: Exception) { finish() }
            }
        }
        root.addView(time)
        root.addView(msg)
        root.addView(dismiss)
        root.addView(snooze)
        setContentView(root)
        try { startRinging() } catch (_: Exception) {}
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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
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

    override fun onResume() {
        FaceChallengeActivity.consumePendingResult()?.let { ok ->
            android.util.Log.i("AlarmRing", "Face pending result ok=$ok")
            if (ok) onChallengeStepComplete() else onFaceChallengeCanceled()
        }

        super.onResume()
        // Đăng ký lại cảm biến lắc nếu đang ở thử thách lắc
        if (challengeType == Alarm.CHALLENGE_SHAKE || challengeType == Alarm.CHALLENGE_SHAKE100) {
            val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm != null && acc != null) {
                sensorManager = sm
                accelerometer = acc
                sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        try { stopChargeChallenge() } catch (_: Exception) {}
        try { restoreFocusMode() } catch (_: Exception) {}
        try { RippleRingsEffect.stop(this) } catch (_: Exception) {}
        stopReadTimer()
        ringTimeoutRunnable?.let { ringTimeoutHandler.removeCallbacks(it) }

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
