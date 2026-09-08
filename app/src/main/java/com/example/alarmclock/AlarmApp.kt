package com.example.alarmclock

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Chống sửa APK / hook — fail → process kill
        try { TamperGuard.verifyOrDie(this) } catch (_: Throwable) {
            try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Exception) {}
        }
        installCrashLogger()
        // Chỉ bảo đảm MainActivity còn bật. KHÔNG đổi icon ở đây:
        // process thường được đánh thức để kêu báo thức — đổi alias lúc này
        // dễ bị OEM buộc dừng app → mất chuông.
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        AppSettings.applyDarkMode(AppSettings.getDarkMode(this))
        val lang = AppSettings.getLanguage(this)
        if (lang != LanguageCatalog.SYSTEM && lang != "en" && lang.isNotBlank()) {
            AppSettings.setLanguage(this, "en")
        }
        LocaleHelper.applySavedLocale(this)
        CloudSyncHelper.init(this)
        DynamicIconHelper.scheduleHourly(this)
    }

    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val body = "=== CRASH $ts thread=${t.name} ===\n$sw\n"
                Log.e("AlarmCrash", body)
                val f = File(filesDir, "crash_log.txt")
                f.appendText(body)
                // Giữ log gọn
                if (f.length() > 200_000) {
                    val tail = f.readText().takeLast(100_000)
                    f.writeText(tail)
                }
            } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
    }

    companion object {
        fun readCrashLog(app: Application): String {
            return try {
                File(app.filesDir, "crash_log.txt").takeIf { it.exists() }?.readText().orEmpty()
            } catch (_: Exception) { "" }
        }
    }
}
