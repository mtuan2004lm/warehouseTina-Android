package com.example.tinawarehouse.domain.scan

import com.example.tinawarehouse.domain.station.StationProfile

/**
 * Cắt chuỗi mã vạch thành mã tracking.
 * Port từ `TrackingCutter` (APIClient.swift) của app iOS — cùng thứ tự thử, cùng kết quả.
 * Khác iOS ở một điểm: trạm được truyền vào tường minh (iOS đọc trạm đang chọn từ biến toàn cục).
 */
object TrackingCutter {

    enum class Mode {
        /** Lấy N ký tự cuối. */
        LAST,

        /** Bỏ N ký tự đầu. */
        DROP_FIRST,

        /** Lấy đoạn đầu tiên khớp regex ở `param`. */
        REGEX,

        /** Dùng nguyên mã. */
        FULL;

        companion object {
            /** Chuỗi không biết (vd. từ server) coi như FULL, giống nhánh `default` của iOS. */
            fun from(value: String): Mode = entries.firstOrNull { it.name == value } ?: FULL
        }
    }

    /** Một mẫu cắt: nếu `detect` (regex, tìm trong mã) khớp thì áp dụng `mode` với `param`. */
    data class Format(val detect: String, val mode: Mode, val param: String = "")

    /** `list`: các mã ứng viên để hỏi công ty (mã gốc đứng đầu). `primary`: mã tracking đã cắt, dùng khi ghi kiện. */
    data class Candidates(val list: List<String>, val primary: String)

    /** Bỏ ký tự điều khiển + khoảng trắng, viết hoa. */
    fun normalize(s: String): String =
        s.filter { it.code >= 32 && it.code != 127 && !it.isWhitespace() }.uppercase()

    private fun has(pattern: String, s: String): Boolean = Regex(pattern).containsMatchIn(s)

    private fun apply(f: Format, code: String): String? {
        if (!has(f.detect, code)) return null
        val n = f.param.toIntOrNull() ?: 0
        return when (f.mode) {
            Mode.LAST -> if (n > 0 && code.length >= n) code.takeLast(n) else null
            Mode.DROP_FIRST -> if (n > 0 && code.length > n) code.drop(n) else null
            Mode.REGEX -> Regex(f.param).find(code)?.value
            Mode.FULL -> code
        }
    }

    /** Các nhóm số của ZIP+4 (nếu khớp mẫu), dùng cho mã 420 + ZIP9 và ZIP9. */
    private fun groups(pattern: String, s: String): List<String>? =
        Regex(pattern).find(s)?.groupValues?.drop(1)?.takeIf { it.isNotEmpty() }

    fun candidates(barcode: String, profile: StationProfile): Candidates {
        val code = normalize(barcode)
        val list = mutableListOf(code)
        val derived = mutableListOf<String>()
        for (f in profile.trackingFormats) {
            val v = apply(f, code) ?: continue
            if (v == code || v in list) continue
            list += v
            derived += v
        }
        if (profile.zipPlus4Variants) {                       // ZIP+4 chỉ có ở USPS (trạm Mỹ)
            for (v in list.toList()) {                        // duyệt bản sao, giống `for v in list` của Swift
                groups("^420(\\d{5})(\\d{4})$", v)?.takeIf { it.size == 2 }?.let { g ->
                    val x = g[0] + "-" + g[1]; if (x !in list) list += x
                }
                groups("^(\\d{5})(\\d{4})$", v)?.takeIf { it.size == 2 }?.let { g ->
                    val x = g[0] + "-" + g[1]; if (x !in list) list += x
                }
                if ('-' in v) {
                    val x = v.replace("-", ""); if (x !in list) list += x
                }
            }
        }
        return Candidates(list, derived.firstOrNull() ?: code)
    }
}
