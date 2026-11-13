package com.example.tinawarehouse.ui.sessions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.state.CreateResult
import com.example.tinawarehouse.state.createCompanySession
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.OkGreen
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Tạo phiên nhận hàng trên công ty (GHI, không xoá được). Port từ CompanyNewSessionView. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(app: TinaApplication, onClose: () -> Unit) {
    val carriers = app.sessions.ui.collectAsState().value.carriers.entries.sortedBy { it.key }
    val station by app.app.station.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    app.ui.lang

    var carrierId by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var useArrival by remember { mutableStateOf(true) }          // bật = gửi "giờ xe đến" (mặc định giờ hiện tại); tắt = để trống như web
    var arrival by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    var count by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createdId by remember { mutableStateOf(0) }

    val zone = ZoneId.systemDefault()
    val canSubmit = !saving && !done && carrierId != 0 && driver.isNotBlank() && count.toIntOrNull() != null
    val fieldMod = Modifier.fillMaxWidth()

    fun clampArrival(t: LocalDateTime): LocalDateTime { val max = LocalDateTime.now().plusHours(1); return if (t.isAfter(max)) max.withSecond(0).withNano(0) else t }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Tạo phiên"), fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onClose) { Text(if (done) tr("Đóng") else tr("Huỷ")) } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = fieldMod.height(56.dp)) {
                    Text(if (carrierId == 0) tr("Chọn hãng") else (carriers.firstOrNull { it.key == carrierId }?.value ?: "#$carrierId"), Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    carriers.forEach { c -> DropdownMenuItem(text = { Text(c.value) }, onClick = { carrierId = c.key; menu = false }) }
                }
            }
            OutlinedTextField(gate, { gate = it }, label = { Text(tr("Cửa kho")) }, singleLine = true, modifier = fieldMod,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            OutlinedTextField(driver, { driver = it }, label = { Text(tr("Người giao")) }, singleLine = true, modifier = fieldMod)
            OutlinedTextField(plate, { plate = it }, label = { Text(tr("Biển số")) }, singleLine = true, modifier = fieldMod,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            OutlinedTextField(vehicle, { vehicle = it }, label = { Text(tr("Loại xe")) }, singleLine = true, modifier = fieldMod)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Ghi giờ xe đến"), Modifier.weight(1f)); Switch(useArrival, { useArrival = it })
            }
            if (useArrival) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val d = DatePickerDialog(ctx, { _, y, m, day -> arrival = clampArrival(arrival.withYear(y).withMonth(m + 1).withDayOfMonth(day)) },
                            arrival.year, arrival.monthValue - 1, arrival.dayOfMonth)
                        d.datePicker.maxDate = System.currentTimeMillis() + 3_600_000L
                        d.show()
                    }) { Text(arrival.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(ctx, { _, h, min -> arrival = clampArrival(arrival.withHour(h).withMinute(min)) }, arrival.hour, arrival.minute, true).show()
                    }) { Text(arrival.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
            }
            OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text(tr("Số kiện hãng khai")) }, singleLine = true, modifier = fieldMod,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            error?.let {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(it, color = Accent) }
            }
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = OkGreen); Spacer(Modifier.width(8.dp))
                    Text(tr("Đã tạo phiên #{0}", createdId), color = OkGreen, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(onClick = { confirm = true }, enabled = canSubmit, modifier = fieldMod.height(52.dp)) {
                    if (saving) { CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White); Spacer(Modifier.width(10.dp)) }
                    Text(if (saving) tr("Đang tạo…") else tr("Tạo phiên"), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr("Tạo phiên?")) },
        text = { Text(tr("Phiên mới sẽ được tạo trên hệ thống công ty.")) },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Huỷ")) } },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                if (!saving && !done) {
                    saving = true; error = null
                    scope.launch {
                        val r = createCompanySession(
                            app.api, station, carrierId, driver, count.toIntOrNull() ?: 0, gate, plate, vehicle,
                            arrival = if (useArrival) arrival.atZone(zone).toInstant() else null,
                        )
                        when (r) {
                            is CreateResult.Created -> { createdId = r.id; done = true }
                            is CreateResult.Failed -> error = r.message
                        }
                        saving = false
                    }
                }
            }) { Text(tr("Tạo")) }
        },
    )
}
