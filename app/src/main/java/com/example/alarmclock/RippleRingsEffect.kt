package com.example.alarmclock

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Hiệu ứng Ripple Rings (giống video DevFlash):
 * khi nhấn nút → 2–3 vòng tròn mở rộng + mờ dần quanh nút.
 */
object RippleRingsEffect {

    fun attach(target: View, ringColor: Int = 0xFF67E8F9.toInt()) {
        val parent = target.parent as? ViewGroup ?: return
        // Bọc target bằng FrameLayout chứa overlay nếu chưa
        if (parent is FrameLayout && parent.tag == "ripple_host") {
            installTouch(target, parent, ringColor)
            return
        }
        val idx = parent.indexOfChild(target)
        val lp = target.layoutParams
        parent.removeView(target)

        val host = FrameLayout(target.context).apply {
            tag = "ripple_host"
            layoutParams = lp
            clipChildren = false
            clipToPadding = false
        }
        // target full size trong host
        host.addView(
            target,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val overlay = RingsOverlay(target.context, ringColor)
        host.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        parent.addView(host, idx)
        // Cho phép vòng tràn ra ngoài
        parent.clipChildren = false
        (parent.parent as? ViewGroup)?.clipChildren = false

        installTouch(target, host, ringColor, overlay)
    }

    private fun installTouch(
        target: View,
        host: ViewGroup,
        ringColor: Int,
        overlay: RingsOverlay? = null
    ) {
        val ov = overlay ?: host.findViewWithTag<RingsOverlay>("rings_overlay")
            ?: return
        target.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ov.spawn(e.x, e.y)
                    v.isPressed = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    // Không consume — vẫn để click listener chạy
                }
            }
            false
        }
    }

    /** Overlay vẽ các vòng mở rộng */
    class RingsOverlay(context: android.content.Context, private val ringColor: Int) :
        View(context) {

        init {
            tag = "rings_overlay"
            setWillNotDraw(false)
            // Không chặn touch
            isClickable = false
            isFocusable = false
        }

        private data class Ring(
            var cx: Float,
            var cy: Float,
            var progress: Float = 0f,
            var animator: ValueAnimator? = null
        )

        private val rings = mutableListOf<Ring>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
            color = ringColor
        }
        private val maxRings = 3

        fun spawn(x: Float, y: Float) {
            // Spawn 3 vòng lệch phase
            for (i in 0 until maxRings) {
                val ring = Ring(x, y)
                rings.add(ring)
                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 700
                    startDelay = (i * 90).toLong()
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        ring.progress = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            rings.remove(ring)
                            invalidate()
                        }
                    })
                }
                ring.animator = anim
                anim.start()
            }
            // Scale nhẹ nút cha
            (parent as? View)?.let { host ->
                host.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                    .withEndAction {
                        host.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }.start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val maxR = (width.coerceAtLeast(height) * 0.85f).coerceAtLeast(1f)
            for (ring in rings.toList()) {
                val r = 8f + ring.progress * maxR
                val a = ((1f - ring.progress) * 200).toInt().coerceIn(0, 200)
                paint.alpha = a
                paint.strokeWidth = (3.5f + (1f - ring.progress) * 3f) * resources.displayMetrics.density
                canvas.drawCircle(ring.cx, ring.cy, r, paint)
            }
        }

        override fun onDetachedFromWindow() {
            rings.forEach { it.animator?.cancel() }
            rings.clear()
            super.onDetachedFromWindow()
        }
    }
}
