package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Đăng nhập Google (play-services-auth + Firebase Auth).
 * Nếu thiếu OAuth client (lỗi 10) → fallback chọn tài khoản / nhập email.
 */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    fun signInIntent(activity: Activity): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            // requestIdToken cần web client id — nếu có trong google-services
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    fun handleResult(activity: Activity, data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.email?.let { AppSettings.setRecoveryEmail(activity, it) }
            account?.email?.let { AppSettings.setGoogleDisplayName(activity, account.displayName ?: it) }
            // Firebase (nếu cấu hình đủ)
            try {
                val auth = FirebaseAuth.getInstance()
                // Không có idToken thì chỉ lưu email local
            } catch (_: Exception) {}
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
        // Giữ recovery email nếu user muốn — không xóa
    }
}
