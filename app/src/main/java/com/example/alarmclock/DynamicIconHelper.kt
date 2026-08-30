package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.Calendar

/**
 * Icon launcher tự đổi theo buổi (sáng / trưa / chiều / tối).
 * Class name dùng namespace (com.example.alarmclock), không dùng applicationId.
 */
object DynamicIconHelper {

    private const val TAG = "DynamicIcon"
    /** Namespace = package của class, khác applicationId */
    private const val CLASS_PKG = "com.example.alarmclock"

    enum class Period(val alias: String, val faceRes: Int) {
        MORNING(".MainAliasMorning", R.drawable.ic_clock_morning),
        NOON(".MainAliasNoon", R.drawable.ic_clock_noon),
        EVENING(".MainAliasEvening", R.drawable.ic_clock_evening),
        NIGHT(".MainAliasNight", R.drawable.ic_clock_night);
    }

    fun currentPeriod(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Period =
        when (hour) {
            in 5..8 -> Period.MORNING
            in 9..14 -> Period.NOON
            in 15..18 -> Period.EVENING
            else -> Period.NIGHT
        }

    fun faceDrawable(context: Context): Int = currentPeriod().faceRes

    fun applySafe(context: Context) {
        try {
            val pm = context.packageManager
            val appId = context.packageName // applicationId = com.alarmclock.dongho
            val target = currentPeriod()
            Log.i(TAG, "apply icon period=$target hour=${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}")

            // Bật alias đúng buổi, tắt các alias khác
            Period.values().forEach { p ->
                val cn = ComponentName(appId, CLASS_PKG + p.alias)
                val state = if (p == target)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                try {
                    pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                    Log.i(TAG, "alias ${p.alias} -> $state")
                } catch (e: Exception) {
                    Log.e(TAG, "fail alias ${p.alias}", e)
                }
            }
            // Tắt MAIN trên MainActivity để chỉ còn 1 icon (alias)
            val main = ComponentName(appId, "$CLASS_PKG.MainActivity")
            pm.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "applySafe failed", e)
            // Nếu lỗi, bật lại MainActivity để app không mất icon
            try {
                val main = ComponentName(context.packageName, "$CLASS_PKG.MainActivity")
                context.packageManager.setComponentEnabledSetting(
                    main,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }

    fun scheduleHourly(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, IconUpdateReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 9911, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pi)
        } catch (_: Exception) {}
    }
}
