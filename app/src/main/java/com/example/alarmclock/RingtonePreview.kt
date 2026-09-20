package com.example.alarmclock

import android.content.Context
import android.widget.Toast

object RingtonePreview {
    fun play(context: Context, uri: String?, name: String? = null) {
        if (uri.isNullOrBlank() || uri == "silent:") {
            TonePlayer.stop()
            Toast.makeText(context, "Im lặng — không phát", Toast.LENGTH_SHORT).show()
            return
        }
        TonePlayer.playUri(context, uri, loop = false, preview = true)
        val label = name ?: AppRingtones.labelOf(uri)
        Toast.makeText(context, "Đang nghe thử: $label", Toast.LENGTH_SHORT).show()
    }

    fun stop() {
        TonePlayer.stop()
    }
}
