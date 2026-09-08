package com.example.alarmclock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Icon launcher cố định ban đêm. Không đổi sáng/trưa/chiều/tối.
 */
object DynamicIconHelper {

    private const val TAG = "DynamicIcon"
    private const val CLASS_PKG = "com.example.alarmclock"

    enum class Period(val alias: String, val faceRes: Int) {
        MORNING(".MainAliasMorning", R.drawable.ic_clock_morning),
        NOON(".MainAliasNoon", R.drawable.ic_clock_noon),
        EVENING(".MainAliasEvening", R.drawable.ic_clock_evening),
        NIGHT(".MainAliasNight", R.drawable.ic_clock_night);
    }

    fun currentPeriod(hour: Int = 0): Period = Period.NIGHT

    fun faceDrawable(context: Context): Int = Period.NIGHT.faceRes

    fun ensureMainEnabled(context: Context) {
        freezeNightIcon(context)
    }

    fun applySafe(context: Context, fromUserUi: Boolean = false) {
        freezeNightIcon(context)
    }

    fun scheduleHourly(context: Context) {}

    fun freezeNightIcon(context: Context) {
        try {
            val pm = context.packageManager
            val appId = context.packageName
            Period.values().forEach { p ->
                val cn = ComponentName(appId, CLASS_PKG + p.alias)
                try {
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "disable ${p.alias}", e)
                }
            }
            val main = ComponentName(appId, "$CLASS_PKG.MainActivity")
            pm.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "freezeNightIcon failed", e)
        }
    }
}
