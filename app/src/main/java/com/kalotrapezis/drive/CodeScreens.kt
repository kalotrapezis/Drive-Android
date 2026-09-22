package com.kalotrapezis.drive

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope

private enum class CodeMode { Codes, Text }

private sealed interface CodeResult {
    data class Payment(val code: String) : CodeResult
    data class Code(val value: String, val url: String?) : CodeResult
    data class Text(val text: String, val payment: String?) : CodeResult
    /** Local Drive's own pairing QR from the computer: pairs right away. */
    data class Pairing(val raw: String, val computer: String) : CodeResult
}

/** An RF payment code takes priority; otherwise a link gets an Open action. Only http(s) links are opened. */
private fun codeResult(value: String, url: String?): CodeResult = PaymentCodes.findRf(value)?.let(CodeResult::Payment)
    ?: CodeResult.Code(value, (url ?: value.trim()).let { link ->
        when {
            link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true) -> link
            link.startsWith("www.", ignoreCase = true) && ' ' !in link -> "https://$link"
            else -> null
        }
    })

/** QR/barcode scanning (continuous) and text recognition (on shutter), both on-device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
internal fun CodeScannerTab(back: () -> Unit, onCode: ((String) -> Boolean)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var hasCamera by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var mode by remember { mutableStateOf(CodeMode.Codes) }
    var result by remember { mutableStateOf<CodeResult?>(null) }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    // A dismissed code is ignored for a moment so the sheet does not pop straight back up.
    var ignoredValue by remember { mutableStateOf<String?>(null) }
    var ignoredUntil by remember { mutableStateOf(0L) }
    val scanning by rememberUpdatedState(hasCamera && mode == CodeMode.Codes && result == null)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodes = remember { BarcodeScanning.getClient() }
    val scope = rememberCoroutineScope()
    var pairStatus by remember { mutableStateOf<String?>(null) }
    fun pair(raw: String, qr: PairingQr) {
        result = CodeResult.Pairing(raw, qr.name)
        pairStatus = "Pairing…"
        scope.launch {
            pairStatus = runCatching { withContext(Dispatchers.IO) { SyncStore(context.applicationContext).let { SyncClient(context.applicationContext, it).pair(qr) } } }
                .fold({ "Paired with ${it.name}. Open Local Sync on the Home screen to back up your photos." }, { it.message ?: "Pairing failed." })
        }
    }
    val texts = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (!granted) error = "Camera access is needed only to scan codes and text."
    }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            barcodes.close()
            texts.close()
        }
    }

    LaunchedEffect(hasCamera, lifecycleOwner) {
        if (!hasCamera) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                // Small or dense barcodes need more than the default 640x480 analysis frames.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { frame ->
                    val image = frame.image
                    if (!scanning || image == null) {
                        frame.close()
                        return@setAnalyzer
                    }
                    barcodes.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { found ->
                            // Several codes can be in view (a QR on the screen, a barcode on a can): Local Drive's own
                            // pairing code wins over everything else.
                            val codes = found.filter { !it.rawValue.isNullOrBlank() }
                            val pairing = codes.firstNotNullOfOrNull { c -> SyncRules.parseQr(c.rawValue!!)?.let { c.rawValue!! to it } }
                            if (onCode != null) { // Sync's pairing mode: every other code is ignored
                                if (scanning && pairing != null && onCode(pairing.first)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return@addOnSuccessListener
                            }
                            val code = codes.firstOrNull() ?: return@addOnSuccessListener
                            val value = pairing?.first ?: code.rawValue!!
                            if (!scanning || (value == ignoredValue && SystemClock.elapsedRealtime() < ignoredUntil)) return@addOnSuccessListener
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (pairing != null) { pair(pairing.first, pairing.second); return@addOnSuccessListener }
                            result = codeResult(value, code.url?.url?.takeIf { code.valueType == Barcode.TYPE_URL })
                        }
                        .addOnCompleteListener { frame.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                capture = imageCapture
            }.onFailure { error = it.message ?: "Could not start the camera." }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }

    fun readText() {
        val imageCapture = capture ?: return
        reading = true
        error = null
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) { image.close(); reading = false; return }
                texts.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                    .addOnSuccessListener { text ->
                        val all = text.textBlocks.joinToString("\n\n") { it.text }
                        if (all.isBlank()) error = "No text found. Move closer and try again."
                        else result = CodeResult.Text(all, PaymentCodes.findRf(all))
                    }
                    .addOnFailureListener { error = it.message ?: "Could not read text." }
                    .addOnCompleteListener { image.close(); reading = false }
            }
            override fun onError(exception: ImageCaptureException) {
                reading = false
                error = exception.message ?: "Could not capture the page."
            }
        })
    }
    fun dismiss() {
        (result as? CodeResult.Code)?.value?.let { ignoredValue = it }
        (result as? CodeResult.Payment)?.code?.let { ignoredValue = it }
        (result as? CodeResult.Pairing)?.raw?.let { ignoredValue = it }
        ignoredUntil = SystemClock.elapsedRealtime() + 3_000
        result = null
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCamera) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (hasCamera && mode == CodeMode.Codes) Canvas(Modifier.fillMaxSize()) {
            val side = size.minDimension * 0.66f
            drawRoundRect(
                Color.White, topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2.4f),
                size = androidx.compose.ui.geometry.Size(side, side), cornerRadius = CornerRadius(28.dp.toPx()), style = Stroke(3.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
            }
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                Text(
                    when {
                        onCode != null -> "Point at the pairing code on the computer"
                        mode == CodeMode.Codes -> "Point at a QR code or barcode"
                        else -> "Frame the text, then tap the shutter"
                    },
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
        Surface(
            color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        ) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasCamera) Button(onClick = { requestCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                else if (mode == CodeMode.Text) Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    if (reading) CircularProgressIndicator()
                    else Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(68.dp).clickable(enabled = capture != null, onClick = ::readText)) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScanPill("Codes", R.drawable.ic_qr_code, mode == CodeMode.Codes) { mode = CodeMode.Codes; error = null }
                    ScanPill("Text", R.drawable.ic_text_scan, mode == CodeMode.Text) { mode = CodeMode.Text; error = null }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    result?.let { current ->
        ModalBottomSheet(onDismissRequest = ::dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    is CodeResult.Payment -> PaymentCodeBlock(context, current.code, large = true)
                    is CodeResult.Code -> {
                        Text(if (current.url != null) "Link" else "Code", style = MaterialTheme.typography.titleLarge)
                        SelectionContainer { Text(current.value, style = MaterialTheme.typography.bodyLarge, maxLines = 8) }
                        current.url?.let { url -> DriveWideAction(R.drawable.ic_open_in_browser, "Open in browser") { openLink(context, url); dismiss() } }
                        DriveWideAction(R.drawable.ic_copy, "Copy") { copyText(context, current.value); dismiss() }
                        DriveWideAction(R.drawable.ic_share, "Share") { shareText(context, current.value) }
                    }
                    is CodeResult.Pairing -> {
                        Text("Local Drive computer", style = MaterialTheme.typography.titleLarge)
                        Text(current.computer, style = MaterialTheme.typography.titleMedium)
                        Text(pairStatus.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        if (pairStatus == "Pairing…") CircularProgressIndicator()
                    }
                    is CodeResult.Text -> {
                        current.payment?.let { PaymentCodeBlock(context, it, large = false) }
                        Text("Text", style = MaterialTheme.typography.titleLarge)
                        Text("Long-press to select part of it.", style = MaterialTheme.typography.bodySmall)
                        SelectionContainer(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            Text(current.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        DriveWideAction(R.drawable.ic_copy, "Copy all") { copyText(context, current.text); dismiss() }
                        DriveWideAction(R.drawable.ic_share, "Share") { shareText(context, current.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCodeBlock(context: Context, code: String, large: Boolean) {
    Text("Payment code (RF)", style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
    SelectionContainer {
        Text(PaymentCodes.format(code), fontFamily = FontFamily.Monospace, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
    }
    DriveWideAction(R.drawable.ic_copy, "Copy payment code") { copyText(context, code) }
    if (large) DriveWideAction(R.drawable.ic_share, "Share payment code") { shareText(context, code) }
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Scanned text", text))
    // Android 13+ shows its own copied confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "No browser can open this link.", Toast.LENGTH_SHORT).show() }
}
