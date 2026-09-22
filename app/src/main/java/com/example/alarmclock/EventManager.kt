package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar

data class EventPalette(
    val id: String,
    val primary: Int,
    val primaryDark: Int,
    val accent: Int,
    val surface: Int,
    val title: String,
    val message: String
)

object EventManager {
    private const val PREF = "event_banner"

    private fun c(hex: String) = Color.parseColor(hex)

    private data class Spec(
        val id: String,
        val startM: Int, val startD: Int,
        val endM: Int, val endD: Int,
        val primary: String, val dark: String, val accent: String, val surface: String,
        val titleVi: String, val msgVi: String,
        val titleEn: String, val msgEn: String
    )

    private val specs = listOf(
        Spec("newyear", 1, 1, 1, 2, "#1565C0", "#0D47A1", "#FFD54F", "#E3F2FD",
            "Năm mới", "Chúc mừng năm mới. Một năm dậy đúng giờ.",
            "New Year", "Happy New Year. Wake on time this year."),
        Spec("tet", 1, 20, 2, 20, "#C62828", "#8E0000", "#F9A825", "#FFF8E1",
            "Tết", "Chúc mừng năm mới. An khang, thức dậy đúng giờ.",
            "Tet", "Happy Lunar New Year. Wake on time."),
        Spec("valentine", 2, 13, 2, 15, "#C2185B", "#880E4F", "#F8BBD0", "#FCE4EC",
            "Valentine", "Ngày lễ tình nhân. Đặt báo thức đừng trễ hẹn.",
            "Valentine", "Valentine's Day. Don't be late."),
        Spec("women", 3, 7, 3, 9, "#AD1457", "#880E4F", "#F48FB1", "#FCE4EC",
            "Quốc tế Phụ nữ", "Chúc 8/3 an lành. Thức dậy nhẹ nhàng.",
            "Women's Day", "Happy International Women's Day."),
        Spec("hungkings", 4, 16, 4, 18, "#B71C1C", "#7F0000", "#FFD54F", "#FFF8E1",
            "Giỗ Tổ Hùng Vương", "Nhớ nguồn. Giữ nhịp ngủ đều.",
            "Hung Kings", "Hung Kings commemoration."),
        Spec("earth", 4, 21, 4, 23, "#2E7D32", "#1B5E20", "#AED581", "#E8F5E9",
            "Ngày Trái Đất", "Tắt đèn sớm, ngủ ngon hơn.",
            "Earth Day", "Earth Day. Sleep earlier tonight."),
        Spec("reunify", 4, 29, 5, 2, "#C62828", "#B71C1C", "#FFD54F", "#FFEBEE",
            "30/4 – 1/5", "Nghỉ lễ. Vẫn đặt báo thức nếu cần.",
            "Reunification / Labor", "Holiday break. Keep your alarm if needed."),
        Spec("children", 6, 1, 6, 2, "#0288D1", "#01579B", "#FFD54F", "#E1F5FE",
            "Quốc tế Thiếu nhi", "1/6 vui vẻ. Ngủ sớm để mai chơi.",
            "Children's Day", "Children's Day. Sleep early."),
        Spec("parents", 6, 27, 6, 29, "#6A1B9A", "#4A148C", "#CE93D8", "#F3E5F5",
            "Ngày Gia đình", "Gia đình Việt Nam. Thức dậy cùng nhau.",
            "Family Day", "Vietnam Family Day."),
        Spec("wounded", 7, 26, 7, 28, "#37474F", "#263238", "#90A4AE", "#ECEFF1",
            "Thương binh liệt sĩ", "27/7 tri ân.",
            "War Invalids Day", "July 27 remembrance."),
        Spec("vulan", 8, 15, 8, 20, "#6A1B9A", "#4A148C", "#FFD54F", "#F3E5F5",
            "Vu Lan", "Mùa báo hiếu. Ngủ đủ giấc.",
            "Vu Lan", "Vu Lan season."),
        Spec("national", 8, 31, 9, 3, "#C62828", "#B71C1C", "#FFD54F", "#FFEBEE",
            "Quốc khánh 2/9", "Chúc Quốc khánh. Dậy đúng giờ.",
            "National Day", "Vietnam National Day."),
        Spec("trungthu", 9, 17, 9, 26, "#B71C1C", "#7F1010", "#F6C445", "#FFF6E4",
            "Trung thu", "Tết Trung thu. Chúc rằm đoàn viên, thức dậy đúng giờ.",
            "Mid-Autumn", "Mid-Autumn Festival. Family and on-time mornings."),
        Spec("women_vn", 10, 19, 10, 21, "#AD1457", "#880E4F", "#F8BBD0", "#FCE4EC",
            "Phụ nữ Việt Nam", "Chúc 20/10. Một ngày nhẹ nhàng.",
            "Vietnam Women's Day", "Vietnam Women's Day."),
        Spec("teachers", 11, 19, 11, 21, "#1565C0", "#0D47A1", "#FFD54F", "#E3F2FD",
            "Nhà giáo Việt Nam", "Chúc 20/11. Dậy sớm đến lớp.",
            "Teachers' Day", "Vietnam Teachers' Day."),
        Spec("halloween", 10, 28, 10, 31, "#EF6C00", "#E65100", "#6A1B9A", "#FFF3E0",
            "Halloween", "Halloween. Đặt báo thức sớm, đừng ngủ quên.",
            "Halloween", "Halloween. Set an early alarm."),
        Spec("army", 12, 21, 12, 23, "#1B5E20", "#0D3B12", "#C8E6C9", "#E8F5E9",
            "Ngày hội Quân đội", "22/12.",
            "Army Day", "Vietnam People's Army Day."),
        Spec("christmas", 12, 20, 12, 26, "#1565C0", "#0D47A1", "#C62828", "#E3F2FD",
            "Giáng sinh", "Giáng sinh an lành. Hẹn gặp bạn sớm mai.",
            "Christmas", "Merry Christmas. See you early tomorrow."),
        Spec("nye", 12, 30, 12, 31, "#4A148C", "#311B92", "#FFD54F", "#EDE7F6",
            "Giao thừa", "Đêm giao thừa. Đặt báo thức năm mới.",
            "New Year's Eve", "New Year's Eve. Set tomorrow's alarm.")
    )

    fun currentSpec(): Spec? {
        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val key = m * 100 + d
        return specs.firstOrNull { s ->
            val a = s.startM * 100 + s.startD
            val b = s.endM * 100 + s.endD
            if (a <= b) key in a..b else key >= a || key <= b
        }
    }

    fun palette(): EventPalette? {
        val s = currentSpec() ?: return null
        val en = Lang.isEn()
        return EventPalette(
            id = s.id,
            primary = c(s.primary),
            primaryDark = c(s.dark),
            accent = c(s.accent),
            surface = c(s.surface),
            title = if (en) s.titleEn else s.titleVi,
            message = if (en) s.msgEn else s.msgVi
        )
    }

    fun currentName(): String = palette()?.title ?: ""

    fun isThemeEnabled(context: Context) =
        AppSettings.prefs(context).getBoolean("event_theme_on", true)

    fun setThemeEnabled(context: Context, on: Boolean) {
        AppSettings.prefs(context).edit().putBoolean("event_theme_on", on).apply()
    }

    fun isDismissed(context: Context): Boolean {
        val p = palette() ?: return true
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("${p.id}_$year", false)
    }

    fun dismiss(context: Context) {
        val p = palette() ?: return
        val year = Calendar.getInstance().get(Calendar.YEAR)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean("${p.id}_$year", true).apply()
    }

    fun applyChrome(activity: Activity) {
        if (!isThemeEnabled(activity)) return
        val p = palette() ?: return
        try {
            activity.window.statusBarColor = p.primary
            if (Build.VERSION.SDK_INT >= 21) activity.window.navigationBarColor = p.surface
        } catch (_: Exception) {}
        listOf("toolbar").forEach { name ->
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id != 0) {
                try { activity.findViewById<View>(id)?.setBackgroundColor(p.primary) } catch (_: Exception) {}
            }
        }
        listOf("fabAdd", "fab", "fabAddEvent").forEach { name ->
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id == 0) return@forEach
            try {
                activity.findViewById<FloatingActionButton>(id)?.apply {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(p.accent)
                    imageTintList = android.content.res.ColorStateList.valueOf(p.primaryDark)
                }
            } catch (_: Exception) {}
        }
        try { activity.window.decorView.setBackgroundColor(p.surface) } catch (_: Exception) {}
    }

    fun bind(banner: View, context: Context) {
        if (!isThemeEnabled(context) || palette() == null || isDismissed(context)) {
            banner.visibility = View.GONE
            return
        }
        val p = palette() ?: run { banner.visibility = View.GONE; return }
        val tv = banner.findViewById<TextView>(R.id.tvEventBanner)
        val close = banner.findViewById<ImageView>(R.id.btnCloseEventBanner)
        tv?.text = p.message
        banner.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(p.primary, p.accent)
        ).apply { cornerRadius = 16f }
        banner.visibility = View.VISIBLE
        close?.setOnClickListener {
            dismiss(context)
            banner.visibility = View.GONE
        }
    }
}
