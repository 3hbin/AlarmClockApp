package com.example.alarmclock

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Mỗi 15 phút đặt lại toàn bộ setAlarmClock — lưới an toàn kiểu Đồng hồ Google. */
class AlarmWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            AlarmScheduler.rescheduleAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "alarm_watchdog"

        fun start(context: Context) {
            try {
                val req = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req
                )
            } catch (_: Exception) {}
        }
    }
}
