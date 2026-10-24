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

    /**
     * onMismatch: quét chưa khớp (hộp vẫn mở, người dùng thử tiếp).
     * onError(code, msg): hộp đã đóng — code theo BiometricPrompt.ERROR_*.
     * Nút phụ "Dùng mã khóa điện thoại" trả về ERROR_NEGATIVE_BUTTON (13).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Xác minh để tắt báo thức",
        negativeText: String = "Dùng mã khóa điện thoại",
        onSuccess: () -> Unit,
        onMismatch: () -> Unit = {},
        onError: (Int, String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString.toString())
                }
                override fun onAuthenticationFailed() {
                    onMismatch()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Vân tay hoặc khuôn mặt hệ thống")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText(negativeText)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
