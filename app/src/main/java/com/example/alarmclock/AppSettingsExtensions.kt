package com.example.alarmclock

import android.content.Context

/** Bổ sung hàm còn thiếu — không đè AppSettings.kt */
fun AppSettings.hasAcceptedWelcome(context: Context) =
    prefs(context).getBoolean("welcome_accepted", false)

fun AppSettings.setWelcomeAccepted(context: Context, ok: Boolean = true) {
    prefs(context).edit().putBoolean("welcome_accepted", ok).apply()
}

fun AppSettings.setBirthday(context: Context, year: Int, month: Int, day: Int, source: String) {
    prefs(context).edit()
        .putInt("bday_year", year)
        .putInt("bday_month", month)
        .putInt("bday_day", day)
        .putString("bday_source", source)
        .apply()
}

fun AppSettings.getBirthdayYear(context: Context) = prefs(context).getInt("bday_year", 0)
fun AppSettings.getBirthdayMonth(context: Context) = prefs(context).getInt("bday_month", 0)
fun AppSettings.getBirthdayDay(context: Context) = prefs(context).getInt("bday_day", 0)
fun AppSettings.getBirthdaySource(context: Context) = prefs(context).getString("bday_source", "") ?: ""

fun AppSettings.clearBirthday(context: Context) {
    prefs(context).edit()
        .remove("bday_year").remove("bday_month").remove("bday_day").remove("bday_source")
        .apply()
}

val Alarm.Companion.REPEAT_YEARLY: Int get() = 3
