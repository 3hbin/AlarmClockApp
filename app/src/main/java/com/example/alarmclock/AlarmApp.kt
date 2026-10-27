package com.example.alarmclock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.facebook.FacebookSdk
import com.google.android.material.color.DynamicColors
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try { DynamicColors.applyToActivitiesIfAvailable(this) } catch (_: Exception) {}
        try { Fx.applyGlobal(this) } catch (_: Throwable) {}
        // Ghi máy này vào danh sách thiết bị (nếu đã đăng nhập) để máy mới hiện đúng lúc
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { AccountSecurity.registerThisDevice(this) } catch (_: Throwable) {}
            }, 4000)
        } catch (_: Throwable) {}
        // Chống sửa APK / hook — fail → process kill
        try { TamperGuard.verifyOrDie(this) } catch (_: Throwable) {
            try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Exception) {}
        }
        installCrashLogger()
        try { DynamicIconHelper.ensureMainEnabled(this) } catch (_: Exception) {}
        AppSettings.applyDarkMode(AppSettings.getDarkMode(this))
        val lang = AppSettings.getLanguage(this)
        if (lang != LanguageCatalog.SYSTEM && lang != "en" && lang.isNotBlank()) {
            AppSettings.setLanguage(this, "en")
        }
        LocaleHelper.applySavedLocale(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                try { UiTranslate.apply(activity) } catch (_: Exception) {}
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        try { Lang.sync(this) } catch (_: Exception) {}
        try { FacebookSdk.sdkInitialize(this) } catch (_: Exception) {}
        CloudSyncHelper.init(this)
        try { CloudSyncHelper.restoreSilently(this) } catch (_: Exception) {}
        try { AlarmScheduler.rescheduleAll(this) } catch (_: Exception) {}
        try { ClockRecovery.run(this) } catch (_: Throwable) {}
        try { registerUnlockRecovery() } catch (_: Throwable) {}
        try {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                // Đổi hình nền → xóa bộ nhớ đệm màu để app lấy màu mới
                android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(
                    { _, _ -> WallpaperTheme.invalidate() },
                    android.os.Handler(mainLooper)
                )
            }
        } catch (_: Throwable) {}
        AppVisibility.install(this)
        try { AlarmWatchdogWorker.start(this) } catch (_: Exception) {}
        try {
            AppSettings.setStatusNotificationEnabled(this, false)
            AlarmKeepAliveService.sync(this)
            WaterReminder.setOn(this, false)
            WaterReminder.cancel(this)
            DayOff.clearPause(this)
        } catch (_: Exception) {}
        // Không xin bỏ tối ưu pin lúc mở app — để máy ngủ khi ở nhà, tránh chai pin.
    }

    /** Mỗi lần người dùng mở khóa màn hình: đặt lại báo thức, chạy lại đếm ngược / bấm giờ đã lưu. */
    private fun registerUnlockRecovery() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_USER_PRESENT)
            addAction(android.content.Intent.ACTION_USER_UNLOCKED)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
                try { AlarmScheduler.rescheduleAll(context) } catch (_: Throwable) {}
                try { ClockRecovery.run(context) } catch (_: Throwable) {}
                try { BedtimeScheduler.armIfOn(context) } catch (_: Throwable) {}
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
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
            try {
                val back = android.content.Intent(this, MainActivity::class.java)
                back.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(back)
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
