package com.example.tinawarehouse.domain.scan

import com.example.tinawarehouse.domain.station.RuleMatcher
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.state.ScanFlow

/**
 * Bộ máy ghép "mã vạch + mã kho" của MỘT tem từ luồng camera. Port từ phần "Đầu vào từ camera" + "Cửa sổ đọc một nhãn"
 * của AutoScanViewModel.swift, nhưng viết đồng bộ (không tự chạy timer): màn quét gọi [tick] ~20 lần/giây.
 * Thuần Kotlin, đồng hồ tiêm vào ([clock], mili giây) để test được.
 *
 *  - Đưa tem vào khung một lần: app gom mã vạch (và các mã vạch khác cùng tem, tối đa 4) + dòng mã kho (OCR) trong ~2,5 giây.
 *  - Đủ cả hai → gửi ([Action.Submit]); chờ thêm ≤ 0,5 giây để các mã khác cùng tem kịp vào khung.
 *  - Hết giờ mà thiếu mã kho: tem tiếp theo của cùng kiện đang chờ → dùng mã kho cũ; tem mới → lần 1 báo THIẾU MÃ KHO,
 *    lần 2 (trong 60 giây) gửi không kèm mã kho.
 *  - Mã đã gửi đưa lại (đã rời khung > 0,7 giây) → [Action.Resend] để công ty trả MÃ TRÙNG.
 */
class ScanEngine(
    private val matcher: (List<String>) -> RuleMatcher.LineMatch?,
    private val pendingCount: () -> Int = { 0 },
    private val clock: () -> Long = System::currentTimeMillis,
    val windowMs: Long = 2500,
) {
    sealed interface Action {
        data class Submit(val code: String, val text: String, val group: String, val extras: List<String>) : Action
        data class EndCode(val text: String) : Action
        /** Nhãn đã gửi được đưa lại: hỏi công ty (trả DUPLICATE → MÃ TRÙNG). */
        data class Resend(val code: String) : Action
        /** Lần đầu chưa đọc được mã kho của tem mới: chưa gửi, nhắc đưa tem vào lại. */
        data class NoWarehouse(val code: String) : Action
        data class Hint(val text: String) : Action
    }

    private class Pending(var barcode: String?, val extras: MutableList<String> = mutableListOf(), var warehouse: Pair<String, String>?, val start: Long)

    private var pending: Pending? = null
    private var recentMatch: Triple<String, String, Long>? = null       // text, group, lúc đọc
    private val done = HashSet<String>()                                // mã đã gửi (hoặc đang gửi) → không gửi lại
    private val cooldown = HashMap<String, Long>()
    private val lastSeen = HashMap<String, Long>()
    private val lastDupAt = HashMap<String, Long>()
    private var suppressTextUntil = 0L
    private val failCooldown = HashMap<String, Long>()
    private var endCodeAt = 0L
    private val noKhoMiss = HashMap<String, Long>()
    private var lastLabel: Pair<String, String>? = null                 // mã kho của tem đang quét
    /** true: vừa trúng kiện BLOCK — bỏ qua mọi đầu vào cho tới khi bấm "Quét tiếp tục". */
    var blockHold = false

    /** Đang trong cửa sổ đọc một nhãn. */
    val reading: Boolean get() = pending != null
    /** Tiến độ cửa sổ đọc 0..1. */
    fun progress(): Float = pending?.let { ((clock() - it.start).toFloat() / windowMs).coerceIn(0f, 1f) } ?: 0f
    /** Mã kho của tem đang quét (dùng khi END CODE). */
    val lastLabelText: String get() = lastLabel?.first ?: ""

    fun onBarcodes(raw: List<String>): List<Action> {
        if (blockHold) return emptyList()
        val out = mutableListOf<Action>()
        val now = clock()
        val codes = raw.map { it.trim() }
        val hasNew = codes.any { it.isNotEmpty() && !ScanFlow.isEndCode(it) && it !in done }
        for (code in codes) {
            if (code.isEmpty()) continue
            if (ScanFlow.isEndCode(code)) {
                if (now - endCodeAt < 3000) continue
                endCodeAt = now
                clearPending()
                out += Action.EndCode(lastLabelText)
                continue
            }
            val prevSeen = lastSeen[code]
            lastSeen[code] = now
            if (code in done) {
                // Nhãn đã gửi: còn nằm trong khung thì im lặng; rời khung rồi đưa lại → MÃ TRÙNG
                if (!hasNew) {
                    suppressTextUntil = now + 2000
                    if (pending != null && pending?.barcode == null) clearPending()
                }
                if (prevSeen != null && now - prevSeen > 700 && (lastDupAt[code]?.let { now - it > 4000 } ?: true)) {
                    lastDupAt[code] = now
                    out += Action.Resend(code)
                } else if (!hasNew) {
                    out += Action.Hint(tr("Tem này đã quét rồi — đưa ra khỏi khung rồi đưa lại để báo trùng"))
                }
                continue
            }
            if (failCooldown[code]?.let { now - it < 8000 } == true) {
                out += Action.Hint(tr("Tem này vừa báo lỗi — đợi vài giây rồi đưa lại")); continue
            }
            if (cooldown[code]?.let { now - it < 1500 } == true) continue

            suppressTextUntil = 0
            // Tem cũ đã rời khung mà chưa có mã kho: chốt ngay
            val pb = pending?.barcode
            if (pb != null && pb != code && pb !in codes && pending?.warehouse == null) out += expire()
            val p = pending
            if (p == null) {
                begin(Pending(code, warehouse = null, start = now))
            } else if (p.barcode == null) {
                p.barcode = code
            } else if (p.barcode != code) {
                // Mã khác trong cùng khung = tracking khác trên cùng tem: gom lại
                if (code !in p.extras && p.extras.size < 4 && p.barcode in codes) p.extras += code
                continue
            }
            pending?.let { cur ->
                if (cur.barcode == code) for (o in codes) {
                    if (o.isNotEmpty() && o != code && !ScanFlow.isEndCode(o) && o !in done && o !in cur.extras && cur.extras.size < 4) cur.extras += o
                }
            }
            // Mã kho có thể đã đọc được vài khung hình trước khi thấy mã vạch
            val r = recentMatch
            if (pending?.warehouse == null && r != null && now - r.third < 3000) pending?.warehouse = r.first to r.second
            out += tryComplete()
            return out
        }
        return out
    }

    fun onLines(lines: List<String>): List<Action> {
        if (blockHold) return emptyList()
        val hit = matcher(lines) ?: return emptyList()
        val now = clock()
        if (now < suppressTextUntil) return emptyList()
        if (failCooldown[hit.text]?.let { now - it < 8000 } == true) return emptyList()
        val m = hit.text to hit.rule.customerGroup
        recentMatch = Triple(m.first, m.second, now)
        lastLabel = m
        val p = pending
        if (p == null) begin(Pending(null, warehouse = m, start = now)) else if (p.warehouse == null) p.warehouse = m
        return tryComplete()
    }

    /** Gọi đều (~50 ms): hết cửa sổ đọc thì chốt; hết thời gian chờ mã phụ thì gửi. */
    fun tick(): List<Action> {
        val p = pending ?: return emptyList()
        if (clock() - p.start >= windowMs) return expire()
        return tryComplete()
    }

    /** Lỗi khi gửi (không phải mất mạng): cho phép quét lại các mã này. */
    fun forget(codes: Collection<String>) { done.removeAll(codes.toSet()) }

    fun clearPending() { pending = null; recentMatch = null }

    /** Bấm "Quét tiếp tục" sau kiện BLOCK. */
    fun continueAfterBlock() { blockHold = false; clearPending() }

    private fun begin(p: Pending) { pending = p }

    private fun tryComplete(): List<Action> {
        val p = pending ?: return emptyList()
        val code = p.barcode ?: return emptyList()
        val w = p.warehouse ?: return emptyList()
        // Chờ thêm chút để các mã khác cùng tem kịp vào khung
        if (p.extras.size < 2 && clock() - p.start < 500) return emptyList()
        clearPending()
        return listOf(submit(code, w.first, w.second, p.extras.toList()))
    }

    private fun expire(): List<Action> {
        val p = pending ?: return emptyList()
        clearPending()
        val now = clock()
        // Đọc được mã kho mà không thấy mã vạch → nhắc nhẹ
        if (p.barcode == null) {
            p.warehouse?.let { failCooldown[it.first] = now; return listOf(Action.Hint(tr("Chưa thấy mã vạch — đưa mã vạch vào khung"))) }
            return emptyList()
        }
        val code = p.barcode!!
        if (p.warehouse != null) return listOf(submit(code, p.warehouse!!.first, p.warehouse!!.second, p.extras.toList()))
        // Tracking tiếp theo của cùng tem đang chờ: dùng mã kho của tem đó
        val l = lastLabel
        if (pendingCount() > 0 && l != null) return listOf(submit(code, l.first, l.second, p.extras.toList()))
        // Tem mới chưa đọc được mã kho: lần đầu cảnh báo, lần hai (trong 60 giây) gửi không kèm mã kho
        val miss = noKhoMiss[code]
        if (miss != null && now - miss < 60_000) {
            noKhoMiss.remove(code)
            return listOf(submit(code, "", "", p.extras.toList()))
        }
        noKhoMiss[code] = now
        cooldown[code] = now + 1500          // đợi ~3 giây rồi mới đọc lại
        return listOf(Action.NoWarehouse(code))
    }

    private fun submit(code: String, text: String, group: String, extras: List<String>): Action {
        val now = clock()
        done += code; cooldown[code] = now
        for (x in extras) { done += x; cooldown[x] = now }
        return Action.Submit(code, text, group, extras)
    }
}
