package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyDamageReport
import com.example.tinawarehouse.data.CompanyTracking
import com.example.tinawarehouse.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackingDetailUi(
    val tracking: CompanyTracking,
    val error: String? = null,
    val damage: CompanyDamageReport? = null,
    val damageNote: String? = null,       // lý do chưa lấy được báo cáo hư hỏng
) {
    /** Kiện đã trả người bán (hoặc đang giữ) thì công ty không nhận báo cáo mới (400). */
    val canReportDamage: Boolean get() = tracking.status != "ON_HOLD" && tracking.status != "RETURNED_TO_SELLER"
    val canEditDamage: Boolean get() = tracking.status != "RETURNED_TO_SELLER"
    val isDamaged: Boolean get() = tracking.condition == "DAMAGED"
}

/** Chi tiết một kiện (chỉ đọc) + báo cáo hư hỏng nếu có. Port từ CompanyTrackingDetailView. */
class TrackingDetailStore(private val api: CompanyApi, initial: CompanyTracking) {
    private val _ui = MutableStateFlow(TrackingDetailUi(initial))
    val ui: StateFlow<TrackingDetailUi> = _ui.asStateFlow()

    /** Danh sách chưa chắc kèm đủ hàng/hold; lấy bản chi tiết để bổ sung, rồi lấy báo cáo hư hỏng nếu kiện hư hỏng. */
    suspend fun refresh() {
        try {
            val t = api.tracking(_ui.value.tracking.id)
            _ui.update { it.copy(tracking = t, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.userMessage()) }
        }
        if (!_ui.value.isDamaged) return
        try {
            val d = api.damageReport(_ui.value.tracking.id)
            _ui.update { it.copy(damage = d, damageNote = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(damageNote = e.userMessage()) }
        }
    }
}
