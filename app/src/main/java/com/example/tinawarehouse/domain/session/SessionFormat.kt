package com.example.tinawarehouse.domain.session

import com.example.tinawarehouse.data.CompanySession
import com.example.tinawarehouse.i18n.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Các hàm định dạng dùng chung cho danh sách phiên. Port từ SessionsView.swift. */
object SessionFormat {
    private val dm = DateTimeFormatter.ofPattern("dd/MM")
    private val dmHm = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    /** Chuỗi giờ ISO của API → Instant (có hoặc không có phần lẻ giây); không đọc được thì null. */
    fun parseIso(iso: String?): Instant? {
        if (iso.isNullOrEmpty()) return null
        return runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull()
    }

    /** Giờ ISO của API → giờ máy "dd/MM HH:mm". Không đọc được thì trả nguyên chuỗi; rỗng thì null. */
    fun time(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (iso.isNullOrEmpty()) return null
        val t = parseIso(iso) ?: return iso
        return dmHm.format(t.atZone(zone))
    }

    /** Giờ hoặc "—" (dùng trong danh sách kiện). */
    fun timeOrDash(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String = time(iso, zone) ?: "—"

    fun dayMonth(d: java.time.LocalDate): String = dm.format(d)

    /** "đã quét / hãng khai" giống web (vd 6 / 20). */
    fun count(s: CompanySession): String {
        val scanned = s.actualScannedCount ?: 0
        val reported = s.carrierReportedCount ?: return "$scanned"
        return "$scanned / $reported"
    }

    enum class CountLevel { NEUTRAL, DONE, MISSING }

    /** Đủ kiện (đã quét ≥ hãng khai) → DONE (xanh); còn thiếu → MISSING (vàng); không có số khai → NEUTRAL. */
    fun countLevel(s: CompanySession): CountLevel {
        val reported = s.carrierReportedCount ?: return CountLevel.NEUTRAL
        return if ((s.actualScannedCount ?: 0) >= reported) CountLevel.DONE else CountLevel.MISSING
    }

    /** Tên người mở phiên: họ tên nếu có, không thì username. */
    fun openedBy(s: CompanySession): String? {
        val u = s.user ?: return null
        return if (u.fullName.isNullOrEmpty()) u.username else u.fullName
    }

    /** Biển số · loại xe (bỏ phần rỗng). */
    fun vehicle(s: CompanySession): String = listOfNotNull(s.licensePlate, s.vehicleType).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Tên hãng: ưu tiên tên API trả kèm, rồi tra theo id; không có thì "hãng {id}". */
    fun carrierName(s: CompanySession, carriers: Map<Int, String>): String {
        s.carrier?.name?.takeIf { it.isNotEmpty() }?.let { return it }
        carriers[s.carrierId]?.let { return it }
        return tr("hãng {0}", s.carrierId)
    }

    /** Tên ảnh logo hãng (không đuôi) theo tên/mã hãng, hoặc null nếu hãng chưa có logo. */
    fun carrierLogo(name: String, code: String?): String? {
        for (text in listOf(name, code ?: "")) {
            val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val joined = tokens.joinToString("")
            if (joined.contains("fedex")) return "logo_fedex"
            if (joined.contains("dhl")) return "logo_dhl"
            if (joined.contains("ontrac")) return "logo_ontrac"
            if (joined.contains("amazon")) return "logo_amazon"
            if ("usps" in tokens) return "logo_usps"
            if ("ups" in tokens) return "logo_ups"
        }
        return null
    }
}
