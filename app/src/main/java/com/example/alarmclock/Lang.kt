package com.example.alarmclock

import android.content.Context
import java.util.Locale

object Lang {
    fun isEn(context: Context? = null): Boolean {
        if (context != null) return AppSettings.isEnglishUi(context)
        return Locale.getDefault().language.equals("en", true)
    }

    fun t(context: Context?, vi: String, en: String): String =
        if (isEn(context)) en else vi
}
