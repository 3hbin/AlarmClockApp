package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class IconUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DynamicIconHelper.applySafe(context)
        DynamicIconHelper.scheduleHourly(context)
    }
}
