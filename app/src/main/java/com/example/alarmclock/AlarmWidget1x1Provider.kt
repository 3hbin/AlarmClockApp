package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class AlarmWidget1x1Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> mgr.updateAppWidget(id, build(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdateHelper.ACTION_TOGGLE) {
            WidgetClickReceiver.toggleNearest(context)
        }
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_alarm_1x1)
            val next = WidgetUpdateHelper.nearestAlarm(context)
            if (next == null) {
                views.setTextViewText(R.id.tvWidget1x1Time, "--:--")
                views.setTextViewText(R.id.tvWidget1x1State, "Tắt")
            } else {
                views.setTextViewText(R.id.tvWidget1x1Time, WidgetUpdateHelper.formatHm(next.first))
                views.setTextViewText(R.id.tvWidget1x1State, if (next.first.isEnabled) "Bật" else "Tắt")
            }
            val toggle = Intent(context, WidgetClickReceiver::class.java).apply {
                action = WidgetUpdateHelper.ACTION_TOGGLE
            }
            views.setOnClickPendingIntent(
                R.id.btnWidget1x1,
                PendingIntent.getBroadcast(
                    context, 11, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}
