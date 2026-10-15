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
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
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
    // Vị trí (dạng số thực) của tab đang chọn — dùng cho kiểu thanh nổi, animate mượt giữa 2 tab
    private var selPos = 0f
    private var selAnim: ValueAnimator? = null
    private val iconDrawables = mutableListOf<android.graphics.drawable.Drawable?>()
    private var downX = 0f
    private var downTime = 0L
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
    private val eventAccent: Int get() = try { EventManager.activeColor(context) } catch (_: Exception) { accentBlue }
    private val accent: Int get() = eventAccent

    private fun isNight(): Boolean {
        val ui = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // Material You: màu lấy từ resource (Android 12+ tự đổi theo hình nền)
    private fun res(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)
    private val themed: Boolean get() = try {
        EventManager.isThemeEnabled(context) && EventManager.palette() != null
    } catch (_: Exception) { false }
    private val barBg: Int get() = res(R.color.surface_container)
    private val pageBg: Int get() = res(R.color.surface)
    private val inactiveIcon: Int get() = res(R.color.on_surface_variant)
    private val labelInactive: Int get() = res(R.color.on_surface_variant)
    /** Nền "viên thuốc" của tab đang chọn: secondary container (M3) hoặc màu sự kiện */
    private val indicatorColor: Int get() =
        if (themed) (accent and 0x00FFFFFF) or 0x38000000 else res(R.color.secondary_container)
    private val onIndicator: Int get() = if (themed) accent else res(R.color.on_secondary_container)
    private val onPrimaryColor: Int get() = if (themed) Color.WHITE else res(R.color.on_primary)
    private val labelActive: Int get() = if (navStyle == Style.GOOGLE) accentGoogle else eventAccent

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
                    setBackgroundColor(Color.TRANSPARENT)
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
            selPos = selectedIndex.toFloat()
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

    fun slideIndicatorFrom(fromIndex: Int) {
        // Thanh đã trượt sang tab mới ngay trên màn hình trước khi chuyển màn.
        // Ở màn mới chỉ giữ nguyên vị trí cuối — KHÔNG chạy lại hiệu ứng (trước đây chạy 2 lần → giật).
        selAnim?.cancel()
        selPos = selectedIndex.toFloat()
        invalidate()
    }

    /** true = tự đọc kiểu thanh từ Cài đặt mỗi khi màn hình hiện lại */
    var followSettings = false

    /** Trả về false → bỏ qua lần chạm này (chống bấm dồn), thanh đứng yên, không giật lùi. */
    var canSelect: (() -> Boolean)? = null

    /** Activity đã bind thanh này (tránh bind lại mỗi lần onResume). */
    var boundTo: Any? = null

    private fun syncStyleFromSettings() {
        try {
            val want = when (AppSettings.getBottomNavStyle(context)) {
                AppSettings.NAV_CURVED -> Style.CURVED
                AppSettings.NAV_GLASS -> Style.PERSISTENT
                else -> Style.GOOGLE
            }
            if (want != navStyle) navStyle = want
        } catch (_: Exception) {}
    }

    fun selectIndex(index: Int, animate: Boolean = true) {
        if (followSettings) syncStyleFromSettings()
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
                run {
                    selAnim?.cancel()
                    selAnim = ValueAnimator.ofFloat(selPos, index.toFloat()).apply {
                        duration = 260L
                        interpolator = DecelerateInterpolator(1.5f)
                        addUpdateListener {
                            selPos = it.animatedValue as Float
                            invalidate()
                        }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: android.animation.Animator) {
                                selPos = selectedIndex.toFloat()
                                invalidate()
                            }
                        })
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
            selAnim?.cancel()
            selPos = index.toFloat()
            invalidate()
        }
        styleItems(animate)
    }

    private fun buildItems() {
        try {
            removeAllViews()
            iconViews.clear()
            labelViews.clear()
            iconDrawables.clear()
            if (items.isEmpty()) return

            // Cả 3 kiểu (nổi / kính lỏng / cong) đều vẽ trực tiếp bằng canvas, không dùng view con
            if (navStyle == Style.GOOGLE || navStyle == Style.PERSISTENT || navStyle == Style.CURVED) {
                items.forEach { item ->
                    iconDrawables.add(
                        if (item.iconRes != 0)
                            androidx.core.content.ContextCompat.getDrawable(context, item.iconRes)?.mutate()
                        else null
                    )
                }
                return
            }

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

    private fun bounceIcon(tv: android.view.View) {
        tv.animate().cancel()
        tv.scaleX = 0.82f
        tv.scaleY = 0.82f
        tv.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(180)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
            .withEndAction {
                tv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start()
            }
            .start()
    }

    private fun styleItems(animate: Boolean) {
        try {
            iconViews.forEachIndexed { i, tv ->
                val selected = i == selectedIndex
                if (animate && selected) bounceIcon(tv)
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
                Style.CURVED -> drawCurvedNav(canvas)
                Style.PERSISTENT -> drawGlass(canvas)
                Style.GOOGLE -> drawFloating(canvas)
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

    // ───────────── Tự ẩn khi cuộn xuống, hiện lại khi cuộn lên ─────────────
    private var navHidden = false
    private var scrollAcc = 0
    private val attachedScrollViews =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<View, Boolean>())

    fun setHidden(hide: Boolean, withAnim: Boolean = true) {
        if (hide == navHidden) return
        navHidden = hide
        val target = if (hide) height + dp(8f) else 0f
        animate().cancel()
        if (withAnim) {
            animate().translationY(target).setDuration(220L)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            translationY = target
        }
    }

    private fun feedScroll(dy: Int) {
        if (dy == 0) return
        if ((dy > 0) != (scrollAcc > 0)) scrollAcc = 0
        scrollAcc += dy
        if (scrollAcc > dp(24f)) setHidden(true)
        else if (scrollAcc < -dp(12f)) setHidden(false)
    }

    /** Tìm RecyclerView / ScrollView trong màn hình rồi gắn tự ẩn thanh tab khi cuộn. */
    fun autoHideOnScroll(activity: android.app.Activity) {
        val scan = Runnable {
            try {
                (activity.findViewById<View>(android.R.id.content) as? ViewGroup)
                    ?.let { attachScrollTargets(it) }
            } catch (_: Exception) {}
        }
        post(scan)
        postDelayed(scan, 800L)
    }

    private fun attachScrollTargets(v: View) {
        if (v === this) return
        if (v is androidx.recyclerview.widget.RecyclerView) {
            if (attachedScrollViews.add(v)) {
                v.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                        feedScroll(dy)
                        if (!rv.canScrollVertically(-1)) setHidden(false)
                    }
                })
            }
            return
        }
        if (v is android.widget.ScrollView || v is androidx.core.widget.NestedScrollView) {
            if (attachedScrollViews.add(v)) {
                var last = v.scrollY
                v.viewTreeObserver.addOnScrollChangedListener {
                    val y = v.scrollY
                    val d = y - last
                    last = y
                    if (d != 0) feedScroll(d)
                    if (y <= 0) setHidden(false)
                }
            }
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) attachScrollTargets(v.getChildAt(i))
        }
    }

    // ───────────── Hit-test / vùng chạm cho cả 3 kiểu ─────────────
    private fun touchTop(): Float = when (navStyle) {
        Style.CURVED -> height - dp(56f) - dp(30f)
        else -> height - floatBottom - floatBarH - dp(6f)
    }

    private fun hitIndex(x: Float): Int {
        val n = items.size
        if (n == 0) return -1
        return when (navStyle) {
            Style.GOOGLE -> floatIndexAt(x)
            Style.PERSISTENT -> {
                val innerL = floatSide + floatPad
                val slot = (width - floatSide * 2 - floatPad * 2) / n
                ((x - innerL) / slot).toInt().coerceIn(0, n - 1)
            }
            Style.CURVED -> (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
        }
    }

    private fun drawIconAt(canvas: Canvas, idx: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val d = iconDrawables.getOrNull(idx) ?: return
        val s = size.toInt()
        val l = (cx - s / 2f).toInt()
        val t = (cy - s / 2f).toInt()
        d.setBounds(l, t, l + s, t + s)
        d.setTint(color)
        d.draw(canvas)
    }

    // ───────────── Kiểu KÍNH LỎNG (giống liquid_glass_bar) ─────────────
    private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun drawGlass(canvas: Canvas) {
        val n = items.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val night = isNight()
        val barL = floatSide
        val barR = w - floatSide
        val barB = h - floatBottom
        val barT = barB - floatBarH
        val radius = floatBarH / 2f
        val cy = barT + floatBarH / 2f

        // bóng mờ nhẹ
        for (k in 4 downTo 1) {
            val grow = dp(1.6f) * k
            shadowPaint.color = Color.argb(if (night) 22 else 9, 0, 0, 0)
            rect.set(barL - grow, barT - grow + dp(4f), barR + grow, barB + grow + dp(2f))
            canvas.drawRoundRect(rect, radius + grow, radius + grow, shadowPaint)
        }

        // thân kính: gradient trong suốt
        val topC = (barBg and 0x00FFFFFF) or ((if (night) 200 else 220) shl 24)
        val botC = (barBg and 0x00FFFFFF) or ((if (night) 170 else 170) shl 24)
        glassFill.shader = android.graphics.LinearGradient(
            0f, barT, 0f, barB, topC, botC, android.graphics.Shader.TileMode.CLAMP
        )
        rect.set(barL, barT, barR, barB)
        canvas.drawRoundRect(rect, radius, radius, glassFill)
        glassFill.shader = null

        // viền sáng (trên-trái sáng, dưới-phải mờ)
        glassEdge.strokeWidth = dp(1.2f)
        glassEdge.shader = android.graphics.LinearGradient(
            barL, barT, barR, barB,
            if (night) Color.argb(120, 255, 255, 255) else Color.argb(235, 255, 255, 255),
            if (night) Color.argb(30, 255, 255, 255) else Color.argb(60, 160, 170, 200),
            android.graphics.Shader.TileMode.CLAMP
        )
        rect.set(barL + dp(0.6f), barT + dp(0.6f), barR - dp(0.6f), barB - dp(0.6f))
        canvas.drawRoundRect(rect, radius, radius, glassEdge)
        glassEdge.shader = null

        // vệt sáng phản chiếu mỏng ở mép trên
        specPaint.color = Color.argb(if (night) 55 else 130, 255, 255, 255)
        rect.set(barL + radius * 0.7f, barT + dp(2.2f), barR - radius * 0.7f, barT + dp(3.6f))
        canvas.drawRoundRect(rect, dp(1f), dp(1f), specPaint)

        // "giọt" kính chạy theo tab chọn, kéo giãn khi di chuyển
        val innerL = barL + floatPad
        val slot = (barR - barL - floatPad * 2) / n
        val pos = selPos.coerceIn(0f, (n - 1).toFloat())
        val frac = pos - kotlin.math.floor(pos)
        val stretch = kotlin.math.sin(Math.PI * frac).toFloat()
        val bcx = innerL + slot * (pos + 0.5f)
        val bw = slot * 1.1f + slot * 0.8f * stretch
        val bh = floatBarH - dp(14f) - dp(4f) * stretch
        bubbleFill.color = (accent and 0x00FFFFFF) or (if (night) 0x4D000000 else 0x33000000)
        rect.set(bcx - bw / 2f, cy - bh / 2f, bcx + bw / 2f, cy + bh / 2f)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, bubbleFill)
        glassEdge.strokeWidth = dp(1.2f)
        glassEdge.color = Color.argb(if (night) 90 else 190, 255, 255, 255)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, glassEdge)
        specPaint.color = Color.argb(if (night) 50 else 120, 255, 255, 255)
        val sw = bw * 0.30f
        val spec = RectF(bcx - sw, cy - bh / 2f + dp(3f), bcx + sw, cy - bh / 2f + dp(5.2f))
        canvas.drawRoundRect(spec, dp(1.1f), dp(1.1f), specPaint)

        // icon + nhãn nhỏ cho tab chọn
        floatText.textSize = dp(9.5f)
        for (i in 0 until n) {
            val c = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
            val cxI = innerL + slot * (i + 0.5f)
            val color = blend(inactiveIcon, onIndicator, c)
            drawIconAt(canvas, i, cxI, cy - dp(5f) * c, floatIconSize * (1f + 0.08f * c), color)
            if (c > 0.1f) {
                val label = items[i].label
                val tw = floatText.measureText(label)
                floatText.color = color
                floatText.alpha = (255 * ((c - 0.1f) / 0.9f)).toInt().coerceIn(0, 255)
                canvas.drawText(label, cxI - tw / 2f, cy + dp(16f), floatText)
            }
        }
    }

    // ───────────── Kiểu CONG (giống curved_navigation_bar) ─────────────
    private val bubbleShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun drawCurvedNav(canvas: Canvas) {
        val n = items.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val barH = dp(56f)
        val top = h - barH
        val slot = w / n
        val pos = selPos.coerceIn(0f, (n - 1).toFloat())
        val frac = pos - kotlin.math.floor(pos)
        val cx = slot * (pos + 0.5f)
        val r = dp(24f)
        val depth = dp(30f)
        val half = dp(54f)

        path.reset()
        path.moveTo(0f, top)
        path.lineTo((cx - half).coerceAtLeast(0f), top)
        path.cubicTo(cx - half * 0.40f, top, cx - half * 0.55f, top + depth, cx, top + depth)
        path.cubicTo(cx + half * 0.55f, top + depth, cx + half * 0.40f, top, cx + half, top)
        path.lineTo(w, top)
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        // bóng mờ phía trên thanh
        val night = isNight()
        for (k in 3 downTo 1) {
            shadowPaint.color = Color.argb(if (night) 26 else 11, 0, 0, 0)
            canvas.save()
            canvas.translate(0f, -dp(1.3f) * k)
            canvas.drawPath(path, shadowPaint)
            canvas.restore()
        }
        barPaint.color = barBg
        canvas.drawPath(path, barPaint)

        // quả cầu nổi: hơi chìm xuống khi đang chạy
        val dip = kotlin.math.sin(Math.PI * frac).toFloat() * dp(6f)
        val bcy = top + depth - r - dp(5f) + dip
        for (k in 2 downTo 1) {
            bubbleShadow.color = Color.argb(if (night) 40 else 22, 0, 0, 0)
            canvas.drawCircle(cx, bcy + dp(2f) * k, r + dp(1f) * k, bubbleShadow)
        }
        buttonPaint.color = accent
        canvas.drawCircle(cx, bcy, r, buttonPaint)

        // icon: tab chọn nằm trong quả cầu (trắng), các tab khác nằm trong thanh
        val barCy = top + barH / 2f
        for (i in 0 until n) {
            val c = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
            val cxI = slot * (i + 0.5f)
            val y = barCy + (bcy - barCy) * c
            drawIconAt(canvas, i, cxI, y, dp(24f), blend(inactiveIcon, onPrimaryColor, c))
        }
    }

    // ───────────── Thanh tab NỔI (giống flutter_floating_bottom_bar) ─────────────
    private val floatSide get() = dp(14f)
    private val floatBarH get() = dp(60f)
    private val floatBottom get() = dp(10f)
    private val floatPad get() = dp(6f)
    private val floatTotalH get() = dp(82f)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val floatBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val floatStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val floatPill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val floatText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val a = (Color.alpha(c1) + (Color.alpha(c2) - Color.alpha(c1)) * k).toInt()
        val r = (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * k).toInt()
        val g = (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * k).toInt()
        val b = (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * k).toInt()
        return Color.argb(a, r, g, b)
    }

    private val floatIconSize get() = dp(22f)
    private val floatGap get() = dp(6f)

    /** Độ rộng ô đang chọn (đủ chứa icon + nhãn dài nhất, tối đa 36% thanh) và độ rộng ô thường. */
    private fun floatWidths(avail: Float): Pair<Float, Float> {
        val n = items.size
        floatText.textSize = dp(11.5f)
        var need = 0f
        items.forEach { need = maxOf(need, floatText.measureText(it.label)) }
        need += floatIconSize + floatGap + dp(20f)
        val selW = need.coerceAtMost(avail * 0.36f)
        val unit = if (n > 1) (avail - selW) / (n - 1) else avail
        return Pair(selW, unit)
    }

    private fun drawFloating(canvas: Canvas) {
        val n = items.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val barL = floatSide
        val barR = w - floatSide
        val barB = h - floatBottom
        val barT = barB - floatBarH
        val radius = floatBarH / 2f

        // Bóng đổ giả (nhiều lớp mờ) — không dùng setShadowLayer để tránh crash máy yếu
        for (k in 5 downTo 1) {
            val grow = dp(1.6f) * k
            shadowPaint.color = Color.argb(if (isNight()) 26 else 14, 0, 0, 0)
            rect.set(barL - grow, barT - grow + dp(4f), barR + grow, barB + grow + dp(2f))
            canvas.drawRoundRect(rect, radius + grow, radius + grow, shadowPaint)
        }

        // Thân thanh
        floatBarPaint.color = barBg
        rect.set(barL, barT, barR, barB)
        canvas.drawRoundRect(rect, radius, radius, floatBarPaint)
        floatStroke.color = if (isNight()) 0x22FFFFFF else 0x12000000
        canvas.drawRoundRect(rect, radius, radius, floatStroke)

        val innerL = barL + floatPad
        val avail = (barR - barL) - floatPad * 2
        val (selW, unit) = floatWidths(avail)
        val pos = selPos.coerceIn(0f, (n - 1).toFloat())

        // Tính vị trí từng ô theo độ "gần" với tab đang chọn (tổng luôn = avail)
        val lefts = FloatArray(n + 1)
        val closeness = FloatArray(n)
        var x = innerL
        for (i in 0 until n) {
            closeness[i] = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
            lefts[i] = x
            x += unit + (selW - unit) * closeness[i]
        }
        lefts[n] = x

        // Viên thuốc nền của tab chọn: trượt mượt giữa 2 ô
        val i0 = pos.toInt().coerceIn(0, n - 1)
        val i1 = (i0 + 1).coerceAtMost(n - 1)
        val f = pos - i0
        val c0 = (lefts[i0] + lefts[i0 + 1]) / 2f
        val c1 = (lefts[i1] + lefts[i1 + 1]) / 2f
        val pc = c0 + (c1 - c0) * f
        val pillH = floatBarH - dp(16f)
        val pillT = barT + (floatBarH - pillH) / 2f
        floatPill.color = indicatorColor
        rect.set(pc - selW / 2f, pillT, pc + selW / 2f, pillT + pillH)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, floatPill)

        // Icon + nhãn
        floatText.textSize = dp(11.5f)
        val cy = barT + floatBarH / 2f
        for (i in 0 until n) {
            val c = closeness[i]
            val l = lefts[i]
            val r = lefts[i + 1]
            val cellCx = (l + r) / 2f
            val label = items[i].label
            val textW = floatText.measureText(label)
            val groupW = floatIconSize + floatGap + textW
            val iconCx = cellCx + (-groupW / 2f + floatIconSize / 2f) * c
            val color = blend(inactiveIcon, onIndicator, c)

            val d = iconDrawables.getOrNull(i)
            if (d != null) {
                val s = floatIconSize.toInt()
                val il = (iconCx - s / 2f).toInt()
                val itop = (cy - s / 2f).toInt()
                d.setBounds(il, itop, il + s, itop + s)
                d.setTint(color)
                d.draw(canvas)
            }

            if (c > 0.08f) {
                val fm = floatText.fontMetrics
                floatText.color = color
                floatText.alpha = (255 * ((c - 0.08f) / 0.92f)).toInt().coerceIn(0, 255)
                val tx = cellCx - groupW / 2f + floatIconSize + floatGap
                canvas.save()
                canvas.clipRect(l, barT, r - dp(4f), barB)
                canvas.drawText(label, tx, cy - (fm.ascent + fm.descent) / 2f, floatText)
                canvas.restore()
            }
        }
    }

    private fun floatIndexAt(xTouch: Float): Int {
        val n = items.size
        if (n == 0) return -1
        val barL = floatSide
        val avail = (width - floatSide) - barL - floatPad * 2
        val (selW, unit) = floatWidths(avail)
        // Hit-test theo tab đang chọn thật (ổn định), không theo vị trí đang animate
        val pos = selectedIndex.toFloat().coerceIn(0f, (n - 1).toFloat())
        var x = barL + floatPad
        for (i in 0 until n) {
            val cw = unit + (selW - unit) * (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
            if (xTouch < x + cw) return i
            x += cw
        }
        return n - 1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y < touchTop()) return false
                downX = event.x
                downTime = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.x - downX) > ViewConfiguration.get(context).scaledTouchSlop
                if (!moved) {
                    val i = hitIndex(event.x)
                    if (i in items.indices) {
                        val isLong = event.eventTime - downTime >= ViewConfiguration.getLongPressTimeout()
                        if (isLong) {
                            onItemLongClick?.invoke(i, items[i])
                        } else if (i != selectedIndex && canSelect?.invoke() != false) {
                            performClick()
                            selectIndex(i, true)
                            onItemSelected?.invoke(i, items[i])
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val total = try {
            // Chiều cao cố định mọi style → không crash khi đổi Android cong
            (when (navStyle) { Style.CURVED -> dp(88f); else -> floatTotalH }).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            dp(66f).toInt()
        }
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(total, MeasureSpec.EXACTLY)
        )
    }
}
