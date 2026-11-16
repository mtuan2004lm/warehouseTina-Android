package com.example.tinawarehouse.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.camera.ScanCamera
import com.example.tinawarehouse.domain.station.RuleMatcher
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.sound.ScanSound
import com.example.tinawarehouse.state.ScanCard
import com.example.tinawarehouse.state.ScanController
import com.example.tinawarehouse.state.ScanEntry
import com.example.tinawarehouse.state.ScanFlow
import com.example.tinawarehouse.state.SessionDetailStore
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.DupRed
import com.example.tinawarehouse.ui.theme.Navy
import com.example.tinawarehouse.ui.theme.OkGreen
import com.example.tinawarehouse.ui.theme.UnknownGray
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Purple = Color(0xFF8B5CF6)
private val Orange = Color(0xFFF59E0B)
private val Yellow = Color(0xFFFACC15)

/**
 * Màn quét camera: đưa tem vào khung một lần, app tự lấy mã vạch + mã kho rồi ghi lên công ty.
 * Port từ AutoScanView.swift. Luôn tối. Dùng chung danh sách mã chờ END CODE với ô gõ tay của chi tiết phiên.
 * Ảnh label / báo hư hỏng: Đợt 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(app: TinaApplication, detail: SessionDetailStore, carrierName: String, onBack: () -> Unit, onSessionClosed: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val station by app.app.station.collectAsState()
    app.ui.lang

    val controller = remember {
        val m = RuleMatcher(station.warehouseRules)
        ScanController(
            detail.flow,
            matcher = { lines -> m.firstMatch(lines, station) },
            isBusiness = { it.isNotEmpty() && m.match(it)?.isBusiness == true },
            scope = scope,
            sound = { s ->
                app.sound.play(s)
                haptic.performHapticFeedback(if (s == ScanSound.FOUND) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
            },
        )
    }
    val ui by controller.ui.collectAsState()
    val pending by controller.pending.collectAsState()
    val session by detail.ui.collectAsState()
    val camera = remember { ScanCamera(context) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var paused by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }       // bảng chọn mã chính / lịch sử: nhả camera
    var torch by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var showPick by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }
    // Camera chạy khi được phép, không dừng và không mở bảng chọn
    val running = granted && !paused && !sheetOpen
    DisposableEffect(running) {
        if (running) {
            camera.onBarcodes = controller::onBarcodes
            camera.onLines = controller::onLines
            camera.start(owner, previewView)
        } else camera.stop()
        onDispose { }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true                     // giữ màn hình luôn sáng khi quét
        onDispose { view.keepScreenOn = false; camera.release() }
    }
    LaunchedEffect(Unit) { while (true) { controller.tick(); delay(50) } }
    LaunchedEffect(torch, running) { if (running) { delay(300); camera.setTorch(torch) } }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SentCounter(ui.sent)
            Box(Modifier.fillMaxWidth().height(200.dp)) { ResultCard(ui) }
            // Khung quét co giãn + các dải thông báo
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Reticle(active = ui.reading, progress = ui.progress, modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp))
                Column(Modifier.fillMaxWidth().padding(top = 10.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ui.queued > 0) Pill(tr("Mất kết nối, đang tự gửi lại ({0} nhãn chờ)", ui.queued), Accent.copy(alpha = 0.9f), Icons.Default.Sync)
                    ui.banner?.let { Pill(it, Navy.copy(alpha = 0.9f)) }
                    ui.hint?.let { Pill(it, Color.Black.copy(alpha = 0.65f)) }
                }
            }
            if (pending.isNotEmpty()) {
                // Nút END CODE: to, dễ bấm bằng ngón cái
                val full = pending.size >= ScanFlow.MAX_PENDING
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (full) DupRed else Accent).clickable { controller.endCode() }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text("END CODE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (full) tr("Bắt buộc bấm để chốt (3/3)") else tr("Bấm để chốt kiện ({0}/3)", pending.size), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(Navy.copy(alpha = 0.9f)).clickable { showPick = true; sheetOpen = true },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                    Text(tr("Chọn mã chính ({0} mã không có trong đơn)", pending.size), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            // Hàng dưới: quay lại · BỎ QUA · menu
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại")) { onBack() }
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(34.dp).clip(CircleShape).background(Navy.copy(alpha = 0.9f)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("BỎ QUA"), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("${ui.skipped}", color = UnknownGray, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Box {
                    RoundButton(Icons.Default.MoreHoriz, tr("Dừng quét")) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Dừng quét")) }, onClick = { menu = false; confirmStop = true })
                        DropdownMenuItem(text = { Text(tr("Kết thúc phiên"), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmClose = true })
                    }
                }
            }
            // Nút chức năng: đèn · lịch sử (ảnh label / hư hỏng: Đợt 6)
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                RoundButton(if (torch) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, tr("Đèn"), size = 48) { torch = !torch }
                RoundButton(Icons.AutoMirrored.Filled.List, tr("Lịch sử quét"), size = 48) { showHistory = true }
            }
        }

        // Đủ 3 tracking không có thông tin → bắt buộc bấm END CODE (không che nút END CODE)
        if (ui.endRequired) {
            Column(
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 70.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(18.dp)).background(DupRed.copy(alpha = 0.95f)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Warning, null, tint = Yellow, modifier = Modifier.size(34.dp))
                Text(tr("BẮT BUỘC BẤM END CODE"), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(tr("Tem này đã quét đủ 3 tracking nhưng không có thông tin. Bấm nút END CODE bên dưới trước khi quét tem khác."),
                    color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }

        // Kiện BLOCK: che camera, báo to, bấm "Quét tiếp tục" mới quét tiếp
        ui.blockCard?.let { r ->
            Cover {
                Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(50.dp))
                Text("BLOCK", color = DupRed, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text(tr("Kiện đang bị block — chưa nhận hàng"), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Code(r.code, 20.sp)
                r.warehouse?.let { Text(it, color = Color.White.copy(alpha = 0.9f), fontFamily = FontFamily.Monospace, maxLines = 1) }
                r.message?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3) }
                Button(onClick = controller::continueAfterBlock, modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text(tr("Quét tiếp tục"), fontWeight = FontWeight.Bold) }
            }
        }

        // Đã dừng quét: che camera, chỉ còn Tiếp tục / Kết thúc / Quay lại
        if (paused) Cover {
            Icon(Icons.Default.PauseCircle, null, tint = Accent, modifier = Modifier.size(56.dp))
            Text(tr("ĐÃ DỪNG QUÉT"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(tr("Đã quét {0} kiện", ui.sent), color = OkGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { paused = false }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text(tr("Tiếp tục quét"), fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { confirmClose = true }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DupRed)) { Text(tr("Kết thúc phiên"), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onBack) { Text(tr("Quay lại danh sách phiên"), color = Color.White.copy(alpha = 0.7f)) }
        }

        // Chưa có quyền camera
        if (!granted && asked) Cover {
            Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(44.dp))
            Text(tr("Cần quyền camera để quét"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text(tr("Mở Cài đặt")) }
            TextButton(onClick = onBack) { Text(tr("Quay lại"), color = Color.White.copy(alpha = 0.7f)) }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(tr("Dừng quét?")) },
        text = { Text(tr("Đã quét {0} kiện. Phiên trên công ty vẫn mở.", ui.sent)) },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(tr("Huỷ")) } },
        confirmButton = { TextButton(onClick = { confirmStop = false; paused = true; torch = false }) { Text(tr("Dừng quét")) } },
    )
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text(tr("Kết thúc phiên?")) },
        text = { Text(tr("Phiên {0} sẽ được đóng trên công ty, không quét thêm vào phiên này được. Đã quét {1} kiện.", session.session.displayCode, ui.sent)) },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(tr("Huỷ")) } },
        confirmButton = {
            TextButton(onClick = {
                confirmClose = false
                scope.launch { if (detail.closeSession()) { camera.stop(); onSessionClosed() } }
            }) { Text(tr("Kết thúc phiên"), color = MaterialTheme.colorScheme.error) }
        },
    )
    session.closeError?.let { e ->
        AlertDialog(onDismissRequest = detail::dismissCloseError, title = { Text(tr("Không đóng được phiên")) }, text = { Text(e) },
            confirmButton = { TextButton(onClick = detail::dismissCloseError) { Text("OK") } })
    }
    if (showPick) PickMainSheet(pending,
        onPick = { c -> controller.chooseMain(c); showPick = false; sheetOpen = false },
        onDismiss = { showPick = false; sheetOpen = false })
    if (showHistory) ModalBottomSheet(onDismissRequest = { showHistory = false }) { History(ui.entries) }
}

@Composable
private fun SentCounter(sent: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Navy.copy(alpha = 0.88f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("ĐÃ GỬI"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("$sent", color = OkGreen, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

/** Thẻ kết quả lớn (giống `resultCard` của iOS). */
@Composable
private fun ResultCard(ui: com.example.tinawarehouse.state.ScanUi) {
    if (ui.reading) {
        Card(Navy.copy(alpha = 0.92f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                Text(tr("Đang đọc nhãn…"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
        return
    }
    val r = ui.last ?: return
    when (r.kind) {
        ScanCard.Kind.TING -> Card(Accent) {
            Big("TING"); Sub(tr("Tracking {0}/3 chưa có thông tin", r.track)); Code(r.code); r.message?.let { Small(it) }
        }
        ScanCard.Kind.NO_WAREHOUSE -> Card(Orange) {
            Big(tr("THIẾU MÃ KHO"), 30.sp); Sub(r.message ?: tr("Đưa tem vào lại")); Code(r.code)
        }
        ScanCard.Kind.NO_NAME -> Card(Purple) {
            if (r.business) BusinessBanner(r.warehouse)
            Big("NO NAME"); Sub(tr("Cả 3 tracking không có thông tin")); Code(r.code); Small(tr("Nên chụp ảnh label để nhân viên VN kiểm tra"))
        }
        ScanCard.Kind.END_BLOCKED -> Card(DupRed) { Big(tr("BẤM END CODE"), 30.sp); Sub(r.message ?: tr("Bắt buộc bấm nút END CODE")) }
        ScanCard.Kind.DUPLICATE -> Card(Accent) { Big(tr("MÃ TRÙNG")); Sub(tr("Nhãn này đã quét trong phiên")); Code(r.code) }
        ScanCard.Kind.BLOCKED -> Card(DupRed) {
            Big("BLOCK"); Sub(tr("Kiện đang bị block — chưa nhận hàng")); Code(r.code); r.message?.let { Small(it, 3) }
        }
        ScanCard.Kind.SUCCESS -> Card(OkGreen) {
            if (r.business) BusinessBanner(r.warehouse)
            Text("SUCCESS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Code(r.code)
            if (!r.business) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("MÃ KHO"), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
                Text(r.warehouse ?: "—", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            r.message?.let { Small(it, 2) }
        }
        ScanCard.Kind.SKIPPED -> Card(UnknownGray) {
            Text(tr("BỎ QUA"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp); Code(r.code); r.message?.let { Small(it, 2) }
        }
    }
}

@Composable
private fun Card(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), content = content,
    )
}

@Composable private fun Big(t: String, size: androidx.compose.ui.unit.TextUnit = 34.sp) = Text(t, color = Color.White, fontSize = size, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
@Composable private fun Sub(t: String) = Text(t, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
@Composable private fun Small(t: String, lines: Int = 2) = Text(t, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = lines, overflow = TextOverflow.Ellipsis)

/** Mã dài: một dòng; quá 40 ký tự thì cắt phần giữa. */
@Composable
private fun Code(c: String, size: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val s = if (c.length > 40) c.take(19) + "…" + c.takeLast(18) else c
    Text(s, color = Color.White.copy(alpha = 0.92f), fontFamily = FontFamily.Monospace, fontSize = size, maxLines = 1)
}

/** Dải vàng "HÀNG KINH DOANH" (mã kho nhóm FADO.VN: DOFA…). */
@Composable
private fun BusinessBanner(wh: String?) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Yellow).padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color.Black, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(tr("HÀNG KINH DOANH"), color = Color.Black, fontWeight = FontWeight.ExtraBold)
        }
        if (!wh.isNullOrEmpty()) Text(wh, color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(t: String, bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(t, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Int = 46, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size.dp).clip(CircleShape).background(Navy.copy(alpha = 0.9f))) {
        Icon(icon, desc, tint = Color.White)
    }
}

/** Màn che toàn màn hình (dừng quét / BLOCK / thiếu quyền). */
@Composable
private fun Cover(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Khung quét: viền trắng nét đứt bo góc; đang đọc nhãn thì vẽ vòng cam chạy theo tiến độ cửa sổ đọc. */
@Composable
private fun Reticle(active: Boolean, progress: Float, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = 24.dp.toPx()
        val stroke = 3.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.85f), cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
        if (active && progress > 0f) {
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
            val pm = PathMeasure().apply { setPath(path, false) }
            val seg = Path()
            pm.getSegment(0f, pm.length * progress, seg, true)
            drawPath(seg, Accent, style = Stroke(width = stroke * 2, cap = StrokeCap.Round))
        }
    }
}

/** Chọn MỘT mã đang chờ làm mã chính; các mã còn lại thành mã phụ của cùng một kiện NO NAME. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickMainSheet(codes: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Chọn mã chính"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(tr("Các mã đã quét nhưng công ty không có trong danh sách đơn hàng"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            codes.forEach { c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { chosen = c }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chosen == c) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (chosen == c) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(c, fontFamily = FontFamily.Monospace)
                }
            }
            Text(tr("Tick một mã làm mã chính. Các mã còn lại sẽ thành mã phụ của cùng một kiện NO NAME. Kiện ghi lên công ty và không xoá được."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { chosen?.let(onPick) }, enabled = chosen != null, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) { Text(tr("Chốt kiện"), fontWeight = FontWeight.Bold) }
        }
    }
}

/** Lịch sử quét của lần quét này (mới nhất ở trên). */
@Composable
private fun History(entries: List<ScanEntry>) {
    val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(tr("Lịch sử quét"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (entries.isEmpty()) Text(tr("Chưa quét nhãn nào."), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(entries) { e ->
                val (label, color) = when (e.status) {
                    ScanEntry.Status.SENT -> "SUCCESS" to OkGreen
                    ScanEntry.Status.NO_NAME -> "NO NAME" to Purple
                    ScanEntry.Status.TRACKED -> "TING" to Accent
                    ScanEntry.Status.DUPLICATE -> tr("MÃ TRÙNG") to Accent
                    ScanEntry.Status.REJECTED -> tr("BỎ QUA") to UnknownGray
                    ScanEntry.Status.NO_WAREHOUSE -> tr("THIẾU MÃ KHO") to Orange
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(e.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt.format(java.util.Date(e.time)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    e.note?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
