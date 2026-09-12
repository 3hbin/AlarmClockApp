package com.example.alarmclock

object AppRingtones {
    data class Item(val id: String, val label: String, val raw: Int)

    val alarms = listOf(
        Item("ringtone_huawei", "Huawei — Alarm", R.raw.ringtone_huawei),
        Item("ringtone_mi", "Xiaomi — Official", R.raw.ringtone_mi),
        Item("ringtone_oppo_holiday", "OPPO — Holiday", R.raw.ringtone_oppo_holiday),
        Item("ringtone_samsung", "Samsung", R.raw.ringtone_samsung),
        Item("ringtone_samsung_s10", "Samsung Galaxy S10", R.raw.ringtone_samsung_s10),
        Item("ringtone_morning_flower", "Morning Flower", R.raw.ringtone_morning_flower),
        Item("ringtone_oz", "Oz", R.raw.ringtone_oz),
        Item("soft_chime", "Chuông êm", R.raw.soft_chime),
        Item("soft_bell", "Chuông nhẹ", R.raw.soft_bell)
    )

    val sleep = listOf(
        Item("sleep_delta", "Sóng não Delta", R.raw.sleep_delta),
        Item("soft_chime", "Chuông êm", R.raw.soft_chime),
        Item("soft_bell", "Chuông nhẹ", R.raw.soft_bell)
    )

    val all get() = (alarms + sleep).distinctBy { it.id }

    const val DEFAULT_ALARM = "app:ringtone_huawei"

    fun rawOf(uri: String?): Int {
        val key = uri?.removePrefix("app:")?.substringAfterLast('/') ?: return R.raw.ringtone_huawei
        return all.find { it.id == key }?.raw ?: R.raw.ringtone_huawei
    }

    fun labelOf(uri: String?): String {
        val key = uri?.removePrefix("app:")?.substringAfterLast('/') ?: return "Huawei — Alarm"
        return all.find { it.id == key }?.label ?: "Huawei — Alarm"
    }

    fun uriOf(id: String) = "app:$id"
}
