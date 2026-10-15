package com.example.alarmclock

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator

object Md3Loading {
    fun show(context: Context, message: String? = null, cancelable: Boolean = false): Dialog {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 40)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val indicator = CircularProgressIndicator(context).apply {
            isIndeterminate = true
            indicatorSize = (48 * context.resources.displayMetrics.density).toInt()
            trackThickness = (4 * context.resources.displayMetrics.density).toInt()
        }
        root.addView(indicator)
        if (!message.isNullOrBlank()) {
            val tv = TextView(context).apply {
                text = message
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 0)
            }
            root.addView(tv)
        }
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(root)
        dialog.setCancelable(cancelable)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }

    fun showNoNetwork(context: Context, message: String = "Không có mạng"): Dialog {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 40, 48, 40)
        }
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }
        root.addView(icon)
        val tv = TextView(context).apply {
            text = message
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        root.addView(tv)
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(root)
        dialog.setCancelable(true)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        return dialog
    }
}
