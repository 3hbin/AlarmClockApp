package com.example.alarmclock

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton

object ThemeFix {
    fun isNight(activity: Activity): Boolean {
        if (AppSettings.getDarkMode(activity) == 1) return true
        if (AppSettings.getDarkMode(activity) == 2) return false
        val ui = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return ui == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun apply(activity: Activity, root: View?) {
        if (!isNight(activity) || root == null) return
        try {
            activity.window.decorView.setBackgroundColor(Color.BLACK)
            activity.window.statusBarColor = Color.BLACK
            activity.window.navigationBarColor = Color.BLACK
        } catch (_: Exception) {}
        paint(root)
    }

    private fun paint(v: View) {
        if (v is TextView && v !is MaterialButton && v !is EditText) {
            val skip = v.getTag(R.id.chat_full_text) != null
            if (!skip) {
                val c = v.currentTextColor
                if (Color.alpha(c) > 40 && !isBright(c)) v.setTextColor(Color.WHITE)
            }
        }
        if (v is ViewGroup) {
            val bg = v.background
            if (bg == null) v.setBackgroundColor(Color.BLACK)
            for (i in 0 until v.childCount) paint(v.getChildAt(i))
        }
    }

    private fun isBright(c: Int): Boolean {
        val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
        return r > 210 && g > 210 && b > 210
    }
}
