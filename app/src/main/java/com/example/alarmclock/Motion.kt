package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Flutter `animations` package equivalents (fade-through, shared-axis, fade-scale).
 * Short durations — no lag on low-end phones.
 */
object Motion {
    private val ease = AccelerateDecelerateInterpolator()
    private val decel = DecelerateInterpolator()

    fun startFadeThrough(from: Activity, intent: Intent) {
        from.startActivity(intent)
        from.overridePendingTransition(R.anim.fade_through_enter, R.anim.fade_through_exit)
    }

    fun startSharedAxis(from: Activity, intent: Intent) {
        from.startActivity(intent)
        from.overridePendingTransition(R.anim.shared_axis_enter, R.anim.shared_axis_exit)
    }

    fun finishFade(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.fade_through_enter, R.anim.fade_through_exit)
    }

    /** Fade-scale like Flutter FadeScaleTransition (dialogs / FAB). */
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

    /** Flag / emoji bounce khi chọn ngôn ngữ. */
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

    /** Slide-fade khi mở danh sách / filter ngôn ngữ. */
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

    /** Pulse nhẹ cho card đang chọn. */
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
