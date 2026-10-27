package com.example.alarmclock

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BottomNavHelper {

    fun items(ctx: android.content.Context) = listOf(
        CurvedBottomNavView.Item(0, "", ctx.getString(R.string.nav_alarm), R.drawable.ic_nav_alarm),
        CurvedBottomNavView.Item(1, "", ctx.getString(R.string.nav_world), R.drawable.ic_nav_globe),
        CurvedBottomNavView.Item(2, "", ctx.getString(R.string.nav_stopwatch), R.drawable.ic_nav_stopwatch),
        CurvedBottomNavView.Item(3, "", ctx.getString(R.string.nav_timer), R.drawable.ic_nav_timer),
        CurvedBottomNavView.Item(4, "", ctx.getString(R.string.nav_gallery), R.drawable.ic_nav_gallery),
        CurvedBottomNavView.Item(5, "", ctx.getString(R.string.nav_bedtime), R.drawable.ic_nav_bed),
        CurvedBottomNavView.Item(6, "", "Chat", R.drawable.ic_nav_chat)
    )

    private fun targetClass(index: Int): Class<out AppCompatActivity> = when (index) {
        0 -> MainActivity::class.java
        1 -> WorldClockActivity::class.java
        2 -> StopwatchActivity::class.java
        3 -> TimerActivity::class.java
        4 -> GalleryActivity::class.java
        5 -> BedtimeActivity::class.java
        6 -> ChatActivity::class.java
        else -> MainActivity::class.java
    }

    private var lastNavAt = 0L

    fun bind(activity: AppCompatActivity, nav: CurvedBottomNavView, selectedIndex: Int) {
        // Đã bind cho đúng activity này rồi (onResume gọi lại) → chỉ đồng bộ kiểu + tab chọn,
        // không dựng lại thanh (dựng lại làm thanh nhảy/giật).
        if (nav.boundTo === activity) {
            try { nav.selectIndex(selectedIndex, animate = false) } catch (_: Exception) {}
            return
        }
        nav.boundTo = activity
        nav.canSelect = { android.os.SystemClock.uptimeMillis() - lastNavAt >= 350L }
        // Kiểu thanh lấy từ Cài đặt (nổi / kính lỏng / cong)
        try {
            nav.navStyle = when (AppSettings.getBottomNavStyle(activity)) {
                AppSettings.NAV_CURVED -> CurvedBottomNavView.Style.CURVED
                AppSettings.NAV_GLASS -> CurvedBottomNavView.Style.PERSISTENT
                else -> CurvedBottomNavView.Style.GOOGLE
            }
            nav.followSettings = true
        } catch (_: Exception) {}
        try {
            nav.setItems(items(activity), selectedIndex)
            nav.selectIndex(selectedIndex, animate = false)
        } catch (_: Exception) {}

        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            // Chống bấm liên tiếp làm tab nhảy loạn
            val nowMs = android.os.SystemClock.uptimeMillis()
            if (nowMs - lastNavAt < 350L) {
                try { nav.selectIndex(selectedIndex, animate = false) } catch (_: Exception) {}
                return@setOnItemSelectedListener
            }
            lastNavAt = nowMs
            val open = {
                try {
                    val cls = targetClass(index)
                    val i = Intent(activity, cls)
                    i.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    val enabled = AppSettings.isAnimationEnabled(activity)
                    if (enabled) {
                        Motion.pendingDir = if (index > selectedIndex) 1 else -1
                        Motion.pendingFrom = selectedIndex
                    } else {
                        Motion.pendingDir = 0
                        Motion.pendingFrom = -1
                    }
                    activity.startActivity(i)
                    if (cls.name.endsWith("ChatActivity")) {
                        // giữ màn báo thức phía sau, không xóa task
                    }
                    activity.overridePendingTransition(0, 0)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        activity,
                        "Không mở được tab: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            open()
        }

        // Tự ẩn thanh tab khi cuộn xuống, hiện lại khi cuộn lên
        try { nav.autoHideOnScroll(activity) } catch (_: Exception) {}
    }

    fun showFaceTestMenu(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(UiTranslate.tr("Bạn muốn làm gì?"))
            .setItems(arrayOf("Test quét mặt", "Test 10 biểu cảm", "Đóng")) { _, which ->
                when (which) {
                    0 -> activity.startActivity(
                        Intent(activity, FaceChallengeActivity::class.java)
                            .putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE)
                    )
                    1 -> activity.startActivity(
                        Intent(activity, FaceChallengeActivity::class.java)
                            .putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR)
                    )
                }
            }
            .show()
    }
}
