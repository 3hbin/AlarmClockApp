package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Flutter `load_switch` tương đương — switch + loading spinner trên thumb.
 * Canvas thuần → nhìn giống nhau trên Samsung / Oppo / Huawei / Pixel / LG.
 */
class LoadSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(view: LoadSwitchView, isChecked: Boolean)
    }

    private var checked = false
    private var loading = false
    private var switchEnabled = true
    private var thumbPos = 0f
    private var spinAngle = 0f
    private var listener: OnCheckedChangeListener? = null

    var colorOn: Int = 0xFF3F51B5.toInt()
    var colorOff: Int = 0xFFBDBDBD.toInt()
    var thumbColor: Int = 0xFFFFFFFF.toInt()
    var spinColor: Int = 0xFF3F51B5.toInt()

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // shadow needs software layer when drawn
    }
    private val spinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density / 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val trackRect = RectF()
    private val spinRect = RectF()

    private val thumbAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 280
        interpolator = OvershootInterpolator(1.15f)
        addUpdateListener {
            thumbPos = it.animatedValue as Float
            invalidate()
        }
    }

    private val spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 750
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spinAngle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        // hardware layer ok without shadow; we draw soft oval shadow manually
    }

    val isChecked: Boolean get() = checked

    fun setOnCheckedChangeListener(l: OnCheckedChangeListener?) {
        listener = l
    }

    /** Bind list: không fire listener. */
    fun setCheckedSilent(value: Boolean) {
        checked = value
        thumbAnim.cancel()
        thumbPos = if (value) 1f else 0f
        invalidate()
    }

    fun setChecked(value: Boolean, animate: Boolean = true, notify: Boolean = true) {
        if (checked == value) return
        checked = value
        if (animate) {
            thumbAnim.cancel()
            thumbAnim.setFloatValues(thumbPos, if (value) 1f else 0f)
            thumbAnim.start()
        } else {
            thumbAnim.cancel()
            thumbPos = if (value) 1f else 0f
            invalidate()
        }
        if (notify) listener?.onCheckedChanged(this, value)
    }

    fun setLoading(value: Boolean) {
        if (loading == value) return
        loading = value
        if (value) {
            if (!spinAnim.isRunning) spinAnim.start()
        } else {
            spinAnim.cancel()
        }
        invalidate()
    }

    fun isLoading(): Boolean = loading

    fun setSwitchEnabled(value: Boolean) {
        switchEnabled = value
        alpha = if (value) 1f else 0.45f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize((52 * density).toInt(), widthMeasureSpec)
        val h = resolveSize((32 * density).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        trackRect.set(0f, 0f, w, h)

        val onA = thumbPos
        val r = ((colorOff shr 16) and 0xFF) * (1 - onA) + ((colorOn shr 16) and 0xFF) * onA
        val g = ((colorOff shr 8) and 0xFF) * (1 - onA) + ((colorOn shr 8) and 0xFF) * onA
        val b = (colorOff and 0xFF) * (1 - onA) + (colorOn and 0xFF) * onA
        trackPaint.color = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        val pad = 3f * density
        val thumbR = radius - pad
        val minX = pad + thumbR
        val maxX = w - pad - thumbR
        val tx = minX + (maxX - minX) * thumbPos
        val ty = h / 2f

        // soft shadow under thumb
        thumbPaint.color = 0x33000000
        canvas.drawCircle(tx, ty + 1.2f * density, thumbR, thumbPaint)
        thumbPaint.color = thumbColor
        canvas.drawCircle(tx, ty, thumbR, thumbPaint)

        if (loading) {
            spinPaint.color = if (thumbPos > 0.5f) 0xFFFFFFFF.toInt() else spinColor
            val sr = thumbR * 0.52f
            spinRect.set(tx - sr, ty - sr, tx + sr, ty + sr)
            canvas.drawArc(spinRect, spinAngle, 280f, false, spinPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!switchEnabled) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!switchEnabled || loading) return true
        setChecked(!checked, animate = true, notify = true)
        return true
    }

    override fun onDetachedFromWindow() {
        thumbAnim.cancel()
        spinAnim.cancel()
        super.onDetachedFromWindow()
    }
}
