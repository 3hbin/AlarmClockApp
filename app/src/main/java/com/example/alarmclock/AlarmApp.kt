package com.example.alarmclock

import android.app.Application

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        AppSettings.applyDarkMode(AppSettings.getDarkMode(this))
        // Chỉ English / system — migrate lựa chọn cũ
        val lang = AppSettings.getLanguage(this)
        if (lang != LanguageCatalog.SYSTEM && lang != "en" && lang.isNotBlank()) {
            AppSettings.setLanguage(this, "en")
        }
        LocaleHelper.applySavedLocale(this)
        CloudSyncHelper.init(this)
        // Icon launcher theo sáng/trưa/chiều/tối
        DynamicIconHelper.applySafe(this)
        DynamicIconHelper.scheduleHourly(this)
    }
}
