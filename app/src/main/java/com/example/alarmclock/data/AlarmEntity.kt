package com.example.alarmclock.data

/**
 * Room Entity stub – chuyển đổi từ model Alarm hiện tại.
 * Thêm dependency room-runtime + room-ktx + kapt/ksp compiler để kích hoạt.
 */
// @Entity(tableName = "alarms")
data class AlarmEntity(
    // @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: String = "",          // "1,2,3,4,5"
    val ringtoneUri: String? = null,
    val challengeType: String = "MATH",   // MATH, SHAKE, PHOTO, NONE
    val isCrescendo: Boolean = true,
    val isStrictAntiSnooze: Boolean = false,
    val skipHolidays: Boolean = false,    // Vietnam Holiday Auto-Skip
    val locationLat: Double? = null,      // GPS Location-based
    val locationLng: Double? = null,
    val groupId: String? = null,          // Social Group Alarm
    val voiceNote: String? = null,        // TTS Note
    val createdAt: Long = System.currentTimeMillis()
)
