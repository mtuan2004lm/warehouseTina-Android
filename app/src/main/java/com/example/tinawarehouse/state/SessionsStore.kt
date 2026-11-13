package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyError
import com.example.tinawarehouse.data.CompanySession
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.domain.station.StationProfile
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bộ lọc giống web: hãng · trạng thái · cửa · giờ xe đến. */
data class SessionFilters(
    val carrier: Int = 0,            // 0 = tất cả
    val status: String = "",         // "" = tất cả · OPEN · CLOSED
    val gate: String = "",           // "" = tất cả
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val active: Boolean get() = carrier != 0 || status.isNotEmpty() || gate.isNotEmpty() || from != null || to != null
}

data class SessionsUi(
    val sessions: List<CompanySession> = emptyList(),
    val error: String? = null,
    val loading: Boolean = false,
    val carriers: Map<Int, String> = emptyMap(),
    val knownGates: Set<String> = emptySet(),
    val filters: SessionFilters = SessionFilters(),
)

/**
 * Danh sách phiên + bộ lọc. Port từ SessionsView.swift (loadCompany).
 * Chỉ nhận kết quả của lần tải MỚI NHẤT (đổi nhiều bộ lọc cùng lúc sẽ gọi API nhiều lần).
 */
class SessionsStore(
    private val api: CompanyApi,
    private val station: () -> StationProfile,
    private val onUnauthorized: () -> Unit = {},
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val _ui = MutableStateFlow(SessionsUi())
    val ui: StateFlow<SessionsUi> = _ui.asStateFlow()
    private val seq = AtomicLong()

    /** Về trạng thái ban đầu (khi đăng xuất, để người dùng sau không thấy dữ liệu cũ). */
    fun reset() { seq.incrementAndGet(); _ui.value = SessionsUi() }

    fun setFilters(f: SessionFilters) = _ui.update { it.copy(filters = f) }
    fun updateFilters(block: (SessionFilters) -> SessionFilters) = _ui.update { it.copy(filters = block(it.filters)) }

    suspend fun load() {
        if (!api.isSignedIn) { _ui.update { it.copy(sessions = emptyList(), error = null) }; return }
        val mine = seq.incrementAndGet()
        _ui.update { it.copy(loading = true) }
        try {
            if (_ui.value.carriers.isEmpty()) {
                val cs = runCatching { api.carriers() }.getOrNull()
                if (cs != null) _ui.update { it.copy(carriers = cs.associate { c -> c.id to c.name }) }
            }
            val f = _ui.value.filters
            val z = zone()
            val from = f.from?.atStartOfDay(z)?.toInstant()                                          // 00:00 ngày bắt đầu
            val to = f.to?.plusDays(1)?.atStartOfDay(z)?.toInstant()?.minusSeconds(1)               // 23:59:59 ngày kết thúc
            val list = api.sessions(
                status = f.status.ifEmpty { null }, carrierId = if (f.carrier == 0) null else f.carrier,
                dockGate = f.gate.ifEmpty { null }, arrivedFrom = from, arrivedTo = to,
            )
            if (seq.get() != mine) return
            val profile = station()
            val gates = list.mapNotNull { it.dockGate }.filter { it.isNotEmpty() }
            _ui.update {
                it.copy(
                    knownGates = it.knownGates + gates,
                    // Chỉ phiên của trạm đang chọn (phiên không rõ trạm vẫn hiện để không bị giấu)
                    sessions = list.filter { s -> profile.matches(s.station) }.sortedByDescending { s -> s.id },
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (seq.get() != mine) return
            if (e is CompanyError && e.status == 401) { onUnauthorized(); return }
            _ui.update { it.copy(error = e.userMessage()) }
        } finally {
            if (seq.get() == mine) _ui.update { it.copy(loading = false) }
        }
    }
}
