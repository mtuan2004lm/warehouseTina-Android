package com.example.tinawarehouse.domain.station

import com.example.tinawarehouse.domain.scan.TrackingCutter

/** Trạm (mã + tên) trên hệ thống công ty. Port từ `CompanyRef` (APIClient.swift). */
data class CompanyRef(val id: Int, val code: String, val name: String)

/**
 * Hồ sơ một trạm (kho): mọi thứ KHÁC NHAU giữa các trạm nằm ở đây — cách cắt mã vạch, quy tắc mã kho,
 * sửa lỗi OCR và cách nhận ra trạm trên hệ thống công ty. Phần chung (đăng nhập, phiên, quét…) không nằm trong hồ sơ.
 *
 * Mỗi trạm một file (StationUS.kt, StationDE.kt). Thêm trạm mới: tạo object giống StationUS rồi thêm vào [all].
 * Port từ `StationProfile` (Stations/StationProfile.swift).
 */
class StationProfile(
    val id: String,                           // mã ngắn trong app: "US", "DE"
    val title: String,                        // tên hiển thị
    val subtitle: String,                     // vd. "OR_1"
    val isReady: Boolean,                     // false = chưa có quy tắc: hiện ở màn đăng nhập nhưng không chọn được
    /** Nhận ra trạm trên hệ thống công ty: mã khớp đúng ([codes]) hoặc mã/tên có chứa một từ trong [keywords]. */
    val codes: List<String>,
    val keywords: List<String>,
    /** Mẫu cắt mã vạch thành tracking, thử theo thứ tự (xem [TrackingCutter]). */
    val trackingFormats: List<TrackingCutter.Format>,
    /** Thêm biến thể ZIP+4 (xxxxx-xxxx) vào danh sách hỏi công ty — chỉ có nghĩa với USPS (Mỹ). */
    val zipPlus4Variants: Boolean,
    /** Quy tắc mã kho, thứ tự = ưu tiên (quy tắc khớp đầu tiên thắng). */
    val warehouseRules: List<WarehouseRule>,
    /** Sửa lỗi OCR riêng của nhãn trạm này; nhận chuỗi đã gộp khoảng trắng + viết hoa. */
    val ocrFix: (String) -> String,
) {
    /** Trạm [ref] trên hệ thống công ty có phải trạm này không. Không biết trạm (null) thì coi là đúng để không giấu phiên. */
    fun matches(ref: CompanyRef?): Boolean {
        if (ref == null) return true
        val code = ref.code.uppercase()
        val name = ref.name.uppercase()
        if (codes.any { it.uppercase() == code }) return true
        return keywords.any { k -> code.contains(k.uppercase()) || name.contains(k.uppercase()) }
    }

    companion object {
        /** Các trạm có trong app, theo thứ tự hiện ở màn đăng nhập. */
        val all: List<StationProfile> by lazy { listOf(StationUS.profile, StationDE.profile) }

        fun find(id: String): StationProfile? = all.firstOrNull { it.id == id }

        /** Trạm mặc định khi chưa chọn hoặc trạm đã lưu chưa sẵn sàng (giống iOS: trạm Mỹ). */
        val default: StationProfile get() = StationUS.profile

        /** Trạm cần dùng cho mã [savedId] đã lưu: không có/chưa sẵn sàng thì về [default]. */
        fun resolve(savedId: String?): StationProfile =
            savedId?.let { find(it) }?.takeIf { it.isReady } ?: default
    }
}
