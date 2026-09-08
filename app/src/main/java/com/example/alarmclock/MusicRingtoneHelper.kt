package com.example.alarmclock

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Chuông Spotify / YouTube Music.
 * App không phát trực tiếp file từ 2 dịch vụ này (cần SDK + tài khoản Premium),
 * nên lưu link/từ khóa rồi lúc báo thức: kêu chuông trong app + mở Spotify/YTM.
 */
object MusicRingtoneHelper {

    private var lastLaunchAt = 0L

    fun isStreaming(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val u = uri.trim()
        return u.startsWith("spotify:") ||
            u.startsWith("ytm:") ||
            u.contains("open.spotify.com") ||
            u.contains("music.youtube.com") ||
            u.contains("youtube.com/") ||
            u.contains("youtu.be/")
    }

    fun statusText(context: Context, uri: String?): String {
        if (uri.isNullOrBlank()) return context.getString(R.string.default_ringtone)
        return when {
            uri == "app:soft_chime" -> context.getString(R.string.ringtone_soft_chime)
            uri == "app:soft_bell" -> context.getString(R.string.ringtone_soft_bell)
            uri.startsWith("spotify:") || uri.contains("open.spotify.com") ->
                "Spotify: ${queryOf(uri)}"
            uri.startsWith("ytm:") || uri.contains("music.youtube.com") ||
                uri.contains("youtube.com") || uri.contains("youtu.be") ->
                "YouTube Music: ${queryOf(uri)}"
            uri.startsWith("content") -> context.getString(R.string.choose_ringtone) + " ✓"
            else -> context.getString(R.string.choose_ringtone) + " ✓"
        }
    }

    fun encodeSpotify(input: String): String {
        val t = input.trim()
        if (t.startsWith("spotify:")) return t
        if (t.contains("open.spotify.com")) return spotifyWebToUri(t) ?: "spotify:q:$t"
        return "spotify:q:$t"
    }

    fun encodeYoutubeMusic(input: String): String {
        val t = input.trim()
        if (t.startsWith("ytm:")) return t
        if (t.startsWith("http") && (t.contains("youtube.com") || t.contains("youtu.be") || t.contains("music.youtube.com"))) {
            return "ytm:url:$t"
        }
        return "ytm:q:$t"
    }

    fun playForAlarm(context: Context, uri: String?) {
        if (!isStreaming(uri)) return
        if (AppSettings.isPureAlarmOnly(context)) return
        val now = System.currentTimeMillis()
        if (now - lastLaunchAt < 2500L) return
        lastLaunchAt = now
        val u = uri!!.trim()
        try {
            when {
                u.startsWith("ytm:q:") ->
                    YouTubeMusicHelper.playSearch(context, u.removePrefix("ytm:q:"))
                u.startsWith("ytm:url:") ->
                    openView(context, u.removePrefix("ytm:url:"))
                u.startsWith("ytm:") ->
                    YouTubeMusicHelper.playSearch(context, u.removePrefix("ytm:"))
                u.contains("music.youtube.com") || u.contains("youtube.com") || u.contains("youtu.be") ->
                    openView(context, u)
                u.startsWith("spotify:q:") ->
                    SpotifyHelper.playSearch(context, u.removePrefix("spotify:q:"))
                u.startsWith("spotify:search:") ->
                    SpotifyHelper.play(context, u)
                u.startsWith("spotify:") ->
                    SpotifyHelper.play(context, u)
                u.contains("open.spotify.com") ->
                    SpotifyHelper.play(context, spotifyWebToUri(u) ?: u)
            }
        } catch (_: Exception) {}
    }

    private fun queryOf(uri: String): String {
        val u = uri.trim()
        return when {
            u.startsWith("spotify:q:") -> u.removePrefix("spotify:q:")
            u.startsWith("spotify:search:") -> Uri.decode(u.removePrefix("spotify:search:"))
            u.startsWith("ytm:q:") -> u.removePrefix("ytm:q:")
            u.startsWith("ytm:url:") -> "link"
            u.startsWith("spotify:track:") -> "bài hát"
            u.startsWith("spotify:playlist:") -> "playlist"
            else -> u.takeLast(28)
        }
    }

    private fun spotifyWebToUri(web: String): String? {
        return try {
            val path = Uri.parse(web).path ?: return null
            val parts = path.trim('/').split('/')
            if (parts.size >= 2) {
                val type = parts[0]
                val id = parts[1].substringBefore('?')
                if (type in listOf("track", "playlist", "album", "artist") && id.isNotBlank()) {
                    return "spotify:$type:$id"
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun openView(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) {
            YouTubeMusicHelper.playSearch(context, "alarm music")
        }
    }
}
