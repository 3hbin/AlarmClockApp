package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FirstLaunchDialog {
    private const val PREF = "onboarding"
    private const val KEY = "is_first_launch"

    fun isFirst(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, false).apply()
    }

    fun show(activity: Activity, force: Boolean = false) {
        if (!force && !isFirst(activity)) return
        val view = activity.layoutInflater.inflate(R.layout.dialog_first_launch, null)
        fun setTv(idName: String, vi: String, en: String) {
            val id = activity.resources.getIdentifier(idName, "id", activity.packageName)
            if (id != 0) (view.findViewById<android.widget.TextView>(id))?.text = Lang.t(activity, vi, en)
        }
        // fallback: walk TextViews
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView) {
                val cur = v.text?.toString().orEmpty()
                v.text = when (cur) {
                    "Hướng dẫn bắt đầu" -> Lang.t(activity, cur, "Getting started")
                    "Tắt tối ưu hóa pin cho app để báo thức không bị hệ thống hủy." ->
                        Lang.t(activity, cur, "Turn off battery optimization so alarms are not killed.")
                    "Cho phép hiển thị trên màn hình khóa để báo thức hiện khi máy ngủ." ->
                        Lang.t(activity, cur, "Allow lock-screen display so the alarm shows while the phone sleeps.")
                    "Bấm nút + để tạo báo thức, chọn giờ rồi Lưu." ->
                        Lang.t(activity, cur, "Tap + to create an alarm, pick a time, then Save.")
                    else -> cur
                }
            }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
        MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(Lang.t(activity, "Đã hiểu và bắt đầu", "Got it")) { _, _ ->
                markDone(activity)
                try { BatteryOptHelper.promptAllowBackground(activity, force = true) } catch (_: Exception) {}
            }
            .setCancelable(false)
            .show()
    }
}
