package com.example.tinawarehouse.data

/**
 * Lỗi khi gọi API công ty. `status` <= 0 là lỗi mạng/địa chỉ (chưa có phản hồi HTTP).
 * Port từ `CompanyError` (APIClient.swift).
 */
class CompanyError(val status: Int, val code: String?, message: String) : Exception(message) {

    /** Câu hiển thị cho người dùng, kèm mã HTTP để dễ báo lỗi. */
    val description: String
        get() {
            val msg = message ?: ""
            if (status <= 0) return msg
            val extra = code ?: ""
            return if (extra.isEmpty()) "$msg (HTTP $status)" else "$msg (HTTP $status, $extra)"
        }
}

/** Lỗi nào cũng đổi được thành một câu cho người dùng. */
fun Throwable.userMessage(): String = (this as? CompanyError)?.description ?: (message ?: toString())
