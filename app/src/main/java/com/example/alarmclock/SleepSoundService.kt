package com.example.alarmclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class SleepSoundService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopTask = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_PLAY
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val raw = intent?.getIntExtra(EXTRA_RAW, R.raw.sleep_ocean) ?: R.raw.sleep_ocean
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Tiếng sóng"
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 30) ?: 30
        currentTitle = title
        currentMinutes = minutes
        playing = true
        startForeground(NOTIF_ID, buildNotification(title, minutes))
        play(raw)
        acquireLock()
        handler.removeCallbacks(stopTask)
        if (minutes > 0) handler.postDelayed(stopTask, minutes * 60_000L)
        return START_STICKY
    }

    private fun play(raw: Int) {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val afd = resources.openRawResourceFd(raw)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {}
    }

    private fun acquireLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmclock:sleep").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun buildNotification(title: String, minutes: Int): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Âm thanh ru ngủ", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, SleepSoundActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SleepSoundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_sleep)
            .setContentTitle(title)
            .setContentText(if (minutes > 0) "Dừng sau $minutes phút" else "Đang phát")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Dừng", stop)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopTask)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        playing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "sleep_sound"
        const val NOTIF_ID = 2105
        const val ACTION_PLAY = "sleep.play"
        const val ACTION_STOP = "sleep.stop"
        const val EXTRA_RAW = "raw"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MINUTES = "minutes"
        @Volatile var playing = false
        @Volatile var currentTitle = "Tiếng sóng"
        @Volatile var currentMinutes = 30
    }
}
