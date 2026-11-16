package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyScanTarget
import com.example.tinawarehouse.data.CompanySession
import com.example.tinawarehouse.data.CompanyTracking
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.domain.scan.TrackingCutter
import com.example.tinawarehouse.domain.station.StationProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Âm/rung cần phát sau một thao tác (màn hình tự phát theo cài đặt). */
enum class Feedback { DUPLICATE, PENDING, END_REQUIRED, WRITTEN, WRITTEN_BLOCK, NO_NAME, ERROR, CLOSED }

data class SessionDetailUi(
    val session: CompanySession,
    val items: List<CompanyTracking> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    // Ô gõ/dán mã (quy tắc giống web: xem ScanFlow)
    val code: String = "",
    val busy: Boolean = false,
    val pending: List<String> = emptyList(),
    val result: FlowResult? = null,
    // Đóng phiên
    val closing: Boolean = false,
    val closed: Boolean = false,
    val closeError: String? = null,
) {
    /** Phiên còn mở thì được quét tiếp / ghi mã / đóng phiên. */
    val isOpen: Boolean get() = session.status == "OPEN" && !closed

    /**
     * Số thứ tự kiện theo lúc quét, giống web: máy chủ trả kiện MỚI NHẤT ở trên cùng, nên dòng trên cùng mang số lớn nhất.
     * Chưa tải hết thì tổng lấy "Đã quét" của phiên (không nhỏ hơn số dòng đã tải).
     */
    val rowTotal: Int get() = if (hasMore) maxOf(session.actualScannedCount ?: 0, items.size) else items.size
    fun rowNumber(index: Int): Int = maxOf(rowTotal - index, 1)
}

/** Chi tiết một phiên: danh sách kiện theo trang, ô gõ mã theo quy tắc web (ScanFlow), đóng phiên. */
class SessionDetailStore(
    private val api: CompanyApi,
    initial: CompanySession,
    station: () -> StationProfile,
    private val feedback: (Feedback) -> Unit = {},
    private val pageSize: Int = 30,
) {
    private val _ui = MutableStateFlow(SessionDetailUi(initial))
    val ui: StateFlow<SessionDetailUi> = _ui.asStateFlow()
    private val sessionId = initial.id
    val flow = ScanFlow(api, CompanyScanTarget(initial.id, initial.carrierId), station)

    fun onCodeChange(v: String) = _ui.update { it.copy(code = v) }

    /** Lấy lại phiên (số đã quét) và tải lại danh sách kiện từ trang 1. */
    suspend fun refreshAll() {
        runCatching { api.session(sessionId) }.getOrNull()?.let { fresh -> _ui.update { it.copy(session = fresh) } }
        _ui.update { it.copy(items = emptyList(), page = 1, hasMore = true, error = null) }
        loadMore()
    }

    suspend fun loadMore() {
        val u = _ui.value
        if (!u.hasMore || u.loading) return
        _ui.update { it.copy(loading = true) }
        try {
            val got = api.trackings(sessionId, u.page, pageSize)
            _ui.update { s ->
                val known = s.items.map { it.id }.toSet()
                s.copy(items = s.items + got.filter { it.id !in known }, hasMore = got.size >= pageSize, page = s.page + 1, error = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.userMessage(), hasMore = false) }
        } finally {
            _ui.update { it.copy(loading = false) }
        }
    }

    /** Nhấn Enter ở ô mã: xử lý theo quy tắc web; xong thì xoá ô để nhập mã tiếp. */
    suspend fun submit() {
        val u0 = _ui.value
        if (u0.code.isBlank() || u0.busy) return
        _ui.update { it.copy(busy = true) }
        val items = u0.items
        val r = flow.submit(u0.code, listedLocally = { set ->
            items.any { t -> TrackingCutter.normalize(t.trackingNumber) in set || t.additionalTrackingNumbers.any { TrackingCutter.normalize(it) in set } }
        }, write = u0.isOpen)
        finish(r, clearInput = r.kind != FlowResult.Kind.ERROR && r.kind != FlowResult.Kind.OFFLINE)
    }

    /** Nút END CODE: ghi kiện NO NAME từ các mã đang chờ. */
    suspend fun endCode() {
        if (_ui.value.busy || !_ui.value.isOpen) return
        _ui.update { it.copy(busy = true) }
        finish(flow.endCode(), clearInput = false)
    }

    fun removePending(code: String) { flow.remove(code); _ui.update { it.copy(pending = flow.pending.value) } }
    fun makeMain(code: String) { flow.makeMain(code); _ui.update { it.copy(pending = flow.pending.value) } }

    private suspend fun finish(r: FlowResult, clearInput: Boolean) {
        _ui.update { it.copy(busy = false, result = r, pending = flow.pending.value, code = if (clearInput) "" else it.code) }
        when (r.kind) {
            FlowResult.Kind.WRITTEN -> feedback(if (r.blocks.isEmpty()) Feedback.WRITTEN else Feedback.WRITTEN_BLOCK)
            FlowResult.Kind.NO_NAME -> feedback(if (r.blocks.isEmpty()) Feedback.NO_NAME else Feedback.WRITTEN_BLOCK)
            FlowResult.Kind.PENDING, FlowResult.Kind.NOT_FOUND_ONLY -> feedback(Feedback.PENDING)
            FlowResult.Kind.END_REQUIRED -> feedback(Feedback.END_REQUIRED)
            FlowResult.Kind.DUPLICATE, FlowResult.Kind.ALREADY_PENDING -> feedback(Feedback.DUPLICATE)
            FlowResult.Kind.ERROR, FlowResult.Kind.OFFLINE -> feedback(Feedback.ERROR)
            FlowResult.Kind.FOUND_ONLY, FlowResult.Kind.NOTHING_PENDING -> Unit
        }
        if (r.kind == FlowResult.Kind.WRITTEN || r.kind == FlowResult.Kind.NO_NAME) refreshAll()
    }

    /** GHI: đóng phiên trên công ty. Trả về true nếu đóng được (màn hình quay về danh sách phiên). */
    suspend fun closeSession(): Boolean {
        if (_ui.value.closing) return false
        _ui.update { it.copy(closing = true) }
        return try {
            api.closeSession(sessionId)
            _ui.update { it.copy(closing = false, closed = true) }
            feedback(Feedback.CLOSED); true
        } catch (e: CancellationException) {
            _ui.update { it.copy(closing = false) }; throw e
        } catch (e: Exception) {
            _ui.update { it.copy(closing = false, closeError = e.userMessage()) }; false
        }
    }

    fun dismissCloseError() = _ui.update { it.copy(closeError = null) }
}
