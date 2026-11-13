package com.example.alarmclock

/** Cách tắt báo thức nhanh, không cần thử thách. */
object QuickDismiss {
    const val NONE = 0
    const val SHAKE = 1          // lắc máy 1 lần
    const val DOUBLE_TAP = 2     // chạm 2 lần liên tiếp vào màn hình
    const val CHARGE = 3         // cắm sạc
    const val FINGERPRINT = 4    // vân tay đã đăng ký trên máy

    fun label(mode: Int): String = when (mode) {
        SHAKE -> Lang.t(null, "Lắc máy 1 lần", "Shake once")
        DOUBLE_TAP -> Lang.t(null, "Chạm 2 lần liên tiếp vào màn hình", "Double-tap the screen")
        CHARGE -> Lang.t(null, "Cắm sạc điện thoại", "Plug in the charger")
        FINGERPRINT -> Lang.t(null, "Vân tay (đã đăng ký trên máy)", "Fingerprint (enrolled on this phone)")
        else -> Lang.t(null, "Nút tắt (mặc định)", "Button (default)")
    }

    val all = listOf(NONE, SHAKE, DOUBLE_TAP, CHARGE, FINGERPRINT)
}
