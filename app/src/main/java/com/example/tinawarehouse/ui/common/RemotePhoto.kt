package com.example.tinawarehouse.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Tải ảnh từ link của công ty (ảnh label / ảnh hư hỏng), có bộ nhớ đệm.
 * Viết tay bằng OkHttp thay vì thêm thư viện Coil cho gọn. [maxSide] thu nhỏ ảnh khi giải mã để đỡ tốn bộ nhớ.
 */
object PhotoLoader {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(url: String, maxSide: Int): Bitmap? {
        val key = "$maxSide|$url"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }
}

/** Một ảnh từ link; vòng xoay khi đang tải, biểu tượng lỗi nếu không mở được. */
@Composable
fun RemotePhoto(url: String, modifier: Modifier = Modifier, fill: Boolean = false, maxSide: Int = 2048) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, maxSide) {
        val b = PhotoLoader.load(url, maxSide)
        if (b == null) failed = true else bmp = b
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit)
    } else {
        Box(modifier.background(Color.Gray.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (failed) Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
