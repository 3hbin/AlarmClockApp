package com.example.alarmclock

import java.util.Calendar

/**
 * Ngày lễ / sự kiện dương lịch (VN + quốc tế thường thấy trên lịch máy).
 */
object VietnamHolidays {
    data class Holiday(val month: Int, val day: Int, val name: String, val allDay: Boolean = true)

    private val fixed = listOf(
        // Tháng 1
        Holiday(1, 1, "Tết Dương lịch"),
        Holiday(1, 9, "Ngày Học sinh – Sinh viên Việt Nam"),
        // Tháng 2
        Holiday(2, 3, "Ngày thành lập Đảng Cộng sản Việt Nam"),
        Holiday(2, 14, "Valentine"),
        Holiday(2, 27, "Ngày Thầy thuốc Việt Nam"),
        // Tháng 3
        Holiday(3, 8, "Quốc tế Phụ nữ"),
        Holiday(3, 20, "Ngày Quốc tế Hạnh phúc"),
        Holiday(3, 26, "Ngày thành lập Đoàn TNCS Hồ Chí Minh"),
        // Tháng 4
        Holiday(4, 21, "Ngày Sách Việt Nam"),
        Holiday(4, 30, "Giải phóng miền Nam / Thống nhất đất nước"),
        // Tháng 5
        Holiday(5, 1, "Quốc tế Lao động"),
        Holiday(5, 7, "Chiến thắng Điện Biên Phủ"),
        Holiday(5, 15, "Ngày Quốc tế Gia đình"),
        Holiday(5, 19, "Sinh nhật Chủ tịch Hồ Chí Minh"),
        // Tháng 6
        Holiday(6, 1, "Quốc tế Thiếu nhi"),
        Holiday(6, 5, "Ngày Môi trường Thế giới"),
        Holiday(6, 28, "Ngày Gia đình Việt Nam"),
        // Tháng 7
        Holiday(7, 27, "Ngày Thương binh – Liệt sĩ"),
        Holiday(7, 28, "Ngày thành lập Công đoàn Việt Nam"),
        // Tháng 8
        Holiday(8, 19, "Cách mạng Tháng Tám"),
        Holiday(8, 2, "Ngày sinh Chủ tịch Tôn Đức Thắng"),
        // Tháng 9
        Holiday(9, 2, "Quốc khánh"),
        Holiday(9, 25, "Tết Trung thu"),
        Holiday(9, 7, "Ngày thành lập Đài Tiếng nói Việt Nam"),
        Holiday(9, 10, "Ngày thành lập Mặt trận Tổ quốc Việt Nam"),
        // Tháng 10
        Holiday(10, 1, "Ngày Quốc tế Người cao tuổi"),
        Holiday(10, 10, "Ngày Giải phóng Thủ đô"),
        Holiday(10, 13, "Ngày Doanh nhân Việt Nam"),
        Holiday(10, 15, "Ngày Truyền thống Hội Liên hiệp Thanh niên"),
        Holiday(10, 20, "Ngày Phụ nữ Việt Nam"),
        Holiday(10, 31, "Halloween"),
        // Tháng 11
        Holiday(11, 9, "Ngày Pháp luật Việt Nam"),
        Holiday(11, 20, "Ngày Nhà giáo Việt Nam"),
        Holiday(11, 23, "Ngày thành lập Hội chữ thập đỏ Việt Nam"),
        Holiday(11, 24, "Ngày Văn hoá Việt Nam"),
        // Tháng 12
        Holiday(12, 1, "Ngày Thế giới phòng chống AIDS"),
        Holiday(12, 19, "Ngày toàn quốc kháng chiến"),
        Holiday(12, 22, "Ngày thành lập Quân đội Nhân dân Việt Nam"),
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

    data class AgendaItem(
        val whenLabel: String,
        val title: String,
        val allDay: Boolean,
        val isToday: Boolean,
        val timeMs: Long
    )

    private val vnDays = arrayOf(
        "", "Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"
    )

    fun agendaItems(from: Calendar = Calendar.getInstance(), count: Int = 40): List<AgendaItem> {
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
