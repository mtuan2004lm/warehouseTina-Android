package com.example.tinawarehouse.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.data.CompanyApi
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.domain.session.TrackingFormat
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.state.DamageStore
import com.example.tinawarehouse.state.TrackingDetailStore
import com.example.tinawarehouse.ui.damage.DamageDialog
import com.example.tinawarehouse.ui.common.PhotoViewer
import com.example.tinawarehouse.ui.common.RemotePhoto
import com.example.tinawarehouse.ui.theme.Accent
import kotlinx.coroutines.launch

/** Chi tiết một kiện (chỉ đọc): mã, trạng thái, khớp đơn, ảnh label, báo cáo hư hỏng, số lượng, hàng, giữ hàng. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingDetailScreen(app: TinaApplication, store: TrackingDetailStore, onBack: () -> Unit) {
    val ui by store.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var damageForm by remember { mutableStateOf<DamageStore?>(null) }
    // Mở bảng báo hư hỏng (báo mới, hoặc sửa báo cáo đã có). Kiện đang giữ / đã trả người bán thì khoá quyết định.
    fun openDamage() {
        val t0 = ui.tracking
        damageForm = DamageStore(app.api, t0.id, t0.trackingNumber, existing = ui.damage,
            lockDecision = t0.status == "ON_HOLD" || t0.status == "RETURNED_TO_SELLER")
    }
    app.ui.lang
    LaunchedEffect(Unit) { store.refresh() }
    val t = ui.tracking

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("Chi tiết kiện"), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Quay lại")) } },
        )
    }) { pad ->
        PullToRefreshBox(isRefreshing = false, onRefresh = { scope.launch { store.refresh() } }, modifier = Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Section(tr("Kiện")) {
                        CodeLines("Tracking", listOf(t.trackingNumber))
                        if (t.additionalTrackingNumbers.isNotEmpty()) CodeLines(tr("Mã phụ"), t.additionalTrackingNumbers)
                        KV(tr("Trạng thái"), TrackingFormat.status(t.status))
                        Stacked(tr("Khớp đơn"), TrackingFormat.matchLine(t))
                        KV(tr("Khách"), t.customerCode ?: "—")
                        KV(tr("Vị trí kệ"), t.binLocation ?: "—")
                        KV(tr("Tình trạng"), TrackingFormat.condition(t.condition))
                        KV("Check-in", SessionFormat.timeOrDash(t.checkedInAt))
                        KV(tr("Ảnh label"), tr("{0} ảnh", t.labelPhotoCount))
                    }
                }
                val labels = t.labelPhotoUrls.map(::absoluteUrl)
                if (labels.isNotEmpty()) item {
                    Section(tr("Ảnh label đã chụp")) {
                        Thumbs(labels) { i -> viewer = labels to i }
                        Caption(tr("Chạm vào ảnh để xem lớn, chụm hai ngón để phóng to."))
                    }
                }
                if (ui.isDamaged) item {
                    Section(tr("Ảnh báo hư hỏng")) {
                        val d = ui.damage
                        when {
                            d != null -> {
                                d.reportedAt?.let { KV(tr("Đã báo lúc"), SessionFormat.timeOrDash(it)) }
                                KV(tr("Loại hư hỏng"), if (d.typeNames.isEmpty()) "—" else d.typeNames.joinToString(", "))
                                KV(tr("Quyết định"), TrackingFormat.decision(d.decision))
                                val urls = d.photoUrls.map(::absoluteUrl)
                                if (urls.isEmpty()) Caption(tr("Công ty chưa có ảnh hư hỏng cho kiện này."))
                                else Thumbs(urls) { i -> viewer = urls to i }
                                if (ui.canEditDamage) ActionButton(tr("Sửa báo cáo / đổi ảnh"), Icons.Default.Edit, ::openDamage)
                            }
                            ui.damageNote != null -> {
                                Caption(tr("Chưa lấy được báo cáo hư hỏng: {0}", ui.damageNote!!))
                                if (ui.canReportDamage) ActionButton(tr("Báo hư hỏng"), Icons.Default.Warning, ::openDamage)
                            }
                            else -> Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                        }
                    }
                }
                if (!ui.isDamaged && ui.canReportDamage) item {
                    Section(tr("Hư hỏng")) { ActionButton(tr("Báo hư hỏng"), Icons.Default.Warning, ::openDamage) }
                }
                item {
                    Section(tr("Số lượng")) {
                        KV(tr("Số dòng hàng"), TrackingFormat.qty(t.orderItemCount))
                        KV(tr("Khai báo"), TrackingFormat.qty(t.totalQuantity))
                        KV(tr("Thực nhận"), TrackingFormat.qty(t.totalActualQuantity))
                        KV(tr("Hư hỏng"), TrackingFormat.qty(t.totalDamagedQuantity))
                    }
                }
                if (t.orderItems.isNotEmpty()) item {
                    Section(tr("Hàng trong kiện")) {
                        t.orderItems.forEach { it2 ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(it2.itemName ?: it2.itemId ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Caption(tr("Đơn {0} · khai {1} · nhận {2} · hỏng {3}", it2.clientOrderId ?: "—",
                                    TrackingFormat.qty(it2.quantity), TrackingFormat.qty(it2.actualQuantity), TrackingFormat.qty(it2.damagedQuantity)))
                            }
                        }
                    }
                }
                if (t.holdCases.isNotEmpty()) item {
                    Section(tr("Giữ hàng")) {
                        t.holdCases.forEach { h ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(h.reasonName ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Caption(if (h.resolvedAt == null) tr("Chưa xử lý")
                                    else tr("Đã xử lý {0}", SessionFormat.timeOrDash(h.resolvedAt)) + (h.resolution?.let { " · $it" } ?: ""))
                            }
                        }
                    }
                }
                ui.error?.let { e -> item { Text(e, color = Accent) } }
            }
        }
    }
    viewer?.let { (urls, start) -> PhotoViewer(urls, start, onClose = { viewer = null }) }
    damageForm?.let { d -> DamageDialog(d, onClose = { damageForm = null; scope.launch { store.refresh() } }) }
}

/** Link ảnh bắt đầu bằng "/" thì ghép với địa chỉ máy chủ công ty. */
private fun absoluteUrl(u: String) = if (u.startsWith("/")) CompanyApi.SERVER_ORIGIN + u else u

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** Nhãn bên trái, giá trị bên phải (chọn/chép được). */
@Composable
private fun KV(k: String, v: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(12.dp))
    SelectionContainer(Modifier.weight(1f)) { Text(v, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }
}

/** Nhãn trên, giá trị dưới to hơn: đọc trọn tên hàng dài. */
@Composable
private fun Stacked(k: String, v: String) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    SelectionContainer { Text(v, fontWeight = FontWeight.SemiBold) }
}

/** Các mã tracking: nhãn trên, mỗi mã một dòng (chữ đơn cách, tự xuống dòng), giữ để chép. */
@Composable
private fun CodeLines(k: String, codes: List<String>) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    SelectionContainer { Column { codes.forEach { Text(it, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) } } }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = Accent, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun Caption(text: String) = Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Dải ảnh nhỏ 110×110; chạm để xem lớn. */
@Composable
private fun Thumbs(urls: List<String>, onOpen: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(urls) { i, u ->
            RemotePhoto(u, Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(i) }, fill = true, maxSide = 400)
        }
    }
}
