package com.example.tinawarehouse.data

import com.example.tinawarehouse.domain.station.CompanyRef
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Mô hình dữ liệu của API công ty. Port từ APIClient.swift; đọc JSON "dễ tính" bằng [JsonElement]
 * (số/chuỗi lẫn lộn hoặc thiếu trường không làm hỏng cả danh sách).
 * Khung phản hồi của mọi API: `{ success, statusCode, message, data, meta? }`.
 */

data class CompanyTokens(val accessToken: String, val refreshToken: String)

data class CompanyUserRef(val id: String, val username: String, val fullName: String?)

data class CompanyMe(
    val id: String,
    val username: String,
    val fullName: String?,
    val systemRole: String?,          // vd. "ADMIN"
    val stations: List<CompanyRef>,   // trạm được GÁN (admin: rỗng)
)

data class CompanyCarrier(val id: Int, val code: String, val name: String, val isActive: Boolean?)

data class CompanyMapping(val id: Int, val customerCode: String, val destinationHubId: Int?)

/** Phiên công ty mà màn quét sẽ ghi vào. */
data class CompanyScanTarget(val sessionId: Int, val carrierId: Int)

/** Kết quả hỏi công ty "mã này đã có chưa" (check-tracking-scan). */
data class CheckResult(val status: String, val detail: String, val blocks: List<String>)

/** Kết quả ghi một kiện (trackings/scan). */
data class ScanResult(val id: String, val matchStatus: String, val checkIn: String, val blocks: List<String>)

/** Một phần của yêu cầu multipart/form-data. */
class CompanyPart(val name: String, val filename: String?, val mime: String?, val data: ByteArray)

data class CompanySession(
    val id: Int,                         // id nội bộ — DÙNG KHI GỌI API (scan, đóng phiên, lấy kiện)
    val stationId: Int,
    val carrierId: Int,
    val status: String,                  // OPEN | CLOSED
    val driverName: String?,
    val dockGate: String?,
    val actualScannedCount: Int?,
    val carrierReportedCount: Int?,
    val licensePlate: String?,
    val vehicleType: String?,
    val vehicleArrivedAt: String?,
    val carrier: CompanyRef?,
    val station: CompanyRef?,
    val user: CompanyUserRef?,
    val code: String?,                   // mã phiên do web sinh cho NGƯỜI DÙNG đọc (vd. DHL0024)
) {
    /**
     * Nhãn phiên hiển thị, GIỐNG WEB: **mã hãng + id (4 chữ số)** — vd. id 24 hãng DHL → "DHL0024".
     * API có sẵn trường `code` thì ưu tiên; không có hãng thì "#<id nội bộ>".
     */
    val displayCode: String
        get() {
            code?.takeIf { it.isNotEmpty() }?.let { return it }
            carrier?.code?.takeIf { it.isNotEmpty() }?.let { return it.uppercase() + "%04d".format(id) }
            return "#$id"
        }

    companion object {
        /** Mã phiên: thử lần lượt vài tên trường hay gặp (chưa xác minh tên thật trên API). */
        private val CODE_KEYS = listOf("code", "sessionCode", "receivingSessionCode", "sessionNo", "refNo")

        fun from(o: JsonObject): CompanySession = CompanySession(
            id = o["id"].strictInt() ?: 0,
            stationId = o["stationId"].strictInt() ?: 0,
            carrierId = o["carrierId"].strictInt() ?: 0,
            status = o["status"].stringValue() ?: "",
            driverName = o["driverName"].stringValue(),
            dockGate = o["dockGate"].stringValue(),
            actualScannedCount = o["actualScannedCount"].strictInt(),
            carrierReportedCount = o["carrierReportedCount"].strictInt(),
            licensePlate = o["licensePlate"].stringValue(),
            vehicleType = o["vehicleType"].stringValue(),
            vehicleArrivedAt = o["vehicleArrivedAt"].stringValue(),
            carrier = parseRef(o["carrier"]),
            station = parseRef(o["station"]),
            user = parseUserRef(o["user"]),
            code = CODE_KEYS.firstNotNullOfOrNull { k -> o[k].stringValue()?.takeIf { it.isNotEmpty() } },
        )
    }
}

fun parseRef(e: JsonElement?): CompanyRef? {
    val o = e as? JsonObject ?: return null
    val id = o["id"].strictInt() ?: return null
    val code = o["code"].stringValue() ?: return null
    val name = o["name"].stringValue() ?: return null
    return CompanyRef(id, code, name)
}

fun parseUserRef(e: JsonElement?): CompanyUserRef? {
    val o = e as? JsonObject ?: return null
    val username = o["username"].stringValue() ?: return null
    return CompanyUserRef(o["id"].lenientString() ?: "", username, o["fullName"].stringValue())
}

fun parseMe(o: JsonObject): CompanyMe? {
    val username = o["username"].stringValue() ?: return null
    return CompanyMe(
        id = o["id"].lenientString() ?: "",
        username = username,
        fullName = o["fullName"].stringValue(),
        systemRole = o["systemRole"].stringValue(),
        stations = o["stations"].objects().mapNotNull { parseRef(it) },
    )
}

fun parseCarrier(o: JsonObject): CompanyCarrier? {
    val id = o["id"].strictInt() ?: return null
    val code = o["code"].stringValue() ?: return null
    val name = o["name"].stringValue() ?: return null
    return CompanyCarrier(id, code, name, (o["isActive"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull())
}

fun parseMapping(o: JsonObject): CompanyMapping? {
    val id = o["id"].strictInt() ?: return null
    val code = o["customerCode"].stringValue() ?: return null
    return CompanyMapping(id, code, o["destinationHubId"].strictInt())
}

// ---------------------------------------------------------------------------------------------
// Kiện (trackings)
// ---------------------------------------------------------------------------------------------

data class CompanyOrderItem(
    val itemId: String?,
    val clientOrderId: String?,
    val itemName: String?,
    val quantity: Double?,
    val actualQuantity: Double?,
    val damagedQuantity: Double?,
    val normalQuantity: Double?,
) {
    companion object {
        fun from(o: JsonObject) = CompanyOrderItem(
            itemId = o["itemId"].lenientString(), clientOrderId = o["clientOrderId"].lenientString(),
            itemName = o["itemName"].lenientString(), quantity = o["quantity"].lenientDouble(),
            actualQuantity = o["actualQuantity"].lenientDouble(), damagedQuantity = o["damagedQuantity"].lenientDouble(),
            normalQuantity = o["normalQuantity"].lenientDouble(),
        )
    }
}

data class CompanyHoldCase(val reasonName: String?, val resolvedAt: String?, val resolution: String?) {
    companion object {
        fun from(o: JsonObject): CompanyHoldCase {
            val reason = o["holdCase"]["reason"]
            return CompanyHoldCase(
                reasonName = reason["name"].lenientString() ?: reason["code"].lenientString(),
                resolvedAt = o["resolvedAt"].lenientString(),
                resolution = o["resolution"].lenientString(),
            )
        }
    }
}

data class CompanyTracking(
    val id: String,
    val trackingNumber: String,
    val additionalTrackingNumbers: List<String>,
    val carrierId: Int?,
    val customerCode: String?,
    val status: String?,          // CHECKED_IN | ON_HOLD | CONSOLIDATED | CHECKED_OUT | ARRIVED_GATEWAY_VN | RETURNED_TO_SELLER
    val condition: String?,       // NORMAL | DAMAGED
    val matchStatus: String?,     // MATCHED | NO_NAME | …
    val binLocation: String?,
    val receivingSessionId: Int?,
    val orderItemCount: Double?,
    val totalQuantity: Double?,
    val totalActualQuantity: Double?,
    val totalDamagedQuantity: Double?,
    val checkedInAt: String?,
    val labelPhotoCount: Int,
    val labelPhotoUrls: List<String>,
    val orderItems: List<CompanyOrderItem>,
    val holdCases: List<CompanyHoldCase>,
) {
    /** Kiện có dòng hàng khai báo. `orderItemCount` luôn có, `orderItems` chỉ có khi gọi kèm include. */
    val hasOrderLines: Boolean get() = orderItems.isNotEmpty() || (orderItemCount ?: 0.0) > 0

    /** "Kiện không xác định" theo cách web công ty tính: `matchStatus` NO_NAME VÀ chưa có dòng hàng khai báo. */
    val isUnknown: Boolean get() = matchStatus == "NO_NAME" && !hasOrderLines

    companion object {
        fun from(o: JsonObject): CompanyTracking {
            val urls = o["labelPhotoUrls"].stringList()
            val photos = o["labelPhotos"].objects()
            return CompanyTracking(
                id = o["id"].lenientString() ?: java.util.UUID.randomUUID().toString(),
                trackingNumber = o["trackingNumber"].lenientString() ?: "?",
                additionalTrackingNumbers = o["additionalTrackingNumbers"].stringList(),
                carrierId = o["carrierId"].lenientDouble()?.toInt(),
                customerCode = o["customerCode"].lenientString(),
                status = o["status"].lenientString(),
                condition = o["condition"].lenientString(),
                matchStatus = o["matchStatus"].lenientString(),
                binLocation = o["binLocation"].lenientString(),
                receivingSessionId = o["receivingSessionId"].lenientDouble()?.toInt(),
                orderItemCount = o["orderItemCount"].lenientDouble(),
                totalQuantity = o["totalQuantity"].lenientDouble(),
                totalActualQuantity = o["totalActualQuantity"].lenientDouble(),
                totalDamagedQuantity = o["totalDamagedQuantity"].lenientDouble(),
                checkedInAt = o["checkedInAt"].lenientString(),
                labelPhotoCount = maxOf(urls.size, photos.size),
                labelPhotoUrls = if (urls.isEmpty()) photos.mapNotNull { it["url"].stringValue() } else urls,
                orderItems = o["orderItems"].objects().map { CompanyOrderItem.from(it) },
                holdCases = o["holdCaseItems"].objects().map { CompanyHoldCase.from(it) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hư hỏng
// ---------------------------------------------------------------------------------------------

/** Loại hư hỏng của công ty (code dùng khi gửi báo cáo, name để hiển thị). */
data class CompanyDamageType(val code: String, val name: String) {
    companion object {
        fun from(o: JsonObject): CompanyDamageType {
            val code = o["code"].lenientString() ?: "?"
            return CompanyDamageType(code, o["name"].lenientString() ?: code)
        }
    }
}

/** Báo cáo hư hỏng của một kiện (đọc từ JSON, chịu được trường thiếu). */
data class CompanyDamageReport(
    val decision: String?,        // RECEIVED | REJECTED
    val reportedAt: String?,
    val typeNames: List<String>,
    val typeCodes: List<String>,
    val photoUrls: List<String>,
) {
    companion object {
        fun from(json: JsonElement, serverOrigin: String): CompanyDamageReport {
            val types = json["damageTypes"].let { (it as? kotlinx.serialization.json.JsonArray) ?: emptyList<JsonElement>() }
            val names = types.mapNotNull { it["name"].stringValue() ?: it["code"].stringValue() }
            val codes = types.mapNotNull { it["code"].stringValue() }
            var urls = json["photoUrls"].stringList()
            if (urls.isEmpty()) urls = json["photos"].objects().mapNotNull { it["url"].stringValue() }
            return CompanyDamageReport(
                decision = json["decision"].stringValue(),
                reportedAt = json["reportedAt"].stringValue(),
                typeNames = names,
                typeCodes = codes,
                photoUrls = if (urls.isEmpty()) json.imageUrls(serverOrigin) else urls,
            )
        }
    }
}
