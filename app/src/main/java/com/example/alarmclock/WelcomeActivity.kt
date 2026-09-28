package com.example.alarmclock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val readyRunnable = Runnable { showConfirmReady() }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppSettings.hasAcceptedWelcome(this)) {
            goHome()
            return
        }
        setContentView(R.layout.activity_welcome)
        bindPrivacyLink()
        findViewById<MaterialButton>(R.id.btnConfirm).setOnClickListener {
            AppSettings.setWelcomeAccepted(this, true)
            goHome()
        }
        handler.postDelayed(readyRunnable, LOADING_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(readyRunnable)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Người mới phải xác nhận trước khi vào app
        moveTaskToBack(true)
    }

    private fun bindPrivacyLink() {
        val tv = findViewById<TextView>(R.id.tvPrivacy)
        val label = getString(R.string.welcome_privacy_label)
        val url = getString(R.string.privacy_policy_url)
        val full = "$label $url"
        val span = SpannableString(full)
        val start = full.indexOf(url)
        val end = start + url.length
        val blue = Color.parseColor("#1A73E8")
        span.setSpan(ForegroundColorSpan(blue), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = span
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }

    private fun showConfirmReady() {
        findViewById<ProgressBar>(R.id.progressWelcome).visibility = View.GONE
        findViewById<TextView>(R.id.tvLoading).visibility = View.GONE
        findViewById<TextView>(R.id.tvWaitHint).visibility = View.GONE
        val btn = findViewById<MaterialButton>(R.id.btnConfirm)
        btn.visibility = View.VISIBLE
        btn.isEnabled = true
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        private const val LOADING_MS = 5_000L

        fun launchIfNeeded(activity: Activity): Boolean {
            if (AppSettings.hasAcceptedWelcome(activity)) return false
            activity.startActivity(Intent(activity, WelcomeActivity::class.java))
            activity.finish()
            return true
        }
    }
}
