package com.example.alarmclock

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ActivityCalendarAgendaBinding
import java.util.Calendar
import java.util.Locale

/** Màn lịch kiểu “Kế hoạch” giống app Lịch hệ thống. */
class CalendarAgendaActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityCalendarAgendaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val now = Calendar.getInstance()
        val monthNames = arrayOf(
            "", "tháng 1", "tháng 2", "tháng 3", "tháng 4", "tháng 5", "tháng 6",
            "tháng 7", "tháng 8", "tháng 9", "tháng 10", "tháng 11", "tháng 12"
        )
        binding.tvMonth.text = "%s năm %d".format(
            monthNames[now.get(Calendar.MONTH) + 1],
            now.get(Calendar.YEAR)
        )

        val items = VietnamHolidays.agendaItems(now, 24)
        binding.recyclerAgenda.layoutManager = LinearLayoutManager(this)
        binding.recyclerAgenda.adapter = AgendaAdapter(items) { item ->
            openEventInSystemCalendar(item)
        }

        binding.fabAddEvent.setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, "Sự kiện mới")
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Không mở được lịch: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openEventInSystemCalendar(item: VietnamHolidays.AgendaItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, item.title)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, item.timeMs)
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, item.allDay)
                    putExtra(
                        CalendarContract.Events.DESCRIPTION,
                        "Từ Báo thức Challenge · ${item.whenLabel}"
                    )
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Không mở được lịch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
