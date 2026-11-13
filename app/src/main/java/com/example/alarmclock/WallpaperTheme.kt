package com.example.alarmclock

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

/**
 * Màu giao diện app lấy theo hình nền điện thoại.
 *  - Android 8.1+: dùng màu chủ đạo hệ thống tính sẵn cho hình nền (không cần cấp quyền).
 *  - Nếu máy không trả về: tự phân tích ảnh hình nền (cần quyền đọc ảnh/bộ nhớ trên Android ≤ 12).
 * Bật / tắt trong Cài đặt. Khi đổi hình nền, lần mở app tiếp theo màu đổi theo.
 */
object WallpaperTheme {
    private const val PREFS = "wallpaper_theme"
    private const val DEFAULT_SEED = 0xFF5B6CFF.toInt()

    @Volatile private var cached: EventPalette? = null
    @Volatile private var cachedSeed = 0

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = sp(c).getBoolean("on", false)

    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("on", on).apply()
        invalidate()
    }

    /** Xóa bộ nhớ đệm để lần sau đọc lại màu hình nền. */
    fun invalidate() {
        cached = null
        cachedSeed = 0
        try { RingTheme.clearCache() } catch (_: Throwable) {}
    }

    /** Bảng màu theo hình nền, hoặc null khi chưa đọc được. */
    fun palette(c: Context): EventPalette? {
        val seed = seedColor(c) ?: return null
        if (cached != null && cachedSeed == seed) return cached
        val p = build(seed)
        cached = p
        cachedSeed = seed
        return p
    }

    /**
     * Gọi khi app mở lại: true nếu màu hình nền đã khác so với lần giao diện vừa vẽ,
     * để màn hình tự vẽ lại theo màu mới.
     */
    fun consumeChanged(c: Context): Boolean {
        if (!isEnabled(c)) return false
        invalidate()
        val seed = seedColor(c) ?: return false
        val applied = sp(c).getInt("applied_seed", 0)
        if (applied == seed) return false
        sp(c).edit().putInt("applied_seed", seed).apply()
        return applied != 0
    }

    fun markApplied(c: Context) {
        val seed = seedColor(c) ?: return
        sp(c).edit().putInt("applied_seed", seed).apply()
    }

    private fun seedColor(c: Context): Int? {
        var seed: Int? = null
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            try {
                val wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                seed = wc?.primaryColor?.toArgb()
            } catch (_: Throwable) {}
        }
        if (seed == null || isNeutral(seed)) {
            val fromBitmap = try { RingTheme.wallpaperBitmap(c)?.let { dominant(it) } } catch (_: Throwable) { null }
            if (fromBitmap != null) seed = fromBitmap
        }
        if (seed != null && isNeutral(seed)) seed = DEFAULT_SEED
        if (seed != null) {
            sp(c).edit().putInt("seed", seed).apply()
            return seed
        }
        val last = sp(c).getInt("seed", 0)
        return if (last != 0) last else null
    }

    private fun isNeutral(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1] < 0.12f
    }

    /** Màu nổi bật nhất của ảnh: gom theo góc màu, ưu tiên màu rực và sáng vừa. */
    private fun dominant(src: Bitmap): Int? {
        val small = Bitmap.createScaledBitmap(src, 40, 40, true)
        val bins = FloatArray(36)
        val sums = Array(36) { FloatArray(3) }
        val hsv = FloatArray(3)
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                Color.colorToHSV(small.getPixel(x, y), hsv)
                if (hsv[1] < 0.15f || hsv[2] < 0.2f) continue
                val w = hsv[1] * hsv[2]
                val b = ((hsv[0] / 10f).toInt()).coerceIn(0, 35)
                bins[b] += w
                sums[b][0] += hsv[0] * w
                sums[b][1] += hsv[1] * w
                sums[b][2] += hsv[2] * w
            }
        }
        var best = -1
        var bestW = 0f
        for (i in 0 until 36) if (bins[i] > bestW) { bestW = bins[i]; best = i }
        if (best < 0 || bestW <= 0f) return null
        val w = bins[best]
        return Color.HSVToColor(floatArrayOf(sums[best][0] / w, sums[best][1] / w, sums[best][2] / w))
    }

    private fun build(seed: Int): EventPalette {
        val hsv = FloatArray(3)
        Color.colorToHSV(seed, hsv)
        val h = hsv[0]
        val s = hsv[1].coerceIn(0.45f, 0.85f)
        val primary = Color.HSVToColor(floatArrayOf(h, s, 0.52f))
        val dark = Color.HSVToColor(floatArrayOf(h, s, 0.38f))
        val accent = Color.HSVToColor(floatArrayOf(h, (s + 0.05f).coerceAtMost(0.9f), 0.86f))
        val surface = Color.HSVToColor(floatArrayOf(h, 0.05f, 0.98f))
        return EventPalette(
            id = "wallpaper",
            primary = primary,
            primaryDark = dark,
            accent = accent,
            surface = surface,
            title = Lang.t("Màu theo hình nền", "Wallpaper colors"),
            message = ""
        )
    }
}
