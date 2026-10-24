package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WidgetUpdateHelper.ACTION_TOGGLE) return
        val id = intent.getIntExtra(WidgetUpdateHelper.EXTRA_ALARM_ID, -1)
        if (id >= 0) toggleId(context, id) else toggleNearest(context)
    }

    companion object {
        fun toggleNearest(context: Context) {
            val next = WidgetUpdateHelper.nearestAlarm(context) ?: return
            toggleId(context, next.first.id)
        }

        fun toggleId(context: Context, alarmId: Int) {
            val repo = AlarmRepository(context)
            val list = repo.getAlarms()
            val alarm = list.find { it.id == alarmId } ?: return
            alarm.isEnabled = !alarm.isEnabled
            repo.saveAlarms(list)
            if (alarm.isEnabled) AlarmScheduler.schedule(context, alarm)
            else AlarmScheduler.cancel(context, alarm.id)
            WidgetUpdateHelper.refreshAll(context)
        }
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdateHelper.refreshAll(context)
    }
}
