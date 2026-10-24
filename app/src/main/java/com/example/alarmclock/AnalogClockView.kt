package com.example.alarmclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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

    private var skyFace: Bitmap? = null
    private var lastPeriod: DynamicIconHelper.Period? = null
    private val rimWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private val handDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A1A1A.toInt()
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5F5F5.toInt()
        style = Paint.Style.FILL
        setShadowLayer(4f, 0f, 1f, 0x44000000)
    }

    private fun ensureSkyFace() {
        val period = DynamicIconHelper.currentPeriod()
        if (period == lastPeriod && skyFace != null) return
        lastPeriod = period
        skyFace = try {
            BitmapFactory.decodeResource(resources, period.faceRes)
        } catch (_: Exception) { null }
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
        ensureSkyFace()
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.92f

        // Mặt đồng hồ sáng (có số) — không dùng bitmap xám trống
        canvas.drawCircle(cx, cy, r, facePaint)
        rimPaint.strokeWidth = r * 0.035f
        rimPaint.color = 0xFFE0E0E0.toInt()
        canvas.drawCircle(cx, cy, r, rimPaint)

        // Vạch + số 1–12
        numberPaint.textSize = r * 0.15f
        numberPaint.color = 0xFF37474F.toInt()
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val inner = if (major) r * 0.78f else r * 0.88f
            val outer = r * 0.94f
            tickPaint.strokeWidth = if (major) r * 0.028f else r * 0.012f
            tickPaint.color = if (major) 0xFF455A64.toInt() else 0xFFB0BEC5.toInt()
            canvas.drawLine(
                (cx + cos(angle) * inner).toFloat(),
                (cy + sin(angle) * inner).toFloat(),
                (cx + cos(angle) * outer).toFloat(),
                (cy + sin(angle) * outer).toFloat(),
                tickPaint
            )
            if (major) {
                val num = if (i == 0) 12 else i / 5
                val ny = cy + sin(angle) * r * 0.65f - (numberPaint.descent() + numberPaint.ascent()) / 2
                canvas.drawText(
                    num.toString(),
                    (cx + cos(angle) * r * 0.65f).toFloat(),
                    ny.toFloat(),
                    numberPaint
                )
            }
        }

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR) % 12
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val hourAngle = Math.toRadians((hour + minute / 60f) * 30.0 - 90)
        val minuteAngle = Math.toRadians((minute + second / 60f) * 6.0 - 90)
        val secondAngle = Math.toRadians(second * 6.0 - 90)

        // Kim tối (xám đậm, không pure black cứng)
        handDark.color = 0xFF2C2C2C.toInt()
        handDark.strokeWidth = r * 0.055f
        canvas.drawLine(
            cx, cy,
            (cx + cos(hourAngle) * r * 0.48).toFloat(),
            (cy + sin(hourAngle) * r * 0.48).toFloat(),
            handDark
        )
        handDark.strokeWidth = r * 0.038f
        canvas.drawLine(
            cx, cy,
            (cx + cos(minuteAngle) * r * 0.68).toFloat(),
            (cy + sin(minuteAngle) * r * 0.68).toFloat(),
            handDark
        )
        // Kim giây mỏng đỏ
        secondPaint.strokeWidth = r * 0.014f
        canvas.drawLine(
            cx, cy,
            (cx + cos(secondAngle) * r * 0.76).toFloat(),
            (cy + sin(secondAngle) * r * 0.76).toFloat(),
            secondPaint
        )
        // Hub trắng
        canvas.drawCircle(cx, cy, r * 0.06f, hubPaint)
        canvas.drawCircle(cx, cy, r * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2C2C2C.toInt()
        })
    }
}
