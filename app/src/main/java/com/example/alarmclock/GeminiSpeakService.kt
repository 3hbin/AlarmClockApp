package com.example.alarmclock

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Giữ process sống để TTS đọc hết quy trình Gemini sau khi tắt chuông. */
class GeminiSpeakService : Service() {
    private var tts: TtsHelper? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        startForeground(NOTIF_ID, buildNotif())
        if (text.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        tts?.shutdown()
        tts = TtsHelper(this).also { helper ->
            helper.onDone = {
                helper.shutdown()
                stopSelf()
            }
            helper.speak(text)
            // an toàn: tự tắt sau 45s nếu TTS không báo xong
            android.os.Handler(mainLooper).postDelayed({
                try { tts?.shutdown() } catch (_: Exception) {}
                stopSelf()
            }, 45_000)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildNotif(): Notification {
        AlarmNotificationHelper.ensureChannels(this)
        return NotificationCompat.Builder(this, AlarmNotificationHelper.CHANNEL_RINGING_FGS)
            .setSmallIcon(R.drawable.ic_gemini_sparkle)
            .setContentTitle("Quy trình Gemini")
            .setContentText("Đang đọc lời nhắc…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 2103
        const val EXTRA_TEXT = "text"
        fun start(ctx: Context, text: String) {
            if (text.isBlank()) return
            val i = Intent(ctx, GeminiSpeakService::class.java).putExtra(EXTRA_TEXT, text)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {
                ctx.startService(i)
            }
        }
    }
}
