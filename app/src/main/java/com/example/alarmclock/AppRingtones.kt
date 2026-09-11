package com.example.alarmclock

object AppRingtones {
    data class Item(val id: String, val label: String, val raw: Int)

    val all = listOf(
        Item("soft_chime", "Chuông êm", R.raw.soft_chime),
        Item("soft_bell", "Chuông nhẹ", R.raw.soft_bell),
        Item("ringtone_oppo_holiday", "OPPO — Holiday", R.raw.ringtone_oppo_holiday),
        Item("ringtone_huawei", "Huawei — Alarm", R.raw.ringtone_huawei),
        Item("ringtone_mi", "Xiaomi — Official", R.raw.ringtone_mi),
        Item("ringtone_morning_flower", "Morning Flower", R.raw.ringtone_morning_flower),
        Item("ringtone_samsung", "Samsung", R.raw.ringtone_samsung),
        Item("ringtone_samsung_s10", "Samsung Galaxy S10", R.raw.ringtone_samsung_s10)
    )

    fun rawOf(uri: String?): Int {
        val key = uri?.removePrefix("app:")?.substringAfterLast('/') ?: return R.raw.soft_chime
        return all.find { it.id == key }?.raw ?: R.raw.soft_chime
    }

    fun labelOf(uri: String?): String {
        val key = uri?.removePrefix("app:")?.substringAfterLast('/') ?: return "Chuông êm"
        return all.find { it.id == key }?.label ?: "Chuông êm"
    }

    fun uriOf(id: String) = "app:$id"
}
