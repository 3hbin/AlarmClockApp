package com.example.alarmclock

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Icon launcher theo buổi.
 *
 * Không đổi icon khi app chạy nền (mốc 19:00…). Việc đổi alias
 * có thể làm OEM giết process và hủy PendingIntent của AlarmManager.
 *
 * Chỉ đổi khi người dùng mở app (foreground). Sau khi đổi:
 * lưu báo thức → reschedule setAlarmClock → khởi động lại Activity.
 */
object DynamicIconHelper {

    private const val TAG = "DynamicIcon"
    private const val CLASS_PKG = "com.example.alarmclock"
    private const val PREFS = "dynamic_icon"
    private const val KEY_PERIOD = "applied_period"
    private const val KEY_SNAPSHOT = "alarm_snapshot"
    private const val KEY_AUTO = "auto_icon"
    const val EXTRA_ICON_RESTART = "icon_restart"

    fun isAutoIcon(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)

    fun setAutoIcon(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO, on).apply()
    }

    /** Tắt tự đổi: khóa icon buổi tối, không restart ở đây. */
    fun lockEveningIcon(context: Context) {
        setAutoIcon(context, false)
        persistAlarmsSnapshot(context)
        applyAliases(context, Period.NIGHT)
        saveApplied(context, Period.NIGHT)
        try { AlarmScheduler.rescheduleAll(context) } catch (_: Exception) {}
    }

    enum class Period(val alias: String, val faceRes: Int) {
        MORNING(".MainAliasMorning", R.drawable.ic_clock_morning),
        NOON(".MainAliasNoon", R.drawable.ic_clock_noon),
        EVENING(".MainAliasEvening", R.drawable.ic_clock_evening),
        NIGHT(".MainAliasNight", R.drawable.ic_clock_night);
    }

    fun currentPeriod(hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): Period =
        when (hour) {
            5, 6, 7, 8, 9, 10 -> Period.MORNING
            11, 12, 13, 14, 15 -> Period.NOON
            16, 17, 18 -> Period.EVENING
            else -> Period.NIGHT
        }

    fun faceDrawable(context: Context): Int = currentPeriod().faceRes

    fun appliedPeriod(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PERIOD, null)

    fun needsSwitch(context: Context): Boolean =
        appliedPeriod(context) != currentPeriod().name

    /**
     * Gọi từ MainActivity.onCreate khi user mở app.
     * @return true nếu đã khởi động lại Activity (caller phải return ngay).
     */
    fun applyOnUserOpen(activity: Activity): Boolean {
        ensureMainEnabled(activity)
        cancelBackgroundIconAlarms(activity)
        if (!isAutoIcon(activity)) {
            if (appliedPeriod(activity) != Period.NIGHT.name) {
                applyAliases(activity, Period.NIGHT)
                saveApplied(activity, Period.NIGHT)
            }
            return false
        }
        val want = currentPeriod()
        if (appliedPeriod(activity) == want.name) return false
        if (activity.intent.getBooleanExtra(EXTRA_ICON_RESTART, false)) {
            saveApplied(activity, want)
            return false
        }
        persistAlarmsSnapshot(activity)
        applyAliases(activity, want)
        saveApplied(activity, want)
        AlarmScheduler.rescheduleAll(activity)
        Log.i(TAG, "icon switched to $want — restarting after reschedule")
        val next = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ICON_RESTART, true)
        }
        activity.startActivity(next)
        activity.finish()
        return true
    }

    /** Không dùng khi app chạy nền. */
    fun applySafe(context: Context) {
        if (AppVisibility.foreground) {
            applyAliases(context, currentPeriod())
            saveApplied(context, currentPeriod())
        } else {
            Log.i(TAG, "skip background icon change")
        }
    }

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

    /** Bản cũ còn hẹn IconUpdateReceiver — hủy hết. */
    fun cancelBackgroundIconAlarms(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 9911,
                Intent(context, IconUpdateReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            pi.cancel()
        } catch (_: Exception) {}
    }

    fun scheduleHourly(context: Context) {
        // Cố ý không hẹn đổi icon nền nữa.
        cancelBackgroundIconAlarms(context)
    }

    fun persistAlarmsSnapshot(context: Context) {
        try {
            val list = AlarmRepository(context).getAlarms()
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(JSONObject().apply {
                    put("id", a.id)
                    put("hour", a.hour)
                    put("minute", a.minute)
                    put("enabled", a.isEnabled)
                    put("label", a.label ?: "")
                    put("repeat", a.repeatMode)
                    put("ringtone", a.ringtoneUri ?: "")
                    put("snooze", a.snoozeMinutes)
                    put("challenge", a.challengeType)
                    put("shake", a.shakeTargetCount)
                    put("strict", a.isStrictAntiSnooze)
                    put("voice", a.voiceNote ?: "")
                    put("crescendo", a.useCrescendo)
                    put("skipHolidays", a.skipHolidays)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_SNAPSHOT, arr.toString()).apply()
            try { AlarmRepository(context).saveAlarms(list) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "snapshot failed", e)
        }
    }

    private fun applyAliases(context: Context, target: Period) {
        try {
            val pm = context.packageManager
            val appId = context.packageName
            Log.i(TAG, "apply icon period=$target hour=${Calendar.getInstance().get(Calendar.HOUR_OF_DAY)}")
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
            ensureMainEnabled(context)
        } catch (e: Exception) {
            Log.e(TAG, "applyAliases failed", e)
            ensureMainEnabled(context)
        }
    }

    private fun saveApplied(context: Context, period: Period) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PERIOD, period.name).apply()
    }
}
