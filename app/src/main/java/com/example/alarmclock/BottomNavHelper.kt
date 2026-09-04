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

    private fun targetClass(index: Int): Class<out AppCompatActivity> = when (index) {
        0 -> MainActivity::class.java
        1 -> WorldClockActivity::class.java
        2 -> StopwatchActivity::class.java
        3 -> TimerActivity::class.java
        4 -> GalleryActivity::class.java
        else -> SettingsActivity::class.java
    }

    fun bind(activity: AppCompatActivity, nav: CurvedBottomNavView, selectedIndex: Int) {
        // Chỉ 1 kiểu mặc định ổn định (Google Nav) — bỏ chọn 3 kiểu
        try {
            nav.navStyle = CurvedBottomNavView.Style.GOOGLE
        } catch (_: Exception) {}
        try {
            nav.setItems(items(activity), selectedIndex)
            nav.selectIndex(selectedIndex, animate = false)
        } catch (_: Exception) {}

        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            val open = {
                try {
                    val cls = targetClass(index)
                    val i = Intent(activity, cls)
                    i.addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    activity.startActivity(i)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        activity,
                        "Không mở được tab: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
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
