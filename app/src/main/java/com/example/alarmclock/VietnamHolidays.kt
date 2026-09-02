package com.example.alarmclock

import java.util.Calendar

/**
 * Ngày lễ / sự kiện dương lịch (VN + một số ngày quốc tế thường thấy trên lịch máy).
 */
object VietnamHolidays {
    data class Holiday(val month: Int, val day: Int, val name: String, val allDay: Boolean = true)

    private val fixed = listOf(
        Holiday(1, 1, "Tết Dương lịch"),
        Holiday(2, 14, "Valentine"),
        Holiday(3, 8, "Quốc tế Phụ nữ"),
        Holiday(4, 30, "Giải phóng miền Nam"),
        Holiday(5, 1, "Quốc tế Lao động"),
        Holiday(6, 1, "Quốc tế Thiếu nhi"),
        Holiday(9, 2, "Quốc khánh"),
        Holiday(10, 20, "Ngày Phụ nữ Việt Nam"),
        Holiday(11, 20, "Ngày Nhà giáo Việt Nam"),
        Holiday(11, 24, "Ngày Văn hoá Việt Nam"),
        Holiday(12, 24, "Đêm Giáng sinh"),
        Holiday(12, 25, "Giáng sinh/Nôen"),
        Holiday(12, 31, "Đêm giao thừa")
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
        return if (name != null) "Hôm nay $dateStr là $name 🎉" else "Hôm nay $dateStr"
    }

    /** Danh sách sự kiện sắp tới (kèm quá khứ gần trong năm) — kiểu agenda lịch máy. */
    data class AgendaItem(
        val whenLabel: String,   // "HÔM NAY" hoặc "THỨ BA, NGÀY 24 THÁNG 11"
        val title: String,
        val allDay: Boolean,
        val isToday: Boolean,
        val timeMs: Long
    )

    private val vnDays = arrayOf(
        "", "Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"
    )

    fun agendaItems(from: Calendar = Calendar.getInstance(), count: Int = 20): List<AgendaItem> {
        val year = from.get(Calendar.YEAR)
        val pairs = (year..year + 1).flatMap { y ->
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
        }.filter {
            // từ đầu năm hiện tại trở đi
            it.second.get(Calendar.YEAR) > year ||
                it.second.get(Calendar.DAY_OF_YEAR) >= from.get(Calendar.DAY_OF_YEAR) - 1
        }.sortedBy { it.second.timeInMillis }
            .take(count)

        return pairs.map { (h, c) ->
            val today = isSameDay(c, from)
            val whenLabel = if (today) {
                "HÔM NAY"
            } else {
                val dow = vnDays[c.get(Calendar.DAY_OF_WEEK)]
                val day = c.get(Calendar.DAY_OF_MONTH)
                val month = c.get(Calendar.MONTH) + 1
                val y = c.get(Calendar.YEAR)
                if (y != year) {
                    "$dow, ngày $day tháng $month năm $y".uppercase()
                } else {
                    "$dow, ngày $day tháng $month".uppercase()
                }
            }
            AgendaItem(whenLabel, h.name, h.allDay, today, c.timeInMillis)
        }
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
