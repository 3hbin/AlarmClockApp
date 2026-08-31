package com.example.alarmclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

/**
 * Khóa tab Cài đặt bằng PIN.
 * Quên PIN → mở Gmail (user bấm Gửi) → nhập mã 6 ô OTP.
 */
object SettingsLockHelper {

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
        val input = EditText(activity).apply {
            hint = "Nhập PIN Cài đặt (≥4 số)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
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
                if (activity is SettingsActivity) activity.finish()
            }
            .show()
    }

    fun showForgotFlow(activity: Activity, onUnlocked: () -> Unit) {
        val email = AppSettings.getRecoveryEmail(activity)
        if (email.isBlank() || !email.contains("@")) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Chưa có Gmail khôi phục")
                .setMessage(
                    "Vào Cài đặt → lưu địa chỉ Gmail khôi phục trước khi đặt PIN.\n\n" +
                        "Không có Gmail thì không gửi được mã qua hộp thư Google."
                )
                .setPositiveButton("Thử PIN lại") { _, _ -> showPinDialog(activity, onUnlocked) }
                .setNegativeButton("Đóng", null)
                .show()
            return
        }

        val code = "%06d".format(Random.nextInt(0, 1_000_000))
        AppSettings.setRecoveryCode(activity, code)

        MaterialAlertDialogBuilder(activity)
            .setTitle("Gửi mã qua Gmail")
            .setMessage(
                "App không có máy chủ — không tự gửi email.\n\n" +
                    "Sẽ mở Gmail với sẵn nội dung tới:\n📧 $email\n\n" +
                    "1. Bấm «Mở Gmail»\n" +
                    "2. Trong Gmail bấm Gửi\n" +
                    "3. Vào Hộp thư đến lấy mã 6 số\n" +
                    "4. Quay lại app nhập mã\n\n" +
                    "Mã hết hạn sau 15 phút."
            )
            .setPositiveButton("Mở Gmail") { _, _ ->
                openGmailRecovery(activity, email, code)
                showOtpDialog(activity, email, onUnlocked)
            }
            .setNegativeButton("Hủy") { _, _ ->
                if (activity is SettingsActivity) activity.finish()
            }
            .show()
    }

    fun openGmailRecovery(activity: Activity, recoveryEmail: String, code6: String) {
        val appName = "Đồng hồ báo thức"
        val subject = "[$appName] Mã khôi phục PIN Cài đặt"
        val body = buildString {
            appendLine("Xin chào,")
            appendLine()
            appendLine("Bạn (hoặc ai đó trên máy này) đã yêu cầu mã khôi phục PIN Cài đặt trong ứng dụng $appName.")
            appendLine()
            appendLine("Mã khôi phục: $code6")
            appendLine("Hiệu lực: 15 phút kể từ lúc tạo.")
            appendLine()
            appendLine("Nếu không phải bạn yêu cầu, hãy bỏ qua email này và không chia sẻ mã cho ai.")
            appendLine()
            appendLine("— Email này do bạn bấm Gửi từ máy của mình (app không có máy chủ gửi thư).")
        }
        val email = recoveryEmail.trim()

        try {
            val gmail = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                setPackage("com.google.android.gm")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(gmail)
            Toast.makeText(activity, "Đã mở Gmail — bấm Gửi để nhận mã trong hộp thư", Toast.LENGTH_LONG).show()
            return
        } catch (_: Exception) {
        }

        try {
            val uri = Uri.parse(
                "mailto:${Uri.encode(email)}" +
                    "?subject=${Uri.encode(subject)}" +
                    "&body=${Uri.encode(body)}"
            )
            activity.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SENDTO, uri), "Chọn Gmail hoặc ứng dụng email")
            )
            return
        } catch (_: Exception) {
        }

        try {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            activity.startActivity(Intent.createChooser(fallback, "Gửi mã khôi phục bằng email"))
        } catch (_: Exception) {
            Toast.makeText(
                activity,
                "Không mở được ứng dụng email. Cài Gmail hoặc nhập thủ công nếu bạn còn nhớ mã.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showOtpDialog(activity: Activity, email: String, onUnlocked: () -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }

        root.addView(TextView(activity).apply {
            text = "Nhập mã 6 số trong Gmail"
            textSize = 16f
            setPadding(0, 0, 0, dp(6))
        })
        root.addView(TextView(activity).apply {
            text = "Hộp thư đến: $email\nMã hết hạn sau 15 phút."
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        })

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val boxes = List(6) {
            EditText(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(50)).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                gravity = Gravity.CENTER
                textSize = 20f
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(1))
                setBackgroundResource(android.R.drawable.edit_text)
            }.also { row.addView(it) }
        }
        root.addView(row)

        val btnConfirm = MaterialButton(activity).apply {
            text = "Xác nhận mã"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        root.addView(btnConfirm)

        val btnGmail = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Mở lại Gmail nếu chưa nhận được mã"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        root.addView(btnGmail)

        fun readCode(): String = boxes.joinToString("") { it.text?.toString().orEmpty() }

        fun tryUnlock(code: String): Boolean {
            if (code.length != 6 || !code.all { it.isDigit() }) {
                Toast.makeText(activity, "Nhập đủ 6 số", Toast.LENGTH_SHORT).show()
                return false
            }
            if (!AppSettings.checkRecoveryCode(activity, code)) {
                Toast.makeText(activity, "Sai mã hoặc đã hết hạn", Toast.LENGTH_SHORT).show()
                return false
            }
            AppSettings.clearRecoveryCode(activity)
            AppSettings.clearSettingsPin(activity)
            AppSettings.settingsUnlockedThisSession = true
            Toast.makeText(
                activity,
                "Đúng mã — đã xóa PIN cũ. Hãy đặt PIN mới trong Cài đặt.",
                Toast.LENGTH_LONG
            ).show()
            onUnlocked()
            return true
        }

        boxes.forEachIndexed { index, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if ((s?.length ?: 0) == 1 && index < boxes.lastIndex) {
                        boxes[index + 1].requestFocus()
                    }
                    val code = readCode()
                    if (code.length == 6) tryUnlock(code)
                }
            })
            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    box.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    boxes[index - 1].apply {
                        setText("")
                        requestFocus()
                    }
                    true
                } else false
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Mã từ Gmail")
            .setView(root)
            .setCancelable(false)
            .setNegativeButton("Hủy") { _, _ ->
                if (activity is SettingsActivity) activity.finish()
            }
            .create()

        btnConfirm.setOnClickListener {
            if (tryUnlock(readCode())) dialog.dismiss()
        }
        btnGmail.setOnClickListener {
            val stored = AppSettings.prefs(activity).getString("settings_recovery_code", null)
            if (stored.isNullOrBlank()) {
                Toast.makeText(activity, "Mã hết hạn — bấm Quên PIN lại", Toast.LENGTH_LONG).show()
            } else {
                openGmailRecovery(activity, email, stored)
            }
        }

        dialog.show()
        boxes[0].post {
            boxes[0].requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(boxes[0], InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
