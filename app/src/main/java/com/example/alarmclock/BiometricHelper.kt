package com.example.alarmclock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Vân tay / khuôn mặt hệ thống (BiometricPrompt).
 * Không đọc được cảm biến thô — đây là cách Android cho phép.
 */
object BiometricHelper {

    fun canAuthenticate(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Xác minh để tắt báo thức",
        onSuccess: () -> Unit,
        onFail: (String) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onFail("Thiết bị không hỗ trợ / chưa đăng ký vân tay-khuôn mặt")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFail(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    onFail("Không khớp")
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Vân tay hoặc khuôn mặt hệ thống")
            .setNegativeButtonText("Hủy")
            .build()
        prompt.authenticate(info)
    }
}
