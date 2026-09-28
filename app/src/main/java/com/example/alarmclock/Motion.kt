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
        val dx = dir * 64f * root.resources.displayMetrics.density
        root.animate().cancel()
        root.translationX = dx
        root.alpha = 0.2f
        root.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(360)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
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
        view.translationY = 28f * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(delay)
            .setDuration(160)
            .setInterpolator(decel)
            .start()
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
