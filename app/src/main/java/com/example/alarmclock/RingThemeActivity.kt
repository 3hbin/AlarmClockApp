package com.example.alarmclock

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Cài đặt giao diện màn hình báo thức: mẫu / hình nền máy / ảnh / màu tự chọn. Tự lưu khi đổi. */
class RingThemeActivity : AppCompatActivity() {

    private lateinit var cfg: RingTheme.Cfg
    private var viewMode = 0
    private var night = false
    private var cText = 0
    private var cSub = 0

    private lateinit var previewRoot: FrameLayout
    private lateinit var pvTitle: TextView
    private lateinit var pvLabel: TextView
    private val blobs = ArrayList<View>()
    private val sections = HashMap<Int, View>()
    private val chips = HashMap<Int, TextView>()
    private val tiles = HashMap<String, FrameLayout>()
    private val ticks = HashMap<String, TextView>()
    private lateinit var dimBox: View
    private lateinit var imageStatus: TextView
    private lateinit var wallpaperInfo: TextView
    private lateinit var hueBar: SeekBar
    private lateinit var satBar: SeekBar
    private lateinit var valBar: SeekBar
    private lateinit var dimBar: SeekBar
    private lateinit var dimLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = UiTranslate.tr("Giao diện báo thức")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        night = ThemeFix.isNight(this)
        cText = if (night) Color.WHITE else 0xFF202124.toInt()
        cSub = if (night) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        val pageBg = if (night) Color.BLACK else 0xFFF6F7FB.toInt()
        try {
            window.decorView.setBackgroundColor(pageBg)
            if (night) {
                window.statusBarColor = Color.BLACK
                window.navigationBarColor = Color.BLACK
            }
        } catch (_: Exception) {}

        RingTheme.clearCache()
        cfg = RingTheme.load(this)
        viewMode = cfg.mode

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(36))
        }
        col.addView(buildPreview(), LinearLayout.LayoutParams(dp(200), dp(340)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(16)
        })
        col.addView(buildChips())
        col.addView(buildPresetSection(), sectionLp())
        col.addView(buildWallpaperSection(), sectionLp())
        col.addView(buildImageSection(), sectionLp())
        col.addView(buildCustomSection(), sectionLp())
        col.addView(buildDimSection(), sectionLp())
        col.addView(TextView(this).apply {
            text = UiTranslate.tr("Thay đổi được lưu tự động và áp dụng từ lần báo thức tiếp theo.")
            textSize = 12f
            setTextColor(cSub)
        }, sectionLp())
        col.addView(TextView(this).apply {
            text = UiTranslate.tr("Đặt lại mặc định")
            textSize = 15f
            setTextColor(0xFFD93025.toInt())
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { resetAll() }
        }, sectionLp())

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pageBg)
            addView(col)
        })
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun sectionLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(14) }

    // ---------------------------------------------------------------- xem trước
    private fun buildPreview(): FrameLayout {
        val fl = FrameLayout(this)
        previewRoot = fl
        fl.clipToOutline = true
        fl.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
            }
        }
        val top = View(this).apply { setBackgroundResource(R.drawable.bg_ring_blob_top); alpha = 0.55f }
        val bot = View(this).apply { setBackgroundResource(R.drawable.bg_ring_blob_bottom); alpha = 0.4f }
        blobs.add(top)
        blobs.add(bot)
        fl.addView(top, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.TOP or Gravity.END).apply {
            topMargin = -dp(30)
            marginEnd = -dp(20)
        })
        fl.addView(bot, FrameLayout.LayoutParams(dp(100), dp(100), Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = dp(20)
            marginStart = -dp(25)
        })
        pvTitle = TextView(this).apply {
            text = UiTranslate.tr("BÁO THỨC")
            textSize = 10f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
        }
        val icon = TextView(this).apply {
            text = "⏰"
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
        }
        val time = TextView(this).apply {
            text = "07:00"
            textSize = 34f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        pvLabel = TextView(this).apply {
            text = UiTranslate.tr("Báo thức")
            textSize = 13f
            gravity = Gravity.CENTER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(22), 0, 0)
            addView(pvTitle)
            addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)).apply { topMargin = dp(10) })
            addView(time, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
            addView(pvLabel)
        }
        fl.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))
        val snooze = TextView(this).apply {
            text = UiTranslate.tr("Báo lại (5 phút)")
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xCCFFFFFF.toInt())
            }
        }
        val stop = TextView(this).apply {
            text = UiTranslate.tr("Tắt")
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xFFEF4444.toInt())
            }
        }
        val btns = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), dp(14))
            addView(snooze, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)))
            addView(stop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                topMargin = dp(8)
            })
        }
        fl.addView(btns, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))
        return fl
    }

    private fun updatePreview() {
        val st = RingTheme.style(this, cfg)
        RingTheme.install(previewRoot, st, null)
        RingTheme.colorize(st, blobs, pvTitle, pvLabel)
    }

    // ---------------------------------------------------------------- chọn kiểu
    private fun buildChips(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf(
            RingTheme.MODE_PRESET to "Mẫu có sẵn",
            RingTheme.MODE_WALLPAPER to "Hình nền máy",
            RingTheme.MODE_IMAGE to "Ảnh của bạn",
            RingTheme.MODE_CUSTOM to "Màu tự chọn"
        )
        for ((m, name) in names) {
            val tv = TextView(this).apply {
                text = name
                textSize = 14f
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener { selectMode(m) }
            }
            chips[m] = tv
            row.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun selectMode(m: Int) {
        viewMode = m
        if (m != RingTheme.MODE_IMAGE || RingTheme.imageFile(this).exists()) {
            cfg = cfg.copy(mode = m)
            RingTheme.save(this, cfg)
        }
        if (m == RingTheme.MODE_WALLPAPER) {
            if (needWallpaperPermission()) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 82)
            } else {
                grabWallpaper()
            }
        }
        refresh()
    }

    // ---------------------------------------------------------------- mẫu có sẵn
    private fun buildPresetSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = RingTheme.presets
        var i = 0
        while (i < list.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until 3) {
                val p = list.getOrNull(i + j)
                if (p == null) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                    continue
                }
                val tile = FrameLayout(this)
                tile.setOnClickListener {
                    cfg = cfg.copy(mode = RingTheme.MODE_PRESET, presetId = p.id)
                    viewMode = RingTheme.MODE_PRESET
                    RingTheme.save(this, cfg)
                    refresh()
                }
                val nameTv = TextView(this).apply {
                    text = p.name
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
                }
                val tick = TextView(this).apply {
                    text = "✓"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    visibility = View.GONE
                }
                tile.addView(nameTv, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply { setMargins(dp(8), 0, 0, dp(8)) })
                tile.addView(tick, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply { setMargins(0, dp(6), dp(8), 0) })
                tiles[p.id] = tile
                ticks[p.id] = tick
                row.addView(tile, LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            box.addView(row)
            i += 3
        }
        sections[RingTheme.MODE_PRESET] = box
        return box
    }

    private fun refreshTiles() {
        for (p in RingTheme.presets) {
            val tile = tiles[p.id] ?: continue
            val selected = viewMode == RingTheme.MODE_PRESET && cfg.presetId == p.id
            tile.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, p.colors).apply {
                cornerRadius = dp(16).toFloat()
                if (selected) setStroke(dp(3), 0xFF1A73E8.toInt())
            }
            ticks[p.id]?.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    // ---------------------------------------------------------------- hình nền máy
    private fun buildWallpaperSection(): View {
        val tv = TextView(this).apply {
            textSize = 14f
            setTextColor(cSub)
        }
        wallpaperInfo = tv
        sections[RingTheme.MODE_WALLPAPER] = tv
        return tv
    }

    /** Android ≤12 cần quyền đọc bộ nhớ để app lấy được hình nền. */
    private fun needWallpaperPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) return false
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun grabWallpaper() {
        Thread {
            RingTheme.clearCache()
            RingTheme.snapshotWallpaper(applicationContext)
            RingTheme.clearCache()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                refresh()
            }
        }.start()
    }

    @Deprecated("permissions")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 82) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                grabWallpaper()
            } else {
                Toast.makeText(this, UiTranslate.tr("Chưa cấp quyền đọc hình nền"), Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    // ---------------------------------------------------------------- ảnh của bạn
    private fun buildImageSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val btn = TextView(this).apply {
            text = UiTranslate.tr("Chọn ảnh từ thư viện")
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF1A73E8.toInt())
            }
            setOnClickListener { pickImage() }
        }
        imageStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(cSub)
            setPadding(0, dp(8), 0, 0)
        }
        box.addView(btn)
        box.addView(imageStatus)
        sections[RingTheme.MODE_IMAGE] = box
        return box
    }

    private fun pickImage() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 81)
        } catch (_: Exception) {
            Toast.makeText(this, UiTranslate.tr("Không mở được thư viện ảnh"), Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 81 && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            Thread {
                val ok = RingTheme.saveImage(applicationContext, uri)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (ok) {
                        RingTheme.clearCache()
                        cfg = cfg.copy(mode = RingTheme.MODE_IMAGE)
                        viewMode = RingTheme.MODE_IMAGE
                        RingTheme.save(this, cfg)
                        refresh()
                    } else {
                        Toast.makeText(this, UiTranslate.tr("Không đọc được ảnh này"), Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    // ---------------------------------------------------------------- màu tự chọn
    private fun buildCustomSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun title(t: String) = TextView(this).apply {
            text = t
            textSize = 14f
            setTextColor(cText)
            setPadding(0, dp(8), 0, 0)
        }
        fun applyCustom() {
            cfg = cfg.copy(
                mode = RingTheme.MODE_CUSTOM,
                hue = hueBar.progress.toFloat(),
                sat = 0.3f + satBar.progress / 100f * 0.7f,
                value = 0.2f + valBar.progress / 100f * 0.55f
            )
            viewMode = RingTheme.MODE_CUSTOM
            RingTheme.save(this, cfg)
            refresh()
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) applyCustom()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        hueBar = SeekBar(this).apply {
            max = 360
            setOnSeekBarChangeListener(listener)
            val rainbow = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
                    0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
                )
            ).apply { cornerRadius = dp(6).toFloat() }
            background = InsetDrawable(rainbow, 0, dp(9), 0, dp(9))
            progressTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        satBar = SeekBar(this).apply { max = 100; setOnSeekBarChangeListener(listener) }
        valBar = SeekBar(this).apply { max = 100; setOnSeekBarChangeListener(listener) }
        box.addView(title("Màu"))
        box.addView(hueBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)))
        box.addView(title("Độ rực"))
        box.addView(satBar)
        box.addView(title("Độ sáng"))
        box.addView(valBar)
        sections[RingTheme.MODE_CUSTOM] = box
        return box
    }

    private fun syncCustomBars() {
        hueBar.progress = cfg.hue.toInt().coerceIn(0, 360)
        satBar.progress = ((cfg.sat - 0.3f) / 0.7f * 100f).toInt().coerceIn(0, 100)
        valBar.progress = ((cfg.value - 0.2f) / 0.55f * 100f).toInt().coerceIn(0, 100)
    }

    // ---------------------------------------------------------------- độ tối nền
    private fun buildDimSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dimLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(cText)
        }
        dimBar = SeekBar(this).apply {
            max = 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    dimLabel.text = "Độ tối nền: $p%"
                    if (fromUser) {
                        cfg = cfg.copy(dim = p)
                        RingTheme.save(this@RingThemeActivity, cfg)
                        updatePreview()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(dimLabel)
        box.addView(dimBar)
        dimBox = box
        return box
    }

    // ---------------------------------------------------------------- chung
    private fun resetAll() {
        cfg = RingTheme.Cfg()
        viewMode = RingTheme.MODE_PRESET
        RingTheme.save(this, cfg)
        syncCustomBars()
        dimBar.progress = cfg.dim
        refresh()
        Toast.makeText(this, UiTranslate.tr("Đã đặt lại giao diện mặc định"), Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        for ((m, tv) in chips) {
            val sel = m == viewMode
            tv.setTextColor(if (sel) Color.WHITE else cText)
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (sel) 0xFF1A73E8.toInt() else if (night) 0xFF1E1E1E.toInt() else 0xFFE8EAED.toInt())
            }
        }
        for ((m, v) in sections) v.visibility = if (m == viewMode) View.VISIBLE else View.GONE
        dimBox.visibility =
            if (viewMode == RingTheme.MODE_WALLPAPER || viewMode == RingTheme.MODE_IMAGE) View.VISIBLE else View.GONE
        imageStatus.text =
            if (RingTheme.imageFile(this).exists()) "Đã chọn ảnh. Bấm nút trên để đổi ảnh khác."
            else "Chưa chọn ảnh nào."
        if (dimBar.progress != cfg.dim) dimBar.progress = cfg.dim
        dimLabel.text = "Độ tối nền: ${cfg.dim}%"
        if (viewMode == RingTheme.MODE_CUSTOM && hueBar.progress == 0 && satBar.progress == 0 && valBar.progress == 0) {
            syncCustomBars()
        }
        wallpaperInfo.text = if (RingTheme.hasWallpaperSource(this)) {
            "Màn hình báo thức đang dùng hình nền hiện tại của điện thoại. " +
                "Kéo thanh \"Độ tối nền\" để chữ dễ đọc hơn. Nếu bạn đổi hình nền máy, hãy bấm lại \"Hình nền máy\" để cập nhật."
        } else if (needWallpaperPermission()) {
            "Cần cho phép app đọc hình nền. Bấm lại \"Hình nền máy\" rồi chọn Cho phép."
        } else {
            "Máy này không cho app đọc hình nền. Màn hình báo thức sẽ cố hiện hình nền máy phía sau; " +
                "nếu không thấy, hãy dùng \"Ảnh của bạn\" và chọn chính ảnh hình nền."
        }
        refreshTiles()
        updatePreview()
    }
}
