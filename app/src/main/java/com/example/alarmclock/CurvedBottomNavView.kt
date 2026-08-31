package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bottom nav 3 kiểu:
 * - CURVED: Android cong + nút tròn nổi
 * - PERSISTENT: persistent_bottom_nav_bar — thanh phẳng, icon+label
 * - GOOGLE: google_nav_bar — pill mở rộng icon+chữ
 */
class CurvedBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class Style { CURVED, PERSISTENT, GOOGLE }

    data class Item(val id: Int, val emoji: String, val label: String, val iconRes: Int = 0)

    private val items = mutableListOf<Item>()
    private var selectedIndex = 0
    private var animX = 0f
    private var pillExpand = 1f
    private var onItemSelected: ((Int, Item) -> Unit)? = null
    private var onItemLongClick: ((Int, Item) -> Boolean)? = null
    var navStyle: Style = Style.CURVED
        set(value) {
            if (field == value) return
            field = value
            applyStyleChrome()
            if (items.isNotEmpty()) {
                buildItems()
                post {
                    animX = centerX(selectedIndex)
                    styleItems(false)
                    invalidate()
                    requestLayout()
                }
            } else {
                invalidate()
                requestLayout()
            }
        }

    private val barColor = Color.WHITE
    private val gapColor = 0xFFF5F5FA.toInt()
    private val accent = 0xFF3F51B5.toInt()
    private val labelActive = 0xFF3F51B5.toInt()
    private val labelInactive = 0xFF90A4AE.toInt()

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
        color = accent
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1A3F51B5
    }
    private val topLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x14000000
    }
    private val path = Path()
    private val rect = RectF()
    private val iconViews = mutableListOf<TextView>()
    private val labelViews = mutableListOf<TextView>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val flatBarH get() = when (navStyle) {
        Style.GOOGLE -> dp(62f)
        Style.PERSISTENT -> dp(58f)
        else -> dp(52f)
    }
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
            Style.PERSISTENT, Style.GOOGLE -> {
                setBackgroundColor(Color.TRANSPARENT)
                barPaint.color = Color.WHITE
                barPaint.setShadowLayer(10f, 0f, -1f, 0x22000000)
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

    fun setOnItemLongClickListener(l: (Int, Item) -> Boolean) {
        onItemLongClick = l
    }

    fun selectIndex(index: Int, animate: Boolean = true) {
        if (index !in items.indices) return
        selectedIndex = index
        val target = centerX(index)
        if (animate && width > 0) {
            ValueAnimator.ofFloat(animX, target).apply {
                duration = when (navStyle) {
                    Style.GOOGLE -> 380L
                    Style.PERSISTENT -> 280L
                    else -> 450L
                }
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener {
                    animX = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            if (navStyle == Style.GOOGLE) {
                ValueAnimator.ofFloat(0.85f, 1f).apply {
                    duration = 320
                    addUpdateListener {
                        pillExpand = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            animX = target
            pillExpand = 1f
            invalidate()
        }
        styleItems(animate)
    }

    private fun buildItems() {
        removeAllViews()
        iconViews.clear()
        labelViews.clear()

        val topPad = when (navStyle) {
            Style.CURVED -> (btnR * 0.85f).toInt()
            Style.GOOGLE -> dp(8f).toInt()
            Style.PERSISTENT -> dp(6f).toInt()
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4f).toInt(), topPad, dp(4f).toInt(), dp(6f).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        items.forEachIndexed { index, item ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
                isLongClickable = true
                setOnLongClickListener {
                    onItemLongClick?.invoke(index, item) ?: false
                }
                setOnClickListener {
                    val changed = selectedIndex != index
                    selectIndex(index, animate = true)
                    postDelayed({
                        onItemSelected?.invoke(index, item)
                    }, if (changed) 220L else 0L)
                }
            }

            val icon: View = if (item.iconRes != 0) {
                android.widget.ImageView(context).apply {
                    setImageResource(item.iconRes)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(26f).toInt()
                    )
                    setColorFilter(
                        if (index == selectedIndex) accent else 0xFF6B7280.toInt(),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                    tag = "nav_icon"
                }
            } else {
                TextView(context).apply {
                    text = item.emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (navStyle == Style.GOOGLE) 17f else 18f)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(28f).toInt()
                    )
                }
            }
            val label = TextView(context).apply {
                text = item.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (navStyle == Style.GOOGLE) 10f else 9f)
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(labelInactive)
                visibility = if (navStyle == Style.GOOGLE) INVISIBLE else VISIBLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            col.addView(icon)
            col.addView(label)
            iconViews.add(icon)
            if (navStyle == Style.GOOGLE) label.visibility = INVISIBLE
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
            // Tint vector icons
            if (tv is android.widget.ImageView) {
                val c = if (selected) accent else 0xFF6B7280.toInt()
                tv.setColorFilter(c, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            when (navStyle) {
                Style.CURVED -> {
                    tv.scaleX = 1f
                    tv.scaleY = 1f
                    if (animated) {
                        tv.animate().alpha(if (selected) 0f else 1f).setDuration(180).start()
                    } else {
                        tv.alpha = if (selected) 0f else 1f
                    }
                    labelViews.getOrNull(i)?.apply {
                        visibility = VISIBLE
                        setTextColor(if (selected) labelActive else labelInactive)
                        paint.isFakeBoldText = selected
                    }
                }
                Style.PERSISTENT -> {
                    tv.alpha = 1f
                    val scale = if (selected) 1.15f else 1f
                    if (animated) {
                        tv.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
                    } else {
                        tv.scaleX = scale
                        tv.scaleY = scale
                    }
                    labelViews.getOrNull(i)?.apply {
                        visibility = VISIBLE
                        setTextColor(if (selected) labelActive else labelInactive)
                        paint.isFakeBoldText = selected
                        alpha = if (selected) 1f else 0.75f
                    }
                }
                Style.GOOGLE -> {
                    tv.alpha = if (selected) 0f else 1f
                    tv.scaleX = 1f
                    tv.scaleY = 1f
                    labelViews.getOrNull(i)?.visibility = INVISIBLE
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        animX = centerX(selectedIndex)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty() || width == 0) return
        when (navStyle) {
            Style.CURVED -> drawCurved(canvas)
            Style.PERSISTENT -> drawPersistent(canvas)
            Style.GOOGLE -> drawGoogle(canvas)
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
        path.cubicTo(cx - half * 1.4f, top, cx - half * 0.9f, top + dip, cx, top + dip)
        path.cubicTo(cx + half * 0.9f, top + dip, cx + half * 1.4f, top, (cx + half * 2.1f).coerceAtMost(w), top)
        path.lineTo(w, top)
        path.lineTo(w, h + 20f)
        path.lineTo(0f, h + 20f)
        path.close()
        canvas.drawPath(path, barPaint)

        val cy = top
        canvas.drawCircle(cx, cy, btnR, buttonPaint)
        canvas.drawCircle(cx, cy, btnR - dp(1.5f), accentRing)

        if (selectedIndex in items.indices) {
            val item = items[selectedIndex]
            if (item.iconRes != 0) {
                val d = context.getDrawable(item.iconRes)?.mutate()
                if (d != null) {
                    val s = (btnR * 1.1f).toInt().coerceAtLeast(1)
                    d.setBounds((cx - s / 2f).toInt(), (cy - s / 2f).toInt(), (cx + s / 2f).toInt(), (cy + s / 2f).toInt())
                    d.setColorFilter(android.graphics.PorterDuffColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN))
                    d.draw(canvas)
                }
            } else {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = dp(20f)
                }
                val emoji = item.emoji
                val fm = tp.fontMetrics
                canvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, tp)
            }
        }
    }

    private fun drawPersistent(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - flatBarH
        rect.set(0f, top, w, h + dp(4f))
        canvas.drawRect(rect, barPaint)
        canvas.drawLine(0f, top, w, top, topLine)

        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val pillW = dp(48f)
        val pillH = dp(4f)
        rect.set(cx - pillW / 2f, top + dp(4f), cx + pillW / 2f, top + dp(4f) + pillH)
        pillPaint.color = accent
        canvas.drawRoundRect(rect, dp(2f), dp(2f), pillPaint)
    }

    private fun drawGoogle(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - flatBarH
        rect.set(0f, top, w, h + dp(4f))
        canvas.drawRect(rect, barPaint)
        canvas.drawLine(0f, top, w, top, topLine)

        if (selectedIndex !in items.indices) return
        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val item = items[selectedIndex]

        val pillH = dp(40f)
        val pillW = dp(96f) * pillExpand
        val pillTop = top + (flatBarH - pillH) / 2f - dp(2f)
        rect.set(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH)
        pillPaint.color = 0x223F51B5.toInt()
        canvas.drawRoundRect(rect, dp(20f), dp(20f), pillPaint)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = dp(16f)
        }
        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            textSize = dp(12f)
            color = accent
            isFakeBoldText = true
        }
        val label = item.label
        val iconSize = dp(18f)
        val labelW = labelP.measureText(label)
        val gap = dp(6f)
        val total = iconSize + gap + labelW
        val startX = cx - total / 2f
        val cy = pillTop + pillH / 2f
        if (item.iconRes != 0) {
            val d = context.getDrawable(item.iconRes)?.mutate()
            if (d != null) {
                val s = iconSize.toInt()
                val left = startX.toInt()
                val top = (cy - s / 2f).toInt()
                d.setBounds(left, top, left + s, top + s)
                d.setColorFilter(android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN))
                d.draw(canvas)
            }
        } else {
            tp.textAlign = Paint.Align.CENTER
            val fm = tp.fontMetrics
            canvas.drawText(item.emoji, startX + iconSize / 2f, cy - (fm.ascent + fm.descent) / 2f, tp)
        }
        val lfm = labelP.fontMetrics
        canvas.drawText(label, startX + iconSize + gap, cy - (lfm.ascent + lfm.descent) / 2f, labelP)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val total = when (navStyle) {
            Style.CURVED -> (flatBarH + btnR + dp(6f)).toInt()
            else -> (flatBarH + dp(6f)).toInt()
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY))
    }
}
