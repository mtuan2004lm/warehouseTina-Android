package com.example.tinawarehouse.state

import com.example.tinawarehouse.domain.scan.ScanEngine
import com.example.tinawarehouse.domain.scan.ScanEngine.Action
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.sound.ScanSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Thẻ kết quả lớn trên màn quét (giống `LastResult` + `resultCard` của iOS). */
data class ScanCard(
    val kind: Kind,
    val code: String,
    val warehouse: String? = null,
    val message: String? = null,
    val track: Int = 0,               // TING: tracking thứ mấy (1..3)
    val business: Boolean = false,    // mã kho thuộc nhóm KINH DOANH → dải vàng "HÀNG KINH DOANH"
) {
    enum class Kind { SUCCESS, TING, NO_WAREHOUSE, NO_NAME, END_BLOCKED, DUPLICATE, BLOCKED, SKIPPED }
}

/** Một dòng lịch sử quét. */
data class ScanEntry(val code: String, val warehouse: String?, val status: Status, val note: String?, val time: Long) {
    enum class Status { SENT, NO_NAME, TRACKED, DUPLICATE, REJECTED, NO_WAREHOUSE }
}

data class ScanUi(
    val sent: Int = 0,
    val skipped: Int = 0,
    val queued: Int = 0,                 // đang chờ gửi lại do mất mạng
    val reading: Boolean = false,
    val progress: Float = 0f,
    val last: ScanCard? = null,
    val entries: List<ScanEntry> = emptyList(),
    val endRequired: Boolean = false,    // đủ 3 tracking không có thông tin → bắt buộc END CODE
    val blockCard: ScanCard? = null,     // kiện BLOCK: che màn hình tới khi bấm "Quét tiếp tục"
    val hint: String? = null,
    val banner: String? = null,
    val lastItemId: String? = null,      // kiện vừa ghi (Đợt 6: gắn ảnh label / báo hư hỏng)
    val lastItemCode: String? = null,
)

/**
 * Điều khiển màn quét camera: nhận mã vạch + dòng OCR → [ScanEngine] ghép một tem → [ScanFlow] ghi lên công ty
 * (cùng quy tắc 3 mã + END CODE với ô gõ tay) → thẻ kết quả, lịch sử, âm thanh. Mất mạng thì giữ lại và tự gửi lại mỗi 4 giây.
 * Port từ AutoScanViewModel.swift (`sendCompany`, `enqueue`). Thuần Kotlin để test được.
 */
class ScanController(
    private val flow: ScanFlow,
    matcher: (List<String>) -> com.example.tinawarehouse.domain.station.RuleMatcher.LineMatch?,
    private val isBusiness: (String) -> Boolean,
    private val scope: CoroutineScope,
    private val sound: (ScanSound) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val engine = ScanEngine(matcher, { flow.pending.value.size }, clock)
    private val _ui = MutableStateFlow(ScanUi())
    val ui: StateFlow<ScanUi> = _ui.asStateFlow()
    val pending: StateFlow<List<String>> get() = flow.pending

    private class Retry(val code: String, val text: String, val extras: List<String>, val end: Boolean)
    private val retryList = mutableListOf<Retry>()
    private var lastRetryAt = 0L
    private var hintUntil = 0L
    private var bannerUntil = 0L
    private var endCodeAt = 0L
    private val jobs = mutableListOf<Job>()

    fun onBarcodes(codes: List<String>) = handle(engine.onBarcodes(codes))
    fun onLines(lines: List<String>) = handle(engine.onLines(lines))

    /** Gọi đều ~50 ms: cửa sổ đọc, tiến độ, ẩn gợi ý, gửi lại hàng chờ. */
    fun tick() {
        handle(engine.tick())
        val now = clock()
        _ui.update {
            it.copy(
                reading = engine.reading, progress = engine.progress(),
                hint = if (now >= hintUntil) null else it.hint,
                banner = if (now >= bannerUntil) null else it.banner,
            )
        }
        if (retryList.isNotEmpty() && now - lastRetryAt >= 4000) {
            lastRetryAt = now
            val batch = retryList.toList(); retryList.clear()
            launch {
                for (r in batch) if (r.end) sendEnd(r.text) else send(r.code, r.text, r.extras)
                _ui.update { it.copy(queued = retryList.size) }
            }
        }
    }

    /** Nút END CODE: chốt các tracking đang chờ (chống bấm đúp 1,5 giây). */
    fun endCode() {
        if (flow.pending.value.isEmpty()) return
        val now = clock()
        if (now - endCodeAt < 1500) return
        endCodeAt = now
        engine.clearPending()
        launch { sendEnd(engine.lastLabelText) }
    }

    /** Chọn một mã đang chờ làm mã chính rồi chốt kiện NO NAME. */
    fun chooseMain(code: String) { flow.makeMain(code); endCodeAt = 0; endCode() }

    /** Hiện một dải thông báo 3 giây (vd. kết quả gửi ảnh label). */
    fun showBanner(text: String) { bannerUntil = clock() + 3000; _ui.update { it.copy(banner = text) } }

    /** Bấm "Quét tiếp tục" sau kiện BLOCK. */
    fun continueAfterBlock() { engine.continueAfterBlock(); _ui.update { it.copy(blockCard = null) } }

    /** Chờ mọi lệnh gửi đang chạy xong (dùng trong test). */
    suspend fun idle() { while (true) { val js = synchronized(jobs) { jobs.toList() }; if (js.all { it.isCompleted }) return; js.joinAll() } }

    private fun launch(block: suspend () -> Unit) { val j = scope.launch { block() }; synchronized(jobs) { jobs.removeAll { it.isCompleted }; jobs += j } }

    private fun handle(actions: List<Action>) {
        for (a in actions) when (a) {
            is Action.Submit -> launch { send(a.code, a.text, a.extras) }
            is Action.EndCode -> launch { sendEnd(a.text) }
            is Action.Resend -> launch { send(a.code, "", emptyList()) }
            is Action.NoWarehouse -> {
                card(ScanCard(ScanCard.Kind.NO_WAREHOUSE, a.code, message = tr("Chưa đọc được mã kho — đưa tem vào lại (chưa gửi lên công ty)")))
                sound(ScanSound.NO_WAREHOUSE)
            }
            is Action.Hint -> showHint(a.text)
        }
    }

    private fun card(c: ScanCard) = _ui.update { it.copy(last = c) }
    private fun entry(e: ScanEntry) = _ui.update { it.copy(entries = listOf(e) + it.entries.take(199)) }
    private fun showHint(t: String) { if (_ui.value.hint == null) { hintUntil = clock() + 1800; _ui.update { it.copy(hint = t) } } }
    private fun whNote(wh: String) = if (wh.isEmpty()) tr(" · không có mã kho") else tr(" · mã kho {0}", wh)

    /** Gửi MỘT tracking (kèm các mã khác cùng tem) theo quy tắc của [ScanFlow]. */
    private suspend fun send(code: String, text: String, extras: List<String>) {
        val customer = if (text.isEmpty() || text == code) "" else text
        val r = flow.submit(code, customer, extras)
        val now = clock()
        val wh = customer.ifEmpty { null }
        when (r.kind) {
            FlowResult.Kind.WRITTEN -> {
                val biz = isBusiness(customer)
                val note = r.message + whNote(customer) + (if (biz) " · " + tr("KINH DOANH") else "") +
                    (if (r.blocks.isNotEmpty()) " · " + tr("ĐANG BLOCK") else "")
                _ui.update { it.copy(sent = it.sent + 1, endRequired = false, lastItemId = r.trackingId.ifEmpty { null }, lastItemCode = r.code) }
                entry(ScanEntry(r.code, wh, if (r.matchStatus == "NO_NAME") ScanEntry.Status.NO_NAME else ScanEntry.Status.SENT, note, now))
                if (r.blocks.isNotEmpty()) {
                    // Công ty báo kiện đang BLOCK: báo to, dừng nhận mã tới khi bấm "Quét tiếp tục"
                    val c = ScanCard(ScanCard.Kind.BLOCKED, r.code, wh, tr("ĐANG BLOCK") + " · " + r.blocks.joinToString(" · "))
                    engine.blockHold = true; engine.clearPending()
                    _ui.update { it.copy(last = c, blockCard = c) }
                    sound(ScanSound.BLOCK)
                } else {
                    card(ScanCard(ScanCard.Kind.SUCCESS, r.code, wh, r.message + whNote(customer), business = biz))
                    sound(if (biz) ScanSound.BUSINESS else ScanSound.FOUND)
                }
            }
            FlowResult.Kind.DUPLICATE -> {
                _ui.update { it.copy(skipped = it.skipped + 1) }
                entry(ScanEntry(r.code, wh, ScanEntry.Status.DUPLICATE, tr("Đã quét trước đó"), now))
                card(ScanCard(ScanCard.Kind.DUPLICATE, r.code, message = tr("Mã đã quét trong phiên")))
                sound(ScanSound.DUPLICATE)
            }
            FlowResult.Kind.PENDING -> {
                val need = r.seq >= ScanFlow.MAX_PENDING
                if (need) _ui.update { it.copy(endRequired = true) }
                entry(ScanEntry(r.code, wh, ScanEntry.Status.TRACKED, r.message, now))
                card(ScanCard(ScanCard.Kind.TING, r.code, message = tr("Công ty không có mã này. Đã thử: ") + r.code, track = r.seq))
                sound(ScanSound.TING)
            }
            FlowResult.Kind.END_REQUIRED -> {
                engine.forget(listOf(code) + extras)
                _ui.update { it.copy(endRequired = true) }
                card(ScanCard(ScanCard.Kind.END_BLOCKED, r.code, message = tr("Bắt buộc bấm nút END CODE")))
                sound(ScanSound.BLOCKED)
            }
            FlowResult.Kind.OFFLINE -> enqueue(Retry(code, text, extras, end = false))
            FlowResult.Kind.ERROR -> {
                engine.forget(listOf(code) + extras)
                _ui.update { it.copy(skipped = it.skipped + 1) }
                entry(ScanEntry(code, wh, ScanEntry.Status.REJECTED, r.message, now))
                card(ScanCard(ScanCard.Kind.SKIPPED, code, wh, r.message))
                sound(ScanSound.REJECTED)
            }
            FlowResult.Kind.ALREADY_PENDING, FlowResult.Kind.NOTHING_PENDING, FlowResult.Kind.NO_NAME,
            FlowResult.Kind.FOUND_ONLY, FlowResult.Kind.NOT_FOUND_ONLY -> Unit
        }
    }

    /** END CODE: ghi kiện NO NAME từ các mã đang chờ. */
    private suspend fun sendEnd(text: String) {
        val r = flow.endCode(text)
        val now = clock()
        when (r.kind) {
            FlowResult.Kind.NOTHING_PENDING -> {
                bannerUntil = now + 3000
                _ui.update { it.copy(banner = r.message, endRequired = false) }
            }
            FlowResult.Kind.NO_NAME -> {
                val biz = isBusiness(r.warehouse)
                val note = r.message + whNote(r.warehouse)
                val wh = r.warehouse.ifEmpty { null }
                _ui.update { it.copy(sent = it.sent + 1, endRequired = false, lastItemId = r.trackingId.ifEmpty { null }, lastItemCode = r.code) }
                entry(ScanEntry(r.code, wh, ScanEntry.Status.NO_NAME, note, now))
                card(ScanCard(ScanCard.Kind.NO_NAME, r.code, wh, note, business = biz))
                sound(if (biz) ScanSound.BUSINESS else ScanSound.NO_NAME)
            }
            FlowResult.Kind.DUPLICATE -> {
                _ui.update { it.copy(skipped = it.skipped + 1, endRequired = false) }
                card(ScanCard(ScanCard.Kind.DUPLICATE, r.code, message = tr("Mã đã quét trong phiên")))
                sound(ScanSound.DUPLICATE)
            }
            FlowResult.Kind.OFFLINE -> enqueue(Retry("ENDCODE", text, emptyList(), end = true))
            else -> {
                _ui.update { it.copy(skipped = it.skipped + 1) }
                card(ScanCard(ScanCard.Kind.SKIPPED, r.code, message = r.message))
                sound(ScanSound.REJECTED)
            }
        }
    }

    /** Mất mạng: giữ lại, tự gửi lại mỗi 4 giây, không mất kiện nào (lệnh quét an toàn khi gửi lại). */
    private fun enqueue(r: Retry) {
        if (retryList.isEmpty()) lastRetryAt = clock()
        retryList += r
        _ui.update { it.copy(queued = retryList.size) }
    }
}
