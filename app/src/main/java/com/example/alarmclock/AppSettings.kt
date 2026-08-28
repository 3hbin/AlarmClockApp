package com.example.alarmclock

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    private const val PREF = "app_settings"

    fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // Pure alarm
    fun setPureAlarmOnly(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("pure_alarm", enabled).apply()
    fun isPureAlarmOnly(context: Context) = prefs(context).getBoolean("pure_alarm", false)

    fun setFaceCaptureOnFail(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("face_capture_fail", enabled).apply()
    fun isFaceCaptureOnFail(context: Context) = prefs(context).getBoolean("face_capture_fail", true)

    // Volume 0..100
    fun setAlarmVolume(context: Context, vol: Int) =
        prefs(context).edit().putInt("alarm_volume", vol.coerceIn(0, 100)).apply()
    fun getAlarmVolume(context: Context) = prefs(context).getInt("alarm_volume", 80)

    // Vibrate
    fun setVibrate(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean("vibrate", on).apply()
    fun isVibrate(context: Context) = prefs(context).getBoolean("vibrate", true)

    // Default snooze minutes
    fun setDefaultSnooze(context: Context, min: Int) =
        prefs(context).edit().putInt("default_snooze", min).apply()
    fun getDefaultSnooze(context: Context) = prefs(context).getInt("default_snooze", 5)

    // Dark mode: 0 system, 1 on, 2 off
    fun setDarkMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("dark_mode", mode).apply()
        applyDarkMode(mode)
    }
    fun getDarkMode(context: Context) = prefs(context).getInt("dark_mode", 0)
    fun applyDarkMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                1 -> AppCompatDelegate.MODE_NIGHT_YES
                2 -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    // 24h format
    fun setUse24h(context: Context, use24: Boolean) =
        prefs(context).edit().putBoolean("use_24h", use24).apply()
    fun isUse24h(context: Context) = prefs(context).getBoolean("use_24h", true)

    // Gallery password (simple hash store)
    fun setGalleryPassword(context: Context, plain: String) {
        val hash = plain.hashCode().toString()
        prefs(context).edit().putString("gallery_pw", hash).apply()
    }
    fun hasGalleryPassword(context: Context) =
        !prefs(context).getString("gallery_pw", null).isNullOrBlank()
    fun checkGalleryPassword(context: Context, plain: String): Boolean {
        val stored = prefs(context).getString("gallery_pw", null) ?: return false
        return stored == plain.hashCode().toString()
    }
    fun clearGalleryPassword(context: Context) =
        prefs(context).edit().remove("gallery_pw").apply()

    // Recovery email (Google)
    fun setRecoveryEmail(context: Context, email: String) =
        prefs(context).edit().putString("recovery_email", email.trim().lowercase()).apply()
    fun getRecoveryEmail(context: Context) =
        prefs(context).getString("recovery_email", "") ?: ""

    // Anti-troll: chống người khác tắt báo thức
    fun setAntiTroll(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean("anti_troll", on).apply()
    fun isAntiTroll(context: Context) = prefs(context).getBoolean("anti_troll", false)

    fun setAntiTrollPin(context: Context, pin: String) =
        prefs(context).edit().putString("anti_troll_pin", pin).apply()
    fun getAntiTrollPin(context: Context) =
        prefs(context).getString("anti_troll_pin", "") ?: ""
    fun hasAntiTrollPin(context: Context) = getAntiTrollPin(context).length >= 4

    fun checkAntiTrollPin(context: Context, input: String): Boolean {
        val pin = getAntiTrollPin(context)
        return pin.isNotEmpty() && pin == input
    }

    // Language (ISO / BCP-47). "system" = follow device.
    fun setLanguage(context: Context, code: String) =
        prefs(context).edit().putString("app_language", code).apply()

    fun getLanguage(context: Context): String =
        prefs(context).getString("app_language", LanguageCatalog.SYSTEM) ?: LanguageCatalog.SYSTEM

    /** Bottom nav style: 0 = curved (Android mặc định), 1 = liquid glass */
    const val NAV_CURVED = 0
    const val NAV_LIQUID_GLASS = 1

    fun setBottomNavStyle(context: Context, style: Int) =
        prefs(context).edit().putInt("bottom_nav_style", style).apply()

    fun getBottomNavStyle(context: Context): Int =
        prefs(context).getInt("bottom_nav_style", NAV_CURVED)
}
