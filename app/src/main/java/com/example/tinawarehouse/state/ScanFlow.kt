package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyError
import com.example.tinawarehouse.data.CompanyScanTarget
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.domain.scan.TrackingCutter
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Kết quả một lần nhập/quét mã. */
data class FlowResult(
    val kind: Kind,
    val code: String = "",
    val message: String = "",
    val blocks: List<String> = emptyList(),
    val matchStatus: String = "",
    val trackingId: String = "",
    val seq: Int = 0,                 // PENDING: mã thứ mấy trong kiện đang chờ (1..3)
) {
    enum class Kind {
        WRITTEN,           // công ty có mã → đã ghi 1 kiện (mã đang chờ thành mã phụ)
        NO_NAME,           // END CODE → đã ghi kiện NO NAME
        PENDING,           // công ty không có → thêm vào kiện đang chờ ("ting")
        END_REQUIRED,      // đã đủ 3 mã chờ mà nhập tiếp → bắt buộc END CODE
        DUPLICATE,         // mã đã quét trong phiên
        ALREADY_PENDING,   // mã đã nằm trong kiện đang chờ
        NOTHING_PENDING,   // bấm END CODE khi chưa có mã chờ
        FOUND_ONLY,        // phiên đã đóng: chỉ kiểm tra, công ty có mã
        NOT_FOUND_ONLY,    // phiên đã đóng: chỉ kiểm tra, công ty không có
        ERROR,
    }
}

/**
 * Quy tắc quét kiện vào phiên công ty — GIỐNG WEB và màn quét iOS (`sendCompany` trong AutoScanViewModel.swift):
 *  - Mã công ty CÓ (FOUND) → ghi 1 kiện ngay; các mã đang chờ thành mã phụ của kiện đó.
 *  - Mã công ty KHÔNG CÓ → thêm vào kiện đang chờ (tối đa 3); đủ 3 mà nhập tiếp → chặn, bắt buộc END CODE.
 *  - END CODE → ghi kiện NO NAME: mã đầu là mã chính, các mã còn lại là mã phụ.
 * Dùng chung cho ô gõ tay ở chi tiết phiên và màn quét camera (Đợt 5). Danh sách chờ chỉ nằm trong bộ nhớ.
 */
class ScanFlow(
    private val api: CompanyApi,
    private val target: CompanyScanTarget,
    private val station: () -> StationProfile,
) {
    companion object {
        const val MAX_PENDING = 3

        /** Chỉ nhận đúng "ENDCODE" (tem Code 39 *ENDCODE* có thể kèm dấu * đầu/cuối). */
        fun isEndCode(s: String): Boolean = s.trim().trim('*').uppercase() == "ENDCODE"
    }

    private val _pending = MutableStateFlow<List<String>>(emptyList())
    /** Các mã chưa có thông tin của kiện đang chờ; mã đầu là mã chính. */
    val pending: StateFlow<List<String>> = _pending.asStateFlow()
    private var pendingText = ""            // mã kho (mã khách) đọc được cùng các mã chờ
    private val lock = Mutex()              // mỗi lần chỉ xử lý một mã (chống nhấn Enter hai lần)

    /** Bỏ một mã khỏi kiện đang chờ (gõ nhầm). */
    fun remove(code: String) { _pending.value = _pending.value - code; if (_pending.value.isEmpty()) pendingText = "" }

    /** Chọn một mã đang chờ làm mã chính (đưa lên đầu). */
    fun makeMain(code: String) {
        val l = _pending.value
        if (code in l) _pending.value = listOf(code) + (l - code)
    }

    fun clear() { _pending.value = emptyList(); pendingText = "" }

    /**
     * Nhập/quét một mã. [customer] = mã kho đọc được (ô gõ tay để trống). [extras] = các mã khác cùng thấy trên tem (camera).
     * [listedLocally] = kiểm tra nhanh mã đã có trong danh sách kiện đang hiện chưa (khỏi hỏi công ty).
     * [write] = false khi phiên đã đóng: chỉ kiểm tra, không ghi.
     */
    suspend fun submit(
        raw: String, customer: String = "", extras: List<String> = emptyList(),
        listedLocally: (Set<String>) -> Boolean = { false }, write: Boolean = true,
    ): FlowResult = lock.withLock {
        if (isEndCode(raw)) return@withLock if (write) endCodeLocked(customer) else FlowResult(FlowResult.Kind.NOTHING_PENDING)
        process(TrackingCutter.normalize(raw), customer, extras, listedLocally, write)
    }

    /** Nút END CODE: ghi kiện NO NAME từ các mã đang chờ. */
    suspend fun endCode(customer: String = ""): FlowResult = lock.withLock { endCodeLocked(customer) }

    private suspend fun process(
        code: String, customer: String, extras: List<String>, listedLocally: (Set<String>) -> Boolean, write: Boolean,
    ): FlowResult {
        if (code.isEmpty()) return FlowResult(FlowResult.Kind.ERROR)
        val cut = TrackingCutter.candidates(code, station())
        if (cut.primary in _pending.value) return FlowResult(FlowResult.Kind.ALREADY_PENDING, cut.primary, tr("Mã đã nằm trong kiện đang chờ"))
        if (listedLocally(cut.list.toSet())) return FlowResult(FlowResult.Kind.DUPLICATE, cut.primary, tr("Mã đã quét trong phiên"))
        if (write && _pending.value.size >= MAX_PENDING) {
            return FlowResult(FlowResult.Kind.END_REQUIRED, cut.primary, tr("Bắt buộc bấm nút END CODE"))
        }
        try {
            // Hỏi song song tất cả mã ứng viên, chọn mã đứng trước có FOUND / DUPLICATE
            val results = coroutineScope { cut.list.map { c -> async { api.checkTracking(c) } }.awaitAll() }
            val i = results.indexOfFirst { it.status == "FOUND" || it.status == "DUPLICATE" }
            val value = if (i >= 0) cut.list[i] else cut.primary
            val status = if (i >= 0) results[i].status else "NOT_FOUND"
            val checkBlocks = if (i >= 0) results[i].blocks else emptyList()

            if (status == "DUPLICATE") return FlowResult(FlowResult.Kind.DUPLICATE, value, tr("Mã đã quét trong phiên"), checkBlocks)
            if (!write) {
                return if (status == "FOUND") FlowResult(FlowResult.Kind.FOUND_ONLY, value, tr("Công ty có mã này trong đơn hàng"), checkBlocks)
                else FlowResult(FlowResult.Kind.NOT_FOUND_ONLY, value, tr("Mã không có trong đơn hàng"))
            }
            if (status == "FOUND") {
                // Mã phụ = các mã đang chờ + các mã khác cùng tem chưa được hỏi, bỏ trùng
                val additional = _pending.value.toMutableList()
                for (x in extras) {
                    val p = TrackingCutter.candidates(TrackingCutter.normalize(x), station()).primary
                    if (p != value && p !in cut.list && p !in additional) additional += p
                }
                val r = api.scan(value, additional, target.carrierId, customer, target.sessionId)
                clear()
                if (r.checkIn == "DUPLICATE") return FlowResult(FlowResult.Kind.DUPLICATE, value, tr("Mã đã quét trong phiên"))
                val note = r.matchStatus + (if (additional.isNotEmpty()) tr(" · +{0} mã phụ", additional.size) else "")
                return FlowResult(FlowResult.Kind.WRITTEN, value, note, (checkBlocks + r.blocks).toSortedSet().toList(), r.matchStatus, r.id)
            }
            // Không có thông tin → thêm vào kiện đang chờ ("ting")
            _pending.value = _pending.value + cut.primary
            if (customer.isNotEmpty()) pendingText = customer
            val seq = _pending.value.size
            val msg = if (seq >= MAX_PENDING) tr("Đủ 3 tracking — bấm nút END CODE") else tr("Tracking {0}/3 chưa có thông tin", seq)
            val res = FlowResult(FlowResult.Kind.PENDING, cut.primary, msg, seq = seq)
            // Camera: các mã khác cùng tem được hỏi tiếp theo thứ tự
            if (extras.isNotEmpty()) return process(TrackingCutter.normalize(extras.first()), customer, extras.drop(1), listedLocally, write)
            return res
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FlowResult(FlowResult.Kind.ERROR, code, e.userMessage())
        }
    }

    private suspend fun endCodeLocked(customer: String): FlowResult {
        val list = _pending.value
        if (list.isEmpty()) return FlowResult(FlowResult.Kind.NOTHING_PENDING, message = tr("Chưa có tracking nào đang chờ — không cần END CODE"))
        val wh = pendingText.ifEmpty { customer }
        return try {
            val r = api.scan(list.first(), list.drop(1), target.carrierId, wh, target.sessionId)
            clear()
            if (r.checkIn == "DUPLICATE") FlowResult(FlowResult.Kind.DUPLICATE, list.first(), tr("Mã đã quét trong phiên"))
            else FlowResult(FlowResult.Kind.NO_NAME, list.first(), "NO NAME · ${r.checkIn}" +
                (if (list.size > 1) tr(" · +{0} mã phụ", list.size - 1) else ""), r.blocks, r.matchStatus, r.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FlowResult(FlowResult.Kind.ERROR, list.first(), (e as? CompanyError)?.description ?: e.userMessage())
        }
    }
}
