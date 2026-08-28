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
        const val CHALLENGE_MATH = 1
        const val CHALLENGE_SHAKE = 2
        const val CHALLENGE_PHOTO = 3
        const val CHALLENGE_FACE = 4
        const val CHALLENGE_BIOMETRIC = 5
    }

    fun getRepeatText(): String = when (repeatMode) {
        REPEAT_ONCE -> "Chỉ 1 lần"
        REPEAT_DAILY -> "Hàng ngày"
        REPEAT_WEEKDAYS -> "Thứ 2 - Thứ 6"
        else -> "Hàng ngày"
    }

    fun getChallengeText(): String = when (challengeType) {
        CHALLENGE_MATH -> "Giải toán"
        CHALLENGE_SHAKE -> "Lắc máy"
        CHALLENGE_PHOTO -> "Chụp ảnh"
            CHALLENGE_FACE -> "Quét mặt"
            CHALLENGE_BIOMETRIC -> "Vân tay/Face hệ thống"
        else -> "Không"
    }
}
