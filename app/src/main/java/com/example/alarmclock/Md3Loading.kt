package com.example.alarmclock

import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Loading indicator Material 3 Expressive (Material You).
 * Dùng LoadingIndicator — hình biến đổi 7 shape (Soft Burst → Cookie → Pentagon → Pill → Sunny → Oval).
 */
object Md3Loading {

    private fun primary(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt())

    private fun onSurface(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1F.toInt())

    private fun surface(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF.toInt())

    fun show(context: Context, message: String? = null, cancelable: Boolean = false): Dialog {
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * density).toInt(), (24 * density).toInt(), (28 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(surface(context))
            elevation = 6 * density
        }
        // LoadingIndicator Material 3 Expressive — morph 7 hình
        val indicator = LoadingIndicator(context).apply {
            setIndicatorColor(primary(context))
            setIndicatorSize((48 * density).toInt())
            setContainerWidth((56 * density).toInt())
            setContainerHeight((56 * density).toInt())
        }
        root.addView(indicator, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        if (!message.isNullOrBlank()) {
            val tv = TextView(context).apply {
                text = message
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(onSurface(context))
                gravity = Gravity.CENTER
                setPadding(0, (16 * density).toInt(), 0, 0)
            }
            root.addView(tv)
        }
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(root)
        dialog.setCancelable(cancelable)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.45f)
        dialog.show()
        return dialog
    }

    fun showNoNetwork(context: Context, message: String = "Không có mạng"): Dialog {
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * density).toInt(), (24 * density).toInt(), (28 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(surface(context))
        }
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0xFFB3261E.toInt()))
            layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
        }
        root.addView(icon)
        val tv = TextView(context).apply {
            text = message
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(onSurface(context))
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, 0)
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

    /** Thanh dài dạng sóng lượn (Wavy) — Material 3 Expressive */
    fun showWavy(context: Context, message: String? = null, cancelable: Boolean = false): Dialog {
        val density = context.resources.displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (24 * density).toInt(), (32 * density).toInt(), (24 * density).toInt())
            setBackgroundColor(surface(context))
        }
        val bar = LinearProgressIndicator(context).apply {
            isIndeterminate = true
            // Style wavy nếu có, không thì track dày + bo tròn
            try {
                val styleId = context.resources.getIdentifier(
                    "Widget.Material3Expressive.LinearProgressIndicator.Wavy",
                    "style",
                    "com.google.android.material"
                )
                if (styleId != 0) context.setTheme(styleId)
            } catch (_: Exception) {}
            trackThickness = (8 * density).toInt()
            trackCornerRadius = (4 * density).toInt()
            setIndicatorColor(primary(context))
            trackColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE7E0EC.toInt())
            layoutParams = LinearLayout.LayoutParams(
                (220 * density).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        root.addView(bar)
        if (!message.isNullOrBlank()) {
            val tv = TextView(context).apply {
                text = message
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(onSurface(context))
                gravity = Gravity.CENTER
                setPadding(0, (16 * density).toInt(), 0, 0)
            }
            root.addView(tv)
        }
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(root)
        dialog.setCancelable(cancelable)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.4f)
        dialog.show()
        return dialog
    }

}
