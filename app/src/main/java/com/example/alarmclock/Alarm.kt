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
    var useCrescendo: Boolean = true
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

        fun challengeLabel(type: Int): String = when (type) {
            CHALLENGE_MATH -> "Giải toán (1 bài)"
            CHALLENGE_SHAKE -> "Lắc máy"
            CHALLENGE_PHOTO -> "Chụp ảnh"
            CHALLENGE_FACE -> "Quét mặt"
            CHALLENGE_BIOMETRIC -> "Vân tay/Face hệ thống"
            CHALLENGE_READ -> "Chọn từ nhanh (10s)"
            CHALLENGE_MATH10 -> "Giải 10 bài toán"
            CHALLENGE_SHAKE100 -> "Lắc máy 100 lần"
            CHALLENGE_TAP200 -> "Bấm 200 lần"
            CHALLENGE_FACE_EXPR -> "10 biểu cảm dễ"
            CHALLENGE_ALL -> "TẤT CẢ thử thách (khó)"
            else -> "Không"
        }
    }

    fun getRepeatText(): String = when (repeatMode) {
        REPEAT_ONCE -> "Chỉ 1 lần"
        REPEAT_DAILY -> "Hàng ngày"
        REPEAT_WEEKDAYS -> "Thứ 2 - Thứ 6"
        else -> "Hàng ngày"
    }

    fun getChallengeText(): String = challengeLabel(challengeType)
}
