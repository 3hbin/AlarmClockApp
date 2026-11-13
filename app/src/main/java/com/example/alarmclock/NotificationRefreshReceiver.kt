package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmManager đánh thức process (kể cả đã bị kill) để đăng lại notif 2001.
 * Không tạo ID mới. Huawei tin AlarmManager hơn WorkManager.
 */
class NotificationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        try {
            AlarmNotificationHelper.restoreRingingNotification(context)
            AlarmNotificationHelper.scheduleRingingRefresh(context)
        } catch (_: Exception) {
        } finally {
            try { pending.finish() } catch (_: Exception) {}
        }
    }
}
