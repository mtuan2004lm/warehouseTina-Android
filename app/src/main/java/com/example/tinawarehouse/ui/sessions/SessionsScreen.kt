package com.example.tinawarehouse.ui.sessions

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.data.CompanySession
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.domain.session.SessionFormat.CountLevel
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.ui.common.CarrierLogo
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.OkGreen
import java.time.LocalDate
import kotlinx.coroutines.launch

private val Yellow = Color(0xFFEAB308)

/** Danh sách phiên nhận hàng + bộ lọc (hãng · trạng thái · cửa · giờ xe đến). Port từ SessionsView.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: TinaApplication, onSettings: () -> Unit, onNew: () -> Unit, onOpen: (CompanySession) -> Unit) {
    val store = app.sessions
    val ui by store.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var showDates by remember { mutableStateOf(false) }
    app.ui.lang   // đọc state để đổi ngôn ngữ là vẽ lại

    // Mỗi lần vào màn này, hoặc đổi bộ lọc: tải lại danh sách
    LaunchedEffect(ui.filters) { store.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Phiên nhận hàng"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Cài đặt")) } },
                actions = {
                    IconButton(onClick = { scope.launch { store.load() } }) { Icon(Icons.Default.Refresh, tr("Tải lại")) }
                    IconButton(onClick = onNew) { Icon(Icons.Default.AddCircle, tr("Tạo phiên mới"), tint = Accent) }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(isRefreshing = ui.loading && ui.sessions.isNotEmpty(), onRefresh = { scope.launch { store.load() } }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item { FilterBar(app, onDates = { showDates = true }) }
                item {
                    when {
                        !app.api.isSignedIn -> Hint(tr("Chưa đăng nhập công ty."))
                        ui.error != null -> Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(ui.error!!, color = Accent)
                        }
                        ui.sessions.isEmpty() && !ui.loading -> Hint(tr("Không có phiên nào phù hợp."))
                        ui.sessions.isEmpty() && ui.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                items(ui.sessions, key = { it.id }) { s -> SessionCard(s, SessionFormat.carrierName(s, ui.carriers), onClick = { onOpen(s) }) }
            }
        }
    }

    if (showDates) DateRangeDialog(ui.filters.from, ui.filters.to,
        onChange = { f, t -> store.updateFilters { it.copy(from = f, to = t) } }, onDismiss = { showDates = false })
}

@Composable
private fun Hint(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionCard(s: CompanySession, carrier: String, onClick: () -> Unit) {
    val level = SessionFormat.countLevel(s)
    val countColor = when (level) { CountLevel.DONE -> OkGreen; CountLevel.MISSING -> Yellow; CountLevel.NEUTRAL -> MaterialTheme.colorScheme.onSurface }
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.displayCode, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    CarrierLogo(carrier, s.carrier?.code)
                }
                s.dockGate?.takeIf { it.isNotEmpty() }?.let { InfoRow(Icons.Outlined.DoorFront, it) }
                s.driverName?.takeIf { it.isNotEmpty() }?.let { InfoRow(Icons.Outlined.Person, it) }
                SessionFormat.vehicle(s).takeIf { it.isNotEmpty() }?.let { InfoRow(Icons.Outlined.LocalShipping, it) }
                SessionFormat.time(s.vehicleArrivedAt)?.let { InfoRow(Icons.Outlined.Schedule, it) }
                s.station?.code?.takeIf { it.isNotEmpty() }?.let { InfoRow(Icons.Outlined.Apartment, it) }
                SessionFormat.openedBy(s)?.takeIf { it.isNotEmpty() }?.let { InfoRow(Icons.Outlined.AccountCircle, tr("mở bởi ") + it) }
            }
            Spacer(Modifier.width(8.dp))
            Text(SessionFormat.count(s), color = countColor, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// Bộ lọc
// ---------------------------------------------------------------------------------------------

@Composable
private fun FilterBar(app: TinaApplication, onDates: () -> Unit) {
    val ui by app.sessions.ui.collectAsState()
    val f = ui.filters
    val all = tr("Tất cả")
    val gates = (ui.knownGates + f.gate).filter { it.isNotEmpty() }.sorted()
    val carriers = ui.carriers.entries.sortedBy { it.value.lowercase() }
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterDropdown(tr("Hãng"), if (f.carrier == 0) all else (ui.carriers[f.carrier] ?: "#${f.carrier}"), f.carrier != 0,
                listOf(all to { app.sessions.updateFilters { it.copy(carrier = 0) } }) + carriers.map { c -> c.value to { app.sessions.updateFilters { it.copy(carrier = c.key) } } })
        }
        item {
            FilterDropdown(tr("Trạng thái"), when (f.status) { "" -> all; "OPEN" -> tr("Đang mở"); else -> tr("Đã đóng") }, f.status.isNotEmpty(), listOf(
                all to { app.sessions.updateFilters { it.copy(status = "") } },
                tr("Đang mở") to { app.sessions.updateFilters { it.copy(status = "OPEN") } },
                tr("Đã đóng") to { app.sessions.updateFilters { it.copy(status = "CLOSED") } },
            ))
        }
        item {
            FilterDropdown(tr("Cửa"), f.gate.ifEmpty { all }, f.gate.isNotEmpty(),
                listOf(all to { app.sessions.updateFilters { it.copy(gate = "") } }) + gates.map { g -> g to { app.sessions.updateFilters { it.copy(gate = g) } } })
        }
        item {
            val v = when {
                f.from == null && f.to == null -> all
                f.to == null -> SessionFormat.dayMonth(f.from!!) + " →"
                f.from == null -> "→ " + SessionFormat.dayMonth(f.to!!)
                else -> SessionFormat.dayMonth(f.from!!) + " → " + SessionFormat.dayMonth(f.to!!)
            }
            Chip(tr("Giờ xe đến"), v, f.from != null || f.to != null, dropdown = false, onClick = onDates)
        }
        if (f.active) item {
            TextButton(onClick = { app.sessions.setFilters(com.example.tinawarehouse.state.SessionFilters()) }) {
                Text(tr("Xoá lọc"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FilterDropdown(title: String, value: String, active: Boolean, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip(title, value, active, dropdown = true) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, act) -> DropdownMenuItem(text = { Text(label) }, onClick = { act(); open = false }) }
        }
    }
}

@Composable
private fun Chip(title: String, value: String, active: Boolean, dropdown: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active, onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title + " ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        },
        trailingIcon = if (dropdown) ({ Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) }) else null,
    )
}

/** Khoảng ngày cho "Giờ xe đến" (từ ngày / đến ngày). */
@Composable
private fun DateRangeDialog(from: LocalDate?, to: LocalDate?, onChange: (LocalDate?, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    fun pick(initial: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(ctx, { _, y, m, d -> set(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Giờ xe đến")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRow(tr("Từ ngày"), from, onToggle = { on -> onChange(if (on) (from ?: LocalDate.now()) else null, to) }, onPick = { pick(from ?: LocalDate.now()) { d -> onChange(d, to) } })
                DateRow(tr("Đến ngày"), to, onToggle = { on -> onChange(from, if (on) (to ?: LocalDate.now()) else null) }, onPick = { pick(to ?: LocalDate.now()) { d -> onChange(from, d) } })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Xong")) } },
        dismissButton = { TextButton(onClick = { onChange(null, null) }) { Text(tr("Xoá lọc"), color = MaterialTheme.colorScheme.error) } },
    )
}

@Composable
private fun DateRow(label: String, value: LocalDate?, onToggle: (Boolean) -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (value != null) TextButton(onClick = onPick) { Text(value.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
        Switch(checked = value != null, onCheckedChange = onToggle)
    }
}
