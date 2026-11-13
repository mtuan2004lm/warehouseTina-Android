package com.example.tinawarehouse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.sound.ScanSound
import com.example.tinawarehouse.sound.SoundChoices
import kotlinx.coroutines.launch

/** Cài đặt: tài khoản + trạm, giao diện, ngôn ngữ, âm thanh khi quét. Port từ SettingsView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: TinaApplication, onClose: () -> Unit) {
    val auth = app.auth
    val email by auth.userEmail.collectAsState()
    val ids by auth.availableStationIds.collectAsState()
    val station by app.app.station.collectAsState()
    val scope = rememberCoroutineScope()
    val changeable = ids.mapNotNull { StationProfile.find(it) }
    val lang = app.ui.lang
    val theme = app.ui.theme
    val sounds = remember { mutableStateMapOf<ScanSound, Int>().apply { ScanSound.entries.forEach { put(it, it.current(app.prefs)) } } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Cài đặt"), fontWeight = FontWeight.Bold) },
            actions = { TextButton(onClick = onClose) { Text(tr("Xong")) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tài khoản + trạm
            LabeledRow(tr("Tài khoản công ty"), email ?: "")
            if (changeable.size > 1) {
                var open by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Trạm"), Modifier.weight(1f))
                    Box {
                        OutlinedButton(onClick = { open = true }) {
                            Text(tr(station.title) + " · " + station.subtitle); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            changeable.forEach { p -> DropdownMenuItem(text = { Text(tr(p.title) + " · " + p.subtitle) }, onClick = { app.app.select(p); open = false }) }
                        }
                    }
                }
            } else {
                LabeledRow(tr("Trạm"), tr(station.title) + " · " + station.subtitle)
            }
            TextButton(onClick = { onClose(); scope.launch { auth.signOut() } }) { Text(tr("Đăng xuất"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            Footer(if (changeable.size > 1) tr("Đổi trạm sẽ áp dụng ngay khi đóng Cài đặt.") else tr("Muốn đổi trạm: đăng xuất rồi chọn trạm khác ở màn đăng nhập."))
            HorizontalDivider()

            // Giao diện
            Header(tr("Giao diện"))
            Segmented(listOf("system" to tr("Theo hệ thống"), "light" to tr("Sáng"), "dark" to tr("Tối")), theme) { app.ui.updateTheme(it) }
            HorizontalDivider()

            // Ngôn ngữ
            Header(tr("Ngôn ngữ"))
            Segmented(listOf("vi" to "Tiếng Việt", "en" to "English"), lang) { app.ui.updateLang(it) }
            HorizontalDivider()

            // Âm thanh
            Header(tr("Âm thanh khi quét"))
            ScanSound.entries.forEach { kind ->
                SoundRow(kind.label, sounds[kind] ?: kind.defaultId,
                    onPick = { id -> sounds[kind] = id; app.prefs.putInt(kind.key, id); app.sound.play(id) },
                    onPlay = { app.sound.play(sounds[kind] ?: kind.defaultId) })
            }
            TextButton(onClick = {
                ScanSound.resetAll(app.prefs); ScanSound.entries.forEach { sounds[it] = it.defaultId }
            }) { Text(tr("Khôi phục âm mặc định"), color = MaterialTheme.colorScheme.error) }
            Footer(tr("Chọn âm cho từng trạng thái. Bấm biểu tượng loa để nghe thử. Chọn \"Tắt\" để không phát âm."))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun Header(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
@Composable private fun Footer(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(selected = key == selected, onClick = { onSelect(key) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun SoundRow(title: String, current: Int, onPick: (Int) -> Unit, onPlay: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { open = true }) { Text(SoundChoices.label(current), maxLines = 1); Icon(Icons.Default.ArrowDropDown, null) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                SoundChoices.all.forEach { id -> DropdownMenuItem(text = { Text(SoundChoices.label(id)) }, onClick = { onPick(id); open = false }) }
            }
        }
        IconButton(onClick = onPlay, enabled = current != SoundChoices.OFF) { Icon(Icons.AutoMirrored.Filled.VolumeUp, tr("Nghe thử")) }
    }
}
