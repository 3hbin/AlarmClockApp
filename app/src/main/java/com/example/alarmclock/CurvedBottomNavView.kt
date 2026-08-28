package com.example.alarmclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Giống Flutter [curved_labeled_navigation_bar]:
 * - Thanh trắng
 * - Đường cong lõm mượt tại mục chọn
 * - Nút tròn nổi trong khoảng lõm
 * - Label dưới mỗi icon
 * - Animation ~600ms easeOut
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

    // Màu giống package: bar trắng, nền sau (lộ qua đường cong) = primary
    private var barColor = Color.WHITE
    private var navBackgroundColor = 0xFF3F51B5.toInt()
    private var buttonColor = Color.WHITE
    private var labelActive = 0xFF3F51B5.toInt()
    private var labelInactive = 0xFF9E9E9E.toInt()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = barColor
        setShadowLayer(18f, 0f, -3f, 0x33000000)
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = buttonColor
        setShadowLayer(14f, 0f, 6f, 0x44000000)
    }
    private val path = Path()
    private val iconViews = mutableListOf<TextView>()
    private val labelViews = mutableListOf<TextView>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** Chiều cao phần thanh phẳng (không tính nút nổi) */
    private val flatBarH get() = dp(56f)
    /** Độ sâu đường cong */
    private val dip get() = dp(26f)
    /** Bán kính nút nổi */
    private val btnR get() = dp(28f)

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(navBackgroundColor)
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
                duration = 600 // giống package default
                interpolator = DecelerateInterpolator(1.5f)
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

        // Hàng icon + label nằm trên phần thanh phẳng
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            // chừa chỗ phía trên cho nút nổi + đường cong
            setPadding(0, (dip + btnR * 0.35f).toInt(), 0, dp(6f).toInt())
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
                    if (selectedIndex != index) selectIndex(index, true)
                    onItemSelected?.invoke(index, item)
                }
            }

            val icon = TextView(context).apply {
                text = item.emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                gravity = Gravity.CENTER
                // chiều cao cố định để label luôn cùng baseline
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32f).toInt())
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
            // Icon mục chọn ẩn (vẽ trong nút nổi); mục khác hiện trên thanh
            if (animated) {
                tv.animate()
                    .alpha(if (selected) 0f else 1f)
                    .setDuration(200)
                    .start()
            } else {
                tv.alpha = if (selected) 0f else 1f
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (items.isEmpty() || width == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        // Đỉnh thanh phẳng
        val top = h - flatBarH
        val cx = if (animX == 0f) centerX(selectedIndex) else animX

        // --- Path thanh trắng + lõm cong (kiểu curved_navigation_bar) ---
        // Lõm rộng ~ 2.2 * btnR, sâu ~ dip
        val half = btnR * 1.15f
        path.reset()
        path.moveTo(0f, top)
        path.lineTo((cx - half * 2f).coerceAtLeast(0f), top)

        // Cubic mượt vào đáy lõm rồi lên lại (đối xứng)
        path.cubicTo(
            cx - half * 1.35f, top,
            cx - half * 0.95f, top + dip,
            cx, top + dip
        )
        path.cubicTo(
            cx + half * 0.95f, top + dip,
            cx + half * 1.35f, top,
            (cx + half * 2f).coerceAtMost(w), top
        )

        path.lineTo(w, top)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        barPaint.color = barColor
        canvas.drawPath(path, barPaint)

        // --- Nút tròn nổi (tâm nằm trên đường top, hơi nhô lên) ---
        val cy = top
        buttonPaint.color = buttonColor
        canvas.drawCircle(cx, cy, btnR, buttonPaint)

        // Icon trong nút
        if (selectedIndex in items.indices) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = dp(22f)
            }
            val emoji = items[selectedIndex].emoji
            val fm = tp.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(emoji, cx, textY, tp)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Tổng cao = phần nhô nút + thanh phẳng
        val total = (flatBarH + btnR + dp(8f)).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY))
    }
}
