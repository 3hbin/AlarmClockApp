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
        view.alpha = 0f
        view.translationY = 18f
        view.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(delay)
            .setDuration(160)
            .setInterpolator(decel)
            .start()
    }

    fun pulse(view: View) {
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
