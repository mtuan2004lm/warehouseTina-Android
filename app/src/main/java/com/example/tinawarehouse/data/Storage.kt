package com.example.tinawarehouse.data

/**
 * Chỗ lưu cài đặt nhỏ (ngôn ngữ, giao diện, trạm đã chọn, tên đăng nhập…). Giống `UserDefaults` của iOS.
 * Bản chạy thật dùng SharedPreferences (xem `SharedPrefsStore`); test dùng [InMemoryKeyValueStore].
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int?)
    fun getStringList(key: String): List<String>?
    fun putStringList(key: String, value: List<String>?)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String) = map[key] as? String
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun getInt(key: String) = map[key] as? Int
    override fun putInt(key: String, value: Int?) { if (value == null) map.remove(key) else map[key] = value }

    @Suppress("UNCHECKED_CAST")
    override fun getStringList(key: String) = map[key] as? List<String>
    override fun putStringList(key: String, value: List<String>?) { if (value == null) map.remove(key) else map[key] = value }
}

/**
 * Chỗ lưu token đăng nhập. Chạy thật: mã hóa bằng Android Keystore (xem `EncryptedTokenStore`), giống Keychain của iOS.
 * Mật khẩu không bao giờ được lưu.
 */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun save(accessToken: String, refreshToken: String)
    fun clear()
}

class InMemoryTokenStore(access: String? = null, refresh: String? = null) : TokenStore {
    @Volatile private var access: String? = access
    @Volatile private var refresh: String? = refresh
    override fun accessToken() = access
    override fun refreshToken() = refresh
    override fun save(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
    override fun clear() { access = null; refresh = null }
}
