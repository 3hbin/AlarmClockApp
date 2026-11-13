package com.example.alarmclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Vùng sóng xanh phát sáng ở đáy màn Gemini Live (giống Gemini Live thật):
 * sóng nhỏ khi chờ, nhấp nhô theo giọng khi nghe, chuyển tím khi nghĩ, nhịp mạnh khi Gemini nói.
 */
class LiveWaveView(context: Context) : View(context) {
    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE
    /** Mức âm thanh 0..1 từ micro khi đang nghe. */
    var level: Float = 0f

    private val d = resources.displayMetrics.density
    private val t0 = System.nanoTime()
    private var energy = 0.1f
    private var color = 0xFF3D7BFF.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val crest = Path()

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                val r = 44 * d
                // Chỉ bo hai góc dưới
                o.setRoundRect(0, -r.toInt(), v.width, v.height, r)
            }
        }
    }

    private fun tint(): Int = when (state) {
        State.IDLE -> 0xFF3D7BFF.toInt()
        State.LISTENING -> 0xFF2FA8FF.toInt()
        State.THINKING -> 0xFF8B6CF0.toInt()
        State.SPEAKING -> 0xFF3D7BFF.toInt()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - t0) / 1_000_000_000f

        val target = when (state) {
            State.IDLE -> 0.12f + 0.05f * sin(t * 1.6f)
            State.LISTENING -> 0.22f + level * 0.78f
            State.THINKING -> 0.36f + 0.10f * sin(t * 4f)
            State.SPEAKING -> (0.45f + 0.30f * abs(sin(t * 3.1f)) + 0.15f * sin(t * 7.3f)).coerceIn(0f, 1f)
        }
        energy += (target - energy) * 0.12f
        color = ColorUtils.blendARGB(color, tint(), 0.06f)

        val light = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.55f)
        val deep = ColorUtils.blendARGB(color, 0xFF001A66.toInt(), 0.35f)

        // Nền phát sáng từ đáy lên
        fill.shader = LinearGradient(
            0f, h * 0.10f, 0f, h,
            intArrayOf(
                ColorUtils.setAlphaComponent(deep, 0),
                ColorUtils.setAlphaComponent(deep, (70 + 90 * energy).toInt().coerceAtMost(200)),
                ColorUtils.setAlphaComponent(color, 235)
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)

        val step = 10 * d
        for (i in 0..2) {
            val phase = t * (0.9f + 0.45f * i) * (1f + energy) + i * 1.7f
            val amp = (10f + 46f * energy) * (1f - 0.22f * i) * d
            val baseY = h * (0.46f + 0.13f * i)
            path.reset()
            crest.reset()
            path.moveTo(0f, h)
            var x = 0f
            var first = true
            while (x <= w + step) {
                val u = x / w * 2f * PI.toFloat()
                val y = baseY - amp * (0.6f * sin(u * 1.3f + phase) + 0.4f * sin(u * 2.4f - phase * 1.3f))
                path.lineTo(x, y)
                if (first) { crest.moveTo(x, y); first = false } else crest.lineTo(x, y)
                x += step
            }
            path.lineTo(w, h)
            path.close()

            val top = when (i) {
                0 -> ColorUtils.setAlphaComponent(light, 210)
                1 -> ColorUtils.setAlphaComponent(color, 190)
                else -> ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), 90)
            }
            val mid = when (i) {
                0 -> ColorUtils.setAlphaComponent(color, 150)
                1 -> ColorUtils.setAlphaComponent(deep, 150)
                else -> ColorUtils.setAlphaComponent(light, 40)
            }
            fill.shader = LinearGradient(
                0f, baseY - amp * 1.4f, 0f, h,
                intArrayOf(ColorUtils.setAlphaComponent(top, 0), top, mid),
                floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP
            )
            c.drawPath(path, fill)

            if (i == 0) {
                // Viền sáng mờ ở đỉnh sóng
                line.shader = null
                val widths = floatArrayOf(14f, 8f, 3f)
                val alphas = intArrayOf(0x14, 0x24, 0x55)
                for (k in 0..2) {
                    line.strokeWidth = widths[k] * d
                    line.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), alphas[k])
                    c.drawPath(crest, line)
                }
            }
        }
        if (Fx.on(context)) postInvalidateOnAnimation()
    }
}
