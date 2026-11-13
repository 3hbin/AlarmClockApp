package com.example.alarmclock

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Màn Thời tiết: hiện tại, chỉ số, mặt trời mọc/lặn, dự báo theo giờ và theo ngày. */
class WeatherActivity : AppCompatActivity() {

    private lateinit var bg: View
    private lateinit var tvCity: TextView
    private lateinit var tvIcon: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvFeels: TextView
    private lateinit var tvStatus: TextView
    private lateinit var statsRow1: LinearLayout
    private lateinit var statsRow2: LinearLayout
    private lateinit var sunRow: LinearLayout
    private lateinit var hoursRow: LinearLayout
    private lateinit var daysBox: LinearLayout
    private lateinit var btnPerm: TextView
    private lateinit var content: LinearLayout

    private var bgColors: IntArray? = null
    private var bgAnim: ValueAnimator? = null
    private var iconAnim: ObjectAnimator? = null
    private var tempAnim: ValueAnimator? = null
    private var loading = false
    private val main = Handler(Looper.getMainLooper())
    private val autoRefresh = object : Runnable {
        override fun run() { refresh(); main.postDelayed(this, 30 * 60_000L) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeatherService.init(this)
        buildUi()
        setContentView(rootLayout)
        WeatherService.load(this)?.let { render(it, offline = true) }
        refresh()
    }

    private lateinit var rootLayout: FrameLayout

    private fun buildUi() {
        rootLayout = FrameLayout(this)
        bg = View(this)
        rootLayout.addView(bg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(28))
        }
        scroll.addView(content)

        // Thanh trên
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_chat_back)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            contentDescription = Lang.t(this@WeatherActivity, "Quay lại", "Back")
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        top.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.cloud_24)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(6) })
        top.addView(TextView(this).apply {
            text = Lang.t(this@WeatherActivity, "Thời tiết", "Weather")
            textSize = 19f; setTextColor(0xFFFFFFFF.toInt()); paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        top.addView(TextView(this).apply {
            text = "⟳"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            contentDescription = Lang.t(this@WeatherActivity, "Làm mới", "Refresh")
            setOnClickListener { refresh(force = true) }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        content.addView(top)

        tvCity = TextView(this).apply {
            textSize = 17f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; paint.isFakeBoldText = true
            setPadding(0, dp(14), 0, 0)
        }
        content.addView(tvCity)

        tvIcon = TextView(this).apply { textSize = 88f; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
        content.addView(tvIcon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        tvTemp = TextView(this).apply {
            textSize = 72f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; paint.isFakeBoldText = true
        }
        content.addView(tvTemp, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        tvDesc = TextView(this).apply { textSize = 17f; setTextColor(0xEEFFFFFF.toInt()); gravity = Gravity.CENTER }
        content.addView(tvDesc)

        tvFeels = TextView(this).apply { textSize = 14f; setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(10)) }
        content.addView(tvFeels)

        tvStatus = TextView(this).apply { textSize = 12f; setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER }
        content.addView(tvStatus)

        btnPerm = TextView(this).apply {
            text = Lang.t(this@WeatherActivity, "Cho phép vị trí để xem thời tiết nơi bạn ở", "Allow location to see weather where you are")
            textSize = 13f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0x33FFFFFF) }
            visibility = View.GONE
            setOnClickListener {
                ActivityCompat.requestPermissions(this@WeatherActivity,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 9401)
            }
        }
        content.addView(btnPerm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10)
        })

        statsRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        statsRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(sectionTitle(Lang.t(this, "Chi tiết", "Details")))
        content.addView(statsRow1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        content.addView(statsRow2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sunRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(sunRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(sectionTitle(Lang.t(this, "Theo giờ", "Hourly")))
        val hScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        hoursRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hScroll.addView(hoursRow)
        content.addView(hScroll)

        content.addView(sectionTitle(Lang.t(this, "5 ngày tới", "Next 5 days")))
        daysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(daysBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        rootLayout.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(0xCCFFFFFF.toInt()); paint.isFakeBoldText = true
        setPadding(dp(4), dp(18), 0, dp(8))
    }

    private fun tile(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0x26FFFFFF) }
        addView(TextView(this@WeatherActivity).apply { text = label; textSize = 12f; setTextColor(0xCCFFFFFF.toInt()) })
        addView(TextView(this@WeatherActivity).apply { text = value; textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); paint.isFakeBoldText = true })
    }

    private fun tileLp() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    // ------------------------------------------------------------------ dữ liệu
    private fun refresh(force: Boolean = false) {
        if (loading) return
        val hasPerm = LocationPlaceHelper.hasPermission(this)
        btnPerm.visibility = if (hasPerm) View.GONE else View.VISIBLE
        loading = true
        if (!force) tvStatus.text = Lang.t(this, "Đang cập nhật…", "Updating…")
        Thread {
            var bundle: WeatherService.Bundle? = null
            var err: String? = null
            try {
                val place = LocationPlaceHelper.lastKnown(this)
                val lat = place?.latitude ?: 21.0278
                val lon = place?.longitude ?: 105.8342
                val fallback = place == null
                val name = if (fallback) "Hà Nội" else cityName(lat, lon)
                bundle = WeatherService.fetch(lat, lon, name, fallback, ApiConfig.weatherApiKey)
            } catch (e: Exception) {
                err = e.message ?: "error"
            }
            main.post {
                loading = false
                if (isFinishing || isDestroyed) return@post
                when {
                    bundle != null -> render(bundle, offline = false)
                    WeatherService.load(this) != null -> render(WeatherService.load(this)!!, offline = true)
                    else -> {
                        tvStatus.text = Lang.t(this, "Không lấy được dữ liệu. Kiểm tra Internet rồi bấm làm mới.",
                            "Could not load data. Check Internet and tap refresh.")
                    }
                }
            }
        }.start()
    }

    private fun cityName(lat: Double, lon: Double): String = try {
        val a = android.location.Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: Lang.t(this, "Vị trí hiện tại", "Current location")
    } catch (_: Exception) { Lang.t(this, "Vị trí hiện tại", "Current location") }

    // ------------------------------------------------------------------ hiển thị
    private fun render(b: WeatherService.Bundle, offline: Boolean) {
        val n = b.now
        tvCity.text = "📍 " + n.city
        tvIcon.text = WeatherService.iconFor(n.id)
        tvDesc.text = WeatherService.descFor(this, n.id)
        tvFeels.text = Lang.t(this, "Cảm nhận ${n.feels.toInt()}°C", "Feels like ${n.feels.toInt()}°C")

        animateTemp(n.temp)
        animateBackground(WeatherService.gradientFor(n.id))
        startIconFloat()

        statsRow1.removeAllViews(); statsRow2.removeAllViews(); sunRow.removeAllViews()
        statsRow1.addView(tile(Lang.t(this, "Độ ẩm", "Humidity"), "${n.humidity}%"), tileLp())
        statsRow1.addView(tile(Lang.t(this, "Gió", "Wind"), "${n.windKmh.toInt()} km/h"), tileLp())
        statsRow2.addView(tile(Lang.t(this, "Áp suất", "Pressure"), "${n.pressure} hPa"), tileLp())
        statsRow2.addView(tile(Lang.t(this, "Tầm nhìn", "Visibility"), String.format(Locale.US, "%.1f km", n.visibilityKm)), tileLp())
        if (n.sunrise > 0 && n.sunset > 0) {
            sunRow.addView(tile(Lang.t(this, "Mặt trời mọc", "Sunrise"), hm(n.sunrise)), tileLp())
            sunRow.addView(tile(Lang.t(this, "Mặt trời lặn", "Sunset"), hm(n.sunset)), tileLp())
        }

        hoursRow.removeAllViews()
        b.hours.forEach { s ->
            hoursRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0x26FFFFFF) }
                addView(TextView(this@WeatherActivity).apply { text = hm(s.time); textSize = 12f; setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER })
                addView(TextView(this@WeatherActivity).apply { text = WeatherService.iconFor(s.id); textSize = 22f; gravity = Gravity.CENTER })
                addView(TextView(this@WeatherActivity).apply { text = "${s.temp.toInt()}°"; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; paint.isFakeBoldText = true })
                addView(TextView(this@WeatherActivity).apply { text = "${s.pop}%"; textSize = 11f; setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(dp(68), LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), 0, dp(4), 0) })
        }

        daysBox.removeAllViews()
        val dayFmt = SimpleDateFormat("EEEE, dd/MM", Locale.getDefault())
        b.days.forEachIndexed { i, d ->
            val label = if (i == 0) Lang.t(this, "Hôm nay", "Today") else dayFmt.format(Date(d.time)).replaceFirstChar { it.uppercase() }
            daysBox.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0x26FFFFFF) }
                addView(TextView(this@WeatherActivity).apply { text = label; textSize = 14f; setTextColor(0xFFFFFFFF.toInt()) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@WeatherActivity).apply { text = "${d.pop}% 🌂"; textSize = 12f; setTextColor(0xBBFFFFFF.toInt()) },
                    LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(TextView(this@WeatherActivity).apply { text = WeatherService.iconFor(d.id); textSize = 20f },
                    LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT))
                addView(TextView(this@WeatherActivity).apply {
                    text = "${d.min.toInt()}° / ${d.max.toInt()}°"; textSize = 14f; setTextColor(0xFFFFFFFF.toInt()); paint.isFakeBoldText = true
                }, LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) })
        }

        // Hiệu ứng xuất hiện lần lượt
        listOf(statsRow1, statsRow2, sunRow, hoursRow, daysBox).forEachIndexed { i, v ->
            Motion.slideFadeIn(v, 120L + i * 90L)
        }

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.updated))
        tvStatus.text = when {
            n.fallback -> Lang.t(this, "Chưa có vị trí — đang hiện Hà Nội · $time", "No location — showing Hanoi · $time")
            offline -> Lang.t(this, "Không có mạng — dữ liệu lúc $time", "Offline — data from $time")
            else -> Lang.t(this, "Cập nhật lúc $time", "Updated at $time")
        }
    }

    private fun hm(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    // ------------------------------------------------------------------ hoạt ảnh
    private fun animateTemp(target: Double) {
        tempAnim?.cancel()
        if (!Fx.on(this)) { tvTemp.text = "${target.toInt()}°C"; return }
        tempAnim = ValueAnimator.ofFloat(0f, target.toFloat()).apply {
            duration = 900L
            addUpdateListener { tvTemp.text = "${(it.animatedValue as Float).toInt()}°C" }
            start()
        }
    }

    private fun animateBackground(to: IntArray) {
        val from = bgColors ?: to
        bgColors = to
        val paintBg: (IntArray) -> Unit = { c ->
            bg.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, c)
        }
        if (!Fx.on(this) || from === to) { paintBg(to); return }
        bgAnim?.cancel()
        bgAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700L
            addUpdateListener { v ->
                val f = v.animatedValue as Float
                val ev = ArgbEvaluator()
                paintBg(intArrayOf(
                    ev.evaluate(f, from[0], to[0]) as Int,
                    ev.evaluate(f, from[1], to[1]) as Int
                ))
            }
            start()
        }
    }

    private fun startIconFloat() {
        iconAnim?.cancel()
        tvIcon.translationY = 0f
        if (!Fx.on(this)) return
        iconAnim = ObjectAnimator.ofFloat(tvIcon, "translationY", -10f, 10f).apply {
            duration = 2200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    // ------------------------------------------------------------------ vòng đời
    override fun onResume() {
        super.onResume()
        main.removeCallbacks(autoRefresh)
        main.postDelayed(autoRefresh, 30 * 60_000L)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(autoRefresh)
        iconAnim?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(autoRefresh)
        iconAnim?.cancel(); tempAnim?.cancel(); bgAnim?.cancel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9401) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (ok) refresh(force = true)
            else Toast.makeText(this, Lang.t(this, "Chưa cấp quyền vị trí", "Location not allowed"), Toast.LENGTH_SHORT).show()
        }
    }
}
