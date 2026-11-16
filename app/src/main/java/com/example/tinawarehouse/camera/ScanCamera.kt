package com.example.tinawarehouse.camera

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Camera chạy liên tục (CameraX), cùng một luồng hình cho hai việc — port từ CameraEngine.swift:
 *  - Đọc mã vạch bằng ML Kit Barcode (mọi khung hình).
 *  - Đọc chữ (mã kho in trên nhãn) bằng ML Kit Text Recognition, khi [ocrEnabled].
 * Kết quả gọi [onBarcodes] / [onLines] trên luồng chính. Tem nằm trong khung (thấy chữ) mà > 1,5 giây không đọc được
 * mã vạch thì tự lấy nét + đo sáng lại ở giữa khung (tối đa mỗi 2 giây).
 */
class ScanCamera(private val context: Context) {
    var onBarcodes: (List<String>) -> Unit = {}
    var onLines: (List<String>) -> Unit = {}
    @Volatile var ocrEnabled = true

    private val barcodes = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_E, Barcode.FORMAT_UPC_A, Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_PDF417,
            Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_AZTEC,
        ).build()
    )
    private val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    @Volatile private var lastBarcodeAt = 0L
    @Volatile private var lastTextAt = 0L
    @Volatile private var lastRefocus = 0L

    /** Gắn camera sau vào [previewView] theo vòng đời của [owner]. */
    fun start(owner: LifecycleOwner, previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get(); provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // 1080p: đủ nét để đọc chữ nhỏ trên nhãn mà vẫn nhẹ
            val selector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { analyze(it) }
            runCatching {
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Nhả camera (dừng quét, mở màn khác). */
    fun stop() { runCatching { provider?.unbindAll() }; camera = null }

    fun setTorch(on: Boolean) { camera?.cameraControl?.enableTorch(on) }
    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun release() { stop(); barcodes.close(); text.close(); executor.shutdown() }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: run { proxy.close(); return }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        val main = ContextCompat.getMainExecutor(context)
        barcodes.process(img)
            .continueWithTask<Text> { t ->
                val codes = (if (t.isSuccessful) t.result else null).orEmpty().mapNotNull { it.rawValue }
                if (codes.isNotEmpty()) { lastBarcodeAt = SystemClock.elapsedRealtime(); main.execute { onBarcodes(codes) } }
                if (ocrEnabled) text.process(img) else Tasks.forResult(null)
            }
            .addOnCompleteListener { t ->
                val result = if (t.isSuccessful) t.result else null
                val lines = result?.textBlocks?.flatMap { b -> b.lines.map { it.text } }.orEmpty().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) { lastTextAt = SystemClock.elapsedRealtime(); main.execute { onLines(lines) } }
                refocusIfStuck()
                proxy.close()
            }
    }

    /** Tem đang trong khung (thấy chữ) nhưng > 1,5 giây không đọc được mã vạch → lấy nét lại ở giữa khung. */
    private fun refocusIfStuck() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTextAt > 1000 || now - lastBarcodeAt < 1500 || now - lastRefocus < 2000) return
        lastRefocus = now
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(1, TimeUnit.SECONDS).build()
        runCatching { camera?.cameraControl?.startFocusAndMetering(action) }
    }
}
