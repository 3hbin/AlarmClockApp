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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bottom nav 3 kiểu — CURVED theo curved_labeled_navigation_bar:
 * thanh trắng lõm sâu + nút tròn nổi chứa icon tab đang chọn.
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

    private val accentBlue = 0xFF4F5BFF.toInt()
    private val accentGoogle = 0xFFEA4335.toInt() // Google red
    private val accent: Int get() = if (navStyle == Style.GOOGLE) accentGoogle else accentBlue
    private fun isNight(): Boolean {
        val ui = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private val barBg: Int get() = if (isNight()) 0xFF1A1C28.toInt() else Color.WHITE
    private val pageBg: Int get() = if (isNight()) 0xFF12131A.toInt() else 0xFFF7F8FC.toInt()
    private val inactiveIcon: Int get() = if (isNight()) 0xFF8B90A5.toInt() else 0xFF6B7280.toInt()
    private val labelInactive: Int get() = if (isNight()) 0xFF8B90A5.toInt() else 0xFF9AA0B4.toInt()
    private val labelActive: Int get() = if (navStyle == Style.GOOGLE) accentGoogle else accentBlue

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(14f, 0f, -2f, 0x30000000)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(12f, 0f, 4f, 0x40000000)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = accent
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1A4F5BFF
    }
    private val topLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x14000000
    }
    private val path = Path()
    private val rect = RectF()
    private val iconViews = mutableListOf<View>()
    private val labelViews = mutableListOf<TextView>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private val flatBarH get() = when (navStyle) {
        Style.GOOGLE -> dp(62f)
        Style.PERSISTENT -> dp(58f)
        else -> dp(58f)
    }
    private val dip get() = dp(30f)
    private val btnR get() = dp(30f)

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
                setBackgroundColor(pageBg)
                barPaint.color = barBg
                barPaint.setShadowLayer(14f, 0f, -2f, if (isNight()) 0x60000000 else 0x30000000)
            }
            Style.PERSISTENT -> {
                setBackgroundColor(Color.TRANSPARENT)
                barPaint.color = if (isNight()) 0xE61A1C28.toInt() else 0xE6FFFFFF.toInt()
                barPaint.setShadowLayer(10f, 0f, -1f, if (isNight()) 0x50000000 else 0x20000000)
            }
            Style.GOOGLE -> {
                setBackgroundColor(Color.TRANSPARENT)
                barPaint.color = barBg
                barPaint.setShadowLayer(8f, 0f, -2f, if (isNight()) 0x60000000 else 0x22000000)
            }
        }
        buttonPaint.color = barBg
        ringPaint.color = accent
        pillPaint.color = if (navStyle == Style.GOOGLE) 0x22EA4335.toInt()
            else if (isNight()) 0x334F5BFF.toInt() else 0x1A4F5BFF.toInt()
        topLine.color = if (isNight()) 0x22FFFFFF.toInt() else 0x14000000
        invalidate()
    }

    fun setItems(list: List<Item>, initial: Int = 0) {
        items.clear()
        items.addAll(list)
        selectedIndex = initial.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        buildItems()
        post {
            animX = centerX(selectedIndex)
            styleItems(false)
            invalidate()
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
                    Style.CURVED -> 420L
                    Style.GOOGLE -> 320L
                    else -> 260L
                }
                interpolator = DecelerateInterpolator(1.5f)
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
        if (items.isEmpty()) return

        val topPad = when (navStyle) {
            Style.CURVED -> (btnR * 0.9f).toInt()
            else -> 0
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = topPad
            }
            setPadding(0, 0, 0, dp(6f).toInt())
        }

        items.forEachIndexed { index, item ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
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
                    }, if (changed) 240L else 0L)
                }
            }

            val icon: View = if (item.iconRes != 0) {
                ImageView(context).apply {
                    setImageResource(item.iconRes)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    layoutParams = LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt())
                    setColorFilter(
                        if (index == selectedIndex) accent else inactiveIcon,
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
            } else {
                TextView(context).apply {
                    text = item.emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), dp(28f).toInt())
                }
            }

            val label = TextView(context).apply {
                text = item.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
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
            if (tv is ImageView) {
                val c = if (selected) accent else inactiveIcon
                tv.setColorFilter(c, android.graphics.PorterDuff.Mode.SRC_IN)
            }
            when (navStyle) {
                Style.CURVED -> {
                    // Icon tab chọn nhô vào nút tròn nổi
                    tv.alpha = 1f
                    val scale = if (selected) 1.2f else 0.92f
                    val ty = if (selected) -dp(26f) else 0f
                    if (animated) {
                        tv.animate()
                            .scaleX(scale).scaleY(scale)
                            .translationY(ty)
                            .setDuration(160)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    } else {
                        tv.scaleX = scale
                        tv.scaleY = scale
                        tv.translationY = ty
                    }
                    labelViews.getOrNull(i)?.apply {
                        visibility = VISIBLE
                        // Ẩn label tab đang chọn (nằm trong nút nổi)
                        alpha = if (selected) 0f else 1f
                        setTextColor(labelInactive)
                    }
                }
                Style.PERSISTENT -> {
                    tv.alpha = 1f
                    val scale = if (selected) 1.12f else 1f
                    if (animated) {
                        tv.animate().scaleX(scale).scaleY(scale).translationY(0f).setDuration(200).start()
                    } else {
                        tv.scaleX = scale
                        tv.scaleY = scale
                        tv.translationY = 0f
                    }
                    labelViews.getOrNull(i)?.apply {
                        visibility = VISIBLE
                        alpha = 1f
                        setTextColor(if (selected) labelActive else labelInactive)
                        paint.isFakeBoldText = selected
                    }
                }
                Style.GOOGLE -> {
                    // Icon/label tab chọn ẩn — drawGoogle vẽ pill + icon + text
                    tv.alpha = if (selected) 0f else 1f
                    tv.scaleX = 1f
                    tv.scaleY = 1f
                    tv.translationY = 0f
                    if (tv is ImageView) {
                        tv.setColorFilter(inactiveIcon, android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    labelViews.getOrNull(i)?.apply {
                        visibility = VISIBLE
                        alpha = if (selected) 0f else 1f
                        setTextColor(labelInactive)
                        paint.isFakeBoldText = false
                    }
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

    /** Lõm sâu + nút tròn trắng nổi — giống curved_labeled_navigation_bar */
    private fun drawCurved(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - flatBarH
        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val notchHalf = btnR * 1.65f

        path.reset()
        path.moveTo(0f, top)
        path.lineTo((cx - notchHalf - dp(6f)).coerceAtLeast(0f), top)
        path.cubicTo(
            cx - notchHalf * 0.5f, top,
            cx - btnR * 1.1f, top + dip,
            cx, top + dip
        )
        path.cubicTo(
            cx + btnR * 1.1f, top + dip,
            cx + notchHalf * 0.5f, top,
            (cx + notchHalf + dp(6f)).coerceAtMost(w), top
        )
        path.lineTo(w, top)
        path.lineTo(w, h + dp(20f))
        path.lineTo(0f, h + dp(20f))
        path.close()
        canvas.drawPath(path, barPaint)

        // Nút tròn nổi
        val cy = top
        buttonPaint.color = barBg
        buttonPaint.setShadowLayer(dp(12f), 0f, dp(3f), if (isNight()) 0x80000000 else 0x45000000)
        canvas.drawCircle(cx, cy, btnR, buttonPaint)
        ringPaint.color = accent
        canvas.drawCircle(cx, cy, btnR - dp(1.5f), ringPaint)
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
        val pillH = dp(3.5f)
        rect.set(cx - pillW / 2f, top + dp(5f), cx + pillW / 2f, top + dp(5f) + pillH)
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
        val pillW = dp(100f) * pillExpand
        val pillTop = top + (flatBarH - pillH) / 2f - dp(2f)
        rect.set(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + pillH)
        pillPaint.color = if (isNight()) 0x33EA4335.toInt() else 0x22EA4335.toInt()
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
                val topI = (cy - s / 2f).toInt()
                d.setBounds(left, topI, left + s, topI + s)
                d.setColorFilter(
                    android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN)
                )
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
