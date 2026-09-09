package com.example.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class AlarmWidget4x2Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> mgr.updateAppWidget(id, build(context)) }
    }

    companion object {
        private val ROW_IDS = listOf(
            Triple(R.id.row0, R.id.tvRow0, R.id.btnRow0),
            Triple(R.id.row1, R.id.tvRow1, R.id.btnRow1),
            Triple(R.id.row2, R.id.tvRow2, R.id.btnRow2)
        )

        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_alarm_4x2)
            val list = WidgetUpdateHelper.nextEnabledAlarms(context, 3)
            ROW_IDS.forEachIndexed { i, ids ->
                if (i < list.size) {
                    val (alarm, _) = list[i]
                    views.setViewVisibility(ids.first, View.VISIBLE)
                    val state = if (alarm.isEnabled) "Bật" else "Tắt"
                    views.setTextViewText(
                        ids.second,
                        "${WidgetUpdateHelper.formatHm(alarm)}  ${alarm.label}  · $state"
                    )
                    val toggle = Intent(context, WidgetClickReceiver::class.java).apply {
                        action = WidgetUpdateHelper.ACTION_TOGGLE
                        putExtra(WidgetUpdateHelper.EXTRA_ALARM_ID, alarm.id)
                    }
                    views.setOnClickPendingIntent(
                        ids.third,
                        PendingIntent.getBroadcast(
                            context, 40 + alarm.id, toggle,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                } else {
                    views.setViewVisibility(ids.first, View.GONE)
                }
            }
            val add = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.btnWidgetAdd,
                PendingIntent.getActivity(
                    context, 49, add,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}
