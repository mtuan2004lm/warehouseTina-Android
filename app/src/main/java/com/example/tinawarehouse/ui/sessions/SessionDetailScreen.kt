package com.example.tinawarehouse.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.data.CompanyTracking
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.domain.session.SessionFormat.CountLevel
import com.example.tinawarehouse.domain.session.TrackingFormat
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.sound.ScanSound
import com.example.tinawarehouse.state.FlowResult
import com.example.tinawarehouse.state.ScanFlow
import com.example.tinawarehouse.state.Feedback
import com.example.tinawarehouse.state.SessionDetailStore
import com.example.tinawarehouse.ui.common.CarrierLogo
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.DupRed
import com.example.tinawarehouse.ui.theme.OkGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Yellow = Color(0xFFEAB308)

/** Phát âm theo cài đặt cho một kết quả (dùng chung ở các màn có ghi/kiểm tra mã). */
fun TinaApplication.playFeedback(f: Feedback) = when (f) {
    Feedback.DUPLICATE -> sound.play(ScanSound.DUPLICATE)
    Feedback.PENDING -> sound.play(ScanSound.TING)
    Feedback.END_REQUIRED -> sound.play(ScanSound.BLOCKED)
    Feedback.WRITTEN -> sound.play(ScanSound.FOUND)
    Feedback.WRITTEN_BLOCK -> sound.play(ScanSound.BLOCK)
    Feedback.NO_NAME -> sound.play(ScanSound.NO_NAME)
    Feedback.ERROR -> sound.play(ScanSound.REJECTED)
    Feedback.CLOSED -> Unit
}

/** Chi tiết phiên: số đã quét, ô gõ/dán mã, tiếp tục quét, danh sách kiện, kết thúc phiên. Port từ CompanyTrackingsView. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionDetailScreen(
    app: TinaApplication, store: SessionDetailStore, carrierName: String,
    onBack: () -> Unit, onOpenTracking: (CompanyTracking) -> Unit, onScan: () -> Unit,
) {
    val ui by store.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var copiedId by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    app.ui.lang

    // Mỗi lần vào (hoặc quay lại từ màn quét / chi tiết kiện): tải lại số đã quét và danh sách kiện
    LaunchedEffect(Unit) { store.refreshAll() }

    fun copy(t: CompanyTracking) {
        clipboard.setText(AnnotatedString(t.trackingNumber))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copiedId = t.id
        scope.launch { delay(1500); if (copiedId == t.id) copiedId = null }
    }
    fun submit() = scope.launch { store.submit() }
    // Còn mã chờ END CODE thì hỏi trước khi thoát (mã chờ chỉ nằm trong bộ nhớ)
    fun back() { if (ui.pending.isNotEmpty()) confirmLeave = true else onBack() }
    BackHandler(enabled = ui.pending.isNotEmpty()) { confirmLeave = true }

    val s = ui.session
    val countColor = when (SessionFormat.countLevel(s)) { CountLevel.DONE -> OkGreen; CountLevel.MISSING -> Yellow; CountLevel.NEUTRAL -> MaterialTheme.colorScheme.onSurface }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(s.displayCode, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1) },
            navigationIcon = { IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại")) } },
        )
    }) { pad ->
        PullToRefreshBox(isRefreshing = ui.loading && ui.items.isNotEmpty(), onRefresh = { scope.launch { store.refreshAll() } }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Đã quét / hãng khai: to, canh giữa
                item {
                    Section {
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CarrierLogo(carrierName, s.carrier?.code, height = 34.dp)
                            Text("${s.actualScannedCount ?: 0} / " + (s.carrierReportedCount?.toString() ?: "—"),
                                fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = countColor, maxLines = 1)
                        }
                    }
                }

                // Ô gõ/dán mã: kiểm tra, rồi (phiên đang mở) ghi lên công ty như khi quét
                item {
                    Section {
                        OutlinedTextField(
                            ui.code, store::onCodeChange, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text(tr("Quét hoặc gõ mã rồi Enter")) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit() }),
                        )
                        if (ui.busy) Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                        ui.result?.let { ResultLine(it) }
                    }
                }

                if (ui.isOpen && ui.pending.isNotEmpty()) item {
                    PendingCard(ui.pending, busy = ui.busy, onRemove = store::removePending, onMain = store::makeMain,
                        onEndCode = { scope.launch { store.endCode() } })
                }

                if (ui.isOpen) item {
                    Section {
                        TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = Accent); Spacer(Modifier.width(8.dp))
                            Text(tr("Tiếp tục quét"), color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                    }
                }

                ui.error?.let { e -> item { Warn(e) } }

                itemsIndexed(ui.items, key = { _, t -> t.id }) { index, t ->
                    LaunchedEffect(t.id, ui.items.size) { if (index == ui.items.lastIndex) store.loadMore() }
                    TrackingRow(ui.rowNumber(index), t, copied = copiedId == t.id, onCopy = { copy(t) }, onClick = { onOpenTracking(t) })
                }
                if (ui.loading) item { Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator() } }

                // Kết thúc phiên: cuối màn hình, luôn hỏi xác nhận
                if (ui.isOpen) item {
                    OutlinedButton(onClick = { confirmClose = true }, enabled = !ui.closing, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp)); Text(tr("Kết thúc phiên"), fontWeight = FontWeight.SemiBold)
                        if (ui.closing) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text(tr("Còn {0} mã chưa END CODE", ui.pending.size)) },
        text = { Text(tr("Các mã đang chờ sẽ bị bỏ, chưa được ghi lên công ty. Vẫn thoát?")) },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(tr("Ở lại")) } },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(tr("Thoát"), color = MaterialTheme.colorScheme.error) } },
    )
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text(tr("Kết thúc phiên?")) },
        text = { Text(tr("Phiên {0} sẽ được đóng trên công ty, không quét thêm vào phiên này được. Đã quét {1} kiện.", s.displayCode, s.actualScannedCount ?: ui.items.size)) },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(tr("Huỷ")) } },
        confirmButton = {
            TextButton(onClick = {
                confirmClose = false
                scope.launch { if (store.closeSession()) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBack() } }
            }) { Text(tr("Kết thúc phiên"), color = MaterialTheme.colorScheme.error) }
        },
    )
    ui.closeError?.let { e ->
        AlertDialog(
            onDismissRequest = store::dismissCloseError,
            title = { Text(tr("Không đóng được phiên")) }, text = { Text(e) },
            confirmButton = { TextButton(onClick = store::dismissCloseError) { Text("OK") } },
        )
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Warn(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Default.Warning, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(text, color = Accent)
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = color); Spacer(Modifier.width(8.dp)); Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
}

/** Kết quả lần nhập mã gần nhất. */
@Composable
private fun ResultLine(r: FlowResult) {
    val (icon, color) = when (r.kind) {
        FlowResult.Kind.WRITTEN, FlowResult.Kind.NO_NAME, FlowResult.Kind.FOUND_ONLY ->
            (if (r.blocks.isEmpty()) Icons.Default.Verified else Icons.Default.Lock) to (if (r.blocks.isEmpty()) OkGreen else DupRed)
        FlowResult.Kind.PENDING, FlowResult.Kind.NOT_FOUND_ONLY -> Icons.AutoMirrored.Filled.HelpOutline to Accent
        FlowResult.Kind.END_REQUIRED, FlowResult.Kind.DUPLICATE, FlowResult.Kind.ALREADY_PENDING -> Icons.Default.Warning to DupRed
        FlowResult.Kind.NOTHING_PENDING, FlowResult.Kind.ERROR -> Icons.Default.Warning to Accent
    }
    if (r.code.isNotEmpty()) Text(r.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    if (r.message.isNotEmpty()) StatusLine(icon, r.message, color)
    if (r.blocks.isNotEmpty()) {
        StatusLine(Icons.Default.Lock, tr("ĐANG BLOCK"), DupRed)
        Text(r.blocks.joinToString("\n"), color = DupRed, fontSize = 14.sp)
    }
}

/**
 * Kiện đang chờ (các mã công ty chưa có thông tin), giống ô giữa trên web: mã đầu (mã chính) chữ to, mã phụ chữ nhỏ;
 * nút × để bỏ mã gõ nhầm, chạm mã phụ để chọn làm mã chính. Bên phải là nút END CODE "n / 3" (đủ 3 thì bắt buộc bấm).
 */
@Composable
private fun PendingCard(codes: List<String>, busy: Boolean, onRemove: (String) -> Unit, onMain: (String) -> Unit, onEndCode: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                codes.forEachIndexed { i, c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (i == 0) Color.Transparent else Yellow.copy(alpha = 0.15f))
                            .clickable(enabled = i > 0) { onMain(c) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = if (i == 0) 26.sp else 17.sp,
                            maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                        if (i > 0) Text("${i + 1}", color = Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                        IconButton(onClick = { onRemove(c) }, enabled = !busy, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, tr("Bỏ mã"), tint = DupRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            val full = codes.size >= ScanFlow.MAX_PENDING
            Column(
                Modifier.width(110.dp).fillMaxHeight().background(if (full) Accent else Accent.copy(alpha = 0.85f))
                    .clickable(enabled = !busy, onClick = onEndCode).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("${codes.size} / ${ScanFlow.MAX_PENDING}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("END CODE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

/** Một dòng kiện: số thứ tự, mã (nút sao chép kế bên; giữ lâu cũng sao chép), khách/kệ, trạng thái + giờ, nhãn. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackingRow(number: Int, t: CompanyTracking, copied: Boolean, onCopy: () -> Unit, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onCopy),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.trackingNumber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                        softWrap = false, modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, tr("Sao chép mã"),
                            tint = if (copied) OkGreen else Accent, modifier = Modifier.size(18.dp))
                    }
                }
                TrackingFormat.who(t).takeIf { it.isNotEmpty() }?.let { Small(it) }
                Small(TrackingFormat.status(t.status) + " · " + SessionFormat.timeOrDash(t.checkedInAt))
                if (t.isUnknown || t.condition == "DAMAGED") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.isUnknown) Tag(tr("Kiện không xác định"), Color(0xFFF97316).copy(alpha = 0.2f))
                    if (t.condition == "DAMAGED") Tag(tr("HƯ HỎNG"), DupRed.copy(alpha = 0.25f))
                }
            }
        }
    }
}

@Composable private fun Small(text: String) = Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)

@Composable
private fun Tag(text: String, bg: Color) =
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(bg, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
