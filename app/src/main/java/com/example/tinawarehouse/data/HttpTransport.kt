package com.example.tinawarehouse.data

/** Một yêu cầu HTTP đã dựng sẵn (địa chỉ đầy đủ, header, nội dung). */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val timeoutSeconds: Long,
)

class HttpResponse(val status: Int, val body: String)

/**
 * Lớp gửi HTTP. Bản chạy thật dùng OkHttp (xem `OkHttpTransport`); test dùng bản giả để kiểm tra
 * luồng đăng nhập/làm mới token mà không cần mạng. Lỗi mạng thì ném IOException (hoặc Exception khác).
 */
interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}
