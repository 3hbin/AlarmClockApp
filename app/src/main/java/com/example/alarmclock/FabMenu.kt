package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MR

/**
 * Menu nút + kiểu Material 3 Expressive: lớp mờ, các nút viên thuốc xếp chéo, nút + xoay thành ×.
 * Dùng chung cho màn chính và danh sách báo thức.
 */
object FabMenu {
    private var overlay: View? = null
    private var open = false

    fun toggle(activity: Activity, fab: View) {
        if (open) close(fab) else show(activity, fab)
    }

    private fun show(activity: Activity, fab: View) {
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val d = activity.resources.displayMetrics.density
            open = true
            fab.animate().rotation(45f).setDuration(220L).start()

            val scrim = FrameLayout(activity).apply {
                setBackgroundColor(0x66000000)
                alpha = 0f
                setOnClickListener { close(fab) }
            }
            val onContainer = MaterialColors.getColor(activity, MR.attr.colorOnPrimaryContainer, 0xFF21005D.toInt())
            val container = MaterialColors.getColor(activity, MR.attr.colorPrimaryContainer, 0xFFEADDFF.toInt())

            data class Item(val label: String, val icon: Int, val action: () -> Unit)
            val items = listOf(
                Item("Thêm báo thức", R.drawable.ic_m_add) {
                    activity.startActivity(Intent(activity, AddEditAlarmActivity::class.java))
                },
                Item("Thời tiết", R.drawable.cloud_24) {
                    activity.startActivity(Intent(activity, WeatherActivity::class.java))
                }
            )

            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            items.forEachIndexed { i, item ->
                val pill = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((22 * d).toInt(), (14 * d).toInt(), (26 * d).toInt(), (14 * d).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 50 * d
                        setColor(container)
                    }
                    isClickable = true
                    setOnClickListener {
                        close(fab)
                        item.action()
                    }
                    addView(ImageView(activity).apply {
                        setImageResource(item.icon)
                        imageTintList = android.content.res.ColorStateList.valueOf(onContainer)
                    }, LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt()))
                    addView(TextView(activity).apply {
                        text = UiTranslate.tr(item.label)
                        textSize = 16f
                        setTextColor(onContainer)
                        paint.isFakeBoldText = true
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = (12 * d).toInt() })
                }
                // Xếp chéo xen kẽ như mẫu Google
                val offset = if (i % 2 == 0) 0 else (18 * d).toInt()
                column.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (10 * d).toInt()
                    marginEnd = offset
                })
                pill.alpha = 0f
                pill.translationY = 40 * d
                pill.animate().alpha(1f).translationY(0f).setStartDelay(60L * (items.size - 1 - i)).setDuration(260L).start()
            }

            // Đặt menu ngay phía trên nút +; nếu chưa đo được vị trí nút thì dùng khoảng cách mặc định
            val fabLoc = IntArray(2); fab.getLocationOnScreen(fabLoc)
            val contLoc = IntArray(2); content.getLocationOnScreen(contLoc)
            val fabTop = fabLoc[1] - contLoc[1]
            val bottomMargin = if (fabTop > 0 && fabTop < content.height) content.height - fabTop + (14 * d).toInt() else (96 * d).toInt()
            scrim.addView(column, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END).apply {
                this.bottomMargin = bottomMargin
                marginEnd = (20 * d).toInt()
            })
            content.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            overlay = scrim
            scrim.animate().alpha(1f).setDuration(200L).start()
        } catch (e: Exception) {
            open = false
            Toast.makeText(activity, "Menu lỗi: ${e.javaClass.simpleName} ${e.message ?: ""}", Toast.LENGTH_LONG).show()
        }
    }

    fun close(fab: View?) {
        if (!open) return
        open = false
        val scrim = overlay ?: return
        overlay = null
        scrim.animate().alpha(0f).setDuration(160L).withEndAction {
            (scrim.parent as? ViewGroup)?.removeView(scrim)
        }.start()
        try { fab?.animate()?.rotation(0f)?.setDuration(200L)?.start() } catch (_: Exception) {}
    }
}
