package com.example.alarmclock

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Chat trợ lý trong máy, chỉ trả lời về báo thức. */
class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = TextView(this).apply {
            text = "Trợ lý: Chào bạn. Hỏi giờ ngủ, báo thức hoặc nhắc nước."
            setTextColor(0xFF111111.toInt())
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(log) }
        val input = EditText(this).apply { hint = "Nhập câu hỏi" }
        val send = Button(this).apply { text = "Gửi" }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(send)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }
        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            log.append("\n\nBạn: $q\nTrợ lý: ${reply(q)}")
            input.setText("")
        }
        setContentView(root)
        try { BottomNavHelper.bind(this, findViewById(R.id.curvedNav), 6) } catch (_: Exception) {}
    }

    private fun reply(q: String): String {
        val s = q.lowercase()
        return when {
            s.contains("nước") -> "Uống một cốc nước. Có thể bật nhắc nước trong menu 3 chấm."
            s.contains("ngủ") -> "Nên ngủ đủ giờ. Vào tab Ngủ để đặt giờ đi ngủ và nhạc ru."
            s.contains("báo thức") -> "Bấm + để thêm báo thức. Giữ một báo thức để sao chép, ghi chú hoặc đổi màu."
            s.contains("cuối tuần") -> "Trong thêm báo thức, bật giờ khác cho thứ 7 và Chủ nhật."
            else -> "Mình chỉ giúp báo thức, ngủ và nhắc nước. Thử hỏi: đặt báo thức thế nào?"
        }
    }
}
