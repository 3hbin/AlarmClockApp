package com.example.alarmclock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.util.Calendar

enum class SeasonEvent { NONE, TET, HALLOWEEN, CHRISTMAS }

object EventManager {
    private const val PREF = "event_banner"

    fun current(): SeasonEvent {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return when (month) {
            1, 2 -> SeasonEvent.TET
            10 -> SeasonEvent.HALLOWEEN
            12 -> SeasonEvent.CHRISTMAS
            else -> SeasonEvent.NONE
        }
    }

    fun isThemeEnabled(context: Context) =
        AppSettings.prefs(context).getBoolean("event_theme_on", true)

    fun setThemeEnabled(context: Context, on: Boolean) {
        AppSettings.prefs(context).edit().putBoolean("event_theme_on", on).apply()
    }

    fun isDismissed(context: Context): Boolean {
        val ev = current()
        if (ev == SeasonEvent.NONE) return true
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean("${ev}_$year", false)
    }

    fun dismiss(context: Context) {
        val ev = current()
        if (ev == SeasonEvent.NONE) return
        val year = Calendar.getInstance().get(Calendar.YEAR)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean("${ev}_$year", true).apply()
    }

    fun bind(banner: View, context: Context) {
        if (!isThemeEnabled(context) || current() == SeasonEvent.NONE || isDismissed(context)) {
            banner.visibility = View.GONE
            return
        }
        val tv = banner.findViewById<TextView>(R.id.tvEventBanner)
        val close = banner.findViewById<ImageView>(R.id.btnCloseEventBanner)
        val (msg, c1, c2) = when (current()) {
            SeasonEvent.TET -> Triple(
                "Chúc mừng năm mới. An khang, thức dậy đúng giờ.",
                Color.parseColor("#C62828"), Color.parseColor("#F9A825")
            )
            SeasonEvent.HALLOWEEN -> Triple(
                "Halloween. Đặt báo thức sớm, đừng ngủ quên.",
                Color.parseColor("#EF6C00"), Color.parseColor("#6A1B9A")
            )
            SeasonEvent.CHRISTMAS -> Triple(
                "Giáng sinh an lành. Hẹn gặp bạn sớm mai.",
                Color.parseColor("#1565C0"), Color.parseColor("#C62828")
            )
            else -> Triple("", 0, 0)
        }
        tv.text = msg
        banner.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)
        ).apply { cornerRadius = 16f }
        banner.visibility = View.VISIBLE
        close.setOnClickListener {
            dismiss(context)
            banner.visibility = View.GONE
        }
    }
}
