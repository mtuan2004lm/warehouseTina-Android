package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.tr
import java.time.Instant

/** Kết quả tạo phiên: thành công (id phiên) hoặc câu lỗi để hiện cho người dùng. */
sealed class CreateResult {
    data class Created(val id: Int) : CreateResult()
    data class Failed(val message: String) : CreateResult()
}

/**
 * Tạo phiên ở TRẠM ĐÃ CHỌN. Trạm khả dụng: trạm được gán, hoặc toàn bộ trạm nếu là ADMIN.
 * Port từ CompanyNewSessionView.create() (SessionsView.swift).
 */
suspend fun createCompanySession(
    api: CompanyApi, profile: StationProfile, carrierId: Int, driver: String, count: Int,
    gate: String = "", plate: String = "", vehicle: String = "", arrival: Instant? = null,
): CreateResult = try {
    val me = api.me()
    val allowed = runCatching { api.allowedStations(me) }.getOrDefault(me.stations)
    val station = allowed.firstOrNull { profile.matches(it) } ?: if (allowed.size == 1) allowed.first() else null
    if (station == null) {
        CreateResult.Failed(if (allowed.isEmpty()) tr("Tài khoản không có trạm nào") else tr("Tài khoản không có {0}", tr(profile.title)))
    } else {
        val id = api.createSession(
            stationId = station.id, carrierId = carrierId, driverName = driver.trim(), reportedCount = count,
            dockGate = gate.trim(), licensePlate = plate.trim(), vehicleType = vehicle.trim(), vehicleArrivedAt = arrival,
        )
        CreateResult.Created(id)
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    CreateResult.Failed(e.userMessage())
}
