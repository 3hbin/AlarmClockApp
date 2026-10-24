package com.example.alarmclock

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityCalendarAgendaBinding
import com.google.android.material.tabs.TabLayout
import java.util.Calendar

/** Lịch: Tháng · Tuần · Ngày · Kế hoạch */
class CalendarAgendaActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityCalendarAgendaBinding
    private val cursor = Calendar.getInstance()
    private val monthNames: Array<String> get() = arrayOf(
        "", Lang.t("tháng 1","January"), Lang.t("tháng 2","February"), Lang.t("tháng 3","March"),
        Lang.t("tháng 4","April"), Lang.t("tháng 5","May"), Lang.t("tháng 6","June"),
        Lang.t("tháng 7","July"), Lang.t("tháng 8","August"), Lang.t("tháng 9","September"),
        Lang.t("tháng 10","October"), Lang.t("tháng 11","November"), Lang.t("tháng 12","December")
    )
    private val vnDays: Array<String>
        get() = arrayOf(
            "",
            Lang.t("Chủ nhật","Sunday"), Lang.t("Thứ hai","Monday"), Lang.t("Thứ ba","Tuesday"),
            Lang.t("Thứ tư","Wednesday"), Lang.t("Thứ năm","Thursday"), Lang.t("Thứ sáu","Friday"),
            Lang.t("Thứ bảy","Saturday")
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loading = Md3Loading.show(this, "Đang mở lịch…")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ try { loading.dismiss() } catch (_: Exception) {} }, 600)
        window.decorView.post { try { EventManager.applyChrome(this) } catch (_: Exception) {} }
        binding = ActivityCalendarAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = Lang.t(this, "Lịch", "Calendar")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        refreshTitle()

        binding.tabModes.addTab(binding.tabModes.newTab().setText(Lang.t(this, "Tháng", "Month")))
        binding.tabModes.addTab(binding.tabModes.newTab().setText(Lang.t(this, "Tuần", "Week")))
        binding.tabModes.addTab(binding.tabModes.newTab().setText(Lang.t(this, "Ngày", "Day")))
        binding.tabModes.addTab(binding.tabModes.newTab().setText(Lang.t(this, "Kế hoạch", "Agenda")))
        binding.tabModes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showMode(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.fabAddEvent.setOnClickListener { openInsertEvent(null) }

        setupHeader()
        // FAB bật lên có độ nảy
        binding.fabAddEvent.scaleX = 0f
        binding.fabAddEvent.scaleY = 0f
        binding.fabAddEvent.animate().scaleX(1f).scaleY(1f)
            .setStartDelay(380).setDuration(340)
            .setInterpolator(OvershootInterpolator(1.6f)).start()

        showMode(0)
    }

    // ——— MÀU / HEADER ———
    private fun accentColor(): Int = try {
        val pal = if (EventManager.isThemeEnabled(this)) EventManager.palette() else null
        pal?.accent ?: ContextCompat.getColor(this, R.color.primary)
    } catch (_: Exception) {
        0xFF4351B7.toInt()
    }

    private fun onAccent(accent: Int): Int =
        if (ColorUtils.calculateLuminance(accent) > 0.6) 0xFF202124.toInt() else 0xFFFFFFFF.toInt()

    private fun setupHeader() {
        val dp = resources.displayMetrics.density
        val accent = accentColor()
        binding.btnToday.text = Lang.t(this, "Hôm nay", "Today")
        binding.btnToday.setTextColor(accent)
        binding.btnToday.background = GradientDrawable().apply {
            cornerRadius = 40 * dp
            setColor(ColorUtils.setAlphaComponent(accent, 0x22))
        }
        binding.btnPrev.imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
        binding.btnNext.imageTintList = android.content.res.ColorStateList.valueOf(0xFF5F6368.toInt())
        binding.btnPrev.setOnClickListener { step(-1) }
        binding.btnNext.setOnClickListener { step(1) }
        binding.btnToday.setOnClickListener { goToday() }
    }

    /** Trước / sau theo chế độ đang xem: tháng · tuần · ngày. */
    private fun step(dir: Int) {
        val mode = binding.tabModes.selectedTabPosition
        when (mode) {
            0 -> cursor.add(Calendar.MONTH, dir)
            1 -> cursor.add(Calendar.WEEK_OF_YEAR, dir)
            2 -> cursor.add(Calendar.DAY_OF_YEAR, dir)
            else -> return
        }
        showMode(mode, dir)
    }

    private fun goToday() {
        val now = System.currentTimeMillis()
        val dir = if (now > cursor.timeInMillis) 1 else -1
        cursor.timeInMillis = now
        showMode(binding.tabModes.selectedTabPosition.coerceAtLeast(0), dir)
    }

    private fun refreshTitle() {
        val m = cursor.get(Calendar.MONTH) + 1
        val y = cursor.get(Calendar.YEAR)
        val name = monthNames[m].replaceFirstChar { it.uppercase() }
        binding.tvMonth.text = Lang.t(this, "%s, %d".format(name, y), "$name $y")
    }

    private fun showMode(index: Int, dir: Int = 0, animate: Boolean = true, popDay: Int = -1) {
        binding.calendarContent.removeAllViews()
        // Kế hoạch không có trước/sau
        val navVis = if (index == 3) View.GONE else View.VISIBLE
        binding.btnPrev.visibility = navVis
        binding.btnNext.visibility = navVis
        when (index) {
            0 -> showMonth(animate, popDay)
            1 -> showWeek()
            2 -> showDay()
            else -> showAgenda()
        }
        val dp = resources.displayMetrics.density
        val v = binding.calendarContent.getChildAt(0)
        if (v != null && animate) {
            if (dir != 0) {
                // Trượt sang tháng/tuần/ngày kế tiếp
                v.translationX = dir * 56 * dp
                v.alpha = 0f
                v.animate().translationX(0f).alpha(1f).setDuration(280)
                    .setInterpolator(DecelerateInterpolator(1.8f)).start()
            } else {
                v.translationY = 14 * dp
                v.alpha = 0f
                v.animate().translationY(0f).alpha(1f).setDuration(240)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
        if (animate) {
            // Tiêu đề trượt nhẹ khi đổi
            binding.tvMonth.alpha = 0f
            binding.tvMonth.translationX = dir * 18 * dp
            binding.tvMonth.animate().alpha(1f).translationX(0f).setDuration(240)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    // ——— THÁNG ———
    private fun showMonth(animate: Boolean = true, popDay: Int = -1) {
        val view = layoutInflater.inflate(R.layout.view_cal_month, binding.calendarContent, false)
        binding.calendarContent.addView(view)
        val grid = view.findViewById<android.widget.GridLayout>(R.id.gridMonth)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerDayEvents)
        val tvLabel = view.findViewById<TextView>(R.id.tvSelectedLabel)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyDay)
        recycler.layoutManager = LinearLayoutManager(this)
        val accent = accentColor()
        refreshTitle()

        grid.removeAllViews()
        val cal = cursor.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // Monday-first: Calendar.SUNDAY=1 ... convert
        val startDow = cal.get(Calendar.DAY_OF_WEEK) // 1=CN ... 7=T7
        val offset = if (startDow == Calendar.SUNDAY) 6 else startDow - 2 // Mon=0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val selectedDay = cursor.get(Calendar.DAY_OF_MONTH)
        val cellH = (54 * resources.displayMetrics.density).toInt()

        // leading blanks
        for (i in 0 until offset) {
            grid.addView(blankCell(cellH))
        }
        var order = 0
        for (d in 1..daysInMonth) {
            val dayCal = cursor.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, d)
            val hasEvent = VietnamHolidays.holidayName(dayCal) != null
            val isToday = isSameDay(dayCal, today)
            val isSelected = d == selectedDay
            val isSunday = dayCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            val cell = dayCell(d, isToday, isSelected, hasEvent, isSunday, cellH, accent)
            cell.setOnClickListener {
                cursor.set(Calendar.DAY_OF_MONTH, d)
                showMode(0, 0, false, d) // đổi ngày chọn: chỉ nảy ô được chọn + danh sách bên dưới
            }
            if (animate) {
                cell.alpha = 0f
                cell.scaleX = 0.6f
                cell.scaleY = 0.6f
                cell.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay((order * 14L).coerceAtMost(420L)).setDuration(260)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
            } else if (d == popDay) {
                cell.scaleX = 0.7f
                cell.scaleY = 0.7f
                cell.animate().scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            order++
            grid.addView(cell)
        }

        // Nhãn ngày đang chọn
        tvLabel.setTextColor(accent)
        tvLabel.text = if (isSameDay(cursor, today)) {
            Lang.t(this, "Hôm nay", "Today")
        } else {
            "%s, %d %s".format(
                vnDays[cursor.get(Calendar.DAY_OF_WEEK)],
                cursor.get(Calendar.DAY_OF_MONTH),
                monthNames[cursor.get(Calendar.MONTH) + 1]
            )
        }

        // events of selected day
        val name = VietnamHolidays.holidayName(cursor)
        val items = if (name != null) {
            listOf(
                VietnamHolidays.AgendaItem(
                    "HÔM NAY".takeIf { isSameDay(cursor, today) } ?: "",
                    name, true, isSameDay(cursor, today), cursor.timeInMillis
                )
            )
        } else emptyList()
        recycler.adapter = AgendaAdapter(items, accent) { openInsertEvent(it) }
        if (items.isEmpty()) {
            tvEmpty.text = Lang.t(this, "Không có sự kiện\nBấm + để thêm", "No events\nTap + to add")
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.alpha = 0f
            tvEmpty.animate().alpha(1f).setStartDelay(120).setDuration(260).start()
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    /** Ô ngày: số trong vòng tròn (chọn = đặc, hôm nay = viền), chấm sự kiện bên dưới. */
    private fun dayCell(
        d: Int, isToday: Boolean, isSelected: Boolean, hasEvent: Boolean,
        isSunday: Boolean, cellH: Int, accent: Int
    ): FrameLayout {
        val dp = resources.displayMetrics.density
        val cell = FrameLayout(this)
        cell.layoutParams = android.widget.GridLayout.LayoutParams().apply {
            width = 0
            height = cellH
            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
        }
        val size = (40 * dp).toInt()
        val num = TextView(this).apply {
            text = d.toString()
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(null, if (isToday || isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                when {
                    isSelected -> onAccent(accent)
                    isToday || hasEvent -> accent
                    isSunday -> 0xFFD93025.toInt()
                    else -> 0xFF202124.toInt()
                }
            )
            if (isSelected) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                }
            } else if (isToday) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke((2 * dp).toInt(), accent)
                    setColor(0x00000000)
                }
            }
        }
        cell.addView(num, FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply {
            topMargin = (3 * dp).toInt()
        })
        if (hasEvent) {
            val dotSize = (5 * dp).toInt()
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF34A853.toInt())
                }
            }
            cell.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (2 * dp).toInt()
            })
        }
        cell.isClickable = true
        return cell
    }

    private fun blankCell(h: Int): View {
        return View(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = h
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            }
        }
    }

    // ——— TUẦN ———
    private fun showWeek() {
        val view = layoutInflater.inflate(R.layout.view_cal_week, binding.calendarContent, false)
        binding.calendarContent.addView(view)
        val cols = view.findViewById<LinearLayout>(R.id.weekColumns)
        cols.removeAllViews()

        val weekStart = cursor.clone() as Calendar
        val dow = weekStart.get(Calendar.DAY_OF_WEEK)
        val toMon = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        weekStart.add(Calendar.DAY_OF_YEAR, toMon)

        val today = Calendar.getInstance()
        val colW = (resources.displayMetrics.widthPixels / 7).coerceAtLeast(72)

        for (i in 0 until 7) {
            val day = weekStart.clone() as Calendar
            day.add(Calendar.DAY_OF_YEAR, i)
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    setMargins(2, 0, 2, 0)
                }
                setBackgroundColor(0xFFF8F9FA.toInt())
                setPadding(6, 8, 6, 8)
            }
            val head = TextView(this).apply {
                text = "%s\n%d".format(
                    arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")[
                        day.get(Calendar.DAY_OF_WEEK) - 1
                    ],
                    day.get(Calendar.DAY_OF_MONTH)
                )
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isSameDay(day, today)) 0xFF1A73E8.toInt() else 0xFF5F6368.toInt())
            }
            col.addView(head)
            val holiday = VietnamHolidays.holidayName(day)
            if (holiday != null) {
                val chip = TextView(this).apply {
                    text = holiday
                    textSize = 11f
                    setTextColor(0xFF0D652D.toInt())
                    setBackgroundColor(0xFFE6F4EA.toInt())
                    setPadding(8, 10, 8, 10)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 8
                    layoutParams = lp
                    setOnClickListener {
                        cursor.timeInMillis = day.timeInMillis
                        openInsertEvent(
                            VietnamHolidays.AgendaItem("", holiday, true, false, day.timeInMillis)
                        )
                    }
                }
                col.addView(chip)
            }
            col.setOnClickListener {
                cursor.timeInMillis = day.timeInMillis
                binding.tabModes.getTabAt(2)?.select()
            }
            cols.addView(col)
        }
        val m = weekStart.get(Calendar.MONTH) + 1
        val y = weekStart.get(Calendar.YEAR)
        binding.tvMonth.text = "%s năm %d, Tuần".format(monthNames[m], y)
    }

    // ——— NGÀY ———
    private fun showDay() {
        val view = layoutInflater.inflate(R.layout.view_cal_day, binding.calendarContent, false)
        binding.calendarContent.addView(view)
        val header = view.findViewById<TextView>(R.id.tvDayHeader)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerDay)
        recycler.layoutManager = LinearLayoutManager(this)

        val d = cursor.get(Calendar.DAY_OF_MONTH)
        val m = cursor.get(Calendar.MONTH) + 1
        val y = cursor.get(Calendar.YEAR)
        header.text = Lang.t(this, "%d tháng %d, %d".format(d, m, y), "%d/%d/%d".format(d, m, y))
        binding.tvMonth.text = header.text

        val name = VietnamHolidays.holidayName(cursor)
        val items = if (name != null) {
            listOf(
                VietnamHolidays.AgendaItem(
                    "Cả ngày", name, true, isSameDay(cursor, Calendar.getInstance()), cursor.timeInMillis
                )
            )
        } else emptyList()
        recycler.adapter = AgendaAdapter(items, accentColor()) { openInsertEvent(it) }
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Không có sự kiện ngày này\nBấm + để thêm"
                gravity = Gravity.CENTER
                setTextColor(0xFF5F6368.toInt())
                setPadding(24, 48, 24, 24)
            }
            (view as ViewGroup).addView(empty)
        }
    }

    // ——— KẾ HOẠCH ———
    private fun showAgenda() {
        val view = layoutInflater.inflate(R.layout.view_cal_agenda, binding.calendarContent, false)
        binding.calendarContent.addView(view)
        val recycler = view as RecyclerView
        recycler.layoutManager = LinearLayoutManager(this)
        val items = VietnamHolidays.agendaItems(Calendar.getInstance(), 40)
        recycler.adapter = AgendaAdapter(items, accentColor()) { openInsertEvent(it) }
        refreshTitle()
    }

    private fun openInsertEvent(item: VietnamHolidays.AgendaItem?) {
        try {
            startActivity(
                Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, item?.title ?: "Sự kiện mới")
                    putExtra(
                        CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                        item?.timeMs ?: System.currentTimeMillis()
                    )
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, item?.allDay ?: true)
                    putExtra(
                        CalendarContract.Events.DESCRIPTION,
                        "Từ Báo thức Challenge"
                    )
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được lịch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private class AgendaAdapter(
        private val items: List<VietnamHolidays.AgendaItem>,
        private val accent: Int,
        private val onClick: (VietnamHolidays.AgendaItem) -> Unit
    ) : RecyclerView.Adapter<AgendaAdapter.VH>() {
        private val shown = HashSet<Int>()

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvWhen: TextView = v.findViewById(R.id.tvWhen)
            val tvAllDay: TextView = v.findViewById(R.id.tvAllDay)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
            val bar: View = v.findViewById(R.id.vBar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_agenda_event, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvWhen.text = item.whenLabel
            holder.tvWhen.visibility = if (item.whenLabel.isBlank()) View.GONE else View.VISIBLE
            holder.tvWhen.setTextColor(
                if (item.isToday) accent else 0xFF5F6368.toInt()
            )
            holder.bar.background = GradientDrawable().apply {
                cornerRadius = 6f
                setColor(accent)
            }
            holder.tvAllDay.text = if (item.allDay) "Cả ngày" else ""
            holder.tvAllDay.visibility = if (item.allDay) View.VISIBLE else View.GONE
            holder.tvTitle.text = item.title
            holder.itemView.setOnClickListener { onClick(item) }

            // Thẻ trượt lên lần lượt (chỉ lần đầu mỗi thẻ)
            if (shown.add(position)) {
                val dp = holder.itemView.resources.displayMetrics.density
                holder.itemView.alpha = 0f
                holder.itemView.translationY = 22 * dp
                holder.itemView.animate().alpha(1f).translationY(0f)
                    .setStartDelay((position * 60L).coerceAtMost(360L)).setDuration(300)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
            } else {
                holder.itemView.alpha = 1f
                holder.itemView.translationY = 0f
            }
        }

        override fun getItemCount() = items.size
    }
}
