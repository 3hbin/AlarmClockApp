package com.example.alarmclock

import android.util.Log
import android.view.View

/**
 * Bật kiểu wavy (gợn sóng) M3 Expressive cho indicator nếu thư viện có hỗ trợ.
 * Tìm các hàm set... có chữ wavelength / amplitude bằng phản chiếu, nên không cần biết
 * tên thuộc tính XML chính xác và không làm lỗi build. Tên hàm tìm được được ghi vào Logcat (tag WavyIndicator).
 */
object WavyIndicator {
    private const val TAG = "WavyIndicator"

    fun apply(view: View, wavelengthPx: Float, amplitudePx: Float): Boolean {
        var applied = false
        try {
            for (m in view.javaClass.methods) {
                if (m.parameterTypes.size != 1) continue
                val n = m.name.lowercase()
                if (!n.startsWith("set")) continue
                val p = m.parameterTypes[0]
                val isFloat = p == Float::class.javaPrimitiveType || p == Float::class.javaObjectType
                val isInt = p == Int::class.javaPrimitiveType || p == Int::class.javaObjectType
                val value: Any? = when {
                    n.contains("wavelength") -> if (isFloat) wavelengthPx else if (isInt) wavelengthPx.toInt() else null
                    n.contains("amplitude") -> if (isFloat) amplitudePx else if (isInt) amplitudePx.toInt() else null
                    else -> null
                }
                if (value != null) {
                    m.invoke(view, value)
                    applied = true
                    Log.i(TAG, "applied ${m.name}($value) on ${view.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "wavy apply failed: ${e.message}")
        }
        if (!applied) Log.i(TAG, "no wavy setter on ${view.javaClass.simpleName}")
        return applied
    }
}
