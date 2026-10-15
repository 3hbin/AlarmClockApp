package com.example.alarmclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class WaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!WaterReminder.isOn(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("water", "Nhắc uống nước", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 77, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            7701,
            NotificationCompat.Builder(context, "water")
                .setSmallIcon(R.drawable.ic_notification_alarm)
                .setContentTitle("Uống nước")
                .setContentText("Đến giờ uống một cốc nước.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        WaterReminder.scheduleNext(context)
    }
}

object WaterReminder {
    private fun prefs(context: Context) =
        context.getSharedPreferences("water", Context.MODE_PRIVATE)

    fun isOn(context: Context) = prefs(context).getBoolean("on", false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) scheduleNext(context) else cancel(context)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            val hour = get(java.util.Calendar.HOUR_OF_DAY)
            if (hour < 8) set(java.util.Calendar.HOUR_OF_DAY, 8)
            if (hour > 21) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 8)
            }
        }
        val pi = PendingIntent.getBroadcast(
            context, 7702, Intent(context, WaterReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setExact(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, 7702, Intent(context, WaterReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
