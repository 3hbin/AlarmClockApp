package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/** Hạt trắng trôi nhẹ trên màn âm thanh ru ngủ. */
class SleepWhiteFxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var playing: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = Array(46) { Dot() }
    private var pulse = 0f

    private class Dot {
        var x = 0f
        var y = 0f
        var r = 1.5f
        var speed = 0.4f
        var alpha = 80
        var ready = false
    }

    private fun reset(dot: Dot, fromBottom: Boolean) {
        if (width == 0 || height == 0) return
        dot.x = Random.nextFloat() * width
        dot.y = if (fromBottom) height + Random.nextFloat() * 40f else Random.nextFloat() * height
        dot.r = 1.2f + Random.nextFloat() * 2.4f
        dot.speed = 0.35f + Random.nextFloat() * 1.1f
        dot.alpha = 70 + Random.nextInt(140)
        dot.ready = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val boost = if (playing) 1.6f else 0.7f
        for (dot in dots) {
            if (!dot.ready) reset(dot, false)
            dot.y -= dot.speed * boost * resources.displayMetrics.density * 0.55f
            dot.x += kotlin.math.sin(dot.y / 40f) * 0.35f
            if (dot.y < -8f) reset(dot, true)
            paint.color = Color.argb((dot.alpha * if (playing) 1f else 0.65f).toInt().coerceIn(40, 230), 255, 255, 255)
            canvas.drawCircle(dot.x, dot.y, dot.r * resources.displayMetrics.density * 0.55f, paint)
        }
        if (playing) {
            pulse = (pulse + 0.012f) % 1f
            val cx = width / 2f
            val cy = height * 0.42f
            val radius = 70f * resources.displayMetrics.density + pulse * 90f * resources.displayMetrics.density
            paint.color = Color.argb((90 * (1f - pulse)).toInt(), 255, 255, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.FILL
        }
        postInvalidateOnAnimation()
    }
}
