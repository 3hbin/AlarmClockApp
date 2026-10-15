package com.example.alarmclock

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BatteryOptHelper {

    private const val PREF_ASKED = "battery_opt_prompted_v2"

    fun isIgnoring(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /** Hiện hộp thoại rồi mở quyền Android + trang chạy nền Huawei. */
    fun promptAllowBackground(activity: Activity, force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoring(activity) && !force && !isHuaweiLike()) return
        val prefs = AppSettings.prefs(activity)
        if (!force && prefs.getBoolean(PREF_ASKED, false) && isIgnoring(activity)) return
        val title = Lang.t(activity, "Cho phép chạy nền", "Allow background run")
        val msg = Lang.t(
            activity,
            "Huawei/Android phát hiện app có thể hao pin vì báo thức cần đánh thức máy đúng giờ.\n\nBấm Cho phép → chọn Cho phép / Không tối ưu hóa.\nTrên Huawei thêm: Khởi động tự động BẬT, Chạy nền BẬT.\n\nKhông bấm Buộc dừng — báo thức sẽ mất lịch.",
            "The phone flagged this alarm app because it must wake the device on time.\n\nTap Allow → choose Allow / Don’t optimize.\nOn Huawei also turn Auto-launch and Run in background ON.\n\nDo not Force stop — alarms will be cleared."
        )
        try {
            MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(Lang.t(activity, "Cho phép", "Allow")) { _, _ ->
                    prefs.edit().putBoolean(PREF_ASKED, true).apply()
                    requestIgnore(activity)
                    activity.window.decorView.postDelayed({
                        openOemBackgroundSettings(activity)
                    }, 600)
                }
                .setNegativeButton(Lang.t(activity, "Để sau", "Later"), null)
                .show()
        } catch (_: Exception) {
            requestIgnore(activity)
        }
    }

    fun requestIgnore(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            if (!isIgnoring(context)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }

    fun openOemBackgroundSettings(context: Context) {
        val pkg = context.packageName
        val tries = mutableListOf<Intent>()
        if (isHuaweiLike()) {
            tries += component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            tries += component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
            tries += component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            tries += component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
            )
            tries += component(
                "com.hihonor.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            tries += Intent("huawei.intent.action.HSM_BOOTAPP_MANAGER")
        }
        tries += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
        }
        for (raw in tries) {
            try {
                val i = Intent(raw)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (_: Exception) {}
        }
    }

    private fun component(pkg: String, cls: String) =
        Intent().setComponent(ComponentName(pkg, cls))

    fun isHuaweiLike(): Boolean {
        val brand = Build.MANUFACTURER.orEmpty().lowercase()
        val m = Build.BRAND.orEmpty().lowercase()
        return brand.contains("huawei") || brand.contains("honor") ||
            m.contains("huawei") || m.contains("honor") ||
            brand.contains("huawei") 
    }
}
