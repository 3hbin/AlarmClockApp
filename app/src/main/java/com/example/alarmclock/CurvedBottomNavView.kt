package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bottom nav 2 kiểu (Cài đặt):
 * - CURVED: Android mặc định — thanh trắng + lõm cong + nút tròn nổi
 * - LIQUID_GLASS: kiểu iOS 26 liquid glass (adaptive_platform_ui) — thanh kính mờ, pill chọn
 */
class CurvedBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class Style { CURVED, LIQUID_GLASS }

    data class Item(val id: Int, val emoji: String, val label: String)

    private val items = mutableListOf<Item>()
    private var selectedIndex = 0
    private var animX = 0f
    private var onItemSelected: ((Int, Item) -> Unit)? = null
    var navStyle: Style = Style.CURVED
        set(value) {
            if (field == value) return
            field = value
            applyStyleChrome()
            invalidate()
            requestLayout()
        }

    private val barColor = Color.WHITE
    private val gapColor = 0xFFF5F5FA.toInt()
    private val labelActive = 0xFF3F51B5.toInt()
    private val labelInactive = 0xFFB0BEC5.toInt()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = barColor
        setShadowLayer(16f, 0f, -2f, 0x28000000)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(12f, 0f, 4f, 0x40000000)
    }
    private val accentRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = 0xFF3F51B5.toInt()
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE6F8FAFF.toInt()
    }
    private val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * resources.displayMetrics.density
        color = 0x66FFFFFF
    }
    private val glassHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x553F51B5
    }
    private val path = Path()
    private val rect = RectF()
    private val iconViews = mutableListOf<TextView>()
    private val labelViews = mutableListOf<TextView>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val flatBarH get() = if (navStyle == Style.LIQUID_GLASS) dp(58f) else dp(52f)
    private val dip get() = dp(22f)
    private val btnR get() = dp(26f)

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        clipChildren = false
        clipToPadding = false
        applyStyleChrome()
    }

    private fun applyStyleChrome() {
        when (navStyle) {
            Style.CURVED -> {
                setBackgroundColor(gapColor)
                barPaint.color = barColor
                barPaint.setShadowLayer(16f, 0f, -2f, 0x28000000)
            }
            Style.LIQUID_GLASS -> {
                setBackgroundColor(Color.TRANSPARENT)
                barPaint.clearShadowLayer()
            }
        }
    }

    fun setItems(list: List<Item>, initial: Int = 0) {
        items.clear()
        items.addAll(list)
        selectedIndex = initial.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        buildItems()
        post {
            animX = centerX(selectedIndex)
            invalidate()
            styleItems(false)
        }
    }

    fun setOnItemSelectedListener(l: (Int, Item) -> Unit) {
        onItemSelected = l
    }

    fun selectIndex(index: Int, animate: Boolean = true) {
        if (index !in items.indices) return
        selectedIndex = index
        val target = centerX(index)
        if (animate && width > 0) {
            ValueAnimator.ofFloat(animX, target).apply {
                duration = if (navStyle == Style.LIQUID_GLASS) 320 else 450
                interpolator = DecelerateInterpolator(1.6f)
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
        styleItems(animate)
    }

    private fun buildItems() {
        removeAllViews()
        iconViews.clear()
        labelViews.clear()

        val topPad = if (navStyle == Style.LIQUID_GLASS) dp(10f).toInt() else (btnR * 0.85f).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, topPad, 0, dp(6f).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        items.forEachIndexed { index, item ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val changed = selectedIndex != index
                    selectIndex(index, animate = true)
                    postDelayed({
                        onItemSelected?.invoke(index, item)
                    }, if (changed) 260L else 0L)
                }
            }

            val icon = TextView(context).apply {
                text = item.emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(28f).toInt()
                )
            }
            val label = TextView(context).apply {
                text = item.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(labelInactive)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            col.addView(icon)
            col.addView(label)
            iconViews.add(icon)
            labelViews.add(label)
            row.addView(col)
        }
        addView(row)
    }

    private fun centerX(index: Int): Float {
        if (items.isEmpty() || width == 0) return width / 2f
        val slot = width.toFloat() / items.size
        return slot * index + slot / 2f
    }

    private fun styleItems(animated: Boolean) {
        iconViews.forEachIndexed { i, tv ->
            val selected = i == selectedIndex
            tv.animate().cancel()
            if (navStyle == Style.LIQUID_GLASS) {
                // Liquid: icon luôn hiện; selected scale nhẹ
                tv.alpha = 1f
                if (animated) {
                    tv.animate()
                        .scaleX(if (selected) 1.12f else 1f)
                        .scaleY(if (selected) 1.12f else 1f)
                        .setDuration(200)
                        .start()
                } else {
                    tv.scaleX = if (selected) 1.12f else 1f
                    tv.scaleY = if (selected) 1.12f else 1f
                }
            } else {
                tv.scaleX = 1f
                tv.scaleY = 1f
                if (animated) {
                    tv.animate().alpha(if (selected) 0f else 1f).setDuration(180).start()
                } else {
                    tv.alpha = if (selected) 0f else 1f
                }
            }
            labelViews.getOrNull(i)?.apply {
                setTextColor(if (selected) labelActive else labelInactive)
                paint.isFakeBoldText = selected
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        animX = centerX(selectedIndex)
        // glass highlight gradient
        glassHighlight.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x55FFFFFF, 0x11FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty() || width == 0) return
        when (navStyle) {
            Style.CURVED -> drawCurved(canvas)
            Style.LIQUID_GLASS -> drawLiquidGlass(canvas)
        }
    }

    private fun drawCurved(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - flatBarH
        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val half = btnR * 1.2f

        path.reset()
        path.moveTo(0f, top)
        path.lineTo((cx - half * 2.1f).coerceAtLeast(0f), top)
        path.cubicTo(
            cx - half * 1.4f, top,
            cx - half * 0.9f, top + dip,
            cx, top + dip
        )
        path.cubicTo(
            cx + half * 0.9f, top + dip,
            cx + half * 1.4f, top,
            (cx + half * 2.1f).coerceAtMost(w), top
        )
        path.lineTo(w, top)
        path.lineTo(w, h + 20f)
        path.lineTo(0f, h + 20f)
        path.close()
        canvas.drawPath(path, barPaint)

        val cy = top
        canvas.drawCircle(cx, cy, btnR, buttonPaint)
        canvas.drawCircle(cx, cy, btnR - dp(1.5f), accentRing)

        if (selectedIndex in items.indices) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = dp(20f)
            }
            val emoji = items[selectedIndex].emoji
            val fm = tp.fontMetrics
            canvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
        }
    }

    /**
     * Liquid Glass Material 3 (Android) — full-width kính mờ, không capsule iOS lơ lửng.
     */
    private fun drawLiquidGlass(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - flatBarH

        // Full-width frosted bar
        rect.set(0f, top, w, h + dp(8f))
        glassPaint.color = 0xF2F5F7FF.toInt()
        glassPaint.setShadowLayer(12f, 0f, -2f, 0x22000000)
        canvas.drawRect(rect, glassPaint)
        glassPaint.clearShadowLayer()

        // Top edge + sheen
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
            color = 0x66FFFFFF
        }
        canvas.drawLine(0f, top + 0.5f, w, top + 0.5f, edge)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, top + dp(18f),
                intArrayOf(0x33FFFFFF, 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, top, w, top + dp(18f), sheen)

        // M3 active indicator pill
        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val pillW = dp(56f)
        val pillH = dp(28f)
        val pillTop = top + dp(8f)
        rect.set(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH)
        pillPaint.color = 0x333F51B5
        canvas.drawRoundRect(rect, dp(14f), dp(14f), pillPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val total = if (navStyle == Style.LIQUID_GLASS) {
            (flatBarH + dp(8f)).toInt()
        } else {
            (flatBarH + btnR + dp(6f)).toInt()
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY))
    }
}
