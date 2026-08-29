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

        val face = skyFace
        if (face != null && !face.isRecycled) {
            // Clip circle + draw sky face (đã không có viền đen)
            canvas.save()
            val path = Path()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            canvas.clipPath(path)
            val src = android.graphics.Rect(0, 0, face.width, face.height)
            val dst = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawBitmap(face, src, dst, null)
            canvas.restore()
            // Viền trắng mỏng (không đen)
            rimWhite.strokeWidth = r * 0.045f
            canvas.drawCircle(cx, cy, r - rimWhite.strokeWidth / 2f, rimWhite)
        } else {
            canvas.drawCircle(cx, cy, r, facePaint)
            rimPaint.strokeWidth = r * 0.04f
            canvas.drawCircle(cx, cy, r, rimPaint)
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
