package com.example.alarmclock

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Đăng nhập Google không phụ thuộc OAuth client (tránh lỗi 10).
 * Cách chính: AccountPicker → lấy email tài khoản Google trên máy.
 * Cách phụ: GoogleSignIn (nếu sau này cấu hình SHA-1 + oauth_client).
 */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    /** Intent chọn tài khoản Google — KHÔNG cần OAuth client ID. */
    fun accountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    /** Fallback Google Sign-In chuẩn (cần OAuth). */
    fun signInIntent(activity: Activity): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    /**
     * Xử lý kết quả AccountPicker.
     * @return email nếu chọn thành công
     */
    fun handleAccountPicker(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
        if (email.isBlank() || !email.contains("@")) return null
        AppSettings.setRecoveryEmail(activity, email)
        val name = email.substringBefore("@").replace('.', ' ').replace('_', ' ')
        AppSettings.setGoogleDisplayName(activity, name)
        return email
    }

    fun handleResult(activity: Activity, data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.email?.let {
                AppSettings.setRecoveryEmail(activity, it)
                AppSettings.setGoogleDisplayName(activity, account.displayName ?: it)
            }
            account
        } catch (e: ApiException) {
            Log.w(TAG, "signIn failed code=${e.statusCode}", e)
            null
        }
    }

    fun lastSignedInEmail(activity: Activity): String? {
        val fromSettings = AppSettings.getRecoveryEmail(activity)
        if (fromSettings.isNotBlank()) return fromSettings
        return try {
            GoogleSignIn.getLastSignedInAccount(activity)?.email
        } catch (_: Exception) {
            null
        }
    }

    fun signOut(activity: Activity) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail().build()
            GoogleSignIn.getClient(activity, gso).signOut()
        } catch (_: Exception) {}
        AppSettings.setGoogleDisplayName(activity, "")
        AppSettings.setRecoveryEmail(activity, "")
    }
}
