package com.example.tinawarehouse.data

import com.example.tinawarehouse.i18n.tr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Các hàm đọc JSON "dễ tính" và xử lý phản hồi của công ty. Port từ `CompanyJSON` + `lenientString/lenientDouble` (APIClient.swift):
 * số/chuỗi lẫn lộn hoặc thiếu trường đều không làm hỏng cả danh sách.
 */

/** Giá trị của khoá [key] nếu phần tử này là object (giống `subscript` của `CompanyJSON`). */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** Chuỗi, chỉ khi là chuỗi JSON. */
fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

/** Số nguyên: số JSON, hoặc chuỗi số (giống `intValue` của iOS). */
fun JsonElement?.intValue(): Int? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return p.content.toIntOrNull()
    return p.doubleOrNull?.toInt()
}

/** Số nguyên chặt: chỉ nhận số JSON nguyên (giống `decode(Int.self)`); chuỗi hay null → null. */
fun JsonElement?.strictInt(): Int? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull || p.isString) return null
    return p.longOrNull?.toInt()
}

/** Chuỗi dễ tính: chuỗi, hoặc số (nguyên rồi thực) đổi thành chuỗi. Boolean/null → null. */
fun JsonElement?.lenientString(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return p.content
    p.longOrNull?.let { return it.toString() }
    p.doubleOrNull?.let { return it.toString() }
    return null
}

/** Số thực dễ tính: số JSON, hoặc chuỗi số. */
fun JsonElement?.lenientDouble(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return p.content.toDoubleOrNull()
    return p.doubleOrNull
}

/** Mảng các chuỗi (bỏ phần tử không phải chuỗi); thiếu → danh sách rỗng. */
fun JsonElement?.stringList(): List<String> =
    (this as? JsonArray)?.mapNotNull { it.stringValue() } ?: emptyList()

/** Các phần tử là object của một mảng; thiếu → danh sách rỗng. */
fun JsonElement?.objects(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

// ---------------------------------------------------------------------------------------------
// Kiện đang block
// ---------------------------------------------------------------------------------------------

/**
 * Kiện đang block thì trả về **lý do block** để hiện cho nhân viên (chỉ lý do, không kèm giờ/nguồn); không block thì rỗng.
 * JSON thật 06/11/2026: `blockRequestedAt`, `metadata.block = { at, info: [ { at, reason, source } ] }` — lấy các `reason`
 * nằm dưới khoá có chữ "block" (bỏ trùng, giữ thứ tự). Có dấu hiệu block mà không có lý do thì trả về "Không rõ lý do".
 */
fun JsonElement.blockHints(): List<String> {
    if (blockRaw().isEmpty()) return emptyList()
    val reasons = mutableListOf<String>()
    for (r in blockReasons(inBlock = false)) if (r !in reasons) reasons += r
    return if (reasons.isEmpty()) listOf(tr("Không rõ lý do")) else reasons
}

/** Các chuỗi `reason` nằm bên trong một khoá có chữ "block" (vd. metadata.block.info[].reason). */
private fun JsonElement.blockReasons(inBlock: Boolean): List<String> = when (this) {
    is JsonObject -> {
        val out = mutableListOf<String>()
        for (k in keys.sorted()) {
            val v = this[k] ?: continue
            if (inBlock && k.lowercase() == "reason" && v.stringValue() != null) {
                val r = v.stringValue()!!.trim()
                if (r.isNotEmpty()) out += r
            } else {
                out += v.blockReasons(inBlock || k.lowercase().contains("block"))
            }
        }
        out
    }
    is JsonArray -> flatMap { it.blockReasons(inBlock) }
    else -> emptyList()
}

/**
 * Dấu hiệu "đang block" trong phản hồi: khoá có chữ "block" mà giá trị có nghĩa (không null/false/rỗng/0),
 * hoặc trạng thái có chữ BLOCK (vd. "BLOCKED"); trả về danh sách "khoá: giá trị" (chỉ để nhận biết, không hiện ra).
 */
private fun JsonElement.blockRaw(path: String = ""): List<String> {
    val out = mutableListOf<String>()
    if (this !is JsonObject) return out
    for (k in keys.sorted()) {
        val v = this[k] ?: continue
        val name = if (path.isEmpty()) k else "$path.$k"
        val meaningful = when (v) {
            is JsonNull -> false
            is JsonObject -> v.isNotEmpty()
            is JsonArray -> v.isNotEmpty()
            is JsonPrimitive -> when {
                v.isString -> v.content.isNotEmpty()
                v.booleanOrNull != null -> v.booleanOrNull == true
                v.doubleOrNull != null -> v.doubleOrNull != 0.0
                else -> true
            }
        }
        val s = v.stringValue()
        if (k.lowercase().contains("block") && meaningful) {
            out += name + ": " + v.pretty().trim().replace("\n", " ")
        } else if (s != null && k.lowercase() in setOf("status", "matchstatus", "checkinresult", "state") && s.uppercase().contains("BLOCK")) {
            out += "$name: $s"
        } else if (v is JsonObject) {
            out += v.blockRaw(name)
        } else if (v is JsonArray) {
            v.forEachIndexed { i, x -> out += x.blockRaw("$name[$i]") }
        }
    }
    return out
}

/** Dạng chữ nhiều dòng, dễ đọc (dùng để xem nguyên văn phản hồi của công ty). */
fun JsonElement.pretty(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is JsonNull -> "null"
        is JsonArray -> if (isEmpty()) "[]" else joinToString("") { "\n$pad- " + it.pretty(indent + 1) }
        is JsonObject -> if (isEmpty()) "{}" else keys.sorted().joinToString("") { "\n$pad$it: " + (this[it]?.pretty(indent + 1) ?: "") }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> if (booleanOrNull == true) "true" else "false"
            else -> {
                val n = doubleOrNull
                if (n == null) content else if (n == Math.rint(n)) n.toLong().toString() else n.toString()
            }
        }
    }
}

/**
 * Mọi chuỗi trông như link ảnh (…jpg/jpeg/png/webp/heic, hoặc nằm dưới /uploads/) trong JSON, không trùng lặp, đúng thứ tự.
 * Link bắt đầu bằng "/" được ghép với địa chỉ máy chủ công ty ([serverOrigin]).
 */
fun JsonElement.imageUrls(serverOrigin: String): List<String> {
    val out = mutableListOf<String>()
    fun walk(j: JsonElement) {
        when (j) {
            is JsonArray -> j.forEach { walk(it) }
            is JsonObject -> j.keys.sorted().forEach { k -> j[k]?.let { walk(it) } }
            is JsonPrimitive -> {
                val t = j.stringValue() ?: return
                val low = t.lowercase()
                val isImg = listOf(".jpg", ".jpeg", ".png", ".webp", ".heic").any { low.contains(it) } || low.contains("/uploads/")
                if (!isImg || !(low.startsWith("http") || t.startsWith("/"))) return
                val full = if (t.startsWith("/")) serverOrigin + t else t
                if (full !in out) out += full
            }
        }
    }
    walk(this)
    return out
}
