package com.example.tinawarehouse.domain.station

/** Quy tắc mã kho: regex nhận dạng nhóm khách + loại hàng. Port từ `WarehouseRule` (Models.swift). */
data class WarehouseRule(
    val id: Int,
    val customerGroup: String,
    val patternRegex: String,
    val businessType: String,          // KINH_DOANH | KHONG_KINH_DOANH
    val requiresImportCheck: Boolean,
    val isActive: Boolean,
) {
    val isBusiness: Boolean get() = businessType == "KINH_DOANH"
}
