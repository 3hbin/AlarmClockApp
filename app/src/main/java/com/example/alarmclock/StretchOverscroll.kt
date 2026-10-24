package com.example.alarmclock

import android.graphics.Canvas
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView

/** Kéo dãn danh sách (stretch), không vệt sáng xanh mờ. */
object StretchOverscroll {

    fun attach(rv: RecyclerView) {
        rv.overScrollMode = View.OVER_SCROLL_ALWAYS
        rv.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return StretchEdge(view, direction)
            }
        }
    }

    private class StretchEdge(
        private val host: RecyclerView,
        private val direction: Int
    ) : EdgeEffect(host.context) {

        private var pulled = 0f

        override fun onPull(deltaDistance: Float) {
            onPull(deltaDistance, 0.5f)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            pulled = (pulled + deltaDistance * 0.45f).coerceIn(0f, 0.14f)
            applyStretch()
        }

        override fun onAbsorb(velocity: Int) {
            pulled = (kotlin.math.abs(velocity) / 18000f).coerceIn(0f, 0.14f)
            applyStretch()
            host.post { onRelease() }
        }

        override fun onRelease() {
            pulled = 0f
            host.animate().cancel()
            host.animate()
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(0.7f))
                .start()
        }

        override fun draw(canvas: Canvas): Boolean = false

        override fun isFinished(): Boolean = pulled <= 0.001f

        override fun finish() {
            pulled = 0f
            host.scaleY = 1f
            host.translationY = 0f
        }

        private fun applyStretch() {
            val h = host.height.coerceAtLeast(1).toFloat()
            when (direction) {
                RecyclerView.EdgeEffectFactory.DIRECTION_TOP -> {
                    host.pivotX = host.width / 2f
                    host.pivotY = 0f
                    host.scaleY = 1f + pulled
                    host.translationY = 0f
                }
                RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM -> {
                    host.pivotX = host.width / 2f
                    host.pivotY = h
                    host.scaleY = 1f + pulled
                    host.translationY = 0f
                }
                else -> {
                    host.translationY =
                        if (direction == RecyclerView.EdgeEffectFactory.DIRECTION_LEFT) pulled * 40f
                        else -pulled * 40f
                }
            }
        }
    }
}
