package com.example.alarmclock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service phát chuông khi báo thức — Samsung A12/OEM thường chặn Activity
 * khi khóa màn, nhưng service + notification HIGH vẫn kêu được.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafe()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val label = intent?.getStringExtra("ALARM_LABEL") ?: "Báo thức"
        val ringtoneUri = intent?.getStringExtra("RINGTONE_URI")
        val hour = intent?.getIntExtra("ALARM_HOUR", -1) ?: -1
        val minute = intent?.getIntExtra("ALARM_MINUTE", -1) ?: -1
        val snooze = intent?.getIntExtra("SNOOZE_MINUTES", 5) ?: 5
        val repeat = intent?.getIntExtra("REPEAT_MODE", Alarm.REPEAT_DAILY) ?: Alarm.REPEAT_DAILY
        val challenge = intent?.getIntExtra("CHALLENGE_TYPE", Alarm.CHALLENGE_NONE) ?: Alarm.CHALLENGE_NONE
        val shake = intent?.getIntExtra("SHAKE_TARGET_COUNT", 10) ?: 10
        val strict = intent?.getBooleanExtra("STRICT_ANTI_SNOOZE", false) ?: false
        val voice = intent?.getStringExtra("VOICE_NOTE")
        val crescendo = intent?.getBooleanExtra("USE_CRESCENDO", true) ?: true

        acquireWake()
        val notif = buildNotification(alarmId, label, hour, minute, snooze, repeat, challenge, shake, strict, voice, crescendo, ringtoneUri)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // 1073741824 = FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(AlarmNotificationHelper.NOTIF_ID_RINGING, notif, 1073741824)
            } else {
                startForeground(AlarmNotificationHelper.NOTIF_ID_RINGING, notif)
            }
        } catch (_: Exception) {
            try { startForeground(AlarmNotificationHelper.NOTIF_ID_RINGING, notif) } catch (_: Exception) {}
        }

        startSound(ringtoneUri)
        // Thử mở màn reo
        try {
            startActivity(
                Intent(this, AlarmRingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    putExtra("ALARM_ID", alarmId)
                    putExtra("ALARM_LABEL", label)
                    putExtra("ALARM_HOUR", hour)
                    putExtra("ALARM_MINUTE", minute)
                    putExtra("SNOOZE_MINUTES", snooze)
                    putExtra("REPEAT_MODE", repeat)
                    putExtra("RINGTONE_URI", ringtoneUri)
                    putExtra("CHALLENGE_TYPE", challenge)
                    putExtra("SHAKE_TARGET_COUNT", shake)
                    putExtra("STRICT_ANTI_SNOOZE", strict)
                    putExtra("VOICE_NOTE", voice)
                    putExtra("USE_CRESCENDO", crescendo)
                }
            )
        } catch (_: Exception) {}

        return START_STICKY
    }

    private fun buildNotification(
        alarmId: Int, label: String, hour: Int, minute: Int,
        snooze: Int, repeat: Int, challenge: Int, shake: Int,
        strict: Boolean, voice: String?, crescendo: Boolean, ringtoneUri: String?
    ): Notification {
        AlarmNotificationHelper.ensureChannels(this)
        val open = Intent(this, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_HOUR", hour)
            putExtra("ALARM_MINUTE", minute)
            putExtra("SNOOZE_MINUTES", snooze)
            putExtra("REPEAT_MODE", repeat)
            putExtra("CHALLENGE_TYPE", challenge)
            putExtra("SHAKE_TARGET_COUNT", shake)
            putExtra("STRICT_ANTI_SNOOZE", strict)
            putExtra("VOICE_NOTE", voice)
            putExtra("USE_CRESCENDO", crescendo)
            putExtra("RINGTONE_URI", ringtoneUri)
            action = AlarmNotificationHelper.ACTION_OPEN_RING + "_$alarmId"
        }
        val openPi = PendingIntent.getActivity(
            this, alarmId + 71000, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AlarmNotificationHelper.CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $label")
            .setContentText("Báo thức đang kêu — chạm để mở")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .build()
    }

    private fun startSound(ringtoneUri: String?) {
        try { player?.release() } catch (_: Exception) {}
        player = null
        try {
            val raw = when {
                ringtoneUri == null || ringtoneUri == "app:soft_chime" ||
                    ringtoneUri.endsWith("/soft_chime") -> R.raw.soft_chime
                ringtoneUri == "app:soft_bell" || ringtoneUri.endsWith("/soft_bell") -> R.raw.soft_bell
                else -> R.raw.soft_chime
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (ringtoneUri != null && ringtoneUri.startsWith("content")) {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmRingService, android.net.Uri.parse(ringtoneUri))
                    isLooping = true
                    prepare()
                    start()
                }
            } else {
                player = MediaPlayer.create(this, raw)?.apply {
                    setAudioAttributes(attrs)
                    isLooping = true
                    start()
                }
            }
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(this, R.raw.soft_chime)?.apply {
                    isLooping = true
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    private fun acquireWake() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AlarmClock:RingService"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60_000L)
            }
        } catch (_: Exception) {}
    }

    private fun stopSelfSafe() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfSafe()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.alarmclock.STOP_RING_SERVICE"

        fun start(ctx: Context, extras: Intent) {
            val i = Intent(ctx, AlarmRingService::class.java).apply {
                extras.extras?.let { putExtras(it) }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (_: Exception) {
                try { ctx.startService(i) } catch (_: Exception) {}
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, AlarmRingService::class.java).setAction(ACTION_STOP))
            } catch (_: Exception) {}
            try {
                ctx.stopService(Intent(ctx, AlarmRingService::class.java))
            } catch (_: Exception) {}
        }
    }
}
