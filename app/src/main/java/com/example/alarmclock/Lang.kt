package com.example.alarmclock

import android.content.Context
import java.util.Locale

object Lang {
    @Volatile private var cachedEn: Boolean? = null

    fun sync(context: Context) {
        LangPack.resolve(context)
    }

    /** true = không phải tiếng Việt (dùng nhánh tiếng Anh / ngôn ngữ khác). */
    fun isEn(context: Context? = null): Boolean {
        val code = if (context != null) LangPack.resolve(context) else LangPack.current()
        return code != "vi"
    }

    fun t(vi: String, en: String): String = LangPack.text(LangPack.current(), vi, en)

    fun t(context: Context?, vi: String, en: String): String =
        if (context != null) LangPack.text(LangPack.resolve(context), vi, en)
        else LangPack.text(LangPack.current(), vi, en)

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
