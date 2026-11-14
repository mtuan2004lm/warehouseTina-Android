package com.example.tinawarehouse.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.tinawarehouse.i18n.tr

/**
 * Xem ảnh toàn màn hình: vuốt ngang đổi ảnh, chụm hai ngón để phóng to (tối đa 6 lần), kéo khi đang phóng to,
 * chạm đúp để về cỡ gốc, nút X để đóng. Port từ PhotoViewer/ZoomablePhoto (SessionsView.swift).
 */
@Composable
fun PhotoViewer(urls: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var zoomed by remember { mutableStateOf(false) }
        val pager = rememberPagerState(initialPage = start.coerceIn(0, maxOf(urls.size - 1, 0))) { urls.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { i ->
                ZoomablePhoto(urls[i], onZoomChange = { if (i == pager.currentPage) zoomed = it })
            }
            if (urls.size > 1) {
                Text("${pager.currentPage + 1} / ${urls.size}", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp))
            }
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).size(44.dp),
            ) { Icon(Icons.Default.Close, tr("Đóng")) }
        }
    }
}

@Composable
private fun ZoomablePhoto(url: String, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun reset() { scale = 1f; offset = Offset.Zero; onZoomChange(false) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(url) {
                // Chỉ xử lý khi chụm 2 ngón hoặc đang phóng to; vuốt 1 ngón lúc cỡ gốc để dành cho việc đổi ảnh.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            onZoomChange(scale > 1f)
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(url) { detectTapGestures(onDoubleTap = { reset() }) },
        contentAlignment = Alignment.Center,
    ) {
        RemotePhoto(url, Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        })
    }
}
