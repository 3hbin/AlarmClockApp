package com.example.alarmclock

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton

object UiTranslate {
    // Google Translate API (vi -> en), short labels corrected for this app.
    private val map = mapOf(
        "Chọn mốc, đặt tên, rồi bắt đầu" to "Pick a time, name it, then start",
        "Sẵn sàng" to "Ready",
        "Tên hẹn giờ, ví dụ Học bài" to "Timer name, for example Study",
        "Bắt đầu" to "Start",
        "Đặt lại" to "Reset",
        "Tự đổi icon theo buổi" to "Change icon by time of day",
        "Tắt sẽ khóa icon buổi tối và thoát app." to "Off locks the night icon and exits the app.",
        "Chế độ tập trung khi báo thức" to "Focus mode when the alarm rings",
        "Khi báo thức kêu: bật DND (chỉ chuông báo thức), ẩn thanh hệ thống. Cần cấp quyền Không làm phiền." to "When the alarm rings: turn on Do Not Disturb (alarm only) and hide the system bar. Do Not Disturb permission is required.",
        "Bật chế độ tập trung" to "Turn on focus mode",
        "Chủ đề sự kiện" to "Event theme",
        "GIỜ ĐI NGỦ" to "BEDTIME",
        "GIỜ DẬY" to "WAKE UP",
        "Đã tắt" to "Off",
        "Tiếng sóng" to "Ocean waves",
        "Chọn âm thanh khác" to "Choose another sound",
        "Không có sự kiện nào để hiển thị" to "No events to show",
        "THÁNG 10" to "OCTOBER",
        "Hẹn giờ" to "Timer",
        "Gõ giờ · phút · giây, hoặc chọn mốc nhanh" to "Type hours, minutes, seconds, or pick a quick time",
        "Giờ" to "Hour",
        "Phút" to "Min",
        "Giây" to "Sec",
        "1 phút" to "1 min",
        "5 phút" to "5 min",
        "10 phút" to "10 min",
        "15 phút" to "15 min",
        "Đang đếm" to "Counting",
        "Ngủ gật" to "Nap",
        "Chưa có báo thức" to "No alarm yet",
        "Hàng ngày" to "Every day",
        "Chỉnh" to "Edit",
        "Thử lại" to "Try again",
        "Báo thức" to "Alarm",
        "Cả ngày" to "All day",
        "HÔM NAY" to "TODAY",
        "Làm việc" to "Work",
        "Nghỉ" to "Day off",
        "Kiểm tra cloud" to "Check cloud",
        "Lưu" to "Save",
        "Chọn nhạc chuông" to "Choose ringtone",
        "Nhạc mặc định" to "Default sound",
        "Giờ khác cho Thứ 7 / Chủ nhật" to "Different time for Saturday / Sunday",
        "Giờ cuối tuần" to "Weekend time",
        "Ghi chú giọng nói (TTS khi reo)" to "Voice note (spoken when it rings)",
        "Quốc khánh" to "National Day",
        "Chưa có ảnh nào" to "No photos yet",
        "Làm mới" to "Refresh",
        "Theo hệ thống" to "Follow system",
        "Chỉ tiếng Anh. Theo hệ thống dùng định dạng ngày giờ máy." to "English only. Follow system uses the phone date and time format.",
        "Âm lượng báo thức" to "Alarm volume",
        "Chế độ rung" to "Vibration",
        "Thời gian hoãn (Snooze) mặc định" to "Default snooze time",
        "Thời lượng đổ chuông" to "Ring duration",
        "Hết giờ tự tắt, không kêu suốt ngày" to "Stops automatically. It will not ring all day.",
        "Định dạng 24 giờ" to "24-hour format",
        "Ngôn ngữ ứng dụng" to "App language",
        "Khóa Cài đặt (PIN)" to "Settings lock (PIN)",
        "Chống người khác vào Cài đặt sửa báo thức. Quên PIN — dùng Gmail khôi phục." to "Stops others from changing alarms. Forgot PIN — recover via Gmail.",
        "PIN mới (≥4 số)" to "New PIN (4 digits or more)",
        "Lưu PIN" to "Save PIN",
        "Xóa PIN" to "Clear PIN",
        "Chưa khóa — ai cũng vào được Cài đặt" to "Unlocked — anyone can open Settings",
        "Test thử thách mặt" to "Face challenge tests",
        "Hoặc giữ tab Cài ở thanh dưới" to "Or long-press the Settings tab on the bottom bar",
        "Test quét mặt" to "Face scan test",
        "Test biểu cảm (Cười / Giận / 👍 ...)" to "Expression test",
        "Đang ở nhà — tạm dừng báo thức" to "At home — pause alarms",
        "Cho phép chạy nền / Tắt tối ưu pin" to "Allow background / turn off battery optimization",
        "Cỡ chữ" to "Text size",
        "Chữ bé" to "Small",
        "Vừa" to "Medium",
        "Chữ to" to "Large",
        "Chế độ tối" to "Dark mode",
        "Bật" to "On",
        "Tắt" to "Off"
    )

    fun apply(activity: Activity) {
        if (!Lang.isEn(activity)) return
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        paint(root)
        root.postDelayed({ if (!activity.isFinishing) paint(root) }, 400)
    }

    private fun paint(v: View) {
        if (v is TextView && v !is MaterialButton) {
            val raw = v.text?.toString() ?: ""
            val next = translate(raw)
            if (next != raw) v.text = next
        } else if (v is MaterialButton) {
            val raw = v.text?.toString() ?: ""
            val next = translate(raw)
            if (next != raw) v.text = next
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) paint(v.getChildAt(i))
        }
    }

    private fun translate(raw: String): String {
        val key = raw.trim()
        map[key]?.let { return it }
        if (key.startsWith("THÁNG ")) return "MONTH " + key.removePrefix("THÁNG ")
        if (key.startsWith("Tháng ")) return "Month " + key.removePrefix("Tháng ")
        return raw
    }
}
