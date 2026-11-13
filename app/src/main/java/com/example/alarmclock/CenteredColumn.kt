package com.example.alarmclock

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Giới hạn chiều rộng nội dung (tối đa 640dp) và căn giữa, để trên máy tính bảng / màn ngang
 * giao diện không bị kéo giãn hết màn hình — kiểu iPad. Điện thoại dọc (< 640dp) không đổi.
 */
class CenteredColumn(context: Context, private val maxPx: Int) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val w = if (size > 0) minOf(size, maxPx) else maxPx
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    companion object {
        const val TAG = "centered_column"

        /** Bọc nội dung của màn hình trong CenteredColumn (chỉ bọc một lần). */
        fun wrap(activity: Activity) {
            try {
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
                if (content.childCount == 0) return
                val first = content.getChildAt(0)
                if (first.tag == TAG) return
                val density = activity.resources.displayMetrics.density
                val column = CenteredColumn(activity, (640 * density).toInt()).apply { tag = TAG }
                content.removeView(first)
                column.addView(first, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                content.addView(column, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_HORIZONTAL))
            } catch (_: Exception) {}
        }

        /** Đăng ký cho toàn app: mọi màn hình đều được giới hạn và căn giữa. */
        fun install(app: Application) {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) = wrap(activity)
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
    }
}
