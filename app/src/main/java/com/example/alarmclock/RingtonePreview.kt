package com.example.alarmclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Nghe thử chuông khi chọn — phát loa nhạc (không phụ thuộc âm lượng báo thức). */
object RingtonePreview {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopTask = Runnable { stop() }

    fun play(context: Context, uri: String?, name: String? = null) {
        stop()
        if (uri.isNullOrBlank() || uri == "silent:") {
            Toast.makeText(context, "Im lặng — không phát", Toast.LENGTH_SHORT).show()
            return
        }
        val app = context.applicationContext
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                Toast.makeText(context, "Tăng âm lượng media để nghe thử", Toast.LENGTH_LONG).show()
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            when {
                uri.startsWith("app:") -> {
                    val raw = AppRingtones.rawOf(uri)
                    val created = MediaPlayer.create(app, raw)
                    if (created != null) {
                        created.setAudioAttributes(attrs)
                        created.start()
                        player = created
                    } else {
                        mp.release()
                        Toast.makeText(context, "Không phát được chuông này", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                else -> {
                    mp.setDataSource(app, Uri.parse(uri))
                    mp.prepare()
                    mp.start()
                    player = mp
                }
            }
            player?.isLooping = false
            val label = name ?: AppRingtones.labelOf(uri)
            Toast.makeText(context, "Đang nghe thử: $label", Toast.LENGTH_SHORT).show()
            handler.postDelayed(stopTask, 8000L)
        } catch (e: Exception) {
            stop()
            Toast.makeText(context, "Không phát được: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop() {
        handler.removeCallbacks(stopTask)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
