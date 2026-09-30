package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        try {
            TimerDoneController.fire(context.applicationContext)
        } finally {
            try { pending.finish() } catch (_: Exception) {}
        }
    }
}
