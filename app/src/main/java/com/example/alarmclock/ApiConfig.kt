package com.example.alarmclock

/**
 * Keys lấy từ BuildConfig (đã gắn trong build.gradle.kts).
 * CẢNH BÁO: Không commit key lên public repo nếu project public.
 * Nên chuyển sang local.properties + secrets khi phát hành.
 */
object ApiConfig {
    val spotifyClientId: String get() = BuildConfig.SPOTIFY_CLIENT_ID
    val youtubeApiKey: String get() = BuildConfig.YOUTUBE_API_KEY
    val weatherApiKey: String get() = BuildConfig.WEATHER_API_KEY
}
