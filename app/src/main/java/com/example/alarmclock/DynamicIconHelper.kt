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
 *
 * LƯU Ý QUAN TRỌNG (OEM Xiaomi/Oppo/Vivo/Samsung):
 * setComponentEnabledSetting() trên activity-alias LAUNCHER thường
 * KILL process hoặc "buộc dừng" app — lúc đó AlarmManager bị hủy,
 * báo thức không kêu cho đến khi user tự mở app lại.
 *
 * Vì vậy:
 * - Chỉ gọi setComponentEnabledSetting khi alias hiện tại SAI buổi.
 * - KHÔNG đổi icon trong Application.onCreate (process có thể vừa
 *   được đánh thức để kêu báo thức).
 * - Sau khi thật sự đổi icon → reschedule lại toàn bộ báo thức.
 */
object DynamicIconHelper {

    private const val TAG = "DynamicIcon"
    private const val CLASS_PKG = "com.example.alarmclock"
    private const val PREF = "dynamic_icon"
    private const val KEY_LAST_PERIOD = "last_period"

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

    /**
     * Chỉ bật lại MainActivity — không đụng alias launcher.
     * An toàn khi gọi từ Application / receiver / service.
     */
    fun ensureMainEnabled(context: Context) {
        try {
            val main = ComponentName(context.packageName, "$CLASS_PKG.MainActivity")
            val pm = context.packageManager
            val state = pm.getComponentEnabledSetting(main)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(
                    main,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Đổi icon nếu cần. [fromUserUi] = true khi user đang mở màn hình chính.
     * Từ background (receiver giờ) mặc định KHÔNG đổi component để tránh
     * hệ thống buộc dừng app và mất báo thức.
     */
    fun applySafe(context: Context, fromUserUi: Boolean = false) {
        try {
            val target = currentPeriod()
            if (!needsSwitch(context, target)) {
                Log.i(TAG, "icon already $target — skip")
                rememberPeriod(context, target)
                return
            }

            // Background: nhớ buổi mới, đợi lần mở app mới đổi icon.
            // Tránh force-stop khi đang ngủ / sắp kêu báo thức.
            if (!fromUserUi) {
                Log.i(TAG, "defer icon switch to next UI open, want=$target")
                rememberPeriod(context, target)
                return
            }

            Log.i(TAG, "apply icon period=$target hour=${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}")
            val pm = context.packageManager
            val appId = context.packageName
            var changed = false

            Period.values().forEach { p ->
                val cn = ComponentName(appId, CLASS_PKG + p.alias)
                val wantEnabled = p == target
                if (isComponentEnabled(pm, cn, defaultEnabled = p == Period.MORNING) != wantEnabled) {
                    val state = if (wantEnabled)
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    try {
                        pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
                        changed = true
                    } catch (e: Exception) {
                        Log.e(TAG, "fail alias ${p.alias}", e)
                    }
                }
            }

            ensureMainEnabled(context)
            rememberPeriod(context, target)

            if (changed) {
                // OEM có thể hủy alarm khi đổi launcher component → đặt lại ngay.
                try {
                    AlarmScheduler.rescheduleAll(context)
                } catch (e: Exception) {
                    Log.e(TAG, "reschedule after icon change failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applySafe failed", e)
            ensureMainEnabled(context)
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

    private fun needsSwitch(context: Context, target: Period): Boolean {
        val pm = context.packageManager
        val appId = context.packageName
        Period.values().forEach { p ->
            val cn = ComponentName(appId, CLASS_PKG + p.alias)
            val enabled = isComponentEnabled(pm, cn, defaultEnabled = p == Period.MORNING)
            if (p == target && !enabled) return true
            if (p != target && enabled) return true
        }
        return false
    }

    private fun isComponentEnabled(
        pm: PackageManager,
        cn: ComponentName,
        defaultEnabled: Boolean
    ): Boolean {
        return when (pm.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> defaultEnabled
        }
    }

    private fun rememberPeriod(context: Context, period: Period) {
        try {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_PERIOD, period.name)
                .apply()
        } catch (_: Exception) {}
    }
}
