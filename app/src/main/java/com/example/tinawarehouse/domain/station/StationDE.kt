package com.example.tinawarehouse.domain.station

/**
 * TRẠM ĐỨC — DE_FRANKFURT · Hub Frankfurt. Port từ `Stations/DE/StationDE.swift`.
 *
 * CÒN THIẾU (chờ quy tắc từ manager kho Đức): `trackingFormats` (mẫu cắt mã Hermes/DPD/DHL/GLS/UPS),
 * `warehouseRules` và `ocrFix`. Khi có thì điền vào đây; hiện để rỗng nên kiện quét ra sẽ "THIẾU MÃ KHO".
 */
object StationDE {
    val profile = StationProfile(
        id = "DE",
        title = "Hub Frankfurt",
        subtitle = "DE_FRANKFURT · Hub Frankfurt",
        isReady = true,                                   // chọn được ở màn đăng nhập
        codes = listOf("DE_FRANKFURT"),
        keywords = listOf("FRANKFURT", "GERMANY", "DEUTSCHLAND"),
        trackingFormats = emptyList(),                    // TODO: mẫu tracking các hãng ở Đức
        zipPlus4Variants = false,
        warehouseRules = emptyList(),                     // TODO: quy tắc mã kho trạm Đức
        ocrFix = { it },                                  // TODO: sửa lỗi OCR nhãn Đức
    )
}
