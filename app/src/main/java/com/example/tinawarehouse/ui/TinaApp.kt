package com.example.tinawarehouse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.ui.login.LoginScreen
import com.example.tinawarehouse.state.SessionDetailStore
import com.example.tinawarehouse.state.TrackingDetailStore
import com.example.tinawarehouse.ui.sessions.NewSessionScreen
import com.example.tinawarehouse.ui.sessions.SessionDetailScreen
import com.example.tinawarehouse.ui.sessions.TrackingDetailScreen
import com.example.tinawarehouse.ui.sessions.playFeedback
import com.example.tinawarehouse.ui.sessions.SessionsScreen
import com.example.tinawarehouse.ui.settings.SettingsScreen
import com.example.tinawarehouse.ui.theme.TinaWarehouseTheme

/** Các màn trong ngăn xếp. Màn chi tiết giữ sẵn "store" để quay lại không mất dữ liệu đang xem. */
private sealed interface Screen {
    data object Sessions : Screen
    data object NewSession : Screen
    data object Settings : Screen
    class SessionDetail(val store: SessionDetailStore, val carrierName: String) : Screen
    class TrackingDetail(val store: TrackingDetailStore) : Screen
    class Scan(val title: String) : Screen
}

/** Gốc của app: chưa đăng nhập → màn đăng nhập (luôn tối); đã đăng nhập → danh sách phiên và các màn con. */
@Composable
fun TinaApp(app: TinaApplication) {
    val signedIn by app.auth.isSignedIn.collectAsState()
    if (!signedIn) {
        DisposableEffect(Unit) { app.sessions.reset(); onDispose { } }      // đăng xuất: không để người sau thấy dữ liệu cũ
        TinaWarehouseTheme(mode = app.ui.theme, forceDark = true) { LoginScreen(app) }
    } else {
        TinaWarehouseTheme(mode = app.ui.theme) { SignedInRoot(app) }
    }
}

@Composable
private fun SignedInRoot(app: TinaApplication) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Sessions) }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { pop() }
    when (val s = stack.last()) {
        Screen.Sessions -> SessionsScreen(app, onSettings = { push(Screen.Settings) }, onNew = { push(Screen.NewSession) }, onOpen = { session ->
            val carrier = SessionFormat.carrierName(session, app.sessions.ui.value.carriers)
            val store = SessionDetailStore(app.api, session, { app.app.station.value }, { app.playFeedback(it) })
            push(Screen.SessionDetail(store, carrier))
        })
        Screen.NewSession -> NewSessionScreen(app, onClose = ::pop)
        Screen.Settings -> SettingsScreen(app, onClose = ::pop)
        is Screen.SessionDetail -> SessionDetailScreen(
            app, s.store, s.carrierName, onBack = ::pop,
            onOpenTracking = { t -> push(Screen.TrackingDetail(TrackingDetailStore(app.api, t))) },
            onScan = { push(Screen.Scan(s.store.ui.value.session.displayCode)) },        // màn quét: Đợt 5
        )
        is Screen.TrackingDetail -> TrackingDetailScreen(app, s.store, onBack = ::pop)
        is Screen.Scan -> ComingSoon(s.title, onBack = ::pop)
    }
}

/** Màn tạm cho màn quét (Đợt 5 sẽ thay bằng màn thật). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoon(title: String, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại")) } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Sắp có"))
        }
    }
}
