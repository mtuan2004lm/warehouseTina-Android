package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.CompanyError
import com.example.tinawarehouse.data.KeyValueStore
import com.example.tinawarehouse.data.userMessage
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Trạng thái đăng nhập. Port từ AuthStore.swift. Chỉ nhớ tên đăng nhập, không lưu mật khẩu. */
class AuthStore(private val api: CompanyApi, private val prefs: KeyValueStore) {
    companion object {
        const val KEY_USERNAME = "auth.username"
        const val KEY_STATIONS = "auth.availableStations"
        const val KEY_EMAIL = "auth.userEmail"
    }

    private val _isSignedIn = MutableStateFlow(api.isSignedIn)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()
    private val _userEmail = MutableStateFlow(prefs.getString(KEY_EMAIL))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _availableStationIds = MutableStateFlow(prefs.getStringList(KEY_STATIONS) ?: emptyList())
    val availableStationIds: StateFlow<List<String>> = _availableStationIds.asStateFlow()

    val rememberedUsername: String get() = prefs.getString(KEY_USERNAME) ?: ""

    /** Gọi khi API báo hết phiên. */
    fun onSessionExpired() {
        _isSignedIn.value = false
        _errorMessage.value = tr("Hết phiên đăng nhập — vui lòng đăng nhập lại")
    }

    suspend fun signIn(username: String, password: String, station: StationProfile) {
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) return
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val me = api.login(user, password)
            val allowed = api.allowedStations(me)
            val ok = allowed.any { station.matches(it) }
            if (!ok) {
                api.logout()
                _errorMessage.value = if (allowed.isEmpty()) tr("Tài khoản không có trạm nào")
                else tr("Tài khoản không có {0}", station.title)
                return
            }
            val ids = StationProfile.all.filter { p -> allowed.any { p.matches(it) } }.map { it.id }
            prefs.putString(KEY_USERNAME, user)
            prefs.putString(KEY_EMAIL, me.username)
            prefs.putStringList(KEY_STATIONS, ids)
            _availableStationIds.value = ids
            _userEmail.value = me.username
            _isSignedIn.value = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val ce = e as? CompanyError
            _errorMessage.value = if (ce != null && ce.status in listOf(400, 401, 403) && ce.code != "SESSION_EXPIRED")
                tr("Sai tên đăng nhập hoặc mật khẩu") else e.userMessage()
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun signOut() {
        runCatching { api.logout() }
        _isSignedIn.value = false
        _userEmail.value = null
        prefs.putString(KEY_EMAIL, null)
    }
}
