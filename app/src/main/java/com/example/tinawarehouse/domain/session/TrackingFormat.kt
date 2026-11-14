package com.example.tinawarehouse.domain.session

import com.example.tinawarehouse.data.CompanyTracking
import com.example.tinawarehouse.i18n.tr

/** Định dạng thông tin kiện. Port từ các hàm co* trong SessionsView.swift. */
object TrackingFormat {
    fun status(s: String?): String = when (s) {
        "CHECKED_IN" -> tr("Đã nhập")
        "ON_HOLD" -> tr("Đang giữ")
        "CONSOLIDATED" -> tr("Đã gom thùng")
        "CHECKED_OUT" -> tr("Đã xuất")
        "ARRIVED_GATEWAY_VN" -> tr("Đã đến cổng VN")
        "RETURNED_TO_SELLER" -> tr("Trả người bán")
        else -> s ?: "—"
    }

    /** Số lượng: số nguyên thì bỏ ".0"; thiếu thì "—". */
    fun qty(v: Double?): String {
        if (v == null) return "—"
        return if (v == Math.rint(v)) v.toLong().toString() else v.toString()
    }

    /** Tên hàng/đơn đã liên kết, vd "Kem chống nắng (đơn 990001)"; chưa có tên thì "Khớp". */
    fun matchedText(t: CompanyTracking): String {
        val names = t.orderItems.mapNotNull { it2 ->
            val n = it2.itemName ?: it2.itemId ?: return@mapNotNull null
            it2.clientOrderId?.let { "$n (${tr("đơn")} $it)" } ?: n
        }
        return if (names.isEmpty()) tr("Khớp") else names.joinToString(", ")
    }

    /** Dòng "Khớp đơn" trong chi tiết kiện. */
    fun matchLine(t: CompanyTracking): String = when {
        t.isUnknown -> tr("Kiện không xác định (không có trong danh sách)")
        t.matchStatus == "MATCHED" || t.hasOrderLines -> matchedText(t)
        else -> t.matchStatus ?: "—"
    }

    fun condition(c: String?): String = when (c) {
        "DAMAGED" -> tr("Hư hỏng")
        "NORMAL" -> tr("Bình thường")
        else -> c ?: "—"
    }

    fun decision(d: String?): String = when (d) {
        "RECEIVED" -> tr("Nhận hàng")
        "REJECTED" -> tr("Từ chối")
        else -> d ?: "—"
    }

    /** "khách X · kệ Y" (bỏ phần thiếu). */
    fun who(t: CompanyTracking): String =
        listOfNotNull(t.customerCode?.let { tr("khách {0}", it) }, t.binLocation?.let { tr("kệ {0}", it) }).joinToString(" · ")
}
