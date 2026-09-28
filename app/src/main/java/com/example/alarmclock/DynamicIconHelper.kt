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
 * QUAN TRỌNG: MainActivity luôn ENABLED — chỉ alias đổi icon launcher.
 * Nếu tắt MainActivity → Intent tab Báo lỗi "Unable to find activity class".
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

    fun currentPeriod(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Period =
        when (hour) {
            // 5:00–10:59 sáng (7:00 là sáng, không phải trưa)
            5, 6, 7, 8, 9, 10 -> Period.MORNING
            // 11:00–15:59 trưa
            11, 12, 13, 14, 15 -> Period.NOON
            // 16:00–18:59 chiều
            16, 17, 18 -> Period.EVENING
            // 19:00–4:59 tối
            else -> Period.NIGHT
        }

    fun faceDrawable(context: Context): Int = currentPeriod().faceRes

    fun applySafe(context: Context) {
        try {
            val pm = context.packageManager
            val appId = context.packageName
            val target = currentPeriod()
            Log.i(TAG, "apply icon period=$target hour=${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}")

            // Bật alias đúng buổi, tắt alias khác (chỉ ảnh hưởng icon launcher)
            Period.values().forEach { p ->
                val cn = ComponentName(appId, CLASS_PKG + p.alias)
                val state = if (p == target)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                try {
                    pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                } catch (e: Exception) {
                    Log.e(TAG, "fail alias ${p.alias}", e)
                }
            }

            // MainActivity LUÔN bật — để Intent tab Báo / REORDER hoạt động
            val main = ComponentName(appId, "$CLASS_PKG.MainActivity")
            pm.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "applySafe failed", e)
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

    /** Gọi 1 lần khi mở app: nếu MainActivity bị tắt từ bản cũ → bật lại ngay. */
    fun ensureMainEnabled(context: Context) {
        try {
            val main = ComponentName(context.packageName, "$CLASS_PKG.MainActivity")
            context.packageManager.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }

    fun scheduleHourly(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, IconUpdateReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 9911, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val nextHour = when {
                hour < 5 -> 5
                hour < 11 -> 11
                hour < 16 -> 16
                hour < 19 -> 19
                else -> 5
            }
            val cal = Calendar.getInstance().apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
                if (nextHour == 5 && hour >= 19) add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, nextHour)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: Exception) {}
    }
}
