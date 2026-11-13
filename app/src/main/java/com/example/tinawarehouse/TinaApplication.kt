package com.example.tinawarehouse

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.data.EncryptedTokenStore
import com.example.tinawarehouse.data.KeyValueStore
import com.example.tinawarehouse.data.OkHttpTransport
import com.example.tinawarehouse.data.SharedPrefsStore
import com.example.tinawarehouse.i18n.L10n
import com.example.tinawarehouse.state.AppStore
import com.example.tinawarehouse.sound.SoundPlayer
import com.example.tinawarehouse.state.AuthStore
import com.example.tinawarehouse.state.SessionsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Cài đặt giao diện: ngôn ngữ ("vi"|"en") và chủ đề ("system"|"light"|"dark"); là state của Compose nên đổi là vẽ lại. */
class UiPrefs(private val prefs: KeyValueStore) {
    var lang by mutableStateOf(prefs.getString("ui.lang") ?: "vi"); private set
    var theme by mutableStateOf(prefs.getString("ui.theme") ?: "system"); private set
    fun updateLang(v: String) { lang = v; prefs.putString("ui.lang", v) }
    fun updateTheme(v: String) { theme = v; prefs.putString("ui.theme", v) }
}

/** Nơi tạo và giữ các đối tượng dùng chung toàn app. */
class TinaApplication : Application() {
    lateinit var prefs: KeyValueStore; private set
    lateinit var ui: UiPrefs; private set
    lateinit var api: CompanyApi; private set
    lateinit var auth: AuthStore; private set
    lateinit var app: AppStore; private set
    lateinit var sessions: SessionsStore; private set
    lateinit var sound: SoundPlayer; private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        prefs = SharedPrefsStore(this)
        ui = UiPrefs(prefs)
        L10n.codeProvider = { ui.lang }          // đọc state của Compose → giao diện tự vẽ lại khi đổi ngôn ngữ
        api = CompanyApi(OkHttpTransport(), EncryptedTokenStore(this))
        auth = AuthStore(api, prefs)
        app = AppStore(prefs)
        sessions = SessionsStore(api, { app.station.value }, { scope.launch { auth.signOut() } })
        sound = SoundPlayer(this, prefs)
        scope.launch { api.sessionExpired.collect { auth.onSessionExpired() } }
    }
}
