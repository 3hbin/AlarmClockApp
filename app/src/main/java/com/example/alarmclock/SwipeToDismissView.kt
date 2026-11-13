package com.example.alarmclock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.sin

/**
 * Thanh "Vuốt để tắt": kéo núm đỏ sang phải hết cỡ để xác nhận.
 * - Dùng thật ở màn hình báo thức (onComplete).
 * - Chế độ demo (setDemo(true)): tự chạy mẫu, không nhận chạm — dùng để xem trước trong Cài đặt.
 */
class SwipeToDismissView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onComplete: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val trackH = (58 * dp).toInt()
    private val pad = (4 * dp).toInt()
    private val thumbSize = trackH - 2 * pad

    private val track = View(context)
    private val fill = View(context)
    private val label = TextView(context)
    private val thumb = FrameLayout(context)

    private var demo = false
    private var maxX = 0f
    private var downX = 0f
    private var startTx = 0f
    private var dragging = false
    private var done = false
    private var loop: ValueAnimator? = null
    private var settle: ValueAnimator? = null
    private val resetRunnable = Runnable { reset() }

    init {
        clipChildren = false
        clipToPadding = false
        track.background = GradientDrawable().apply {
            cornerRadius = trackH / 2f
            setColor(0x33EF4444)
            setStroke((1.5f * dp).toInt(), 0x99EF4444.toInt())
        }
        fill.background = GradientDrawable().apply {
            cornerRadius = trackH / 2f
            setColor(0x66EF4444)
        }
        label.apply {
            text = Lang.t(context, "Vuốt để tắt", "Swipe to dismiss")
            gravity = Gravity.CENTER
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(thumbSize + 2 * pad, 0, (16 * dp).toInt(), 0)
        }
        thumb.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFEF4444.toInt())
            }
            elevation = 4 * dp
            addView(
                ImageView(context).apply { setImageResource(R.drawable.ic_swipe_arrow) },
                LayoutParams((30 * dp).toInt(), (30 * dp).toInt(), Gravity.CENTER)
            )
        }
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, trackH))
        addView(fill, LayoutParams(thumbSize + 2 * pad, trackH))
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, trackH))
        addView(thumb, LayoutParams(thumbSize, thumbSize).apply {
            leftMargin = pad
            topMargin = pad
        })
    }

    fun setLabel(text: String) {
        if (label.text.toString() != text) label.text = text
    }

    fun setDemo(value: Boolean) {
        demo = value
        if (isAttachedToWindow) startLoop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        maxX = (w - thumbSize - 2 * pad).toFloat().coerceAtLeast(0f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        loop?.cancel()
        settle?.cancel()
        removeCallbacks(resetRunnable)
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------- hoạt ảnh nền

    private fun startLoop() {
        loop?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        if (demo) {
            // Mẫu: giữ yên → kéo hết cỡ → giữ → về lại
            a.duration = 3000
            a.interpolator = LinearInterpolator()
            a.addUpdateListener {
                if (maxX <= 0f) return@addUpdateListener
                val t = it.animatedValue as Float
                val p = when {
                    t < 0.15f -> 0f
                    t < 0.65f -> smooth((t - 0.15f) / 0.5f) * 0.94f
                    t < 0.8f -> 0.94f
                    else -> (1f - smooth((t - 0.8f) / 0.2f)) * 0.94f
                }
                setTx(p * maxX)
            }
        } else {
            // Thật: núm nhích nhẹ định kỳ để gợi ý "vuốt sang phải"
            a.duration = 2600
            a.interpolator = LinearInterpolator()
            a.addUpdateListener {
                if (dragging || done) return@addUpdateListener
                val t = it.animatedValue as Float
                val nudge = if (t < 0.22f) sin((t / 0.22f) * PI).toFloat() * 14 * dp else 0f
                setTx(nudge)
            }
        }
        a.repeatCount = ValueAnimator.INFINITE
        loop = a
        a.start()
    }

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // ---------------------------------------------------------------- kéo

    private fun setTx(tx: Float) {
        thumb.translationX = tx
        (fill.layoutParams as LayoutParams).width = (tx + thumbSize + 2 * pad).toInt()
        fill.requestLayout()
        label.alpha = if (maxX > 0f) (1f - tx / maxX * 1.5f).coerceIn(0f, 1f) else 1f
    }

    private fun animateTo(tx: Float, dur: Long, overshoot: Boolean, end: (() -> Unit)? = null) {
        settle?.cancel()
        val a = ValueAnimator.ofFloat(thumb.translationX, tx)
        a.duration = dur
        a.interpolator = if (overshoot) OvershootInterpolator(1.4f) else DecelerateInterpolator()
        a.addUpdateListener { setTx(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            private var canceled = false
            override fun onAnimationCancel(animation: Animator) { canceled = true }
            override fun onAnimationEnd(animation: Animator) { if (!canceled) end?.invoke() }
        })
        settle = a
        a.start()
    }

    private fun reset() {
        dragging = false
        animateTo(0f, 260, true) { done = false }
    }

    private fun complete() {
        done = true
        animateTo(maxX, 120, false)
        try {
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS
            )
        } catch (_: Exception) {}
        onComplete?.invoke()
        // Nếu màn hình không đóng ngay (cần thử thách, bị chặn…) thì trả núm về chỗ cũ
        postDelayed(resetRunnable, 900)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (demo) return false
        if (done) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                settle?.cancel()
                removeCallbacks(resetRunnable)
                dragging = true
                downX = e.x
                startTx = thumb.translationX
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                setTx((startTx + e.x - downX).coerceIn(0f, maxX))
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                if (e.actionMasked == MotionEvent.ACTION_UP && maxX > 0f &&
                    thumb.translationX >= maxX * 0.85f
                ) complete() else reset()
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
