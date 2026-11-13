package com.example.alarmclock

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.CompoundButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Công tắc Material 3 (SwitchMaterial) dùng chung cho toàn app.
 * Giữ các hàm cũ (setChecked có animate/notify, setCheckedSilent, setLoading, colorOn) để không phải sửa nơi gọi.
 */
class LoadSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchMaterial(context, attrs) {

    private var savedListener: OnCheckedChangeListener? = null

    /** Màu track khi bật (mặc định: colorPrimary của theme). */
    var colorOn: Int = 0
        set(value) {
            field = value
            applyTrack()
        }

    /** Giữ tương thích với code cũ; M3 không cần màu quay riêng. */
    var spinColor: Int = 0

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        savedListener = listener
        super.setOnCheckedChangeListener(listener)
    }

    /** Đổi trạng thái; notify=false thì không gọi listener (animate giữ để tương thích). */
    fun setChecked(value: Boolean, animate: Boolean = true, notify: Boolean = true) {
        if (notify) isChecked = value else setCheckedSilent(value)
    }

    /** Đổi trạng thái mà không gọi listener (dùng khi nạp cài đặt). */
    fun setCheckedSilent(value: Boolean) {
        val l = savedListener
        super.setOnCheckedChangeListener(null)
        isChecked = value
        super.setOnCheckedChangeListener(l)
    }

    /** Khi đang lưu: khóa công tắc và làm mờ nhẹ. */
    fun setLoading(value: Boolean) {
        isEnabled = !value
        alpha = if (value) 0.45f else 1f
    }

    private fun applyTrack() {
        val on = if (colorOn != 0) colorOn else return
        val off = try {
            com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE7E0EC.toInt())
        } catch (_: Exception) { 0xFFE7E0EC.toInt() }
        trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(on, off)
        )
    }
}

/** Dùng được với mọi CompoundButton (kể cả khi biến có kiểu SwitchMaterial). */
fun CompoundButton.setLoading(value: Boolean) {
    if (this is LoadSwitchView) { this.setLoading(value); return }
    isEnabled = !value
    alpha = if (value) 0.45f else 1f
}

fun CompoundButton.setCheckedSilent(value: Boolean) {
    if (this is LoadSwitchView) { this.setCheckedSilent(value); return }
    isChecked = value
}

/** setChecked(value, animate, notify) cho mọi CompoundButton (kể cả khi biến có kiểu CompoundButton/SwitchMaterial). */
fun CompoundButton.setChecked(value: Boolean, animate: Boolean = true, notify: Boolean = true) {
    if (this is LoadSwitchView) { this.setChecked(value, animate, notify); return }
    isChecked = value
}
