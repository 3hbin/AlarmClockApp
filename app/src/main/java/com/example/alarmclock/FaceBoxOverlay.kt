package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Khung vuông theo dõi khuôn mặt: đỏ = chờ / sai, xanh = đúng. Luôn vẽ vòng hướng dẫn giữa. */
class FaceBoxOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val box = RectF()
    private var hasFace = false
    private var ok = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
    }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = 0x88FFFFFF.toInt()
    }
    private val guideFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x22000000
    }

    fun update(left: Float, top: Float, right: Float, bottom: Float, matched: Boolean) {
        hasFace = true
        ok = matched
        box.set(left, top, right, bottom)
        invalidate()
    }

    fun clear() {
        hasFace = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.28f
        // Vòng hướng dẫn luôn hiện
        canvas.drawCircle(cx, cy, radius, guideFill)
        canvas.drawCircle(cx, cy, radius, guidePaint)

        if (!hasFace) return
        val color = if (ok) 0xFF22C55E.toInt() else 0xFFEF4444.toInt()
        paint.color = color
        corner.color = color
        val r = 12f * resources.displayMetrics.density
        canvas.drawRoundRect(box, r, r, paint)
        val len = box.width().coerceAtMost(box.height()) * 0.18f
        canvas.drawLine(box.left, box.top + len, box.left, box.top, corner)
        canvas.drawLine(box.left, box.top, box.left + len, box.top, corner)
        canvas.drawLine(box.right - len, box.top, box.right, box.top, corner)
        canvas.drawLine(box.right, box.top, box.right, box.top + len, corner)
        canvas.drawLine(box.left, box.bottom - len, box.left, box.bottom, corner)
        canvas.drawLine(box.left, box.bottom, box.left + len, box.bottom, corner)
        canvas.drawLine(box.right - len, box.bottom, box.right, box.bottom, corner)
        canvas.drawLine(box.right, box.bottom, box.right, box.bottom - len, corner)
    }
}
