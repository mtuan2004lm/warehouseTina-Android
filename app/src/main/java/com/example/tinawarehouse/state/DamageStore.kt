package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyDamageReport
import com.example.tinawarehouse.data.CompanyDamageType
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DamageUi(
    val code: String,
    val existing: CompanyDamageReport?,        // có = đang SỬA báo cáo đã gửi (PATCH); null = báo mới (POST)
    val lockDecision: Boolean,                 // kiện đang giữ / đã trả người bán: không đổi được quyết định
    val types: List<CompanyDamageType> = emptyList(),
    val selected: Set<String> = emptySet(),
    val photos: List<ByteArray> = emptyList(), // ảnh JPEG mới chụp (đã thu nhỏ), tối đa 10
    val decision: String = "RECEIVED",         // RECEIVED = Nhận hàng · REJECTED = Từ chối
    val loading: Boolean = true,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    val isEdit: Boolean get() = existing != null
    val decisionChanged: Boolean get() = existing != null && decision != (existing.decision ?: "RECEIVED")
    val hasChanges: Boolean get() = existing == null || selected != existing.typeCodes.toSet() || decisionChanged || photos.isNotEmpty()
    val canSubmit: Boolean get() = !saving && !done && selected.isNotEmpty() && hasChanges
    val canAddPhoto: Boolean get() = photos.size < DamageStore.MAX_PHOTOS
    /** Không đổi được quyết định: kiện bị khoá, hoặc báo cáo cũ đã Từ chối (không đảo lại được). */
    val decisionLocked: Boolean get() = lockDecision || existing?.decision == "REJECTED"

    /** Câu trong hộp xác nhận trước khi gửi. */
    val confirmMessage: String
        get() = when {
            existing == null -> tr("Mỗi kiện chỉ có một báo cáo. Gửi xong vẫn sửa được trong Chi tiết kiện.")
            decisionChanged && decision == "REJECTED" -> tr("Từ chối sẽ chuyển kiện sang trả người bán và KHÔNG đảo lại được.")
            photos.isEmpty() -> tr("Cập nhật loại hư hỏng/quyết định, giữ nguyên ảnh cũ.")
            else -> tr("Ảnh mới sẽ thay toàn bộ ảnh cũ trên công ty.")
        }
}

/**
 * Báo hư hỏng của một kiện (GHI thật lên công ty), giống web: loại hư hỏng → ảnh → quyết định.
 * Port từ CompanyDamageSheet (AutoScanView.swift).
 */
class DamageStore(
    private val api: CompanyApi, private val trackingId: String, code: String,
    existing: CompanyDamageReport? = null, lockDecision: Boolean = false,
) {
    companion object { const val MAX_PHOTOS = 10 }

    private val _ui = MutableStateFlow(
        DamageUi(code, existing, lockDecision,
            selected = existing?.typeCodes?.toSet() ?: emptySet(), decision = existing?.decision ?: "RECEIVED")
    )
    val ui: StateFlow<DamageUi> = _ui.asStateFlow()

    suspend fun loadTypes() {
        try {
            val t = api.damageTypes().sortedBy { it.name }
            _ui.update { it.copy(types = t, error = null, loading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { it.copy(error = e.userMessage(), loading = false) }
        }
    }

    fun toggle(code: String) = _ui.update { it.copy(selected = if (code in it.selected) it.selected - code else it.selected + code) }
    fun addPhoto(jpeg: ByteArray) = _ui.update { if (it.canAddPhoto) it.copy(photos = it.photos + jpeg) else it }
    fun removePhoto(index: Int) = _ui.update { it.copy(photos = it.photos.filterIndexed { i, _ -> i != index }) }
    fun setDecision(d: String) = _ui.update { if (it.decisionLocked) it else it.copy(decision = d) }

    /** GHI lên công ty: báo mới (POST) hoặc sửa (PATCH — chỉ gửi quyết định khi đổi; ảnh mới thay toàn bộ ảnh cũ). */
    suspend fun send(): Boolean {
        val u = _ui.value
        if (!u.canSubmit) return false
        _ui.update { it.copy(saving = true, error = null) }
        val codes = u.types.map { it.code }.filter { it in u.selected }
        return try {
            if (u.existing == null) api.reportDamage(trackingId, codes, u.decision, u.photos)
            else api.updateDamageReport(trackingId, codes, if (u.decisionChanged) u.decision else null, u.photos)
            _ui.update { it.copy(saving = false, done = true) }
            true
        } catch (e: CancellationException) {
            _ui.update { it.copy(saving = false) }; throw e
        } catch (e: Exception) {
            _ui.update { it.copy(saving = false, error = e.userMessage()) }
            false
        }
    }
}

/** Gửi ảnh label của kiện vừa quét; trả về câu thông báo để hiện trên màn quét. */
suspend fun uploadLabelPhoto(api: CompanyApi, trackingId: String, code: String, jpeg: ByteArray): String = try {
    api.uploadLabelPhoto(trackingId, jpeg)
    tr("Đã gửi ảnh label cho {0}", code)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    tr("Gửi ảnh label thất bại: {0}", e.userMessage())
}
