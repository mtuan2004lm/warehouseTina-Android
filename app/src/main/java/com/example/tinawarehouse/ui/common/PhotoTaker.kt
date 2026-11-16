package com.example.tinawarehouse.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.tinawarehouse.data.CompanyApi
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Link ảnh bắt đầu bằng "/" thì ghép với địa chỉ máy chủ công ty. */
fun absolutePhotoUrl(u: String): String = if (u.startsWith("/")) CompanyApi.SERVER_ORIGIN + u else u

/**
 * Chụp một ảnh bằng app camera của máy (giống PhotoPicker của iOS). Trả về hàm để gọi khi bấm nút chụp;
 * ảnh chụp xong được xoay đúng chiều, thu nhỏ cạnh dài ≤ [maxSide] và nén JPEG rồi đưa vào [onJpeg].
 * Tự xin quyền camera nếu chưa có (app khai báo quyền CAMERA nên phải có quyền mới mở được camera của máy).
 */
@Composable
fun rememberPhotoTaker(maxSide: Int = 1600, quality: Int = 70, onJpeg: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onJpeg)
    var path by rememberSaveable { mutableStateOf<String?>(null) }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = path; path = null
        if (p != null) scope.launch {
            val jpeg = if (ok) withContext(Dispatchers.IO) { runCatching { toJpeg(File(p), maxSide, quality) }.getOrNull() } else null
            withContext(Dispatchers.IO) { File(p).delete() }
            if (jpeg != null) callback(jpeg)
        }
    }
    val open = {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        path = f.absolutePath
        take.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f))
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) open() }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
        else ask.launch(Manifest.permission.CAMERA)
    }
}

/** Đọc file ảnh → xoay theo EXIF → thu nhỏ cạnh dài ≤ [maxSide] → JPEG. */
private fun toJpeg(file: File, maxSide: Int, quality: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val longest = maxOf(bmp.width, bmp.height)
    val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
    if (degrees != 0f || scale != 1f) {
        val m = Matrix().apply { postScale(scale, scale); postRotate(degrees) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
