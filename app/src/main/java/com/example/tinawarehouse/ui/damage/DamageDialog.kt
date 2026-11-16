package com.example.tinawarehouse.ui.damage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.state.DamageStore
import com.example.tinawarehouse.ui.common.RemotePhoto
import com.example.tinawarehouse.ui.common.absolutePhotoUrl
import com.example.tinawarehouse.ui.common.rememberPhotoTaker
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.OkGreen
import kotlinx.coroutines.launch

/**
 * Báo hư hỏng của kiện (GHI thật lên công ty) — giống web: loại hư hỏng → ảnh (tối đa 10) → quyết định (cuối cùng).
 * Port từ CompanyDamageSheet (AutoScanView.swift). Toàn màn hình; [onClose] gọi khi đóng (dù đã gửi hay huỷ).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageDialog(store: DamageStore, onClose: () -> Unit) {
    val ui by store.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    val takePhoto = rememberPhotoTaker { store.addPhoto(it) }
    LaunchedEffect(Unit) { store.loadTypes() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(if (ui.done) tr("Đóng") else tr("Huỷ")) }
                    Text(if (ui.isEdit) tr("Sửa báo cáo hư hỏng") else tr("Báo hư hỏng"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(ui.code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                    // 1. Loại hư hỏng (chọn được nhiều)
                    Header(tr("Loại hư hỏng"))
                    if (ui.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    ui.types.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { t ->
                                val on = t.code in ui.selected
                                Row(
                                    Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (on) Accent.copy(alpha = 0.25f) else Color.Gray.copy(alpha = 0.15f))
                                        .border(1.5.dp, if (on) Accent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !ui.done) { store.toggle(t.code) }.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(6.dp))
                                    Text(t.name, fontSize = 14.sp, maxLines = 2)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    // 2. Ảnh hư hỏng (tối đa 10)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Header(tr("Ảnh hư hỏng"), Modifier.weight(1f))
                        OutlinedButton(onClick = takePhoto, enabled = ui.canAddPhoto && !ui.done) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Chụp ảnh"))
                        }
                    }
                    val old = ui.existing?.photoUrls.orEmpty().map(::absolutePhotoUrl)
                    if (old.isNotEmpty()) {
                        Note(tr("Ảnh hiện có trên công ty ({0})", old.size))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(old) { u ->
                                RemotePhoto(u, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).alpha(if (ui.photos.isEmpty()) 1f else 0.35f), fill = true, maxSide = 300)
                            }
                        }
                        if (ui.photos.isEmpty()) Note(tr("Muốn đổi ảnh: chụp ảnh mới bên dưới. Ảnh mới sẽ THAY TOÀN BỘ ảnh cũ."))
                        else Note(tr("Sẽ thay toàn bộ ảnh cũ bằng {0} ảnh mới.", ui.photos.size), Accent)
                    }
                    if (ui.photos.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ui.photos) { i, jpeg ->
                            val bmp = remember(jpeg) {
                                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                            }
                            Box {
                                if (bmp != null) Image(bmp, null, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                if (!ui.done) Icon(Icons.Default.Cancel, tr("Bỏ ảnh"), tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f)).clickable { store.removePhoto(i) })
                            }
                        }
                    }

                    // 3. Quyết định — luôn nằm cuối cùng
                    Header(tr("Quyết định"))
                    val options = listOf("RECEIVED" to tr("Nhận hàng"), "REJECTED" to tr("Từ chối"))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (key, label) ->
                            SegmentedButton(selected = ui.decision == key, onClick = { store.setDecision(key) }, enabled = !ui.decisionLocked && !ui.done,
                                shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
                        }
                    }
                    if (ui.isEdit && ui.decisionLocked) Note(tr("Không đổi được quyết định của kiện này (đang giữ hoặc đã từ chối)."))
                    if (ui.decision == "REJECTED" && ui.existing?.decision != "REJECTED")
                        Note(tr("Từ chối: kiện chuyển sang trả người bán. Kiện đã vào thùng thì công ty không cho từ chối."), Accent)

                    ui.error?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(it, color = Accent, fontSize = 13.sp)
                        }
                    }
                    if (ui.done) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = OkGreen); Spacer(Modifier.width(8.dp))
                            Text(if (ui.isEdit) tr("Đã cập nhật báo cáo hư hỏng") else tr("Đã gửi báo cáo hư hỏng"), color = OkGreen, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(onClick = { confirm = true }, enabled = ui.canSubmit, modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)) {
                            if (ui.saving) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(if (ui.saving) tr("Đang gửi…") else if (ui.isEdit) tr("Lưu thay đổi") else tr("Gửi báo cáo"), fontWeight = FontWeight.Bold)
                        }
                        if (ui.selected.isEmpty()) Note(tr("Chọn ít nhất một loại hư hỏng."))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(if (ui.isEdit) tr("Cập nhật báo cáo hư hỏng trên công ty?") else tr("Gửi báo cáo hư hỏng lên công ty?")) },
        text = { Text(ui.confirmMessage) },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Huỷ")) } },
        confirmButton = { TextButton(onClick = { confirm = false; scope.launch { store.send() } }) { Text(if (ui.isEdit) tr("Lưu") else tr("Gửi")) } },
    )
}

@Composable private fun Header(t: String, modifier: Modifier = Modifier) = Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = modifier)
@Composable private fun Note(t: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) = Text(t, fontSize = 13.sp, color = color)
