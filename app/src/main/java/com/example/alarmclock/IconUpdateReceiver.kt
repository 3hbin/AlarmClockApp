package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class IconUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // fromUserUi = false: không đụng activity-alias khi app đang chạy nền.
        // Chỉ đánh dấu buổi mới; icon thật sự đổi lúc user mở app.
        DynamicIconHelper.applySafe(context, fromUserUi = false)
        DynamicIconHelper.scheduleHourly(context)
    }
}
