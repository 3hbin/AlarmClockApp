package com.example.alarmclock

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Google Sign-In chuẩn (đã có oauth_client + SHA-1 trong google-services.json).
 * Fallback: AccountPicker nếu Sign-In lỗi.
 */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"
    /** Web client_id (client_type 3) — dùng cho requestIdToken */
    private const val WEB_CLIENT_ID =
        "297353017052-lkqrj6s8a1ube2c8quhvk9ebkhodedbq.apps.googleusercontent.com"

    fun options(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
    }

    fun signInIntent(activity: Activity): Intent {
        return GoogleSignIn.getClient(activity, options()).signInIntent
    }

    fun accountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"), null, null, null, null
        )
    }

    fun handleResult(activity: Activity, data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrBlank()) {
                AppSettings.setRecoveryEmail(activity, email)
                AppSettings.setGoogleDisplayName(
                    activity,
                    account.displayName?.takeIf { it.isNotBlank() } ?: email
                )
                AppSettings.setGooglePhotoUrl(activity, account.photoUrl?.toString().orEmpty())
            }
            // Firebase Auth (tùy chọn)
            try {
                val token = account?.idToken
                if (!token.isNullOrBlank()) {
                    val cred = GoogleAuthProvider.getCredential(token, null)
                    FirebaseAuth.getInstance().signInWithCredential(cred)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase link skip: ${e.message}")
            }
            account
        } catch (e: ApiException) {
            Log.w(TAG, "signIn failed code=${e.statusCode}", e)
            null
        }
    }

    fun handleAccountPicker(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        val email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim().orEmpty()
        if (email.isBlank() || !email.contains("@")) return null
        AppSettings.setRecoveryEmail(activity, email)
        val name = email.substringBefore("@").replace('.', ' ').replace('_', ' ')
        AppSettings.setGoogleDisplayName(activity, name)
        AppSettings.setGooglePhotoUrl(activity, "")
        return email
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

    /** Xóa phiên Google trên máy rồi mới trả intent, để màn chọn tài khoản không tự đóng. Không xóa email đã lưu. */
    fun preparePicker(activity: Activity, onReady: (Intent) -> Unit) {
        try {
            val client = GoogleSignIn.getClient(activity, options())
            client.signOut().addOnCompleteListener {
                try {
                    onReady(client.signInIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "signInIntent after signOut: ${e.message}")
                    onReady(accountPickerIntent())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "preparePicker: ${e.message}")
            onReady(accountPickerIntent())
        }
    }

    fun signOut(activity: Activity) {
        try {
            GoogleSignIn.getClient(activity, options()).signOut()
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        AppSettings.setGoogleDisplayName(activity, "")
        AppSettings.setGooglePhotoUrl(activity, "")
        // Giữ recovery email trừ khi user muốn xóa — clear khi đăng xuất
        AppSettings.setRecoveryEmail(activity, "")
    }
}
