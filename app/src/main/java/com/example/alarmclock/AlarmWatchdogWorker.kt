package com.example.alarmclock

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Canh báo thức mỗi 15 phút: nếu hệ thống hủy AlarmManager thì đặt lại.
 * Không thay exact alarm — chỉ lưới an toàn (đúng gợi ý Gemini).
 */
class AlarmWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            AlarmScheduler.rescheduleAll(applicationContext)
            // Nếu vẫn đang kêu: đăng lại 2001 (OEM có thể đã nuốt notif sau nhiều giờ).
            try { AlarmNotificationHelper.restoreRingingNotification(applicationContext) } catch (_: Exception) {}
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "alarm_watchdog"

        fun start(context: Context) {
            try {
                val req = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req
                )
            } catch (_: Exception) {}
        }
    }
}
