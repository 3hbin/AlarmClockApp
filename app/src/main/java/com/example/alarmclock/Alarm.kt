package com.example.alarmclock

data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var isEnabled: Boolean = true,
    var label: String = "Báo thức",
    var repeatMode: Int = REPEAT_DAILY,
    var snoozeMinutes: Int = 5,
    var ringtoneUri: String? = null,
    var challengeType: Int = CHALLENGE_NONE,
    var shakeTargetCount: Int = 10,
    var skipHolidays: Boolean = false,
    var isStrictAntiSnooze: Boolean = false,
    var voiceNote: String? = null,
    var useCrescendo: Boolean = true,
    var group: String = "Chung",
    var useWeekendSchedule: Boolean = false,
    var weekendHour: Int = -1,
    var weekendMinute: Int = -1,
    var routineOn: Boolean = false,
    var routineWeather: Boolean = true,
    var routineCalendar: Boolean = true,
    var routineTasks: Boolean = true,
    var routineTomorrow: Boolean = true,
    var qrToken: String = ""
) {
    companion object {
        const val REPEAT_ONCE = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_WEEKDAYS = 2

        const val CHALLENGE_NONE = 0
        const val CHALLENGE_MATH = 1          // 1 bài (dễ)
        const val CHALLENGE_SHAKE = 2         // lắc (shakeTargetCount)
        const val CHALLENGE_PHOTO = 3
        const val CHALLENGE_FACE = 4          // quét mặt cơ bản
        const val CHALLENGE_BIOMETRIC = 5
        const val CHALLENGE_READ = 6          // đọc câu (không lặp)
        const val CHALLENGE_MATH10 = 7        // 10 bài toán liên tiếp
        const val CHALLENGE_SHAKE100 = 8      // lắc 100 lần
        const val CHALLENGE_TAP200 = 9        // bấm 200 lần (chống auto-click)
        const val CHALLENGE_FACE_EXPR = 10    // cười / giận / nhắm mắt / …
        const val CHALLENGE_ALL = 11         // tất cả thử thách (khó)
        const val CHALLENGE_ALL_EASY = 12    // tất cả — dễ, không mất ngủ
        const val CHALLENGE_QR = 13          // quét mã QR

        fun challengeLabel(type: Int): String = when (type) {
            CHALLENGE_MATH -> Lang.t(null, "Giải toán (1 bài)", "Math (1)")
            CHALLENGE_SHAKE -> Lang.t(null, "Lắc máy", "Shake")
            CHALLENGE_PHOTO -> Lang.t(null, "Chụp ảnh", "Photo")
            CHALLENGE_FACE -> Lang.t(null, "Quét mặt", "Face scan")
            CHALLENGE_BIOMETRIC -> Lang.t(null, "Vân tay/Face hệ thống", "Fingerprint / Face")
            CHALLENGE_READ -> Lang.t(null, "Chọn từ nhanh (10s)", "Pick words (10s)")
            CHALLENGE_MATH10 -> Lang.t(null, "Giải 10 bài toán", "10 math problems")
            CHALLENGE_SHAKE100 -> Lang.t(null, "Lắc máy 100 lần", "Shake 100 times")
            CHALLENGE_TAP200 -> Lang.t(null, "Bấm 200 lần", "Tap 200 times")
            CHALLENGE_FACE_EXPR -> Lang.t(null, "10 biểu cảm dễ", "10 easy expressions")
            CHALLENGE_ALL -> Lang.t(null, "TẤT CẢ thử thách (khó)", "ALL challenges (hard)")
            CHALLENGE_ALL_EASY -> Lang.t(null, "TẤT CẢ dễ", "ALL easy")
            CHALLENGE_QR -> Lang.t(null, "Quét mã QR", "Scan QR")
            else -> Lang.t(null, "Không", "None")
        }
    }

    fun hourFor(cal: java.util.Calendar): Pair<Int, Int> {
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val weekend = dow == java.util.Calendar.SATURDAY || dow == java.util.Calendar.SUNDAY
        return if (useWeekendSchedule && weekend && weekendHour >= 0)
            weekendHour to weekendMinute.coerceIn(0, 59)
        else hour to minute
    }

    fun getRepeatText(): String {
        val base = when (repeatMode) {
            REPEAT_ONCE -> Lang.t(null, "Chỉ 1 lần", "Once")
            REPEAT_DAILY -> Lang.t(null, "Hàng ngày", "Every day")
            REPEAT_WEEKDAYS -> Lang.t(null, "Thứ 2 - Thứ 6", "Mon – Fri")
            else -> Lang.t(null, "Hàng ngày", "Every day")
        }
        val g = group.trim().ifBlank { Lang.t(null, "Chung", "General") }
        val we = if (useWeekendSchedule && weekendHour >= 0)
            " · CN ${"%02d:%02d".format(weekendHour, weekendMinute)}" else ""
        return if (g != Lang.t(null, "Chung", "General")) "$g · $base$we" else "$base$we"
    }

    fun getChallengeText(): String = challengeLabel(challengeType)
}
