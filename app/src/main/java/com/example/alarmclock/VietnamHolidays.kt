package com.example.alarmclock

import java.util.Calendar

/**
 * Ngày lễ cố định (dương lịch) Việt Nam.
 * Tết Âm lịch / Giỗ Tổ cần lịch âm hoặc API riêng.
 */
object VietnamHolidays {
    data class Holiday(val month: Int, val day: Int, val name: String)

    private val fixed = listOf(
        Holiday(1, 1, "Tết Dương lịch"),
        Holiday(4, 30, "Giải phóng miền Nam"),
        Holiday(5, 1, "Quốc tế Lao động"),
        Holiday(9, 2, "Quốc khánh Việt Nam")
    )

    fun isHoliday(cal: Calendar): Boolean {
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return fixed.any { it.month == m && it.day == d }
    }

    fun holidayName(cal: Calendar): String? {
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return fixed.firstOrNull { it.month == m && it.day == d }?.name
    }

    fun todayMessage(cal: Calendar = Calendar.getInstance()): String {
        val name = holidayName(cal)
        val dateStr = "%02d/%02d/%04d".format(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
        return if (name != null) {
            "Hôm nay $dateStr là $name 🎉"
        } else {
            "Hôm nay $dateStr — không phải ngày lễ cố định"
        }
    }

    fun upcomingLines(from: Calendar = Calendar.getInstance(), count: Int = 6): List<String> {
        val lines = mutableListOf<String>()
        val year = from.get(Calendar.YEAR)
        val all = (year..year + 1).flatMap { y ->
            fixed.map { h ->
                val c = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, h.month - 1)
                    set(Calendar.DAY_OF_MONTH, h.day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                h to c
            }
        }.filter { it.second.timeInMillis >= from.timeInMillis - 24 * 3600_000L }
            .sortedBy { it.second.timeInMillis }
            .take(count)
        all.forEach { (h, c) ->
            val ds = "%02d/%02d/%04d".format(
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.YEAR)
            )
            val mark = if (isSameDay(c, from)) " ← HÔM NAY" else ""
            lines.add("$ds · ${h.name}$mark")
        }
        return lines
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun skipToNextWorkingDay(cal: Calendar) {
        while (isHoliday(cal) ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}
