package com.kalotrapezis.drive
import androidx.compose.material3.Slider
import java.util.UUID
import java.util.concurrent.Executors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaMetadataRetriever
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

private const val SCANNER_AUTO_CAPTURE = "scanner_auto_capture"
private const val SCAN_MAX_EDGE = 3508f
private const val SHOW_SCANNER_DETECTION_GUIDE = true

/**
 * One scanned page. [quad] is the page outline in the photo's upright normalized coordinates (null: use the whole
 * photo); [autoQuad] is what detection found, kept for Reset. Edits are stored as data and rendered on demand.
 */
internal data class CapturedPage(
    val file: File,
    val quad: DocumentQuad? = null,
    val autoQuad: DocumentQuad? = quad,
    val rotation: Int = 0,
    val filter: ScanFilter = ScanFilter.Original,
    val autoFix: Boolean = true,
    val paperTone: Int = ScanFilters.WHITE,
    val strokes: List<ScanStroke> = emptyList(),
) {
    val key: String get() = file.name
}

/**
 * A hand-painted brush stroke, drawn last over the rendered page. Points are normalized to the rendered page;
 * [width] is relative to the page's mean side, so it survives rotation.
 */
internal data class ScanStroke(val points: List<ScanPoint>, val color: Int, val width: Float)

internal fun ScanStroke.rotated(degrees: Int): ScanStroke = copy(points = points.map { point ->
    when ((degrees % 360 + 360) % 360) {
        90 -> ScanPoint(1f - point.y, point.x)
        180 -> ScanPoint(1f - point.x, 1f - point.y)
        270 -> ScanPoint(point.y, 1f - point.x)
        else -> point
    }
})

/** Rotates the page and its painted strokes together. */
private fun CapturedPage.rotatedBy(degrees: Int) = copy(rotation = (rotation + degrees + 360) % 360, strokes = strokes.map { it.rotated(degrees) })

@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
internal fun CameraScanTab(back: () -> Unit, error: String?, pages: List<CapturedPage>, openPages: () -> Unit, captured: (CapturedPage) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scannerPreferences = remember(context) { context.getSharedPreferences("scanner", Context.MODE_PRIVATE) }
    var autoCaptureEnabled by remember { mutableStateOf(scannerPreferences.getBoolean(SCANNER_AUTO_CAPTURE, false)) }
    var pageFound by remember { mutableStateOf(false) }
    var pageStable by remember { mutableStateOf(false) }
    var lastDetectedQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var stableFrames by remember { mutableStateOf(0) }
    var stableSinceMillis by remember { mutableStateOf<Long?>(null) }
    var lastVisibleDetectionMillis by remember { mutableStateOf(0L) }
    var pageOutline by remember { mutableStateOf<DocumentQuad?>(null) }
    var captureQuad by remember { mutableStateOf<DocumentQuad?>(null) }
    var autoProgress by remember { mutableStateOf(0f) }
    var captureInProgress by remember { mutableStateOf(false) }
    var waitingForNextPage by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val requestCamera = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (!granted) cameraError = "Camera access is needed only to scan a page."
    }

    LaunchedEffect(hasCamera, lifecycleOwner) {
        if (!hasCamera) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider
                previewView.post {
                    runCatching {
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                        val imageCapture = ImageCapture.Builder().setTargetRotation(targetRotation).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                        val analysis = ImageAnalysis.Builder().setTargetRotation(targetRotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(analysisExecutor) { frame ->
                            val detected = lumaFrame(frame)?.let(ScanDetection::detect)
                            val detectedInCapture = detected?.let {
                                val crop = frame.cropRect
                                ScanDetection.toCaptureFrame(it, frame.width, frame.height, crop.left, crop.top, crop.width(), crop.height(), frame.imageInfo.rotationDegrees)
                            }
                            ContextCompat.getMainExecutor(context).execute {
                                try {
                                    val outline = detected?.let { mapToPreview(frame, it, previewView) }
                                    val now = SystemClock.elapsedRealtime()
                                    if (detected == null || outline == null) {
                                        // The outline is held through a bad frame, but a held outline is a memory,
                                        // not a page: auto capture waits for the camera to see it again.
                                        pageStable = false
                                        if (!keepDetectionVisible(lastVisibleDetectionMillis, now)) {
                                            stableFrames = 0
                                            stableSinceMillis = null
                                            pageFound = false
                                            pageStable = false
                                            lastDetectedQuad = null
                                            pageOutline = null
                                            captureQuad = null
                                        }
                                    } else {
                                        lastVisibleDetectionMillis = now
                                        pageFound = true
                                        stableFrames = if (ScanDetection.isStable(lastDetectedQuad, detected)) stableFrames + 1 else 1
                                        stableSinceMillis = if (stableFrames == 1) now else stableSinceMillis ?: now
                                        // Each frame nudges the outline rather than replacing it, so a corner
                                        // that wobbles by a pixel stops dragging the whole page with it.
                                        val settled = ScanDetection.smooth(lastDetectedQuad, detected)
                                        lastDetectedQuad = settled
                                        pageStable = detectionIsSettled(stableSinceMillis, now)
                                        pageOutline = outline
                                        captureQuad = detectedInCapture
                                    }
                                } finally {
                                    frame.close()
                                }
                            }
                        }
                        val viewPort = checkNotNull(previewView.viewPort) { "Camera preview is not ready." }
                        val useCases = UseCaseGroup.Builder()
                            .setViewPort(viewPort)
                            .addUseCase(preview)
                            .addUseCase(imageCapture)
                            .addUseCase(analysis)
                            .build()
                        capture = null
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCases)
                        capture = imageCapture
                    }.onFailure { cameraError = it.message ?: "Could not start the camera." }
                }
            }.onFailure { cameraError = it.message ?: "Could not start the camera." }
        }, ContextCompat.getMainExecutor(context))
    }
    androidx.compose.runtime.DisposableEffect(cameraProvider) {
        val boundProvider = cameraProvider
        onDispose { boundProvider?.unbindAll() }
    }
    androidx.compose.runtime.DisposableEffect(analysisExecutor) {
        onDispose { analysisExecutor.shutdown() }
    }

    fun capturePage() {
        val imageCapture = capture ?: return
        captureInProgress = true
        val quadAtCapture = captureQuad
        val file = File(context.cacheDir, "scans/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    captureInProgress = false
                    waitingForNextPage = true
                    Thread {
                        // Resolve the outline once; a quarter-size decode is plenty for detection.
                        val quad = decodeCapturedScan(file, 4)?.let { pageQuad(it, quadAtCapture) }
                        val page = CapturedPage(file, quad)
                        ContextCompat.getMainExecutor(context).execute { captured(page) }
                    }.start()
                }
                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    cameraError = exception.message ?: "Could not capture the page."
                }
            },
        )
    }
    LaunchedEffect(autoCaptureEnabled, pageStable, capture, captureInProgress, waitingForNextPage) {
        autoProgress = 0f
        if (!autoCaptureEnabled || !pageStable || capture == null || captureInProgress || waitingForNextPage) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (pageStable && autoCaptureEnabled && !captureInProgress) {
            autoProgress = autoCaptureProgress(System.currentTimeMillis() - startedAt)
            if (autoProgress >= 1f) {
                capturePage()
                return@LaunchedEffect
            }
            delay(50)
        }
    }
    LaunchedEffect(waitingForNextPage, autoCaptureEnabled) {
        if (waitingForNextPage && autoCaptureEnabled) {
            delay(NEXT_PAGE_REARM_DELAY_MILLIS)
            waitingForNextPage = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (SHOW_SCANNER_DETECTION_GUIDE) pageOutline?.let { PageOutline(it) }
        Surface(
            color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) { Column(Modifier.statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                Text("Scan page", style = MaterialTheme.typography.titleLarge)
            }
            Text("Keep the page inside the frame, then capture it to edit.", style = MaterialTheme.typography.bodyMedium)
        } }
        Surface(
            shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.65f), contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
        ) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!hasCamera) Button(onClick = { requestCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    }, enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                        Icon(painterResource(if (torchEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off), contentDescription = if (torchEnabled) "Flash on" else "Flash off", tint = Color.White)
                    }
                    ScannerShutter(
                        autoEnabled = autoCaptureEnabled,
                        documentDetected = pageStable && !waitingForNextPage,
                        progress = autoProgress,
                        enabled = capture != null && !captureInProgress,
                        capture = ::capturePage,
                    )
                    pages.lastOrNull()?.let { latest -> ScanPagesButton(latest, pages.size, openPages) }
                        ?: Box(Modifier.size(48.dp))
                }
                Surface(
                    shape = CircleShape,
                    color = if (autoCaptureEnabled) Color(0xFF1976D2) else Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                    modifier = Modifier.padding(top = 8.dp).clickable {
                        autoCaptureEnabled = !autoCaptureEnabled
                        scannerPreferences.edit().putBoolean(SCANNER_AUTO_CAPTURE, autoCaptureEnabled).apply()
                    },
                ) { Text(if (autoCaptureEnabled) "Auto capture on" else "Auto capture off", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                if (autoCaptureEnabled) Text(
                    when {
                        waitingForNextPage -> "Move to the next page"
                        pageStable -> "Page stable — hold steady"
                        pageFound -> "Page found — hold steady"
                        else -> "Point at a page"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            (cameraError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } }
    }
}

internal fun autoCaptureProgress(elapsedMillis: Long): Float = (elapsedMillis.coerceIn(0, 2_500).toFloat() / 2_500f)
internal const val NEXT_PAGE_REARM_DELAY_MILLIS = 2_000L
internal const val DETECTION_SETTLE_MILLIS = 650L
// Paper does not disappear because one frame out of thirty went badly — a hand crossing, the light changing, a
// reflection off the flash. Holding the last outline for a beat is what makes the finder feel decided rather
// than twitchy; below about half a second it lets go while you are still lining the page up.
private const val DETECTION_LOSS_GRACE_MILLIS = 900L
internal fun detectionIsSettled(stableSinceMillis: Long?, nowMillis: Long): Boolean = stableSinceMillis != null && nowMillis - stableSinceMillis >= DETECTION_SETTLE_MILLIS
private fun keepDetectionVisible(lastVisibleMillis: Long, nowMillis: Long): Boolean = nowMillis - lastVisibleMillis <= DETECTION_LOSS_GRACE_MILLIS

private fun lumaFrame(frame: ImageProxy): LumaFrame? = frame.planes.firstOrNull()?.let { plane ->
    val buffer = plane.buffer.duplicate()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    LumaFrame(bytes, frame.width, frame.height, plane.rowStride, plane.pixelStride)
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
private fun mapToPreview(frame: ImageProxy, quad: DocumentQuad, preview: PreviewView): DocumentQuad? {
    val previewTransform = preview.outputTransform ?: return null
    val points = quad.points.flatMap { point -> listOf(point.x * frame.width, point.y * frame.height) }.toFloatArray()
    // The detector reads the raw analysis buffer, so it must use the factory defaults: no prior crop or rotation.
    val sourceTransform = ImageProxyTransformFactory().getOutputTransform(frame)
    CoordinateTransform(sourceTransform, previewTransform).mapPoints(points)
    return DocumentQuad(
        ScanPoint(points[0], points[1]), ScanPoint(points[2], points[3]),
        ScanPoint(points[4], points[5]), ScanPoint(points[6], points[7]),
    )
}

@Composable
private fun PageOutline(quad: DocumentQuad) = Canvas(Modifier.fillMaxSize()) {
    val radius = 10.dp.toPx()
    quad.points.forEach { point ->
        val visible = point.clampToViewport(size.width, size.height, radius)
        drawCircle(Color.White, radius, Offset(visible.x, visible.y))
        drawCircle(Color.Black.copy(alpha = 0.65f), radius, Offset(visible.x, visible.y), style = Stroke(2.dp.toPx()))
    }
}

internal fun ScanPoint.clampToViewport(width: Float, height: Float, inset: Float): ScanPoint = ScanPoint(
    x.coerceIn(inset, (width - inset).coerceAtLeast(inset)),
    y.coerceIn(inset, (height - inset).coerceAtLeast(inset)),
)

@Composable
private fun ScanPagesButton(page: CapturedPage, count: Int, open: () -> Unit) {
    val bitmap = rememberScanRender(page, SCAN_THUMBNAIL_SAMPLE, SCAN_THUMBNAIL_EDGE)
    Surface(shape = MaterialTheme.shapes.medium, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(52.dp, 68.dp).clickable(onClick = open)) {
        Box(Modifier.fillMaxSize()) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "$count captured pages", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Surface(shape = CircleShape, color = Color(0xFF1976D2), contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)) {
                Text(count.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScannerShutter(autoEnabled: Boolean, documentDetected: Boolean, progress: Float, enabled: Boolean, capture: () -> Unit) {
    val ring = if (autoEnabled && documentDetected) Color(0xFF2196F3) else Color(0xFF90A4AE)
    Canvas(
        modifier = Modifier.size(86.dp).semantics { contentDescription = "Capture page" }.clickable(enabled = enabled, onClick = capture),
    ) {
        val stroke = 5.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        drawCircle(Color.White, radius = radius - stroke / 2f)
        drawCircle(ring, radius = radius, style = Stroke(stroke))
        if (progress > 0f) drawArc(Color(0xFF0D47A1), -90f, 360f * progress, false, style = Stroke(stroke * 1.7f))
    }
}

internal fun rotateScan(source: Bitmap, degrees: Int): Bitmap = if (degrees % 360 == 0) source else Matrix().let { matrix ->
    matrix.postRotate(degrees.toFloat())
    Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** Renders a page from its photo and stored edits: straighten + gap fill, rotation, then filter. */
internal fun renderScanPage(page: CapturedPage, sampleSize: Int, maxEdge: Float = SCAN_MAX_EDGE): Bitmap? {
    val source = decodeCapturedScan(page.file, sampleSize) ?: return null
    val straightened = page.quad?.let { autoFixScan(source, it, maxEdge, page.autoFix) } ?: source
    val result = rotateScan(straightened, page.rotation).let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
    if (page.filter != ScanFilter.Original) {
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        ScanFilters.apply(pixels, result.width, result.height, page.filter, page.paperTone)
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    }
    drawStrokes(result, page.strokes)
    return result
}

/** Burns brush strokes into a mutable bitmap. */
internal fun drawStrokes(target: Bitmap, strokes: List<ScanStroke>) {
    if (strokes.isEmpty()) return
    val canvas = android.graphics.Canvas(target)
    val side = (target.width + target.height) / 2f
    strokes.forEach { stroke ->
        canvas.drawPath(stroke.points.toPath(target.width.toFloat(), target.height.toFloat()), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color; style = android.graphics.Paint.Style.STROKE; strokeWidth = stroke.width * side
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        })
    }
}

private fun List<ScanPoint>.toPath(width: Float, height: Float) = android.graphics.Path().also { path ->
    forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x * width, point.y * height) else path.lineTo(point.x * width, point.y * height) }
    // A tap is a dot: give single-point strokes a tiny segment so the round cap draws.
    if (size == 1) path.lineTo(first().x * width + 0.1f, first().y * height)
}

private const val SCAN_THUMBNAIL_SAMPLE = 8
private const val SCAN_THUMBNAIL_EDGE = 480f

@Composable
private fun rememberScanRender(page: CapturedPage, sampleSize: Int, maxEdge: Float): Bitmap? =
    produceState<Bitmap?>(null, page) { value = withContext(Dispatchers.Default) { runCatching { renderScanPage(page, sampleSize, maxEdge) }.getOrNull() } }.value

private fun decodeCapturedScan(file: File, sampleSize: Int = 1): Bitmap? {
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val source = BitmapFactory.decodeFile(file.path, options) ?: return null
    val matrix = Matrix()
    when (runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun detectBitmapPage(source: Bitmap): DocumentQuad? {
    val scale = maxOf(1, maxOf(source.width, source.height) / 256)
    val width = source.width / scale
    val height = source.height / scale
    val luma = ByteArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        val pixel = source.getPixel(x * scale, y * scale)
        luma[y * width + x] = ((android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000).toByte()
    }
    return ScanDetection.detect(LumaFrame(luma, width, height, width, 1))
}

/** Prefer the outline the user saw live; re-detecting on the still can lock onto another bright shape (e.g. a laptop screen). */
private fun pageQuad(source: Bitmap, liveQuad: DocumentQuad?): DocumentQuad? =
    liveQuad?.takeIf(DocumentQuad::isValidCrop) ?: detectBitmapPage(source)?.takeIf(DocumentQuad::isValidCrop)

/** Straightens the page with a small outer margin so no paper is cut off, then paints the visible table gaps paper-coloured. */
private fun autoFixScan(source: Bitmap, quad: DocumentQuad, maxEdge: Float = SCAN_MAX_EDGE, fillGaps: Boolean = true): Bitmap {
    val page = cropScan(source, quad.withCropMargin(0.04f), maxEdge)
    if (!fillGaps) return page
    val pixels = IntArray(page.width * page.height)
    page.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
    ScanDetection.fillPageGaps(pixels, page.width, page.height)
    page.setPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
    return page
}

internal fun DocumentQuad.withCropMargin(margin: Float = 0.02f): DocumentQuad {
    val center = ScanPoint(points.sumOf { it.x.toDouble() }.toFloat() / 4f, points.sumOf { it.y.toDouble() }.toFloat() / 4f)
    fun expand(point: ScanPoint) = ScanPoint(
        (center.x + (point.x - center.x) * (1f + margin)).coerceIn(0f, 1f),
        (center.y + (point.y - center.y) * (1f + margin)).coerceIn(0f, 1f),
    )
    return DocumentQuad(expand(topLeft), expand(topRight), expand(bottomRight), expand(bottomLeft))
}

internal fun cropScan(source: Bitmap, quad: DocumentQuad, maxEdge: Float = SCAN_MAX_EDGE): Bitmap {
    val sourcePoints = quad.points.flatMap { point -> listOf(point.x * source.width, point.y * source.height) }.toFloatArray()
    fun distance(first: ScanPoint, second: ScanPoint) = kotlin.math.hypot((first.x - second.x) * source.width, (first.y - second.y) * source.height)
    val rawWidth = maxOf(distance(quad.topLeft, quad.topRight), distance(quad.bottomLeft, quad.bottomRight))
    val rawHeight = maxOf(distance(quad.topLeft, quad.bottomLeft), distance(quad.topRight, quad.bottomRight))
    // Cap at A4 @ 300 dpi on the long edge; larger pages only cost memory and PDF size.
    val scale = minOf(1f, maxEdge / maxOf(rawWidth, rawHeight))
    val width = maxOf(1, (rawWidth * scale).roundToInt())
    val height = maxOf(1, (rawHeight * scale).roundToInt())
    val destination = floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat())
    val matrix = Matrix()
    check(matrix.setPolyToPoly(sourcePoints, 0, destination, 0, 4)) { "Could not correct this crop." }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result -> android.graphics.Canvas(result).drawBitmap(source, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
}

private fun DocumentQuad.withPoint(index: Int, point: ScanPoint): DocumentQuad = when (index) {
    0 -> copy(topLeft = point); 1 -> copy(topRight = point); 2 -> copy(bottomRight = point); else -> copy(bottomLeft = point)
}

private fun DocumentQuad.isValidCrop(): Boolean {
    fun cross(first: ScanPoint, second: ScanPoint, third: ScanPoint): Float =
        (second.x - first.x) * (third.y - second.y) - (second.y - first.y) * (third.x - second.x)
    val turns = points.indices.map { cross(points[it], points[(it + 1) % 4], points[(it + 2) % 4]) }
    return turns.all { it > 0.002f } || turns.all { it < -0.002f }
}


private enum class ScanPanel { Adjust, Filters, Paint }
private enum class ScanDocumentMode { Pages, Corners, Reorder }

/** The scanned document: page viewer, filmstrip and an actions island, following the gallery viewer layout. */
@Composable
internal fun ScanDocumentTab(
    pages: List<CapturedPage>,
    selected: Int,
    select: (Int) -> Unit,
    update: (Int, CapturedPage) -> Unit,
    move: (Int, Int) -> Unit,
    retake: (Int) -> Unit,
    addPage: () -> Unit,
    discard: () -> Unit,
    saving: Boolean,
    error: String?,
    save: (String) -> Unit,
) {
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { addPage() }
        return
    }
    val current = selected.coerceIn(pages.indices)
    val page = pages[current]
    var mode by remember { mutableStateOf(ScanDocumentMode.Pages) }
    var panel by remember { mutableStateOf<ScanPanel?>(null) }
    var saveOpen by remember { mutableStateOf(false) }
    var discardOpen by remember { mutableStateOf(false) }
    var paintColor by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var brushWidth by remember { mutableStateOf(0.03f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler {
        when {
            mode != ScanDocumentMode.Pages -> mode = ScanDocumentMode.Pages
            panel != null -> panel = null
            else -> discardOpen = true
        }
    }
    when (mode) {
        ScanDocumentMode.Corners -> {
            ScanCornerEditor(page, done = { quad -> update(current, page.copy(quad = quad)); mode = ScanDocumentMode.Pages }, cancel = { mode = ScanDocumentMode.Pages })
            return
        }
        ScanDocumentMode.Reorder -> {
            ScanReorderGrid(pages, move, open = { index -> select(index); mode = ScanDocumentMode.Pages }, back = { mode = ScanDocumentMode.Pages })
            return
        }
        ScanDocumentMode.Pages -> Unit
    }

    val pagerState = rememberPagerState(initialPage = current) { pages.size }
    LaunchedEffect(current, pages.size) { if (pagerState.currentPage != current) pagerState.scrollToPage(current) }
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != current) select(pagerState.currentPage) }
    val filmstrip = rememberLazyListState()
    LaunchedEffect(current) { filmstrip.animateScrollToItem(current) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Gallery-style header: separate floating islands for Back, title and the auto-fix toggle.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                IconButton(onClick = { discardOpen = true }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            }
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Page ${current + 1} of ${pages.size}", style = MaterialTheme.typography.titleSmall)
                    Text(if (page.autoFix) page.filter.label else "${page.filter.label} · Auto fix off", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                IconButton(onClick = { update(current, page.copy(autoFix = !page.autoFix)) }) {
                    Icon(
                        painterResource(R.drawable.ic_auto_fix),
                        contentDescription = if (page.autoFix) "Auto fix on for this page" else "Auto fix off for this page",
                        tint = islandContentColor().copy(alpha = if (page.autoFix) 1f else 0.35f),
                    )
                }
            }
        }
        HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), userScrollEnabled = panel != ScanPanel.Paint, key = { pages[it].key }) { index ->
            val item = pages[index]
            // Strokes are drawn as a live overlay, so painting never waits for a re-render.
            val bitmap = rememberScanRender(item.copy(strokes = emptyList()), 2, 2000f)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (bitmap == null) CircularProgressIndicator()
                else {
                    Image(bitmap.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    ScanStrokeLayer(bitmap.width, bitmap.height, item.strokes, panel == ScanPanel.Paint && index == current, paintColor, brushWidth) { stroke ->
                        update(index, item.copy(strokes = item.strokes + stroke))
                    }
                }
            }
        }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), state = filmstrip, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(pages, key = { _, item -> item.key }) { index, item ->
                Box(Modifier.size(48.dp, 64.dp).clip(MaterialTheme.shapes.small).clickable { select(index) }) {
                    ScanThumbnail(item, Modifier.fillMaxSize())
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 5.dp))
                    if (index == current) Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp, 64.dp).clickable(onClick = addPage)) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Add page") }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        ScanActionsIsland(
            panel = panel,
            showPanel = { panel = it },
            page = page,
            update = { update(current, it) },
            applyFilterToAll = { filter -> pages.forEachIndexed { index, item -> update(index, item.copy(filter = filter, paperTone = page.paperTone)) } },
            matchPages = {
                scope.launch {
                    val tone = withContext(Dispatchers.Default) {
                        ScanFilters.average(pages.mapNotNull { item -> renderScanPage(item.copy(filter = ScanFilter.Original, strokes = emptyList()), SCAN_THUMBNAIL_SAMPLE, SCAN_THUMBNAIL_EDGE)?.let { paperSwatchesOf(it)[1] } })
                    }
                    pages.forEachIndexed { index, item -> update(index, item.copy(filter = ScanFilter.SamePaper, paperTone = tone)) }
                }
            },
            paintColor = paintColor,
            setPaintColor = { paintColor = it },
            brushWidth = brushWidth,
            setBrushWidth = { brushWidth = it },
            retake = { retake(current) },
            crop = { panel = null; mode = ScanDocumentMode.Corners },
            reorder = { panel = null; mode = ScanDocumentMode.Reorder },
            save = { panel = null; saveOpen = true },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }

    if (saveOpen) {
        var name by remember { mutableStateOf("Scan ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm").withZone(ZoneId.systemDefault()).format(Instant.now())}") }
        AlertDialog(
            onDismissRequest = { if (!saving) saveOpen = false },
            title = { Text("Save PDF in Files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, singleLine = true, enabled = !saving)
                    Text("${pages.size} ${if (pages.size == 1) "page" else "pages"} · Documents › Scanned Documents", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { save(name) }, enabled = !saving && name.isNotBlank()) { Text(if (saving) "Saving…" else "Save") } },
            dismissButton = { TextButton(onClick = { saveOpen = false }, enabled = !saving) { Text("Cancel") } },
        )
    }
    if (discardOpen) AlertDialog(
        onDismissRequest = { discardOpen = false },
        title = { Text("Discard this scan?") },
        text = { Text("${pages.size} unsaved ${if (pages.size == 1) "page" else "pages"} will be removed.") },
        confirmButton = { TextButton(onClick = { discardOpen = false; discard() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardOpen = false }) { Text("Keep editing") } },
    )
}

@Composable
private fun ScanActionsIsland(
    panel: ScanPanel?, showPanel: (ScanPanel?) -> Unit, page: CapturedPage, update: (CapturedPage) -> Unit,
    applyFilterToAll: (ScanFilter) -> Unit, matchPages: () -> Unit,
    paintColor: Int, setPaintColor: (Int) -> Unit, brushWidth: Float, setBrushWidth: (Float) -> Unit,
    retake: () -> Unit, crop: () -> Unit, reorder: () -> Unit, save: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    fun toggle(target: ScanPanel) = showPanel(if (panel == target) null else target)
    val dragThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Tap the handle to collapse; pull it up to open manual painting, pull down to close.
            Box(Modifier.fillMaxWidth().height(22.dp).clickable { expanded = !expanded }.pointerInput(panel) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = {
                        if (dragged < -dragThreshold) { expanded = true; showPanel(ScanPanel.Paint) }
                        else if (dragged > dragThreshold) { if (panel != null) showPanel(null) else expanded = false }
                    },
                )
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(islandContentColor().copy(alpha = 0.72f)))
            }
            AnimatedVisibility(expanded) {
                Column {
                    AnimatedVisibility(panel == ScanPanel.Adjust) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { ScanPill("Crop", R.drawable.ic_crop, false, crop) }
                            item { ScanPill("Left", R.drawable.ic_rotate_left, false) { update(page.rotatedBy(270)) } }
                            item { ScanPill("Right", R.drawable.ic_rotate_right, false) { update(page.rotatedBy(90)) } }
                            item { ScanPill("Reset", R.drawable.ic_refresh, false) { update(page.rotatedBy(-page.rotation).copy(quad = page.autoQuad)) } }
                        }
                    }
                    AnimatedVisibility(panel == ScanPanel.Filters) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { ScanPill("Match pages", R.drawable.ic_contrast, page.filter == ScanFilter.SamePaper, matchPages) }
                            items(ScanFilter.entries.filter { it != ScanFilter.SamePaper }) { filter -> ScanPill(filter.label, null, page.filter == filter) { update(page.copy(filter = filter)) } }
                            item { ScanPill("Apply to all", R.drawable.ic_check, false) { applyFilterToAll(page.filter) } }
                        }
                    }
                    AnimatedVisibility(panel == ScanPanel.Paint) {
                        ScanPaintPanel(page, paintColor, setPaintColor, brushWidth, setBrushWidth, undo = { update(page.copy(strokes = page.strokes.dropLast(1))) })
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton(onClick = retake) { Icon(painterResource(R.drawable.ic_retake), contentDescription = "Retake this page") }
                        ScanIslandToggle(R.drawable.ic_tune, "Adjust", panel == ScanPanel.Adjust) { toggle(ScanPanel.Adjust) }
                        ScanIslandToggle(R.drawable.ic_contrast, "Filters", panel == ScanPanel.Filters) { toggle(ScanPanel.Filters) }
                        IconButton(onClick = reorder) { Icon(painterResource(R.drawable.ic_view_grid), contentDescription = "Reorder pages") }
                        IconButton(onClick = save) { Icon(painterResource(R.drawable.ic_save), contentDescription = "Save PDF") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScanIslandToggle(icon: Int, label: String, selected: Boolean, click: () -> Unit) = IconButton(
    onClick = click,
    modifier = Modifier.background(if (selected) driveNavigationSelectedColor() else Color.Transparent, CircleShape),
) { Icon(painterResource(icon), contentDescription = label, tint = if (selected) driveNavigationSelectedContentColor() else islandContentColor()) }

@Composable
internal fun ScanPill(label: String, icon: Int?, selected: Boolean, click: () -> Unit) = Surface(
    color = if (selected) driveNavigationSelectedColor() else islandContentColor().copy(alpha = 0.1f),
    contentColor = if (selected) driveNavigationSelectedContentColor() else islandContentColor(),
    shape = CircleShape,
    modifier = Modifier.heightIn(min = 40.dp).clickable(onClick = click).semantics { this.selected = selected },
) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ScanThumbnail(page: CapturedPage, modifier: Modifier) {
    val bitmap = rememberScanRender(page, SCAN_THUMBNAIL_SAMPLE, SCAN_THUMBNAIL_EDGE)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Page grid with numbers; long-press a page and drag it to a new position. */
@Composable
private fun ScanReorderGrid(pages: List<CapturedPage>, move: (Int, Int) -> Unit, open: (Int) -> Unit, back: () -> Unit) {
    val gridState = rememberLazyGridState()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentMove by rememberUpdatedState(move)
    fun androidx.compose.foundation.lazy.grid.LazyGridItemInfo.contains(point: Offset) =
        point.x >= offset.x && point.x < offset.x + size.width && point.y >= offset.y && point.y < offset.y + size.height
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ModuleHeader("Pages", back)
        Text("Long-press a page and drag it to reorder.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.contains(position) }?.let { draggingKey = it.key as String; dragOffset = Offset.Zero }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        val items = gridState.layoutInfo.visibleItemsInfo
                        val dragged = items.firstOrNull { it.key == draggingKey } ?: return@detectDragGesturesAfterLongPress
                        val center = Offset(dragged.offset.x + dragged.size.width / 2f, dragged.offset.y + dragged.size.height / 2f) + dragOffset
                        val target = items.firstOrNull { it.key != draggingKey && it.contains(center) } ?: return@detectDragGesturesAfterLongPress
                        currentMove(dragged.index, target.index)
                        // The dragged page now lays out at the target slot; keep it under the finger.
                        dragOffset += Offset((dragged.offset.x - target.offset.x).toFloat(), (dragged.offset.y - target.offset.y).toFloat())
                    },
                    onDragEnd = { draggingKey = null; dragOffset = Offset.Zero },
                    onDragCancel = { draggingKey = null; dragOffset = Offset.Zero },
                )
            },
        ) {
            itemsIndexed(pages, key = { _, page -> page.key }) { index, page ->
                val dragging = page.key == draggingKey
                Column(
                    Modifier.zIndex(if (dragging) 1f else 0f)
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (dragging) { translationX = dragOffset.x; translationY = dragOffset.y; scaleX = 1.06f; scaleY = 1.06f; shadowElevation = 12f }
                        }
                        .clickable { open(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ScanThumbnail(page, Modifier.fillMaxWidth().aspectRatio(0.72f).clip(MaterialTheme.shapes.small))
                    Text("${index + 1}", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** Drag the four corners over the original photo; a magnifier with a grid follows in the opposite screen corner. */
@Composable
private fun ScanCornerEditor(page: CapturedPage, done: (DocumentQuad) -> Unit, cancel: () -> Unit) {
    val source by produceState<Bitmap?>(null, page.file) { value = withContext(Dispatchers.IO) { decodeCapturedScan(page.file, 2) } }
    CornerEditor(source, page.quad ?: DocumentQuad.fullFrame(), page.autoQuad ?: DocumentQuad.fullFrame(), "Auto detect", done, cancel)
}

/** Shared by the scanner and the photo editor. [reset] is what the left button restores, labelled [resetLabel]. */
@Composable
internal fun CornerEditor(source: Bitmap?, initial: DocumentQuad, reset: DocumentQuad, resetLabel: String, done: (DocumentQuad) -> Unit, cancel: () -> Unit) {
    var crop by remember(source, initial) { mutableStateOf(initial) }
    var draggedHandle by remember { mutableStateOf(-1) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(top = 112.dp, start = 12.dp, end = 12.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val image = source
            if (image == null) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                fun imagePoint(position: Offset): ScanPoint? {
                    if (canvasSize == IntSize.Zero) return null
                    val scale = minOf(canvasSize.width / image.width.toFloat(), canvasSize.height / image.height.toFloat())
                    val left = (canvasSize.width - image.width * scale) / 2f
                    val top = (canvasSize.height - image.height * scale) / 2f
                    return ScanPoint(((position.x - left) / (image.width * scale)).coerceIn(0f, 1f), ((position.y - top) / (image.height * scale)).coerceIn(0f, 1f))
                }
                Canvas(
                    Modifier.fillMaxWidth().weight(1f).background(Color(0xFF303030)).onSizeChanged { canvasSize = it }
                        .pointerInput(image) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    val point = imagePoint(position) ?: return@detectDragGestures
                                    draggedHandle = crop.points.indices.minByOrNull { index ->
                                        val handle = crop.points[index]
                                        (handle.x - point.x) * (handle.x - point.x) + (handle.y - point.y) * (handle.y - point.y)
                                    }?.takeIf { index ->
                                        val handle = crop.points[index]
                                        (handle.x - point.x) * (handle.x - point.x) + (handle.y - point.y) * (handle.y - point.y) < 0.02f
                                    } ?: -1
                                },
                                onDrag = { change, _ -> if (draggedHandle >= 0) imagePoint(change.position)?.let { crop = crop.withPoint(draggedHandle, it) } },
                                onDragEnd = { draggedHandle = -1 },
                                onDragCancel = { draggedHandle = -1 },
                            )
                        },
                ) {
                    val scale = minOf(size.width / image.width, size.height / image.height)
                    val target = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt())
                    drawImage(image.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - target.width) / 2).roundToInt(), ((size.height - target.height) / 2).roundToInt()), dstSize = target)
                    fun canvasPoint(point: ScanPoint) = Offset((size.width - target.width) / 2f + point.x * target.width, (size.height - target.height) / 2f + point.y * target.height)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        crop.points.forEachIndexed { index, point -> canvasPoint(point).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                        close()
                    }
                    drawPath(path, Color(0xFF2196F3), style = Stroke(3.dp.toPx()))
                    crop.points.forEach { point -> drawCircle(Color(0xFF2196F3), 12.dp.toPx(), canvasPoint(point)) }
                    crop.points.getOrNull(draggedHandle)?.let { handle ->
                        // Magnifier in the opposite screen corner so the finger never covers it.
                        val loupe = 140.dp.toPx()
                        val zoom = 3f * scale
                        val margin = 12.dp.toPx()
                        val handleOnScreen = canvasPoint(handle)
                        val topLeft = Offset(
                            if (handleOnScreen.x < size.width / 2f) size.width - loupe - margin else margin,
                            if (handleOnScreen.y < size.height / 2f) size.height - loupe - margin else margin,
                        )
                        val center = topLeft + Offset(loupe / 2f, loupe / 2f)
                        fun loupePoint(point: ScanPoint) = center + Offset((point.x - handle.x) * image.width * zoom, (point.y - handle.y) * image.height * zoom)
                        clipRect(topLeft.x, topLeft.y, topLeft.x + loupe, topLeft.y + loupe) {
                            drawRect(Color.Black, topLeft, androidx.compose.ui.geometry.Size(loupe, loupe))
                            val imageOrigin = loupePoint(ScanPoint(0f, 0f))
                            drawImage(
                                image.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(imageOrigin.x.roundToInt(), imageOrigin.y.roundToInt()),
                                dstSize = IntSize((image.width * zoom).roundToInt(), (image.height * zoom).roundToInt()),
                            )
                            val cell = loupe / 6f
                            for (line in 1 until 6) {
                                drawLine(Color.White.copy(alpha = 0.35f), Offset(topLeft.x + cell * line, topLeft.y), Offset(topLeft.x + cell * line, topLeft.y + loupe), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.35f), Offset(topLeft.x, topLeft.y + cell * line), Offset(topLeft.x + loupe, topLeft.y + cell * line), 1.dp.toPx())
                            }
                            listOf(crop.points[(draggedHandle + 1) % 4], crop.points[(draggedHandle + 3) % 4]).forEach { neighbour ->
                                drawLine(Color(0xFF2196F3), center, loupePoint(neighbour), 2.dp.toPx())
                            }
                            drawCircle(Color(0xFF2196F3), 3.dp.toPx(), center)
                        }
                        drawRect(Color.White, topLeft, androidx.compose.ui.geometry.Size(loupe, loupe), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Text("Drag each blue corner onto a corner of the page.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { crop = reset }, modifier = Modifier.weight(1f)) { Text(resetLabel) }
                Button(onClick = { done(crop) }, enabled = crop.isValidCrop(), modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
        ModuleHeader("Crop & straighten", cancel, Modifier.align(Alignment.TopCenter))
    }
}

private fun paperSwatchesOf(bitmap: Bitmap): List<Int> {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return ScanFilters.paperSwatches(pixels)
}

/** Colours taken from this page's paper (lit, typical, light shadow, shadow), white, black, a custom colour, brush size. */
@Composable
private fun ScanPaintPanel(page: CapturedPage, color: Int, setColor: (Int) -> Unit, width: Float, setWidth: (Float) -> Unit, undo: () -> Unit) {
    val thumbnail = rememberScanRender(page.copy(strokes = emptyList()), SCAN_THUMBNAIL_SAMPLE, SCAN_THUMBNAIL_EDGE)
    val paper = remember(thumbnail) { thumbnail?.let(::paperSwatchesOf).orEmpty() }
    PaintPanel(paper + listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK), "Draw on the page to paint over marks.", color, setColor, width, setWidth, undo)
}

/** Swatches, a custom colour picker, brush size and Undo. Shared by the scanner and the photo editor. */
@Composable
internal fun PaintPanel(swatches: List<Int>, hint: String, color: Int, setColor: (Int) -> Unit, width: Float, setWidth: (Float) -> Unit, undo: () -> Unit) {
    var custom by remember { mutableStateOf<Int?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hint, style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(swatches + listOfNotNull(custom)) { swatch ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color(swatch))
                        .border(if (swatch == color) 3.dp else 1.dp, if (swatch == color) islandContentColor() else islandContentColor().copy(alpha = 0.3f), CircleShape)
                        .clickable { setColor(swatch) },
                )
            }
            item {
                Surface(shape = CircleShape, color = islandContentColor().copy(alpha = 0.1f), modifier = Modifier.size(36.dp).clickable { pickerOpen = true }) {
                    Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_palette), contentDescription = "Pick a colour", modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((6 + width * 280).dp.coerceAtMost(28.dp)).clip(CircleShape).background(Color(color)).border(1.dp, islandContentColor().copy(alpha = 0.4f), CircleShape))
            }
            Slider(value = width, onValueChange = setWidth, valueRange = 0.005f..0.08f, modifier = Modifier.weight(1f))
            ScanPill("Undo", R.drawable.ic_restore, false, undo)
        }
    }
    if (pickerOpen) ScanColorPicker(color, cancel = { pickerOpen = false }) { picked -> custom = picked; setColor(picked); pickerOpen = false }
}

@Composable
private fun ScanColorPicker(initial: Int, cancel: () -> Unit, pick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    val picked = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    AlertDialog(
        onDismissRequest = cancel,
        title = { Text("Brush colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.medium).background(Color(picked)))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = { TextButton(onClick = { pick(picked) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
    )
}

/** Draws committed strokes over the page image and, while [painting], records a new stroke under the finger. */
@Composable
internal fun ScanStrokeLayer(imageWidth: Int, imageHeight: Int, strokes: List<ScanStroke>, painting: Boolean, color: Int, width: Float, add: (ScanStroke) -> Unit) {
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val live = remember { androidx.compose.runtime.mutableStateListOf<ScanPoint>() }
    val currentAdd by rememberUpdatedState(add)
    val currentColor by rememberUpdatedState(color)
    val currentWidth by rememberUpdatedState(width)
    fun imageRect(): androidx.compose.ui.geometry.Rect {
        val scale = minOf(layerSize.width / imageWidth.toFloat(), layerSize.height / imageHeight.toFloat())
        val w = imageWidth * scale
        val h = imageHeight * scale
        return androidx.compose.ui.geometry.Rect(Offset((layerSize.width - w) / 2f, (layerSize.height - h) / 2f), androidx.compose.ui.geometry.Size(w, h))
    }
    fun normalize(position: Offset): ScanPoint = imageRect().let { rect ->
        ScanPoint(((position.x - rect.left) / rect.width).coerceIn(0f, 1f), ((position.y - rect.top) / rect.height).coerceIn(0f, 1f))
    }
    val input = if (painting) Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { live.clear(); live += normalize(it) },
            onDrag = { change, _ -> change.consume(); live += normalize(change.position) },
            onDragEnd = { if (live.isNotEmpty()) currentAdd(ScanStroke(live.toList(), currentColor, currentWidth)); live.clear() },
            onDragCancel = { live.clear() },
        )
    } else Modifier
    Canvas(Modifier.fillMaxSize().onSizeChanged { layerSize = it }.then(input)) {
        val rect = imageRect()
        fun draw(points: List<ScanPoint>, strokeColor: Int, strokeWidth: Float) {
            if (points.isEmpty()) return
            val path = androidx.compose.ui.graphics.Path().apply {
                points.forEachIndexed { index, point ->
                    val x = rect.left + point.x * rect.width
                    val y = rect.top + point.y * rect.height
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                if (points.size == 1) relativeLineTo(0.1f, 0f)
            }
            drawPath(path, Color(strokeColor), style = Stroke(strokeWidth * (rect.width + rect.height) / 2f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        strokes.forEach { draw(it.points, it.color, it.width) }
        draw(live.toList(), currentColor, currentWidth)
    }
}
