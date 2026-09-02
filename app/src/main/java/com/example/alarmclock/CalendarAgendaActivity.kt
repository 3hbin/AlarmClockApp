package com.example.alarmclock

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private val monthNames = arrayOf(
        "", "tháng 1", "tháng 2", "tháng 3", "tháng 4", "tháng 5", "tháng 6",
        "tháng 7", "tháng 8", "tháng 9", "tháng 10", "tháng 11", "tháng 12"
    )
    private val vnDays = arrayOf(
        "", "Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        refreshTitle()

        binding.tabModes.addTab(binding.tabModes.newTab().setText("Tháng"))
        binding.tabModes.addTab(binding.tabModes.newTab().setText("Tuần"))
        binding.tabModes.addTab(binding.tabModes.newTab().setText("Ngày"))
        binding.tabModes.addTab(binding.tabModes.newTab().setText("Kế hoạch"))
        binding.tabModes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showMode(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.fabAddEvent.setOnClickListener { openInsertEvent(null) }

        showMode(0)
    }

    private fun refreshTitle() {
        val m = cursor.get(Calendar.MONTH) + 1
        val y = cursor.get(Calendar.YEAR)
        binding.tvMonth.text = "%s năm %d".format(monthNames[m], y)
    }

    private fun showMode(index: Int) {
        binding.calendarContent.removeAllViews()
        when (index) {
            0 -> showMonth()
            1 -> showWeek()
            2 -> showDay()
            else -> showAgenda()
        }
    }

    // ——— THÁNG ———
    private fun showMonth() {
        val view = layoutInflater.inflate(R.layout.view_cal_month, binding.calendarContent, false)
        binding.calendarContent.addView(view)
        val grid = view.findViewById<android.widget.GridLayout>(R.id.gridMonth)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerDayEvents)
        recycler.layoutManager = LinearLayoutManager(this)

        grid.removeAllViews()
        val cal = cursor.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        // Monday-first: Calendar.SUNDAY=1 ... convert
        var startDow = cal.get(Calendar.DAY_OF_WEEK) // 1=CN ... 7=T7
        val offset = if (startDow == Calendar.SUNDAY) 6 else startDow - 2 // Mon=0
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val cellH = (resources.displayMetrics.widthPixels / 7) - 4

        // leading blanks
        for (i in 0 until offset) {
            grid.addView(blankCell(cellH))
        }
        for (d in 1..daysInMonth) {
            val dayCal = cursor.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, d)
            val hasEvent = VietnamHolidays.holidayName(dayCal) != null
            val isToday = isSameDay(dayCal, today)
            val isSelected = d == cursor.get(Calendar.DAY_OF_MONTH)
            val tv = TextView(this).apply {
                text = d.toString()
                gravity = Gravity.CENTER
                textSize = 15f
                setTypeface(null, if (isToday || isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    when {
                        isSelected || isToday -> 0xFFFFFFFF.toInt()
                        hasEvent -> 0xFF1A73E8.toInt()
                        else -> 0xFF202124.toInt()
                    }
                )
                if (isSelected || isToday) {
                    setBackgroundColor(0xFF1A73E8.toInt())
                }
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = cellH.coerceAtLeast(48)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(2, 2, 2, 2)
                }
                setOnClickListener {
                    cursor.set(Calendar.DAY_OF_MONTH, d)
                    showMode(0) // refresh selection + bottom list
                }
            }
            // event dot under day — use compound or second line
            if (hasEvent && !isSelected && !isToday) {
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, android.R.drawable.presence_online)
            }
            grid.addView(tv)
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
        recycler.adapter = AgendaAdapter(items) { openInsertEvent(it) }
    }

    private fun blankCell(h: Int): View {
        return View(this).apply {
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = h.coerceAtLeast(48)
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
        header.text = "%d tháng %d, %d".format(d, m, y)
        binding.tvMonth.text = header.text

        val name = VietnamHolidays.holidayName(cursor)
        val items = if (name != null) {
            listOf(
                VietnamHolidays.AgendaItem(
                    "Cả ngày", name, true, isSameDay(cursor, Calendar.getInstance()), cursor.timeInMillis
                )
            )
        } else emptyList()
        recycler.adapter = AgendaAdapter(items) { openInsertEvent(it) }
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
        recycler.adapter = AgendaAdapter(items) { openInsertEvent(it) }
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
        private val onClick: (VietnamHolidays.AgendaItem) -> Unit
    ) : RecyclerView.Adapter<AgendaAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvWhen: TextView = v.findViewById(R.id.tvWhen)
            val tvAllDay: TextView = v.findViewById(R.id.tvAllDay)
            val tvTitle: TextView = v.findViewById(R.id.tvTitle)
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
                if (item.isToday) 0xFF1A73E8.toInt() else 0xFF5F6368.toInt()
            )
            holder.tvAllDay.text = if (item.allDay) "Cả ngày" else ""
            holder.tvTitle.text = item.title
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
