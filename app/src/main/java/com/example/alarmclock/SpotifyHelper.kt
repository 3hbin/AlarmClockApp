package com.example.alarmclock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Cách đơn giản: mở Spotify app và phát URI.
 * Không cần SHA-1 / App Remote SDK.
 *
 * URI ví dụ:
 *  - spotify:track:4cOdK2wGLETKBW3PvgPWqT
 *  - spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
 *  - spotify:album:...
 */
object SpotifyHelper {

    fun play(context: Context, spotifyUri: String? = null) {
        try {
            val uri = spotifyUri?.takeIf { it.isNotBlank() }
                ?: "spotify:playlist:37i9dQZF1DX4sWSpwq3LiO" // Peaceful Piano mặc định

            // Thử mở bằng Spotify app
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage("com.spotify.music")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: mở trên web / Play Store
            try {
                val web = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(web)
            } catch (e2: Exception) {
                Toast.makeText(context, "Chưa cài Spotify", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playSearch(context: Context, query: String) {
        play(context, "spotify:search:${Uri.encode(query)}")
    }
}
