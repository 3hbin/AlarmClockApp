package com.example.alarmclock

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Thanh dưới — khi không ở MainTabActivity thì mở host ViewPager2 (không animation). */
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
        if (activity is MainTabActivity) return // host tự bind

        nav.navStyle = when (AppSettings.getBottomNavStyle(activity)) {
            AppSettings.NAV_CURVED -> CurvedBottomNavView.Style.CURVED
            AppSettings.NAV_PERSISTENT -> CurvedBottomNavView.Style.PERSISTENT
            else -> CurvedBottomNavView.Style.GOOGLE
        }
        if (!AppSettings.hasExplicitNavStyle(activity)) {
            nav.navStyle = CurvedBottomNavView.Style.GOOGLE
        }
        nav.setItems(items(activity), selectedIndex)
        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            val open = {
                val i = Intent(activity, MainTabActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    .putExtra(MainTabActivity.EXTRA_TAB, index)
                activity.startActivity(i)
                try { activity.overridePendingTransition(0, 0) } catch (_: Exception) {}
                activity.finish()
                try { activity.overridePendingTransition(0, 0) } catch (_: Exception) {}
            }
            if (index == 5) {
                SettingsLockHelper.requireUnlock(activity) { open() }
            } else {
                open()
            }
        }
    }

    fun showFaceTestMenu(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Bạn muốn làm gì?")
            .setItems(
                arrayOf(
                    "Test quét mặt (giữ 2 giây)",
                    "Test 10 biểu cảm",
                    "Đóng"
                )
            ) { _, which ->
                when (which) {
                    0 -> activity.startActivity(
                        Intent(activity, FaceChallengeActivity::class.java).apply {
                            putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_FACE)
                        }
                    )
                    1 -> activity.startActivity(
                        Intent(activity, FaceChallengeActivity::class.java).apply {
                            putExtra(FaceChallengeActivity.EXTRA_MODE, FaceChallengeActivity.MODE_EXPR)
                        }
                    )
                }
            }
            .show()
    }
}
