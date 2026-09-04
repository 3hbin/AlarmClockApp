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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bottom nav 3 kiểu — bản ổn định (không SOFTWARE layer / setShadowLayer).
 * Android cong: thanh + lõm nhẹ + icon tab chọn nhấc lên (không vòng tròn shadow).
 * Tránh crash Huawei / máy yếu.
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
            try {
                applyStyleChrome()
                if (items.isNotEmpty()) {
                    buildItems()
                    post {
                        try {
                            animX = centerX(selectedIndex)
                            styleItems(false)
                            requestLayout()
                            invalidate()
                        } catch (_: Exception) {
                            try { invalidate() } catch (_: Exception) {}
                        }
                    }
                } else {
                    requestLayout()
                    invalidate()
                }
            } catch (_: Exception) {
                try { invalidate() } catch (_: Exception) {}
            }
        }

    private val accentBlue = 0xFF4F5BFF.toInt()
    private val accentGoogle = 0xFFEA4335.toInt()
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

    // Không setShadowLayer — gây crash trên Huawei khi kết hợp SOFTWARE layer
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = accentBlue
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

    // Chiều cao ổn định cho mọi style → không nhảy layout khi đổi
    private val flatBarH get() = dp(58f)
    private val dip get() = dp(18f) // lõm nhẹ, không sâu 30dp

    init {
        setWillNotDraw(false)
        // HARDWARE only — SOFTWARE + shadow = crash máy yếu
        setLayerType(LAYER_TYPE_HARDWARE, null)
        clipChildren = false
        clipToPadding = false
        applyStyleChrome()
    }

    private fun applyStyleChrome() {
        try {
            when (navStyle) {
                Style.CURVED -> {
                    setBackgroundColor(pageBg)
                    barPaint.color = barBg
                }
                Style.PERSISTENT -> {
                    setBackgroundColor(Color.TRANSPARENT)
                    barPaint.color = if (isNight()) 0xE61A1C28.toInt() else 0xE6FFFFFF.toInt()
                }
                Style.GOOGLE -> {
                    setBackgroundColor(Color.TRANSPARENT)
                    barPaint.color = if (isNight()) 0xF21A1C28.toInt() else 0xF2FFFFFF.toInt()
                }
            }
            buttonPaint.color = barBg
            ringPaint.color = accent
            pillPaint.color = if (navStyle == Style.GOOGLE) 0x22EA4335.toInt()
            else if (isNight()) 0x334F5BFF.toInt() else 0x1A4F5BFF.toInt()
            topLine.color = if (isNight()) 0x22FFFFFF.toInt() else 0x14000000
            invalidate()
        } catch (_: Exception) {}
    }

    fun setItems(list: List<Item>, initial: Int = 0) {
        try {
            items.clear()
            items.addAll(list)
            selectedIndex = initial.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            buildItems()
            post {
                try {
                    animX = centerX(selectedIndex)
                    styleItems(false)
                    invalidate()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
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
            try {
                ValueAnimator.ofFloat(animX, target).apply {
                    duration = 280L
                    interpolator = DecelerateInterpolator(1.4f)
                    addUpdateListener {
                        animX = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
                if (navStyle == Style.GOOGLE) {
                    ValueAnimator.ofFloat(0.85f, 1f).apply {
                        duration = 280
                        addUpdateListener {
                            pillExpand = it.animatedValue as Float
                            invalidate()
                        }
                        start()
                    }
                }
            } catch (_: Exception) {
                animX = target
                invalidate()
            }
        } else {
            animX = target
            pillExpand = 1f
            invalidate()
        }
        styleItems(animate)
    }

    private fun buildItems() {
        try {
            removeAllViews()
            iconViews.clear()
            labelViews.clear()
            if (items.isEmpty()) return

            // CURVED: padding trên nhỏ để icon nhấc lên trong vùng lõm
            val topPad = if (navStyle == Style.CURVED) dp(10f).toInt() else 0
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(0, topPad, 0, 0)
                }
                weightSum = items.size.toFloat()
            }

            items.forEachIndexed { i, item ->
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (i != selectedIndex) {
                            selectIndex(i, true)
                            onItemSelected?.invoke(i, item)
                        }
                    }
                    setOnLongClickListener {
                        onItemLongClick?.invoke(i, item) ?: false
                    }
                }

                val icon: View = if (item.iconRes != 0) {
                    ImageView(context).apply {
                        setImageResource(item.iconRes)
                        layoutParams = LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt())
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                } else {
                    TextView(context).apply {
                        text = item.emoji
                        textSize = 18f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
                val label = TextView(context).apply {
                    text = item.label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(labelInactive)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(2f).toInt() }
                    maxLines = 1
                }
                cell.addView(icon)
                cell.addView(label)
                row.addView(cell)
                iconViews.add(icon)
                labelViews.add(label)
            }
            addView(row)
        } catch (_: Exception) {}
    }

    private fun centerX(index: Int): Float {
        if (items.isEmpty() || width <= 0) return 0f
        val slot = width.toFloat() / items.size
        return slot * index + slot / 2f
    }

    private fun styleItems(animate: Boolean) {
        try {
            iconViews.forEachIndexed { i, tv ->
                val selected = i == selectedIndex
                when (navStyle) {
                    Style.CURVED -> {
                        // Icon tab chọn nhấc lên vào vùng lõm (không vẽ vòng tròn shadow)
                        val lift = if (selected) -dp(10f) else 0f
                        val scale = if (selected) 1.12f else 1f
                        if (animate) {
                            tv.animate().translationY(lift).scaleX(scale).scaleY(scale)
                                .setDuration(220).start()
                        } else {
                            tv.translationY = lift
                            tv.scaleX = scale
                            tv.scaleY = scale
                        }
                        tv.alpha = 1f
                        if (tv is ImageView) {
                            tv.setColorFilter(
                                if (selected) accent else inactiveIcon,
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                        }
                        labelViews.getOrNull(i)?.apply {
                            visibility = VISIBLE
                            alpha = 1f
                            setTextColor(if (selected) labelActive else labelInactive)
                            paint.isFakeBoldText = selected
                        }
                    }
                    Style.PERSISTENT -> {
                        tv.translationY = 0f
                        tv.scaleX = if (selected) 1.08f else 1f
                        tv.scaleY = if (selected) 1.08f else 1f
                        tv.alpha = 1f
                        if (tv is ImageView) {
                            tv.setColorFilter(
                                if (selected) accent else inactiveIcon,
                                android.graphics.PorterDuff.Mode.SRC_IN
                            )
                        }
                        labelViews.getOrNull(i)?.apply {
                            visibility = VISIBLE
                            alpha = 1f
                            setTextColor(if (selected) labelActive else labelInactive)
                            paint.isFakeBoldText = selected
                        }
                    }
                    Style.GOOGLE -> {
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
        } catch (_: Exception) {}
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        try { animX = centerX(selectedIndex) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            if (items.isEmpty() || width <= 0 || height <= 0) return
            when (navStyle) {
                Style.CURVED -> drawCurved(canvas)
                Style.PERSISTENT -> drawPersistent(canvas)
                Style.GOOGLE -> drawGoogle(canvas)
            }
        } catch (_: Exception) {}
    }

    /** Android cong an toàn: thanh + lõm nhẹ, không vòng tròn shadow */
    private fun drawCurved(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val top = (h - flatBarH).coerceAtLeast(0f)
        val cx = if (animX == 0f) centerX(selectedIndex) else animX
        val notchHalf = dp(36f)

        path.reset()
        path.moveTo(0f, top)
        val left = (cx - notchHalf).coerceAtLeast(0f)
        val right = (cx + notchHalf).coerceAtMost(w)
        path.lineTo(left, top)
        // Lõm nhẹ dạng cubic
        path.cubicTo(
            cx - notchHalf * 0.45f, top,
            cx - dp(14f), top + dip,
            cx, top + dip
        )
        path.cubicTo(
            cx + dp(14f), top + dip,
            cx + notchHalf * 0.45f, top,
            right, top
        )
        path.lineTo(w, top)
        path.lineTo(w, h + dp(8f))
        path.lineTo(0f, h + dp(8f))
        path.close()
        canvas.drawPath(path, barPaint)

        // Viền accent mỏng trên lõm (không shadow)
        ringPaint.color = accent
        ringPaint.strokeWidth = dp(2f)
        canvas.drawCircle(cx, top + dip * 0.15f, dp(3f), buttonPaint)
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
            val fm = tp.fontMetrics
            canvas.drawText(item.emoji, startX + iconSize / 2f, cy - (fm.ascent + fm.descent) / 2f, tp)
        }
        val lfm = labelP.fontMetrics
        canvas.drawText(label, startX + iconSize + gap, cy - (lfm.ascent + lfm.descent) / 2f, labelP)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val total = try {
            // Chiều cao cố định mọi style → không crash khi đổi Android cong
            (flatBarH + dp(8f)).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            dp(66f).toInt()
        }
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY)
        )
    }
}
