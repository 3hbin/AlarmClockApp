package com.example.alarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver cũ từng đổi icon lúc 19:00 khi app ngủ — đã tắt.
 * Nếu hệ thống còn bắn alarm cũ thì chỉ hủy lịch, không đụng alias.
 */
class IconUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DynamicIconHelper.cancelBackgroundIconAlarms(context)
    }
}
