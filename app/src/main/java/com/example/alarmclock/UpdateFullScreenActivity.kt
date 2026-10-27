package com.example.alarmclock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class UpdateFullScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()

        val tag = intent.getStringExtra("tag") ?: "mới"
        val current = intent.getStringExtra("current") ?: ""

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF1C1B1F.toInt())
            setPadding(48, 48, 48, 48)
        }

        val icon = android.widget.ImageView(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info)
            setColorFilter(0xFFD0BCFF.toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(160, 160).apply {
                bottomMargin = 32
            }
        }
        root.addView(icon)

        val title = TextView(this).apply {
            text = UiTranslate.tr("Có bản cập nhật mới")
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
        }
        root.addView(title)

        val msg = TextView(this).apply {
            text = if (current.isNotBlank()) "Phiên bản hiện tại: v$current\nPhiên bản mới: $tag" else "Phiên bản mới: $tag"
            textSize = 16f
            setTextColor(0xFFE6E1E5.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }
        root.addView(msg)

        val btnUpdate = MaterialButton(this).apply {
            text = UiTranslate.tr("Cập nhật ngay")
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apkpure.com/search?q=com.alarmclock.dongho")))
                } catch (_: Exception) {}
                finish()
            }
        }
        root.addView(btnUpdate)

        val btnLater = MaterialButton(this).apply {
            text = UiTranslate.tr("Để sau")
            setOnClickListener { finish() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        root.addView(btnLater)

        setContentView(root)
    }

    override fun onBackPressed() {
        // Không cho thoát bằng nút back — phải chọn Cập nhật hoặc Để sau
    }
}
