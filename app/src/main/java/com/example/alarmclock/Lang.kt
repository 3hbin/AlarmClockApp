package com.example.alarmclock

import android.content.Context
import java.util.Locale

object Lang {
    @Volatile private var cachedEn: Boolean? = null

    fun sync(context: Context) {
        cachedEn = AppSettings.isEnglishUi(context)
    }

    fun isEn(context: Context? = null): Boolean {
        if (context != null) {
            val v = AppSettings.isEnglishUi(context)
            cachedEn = v
            return v
        }
        cachedEn?.let { return it }
        return Locale.getDefault().language.equals("en", true)
    }

    fun t(vi: String, en: String): String = if (isEn()) en else vi

    fun t(context: Context?, vi: String, en: String): String =
        if (isEn(context)) en else vi

    fun groupName(raw: String): String {
        return when (raw.trim().lowercase()) {
            "", "chung" -> t("Chung", "General")
            "học", "hoc" -> t("Học", "Study")
            "tập", "tap" -> t("Tập", "Workout")
            "làm việc", "lam viec" -> t("Làm việc", "Work")
            "khác", "khac" -> t("Khác", "Other")
            else -> raw
        }
    }

    fun displayLabel(raw: String): String {
        val s = raw.trim()
        if (s.startsWith("Ngủ gật")) return s.replace("Ngủ gật", t("Ngủ gật", "Nap"))
        if (s == "Báo thức" || s == "Báo thức Challenge") return t("Báo thức", "Alarm")
        return s
    }

    fun minLabel(n: Int): String = t("$n phút", "$n min")
}
