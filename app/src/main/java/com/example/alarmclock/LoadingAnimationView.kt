package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tương đương Flutter `loading_animation_widget` — vẽ Canvas thuần,
 * animation giống nhau trên Samsung / Oppo / Huawei / Pixel / LG (không dùng ProgressBar hệ thống).
 */
class LoadingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Style {
        WAVE_DOTS,
        STAGGERED_DOTS_WAVE,
        THREE_ROTATING_DOTS,
        FOUR_ROTATING_DOTS,
        BOUNCING_BALL,
        INK_DROP,
        DISCRETE_CIRCULAR,
        HORIZONTAL_DOTS
    }

    var style: Style = Style.STAGGERED_DOTS_WAVE
        set(value) {
            field = value
            invalidate()
        }

    var animColor: Int = 0xFF3F51B5.toInt()
        set(value) {
            field = value
            paint.color = value
            paint2.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = animColor
    }
    private val paint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        color = animColor
    }
    private val rect = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    fun start() {
        if (anim.isRunning) return
        anim.start()
    }

    fun stop() {
        anim.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        when (style) {
            Style.WAVE_DOTS -> drawWaveDots(canvas, cx, cy, size)
            Style.STAGGERED_DOTS_WAVE -> drawStaggeredDots(canvas, cx, cy, size)
            Style.THREE_ROTATING_DOTS -> drawRotatingDots(canvas, cx, cy, size, 3)
            Style.FOUR_ROTATING_DOTS -> drawRotatingDots(canvas, cx, cy, size, 4)
            Style.BOUNCING_BALL -> drawBouncingBall(canvas, cx, cy, size)
            Style.INK_DROP -> drawInkDrop(canvas, cx, cy, size)
            Style.DISCRETE_CIRCULAR -> drawDiscreteCircular(canvas, cx, cy, size)
            Style.HORIZONTAL_DOTS -> drawHorizontalDots(canvas, cx, cy, size)
        }
    }

    private fun drawWaveDots(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val n = 5
        val r = size * 0.08f
        val gap = size * 0.18f
        val startX = cx - (n - 1) * gap / 2f
        for (i in 0 until n) {
            val phase = (progress + i * 0.12f) % 1f
            val y = cy + sin(phase * 2 * PI).toFloat() * size * 0.18f
            paint.alpha = (120 + 135 * (0.5f + 0.5f * cos(phase * 2 * PI).toFloat())).toInt().coerceIn(80, 255)
            canvas.drawCircle(startX + i * gap, y, r, paint)
        }
        paint.alpha = 255
    }

    private fun drawStaggeredDots(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val n = 5
        val baseR = size * 0.07f
        val gap = size * 0.16f
        val startX = cx - (n - 1) * gap / 2f
        for (i in 0 until n) {
            val phase = ((progress * 1.2f) + i * 0.15f) % 1f
            // scale up then down
            val s = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
            val r = baseR * (0.55f + 0.9f * s)
            paint.alpha = (100 + 155 * s).toInt().coerceIn(80, 255)
            canvas.drawCircle(startX + i * gap, cy, r, paint)
        }
        paint.alpha = 255
    }

    private fun drawRotatingDots(canvas: Canvas, cx: Float, cy: Float, size: Float, count: Int) {
        val radius = size * 0.28f
        val r = size * 0.08f
        val angle0 = progress * 2 * PI
        for (i in 0 until count) {
            val a = angle0 + i * (2 * PI / count)
            val x = cx + cos(a).toFloat() * radius
            val y = cy + sin(a).toFloat() * radius
            paint.alpha = (100 + 155 * ((i + 1).toFloat() / count)).toInt()
            canvas.drawCircle(x, y, r * (0.7f + 0.3f * i / count), paint)
        }
        paint.alpha = 255
    }

    private fun drawBouncingBall(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val r = size * 0.12f
        // bounce 0→1→0
        val t = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
        val ease = 1f - (1f - t) * (1f - t) // easeOut
        val y = cy + size * 0.22f - ease * size * 0.45f
        // shadow
        paint.alpha = (60 * (1f - ease * 0.7f)).toInt().coerceIn(20, 80)
        canvas.drawOval(cx - r * (1.2f - 0.4f * ease), cy + size * 0.28f, cx + r * (1.2f - 0.4f * ease), cy + size * 0.34f, paint)
        paint.alpha = 255
        canvas.drawCircle(cx, y, r, paint)
    }

    private fun drawInkDrop(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val maxR = size * 0.35f
        val r = maxR * progress
        paint.alpha = ((1f - progress) * 220).toInt().coerceIn(0, 220)
        canvas.drawCircle(cx, cy, r.coerceAtLeast(2f), paint)
        // inner solid
        paint.alpha = 255
        canvas.drawCircle(cx, cy, size * 0.08f, paint)
    }

    private fun drawDiscreteCircular(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val radius = size * 0.32f
        paint2.strokeWidth = size * 0.06f
        paint2.alpha = 60
        canvas.drawCircle(cx, cy, radius, paint2)
        paint2.alpha = 255
        val sweep = 70f + 40f * sin(progress * 2 * PI).toFloat()
        val start = progress * 360f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, start, sweep, false, paint2)
    }

    private fun drawHorizontalDots(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val n = 3
        val r = size * 0.1f
        val gap = size * 0.28f
        val startX = cx - gap
        for (i in 0 until n) {
            val phase = (progress + i * 0.25f) % 1f
            val s = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
            paint.alpha = (100 + 155 * s).toInt()
            canvas.drawCircle(startX + i * gap, cy, r * (0.6f + 0.5f * s), paint)
        }
        paint.alpha = 255
    }
}
