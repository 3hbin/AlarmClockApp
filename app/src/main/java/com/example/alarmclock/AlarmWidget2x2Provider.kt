package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmWidget2x2Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> mgr.updateAppWidget(id, build(context)) }
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_alarm_2x2)
            val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.tvWidget2x2Clock, clock)
            val next = WidgetUpdateHelper.nearestAlarm(context)
            if (next == null) {
                views.setTextViewText(R.id.tvWidget2x2Next, "Không có báo")
                views.setTextViewText(R.id.tvWidget2x2Count, "—")
            } else {
                val a = next.first
                views.setTextViewText(
                    R.id.tvWidget2x2Next,
                    "${WidgetUpdateHelper.formatHm(a)}  ${a.label}"
                )
                views.setTextViewText(
                    R.id.tvWidget2x2Count,
                    "Còn ${WidgetUpdateHelper.formatCountdown(next.second)}"
                )
            }
            val open = Intent(context, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.layoutWidget2x2,
                PendingIntent.getActivity(
                    context, 22, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}
