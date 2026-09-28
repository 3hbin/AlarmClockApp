package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Loading tự nhận diện hãng máy (Canvas — không lấy được loader gốc OEM):
 * Samsung → pulse 1 chấm · Google/Pixel → 4 chấm orbit
 * Xiaomi → wave · Oppo/Realme/OnePlus → 3 chấm quay
 * Huawei/Honor → vòng cung · Vivo → staggered · LG → 3 chấm ngang
 * Motorola → 4 chấm quay · Sony → ink drop · khác → Google orbit
 */
class LoadingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Style {
        /** 1 chấm xanh scale — giống Circle Loader Samsung */
        SAMSUNG_PULSE,
        /** 4 chấm quay, 1 cyan — kiểu Material / Google */
        GOOGLE_ORBIT,
        WAVE_DOTS,
        STAGGERED_DOTS_WAVE,
        THREE_ROTATING_DOTS,
        FOUR_ROTATING_DOTS,
        BOUNCING_BALL,
        INK_DROP,
        DISCRETE_CIRCULAR,
        HORIZONTAL_DOTS
    }

    var style: Style = brandDefaultStyle()
        set(value) {
            field = value
            invalidate()
        }

    /** Xanh Samsung / Material blue */
    var animColor: Int = 0xFF2196F3.toInt()
        set(value) {
            field = value
            paint.color = value
            paint2.color = value
            invalidate()
        }

    /** Cyan nổi bật trên orbit Google */
    var accentColor: Int = 0xFF00E5C0.toInt()
        set(value) {
            field = value
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

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        // Mặc định theo hãng máy — không dính logo
        applyBrandDefault()
    }

    /** Gọi khi muốn reset style theo hãng máy hiện tại. */
    fun applyBrandDefault() {
        style = brandDefaultStyle()
        // Màu theo hãng (gần palette UI gốc)
        when (detectBrand()) {
            Brand.SAMSUNG -> {
                animColor = 0xFF2196F3.toInt()
                accentColor = 0xFF2196F3.toInt()
            }
            Brand.GOOGLE -> {
                animColor = 0xFF4285F4.toInt()
                accentColor = 0xFF34A853.toInt()
            }
            Brand.XIAOMI -> {
                animColor = 0xFFFF6900.toInt()
                accentColor = 0xFFFF6900.toInt()
            }
            Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> {
                animColor = 0xFF00C853.toInt()
                accentColor = 0xFF00C853.toInt()
            }
            Brand.HUAWEI, Brand.HONOR -> {
                animColor = 0xFFCF0A2C.toInt()
                accentColor = 0xFFCF0A2C.toInt()
            }
            Brand.VIVO -> {
                animColor = 0xFF415FFF.toInt()
                accentColor = 0xFF415FFF.toInt()
            }
            Brand.LG -> {
                animColor = 0xFFA50034.toInt()
                accentColor = 0xFFA50034.toInt()
            }
            Brand.MOTOROLA -> {
                animColor = 0xFFE1140A.toInt()
                accentColor = 0xFFE1140A.toInt()
            }
            Brand.SONY -> {
                animColor = 0xFF000000.toInt()
                accentColor = 0xFF000000.toInt()
            }
            else -> {
                animColor = 0xFF3F51B5.toInt()
                accentColor = 0xFF00E5C0.toInt()
            }
        }
    }

    private enum class Brand {
        SAMSUNG, GOOGLE, XIAOMI, OPPO, REALME, ONEPLUS,
        HUAWEI, HONOR, VIVO, LG, MOTOROLA, SONY, OTHER
    }

    private fun detectBrand(): Brand {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        val b = (Build.BRAND ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val all = "$m $b $model"
        return when {
            "samsung" in all -> Brand.SAMSUNG
            "google" in all || "pixel" in all -> Brand.GOOGLE
            "xiaomi" in all || "redmi" in all || "poco" in all || "miui" in all -> Brand.XIAOMI
            "realme" in all -> Brand.REALME
            "oneplus" in all || "oppo" in all -> Brand.OPPO
            "huawei" in all -> Brand.HUAWEI
            "honor" in all -> Brand.HONOR
            "vivo" in all || "iqoo" in all -> Brand.VIVO
            "lg" in all || "lge" in all -> Brand.LG
            "motorola" in all || "moto" in all -> Brand.MOTOROLA
            "sony" in all || "xperia" in all -> Brand.SONY
            else -> Brand.OTHER
        }
    }

    private fun brandDefaultStyle(): Style = when (detectBrand()) {
        Brand.SAMSUNG -> Style.SAMSUNG_PULSE
        Brand.GOOGLE -> Style.GOOGLE_ORBIT
        Brand.XIAOMI -> Style.WAVE_DOTS
        Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> Style.THREE_ROTATING_DOTS
        Brand.HUAWEI, Brand.HONOR -> Style.DISCRETE_CIRCULAR
        Brand.VIVO -> Style.STAGGERED_DOTS_WAVE
        Brand.LG -> Style.HORIZONTAL_DOTS
        Brand.MOTOROLA -> Style.FOUR_ROTATING_DOTS
        Brand.SONY -> Style.INK_DROP
        Brand.OTHER -> Style.GOOGLE_ORBIT
    }

    private fun isSamsungDevice(): Boolean = detectBrand() == Brand.SAMSUNG

    fun start() {
        if (anim.isRunning) return
        anim.start()
    }

    fun stop() {
        anim.cancel()
        progress = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        paint.color = animColor
        paint.alpha = 255
        when (style) {
            Style.SAMSUNG_PULSE -> drawSamsungPulse(canvas, cx, cy, size)
            Style.GOOGLE_ORBIT -> drawGoogleOrbit(canvas, cx, cy, size)
            Style.WAVE_DOTS, Style.STAGGERED_DOTS_WAVE -> drawStaggeredWave(canvas, cx, cy, size)
            Style.THREE_ROTATING_DOTS -> drawRotatingDots(canvas, cx, cy, size, 3)
            Style.FOUR_ROTATING_DOTS -> drawRotatingDots(canvas, cx, cy, size, 4)
            Style.BOUNCING_BALL -> drawBouncingBall(canvas, cx, cy, size)
            Style.INK_DROP -> drawInkDrop(canvas, cx, cy, size)
            Style.DISCRETE_CIRCULAR -> drawDiscreteCircular(canvas, cx, cy, size)
            Style.HORIZONTAL_DOTS -> drawHorizontalDots(canvas, cx, cy, size)
        }
    }

    /** Video 1: 1 chấm xanh giữa — scale pulse (chỉ Samsung). Không logo. */
    private fun drawSamsungPulse(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val base = size * 0.12f
        // 0→1→0 mượt
        val wave = (sin(progress * 2 * PI).toFloat() + 1f) / 2f
        val scale = 0.55f + 0.55f * wave
        val r = base * scale
        // halo mờ
        paint.alpha = (40 + 50 * (1f - wave)).toInt().coerceIn(30, 90)
        canvas.drawCircle(cx, cy, r * 1.85f, paint)
        // chấm chính
        paint.alpha = 255
        paint.color = animColor
        canvas.drawCircle(cx, cy, r, paint)
    }

    /** Video 2: 4 chấm quỹ đạo, 1 cyan dẫn — máy khác. Không logo. */
    private fun drawGoogleOrbit(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val radius = size * 0.28f
        val r = size * 0.09f
        val angle0 = progress * 2 * PI
        val lead = ((progress * 4f).toInt()) % 4
        for (i in 0 until 4) {
            val a = angle0 + i * (PI / 2.0)
            val x = cx + cos(a).toFloat() * radius
            val y = cy + sin(a).toFloat() * radius
            if (i == lead) {
                paint.color = accentColor
                paint.alpha = 255
                canvas.drawCircle(x, y, r * 1.08f, paint)
            } else {
                paint.color = animColor
                paint.alpha = 230
                canvas.drawCircle(x, y, r * 0.92f, paint)
            }
        }
        paint.alpha = 255
        paint.color = animColor
    }

    private fun drawStaggeredWave(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val n = 5
        val r = size * 0.07f
        val gap = size * 0.16f
        val startX = cx - (n - 1) * gap / 2f
        for (i in 0 until n) {
            val phase = (progress + i * 0.12f) % 1f
            val s = sin(phase * 2 * PI).toFloat()
            val y = cy + s * size * 0.18f
            paint.alpha = (140 + 100 * ((s + 1f) / 2f)).toInt()
            canvas.drawCircle(startX + i * gap, y, r, paint)
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
        val t = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
        val ease = 1f - (1f - t) * (1f - t)
        val y = cy + size * 0.22f - ease * size * 0.45f
        paint.alpha = (60 * (1f - ease * 0.7f)).toInt().coerceIn(20, 80)
        canvas.drawOval(
            cx - r * (1.2f - 0.4f * ease),
            cy + size * 0.28f,
            cx + r * (1.2f - 0.4f * ease),
            cy + size * 0.34f,
            paint
        )
        paint.alpha = 255
        canvas.drawCircle(cx, y, r, paint)
    }

    private fun drawInkDrop(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val maxR = size * 0.35f
        val r = maxR * progress
        paint.alpha = ((1f - progress) * 220).toInt().coerceIn(0, 220)
        canvas.drawCircle(cx, cy, r.coerceAtLeast(2f), paint)
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
