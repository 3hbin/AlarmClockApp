package com.example.alarmclock

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

/**
 * Khóa tab Cài đặt bằng PIN.
 * Quên PIN → gửi mã 6 số qua Gmail (mailto) + thông báo trên máy.
 */
object SettingsLockHelper {

    private const val CHANNEL_ID = "settings_recovery"

    fun requireUnlock(activity: Activity, onUnlocked: () -> Unit) {
        if (!AppSettings.hasSettingsPin(activity)) {
            AppSettings.settingsUnlockedThisSession = true
            onUnlocked()
            return
        }
        if (AppSettings.settingsUnlockedThisSession) {
            onUnlocked()
            return
        }
        showPinDialog(activity, onUnlocked)
    }

    fun showPinDialog(activity: Activity, onUnlocked: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(android.R.layout.simple_list_item_1, null)
        // custom layout inline via EditText
        val input = EditText(activity).apply {
            hint = "Nhập PIN Cài đặt (≥4 số)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("🔒 Khóa Cài đặt")
            .setMessage("Nhập PIN để mở Cài đặt (chống người khác sửa).")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Mở") { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (AppSettings.checkSettingsPin(activity, pin)) {
                    AppSettings.settingsUnlockedThisSession = true
                    Toast.makeText(activity, "Đã mở khóa Cài đặt", Toast.LENGTH_SHORT).show()
                    onUnlocked()
                } else {
                    Toast.makeText(activity, "Sai PIN", Toast.LENGTH_SHORT).show()
                    showPinDialog(activity, onUnlocked)
                }
            }
            .setNeutralButton("Quên PIN?") { _, _ ->
                showForgotFlow(activity, onUnlocked)
            }
            .setNegativeButton("Hủy") { _, _ ->
                // quay về màn trước nếu đang ở Settings
                if (activity is SettingsActivity) activity.finish()
            }
            .show()
    }

    fun showForgotFlow(activity: Activity, onUnlocked: () -> Unit) {
        val email = AppSettings.getRecoveryEmail(activity)
        if (email.isBlank() || !email.contains("@")) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Chưa có email khôi phục")
                .setMessage(
                    "Chưa lưu Gmail khôi phục.\n\n" +
                    "Nếu bạn nhớ PIN cũ: hủy và nhập lại.\n" +
                    "Nếu mất hẳn PIN và chưa có email: chỉ còn cách xóa dữ liệu app (mất cài đặt)."
                )
                .setPositiveButton("Thử PIN lại") { _, _ -> showPinDialog(activity, onUnlocked) }
                .setNegativeButton("Đóng", null)
                .show()
            return
        }

        val code = "%06d".format(Random.nextInt(0, 1_000_000))
        AppSettings.setRecoveryCode(activity, code)

        // 1) Thông báo trên máy (luôn có)
        postRecoveryNotification(activity, code)

        // 2) Mở Gmail / email app gửi mã cho chính user
        try {
            val uri = Uri.parse("mailto:$email")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra(Intent.EXTRA_SUBJECT, "Mã khôi phục PIN Cài đặt — Đồng hồ báo thức")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Mã khôi phục PIN Cài đặt của bạn là: $code\n\n" +
                        "Mã có hiệu lực 15 phút.\n" +
                        "Nhập mã này trong app để đặt lại PIN.\n\n" +
                        "(Tự gửi từ máy bạn — không có server trung gian.)"
                )
            }
            activity.startActivity(Intent.createChooser(intent, "Gửi mã qua Gmail"))
        } catch (_: Exception) {
            Toast.makeText(activity, "Không mở được Gmail — xem mã trong thông báo", Toast.LENGTH_LONG).show()
        }

        // 3) Dialog nhập mã
        val input = EditText(activity).apply {
            hint = "Nhập mã 6 số từ Gmail / thông báo"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Nhập mã khôi phục")
            .setMessage(
                "Đã tạo mã 6 số.\n" +
                "• Kiểm tra thông báo trên máy\n" +
                "• Hoặc gửi email tới $email rồi xem Gmail\n\n" +
                "Mã hết hạn sau 15 phút."
            )
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Xác nhận") { _, _ ->
                val entered = input.text?.toString()?.trim().orEmpty()
                if (AppSettings.checkRecoveryCode(activity, entered)) {
                    AppSettings.clearRecoveryCode(activity)
                    AppSettings.clearSettingsPin(activity)
                    AppSettings.settingsUnlockedThisSession = true
                    Toast.makeText(activity, "Đúng mã — đã xóa PIN cũ. Hãy đặt PIN mới trong Cài đặt.", Toast.LENGTH_LONG).show()
                    onUnlocked()
                } else {
                    Toast.makeText(activity, "Sai mã hoặc đã hết hạn", Toast.LENGTH_SHORT).show()
                    showForgotFlow(activity, onUnlocked)
                }
            }
            .setNegativeButton("Hủy") { _, _ ->
                if (activity is SettingsActivity) activity.finish()
            }
            .show()
    }

    private fun postRecoveryNotification(context: Context, code: String) {
        try {
            ensureChannel(context)
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Mã khôi phục PIN Cài đặt")
                .setContentText("Mã: $code (15 phút)")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Mã khôi phục: $code\nHết hạn sau 15 phút.\nNhập mã này trong app để đặt lại PIN Cài đặt."
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(7102, notif)
        } catch (_: Exception) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Khôi phục PIN Cài đặt",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
