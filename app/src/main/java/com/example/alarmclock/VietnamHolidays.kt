package com.example.alarmclock

import java.util.Calendar

/**
 * Ngày lễ cố định (dương lịch) Việt Nam.
 * Tết Âm lịch / Giỗ Tổ cần lịch âm hoặc API riêng.
 */
object VietnamHolidays {
    private val fixed = setOf(
        "01-01", // Tết Dương lịch
        "04-30", // Giải phóng miền Nam
        "05-01", // Quốc tế Lao động
        "09-02"  // Quốc khánh
    )

    fun isHoliday(cal: Calendar): Boolean {
        val key = "%02d-%02d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return fixed.contains(key)
    }

    fun skipToNextWorkingDay(cal: Calendar) {
        while (isHoliday(cal) ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        ) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}
