package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * 6 nút thanh dưới — emoji như bản cũ (dễ nhìn, không vector).
 */
object BottomNavHelper {

    fun items(ctx: Context) = listOf(
        CurvedBottomNavView.Item(0, "⏰", ctx.getString(R.string.nav_alarm)),
        CurvedBottomNavView.Item(1, "🌍", ctx.getString(R.string.nav_world)),
        CurvedBottomNavView.Item(2, "⏱️", ctx.getString(R.string.nav_stopwatch)),
        CurvedBottomNavView.Item(3, "⏳", ctx.getString(R.string.nav_timer)),
        CurvedBottomNavView.Item(4, "🖼️", ctx.getString(R.string.nav_gallery)),
        CurvedBottomNavView.Item(5, "⚙️", ctx.getString(R.string.nav_settings))
    )

    fun bind(activity: AppCompatActivity, nav: CurvedBottomNavView, selectedIndex: Int) {
        nav.navStyle = when (AppSettings.getBottomNavStyle(activity)) {
            AppSettings.NAV_PERSISTENT -> CurvedBottomNavView.Style.PERSISTENT
            AppSettings.NAV_GOOGLE -> CurvedBottomNavView.Style.GOOGLE
            else -> CurvedBottomNavView.Style.CURVED
        }
        nav.setItems(items(activity), selectedIndex)
        nav.setOnItemSelectedListener { index, _ ->
            if (index == selectedIndex) return@setOnItemSelectedListener
            navigate(activity, index)
        }
        nav.setOnItemLongClickListener { index, _ ->
            if (index == 5) {
                showFaceTestMenu(activity)
                true
            } else false
        }
    }

    fun showFaceTestMenu(activity: Activity) {
        val items = arrayOf(
            activity.getString(R.string.test_face_scan),
            activity.getString(R.string.test_face_expr)
        )
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.test_menu_title))
            .setItems(items) { _, which ->
                val mode = if (which == 0) FaceChallengeActivity.MODE_FACE
                else FaceChallengeActivity.MODE_EXPR
                activity.startActivity(
                    Intent(activity, FaceChallengeActivity::class.java).apply {
                        putExtra(FaceChallengeActivity.EXTRA_MODE, mode)
                    }
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
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
        if (target == MainActivity::class.java) {
            if (from !is MainActivity) {
                from.finish()
                from.overridePendingTransition(R.anim.fade_through_enter, R.anim.fade_through_exit)
            }
            return
        }
        if (from::class.java == target) return

        if (target == SettingsActivity::class.java &&
            AppSettings.hasSettingsPin(from) &&
            !AppSettings.settingsUnlockedThisSession
        ) {
            SettingsLockHelper.requireUnlock(from) {
                startTab(from, target)
            }
            return
        }
        startTab(from, target)
    }

    private fun startTab(from: Activity, target: Class<*>) {
        val intent = Intent(from, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        from.startActivity(intent)
        from.overridePendingTransition(R.anim.fade_through_enter, R.anim.fade_through_exit)
        if (from !is MainActivity) {
            from.finish()
        }
    }
}
