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

    /** Cỡ chữ: 0=bé (0.85), 1=vừa (1.0), 2=to (1.30) */
    fun setFontScaleMode(context: Context, mode: Int) =
        prefs(context).edit().putInt("font_scale_mode", mode.coerceIn(0, 2)).apply()
    fun getFontScaleMode(context: Context) = prefs(context).getInt("font_scale_mode", 1)
    fun getFontScale(context: Context): Float = when (getFontScaleMode(context)) {
        0 -> 0.85f
        2 -> 1.30f
        else -> 1.0f
    }

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
    fun setGoogleDisplayName(context: Context, name: String) =
        prefs(context).edit().putString("google_display_name", name).apply()
    fun getGoogleDisplayName(context: Context) =
        prefs(context).getString("google_display_name", "") ?: ""

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


    // ===== Khóa Cài đặt (PIN) =====
    fun setSettingsPin(context: Context, plain: String) {
        val hash = plain.hashCode().toString()
        prefs(context).edit().putString("settings_pin", hash).apply()
    }
    fun hasSettingsPin(context: Context) =
        !prefs(context).getString("settings_pin", null).isNullOrBlank()
    fun checkSettingsPin(context: Context, plain: String): Boolean {
        val stored = prefs(context).getString("settings_pin", null) ?: return false
        return stored == plain.hashCode().toString()
    }
    fun clearSettingsPin(context: Context) =
        prefs(context).edit().remove("settings_pin").apply()

    /** Session unlock Cài đặt — hết khi tắt app / process chết */
    @Volatile var settingsUnlockedThisSession: Boolean = false

    /** Session unlock toàn app (giống ngân hàng) */
    @Volatile var appUnlockedThisSession: Boolean = false

    /** Khóa cả app mỗi lần mở (dùng chung PIN Cài đặt) */
    fun setAppLockEnabled(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean("app_lock_full", on).apply()
    fun isAppLockEnabled(context: Context) =
        prefs(context).getBoolean("app_lock_full", false) && hasSettingsPin(context)

    // Mã khôi phục PIN (6 số), hết hạn 15 phút
    fun setRecoveryCode(context: Context, code: String) {
        prefs(context).edit()
            .putString("settings_recovery_code", code)
            .putLong("settings_recovery_exp", System.currentTimeMillis() + 15 * 60 * 1000L)
            .apply()
    }
    fun checkRecoveryCode(context: Context, input: String): Boolean {
        val code = prefs(context).getString("settings_recovery_code", null) ?: return false
        val exp = prefs(context).getLong("settings_recovery_exp", 0L)
        if (System.currentTimeMillis() > exp) return false
        return code == input.trim()
    }
    fun clearRecoveryCode(context: Context) =
        prefs(context).edit().remove("settings_recovery_code").remove("settings_recovery_exp").apply()

    // Language (ISO / BCP-47). "system" = follow device.
    fun setLanguage(context: Context, code: String) =
        prefs(context).edit().putString("app_language", code).apply()

    fun getLanguage(context: Context): String =
        prefs(context).getString("app_language", LanguageCatalog.SYSTEM) ?: LanguageCatalog.SYSTEM

    /** Bottom nav: 0 = curved, 1 = persistent, 2 = google nav */
    const val NAV_CURVED = 0
    const val NAV_PERSISTENT = 1
    const val NAV_GOOGLE = 2
    @Deprecated("Removed") const val NAV_LIQUID_GLASS = 1

    fun setBottomNavStyle(context: Context, style: Int) =
        prefs(context).edit().putInt("bottom_nav_style", style).apply()

    fun getBottomNavStyle(context: Context): Int {
        val s = prefs(context).getInt("bottom_nav_style", NAV_CURVED)
        // map old liquid glass -> persistent
        return if (s !in 0..2) NAV_CURVED else s
    }
}
