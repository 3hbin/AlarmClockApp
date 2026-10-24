package com.example.alarmclock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Gửi tin nhắn cứu viện tới số đã lưu.
 * Cần quyền SEND_SMS. Google Play hạn chế quyền này — chỉ dùng cho bản cá nhân / sideload.
 */
object EmergencySmsHelper {

    private const val PREF = "emergency_prefs"
    private const val KEY_PHONE = "phone"
    private const val KEY_MESSAGE = "message"

    fun setPhone(context: Context, phone: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_PHONE, phone.trim()).apply()
    }

    fun getPhone(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, "") ?: ""

    fun setCustomMessage(context: Context, msg: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_MESSAGE, msg).apply()
    }

    fun getCustomMessage(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MESSAGE, "Tôi cần hỗ trợ. Đây là tin nhắn tự động từ AlarmClockApp.")
            ?: "Tôi cần hỗ trợ. Đây là tin nhắn tự động từ AlarmClockApp."

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Gửi SMS. locationText có thể là tọa độ hoặc "không lấy được vị trí".
     */
    fun sendAlert(context: Context, locationText: String = ""): Boolean {
        val phone = getPhone(context)
        if (phone.isBlank()) {
            Toast.makeText(context, "Chưa đặt số điện thoại cứu viện", Toast.LENGTH_LONG).show()
            return false
        }
        if (!canSend(context)) {
            Toast.makeText(context, "Chưa cấp quyền Gửi SMS", Toast.LENGTH_LONG).show()
            return false
        }
        val body = buildString {
            append(getCustomMessage(context))
            if (locationText.isNotBlank()) {
                append("\nVị trí: ").append(locationText)
            }
        }
        return try {
            val sms = SmsManager.getDefault()
            val parts = sms.divideMessage(body)
            if (parts.size == 1) {
                sms.sendTextMessage(phone, null, body, null, null)
            } else {
                sms.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            Toast.makeText(context, "Đã gửi SMS cứu viện tới $phone", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi gửi SMS: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
