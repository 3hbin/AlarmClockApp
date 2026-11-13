package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Vùng sáng mờ ở đáy màn Gemini Live (giống app Gemini): các mảng sáng xanh trôi ngang,
 * dâng cao và chuyển trắng-tím khi đang nghe / đang nói.
 */
class LiveGlowView(context: Context) : View(context) {
    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE
    /** Mức âm thanh micro 0..1. */
    var level: Float = 0f

    private val bg = 0xFF101114.toInt()
    private var amp = 0f
    private val t0 = System.nanoTime()
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fadePaint = Paint()
    private val cur = IntArray(4)

    private val unit = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
    )

    init {
        blobPaint.shader = unit
        val p = palette()
        for (i in 0..3) cur[i] = p[i]
    }

    private fun palette(): IntArray = when (state) {
        State.IDLE -> intArrayOf(0xFF38C8FF.toInt(), 0xFF2F6BFF.toInt(), 0xFF1E49C8.toInt(), 0xFF5AD7FF.toInt())
        State.LISTENING -> intArrayOf(0xFFEAF3FF.toInt(), 0xFF9CC4FF.toInt(), 0xFF6FA2FF.toInt(), 0xFFCDB8FF.toInt())
        State.THINKING -> intArrayOf(0xFFB388FF.toInt(), 0xFFFF80AB.toInt(), 0xFF7C4DFF.toInt(), 0xFF82B1FF.toInt())
        State.SPEAKING -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFBFD7FF.toInt(), 0xFF8FB4FF.toInt(), 0xFFC9B6FF.toInt())
    }

    private fun lerp(a: Int, b: Int, f: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fadePaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.62f,
            bg, (bg and 0x00FFFFFF), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1_000_000_000f
        val w = width.toFloat()
        val h = height.toFloat()

        val target = when (state) {
            State.IDLE -> 0.12f + 0.05f * sin(t * 1.6f)
            State.LISTENING -> 0.25f + level * 0.75f
            State.THINKING -> 0.38f + 0.18f * sin(t * 3f)
            State.SPEAKING -> (0.45f + 0.30f * abs(sin(t * 5.5f)) + 0.12f * sin(t * 9f)).coerceIn(0f, 1f)
        }
        amp += (target - amp) * 0.14f

        val pal = palette()
        for (i in 0..3) cur[i] = lerp(cur[i], pal[i], 0.06f)

        for (i in 0 until 6) {
            val ph = i * 1.7f
            val cx = w * (0.08f + 0.84f * (i / 5f)) + w * 0.08f * sin(t * (0.6f + 0.13f * i) + ph)
            val rise = 0.12f + amp * 0.70f * (0.5f + 0.5f * sin(t * (1.3f + 0.21f * i) + ph))
            val cy = h - h * 0.46f * rise
            val rx = w * (0.34f + 0.06f * (i % 3))
            val ry = h * (0.10f + 0.20f * amp) * (0.8f + 0.2f * (i % 2))
            blobPaint.colorFilter = PorterDuffColorFilter(cur[i % 4], PorterDuff.Mode.SRC_IN)
            blobPaint.alpha = (150 + 100 * amp).toInt().coerceIn(0, 255)
            c.save()
            c.translate(cx, cy)
            c.scale(rx, ry)
            c.drawCircle(0f, 0f, 1f, blobPaint)
            c.restore()
        }

        // Mờ dần lên phía trên để vùng sáng "nhô" ra từ đáy
        c.drawRect(0f, 0f, w, h * 0.62f, fadePaint)

        if (Fx.on(context)) postInvalidateOnAnimation()
    }
}
