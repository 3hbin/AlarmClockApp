package com.example.alarmclock

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Chính sách quyền riêng tư"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val url = "https://3hbin.github.io/AlarmClockApp/privacy.html"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val hint = TextView(this).apply {
            text = url
            setPadding(32, 24, 32, 16)
            setTextColor(0xFF1565C0.toInt())
            setOnClickListener {
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (_: Exception) {}
            }
        }
        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = true
        }
        root.addView(hint)
        root.addView(web)
        setContentView(root)
        try {
            web.loadUrl(url)
        } catch (e: Exception) {
            hint.text = "Không mở được trang. Bấm dòng link phía trên hoặc mở trình duyệt."
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
