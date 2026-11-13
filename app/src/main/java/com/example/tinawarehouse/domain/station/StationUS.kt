package com.example.tinawarehouse.domain.station

import com.example.tinawarehouse.domain.scan.TrackingCutter.Format
import com.example.tinawarehouse.domain.scan.TrackingCutter.Mode

/**
 * TRẠM MỸ — OR_1 Hub Oregon. Mọi quy tắc riêng của trạm Mỹ nằm trong file này.
 * Port từ `Stations/US/StationUS.swift` của app iOS (09/11/2026), nội dung không đổi.
 */
object StationUS {

    val profile = StationProfile(
        id = "US",
        title = "Hub Oregon",
        subtitle = "OR_1",
        isReady = true,
        codes = listOf("OR_1", "OR1"),
        keywords = emptyList(),           // OREGON là trạm thừa/test — bỏ để US khớp ĐÚNG OR_1 (nhận bằng codes)
        trackingFormats = trackingFormats(),
        zipPlus4Variants = true,
        warehouseRules = warehouseRules(),
        ocrFix = { ocrFix(it) },
    )

    /** 1. Cắt mã vạch thành tracking (mẫu đầu khớp trước; mã đã cắt đầu tiên là tracking dùng khi ghi kiện). */
    private fun trackingFormats() = listOf(
        Format("^420\\d{5}\\d{20,}$", Mode.DROP_FIRST, "8"),          // USPS: 420 + ZIP5 + tracking
        Format("^420\\d{9}\\d{20,}$", Mode.DROP_FIRST, "12"),         // USPS: 420 + ZIP9 + tracking
        Format("^9\\d{19,33}$", Mode.FULL),                           // USPS: 20–34 số bắt đầu bằng 9
        Format("^[A-Z]{2}\\d{9}US$", Mode.FULL),                      // USPS quốc tế
        Format("1Z[0-9A-Z]{16}", Mode.REGEX, "1Z[0-9A-Z]{16}"),       // UPS (kể cả trong chuỗi dài)
        Format("^\\d{30,34}$", Mode.LAST, "12"),                      // FedEx Express: 12 số cuối
        Format("^96\\d{20}$", Mode.LAST, "15"),                       // FedEx Ground: 15 số cuối
        Format("^92\\d{20}$", Mode.LAST, "20"),                       // FedEx SmartPost: 20 số cuối
        Format("^(\\d{12}|\\d{15})$", Mode.FULL),                     // FedEx: 12 hoặc 15 số
        Format("^\\d{10}$", Mode.FULL),                               // DHL Express
        Format("^(JD|JJD|JVGL|GM)\\d{16,20}$", Mode.FULL),            // DHL eCommerce
        Format("TB[ACM]\\d{12}", Mode.REGEX, "TB[ACM]\\d{12}"),       // Amazon Logistics
    )

    /** 2. Quy tắc mã kho (đã gồm bản sửa FADO.VN cho "FD VAT HUE"). Thứ tự = ưu tiên. */
    private fun warehouseRules() = listOf(
        WarehouseRule(1, "Giaonhan247", "^(SGKYDUYEN_|HNKYDUYEN_|SGVO_|HNVO_)", "KHONG_KINH_DOANH", false, true),
        WarehouseRule(2, "FADO 168", "^(CHHEN_|SG HUE_DOFA168|SG FRAGILE_DOFA168)", "KHONG_KINH_DOANH", false, true),
        WarehouseRule(3, "MICAFI", "(MCF$|MCF\\s)", "KHONG_KINH_DOANH", false, true),
        WarehouseRule(
            4, "FADO.VN", "^(FD ?VAT ?HUE|SG ?HUE DOFA|HN ?BANGTY DOFA|SG ?FRAGILE DOFA|HN ?FRAGILE DOFA)",
            "KINH_DOANH", true, true,
        ),
    )

    /**
     * 3. Sửa lỗi OCR hay gặp trên nhãn trạm Mỹ trước khi khớp quy tắc:
     *  - bỏ tiền tố "SHIP TO" / "DELIVER TO" / "TO" (vd. "SHIP TO SGHUE DOFA" -> "SGHUE DOFA")
     *  - "HUF" bị đọc sai thay "HUE" (HNHUF MCF → HNHUE MCF)
     *  - số 0 bị đọc thay chữ O trong tiền tố: "SGV0_A12" -> "SGVO_A12"
     *  - dấu "_" bị đọc thành khoảng trắng/gạch ngang: "SGVO A12" -> "SGVO_A12"
     *  - "SG HUE DOFA16&." (số 8 bị đọc sai) / "SG FRAGILE DOFA168" (mất "_") -> "SG HUE_DOFA168" / "SG FRAGILE_DOFA168",
     *    nếu không sẽ bị nhầm sang FADO.VN (kinh doanh)
     */
    fun ocrFix(s: String): String {
        var t = s
        // Bỏ nhãn "SHIP TO" / "DELIVER TO" đứng trước mã kho (cùng một dòng với mã kho trên tem USPS/DHL)
        repeat(2) {
            t = t.replace(Regex("^((USPS|UPS|FEDEX|DHL)\\s+)?(SHIP\\s*TO|DELIVER(Y)?\\s*TO|SHIP|TO)\\s*[:\\-]?\\s+"), "")
        }
        // OCR hay đọc chữ E cuối của "HUE" (Huế) thành F
        t = t.replace(Regex("^((?:HN|SG) ?HU|FD ?VAT ?HU)F\\b"), "\$1E")
        t = t.replace(Regex("^(SGV|HNV)0"), "\$1O")
        t = t.replace(Regex("^(SGKYDUYEN|HNKYDUYEN|SGVO|HNVO|CHHEN)[ \\-]"), "\$1_")
        t = t.replace(Regex("^(SG HUE|SG FRAGILE)[ \\-]DOFA168"), "\$1_DOFA168")
        // "168" hay bị đọc sai ("DOFA16&.", "DOFA165", "DOFA 168"…) → chuẩn hoá về FADO 168
        t = t.replace(Regex("^SG ?(HUE|FRAGILE)[ _\\-]*DOFA\\s*16\\S*"), "SG \$1_DOFA168")
        return t
    }
}
