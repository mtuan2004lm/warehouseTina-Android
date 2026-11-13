package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.KeyValueStore
import com.example.tinawarehouse.domain.station.RuleMatcher
import com.example.tinawarehouse.domain.station.StationProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Trạm đang dùng + bộ luật khớp theo trạm. Port từ AppStore.swift. */
class AppStore(private val prefs: KeyValueStore) {
    companion object { const val KEY_STATION = "app.station" }

    private val _station = MutableStateFlow(StationProfile.resolve(prefs.getString(KEY_STATION)))
    val station: StateFlow<StationProfile> = _station.asStateFlow()

    val matcher: RuleMatcher get() = RuleMatcher(_station.value.warehouseRules)
    fun select(profile: StationProfile) {
        prefs.putString(KEY_STATION, profile.id)
        _station.value = profile
    }
}
