package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Curved bottom navigation bar kiểu Flutter curved_navigation_bar:
 * - Thanh cong lõm tại mục đang chọn
 * - Icon nổi lên trong vòng tròn
 */
class CurvedBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class Item(val id: Int, val emoji: String, val label: String)

    private val items = mutableListOf<Item>()
    private var selectedIndex = 0
    private var animX = 0f
    private var onItemSelected: ((Int, Item) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, -4f, 0x33000000)
    }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.purple_500)
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val itemViews = mutableListOf<TextView>()

    private val barHeight: Float
        get() = 64f * resources.displayMetrics.density
    private val curveDepth: Float
        get() = 28f * resources.displayMetrics.density
    private val circleRadius: Float
        get() = 28f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        clipChildren = false
        clipToPadding = false
    }

    fun setItems(list: List<Item>, initial: Int = 0) {
        items.clear()
        items.addAll(list)
        selectedIndex = initial.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        buildItemViews()
        post {
            animX = centerXFor(selectedIndex)
            invalidate()
            updateItemStyles()
        }
    }

    fun setOnItemSelectedListener(listener: (Int, Item) -> Unit) {
        onItemSelected = listener
    }

    fun selectIndex(index: Int, animate: Boolean = true) {
        if (index !in items.indices) return
        selectedIndex = index
        val target = centerXFor(index)
        if (animate) {
            ValueAnimator.ofFloat(animX, target).apply {
                duration = 320
                interpolator = OvershootInterpolator(1.2f)
                addUpdateListener {
                    animX = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animX = target
            invalidate()
        }
        updateItemStyles()
    }

    private fun buildItemViews() {
        removeAllViews()
        itemViews.clear()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            gravity = Gravity.BOTTOM
        }
        items.forEachIndexed { index, item ->
            val tv = TextView(context).apply {
                text = item.emoji
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    if (selectedIndex != index) {
                        selectIndex(index)
                        onItemSelected?.invoke(index, item)
                    } else {
                        onItemSelected?.invoke(index, item)
                    }
                }
            }
            itemViews.add(tv)
            row.addView(tv)
        }
        addView(row)
        // padding top for floating circle
        setPadding(0, (curveDepth / 2).toInt(), 0, 0)
    }

    private fun centerXFor(index: Int): Float {
        if (items.isEmpty() || width == 0) return width / 2f
        val slot = width.toFloat() / items.size
        return slot * index + slot / 2f
    }

    private fun updateItemStyles() {
        itemViews.forEachIndexed { i, tv ->
            tv.alpha = if (i == selectedIndex) 0f else 1f // selected drawn on circle
            tv.translationY = if (i == selectedIndex) -curveDepth * 0.3f else 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        animX = centerXFor(selectedIndex)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val top = curveDepth
        val cx = animX
        val notchR = circleRadius + 10f * resources.displayMetrics.density

        path.reset()
        path.moveTo(0f, top)
        // line to left of notch
        path.lineTo((cx - notchR).coerceAtLeast(0f), top)
        // concave curve (notch)
        path.quadTo(cx - notchR * 0.55f, top, cx - notchR * 0.45f, top + curveDepth * 0.35f)
        path.quadTo(cx, top + curveDepth * 1.05f, cx + notchR * 0.45f, top + curveDepth * 0.35f)
        path.quadTo(cx + notchR * 0.55f, top, (cx + notchR).coerceAtMost(w), top)
        path.lineTo(w, top)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        canvas.drawPath(path, barPaint)

        // floating circle
        val cy = top - 4f * resources.displayMetrics.density
        canvas.drawCircle(cx, cy, circleRadius, circlePaint)

        // selected emoji on circle
        if (selectedIndex in items.indices) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = 22f * resources.displayMetrics.scaledDensity
            }
            val emoji = items[selectedIndex].emoji
            val fm = textPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(emoji, cx, textY, textPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (barHeight + curveDepth).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}
