package com.example.alarmclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

/**
 * Icon launcher tự đổi theo buổi (sáng / trưa / chiều / tối).
 * Icon PNG đã render nền trong suốt — không có viền đen.
 */
object DynamicIconHelper {

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
            val pkg = context.packageName
            val target = currentPeriod()
            // Bật alias đúng buổi, tắt các alias khác
            Period.values().forEach { p ->
                val cn = ComponentName(pkg, pkg + p.alias)
                val state = if (p == target)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            }
            // Tắt MAIN trên MainActivity để chỉ còn 1 icon (alias)
            val main = ComponentName(pkg, "$pkg.MainActivity")
            pm.setComponentEnabledSetting(
                main,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // Nếu lỗi, bật lại MainActivity để app không mất icon
            try {
                val main = ComponentName(context, MainActivity::class.java)
                context.packageManager.setComponentEnabledSetting(
                    main,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) { }
        }
    }

    fun scheduleHourly(context: Context) {
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
            set(Calendar.MILLISECOND, 0)
        }
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC, cal.timeInMillis, pi)
        } catch (_: Exception) {
            try {
                am.set(AlarmManager.RTC, cal.timeInMillis, pi)
            } catch (_: Exception) { }
        }
    }
}
