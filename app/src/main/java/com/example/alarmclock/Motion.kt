package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator

/** Micro-interactions only — NO activity transition animations (tránh nháy trắng). */
object Motion {
    private val ease = AccelerateDecelerateInterpolator()
    private val decel = DecelerateInterpolator()

    private fun on(view: View): Boolean = try {
        AppSettings.isAnimationEnabled(view.context)
    } catch (_: Exception) { true }

    var pendingDir: Int = 0
    var pendingFrom: Int = -1

    fun playTabEnter(activity: Activity) {
        val dir = pendingDir
        val from = pendingFrom
        pendingDir = 0
        pendingFrom = -1
        if (dir == 0) return
        val root = activity.window?.decorView ?: return
        if (!on(root)) return
        // KHÔNG đổi alpha/translation của decorView nữa: làm lộ nền đen phía sau → nháy đen.
        root.animate().cancel()
        root.alpha = 1f
        root.translationX = 0f
        if (from >= 0) {
            root.post {
                try {
                    activity.findViewById<CurvedBottomNavView>(R.id.curvedNav)
                        ?.slideIndicatorFrom(from)
                } catch (_: Exception) {}
            }
        }
    }

    fun startFadeThrough(from: Activity, intent: Intent) {
        from.startActivity(intent)
        try { from.overridePendingTransition(0, 0) } catch (_: Exception) {}
    }

    fun startSharedAxis(from: Activity, intent: Intent) {
        from.startActivity(intent)
        try { from.overridePendingTransition(0, 0) } catch (_: Exception) {}
    }

    fun finishFade(activity: Activity) {
        activity.finish()
        try { activity.overridePendingTransition(0, 0) } catch (_: Exception) {}
    }

    fun fadeScaleIn(view: View, delay: Long = 0) {
        if (!on(view)) {
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay)
            .setDuration(180)
            .setInterpolator(decel)
            .start()
    }

    fun press(view: View, then: () -> Unit) {
        if (!on(view)) {
            then()
            return
        }
        view.animate().cancel()
        view.animate()
            .scaleX(0.92f).scaleY(0.92f)
            .setDuration(70)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(110)
                    .setInterpolator(decel)
                    .withEndAction(then)
                    .start()
            }
            .start()
    }

    fun bounce(view: View, then: (() -> Unit)? = null) {
        if (!on(view)) {
            then?.invoke()
            return
        }
        view.animate().cancel()
        view.animate()
            .scaleX(1.18f).scaleY(1.18f)
            .setDuration(120)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(decel)
                    .withEndAction { then?.invoke() }
                    .start()
            }
            .start()
    }

    fun slideFadeIn(view: View, delay: Long = 0) {
        if (!on(view)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = 22f * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(delay)
            .setDuration(220)
            .setInterpolator(decel)
            .start()
    }

    /** Hiện lần lượt các con của một khung: trượt nhẹ từ dưới lên, cách nhau vài chục ms. */
    fun stagger(container: android.view.ViewGroup, baseDelay: Long = 0, step: Long = 40, max: Int = 12) {
        val n = minOf(container.childCount, max)
        for (i in 0 until n) {
            val c = container.getChildAt(i)
            if (c.visibility == View.VISIBLE) slideFadeIn(c, baseDelay + i * step)
        }
    }

    /** Dùng trong onBindViewHolder: item mới hiện lần đầu thì trượt vào, item cũ giữ nguyên. Trả về vị trí đã animate lớn nhất. */
    fun listItem(view: View, position: Int, last: Int): Int {
        view.animate().cancel()
        if (!on(view) || position <= last) {
            view.alpha = 1f
            view.translationY = 0f
            return last
        }
        slideFadeIn(view, (position % 8) * 35L)
        return position
    }

    /** Nhấn vào thì thẻ lún nhẹ (0.97), thả ra thì bật lại. Không chặn sự kiện bấm. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun pressable(view: View) {
        view.setOnTouchListener { v, e ->
            if (on(v)) {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                }
            }
            false
        }
    }

    fun pulse(view: View) {
        if (!on(view)) return
        view.animate().cancel()
        view.animate()
            .scaleX(1.03f).scaleY(1.03f)
            .setDuration(140)
            .setInterpolator(ease)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(decel)
                    .start()
            }
            .start()
    }
}
