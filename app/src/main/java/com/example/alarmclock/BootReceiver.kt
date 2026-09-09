package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACTIONS) return
        val pending = goAsync()
        try {
            AlarmScheduler.rescheduleAll(context)
            // Notification đang kêu được lưu riêng nên khôi phục lại sau reboot,
            // không phụ thuộc việc foreground service có được Android phục hồi hay không.
            try { AlarmNotificationHelper.restoreRingingNotification(context) } catch (_: Exception) {}
            AlarmKeepAliveService.sync(context)
            try { AlarmWatchdogWorker.start(context) } catch (_: Exception) {}
            try { WidgetUpdateHelper.refreshAll(context) } catch (_: Exception) {}
        } catch (_: Exception) {
        } finally {
            try { pending.finish() } catch (_: Exception) {}
        }
    }

    companion object {
        private val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
