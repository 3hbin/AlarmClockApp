package com.example.alarmclock.feature

/**
 * Các tính năng đã implement thực tế:
 * 1. Math/Shake Challenge - AlarmRingActivity
 * 2. Sleep Cycle Calculator - SleepCycleCalculator.kt
 * 3. TTS Voice Note - TtsHelper.kt
 * 4. Crescendo - SoundHelper
 * 5. Photo Verification - scaffold (CHALLENGE_PHOTO)
 * 8. Power Nap - TimerActivity
 * 9. Random Sound - SoundHelper
 * 10. Sleep Habits Tracker - AlarmRepository.logSleep
 * 13. Wake-up Light - FlashHelper
 * 14. Voice Alarm / TTS Note - TtsHelper
 * 15. Vietnam Holiday Auto-Skip - VietnamHolidays + AlarmScheduler
 * 16. Dream Journal - DreamRecorderHelper
 * 17. Strict Anti-Snooze - flag isStrictAntiSnooze
 *
 * Các tính năng cần API / backend (giữ interface):
 */

interface MusicIntegration {
    fun playSpotify(uri: String)
    fun playYouTubeMusic(query: String)
}

interface GroupAlarmSync {
    fun joinGroup(groupId: String)
    fun notifyMembers(alarmId: Long)
}

interface CloudSync {
    fun pushAlarms()
    fun pullAlarms()
}

interface SmartHomeApi {
    fun turnOnLights(brightness: Int)
    fun setThermostat(celsius: Float)
}

interface LocationAlarm {
    fun scheduleNear(lat: Double, lng: Double, radiusM: Float)
}

interface EmergencySms {
    fun sendAlert(phone: String, message: String)
}
