package com.example.alarmclock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object YouTubeMusicHelper {

    /** Mở YouTube Music hoặc YouTube với từ khóa */
    fun playSearch(context: Context, query: String = "morning alarm music") {
        val encoded = Uri.encode(query)
        val intents = listOf(
            // YouTube Music app
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encoded")).apply {
                setPackage("com.google.android.apps.youtube.music")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // YouTube app
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Browser fallback
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encoded")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) { }
        }
        Toast.makeText(context, "Không mở được YouTube Music", Toast.LENGTH_SHORT).show()
    }
}
