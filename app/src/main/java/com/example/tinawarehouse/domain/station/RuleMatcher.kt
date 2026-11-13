package com.example.tinawarehouse.domain.station

/**
 * Khớp chuỗi đọc được (OCR) với bộ quy tắc regex. Cùng thứ tự và cách khớp với server:
 * duyệt theo id tăng dần, quy tắc đầu tiên khớp thắng, không phân biệt hoa/thường.
 * Port từ `RuleMatcher` (RuleMatcher.swift).
 */
class RuleMatcher(rules: List<WarehouseRule>) {

    private class Compiled(val rule: WarehouseRule, val regex: Regex)

    private val compiled: List<Compiled> = rules
        .filter { it.isActive }
        .sortedBy { it.id }
        .mapNotNull { rule ->
            runCatching { Regex(rule.patternRegex, RegexOption.IGNORE_CASE) }.getOrNull()?.let { Compiled(rule, it) }
        }

    val isEmpty: Boolean get() = compiled.isEmpty()

    fun match(text: String): WarehouseRule? {
        val value = normalize(text)
        if (value.isEmpty()) return null
        return compiled.firstOrNull { it.regex.containsMatchIn(value) }?.rule
    }

    /** Dòng OCR khớp + quy tắc khớp. */
    data class LineMatch(val text: String, val rule: WarehouseRule)

    /**
     * Thử từng dòng OCR (và cả đoạn ghép 2 dòng liên tiếp, vì mã kho đôi khi bị ngắt dòng).
     * [profile] cho biết cách sửa lỗi OCR riêng của trạm.
     */
    fun firstMatch(lines: List<String>, profile: StationProfile): LineMatch? {
        val cleaned = lines.map { normalizeOCR(it, profile) }.filter { it.isNotEmpty() }
        val candidates = cleaned.toMutableList()
        if (cleaned.size > 1) for (i in 0 until cleaned.size - 1) candidates += cleaned[i] + " " + cleaned[i + 1]
        for (c in candidates) match(c)?.let { return LineMatch(c, it) }
        // OCR hay dính thêm chữ phía trước (vd. "USPS DELIVER TO: FDVAT HUE"): thử bỏ dần tối đa 4 từ đầu dòng
        for (c in cleaned) {
            val words = c.split(" ").filter { it.isNotEmpty() }
            if (words.size <= 1) continue
            for (drop in 1..minOf(4, words.size - 1)) {
                val tail = words.drop(drop).joinToString(" ")
                match(tail)?.let { return LineMatch(tail, it) }
            }
        }
        return null
    }

    companion object {
        /** Gộp khoảng trắng thừa, bỏ ký tự điều khiển. */
        fun normalize(s: String): String = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

        /**
         * Chuẩn hoá chữ OCR trước khi khớp quy tắc: gộp khoảng trắng, viết hoa, rồi sửa lỗi OCR RIÊNG của [profile].
         * Văn bản đã chuẩn hoá cũng là chuỗi được gửi lên server.
         */
        fun normalizeOCR(s: String, profile: StationProfile): String = profile.ocrFix(normalize(s).uppercase())
    }
}
