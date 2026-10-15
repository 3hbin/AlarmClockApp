package com.example.alarmclock

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    fun applySavedLocale(context: Context) {
        val code = AppSettings.getLanguage(context)
        applyLocale(code)
    }

    fun applyLocale(code: String) {
        if (code == LanguageCatalog.SYSTEM || code.isBlank()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            return
        }
        val tag = code.replace('_', '-')
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Wrap context: ngôn ngữ + cỡ chữ (bé / vừa / to). */
    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        var changed = false

        val scale = AppSettings.getFontScale(context)
        if (config.fontScale != scale) {
            config.fontScale = scale
            changed = true
        }

        val code = AppSettings.getLanguage(context)
        if (code != LanguageCatalog.SYSTEM && code.isNotBlank()) {
            val locale = localeFromCode(code)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
            changed = true
        }

        return if (changed) context.createConfigurationContext(config) else context
    }

    private fun localeFromCode(code: String): Locale {
        val parts = code.replace('_', '-').split('-')
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale.Builder().setLanguage(parts[0]).setRegion(parts.getOrNull(1) ?: "").build()
        }
    }
}
