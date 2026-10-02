package com.example.alarmclock

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Một nguồn phát duy nhất — tránh 2 chuông chồng (hệ thống + raw app).
 */
object TonePlayer {
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopTask = Runnable { stop() }

    fun stop() {
        handler.removeCallbacks(stopTask)
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
    }

    fun playAppRaw(ctx: Context, raw: Int, loop: Boolean) {
        stop()
        val app = ctx.applicationContext
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttrs())
            val afd = app.resources.openRawResourceFd(raw)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = loop
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(app, raw)?.apply {
                    isLooping = loop
                    start()
                }
            } catch (_: Exception) {}
        }
    }

    fun playUri(ctx: Context, uri: String?, loop: Boolean, preview: Boolean = false) {
        stop()
        if (uri.isNullOrBlank() || uri == "silent:") return
        val app = ctx.applicationContext
        val attrs = if (preview) mediaAttrs() else alarmAttrs()

        if (uri.startsWith("music:spotify:") || uri.startsWith("music:ytm:") || uri.startsWith("music:tiktok:")) {
            openMusicApp(app, uri)
            if (!preview) playAppRaw(app, R.raw.ringtone_oz, loop)
            return
        }
        if (uri.startsWith("app:")) {
            playAppRaw(app, AppRingtones.rawOf(uri), loop)
            if (preview) handler.postDelayed(stopTask, 8000L)
            return
        }

        val parsed = try { Uri.parse(uri) } catch (_: Exception) { null }
        if (parsed != null) {
            // Ưu tiên Ringtone API cho chuông hệ thống — MediaPlayer hay fail trên Huawei.
            try {
                val r = RingtoneManager.getRingtone(app, parsed)
                if (r != null) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        r.audioAttributes = attrs
                        r.isLooping = loop
                    }
                    r.play()
                    ringtone = r
                    if (preview) handler.postDelayed(stopTask, 8000L)
                    return
                }
            } catch (_: Exception) {}
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(app, parsed)
                mp.isLooping = loop
                mp.prepare()
                mp.start()
                player = mp
                if (preview) handler.postDelayed(stopTask, 8000L)
                return
            } catch (_: Exception) {}
        }

        // Không fallback Huawei khi user đã chọn hệ thống — tránh "kêu nhạc trong app".
        if (preview) return
        playAppRaw(app, R.raw.ringtone_oz, loop)
    }

    private fun openMusicApp(ctx: Context, uri: String) {
        val pkg = when { uri.startsWith("music:ytm:") -> MusicLibraryActivity.PKG_YTM; uri.startsWith("music:tiktok:") -> "com.ss.android.ugc.trill"; else -> MusicLibraryActivity.PKG_SPOTIFY }
        val id = uri.substringAfter("music:spotify:").substringAfter("music:ytm:")
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(if (pkg == MusicLibraryActivity.PKG_SPOTIFY) "spotify:track:$id" else "https://music.youtube.com/watch?v=$id")
            setPackage(pkg)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { ctx.startActivity(view) } catch (_: Exception) {
            try { ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(pkg)) } catch (_: Exception) {}
        }
    }

    private fun alarmAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun mediaAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}
