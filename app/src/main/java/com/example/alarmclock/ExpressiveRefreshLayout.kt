package com.example.alarmclock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.loadingindicator.LoadingIndicator
import kotlin.math.exp
import kotlin.math.min

/**
 * Kéo xuống để làm mới kiểu Material 3 Expressive.
 *
 * Hiệu ứng (theo video "Loading Indicator Overview"):
 *  1. Kéo xuống: nội dung bị đẩy xuống theo lực cản (rubber-band), khung tròn chứa
 *     LoadingIndicator hiện dần (phóng to + mờ → rõ) giữa khoảng trống, hình xoay theo ngón tay.
 *  2. Vượt ngưỡng: rung nhẹ một lần.
 *  3. Thả tay: nội dung nảy về vị trí "đang làm mới", LoadingIndicator biến hình
 *     liên tục (morph nhiều hình) cho tới khi isRefreshing = false.
 *  4. Xong: khung thu nhỏ + mờ dần, nội dung trượt về chỗ cũ.
 *
 * Dùng thay SwipeRefreshLayout, cùng API: isRefreshing, setOnRefreshListener,
 * setColorSchemeColors, setProgressBackgroundColorSchemeColor.
 * Chỉ nên có đúng 1 view con (nội dung cuộn được).
 */
class ExpressiveRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val containerSize = (48 * density).toInt()
    private val shapeSize = (28 * density).toInt()
    private val triggerDistance = 72 * density
    private val refreshOffset = 64 * density
    private val maxOffset = 120 * density
    private val minRefreshMs = 700L

    private var listener: (() -> Unit)? = null
    private var target: View? = null
    private var overlay: FrameLayout? = null
    private var indicator: LoadingIndicator? = null

    private var indicatorColor: Int =
        MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF6750A4.toInt())
    private var containerColor: Int =
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFEADDFF.toInt())
    private val containerBg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(containerColor)
    }

    private var offset = 0f
    private var refreshing = false
    private var refreshStartedAt = 0L
    private var pendingStop: Runnable? = null

    private var dragging = false
    private var passedTrigger = false
    private var activePointerId = -1
    private var downY = 0f
    private var pullStartY = 0f
    private var anim: ValueAnimator? = null

    var isRefreshing: Boolean
        get() = refreshing && pendingStop == null
        set(value) = setRefreshingInternal(value)

    fun setOnRefreshListener(block: (() -> Unit)?) {
        listener = block
    }

    /** Màu hình biến dạng (lấy màu đầu tiên). */
    fun setColorSchemeColors(vararg colors: Int) {
        if (colors.isEmpty()) return
        indicatorColor = colors[0]
        indicator?.setIndicatorColor(indicatorColor)
    }

    /** Màu khung tròn phía sau hình biến dạng. */
    fun setProgressBackgroundColorSchemeColor(color: Int) {
        containerColor = color
        containerBg.setColor(color)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        if (overlay != null) return
        target = if (childCount > 0) getChildAt(0) else null
        buildOverlay()
    }

    private fun buildOverlay() {
        val shapeColor = indicatorColor
        val ind = LoadingIndicator(context).apply {
            setIndicatorColor(shapeColor)
            setIndicatorSize(shapeSize)
            setContainerWidth(containerSize)
            setContainerHeight(containerSize)
        }
        val box = FrameLayout(context).apply {
            background = containerBg
            visibility = View.GONE
            addView(ind, LayoutParams(containerSize, containerSize, Gravity.CENTER))
        }
        indicator = ind
        overlay = box
        addView(box, LayoutParams(containerSize, containerSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    // ---------------------------------------------------------------- touch

    private fun canScrollUp(v: View?): Boolean {
        if (v == null) return false
        if (v.canScrollVertically(-1)) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val c = v.getChildAt(i)
                if (c === overlay) continue
                if (c.visibility == View.VISIBLE && canScrollUp(c)) return true
            }
        }
        return false
    }

    override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
        // Không tự khóa chính mình để vẫn chặn được khi danh sách vừa cuộn về đầu.
        parent?.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || refreshing || target == null) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downY = ev.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) return false
                val idx = ev.findPointerIndex(activePointerId)
                if (idx < 0) return false
                val y = ev.getY(idx)
                if (canScrollUp(target)) {
                    downY = y
                    return false
                }
                maybeStartDrag(y)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.getPointerId(ev.actionIndex) == activePointerId) activePointerId = -1
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                activePointerId = -1
            }
        }
        return dragging
    }

    private fun maybeStartDrag(y: Float) {
        if (!dragging && y - downY > touchSlop) {
            dragging = true
            passedTrigger = false
            pullStartY = downY + touchSlop
            anim?.cancel()
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled || refreshing) return false
                activePointerId = ev.getPointerId(0)
                downY = ev.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = ev.findPointerIndex(activePointerId)
                if (idx < 0) return false
                val y = ev.getY(idx)
                if (!dragging) maybeStartDrag(y)
                if (dragging) {
                    val raw = (y - pullStartY).coerceAtLeast(0f)
                    applyOffset(rubber(raw))
                    val crossed = offset >= triggerDistance
                    if (crossed && !passedTrigger) {
                        passedTrigger = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } else if (!crossed && passedTrigger) {
                        passedTrigger = false
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.getPointerId(ev.actionIndex) == activePointerId) endDrag(false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                endDrag(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                endDrag(true)
                return true
            }
        }
        return true
    }

    private fun endDrag(cancelled: Boolean) {
        val was = dragging
        dragging = false
        activePointerId = -1
        if (!was) return
        if (!cancelled && offset >= triggerDistance) startRefreshing(true)
        else animateOffsetTo(0f, 220L, false)
    }

    /** Lực cản: càng kéo càng nặng, tối đa maxOffset. */
    private fun rubber(raw: Float): Float = maxOffset * (1f - exp(-raw / (maxOffset * 1.3f)))

    // ------------------------------------------------------------ refreshing

    private fun setRefreshingInternal(value: Boolean) {
        if (value) {
            cancelPendingStop()
            if (!refreshing) startRefreshing(false)
        } else if (refreshing && pendingStop == null) {
            stopRefreshing()
        }
    }

    private fun startRefreshing(notify: Boolean) {
        refreshing = true
        refreshStartedAt = SystemClock.uptimeMillis()
        animateOffsetTo(refreshOffset, 260L, true)
        if (notify) listener?.invoke()
    }

    /** Giữ hiệu ứng tối thiểu 700ms để người dùng kịp thấy dù dữ liệu tải xong ngay. */
    private fun stopRefreshing() {
        val remain = minRefreshMs - (SystemClock.uptimeMillis() - refreshStartedAt)
        val r = Runnable { finishRefresh() }
        pendingStop = r
        if (remain > 0) postDelayed(r, remain) else r.run()
    }

    private fun finishRefresh() {
        pendingStop = null
        refreshing = false
        animateOffsetTo(0f, 240L, false)
    }

    private fun cancelPendingStop() {
        pendingStop?.let { removeCallbacks(it) }
        pendingStop = null
    }

    // -------------------------------------------------------------- visuals

    private fun animateOffsetTo(to: Float, duration: Long, overshoot: Boolean) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(offset, to)
        a.duration = duration
        a.interpolator = if (overshoot) OvershootInterpolator(1.1f) else DecelerateInterpolator(1.6f)
        a.addUpdateListener { applyOffset(it.animatedValue as Float) }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (anim === animation) anim = null
            }
        })
        anim = a
        a.start()
    }

    private fun applyOffset(raw: Float) {
        val o = raw.coerceAtLeast(0f)
        offset = o
        target?.translationY = o
        val box = overlay ?: return
        if (o <= 0.5f && !refreshing) {
            if (box.visibility != View.GONE) box.visibility = View.GONE
            return
        }
        if (box.visibility != View.VISIBLE) box.visibility = View.VISIBLE
        val p = min(o / refreshOffset, 1f)
        val s = 0.3f + 0.7f * p
        box.scaleX = s
        box.scaleY = s
        box.alpha = min(o / (refreshOffset * 0.5f), 1f)
        box.translationY = o / 2f - containerSize / 2f
        indicator?.rotation = o / density * 3f
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        cancelPendingStop()
        refreshing = false
        dragging = false
        applyOffset(0f)
        super.onDetachedFromWindow()
    }
}
