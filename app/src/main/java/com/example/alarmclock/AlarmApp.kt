package com.example.alarmclock

import android.app.Application

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.applyDarkMode(AppSettings.getDarkMode(this))
        LocaleHelper.applySavedLocale(this)
        CloudSyncHelper.init(this)
    }
}
