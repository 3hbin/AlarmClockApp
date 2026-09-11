package com.example.alarmclock

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import java.net.URL

object GoogleProfile {
    fun saveFromAccount(context: Context, email: String?, name: String?, photo: String?) {
        if (!email.isNullOrBlank()) AppSettings.setRecoveryEmail(context, email)
        if (!name.isNullOrBlank()) AppSettings.setGoogleDisplayName(context, name)
        AppSettings.prefs(context).edit().putString("google_photo_url", photo.orEmpty()).apply()
    }

    fun photoUrl(context: Context) =
        AppSettings.prefs(context).getString("google_photo_url", "") ?: ""

    fun bind(context: Context, nameView: TextView?, emailView: TextView?, avatar: ImageView?) {
        val name = AppSettings.getGoogleDisplayName(context)
        val email = AppSettings.getRecoveryEmail(context)
        nameView?.text = name.ifBlank { if (email.isBlank()) "Chưa đăng nhập Google" else email }
        emailView?.text = email
        emailView?.visibility = if (email.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        val url = photoUrl(context)
        if (avatar == null || url.isBlank()) return
        Thread {
            try {
                URL(url).openStream().use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    Handler(Looper.getMainLooper()).post { avatar.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {}
        }.start()
    }
}
