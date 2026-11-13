package com.example.tinawarehouse.data

import com.example.tinawarehouse.domain.station.CompanyRef
import com.example.tinawarehouse.i18n.tr
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Một nơi duy nhất nói chuyện với API công ty (apiwh-dev). Port từ `CompanyAPI` (APIClient.swift).
 *
 * - Xác thực Bearer. **Refresh token dùng MỘT lần**: mỗi lần làm mới nhận cặp token mới.
 * - 401 → làm mới MỘT lần rồi gọi lại; nhiều lệnh cùng nhận 401 chỉ làm mới một lần ([refreshMutex]).
 * - Làm mới lỗi (400/401/403) → hết phiên: xoá token và phát [sessionExpired] để app về màn đăng nhập.
 * - Mật khẩu không bao giờ được lưu; chỉ lưu token (qua [TokenStore] đã mã hóa).
 */
class CompanyApi(
    private val transport: HttpTransport,
    private val tokens: TokenStore,
    private val baseUrl: String = DEFAULT_BASE,
    private val serverOrigin: String = SERVER_ORIGIN,
) {
    companion object {
        /** API dev; sẽ đổi sang production + versioning sau. */
        const val DEFAULT_BASE = "https://apiwh-dev.tinashipping.com/api"
        const val SERVER_ORIGIN = "https://apiwh-dev.tinashipping.com"
        private const val INCLUDE_SESSION = "user,station,carrier"
        private const val INCLUDE_TRACKING = "orderItems,holdCaseItems"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Phát một lần khi làm mới token thất bại (hết hạn / đăng nhập nơi khác) → về màn đăng nhập. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    val isSignedIn: Boolean get() = tokens.accessToken() != null

    private val refreshMutex = Mutex()

    // ------------------------------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------------------------------

    suspend fun login(username: String, password: String): CompanyMe {
        val data = request("POST", "/auth/login", json = mapOf("username" to username, "password" to password), auth = false).data()
        saveTokens(data)
        return me()
    }

    suspend fun logout() {
        tokens.refreshToken()?.let { rt ->
            tryOrNull { request("POST", "/auth/logout", json = mapOf("refreshToken" to rt), auth = false) }
        }
        tokens.clear()
    }

    private fun saveTokens(data: JsonElement?) {
        val access = data["accessToken"].stringValue()
        val refresh = data["refreshToken"].stringValue()
        if (access == null || refresh == null) {
            throw CompanyError(200, "DECODE", tr("Không đọc được phản hồi của API công ty: {0}", "token"))
        }
        tokens.save(access, refresh)
    }

    /**
     * Nhiều lệnh cùng nhận 401 một lúc chỉ được làm mới token MỘT lần; refreshToken dùng một lần nên lần làm mới thứ hai
     * sẽ bị từ chối và làm mất đăng nhập. Lệnh đến sau thấy token đã đổi thì chỉ cần gọi lại.
     */
    private suspend fun refreshOnce(usedToken: String?) {
        refreshMutex.withLock {
            val current = tokens.accessToken()
            if (current != usedToken) {
                // Lệnh khác đã làm mới xong (hoặc đã hết phiên: token bị xoá)
                if (current == null) throw sessionExpiredError()
                return
            }
            try {
                refresh()
            } catch (e: CompanyError) {
                if (e.status == 400 || e.status == 401 || e.status == 403) {
                    tokens.clear()
                    _sessionExpired.tryEmit(Unit)
                    throw sessionExpiredError()
                }
                throw e
            }
        }
    }

    private fun sessionExpiredError() =
        CompanyError(401, "SESSION_EXPIRED", tr("Hết phiên đăng nhập — vui lòng đăng nhập lại"))

    /** Làm mới token (refreshToken dùng một lần: mỗi lần làm mới nhận cặp token mới). */
    private suspend fun refresh() {
        val rt = tokens.refreshToken() ?: throw CompanyError(401, "NO_TOKEN", tr("Chưa đăng nhập công ty"))
        val data = request("POST", "/auth/refresh", json = mapOf("refreshToken" to rt), auth = false).data()
        saveTokens(data)
    }

    // ------------------------------------------------------------------------------------------
    // Chỉ đọc
    // ------------------------------------------------------------------------------------------

    suspend fun me(): CompanyMe {
        val data = request("GET", "/auth/me").data() as? JsonObject
        return data?.let { parseMe(it) }
            ?: throw CompanyError(200, "DECODE", tr("Không đọc được phản hồi của API công ty: {0}", "me"))
    }

    /** Danh sách tất cả trạm trên hệ thống (GET /stations). Dùng cho tài khoản ADMIN không được gán trạm cụ thể. */
    suspend fun stations(): List<CompanyRef> =
        request("GET", "/stations", query = listOf("limit" to "100")).data().objects().mapNotNull { parseRef(it) }

    /** Trạm tài khoản được PHÉP dùng: trạm được gán (`me.stations`); nếu không có mà vai trò là ADMIN thì lấy toàn bộ trạm. */
    suspend fun allowedStations(me: CompanyMe): List<CompanyRef> {
        if (me.stations.isNotEmpty()) return me.stations
        if (me.systemRole?.uppercase() == "ADMIN") return stations()
        return emptyList()
    }

    suspend fun carriers(): List<CompanyCarrier> =
        request("GET", "/carriers", query = listOf("limit" to "100")).data().objects().mapNotNull { parseCarrier(it) }

    suspend fun customerCodeMappings(): List<CompanyMapping> =
        request("GET", "/customer-code-mappings", query = listOf("limit" to "100")).data().objects().mapNotNull { parseMapping(it) }

    /** Một phiên theo id (kèm `actualScannedCount` tính sẵn). */
    suspend fun session(id: Int): CompanySession {
        val root = tryOrNull { request("GET", "/receiving-sessions/$id", query = listOf("include" to INCLUDE_SESSION)) }
            ?: request("GET", "/receiving-sessions/$id")
        return CompanySession.from(root.data() as? JsonObject ?: throw decodeError("session"))
    }

    /**
     * Danh sách phiên, lọc ở máy chủ giống web: trạng thái (OPEN | CLOSED), hãng, cửa (`dockGate`, khớp đúng),
     * giờ xe đến (`openedFrom`/`openedTo` — lọc theo `vehicleArrivedAt`). null/rỗng = không lọc.
     */
    suspend fun sessions(
        status: String?, carrierId: Int?, dockGate: String? = null,
        arrivedFrom: Instant? = null, arrivedTo: Instant? = null,
    ): List<CompanySession> {
        val q = mutableListOf("limit" to "50")
        if (!status.isNullOrEmpty()) q += "status" to status
        if (carrierId != null && carrierId > 0) q += "carrierId" to carrierId.toString()
        if (!dockGate.isNullOrEmpty()) q += "dockGate" to dockGate
        if (arrivedFrom != null) q += "openedFrom" to iso(arrivedFrom)
        if (arrivedTo != null) q += "openedTo" to iso(arrivedTo)
        // include=user,station,carrier nhúng sẵn người mở phiên, trạm và hãng; máy chủ từ chối include thì gọi lại như cũ.
        val root = tryOrNull { request("GET", "/receiving-sessions", query = q + ("include" to INCLUDE_SESSION)) }
            ?: request("GET", "/receiving-sessions", query = q)
        return root.data().objects().map { CompanySession.from(it) }
    }

    /** Kiện của một phiên (GET /trackings?receivingSessionId=). Trả về một trang; kiện quét MỚI NHẤT ở trên cùng. */
    suspend fun trackings(sessionId: Int, page: Int, limit: Int = 30): List<CompanyTracking> {
        val base = listOf(
            "receivingSessionId" to sessionId.toString(), "page" to page.toString(), "limit" to limit.toString(),
            "sort" to "checkedInAt", "order" to "desc",
        )
        val root = tryOrNull { request("GET", "/trackings", query = base + ("include" to INCLUDE_TRACKING)) }
            ?: request("GET", "/trackings", query = base)
        return root.data().objects().map { CompanyTracking.from(it) }
    }

    /** Chi tiết một kiện (GET /trackings/{id}). */
    suspend fun tracking(id: String): CompanyTracking {
        val enc = encodePath(id)
        val root = tryOrNull { request("GET", "/trackings/$enc", query = listOf("include" to INCLUDE_TRACKING)) }
            ?: request("GET", "/trackings/$enc")
        return CompanyTracking.from(root.data() as? JsonObject ?: throw decodeError("tracking"))
    }

    /** Báo cáo hư hỏng của một kiện (GET /trackings/{id}/damage-report). */
    suspend fun damageReport(trackingId: String): CompanyDamageReport {
        val raw = request("GET", "/trackings/${encodePath(trackingId)}/damage-report").data() ?: JsonNull
        return CompanyDamageReport.from(raw, serverOrigin)
    }

    /** Hỏi công ty "mã này đã có chưa" (POST /trackings/check-tracking-scan). Không tạo kiện. */
    suspend fun checkTracking(code: String): CheckResult {
        val data = request("POST", "/trackings/check-tracking-scan", json = mapOf("code" to code)).data() ?: JsonNull
        return CheckResult(data["status"].stringValue() ?: "?", data.pretty().trim(), data.blockHints())
    }

    /** Loại hư hỏng của công ty (GET /damage-types). */
    suspend fun damageTypes(): List<CompanyDamageType> =
        request("GET", "/damage-types", query = listOf("limit" to "100")).data().objects().map { CompanyDamageType.from(it) }

    // ------------------------------------------------------------------------------------------
    // GHI lên công ty (chỉ gọi khi người dùng đã xác nhận)
    // ------------------------------------------------------------------------------------------

    /** Tạo phiên nhận hàng (POST /receiving-sessions). Chỉ lấy `id`, không giải mã cả phiên. */
    suspend fun createSession(
        stationId: Int, carrierId: Int, driverName: String, reportedCount: Int, dockGate: String,
        licensePlate: String = "", vehicleType: String = "", vehicleArrivedAt: Instant? = null,
    ): Int {
        val body = mutableMapOf<String, Any?>(
            "stationId" to stationId, "carrierId" to carrierId, "driverName" to driverName, "carrierReportedCount" to reportedCount,
        )
        if (dockGate.isNotEmpty()) body["dockGate"] = dockGate
        if (licensePlate.isNotEmpty()) body["licensePlate"] = licensePlate      // biển số (không bắt buộc, giống web)
        if (vehicleType.isNotEmpty()) body["vehicleType"] = vehicleType         // loại xe (không bắt buộc, giống web)
        // Giờ xe đến (ISO 8601 UTC): danh sách phiên trên web lọc ngày theo giờ này; để trống thì phiên không hiện khi lọc ngày.
        if (vehicleArrivedAt != null) body["vehicleArrivedAt"] = iso(vehicleArrivedAt)
        val data = request("POST", "/receiving-sessions", json = body).data()
        return data["id"].intValue() ?: 0
    }

    /** Đóng phiên nhận hàng (PATCH /receiving-sessions/{id}): `status` = CLOSED kèm `vehicleDepartedAt`. */
    suspend fun closeSession(id: Int, now: Instant = Instant.now()) {
        request("PATCH", "/receiving-sessions/$id", json = mapOf("status" to "CLOSED", "vehicleDepartedAt" to iso(now)))
    }

    /** Quét một kiện (POST /trackings/scan). Gửi lại cùng mã thì công ty trả DUPLICATE (không tạo trùng). */
    suspend fun scan(
        trackingNumber: String, additional: List<String>, carrierId: Int, customerCode: String, sessionId: Int,
    ): ScanResult {
        val body = mutableMapOf<String, Any?>(
            "trackingNumber" to trackingNumber, "carrierId" to carrierId, "receivingSessionId" to sessionId,
        )
        if (additional.isNotEmpty()) body["additionalTrackingNumbers"] = additional
        if (customerCode.isNotEmpty()) body["customerCode"] = customerCode
        val data = request("POST", "/trackings/scan", json = body).data() ?: JsonNull
        val id = data["id"].stringValue() ?: data["id"].intValue()?.toString() ?: ""
        return ScanResult(id, data["matchStatus"].stringValue() ?: "?", data["checkInResult"].stringValue() ?: "?", data.blockHints())
    }

    /** Ảnh label của một kiện (POST /trackings/{id}/label-photos, multipart, trường `files`). */
    suspend fun uploadLabelPhoto(trackingId: String, jpeg: ByteArray) {
        request("POST", "/trackings/${encodePath(trackingId)}/label-photos",
            multipart = listOf(CompanyPart("files", "label.jpg", "image/jpeg", jpeg)))
    }

    /**
     * Báo hư hỏng của một kiện (POST /trackings/{id}/damage-report, multipart).
     * decision: "RECEIVED" (nhận hàng) hoặc "REJECTED" (từ chối). Mỗi kiện chỉ có một báo cáo (gửi lần hai bị 409).
     */
    suspend fun reportDamage(trackingId: String, typeCodes: List<String>, decision: String, photos: List<ByteArray>) {
        val parts = typeCodes.map { textPart("damageTypeCodes", it) }.toMutableList()
        parts += textPart("decision", decision)
        photos.forEachIndexed { i, d -> parts += CompanyPart("files", "damage${i + 1}.jpg", "image/jpeg", d) }
        request("POST", "/trackings/${encodePath(trackingId)}/damage-report", multipart = parts)
    }

    /**
     * SỬA báo cáo hư hỏng đã có (PATCH /trackings/{id}/damage-report, multipart, từng phần).
     * typeCodes: bộ loại mới (thay cả bộ). decision: chỉ gửi khi muốn đổi (null = giữ nguyên).
     * photos: ảnh mới THAY TOÀN BỘ ảnh cũ (rỗng = giữ nguyên). Từ chối (REJECTED) không đảo lại được.
     */
    suspend fun updateDamageReport(trackingId: String, typeCodes: List<String>, decision: String?, photos: List<ByteArray>) {
        val parts = typeCodes.map { textPart("damageTypeCodes", it) }.toMutableList()
        if (decision != null) parts += textPart("decision", decision)
        photos.forEachIndexed { i, d -> parts += CompanyPart("files", "damage${i + 1}.jpg", "image/jpeg", d) }
        request("PATCH", "/trackings/${encodePath(trackingId)}/damage-report", multipart = parts)
    }

    // ------------------------------------------------------------------------------------------
    // Gọi mạng chung
    // ------------------------------------------------------------------------------------------

    private suspend fun request(
        method: String, path: String,
        query: List<Pair<String, String>> = emptyList(),
        json: Map<String, Any?>? = null,
        multipart: List<CompanyPart>? = null,
        auth: Boolean = true,
        retry: Boolean = true,
    ): JsonElement {
        val url = baseUrl + path + if (query.isEmpty()) "" else "?" + query.joinToString("&") { (k, v) -> "${encodeQuery(k)}=${encodeQuery(v)}" }
        val headers = mutableMapOf("Accept" to "application/json")
        var body: ByteArray? = null
        if (json != null) {
            headers["Content-Type"] = "application/json"
            body = toJsonObject(json).toString().toByteArray(Charsets.UTF_8)
        }
        if (multipart != null) {
            val boundary = "Boundary-" + UUID.randomUUID()
            headers["Content-Type"] = "multipart/form-data; boundary=$boundary"
            body = buildMultipart(boundary, multipart)
        }
        var usedToken: String? = null
        if (auth) {
            val t = tokens.accessToken() ?: throw CompanyError(401, "NO_TOKEN", tr("Chưa đăng nhập công ty"))
            usedToken = t
            headers["Authorization"] = "Bearer $t"
        }
        val resp = try {
            transport.execute(HttpRequest(method, url, headers, body, if (multipart == null) 15 else 90))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw CompanyError(0, "NETWORK", tr("Không kết nối được tới API công ty: {0}", e.message ?: e.toString()))
        }
        if (resp.status == 401 && auth && retry) {
            // Token hết hạn: làm mới một lần rồi gọi lại. Nếu lệnh khác đã làm mới rồi thì chỉ cần gọi lại.
            refreshOnce(usedToken)
            return request(method, path, query, json, multipart, auth, retry = false)
        }
        if (resp.status !in 200..299) {
            val e = runCatching { this.json.parseToJsonElement(resp.body) }.getOrNull()
            throw CompanyError(resp.status, e["code"].stringValue(), e["message"].stringValue() ?: tr("Lỗi API công ty"))
        }
        return try {
            this.json.parseToJsonElement(resp.body)
        } catch (e: Exception) {
            throw CompanyError(resp.status, "DECODE", tr("Không đọc được phản hồi của API công ty: {0}", e.message ?: e.toString()))
        }
    }

    /** Phần `data` của khung phản hồi `{ success, statusCode, message, data, meta? }`. */
    private fun JsonElement.data(): JsonElement? = this["data"]

    private fun decodeError(what: String) = CompanyError(200, "DECODE", tr("Không đọc được phản hồi của API công ty: {0}", what))

    /** Giống `try?` của Swift: lỗi nào cũng thành null (trừ huỷ coroutine). */
    private suspend fun <T> tryOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun iso(t: Instant): String = t.truncatedTo(ChronoUnit.SECONDS).toString()      // vd. 2026-11-05T07:46:00Z

    private fun encodeQuery(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun encodePath(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun textPart(name: String, value: String) = CompanyPart(name, null, null, value.toByteArray(Charsets.UTF_8))

    private fun buildMultipart(boundary: String, parts: List<CompanyPart>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (p in parts) {
            var head = "--$boundary\r\nContent-Disposition: form-data; name=\"${p.name}\""
            if (p.filename != null) head += "; filename=\"${p.filename}\""
            head += "\r\n"
            if (p.mime != null) head += "Content-Type: ${p.mime}\r\n"
            out.write((head + "\r\n").toByteArray(Charsets.UTF_8))
            out.write(p.data)
            out.write("\r\n".toByteArray(Charsets.UTF_8))
        }
        out.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun toJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in map) put(k, toJsonElement(v))
    }

    private fun toJsonElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is List<*> -> buildJsonArray { v.forEach { add(toJsonElement(it)) } }
        else -> JsonPrimitive(v.toString())
    }
}
