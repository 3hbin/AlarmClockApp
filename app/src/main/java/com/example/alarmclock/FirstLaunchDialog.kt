package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FirstLaunchDialog {
    private const val PREF = "onboarding"
    private const val KEY = "is_first_launch"

    fun isFirst(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, false).apply()
    }

    fun show(activity: Activity, force: Boolean = false) {
        if (!force && !isFirst(activity)) return
        val view = activity.layoutInflater.inflate(R.layout.dialog_first_launch, null)
        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton("Đã hiểu và bắt đầu") { _, _ ->
                markDone(activity)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${activity.packageName}")
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            .setCancelable(false)
            .show()
    }
}
