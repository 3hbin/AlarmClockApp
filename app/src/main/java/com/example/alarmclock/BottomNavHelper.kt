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
        CurvedBottomNavView.Item(5, "", ctx.getString(R.string.nav_settings), R.drawable.ic_nav_settings)
    )

    fun bind(activity: AppCompatActivity, nav: CurvedBottomNavView, selectedIndex: Int) {
        if (activity is MainTabActivity) return

        val style = AppSettings.getBottomNavStyle(activity)
        nav.navStyle = when (style) {
            AppSettings.NAV_CURVED -> CurvedBottomNavView.Style.CURVED
            AppSettings.NAV_GLASS, AppSettings.NAV_PERSISTENT -> CurvedBottomNavView.Style.PERSISTENT
            else -> CurvedBottomNavView.Style.GOOGLE
        }
        nav.setItems(items(activity), selectedIndex)
        // Ép highlight đúng tab hiện tại (không animate → hết giật)
        try { nav.selectIndex(selectedIndex, animate = false) } catch (_: Exception) {}

        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            val cls = when (index) {
                0 -> MainActivity::class.java
                1 -> WorldClockActivity::class.java
                2 -> StopwatchActivity::class.java
                3 -> TimerActivity::class.java
                4 -> GalleryActivity::class.java
                5 -> SettingsActivity::class.java
                else -> null
            } ?: return@setOnItemSelectedListener

            val open = {
                // Highlight ngay tab đích trước khi chuyển (giảm cảm giác giật)
                try { nav.selectIndex(index, animate = false) } catch (_: Exception) {}
                val i = Intent(activity, cls)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                activity.startActivity(i)
                try { activity.overridePendingTransition(0, 0) } catch (_: Exception) {}
            }
            if (index == 5) SettingsLockHelper.requireUnlock(activity) { open() }
            else open()
        }
    }

    fun showFaceTestMenu(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Bạn muốn làm gì?")
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
