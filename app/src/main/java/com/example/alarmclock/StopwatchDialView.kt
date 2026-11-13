package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Vòng quay bấm giờ: một vòng = 60 giây, có vạch giây, đuôi sáng và chấm phát sáng. */
class StopwatchDialView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var accent: Int = 0xFF4351B7.toInt()
        set(v) { field = v; rebuildShader(); invalidate() }
    var night: Boolean = false
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private var elapsed = 0L
    private var running = false
    private val rect = RectF()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setState(ms: Long, isRunning: Boolean) {
        elapsed = ms
        running = isRunning
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = when {
            w > 0 && h > 0 -> min(w, h)
            w > 0 -> w
            h > 0 -> h
            else -> (280 * d).toInt()
        }
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    private fun rebuildShader() {
        if (width == 0 || height == 0) return
        val soft = (accent and 0x00FFFFFF) or 0x40000000
        arc.shader = SweepGradient(width / 2f, height / 2f, intArrayOf(soft, accent, accent), floatArrayOf(0f, 0.97f, 1f))
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val stroke = 11 * d
        val r = min(width, height) / 2f - 26 * d

        // Vạch giây
        val sec = ((elapsed / 1000) % 60).toInt()
        val baseTick = if (night) 0x55FFFFFF else 0x33000000
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val ang = Math.toRadians(i * 6.0 - 90.0)
            val r1 = r + 15 * d
            val r2 = r1 + (if (major) 9 else 5) * d
            tick.strokeWidth = (if (major) 2.4f else 1.4f) * d
            val lit = (running || elapsed > 0) && i == sec
            tick.color = if (lit) accent else baseTick
            c.drawLine(
                cx + (r1 * cos(ang)).toFloat(), cy + (r1 * sin(ang)).toFloat(),
                cx + (r2 * cos(ang)).toFloat(), cy + (r2 * sin(ang)).toFloat(), tick
            )
        }

        // Đường nền
        track.strokeWidth = stroke
        track.color = (accent and 0x00FFFFFF) or 0x26000000
        c.drawCircle(cx, cy, r, track)

        // Cung tiến độ (một vòng mỗi phút)
        val sweep = ((elapsed % 60000L) / 60000f) * 360f
        if (elapsed > 0) {
            arc.strokeWidth = stroke
            rect.set(cx - r, cy - r, cx + r, cy + r)
            c.save()
            c.rotate(-90f, cx, cy)
            c.drawArc(rect, 0f, sweep.coerceAtLeast(0.5f), false, arc)
            c.restore()

            // Chấm phát sáng ở đầu cung
            val a = Math.toRadians(sweep - 90.0)
            val px = cx + (r * cos(a)).toFloat()
            val py = cy + (r * sin(a)).toFloat()
            val pulse = if (running) 1f + 0.18f * sin(elapsed / 220.0).toFloat() else 1f
            dot.style = Paint.Style.FILL
            dot.color = (accent and 0x00FFFFFF) or 0x40000000
            c.drawCircle(px, py, 15 * d * pulse, dot)
            dot.color = 0xFFFFFFFF.toInt()
            c.drawCircle(px, py, 8 * d, dot)
            dot.style = Paint.Style.STROKE
            dot.strokeWidth = 3 * d
            dot.color = accent
            c.drawCircle(px, py, 8 * d, dot)
        }
    }
}
