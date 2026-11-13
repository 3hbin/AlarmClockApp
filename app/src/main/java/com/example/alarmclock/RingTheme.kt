package com.example.alarmclock

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * Giao diện màn hình báo thức đang đổ chuông: mẫu có sẵn, hình nền điện thoại,
 * ảnh người dùng chọn, hoặc màu tự chọn. Lưu trong SharedPreferences "ring_theme".
 */
object RingTheme {
    const val MODE_PRESET = 0
    const val MODE_WALLPAPER = 1
    const val MODE_IMAGE = 2
    const val MODE_CUSTOM = 3

    private const val LAYER_TAG = "ring_theme_layer"

    class Preset(
        val id: String,
        val name: String,
        val colors: IntArray,
        val accent: Int,
        val label: Int,
        val blob: Int
    )

    val presets: List<Preset> = listOf(
        Preset("dem", "Đêm xanh", intArrayOf(0xFF0D1B4A.toInt(), 0xFF0D1B4A.toInt()),
            0xFFA5B4FC.toInt(), 0xFFC7D2FE.toInt(), 0xFF4F46E5.toInt()),
        Preset("binhminh", "Bình minh", intArrayOf(0xFFB3365F.toInt(), 0xFFF0673F.toInt(), 0xFFF7A24B.toInt()),
            0xFFFFE4D1.toInt(), 0xFFFFF1E6.toInt(), 0xFFFFD2A8.toInt()),
        Preset("hoanghon", "Hoàng hôn", intArrayOf(0xFF2B1055.toInt(), 0xFF8E2A7F.toInt(), 0xFFE5527A.toInt()),
            0xFFFFD6E8.toInt(), 0xFFFFE8F2.toInt(), 0xFFFF8FB8.toInt()),
        Preset("daiduong", "Đại dương", intArrayOf(0xFF021B3A.toInt(), 0xFF0B5C8F.toInt(), 0xFF18A3B8.toInt()),
            0xFFB8F1FF.toInt(), 0xFFDFF9FF.toInt(), 0xFF38C6D9.toInt()),
        Preset("rung", "Rừng xanh", intArrayOf(0xFF062E22.toInt(), 0xFF0F6B4C.toInt(), 0xFF2EA36B.toInt()),
            0xFFBBF7D0.toInt(), 0xFFDCFCE7.toInt(), 0xFF34D399.toInt()),
        Preset("tim", "Tím mơ", intArrayOf(0xFF1A0B3B.toInt(), 0xFF4C2BB5.toInt(), 0xFF9B5CF6.toInt()),
            0xFFDDD0FF.toInt(), 0xFFEFE7FF.toInt(), 0xFFA78BFA.toInt()),
        Preset("anhdao", "Hồng anh đào", intArrayOf(0xFF5B1238.toInt(), 0xFFB83A78.toInt(), 0xFFF27DAA.toInt()),
            0xFFFFD9EA.toInt(), 0xFFFFEBF4.toInt(), 0xFFFFB3D1.toInt()),
        Preset("amoled", "Đen AMOLED", intArrayOf(0xFF000000.toInt(), 0xFF0B0B10.toInt(), 0xFF15151F.toInt()),
            0xFF9CA3AF.toInt(), 0xFFD1D5DB.toInt(), 0xFF374151.toInt()),
        Preset("nang", "Nắng mai", intArrayOf(0xFF7C2D12.toInt(), 0xFFC2570C.toInt(), 0xFFF5A524.toInt()),
            0xFFFFEDC2.toInt(), 0xFFFFF5DD.toInt(), 0xFFFFD27A.toInt())
    )

    data class Cfg(
        val mode: Int = MODE_PRESET,
        val presetId: String = "dem",
        val hue: Float = 230f,
        val sat: Float = 0.7f,
        val value: Float = 0.5f,
        val dim: Int = 40
    )

    class Style(
        val colors: IntArray,
        val accent: Int,
        val label: Int,
        val blob: Int,
        val photo: Bitmap? = null,
        val liveWallpaper: Boolean = false,
        val dim: Int = 0
    )

    private fun sp(c: Context) = c.getSharedPreferences("ring_theme", Context.MODE_PRIVATE)

    fun load(c: Context): Cfg {
        val s = sp(c)
        return Cfg(
            mode = s.getInt("mode", MODE_PRESET),
            presetId = s.getString("preset", "dem") ?: "dem",
            hue = s.getFloat("hue", 230f),
            sat = s.getFloat("sat", 0.7f),
            value = s.getFloat("value", 0.5f),
            dim = s.getInt("dim", 40)
        )
    }

    fun save(c: Context, cfg: Cfg) {
        sp(c).edit()
            .putInt("mode", cfg.mode)
            .putString("preset", cfg.presetId)
            .putFloat("hue", cfg.hue)
            .putFloat("sat", cfg.sat)
            .putFloat("value", cfg.value)
            .putInt("dim", cfg.dim)
            .apply()
    }

    fun imageFile(c: Context) = File(c.filesDir, "ring_bg.jpg")

    /** Cấu hình mặc định = giữ nguyên giao diện gốc trong XML. */
    fun isDefault(cfg: Cfg) = cfg.mode == MODE_PRESET && cfg.presetId == "dem"

    // ---- bộ nhớ đệm bitmap nền (tránh giải mã lại khi kéo thanh trượt)
    private var cacheKey = ""
    private var cacheBmp: Bitmap? = null
    fun clearCache() { cacheKey = ""; cacheBmp = null }


    fun wallpaperSnapshotFile(c: Context) = File(c.filesDir, "ring_wallpaper.jpg")

    /** Đọc trực tiếp hình nền hiện tại (cần quyền đọc bộ nhớ trên Android ≤12). Null nếu máy không cho. */
    private fun liveWallpaperBitmap(c: Context): Bitmap? {
        return try {
            val d = WallpaperManager.getInstance(c).drawable ?: return null
            val w = d.intrinsicWidth.coerceAtLeast(1)
            val h = d.intrinsicHeight.coerceAtLeast(1)
            val k = minOf(1f, 1440f / maxOf(w, h))
            d.toBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
        } catch (_: Throwable) { null }
    }

    /** Hình nền để dùng: đọc trực tiếp, nếu không được thì dùng bản chụp đã lưu. */
    fun wallpaperBitmap(c: Context): Bitmap? {
        if (cacheKey == "wp") return cacheBmp
        liveWallpaperBitmap(c)?.let { cacheKey = "wp"; cacheBmp = it; return it }
        val f = wallpaperSnapshotFile(c)
        if (!f.exists()) return null
        val key = "wps" + f.lastModified()
        if (cacheKey == key) return cacheBmp
        return BitmapFactory.decodeFile(f.absolutePath)?.also { cacheKey = key; cacheBmp = it }
    }

    /** Lưu bản chụp hình nền hiện tại để lúc báo thức không phụ thuộc quyền. */
    fun snapshotWallpaper(c: Context): Boolean {
        val bmp = liveWallpaperBitmap(c) ?: return false
        return try {
            wallpaperSnapshotFile(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (_: Throwable) { false }
    }

    fun hasWallpaperSource(c: Context): Boolean = wallpaperBitmap(c) != null

    fun style(c: Context, cfg: Cfg): Style {
        val dim = cfg.dim.coerceIn(0, 80)
        when (cfg.mode) {
            MODE_CUSTOM -> {
                val h = cfg.hue.coerceIn(0f, 360f)
                val s = cfg.sat.coerceIn(0.3f, 1f)
                val v = cfg.value.coerceIn(0.2f, 0.75f)
                val top = Color.HSVToColor(floatArrayOf(h, s, v * 0.55f))
                val bottom = Color.HSVToColor(floatArrayOf(h, s, v))
                return Style(
                    intArrayOf(top, bottom),
                    accent = Color.HSVToColor(floatArrayOf(h, s * 0.3f, 1f)),
                    label = Color.HSVToColor(floatArrayOf(h, s * 0.15f, 1f)),
                    blob = Color.HSVToColor(floatArrayOf(h, s * 0.6f, (v + 0.25f).coerceAtMost(1f)))
                )
            }
            MODE_IMAGE -> {
                val f = imageFile(c)
                val key = "img" + f.lastModified()
                val bmp = if (f.exists()) {
                    if (cacheKey == key) cacheBmp
                    else BitmapFactory.decodeFile(f.absolutePath)?.also { cacheKey = key; cacheBmp = it }
                } else null
                if (bmp != null) return photoStyle(bmp, dim)
            }
            MODE_WALLPAPER -> {
                val bmp = wallpaperBitmap(c)
                return if (bmp != null) photoStyle(bmp, dim)
                else Style(
                    intArrayOf(0xFF37474F.toInt(), 0xFF78909C.toInt()),
                    0xFFE8EAFF.toInt(), Color.WHITE, 0, liveWallpaper = true, dim = dim
                )
            }
        }
        val p = presets.firstOrNull { it.id == cfg.presetId } ?: presets[0]
        return Style(p.colors, p.accent, p.label, p.blob)
    }

    private fun photoStyle(bmp: Bitmap, dim: Int) =
        Style(intArrayOf(Color.BLACK, Color.BLACK), 0xFFE8EAFF.toInt(), Color.WHITE, 0, photo = bmp, dim = dim)

    /** Vẽ nền (gradient / ảnh / hình nền sống) vào root. window = null khi chỉ xem trước. */
    fun install(root: FrameLayout, st: Style, window: Window?) {
        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i).tag == LAYER_TAG) root.removeViewAt(i)
        }
        val ctx = root.context
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        if (st.photo != null) {
            root.setBackgroundColor(Color.BLACK)
            val iv = ImageView(ctx).apply {
                tag = LAYER_TAG
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(st.photo)
            }
            root.addView(iv, 0, FrameLayout.LayoutParams(match, match))
        } else if (st.liveWallpaper && window != null) {
            root.setBackgroundColor(Color.TRANSPARENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, st.colors)
        }
        if (st.photo != null || (st.liveWallpaper && window != null)) {
            val scrim = View(ctx).apply {
                tag = LAYER_TAG
                setBackgroundColor(Color.argb(st.dim * 255 / 100, 0, 0, 0))
            }
            root.addView(scrim, if (st.photo != null) 1 else 0, FrameLayout.LayoutParams(match, match))
        }
    }

    fun colorize(st: Style, blobs: List<View>, title: TextView?, label: TextView?) {
        val overPhoto = st.photo != null || st.liveWallpaper
        for (v in blobs) {
            if (overPhoto) {
                v.visibility = View.GONE
            } else {
                v.visibility = View.VISIBLE
                v.backgroundTintList = ColorStateList.valueOf(st.blob)
            }
        }
        title?.setTextColor(st.accent)
        label?.setTextColor(st.label)
    }

    /** Gọi trong AlarmRingActivity sau khi inflate layout. */
    fun applyToRing(activity: Activity, root: View, blobs: List<View>, title: TextView?, label: TextView?) {
        val cfg = load(activity)
        if (isDefault(cfg)) return
        val fl = root as? FrameLayout ?: return
        clearCache()
        val st = style(activity, cfg)
        install(fl, st, activity.window)
        colorize(st, blobs, title, label)
    }

    /** Lưu ảnh người dùng chọn: thu nhỏ ≤1600px, xoay đúng chiều, JPEG. */
    fun saveImage(c: Context, uri: Uri): Boolean {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val rot = try {
                c.contentResolver.openInputStream(uri)?.use {
                    when (android.media.ExifInterface(it).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (_: Exception) { 0 }
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            imageFile(c).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return true
        } catch (_: Throwable) {
            return false
        }
    }
}
