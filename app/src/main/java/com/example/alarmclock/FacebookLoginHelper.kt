package com.example.alarmclock

import android.app.Activity
import android.widget.Toast
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth

object FacebookLoginHelper {
    val callbackManager: CallbackManager = CallbackManager.Factory.create()

    fun start(activity: Activity) {
        try {
            FacebookSdk.sdkInitialize(activity.applicationContext)
            LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken.token
                    val cred = FacebookAuthProvider.getCredential(token)
                    FirebaseAuth.getInstance().signInWithCredential(cred)
                        .addOnSuccessListener {
                            val email = it.user?.email.orEmpty()
                            if (email.isNotBlank()) AppSettings.setRecoveryEmail(activity, email)
                            val name = it.user?.displayName?.takeIf { n -> n.isNotBlank() } ?: "Facebook"
                            AppSettings.setGoogleDisplayName(activity, name)
                            val photo = it.user?.photoUrl?.toString().orEmpty()
                            if (photo.isNotBlank()) AppSettings.setGooglePhotoUrl(activity, photo)
                            Toast.makeText(activity, Lang.t(activity, "Đã đăng nhập Facebook", "Signed in with Facebook"), Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(activity, e.message ?: "Facebook lỗi", Toast.LENGTH_LONG).show()
                        }
                }
                override fun onCancel() {
                    Toast.makeText(activity, Lang.t(activity, "Đã hủy", "Cancelled"), Toast.LENGTH_SHORT).show()
                }
                override fun onError(error: FacebookException) {
                    Toast.makeText(activity, error.message ?: "Facebook lỗi", Toast.LENGTH_LONG).show()
                }
            })
            LoginManager.getInstance().logInWithReadPermissions(activity, listOf("public_profile"))
        } catch (e: Exception) {
            Toast.makeText(activity, e.message ?: "Không mở được Facebook", Toast.LENGTH_LONG).show()
        }
    }
}
