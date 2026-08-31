package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Gắn cùng 6 nút thanh dưới cho mọi màn chính — không chỉ Báo thức.
 * index: 0 Báo, 1 Thế giới, 2 Bấm, 3 Đếm, 4 Ảnh, 5 Cài
 */
object BottomNavHelper {

    fun items() = listOf(
        CurvedBottomNavView.Item(0, "⏰", "Báo"),
        CurvedBottomNavView.Item(1, "🌍", "Thế giới"),
        CurvedBottomNavView.Item(2, "⏱️", "Bấm"),
        CurvedBottomNavView.Item(3, "⏳", "Đếm"),
        CurvedBottomNavView.Item(4, "🖼️", "Ảnh"),
        CurvedBottomNavView.Item(5, "⚙️", "Cài")
    )

    fun bind(activity: AppCompatActivity, nav: CurvedBottomNavView, selectedIndex: Int) {
        nav.navStyle = when (AppSettings.getBottomNavStyle(activity)) {
            AppSettings.NAV_PERSISTENT -> CurvedBottomNavView.Style.PERSISTENT
            AppSettings.NAV_GOOGLE -> CurvedBottomNavView.Style.GOOGLE
            else -> CurvedBottomNavView.Style.CURVED
        }
        nav.setItems(items(), selectedIndex)
        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            navigate(activity, index)
        }
        // Giữ tab Cài → menu test camera / biểu cảm
        nav.setOnItemLongClickListener { index, _ ->
            if (index == 5) {
                showFaceTestMenu(activity)
                true
            } else false
        }
    }

    fun showFaceTestMenu(activity: android.app.Activity) {
        val items = arrayOf(
            "📷 Test quét mặt (camera)",
            "😊 Test 10 biểu cảm dễ"
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle("Bạn muốn làm gì?")
            .setItems(items) { _, which ->
                val mode = if (which == 0) FaceChallengeActivity.MODE_FACE
                else FaceChallengeActivity.MODE_EXPR
                activity.startActivity(
                    android.content.Intent(activity, FaceChallengeActivity::class.java).apply {
                        putExtra(FaceChallengeActivity.EXTRA_MODE, mode)
                    }
                )
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun navigate(from: Activity, index: Int) {
        val target = when (index) {
            0 -> MainActivity::class.java
            1 -> WorldClockActivity::class.java
            2 -> StopwatchActivity::class.java
            3 -> TimerActivity::class.java
            4 -> GalleryActivity::class.java
            5 -> SettingsActivity::class.java
            else -> return
        }
        // Đã ở Main → chỉ cần finish các màn con
        if (target == MainActivity::class.java) {
            if (from !is MainActivity) {
                from.finish()
                from.overridePendingTransition(R.anim.fade_through_enter, R.anim.fade_through_exit)
            }
            return
        }
        if (from::class.java == target) return

        // Khóa Cài đặt bằng PIN
        if (target == SettingsActivity::class.java &&
            AppSettings.hasSettingsPin(from) &&
            !AppSettings.settingsUnlockedThisSession
        ) {
            SettingsLockHelper.requireUnlock(from) {
                val intent = Intent(from, target).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                Motion.startSharedAxis(from, intent)
                if (from !is MainActivity) from.finish()
            }
            return
        }

        val intent = Intent(from, target).apply {
            // Tránh chồng activity khi nhảy tab liên tục
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        Motion.startSharedAxis(from, intent)
        // Main giữ lại trong stack; các tab khác finish để không chồng vô hạn
        if (from !is MainActivity) {
            from.finish()
        }
    }
}
