package com.example.alarmclock

/**
 * Chu kỳ ngủ trung bình ~90 phút.
 * Gợi ý giờ đi ngủ để thức dậy đúng chu kỳ.
 */
object SleepCycleCalculator {
    private const val CYCLE_MINUTES = 90
    private const val FALL_ASLEEP_BUFFER = 15 // phút để chìm vào giấc ngủ

    fun suggestBedtimes(wakeHour: Int, wakeMinute: Int, maxCycles: Int = 6): List<Pair<Int, Int>> {
        val wakeTotal = wakeHour * 60 + wakeMinute
        val list = mutableListOf<Pair<Int, Int>>()
        for (c in 1..maxCycles) {
            var bed = wakeTotal - (c * CYCLE_MINUTES) - FALL_ASLEEP_BUFFER
            if (bed < 0) bed += 24 * 60
            list.add(bed / 60 to bed % 60)
        }
        return list
    }

    fun formatTime(h: Int, m: Int): String = "%02d:%02d".format(h, m)
}
