package com.example.alarmclock

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ChatErrorKind {
    RATE_LIMIT,
    NETWORK,
    OTHER
}

/**
 * Trạng thái lỗi một lượt chat — dùng được cho ViewModel / Adapter.
 */
data class ChatApiError(
    val errorMessage: String,
    val canRetry: Boolean,
    val rawPrompt: String,
    val kind: ChatErrorKind = ChatErrorKind.OTHER,
    val httpCode: Int? = null
)

object ChatApiExceptionMapper {

    fun fromHttp(code: Int, body: String, rawPrompt: String): ChatApiError {
        val kind = when (code) {
            HttpURLConnection.HTTP_UNAVAILABLE,
            429,
            502,
            503,
            504 -> ChatErrorKind.RATE_LIMIT
            else -> ChatErrorKind.OTHER
        }
        val message = when (code) {
            429, 503, 502, 504, HttpURLConnection.HTTP_UNAVAILABLE ->
                "Gemini đang quá tải. Bấm Thử lại sau vài giây."
            401, 403 ->
                "Khóa API không hợp lệ hoặc hết hạn. Kiểm tra lại nút Khóa."
            404 ->
                "Không tìm thấy mô hình Gemini. Thử lại hoặc đổi khóa."
            else ->
                "Lỗi máy chủ ($code). Bấm Thử lại."
        }
        val hint = body.trim().take(80)
        return ChatApiError(
            errorMessage = if (hint.isBlank()) message else message,
            canRetry = code != 401 && code != 403,
            rawPrompt = rawPrompt,
            kind = kind,
            httpCode = code
        )
    }

    fun fromThrowable(t: Throwable, rawPrompt: String): ChatApiError {
        return when (t) {
            is UnknownHostException,
            is java.net.ConnectException,
            is SocketTimeoutException,
            is IOException -> ChatApiError(
                errorMessage = "Mất kết nối mạng. Kiểm tra Wi‑Fi/4G rồi bấm Thử lại.",
                canRetry = true,
                rawPrompt = rawPrompt,
                kind = ChatErrorKind.NETWORK
            )
            else -> ChatApiError(
                errorMessage = "Không gửi được tin. Bấm Thử lại.",
                canRetry = true,
                rawPrompt = rawPrompt,
                kind = ChatErrorKind.OTHER
            )
        }
    }
}
