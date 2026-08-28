package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Đồng hồ kim tròn kiểu icon Clock Android / Material.
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 4f, 0x44000000)
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF212121.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 14f
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1565C0.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 10f
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt()
        style = Paint.Style.FILL
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF455A64.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val ticker = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.88f

        // face
        canvas.drawCircle(cx, cy, r, facePaint)
        rimPaint.strokeWidth = r * 0.04f
        canvas.drawCircle(cx, cy, r, rimPaint)

        // ticks + numbers
        numberPaint.textSize = r * 0.14f
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val inner = if (major) r * 0.78f else r * 0.88f
            val outer = r * 0.94f
            tickPaint.strokeWidth = if (major) r * 0.025f else r * 0.012f
            tickPaint.color = if (major) 0xFF37474F.toInt() else 0xFF90A4AE.toInt()
            canvas.drawLine(
                (cx + cos(angle) * inner).toFloat(),
                (cy + sin(angle) * inner).toFloat(),
                (cx + cos(angle) * outer).toFloat(),
                (cy + sin(angle) * outer).toFloat(),
                tickPaint
            )
            if (major) {
                val num = if (i == 0) 12 else i / 5
                val ny = cy + sin(angle) * r * 0.68f - (numberPaint.descent() + numberPaint.ascent()) / 2
                canvas.drawText(num.toString(), (cx + cos(angle) * r * 0.68f).toFloat(), ny.toFloat(), numberPaint)
            }
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR) % 12
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val hourAngle = Math.toRadians((hour + minute / 60f) * 30.0 - 90)
        val minuteAngle = Math.toRadians((minute + second / 60f) * 6.0 - 90)
        val secondAngle = Math.toRadians(second * 6.0 - 90)

        hourPaint.strokeWidth = r * 0.045f
        minutePaint.strokeWidth = r * 0.03f
        secondPaint.strokeWidth = r * 0.015f

        // hour hand
        canvas.drawLine(
            cx, cy,
            (cx + cos(hourAngle) * r * 0.5).toFloat(),
            (cy + sin(hourAngle) * r * 0.5).toFloat(),
            hourPaint
        )
        // minute hand
        canvas.drawLine(
            cx, cy,
            (cx + cos(minuteAngle) * r * 0.68).toFloat(),
            (cy + sin(minuteAngle) * r * 0.68).toFloat(),
            minutePaint
        )
        // second hand
        canvas.drawLine(
            cx, cy,
            (cx + cos(secondAngle) * r * 0.78).toFloat(),
            (cy + sin(secondAngle) * r * 0.78).toFloat(),
            secondPaint
        )
        canvas.drawCircle(cx, cy, r * 0.04f, centerPaint)
        canvas.drawCircle(cx, cy, r * 0.018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }
}
