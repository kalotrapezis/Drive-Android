package com.kalotrapezis.drive

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import android.media.MediaMetadataRetriever
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private const val DRIVE_PATH = "/sdcard/Drive/"
private const val PHOTO_SETUP_COMPLETED = "photo_setup_completed"

private fun driveRoot(): File = File(Environment.getExternalStorageDirectory(), "Drive")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocalDriveApp() }
    }
}

private fun authenticateVault(activity: Activity, success: () -> Unit, failure: (String) -> Unit) {
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val manager = activity.getSystemService(BiometricManager::class.java)
    if (manager.canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
        failure("Set up a screen lock or biometric unlock in Android settings first.")
        return
    }
    BiometricPrompt.Builder(activity)
        .setTitle("Unlock Hidden")
        .setSubtitle("Use biometrics or your phone screen lock")
        .setAllowedAuthenticators(allowed)
        .build()
        .authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = success()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) failure(errString.toString())
            }
        })
}

internal data class Entry(
    val name: String,
    val detail: String,
    val relativePath: String? = null,
    val contentUri: Uri? = null,
    val takenMillis: Long = 0,
    val sizeBytes: Long = 0,
    val photoKey: String = "",
    val isVideo: Boolean = false,
)
private sealed interface ListState {
    data object Idle : ListState
    data object Loading : ListState
    data class Items(val entries: List<Entry>) : ListState
    data class Error(val message: String) : ListState
}
private sealed interface DriveListState {
    data object Idle : DriveListState
    data object Loading : DriveListState
    data class Items(val folder: String, val entries: List<DriveItem>) : DriveListState
    data class Error(val message: String) : DriveListState
}

@Composable
private fun LocalDriveApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(context) { context.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf(if (preferences.getBoolean(PHOTO_SETUP_COMPLETED, false)) Screen.Home else Screen.PhotoSetup) }
    var photosPane by remember { mutableStateOf(PhotosPane.Timeline) }
    val currentScreen by rememberUpdatedState(screen)
    var driveState by remember { mutableStateOf<DriveListState>(DriveListState.Idle) }
    var driveFolder by remember { mutableStateOf("") }
    var drivePane by remember { mutableStateOf(DrivePane.Home) }
    val currentDrivePane by rememberUpdatedState(drivePane)
    val driveRecents = remember(context) { DriveRecents(context) }
    val driveOpeners = remember(context) { DriveOpeners(context) }
    var recentsVersion by remember { mutableStateOf(0) }
    var photosState by remember { mutableStateOf<ListState>(ListState.Idle) }
    var photoFilter by remember { mutableStateOf<PhotoFilter>(PhotoFilter.Timeline) }
    val photoMetadata = remember(context) { PhotoMetadataStore(context.applicationContext) }
    var metadataVersion by remember { mutableStateOf(0) }

    fun hasAllFilesAccess() = Environment.isExternalStorageManager()
    fun loadDrive(folder: String = driveFolder) {
        if (!hasAllFilesAccess()) {
            driveState = DriveListState.Error("All files access is required to use $DRIVE_PATH.")
            return
        }
        driveState = DriveListState.Loading
        Thread {
            val result = runCatching {
                val root = driveRoot()
                check((root.exists() && root.isDirectory) || root.mkdirs()) { "Could not create Drive." }
                if (folder == "Trash" && !File(root, folder).exists()) emptyList() else listDriveFolder(root, folder)
            }
            (context as MainActivity).runOnUiThread {
                driveState = result.fold(
                    onSuccess = { DriveListState.Items(folder, it) },
                    onFailure = { DriveListState.Error("Cannot read $DRIVE_PATH: ${it.message ?: "storage unavailable"}") },
                )
            }
        }.start()
    }
    fun hasVisualAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 34 ->
            (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) ||
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        Build.VERSION.SDK_INT == 33 ->
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        else -> context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    fun photoPermission(): String = when {
        Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED -> "Full photo and video access"
        Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED -> "Selected photos and videos only"
        Build.VERSION.SDK_INT == 33 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED -> "Photo and video access granted"
        Build.VERSION.SDK_INT <= 32 && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> "Photo and video access granted"
        else -> "Photo and video access not granted"
    }
    fun canReadPhotos() = hasVisualAccess()
    fun photoPermissions() = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT == 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun loadPhotos() {
        if (!canReadPhotos()) { photosState = ListState.Error("Allow photos and videos to view DCIM and Screenshots."); return }
        photosState = ListState.Loading
        Thread {
            val result = runCatching { listPhotos(context) }
            (context as MainActivity).runOnUiThread {
                photosState = result.fold(
                    onSuccess = { ListState.Items(it) },
                    onFailure = { ListState.Error("Cannot read photos: ${it.message ?: "permission lost"}") },
                )
            }
        }.start()
    }
    val requestPhotos = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadPhotos() }
    val requestTrash = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) loadPhotos()
    }
    fun finishPhotoSetup(allowPhotos: Boolean) {
        preferences.edit().putBoolean(PHOTO_SETUP_COMPLETED, true).apply()
        screen = Screen.Home
        if (allowPhotos && !canReadPhotos()) requestPhotos.launch(photoPermissions())
    }
    fun movePhotosToTrash(uris: Set<Uri>) {
        if (uris.isEmpty()) return
        runCatching { MediaStore.createTrashRequest(context.contentResolver, uris.toList(), true) }
            .onSuccess { requestTrash.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            .onFailure { photosState = ListState.Error("Cannot move photos to trash: ${it.message ?: "request failed"}") }
    }
    fun selectedEntries(uris: Set<Uri>) = (photosState as? ListState.Items)?.entries.orEmpty().filter { it.contentUri in uris }
    fun metadataAction(action: () -> Unit): String? = runCatching(action).fold(
        onSuccess = { metadataVersion++; null },
        onFailure = { it.message ?: "Could not save photo metadata." },
    )
    fun openDriveFile(relative: String, forceChooser: Boolean): String? = runCatching {
        val file = DriveRules.file(driveRoot(), relative)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .also { it.clipData = ClipData.newRawUri(file.name, uri) }
        fun chooser() {
            val callback = Intent(context, DriveOpenerReceiver::class.java)
                .setAction(FILE_OPENER_CHOSEN)
                .setData(Uri.Builder().scheme("localdrive").authority("opener").appendPath(relative).build())
                .putExtra(FILE_OPENER_PATH, relative)
            val pending = PendingIntent.getBroadcast(
                context,
                relative.hashCode(),
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            context.startActivity(Intent.createChooser(viewIntent, "Open with", pending.intentSender))
        }
        val opener = driveOpeners.component(relative)
        if (forceChooser || opener == null) chooser() else runCatching {
            context.startActivity(Intent(viewIntent).setComponent(opener))
        }.getOrElse {
            driveOpeners.clear(relative)
            chooser()
        }
        driveRecents.record(driveRoot(), relative, System.currentTimeMillis())
        recentsVersion++
    }.fold(onSuccess = { null }, onFailure = { it.message ?: "Could not open this file. Install an app that supports it." })
    BackHandler(enabled = screen != Screen.Home && screen != Screen.PhotoSetup) {
        when (screen) {
            Screen.Photos -> screen = Screen.Home
            Screen.Drive -> when {
                drivePane == DrivePane.Files && driveFolder.isNotEmpty() -> {
                    driveFolder = DriveRules.parent(driveFolder)
                    loadDrive()
                }
                drivePane == DrivePane.Files -> drivePane = DrivePane.Home
                else -> screen = Screen.Home
            }
            else -> screen = Screen.Home
        }
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) when (currentScreen) {
                Screen.Drive -> if (currentDrivePane == DrivePane.Files) loadDrive()
                Screen.Photos -> loadPhotos()
                Screen.PhotoSetup -> Unit
                Screen.Home -> Unit
                Screen.Sync -> Unit
                Screen.Settings -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val darkTheme = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.PhotoSetup -> PhotoSetup({ finishPhotoSetup(true) }, { finishPhotoSetup(false) })
                Screen.Home -> Home(
                    openPhotos = { screen = Screen.Photos; photosPane = PhotosPane.Timeline; photoFilter = PhotoFilter.Timeline; loadPhotos() },
                    openScreenshots = { screen = Screen.Photos; photosPane = PhotosPane.Timeline; photoFilter = PhotoFilter.Screenshots; loadPhotos() },
                    openDocuments = { screen = Screen.Photos; photosPane = PhotosPane.Timeline; photoFilter = PhotoFilter.Documents; loadPhotos() },
                    openFiles = { screen = Screen.Drive; drivePane = DrivePane.Files; driveFolder = ""; loadDrive("") },
                    openFavorites = { screen = Screen.Drive; drivePane = DrivePane.Favorites; driveFolder = "" },
                    openRecent = { screen = Screen.Drive; drivePane = DrivePane.Home; driveFolder = "" },
                    openSync = { screen = Screen.Sync },
                    openSettings = { screen = Screen.Settings },
                )
                Screen.Drive -> DriveTab(
                    home = { screen = Screen.Home },
                    pane = drivePane,
                    setPane = { drivePane = it },
                    folder = driveFolder,
                    state = driveState,
                    hasAccess = hasAllFilesAccess(),
                    grant = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                    },
                    refresh = ::loadDrive,
                    openFolder = { relative -> driveFolder = relative; drivePane = DrivePane.Files; loadDrive(relative) },
                    goParent = {
                        if (driveFolder.isEmpty()) drivePane = DrivePane.Home else {
                            driveFolder = DriveRules.parent(driveFolder)
                            loadDrive()
                        }
                    },
                    recents = { driveRecents.read(driveRoot()) },
                    recentsVersion = recentsVersion,
                    openFile = ::openDriveFile,
                )
                Screen.Photos -> PhotoTab({ screen = Screen.Home }, photosPane, { photosPane = it }, photoFilter, { photoFilter = it }, photoPermission(), photosState, photoMetadata, metadataVersion, {
                    requestPhotos.launch(photoPermissions())
                }, ::loadPhotos, ::movePhotosToTrash,
                    { uris, favorite -> metadataAction { photoMetadata.setFavorite(selectedEntries(uris).map { it.photoKey }, favorite) } },
                    { collectionId, uris -> metadataAction { photoMetadata.addToCollection(collectionId, selectedEntries(uris).map { it.photoKey }) } },
                    { collectionId, uris -> metadataAction { photoMetadata.removeFromCollection(collectionId, selectedEntries(uris).map { it.photoKey }) } },
                    { name -> runCatching { photoMetadata.createCollection(name) }.onSuccess { metadataVersion++ } },
                    { collectionId -> metadataAction { photoMetadata.deleteCollection(collectionId) } },
                )
                Screen.Sync -> SyncTab(back = { screen = Screen.Home }, openSettings = { screen = Screen.Settings })
                Screen.Settings -> SettingsTab(
                    back = { screen = Screen.Home },
                    metadataStore = photoMetadata,
                    photoPermission = photoPermission(),
                    hasDriveAccess = hasAllFilesAccess(),
                    managePhotos = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    },
                    manageDrive = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                    },
                    openSync = { screen = Screen.Sync },
                )
            }
        }
        }
    }
}

private enum class Screen { PhotoSetup, Home, Drive, Photos, Sync, Settings }
private enum class DrivePane { Home, Favorites, Files }
private enum class DriveSort { Name, Modified }
private enum class PhotosPane { Timeline, Collections }
private sealed interface PhotoFilter {
    data object Timeline : PhotoFilter
    data object Favorites : PhotoFilter
    data object People : PhotoFilter
    data object Documents : PhotoFilter
    data object Screenshots : PhotoFilter
    data object Videos : PhotoFilter
    data object Review : PhotoFilter
    data object Hidden : PhotoFilter
    data object Map : PhotoFilter
    data class Collection(val id: Long) : PhotoFilter
}

@Composable
private fun Home(
    openPhotos: () -> Unit,
    openScreenshots: () -> Unit,
    openDocuments: () -> Unit,
    openFiles: () -> Unit,
    openFavorites: () -> Unit,
    openRecent: () -> Unit,
    openSync: () -> Unit,
    openSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Local Drive",
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f), Offset(2f, 3f), 2f),
                ),
            )
        }
        item { HomeWideCard("Local Sync", "No trusted device connected", R.drawable.ic_sync, openSync) }
        item {
            HomeGroup(MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomePrimaryCard("Photos", R.drawable.ic_gallery, openPhotos, Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeCompactCard("Screenshots", R.drawable.ic_gallery, openScreenshots)
                        HomeCompactCard("Documents", R.drawable.ic_file, openDocuments)
                    }
                }
            }
        }
        item {
            HomeGroup(MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeFilesCard(openFiles, Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeCompactCard("Favorites", R.drawable.ic_favorite_border, openFavorites)
                        HomeCompactCard("Recent", R.drawable.ic_home, openRecent)
                    }
                }
            }
        }
        item { HomeWideCard("Settings", "Permissions and local storage", R.drawable.ic_settings, openSettings) }
    }
}

@Composable private fun HomeGroup(color: Color, content: @Composable () -> Unit) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = color,
    modifier = Modifier.fillMaxWidth(),
) { Box(Modifier.padding(10.dp)) { content() } }

@Composable private fun HomePrimaryCard(label: String, icon: Int, click: () -> Unit, modifier: Modifier = Modifier) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    modifier = modifier.height(156.dp).clickable(onClick = click),
) { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
    Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(38.dp))
    Text(label, style = MaterialTheme.typography.titleLarge)
} }

@Composable private fun HomeCompactCard(label: String, icon: Int, click: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    modifier = Modifier.fillMaxWidth().height(73.dp).clickable(onClick = click),
) { Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
} }

@Composable private fun HomeFilesCard(click: () -> Unit, modifier: Modifier = Modifier) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    modifier = modifier.height(156.dp).clickable(onClick = click),
) { Box(Modifier.fillMaxSize().padding(18.dp)) {
    Image(
        painter = painterResource(R.drawable.files_documents), contentDescription = null, contentScale = ContentScale.Fit,
        alpha = 0.82f, modifier = Modifier.align(Alignment.TopEnd).size(100.dp),
    )
    Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = "Files", modifier = Modifier.size(28.dp))
        Text("Files", style = MaterialTheme.typography.titleLarge)
    }
} }

@Composable private fun HomeWideCard(label: String, detail: String, icon: Int, click: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).clickable(onClick = click),
) { Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(28.dp))
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall)
    }
} }

@Composable
private fun SyncTab(back: () -> Unit, openSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(start = 16.dp, top = 160.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            SettingsCard("Connection") {
                Text("Not connected", style = MaterialTheme.typography.titleMedium)
                Text("Discovery and pairing are not enabled yet. This screen never claims that a device is online.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SettingsCard("Paired devices") {
                Text("No paired devices", style = MaterialTheme.typography.titleMedium)
                Text("When pairing is implemented, each card will show a device name, key fingerprint, capabilities and Last seen status.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SettingsCard("Fixed sync map") {
                Text("Files  ·  $DRIVE_PATH", style = MaterialTheme.typography.bodyMedium)
                Text("Camera  ·  DCIM/Camera", style = MaterialTheme.typography.bodyMedium)
                Text("Screenshots  ·  Pictures/Screenshots", style = MaterialTheme.typography.bodyMedium)
                Text("Desktop targets are LocalDrive/Drive and LocalDrive/Photos after a trusted device is added.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsCard("Transfer activity") {
                Text("No sync actions yet", style = MaterialTheme.typography.titleMedium)
                Text("Pairing, previews and verified transfers are the next sync slice. Nothing is copied or deleted from this page.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Button(onClick = openSettings, modifier = Modifier.fillMaxWidth()) { Text("Open settings") } }
            item { Box(Modifier.heightIn(min = 32.dp)) }
        }
        ModuleHeader("Sync", back, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun SettingsTab(
    back: () -> Unit,
    metadataStore: PhotoMetadataStore,
    photoPermission: String,
    hasDriveAccess: Boolean,
    managePhotos: () -> Unit,
    manageDrive: () -> Unit,
    openSync: () -> Unit,
) {
    var searchQuality by remember { mutableStateOf(metadataStore.searchQuality()) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(start = 16.dp, top = 160.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            SettingsCard("Storage locations") {
                Text("Drive workspace", style = MaterialTheme.typography.titleMedium)
                Text(DRIVE_PATH, style = MaterialTheme.typography.bodyMedium)
                Text("Camera photos stay in DCIM/Camera. Screenshots stay in Pictures/Screenshots. These paths are fixed so sync never needs to guess or duplicate photos.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsCard("Permissions") {
                Text("Photos: $photoPermission", style = MaterialTheme.typography.bodyMedium)
                Text("Drive: ${if (hasDriveAccess) "All files access granted" else "All files access not granted"}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = managePhotos) { Text("Photos") }
                    Button(onClick = manageDrive) { Text("Drive") }
                }
            }
        }
        item {
            SettingsCard("Gallery") {
                Text("Search quality", style = MaterialTheme.typography.titleMedium)
                SearchQualityOption("Fast", "Default · quick local labels", searchQuality == PhotoSearchQuality.Fast) {
                    searchQuality = PhotoSearchQuality.Fast
                    metadataStore.setSearchQuality(searchQuality)
                }
                SearchQualityOption("Advanced", "Fast labels plus experimental Scene tags; slower", searchQuality == PhotoSearchQuality.Advanced) {
                    searchQuality = PhotoSearchQuality.Advanced
                    metadataStore.setSearchQuality(searchQuality)
                }
                Text("Changing this re-analyzes photos the next time you open People or Documents. Advanced keeps the Fast tags too.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsCard("Connections and devices") {
                Text("No device is paired yet.", style = MaterialTheme.typography.bodyMedium)
                Text("A future trusted device uses a device ID and public-key fingerprint. IP and port are discovered again after a network change; a MAC address is not used as identity.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = openSync) { Text("Open Sync") }
            }
        }
        item {
            SettingsCard("Appearance") {
                Text("Follow system", style = MaterialTheme.typography.titleMedium)
                Text("Local Drive follows the Android light/dark theme and dynamic colour where Android provides it.", style = MaterialTheme.typography.bodyMedium)
            }
        }
            item { Box(Modifier.heightIn(min = 32.dp)) }
        }
        ModuleHeader("Settings", back, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun SearchQualityOption(title: String, detail: String, selected: Boolean, choose: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
    modifier = Modifier.fillMaxWidth().clickable(onClick = choose),
) {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = choose, colors = RadioButtonDefaults.colors(selectedColor = driveNavigationSelectedColor()))
        Column(Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ModuleHeader(title: String, back: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = islandColor(), contentColor = islandContentColor(), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back to Home") }
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 1.dp, max = 1.dp).background(islandContentColor().copy(alpha = 0.35f)))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) = Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun PhotoSetup(allow: () -> Unit, skip: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Gallery setup", style = MaterialTheme.typography.headlineMedium)
        Text("Local Drive needs permission to show your real Camera and Screenshots photos and videos.")
        Text("Photos stay where they are. The app does not choose a folder or copy them into LocalDrive.", style = MaterialTheme.typography.bodyMedium)
        Text("You can change this later from the Gallery pull-up tools.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = allow, modifier = Modifier.fillMaxWidth()) { Text("Yes, allow photos") }
        Button(onClick = skip, modifier = Modifier.fillMaxWidth()) { Text("No, not now") }
    }
}

@Composable
private fun DriveTab(
    home: () -> Unit,
    pane: DrivePane,
    setPane: (DrivePane) -> Unit,
    folder: String,
    state: DriveListState,
    hasAccess: Boolean,
    grant: () -> Unit,
    refresh: () -> Unit,
    openFolder: (String) -> Unit,
    goParent: () -> Unit,
    recents: () -> List<DriveRecent>,
    recentsVersion: Int,
    openFile: (String, Boolean) -> String?,
) {
    val context = LocalContext.current
    val root = remember { driveRoot() }
    val metadata = remember(context) { DriveMetadata(context) }
    val driveOpeners = remember(context) { DriveOpeners(context) }
    var grid by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(DriveSort.Name) }
    var toolsOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var emptyTrashConfirm by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var metadataVersion by remember { mutableStateOf(0) }
    val recentItems = remember(recentsVersion, hasAccess) { if (hasAccess) recents() else emptyList() }
    val favoriteItems = remember(metadataVersion, hasAccess) { if (hasAccess) metadata.favorites(root) else emptyList() }
    val filtering = searchQuery.isNotBlank() || selectedTag != null
    val searchItems by produceState(emptyList<DriveItem>(), searchQuery, selectedTag, metadataVersion, state, hasAccess) {
        value = if (!hasAccess || !filtering) emptyList() else withContext(Dispatchers.IO) {
            runCatching {
                DriveRules.allItems(root).filter { item ->
                    val itemTags = metadata.tags(item.relativePath)
                    (selectedTag == null || selectedTag in itemTags) &&
                        (searchQuery.isBlank() || item.file.name.contains(searchQuery.trim(), ignoreCase = true) || itemTags.any { it.contains(searchQuery.trim(), ignoreCase = true) })
                }
            }.getOrDefault(emptyList())
        }
    }
    val spaceUsage by produceState<DriveSpaceUsage?>(null, toolsOpen, metadataVersion, state) {
        value = if (toolsOpen && hasAccess) withContext(Dispatchers.IO) { runCatching { DriveRules.spaceUsage(root) }.getOrNull() } else null
    }
    fun perform(item: DriveItem, action: DriveItemAction) {
        Thread {
            val result = runCatching {
                when (action) {
                    is DriveItemAction.Rename -> DriveRules.rename(root, item.relativePath, action.name).also { metadata.rewritePath(item.relativePath, it); driveOpeners.rewritePath(item.relativePath, it) }
                    is DriveItemAction.Copy -> DriveRules.copy(root, item.relativePath, action.destination)
                    is DriveItemAction.Move -> DriveRules.move(root, item.relativePath, action.destination).also { metadata.rewritePath(item.relativePath, it); driveOpeners.rewritePath(item.relativePath, it) }
                    is DriveItemAction.Favorite -> metadata.setFavorite(item.relativePath, action.add)
                    is DriveItemAction.Color -> metadata.setColor(item.relativePath, action.color)
                    is DriveItemAction.Tags -> metadata.setTags(item.relativePath, action.names)
                    DriveItemAction.Trash -> DriveRules.moveToTrash(root, item.relativePath).also { metadata.rewritePath(item.relativePath, it); driveOpeners.rewritePath(item.relativePath, it) }
                }
            }
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { metadataVersion++; refresh() }.onFailure { error = it.message ?: "Could not change this item." }
            }
        }.start()
    }
    fun createTag(name: String): String? = runCatching { metadata.createTag(name) }.fold(
        onSuccess = { metadataVersion++; null },
        onFailure = { it.message ?: "Could not create this tag." },
    )
    fun emptyTrash() {
        Thread {
            val result = runCatching { DriveRules.emptyTrash(root) }
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { metadataVersion++; refresh() }.onFailure { error = it.message ?: "Could not empty Trash." }
            }
        }.start()
    }
    Box(Modifier.fillMaxSize()) {
        when {
            !hasAccess -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Drive needs all files access", style = MaterialTheme.typography.headlineSmall)
                Text("This reads only Drive in main storage. It is needed to browse and open your local Drive files.")
                Button(onClick = grant) { Text("Allow all files access") }
            }
            filtering -> DriveFiles("", DriveListState.Items("", searchItems), grid, sort, metadata, { grid = !grid }, { relative -> searchQuery = ""; selectedTag = null; openFolder(relative) }, { relative -> error = openFile(relative, false) }, { relative -> error = openFile(relative, true) }, { searchQuery = ""; selectedTag = null }, refresh, ::perform, title = selectedTag?.let { "#$it" } ?: "Search files", emptyMessage = "No matching Drive items.")
            pane == DrivePane.Home -> DriveHome(root, recentItems, metadata, metadataVersion, home, { relative -> error = openFile(relative, false) }, { relative -> error = openFile(relative, true) }, { setPane(DrivePane.Files); refresh() }, ::perform)
            pane == DrivePane.Favorites -> DriveFavorites(favoriteItems, metadata, home, { relative -> error = openFile(relative, false) }, { relative -> error = openFile(relative, true) }, openFolder, ::perform)
            pane == DrivePane.Files -> DriveFiles(folder, state, grid, sort, metadata, { grid = !grid }, openFolder, { relative -> error = openFile(relative, false) }, { relative -> error = openFile(relative, true) }, goParent, refresh, ::perform, emptyTrash = if (folder == "Trash") { { emptyTrashConfirm = true } } else null)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DriveBottomBar(
                pane = pane,
                home = { searchQuery = ""; selectedTag = null; setPane(DrivePane.Home) },
                favorites = { searchQuery = ""; selectedTag = null; setPane(DrivePane.Favorites) },
                files = { searchQuery = ""; selectedTag = null; setPane(DrivePane.Files); refresh() },
                expand = { toolsOpen = true },
                visible = !toolsOpen,
            )
            SearchButton(visible = !toolsOpen, description = "Search files") {
                setPane(DrivePane.Files)
                searchOpen = true
            }
        }
        error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp, start = 20.dp, end = 20.dp)) {
                Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
    if (searchOpen) SearchSheet(searchQuery, { searchQuery = it }, { searchQuery = "" }, { searchOpen = false }, title = "Search files", hint = "File name or tag")
    if (toolsOpen) DriveToolsSheet(
        sort = sort,
        setSort = { sort = it },
        openTags = { toolsOpen = false; tagsOpen = true },
        openTrash = { selectedTag = null; searchQuery = ""; setPane(DrivePane.Files); toolsOpen = false; openFolder("Trash") },
        usage = spaceUsage,
        dismiss = { toolsOpen = false },
    )
    if (tagsOpen) DriveTagsSheet(
        tags = metadata.allTags(root),
        selectedTag = selectedTag,
        chooseTag = { selectedTag = it; searchQuery = ""; setPane(DrivePane.Files); tagsOpen = false },
        createTag = ::createTag,
        dismiss = { tagsOpen = false },
    )
    if (emptyTrashConfirm) EmptyTrashSheet(
        dismiss = { emptyTrashConfirm = false },
        emptyTrash = { emptyTrashConfirm = false; emptyTrash() },
    )
}

@Composable
private fun DriveFavorites(items: List<DriveItem>, metadata: DriveMetadata, home: () -> Unit, openFile: (String) -> Unit, openWith: (String) -> Unit, openFolder: (String) -> Unit, perform: (DriveItem, DriveItemAction) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onBackground
    var moreItem by remember { mutableStateOf<DriveItem?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilesPageHeader("Favorites", R.drawable.ic_favorite_border, home)
        if (items.isEmpty()) {
            Icon(painterResource(R.drawable.ic_favorite_border), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Text("No files in Favorites yet.", color = textColor, style = MaterialTheme.typography.titleMedium)
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.relativePath }) { item ->
                DriveRowItem(item, metadata.color(item.relativePath), metadata.tags(item.relativePath), openFolder, openFile) { moreItem = item }
            }
            item { Box(Modifier.heightIn(min = 92.dp)) }
        }
    }
    moreItem?.let { item -> DriveItemMoreSheet(item, metadata, { if (item.isDirectory) openFolder(item.relativePath) else openFile(item.relativePath) }, { openWith(item.relativePath) }, perform, { moreItem = null }) }
}

@Composable
private fun DriveHome(root: File, recents: List<DriveRecent>, metadata: DriveMetadata, metadataVersion: Int, home: () -> Unit, openFile: (String) -> Unit, openWith: (String) -> Unit, openFiles: () -> Unit, perform: (DriveItem, DriveItemAction) -> Unit) {
    var moreItem by remember { mutableStateOf<DriveItem?>(null) }
    val items = remember(recents, metadataVersion) { recents.mapNotNull { recent -> runCatching { DriveRules.item(root, recent.relativePath) }.getOrNull()?.let { DriveItem(it, recent.relativePath, false) } } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FilesPageHeader("Recent files", R.drawable.ic_home, home)
        }
        item { Box(Modifier.heightIn(min = 6.dp)) }
        if (items.isEmpty()) item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No opened files yet")
                    Text("Open a file from Files and it will appear here.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = openFiles) { Text("Browse files") }
                }
            }
        }
        items(items, key = { it.relativePath }) { item ->
            DriveRowItem(item, metadata.color(item.relativePath), metadata.tags(item.relativePath), {}, openFile) { moreItem = item }
        }
        item { Box(Modifier.heightIn(min = 92.dp)) }
    }
    moreItem?.let { item -> DriveItemMoreSheet(item, metadata, { openFile(item.relativePath) }, { openWith(item.relativePath) }, perform, { moreItem = null }) }
}

@Composable
private fun DriveFiles(
    folder: String,
    state: DriveListState,
    grid: Boolean,
    sort: DriveSort,
    metadata: DriveMetadata,
    toggleGrid: () -> Unit,
    openFolder: (String) -> Unit,
    openFile: (String) -> Unit,
    openWith: (String) -> Unit,
    goParent: () -> Unit,
    refresh: () -> Unit,
    perform: (DriveItem, DriveItemAction) -> Unit,
    title: String? = null,
    emptyMessage: String = "This folder is empty.",
    emptyTrash: (() -> Unit)? = null,
) {
    var moreItem by remember { mutableStateOf<DriveItem?>(null) }
    val isTrash = folder == "Trash"
    val topPadding = if (isTrash) 206.dp else 132.dp
    PullToRefreshBox(isRefreshing = state is DriveListState.Loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                DriveListState.Idle, DriveListState.Loading -> TimelineMessage("Loading files…")
                is DriveListState.Error -> TimelineMessage(state.message, MaterialTheme.colorScheme.error)
                is DriveListState.Items -> {
                    val entries = remember(state.entries, sort) {
                        when (sort) {
                            DriveSort.Name -> state.entries.sortedWith(compareBy<DriveItem>({ !it.isDirectory }, { it.file.name.lowercase(Locale.ROOT) }))
                            DriveSort.Modified -> state.entries.sortedWith(compareBy<DriveItem> { !it.isDirectory }.thenByDescending { it.file.lastModified() }.thenBy { it.file.name.lowercase(Locale.ROOT) })
                        }
                    }
                    if (state.entries.isEmpty()) TimelineMessage(emptyMessage)
                    else if (grid) LazyVerticalGrid(
                        GridCells.Fixed(2),
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = topPadding, end = 16.dp, bottom = 164.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(entries, key = { it.relativePath }) { item -> DriveGridItem(item, metadata.color(item.relativePath), metadata.tags(item.relativePath), openFolder, openFile) { moreItem = item } }
                    } else LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = topPadding, end = 16.dp, bottom = 164.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries, key = { it.relativePath }) { item -> DriveRowItem(item, metadata.color(item.relativePath), metadata.tags(item.relativePath), openFolder, openFile) { moreItem = item } }
                    }
                }
            }
            FilesPageHeader(
                title = title ?: if (folder.isEmpty()) "Files" else folder.substringAfterLast('/'),
                icon = if (isTrash) R.drawable.ic_delete else R.drawable.ic_folder,
                back = goParent,
                emptyTrash = emptyTrash,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp),
                trailing = {
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                        IconButton(onClick = toggleGrid) {
                            Icon(painterResource(if (grid) R.drawable.ic_view_list else R.drawable.ic_view_grid), contentDescription = if (grid) "Show list" else "Show grid")
                        }
                    }
                },
            )
            if (isTrash) TrashWarning(modifier = Modifier.align(Alignment.TopCenter).padding(start = 16.dp, top = 112.dp, end = 16.dp))
            moreItem?.let { item ->
                DriveItemMoreSheet(item, metadata, { if (item.isDirectory) openFolder(item.relativePath) else openFile(item.relativePath) }, { openWith(item.relativePath) }, perform, { moreItem = null })
            }
        }
    }
}

@Composable
internal fun FilesPageHeader(
    title: String,
    icon: Int,
    back: () -> Unit,
    emptyTrash: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emptyTrash != null) Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = CircleShape) {
            IconButton(onClick = emptyTrash) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Empty Trash") }
        }
        Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
            IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
        }
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable private fun TrashWarning(modifier: Modifier = Modifier) = Surface(
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth(),
) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp))
    Text("Items in Trash can be deleted permanently.", style = MaterialTheme.typography.bodySmall)
} }

private sealed interface DriveItemAction {
    data class Rename(val name: String) : DriveItemAction
    data class Copy(val destination: String) : DriveItemAction
    data class Move(val destination: String) : DriveItemAction
    data class Favorite(val add: Boolean) : DriveItemAction
    data class Color(val color: DriveFolderColor?) : DriveItemAction
    data class Tags(val names: Set<String>) : DriveItemAction
    data object Trash : DriveItemAction
}

private enum class DriveItemSheetPanel { Menu, Rename, Copy, Move, Tags, Color, Properties, Trash, Sync }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveItemMoreSheet(item: DriveItem, metadata: DriveMetadata, open: () -> Unit, openWith: () -> Unit, perform: (DriveItem, DriveItemAction) -> Unit, dismiss: () -> Unit) {
    var panel by remember(item.relativePath) { mutableStateOf(DriveItemSheetPanel.Menu) }
    var rename by remember(item.relativePath) { mutableStateOf(item.file.name) }
    var selectedTags by remember(item.relativePath) { mutableStateOf(metadata.tags(item.relativePath)) }
    var newTag by remember(item.relativePath) { mutableStateOf("") }
    var tagError by remember(item.relativePath) { mutableStateOf<String?>(null) }
    var destination by remember(item.relativePath) { mutableStateOf(DriveRules.parent(item.relativePath)) }
    val destinations = remember(item.relativePath) { DriveRules.destinations(driveRoot()) }
    fun submit(action: DriveItemAction) { perform(item, action); dismiss() }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (panel != DriveItemSheetPanel.Menu) IconButton(onClick = { panel = DriveItemSheetPanel.Menu }) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back to item actions") }
            Text(item.file.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (panel) {
                DriveItemSheetPanel.Menu -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DriveActionTile(R.drawable.ic_copy, "Copy") { panel = DriveItemSheetPanel.Copy }
                        DriveActionTile(R.drawable.ic_move, "Move") { panel = DriveItemSheetPanel.Move }
                        DriveActionTile(R.drawable.ic_edit, "Rename") { panel = DriveItemSheetPanel.Rename }
                        DriveActionTile(R.drawable.ic_info, "Properties") { panel = DriveItemSheetPanel.Properties }
                    }
                    if (!item.isDirectory) DriveWideAction(R.drawable.ic_open_with, "Open with…") { dismiss(); openWith() }
                    DriveWideAction(if (metadata.isFavorite(item.relativePath)) R.drawable.ic_remove_favorite else R.drawable.ic_favorite_border, if (metadata.isFavorite(item.relativePath)) "Remove from Favorites" else "Add to Favorites") { submit(DriveItemAction.Favorite(!metadata.isFavorite(item.relativePath))) }
                    DriveWideAction(R.drawable.ic_tag, "Tags") { panel = DriveItemSheetPanel.Tags }
                    if (item.isDirectory) DriveWideAction(R.drawable.ic_palette, "Change folder color") { panel = DriveItemSheetPanel.Color }
                    if (item.isDirectory) DriveWideAction(R.drawable.ic_sync, "Sync now") { panel = DriveItemSheetPanel.Sync }
                    if (item.relativePath != "Trash" && !item.relativePath.startsWith("Trash/")) DriveWideAction(R.drawable.ic_delete, "Move to Trash") { panel = DriveItemSheetPanel.Trash }
                }
                DriveItemSheetPanel.Rename -> {
                    Text("Rename this ${if (item.isDirectory) "folder" else "file"}.")
                    OutlinedTextField(rename, { rename = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New name") }, singleLine = true)
                    DriveWideAction(R.drawable.ic_check, "Rename") { submit(DriveItemAction.Rename(rename)) }
                }
                DriveItemSheetPanel.Copy, DriveItemSheetPanel.Move -> {
                    val copying = panel == DriveItemSheetPanel.Copy
                    Text(if (copying) "Choose where to copy this item." else "Choose where to move this item.")
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(destinations, key = { it.relativePath }) { target ->
                            Surface(shape = MaterialTheme.shapes.medium, color = if (target.relativePath == destination) driveNavigationSelectedColor() else islandColor(), contentColor = if (target.relativePath == destination) driveNavigationSelectedContentColor() else islandContentColor(), modifier = Modifier.fillMaxWidth().clickable { destination = target.relativePath }) {
                                Text(target.label, modifier = Modifier.padding(14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    DriveWideAction(if (copying) R.drawable.ic_copy else R.drawable.ic_move, if (copying) "Copy here" else "Move here") { submit(if (copying) DriveItemAction.Copy(destination) else DriveItemAction.Move(destination)) }
                }
                DriveItemSheetPanel.Tags -> {
                    val availableTags = metadata.allTags(driveRoot()).plus(selectedTags).distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
                    Text("Select existing tags, or create one for this item.")
                    if (availableTags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableTags, key = { it }) { tag ->
                            Surface(
                                color = if (tag in selectedTags) driveNavigationSelectedColor() else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (tag in selectedTags) driveNavigationSelectedContentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.clickable { selectedTags = selectedTags.let { tags -> if (tag in tags) tags - tag else tags + tag } },
                            ) { Text("#$tag", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) }
                        }
                    }
                    OutlinedTextField(newTag, { newTag = it; tagError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("New tag") }, singleLine = true, isError = tagError != null, supportingText = tagError?.let { { Text(it) } })
                    DriveWideAction(R.drawable.ic_tag, "Add tag") {
                        runCatching { DriveTagRules.name(newTag) }.onSuccess { tag -> selectedTags += tag; newTag = "" }.onFailure { tagError = it.message }
                    }
                    DriveWideAction(R.drawable.ic_check, "Save tags") { submit(DriveItemAction.Tags(selectedTags)) }
                }
                DriveItemSheetPanel.Color -> {
                    Text("Choose a folder color.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DriveFolderColor.entries.forEach { color -> DriveColorCircle(color, metadata.color(item.relativePath) == color) { submit(DriveItemAction.Color(color)) } }
                    }
                    DriveNoColorCircle(metadata.color(item.relativePath) == null) { submit(DriveItemAction.Color(null)) }
                }
                DriveItemSheetPanel.Properties -> {
                    Text(if (item.isDirectory) "Folder" else fileTypeLabel(item.file.name))
                    Text("Location: /sdcard/Drive/${item.relativePath}", style = MaterialTheme.typography.bodyMedium)
                    Text("${item.file.length()} bytes", style = MaterialTheme.typography.bodyMedium)
                    if (metadata.tags(item.relativePath).isNotEmpty()) Text("Tags: ${metadata.tags(item.relativePath).joinToString()}", style = MaterialTheme.typography.bodyMedium)
                    DriveWideAction(if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file, if (item.isDirectory) "Open folder" else "Open file") { dismiss(); open() }
                }
                DriveItemSheetPanel.Trash -> {
                    Text("Are you sure you want to move this item to Trash?")
                    DriveWideAction(R.drawable.ic_delete, "Move to Trash") { submit(DriveItemAction.Trash) }
                }
                DriveItemSheetPanel.Sync -> {
                    Text("No paired device is available yet. Sync will only start after a trusted device is connected.")
                    DriveWideAction(R.drawable.ic_check, "OK", dismiss)
                }
            }
        }
    }
}

private val DriveTrashAccent = Color(0xFFE3685F)

@Composable private fun RowScope.DriveActionTile(icon: Int, description: String, click: () -> Unit) = Surface(
    color = driveNavigationSelectedColor(), contentColor = driveNavigationSelectedContentColor(), shape = CircleShape,
    modifier = Modifier.weight(1f).height(64.dp),
) { IconButton(onClick = click, modifier = Modifier.fillMaxWidth().height(64.dp)) { Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(26.dp)) } }

@Composable private fun DriveWideAction(icon: Int, description: String, click: () -> Unit) = Surface(
    color = driveNavigationSelectedColor(), contentColor = driveNavigationSelectedContentColor(), shape = CircleShape,
    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = click),
) { Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(24.dp))
    Text(description, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
} }

@Composable private fun DriveColorCircle(color: DriveFolderColor, selected: Boolean, click: () -> Unit) = Surface(
    color = driveFolderAccent(color).copy(alpha = if (isSystemInDarkTheme()) 0.68f else 0.58f), contentColor = if (color == DriveFolderColor.Yellow) Color.Black else Color.White, shape = CircleShape,
    modifier = Modifier.size(44.dp).then(if (selected) Modifier.border(2.dp, islandContentColor(), CircleShape) else Modifier).clickable(onClick = click),
) { if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = color.name, modifier = Modifier.padding(10.dp)) }

@Composable private fun DriveNoColorCircle(selected: Boolean, click: () -> Unit) = Surface(
    color = driveNavigationSelectedColor(), contentColor = driveNavigationSelectedContentColor(), shape = CircleShape,
    modifier = Modifier.size(44.dp).then(if (selected) Modifier.border(2.dp, islandContentColor(), CircleShape) else Modifier).clickable(onClick = click),
) { Icon(painterResource(R.drawable.ic_no_color), contentDescription = "No folder color", modifier = Modifier.padding(10.dp), tint = DriveTrashAccent) }

@Composable private fun DriveRowItem(item: DriveItem, folderColor: DriveFolderColor?, tags: Set<String>, openFolder: (String) -> Unit, openFile: (String) -> Unit, more: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth().clickable { if (item.isDirectory) openFolder(item.relativePath) else openFile(item.relativePath) },
) {
    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(driveItemIcon(item)), contentDescription = driveItemIconDescription(item), tint = driveItemIconColor(item, folderColor), modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(item.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(driveItemTypeLabel(item) + if (tags.isEmpty()) "" else " · ${tags.joinToString(" ") { "#$it" }}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = more) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options") }
    }
}

@Composable private fun DriveGridItem(item: DriveItem, folderColor: DriveFolderColor?, tags: Set<String>, openFolder: (String) -> Unit, openFile: (String) -> Unit, more: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.aspectRatio(1f).clickable { if (item.isDirectory) openFolder(item.relativePath) else openFile(item.relativePath) },
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Icon(painterResource(driveItemIcon(item)), contentDescription = driveItemIconDescription(item), tint = driveItemIconColor(item, folderColor), modifier = Modifier.size(64.dp))
            IconButton(onClick = more) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options") }
        }
        Column {
            Text(item.file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(driveItemTypeLabel(item) + if (tags.isEmpty()) "" else " · ${tags.joinToString(" ") { "#$it" }}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun driveItemIcon(item: DriveItem): Int = when {
    item.relativePath == "Trash" -> R.drawable.ic_delete
    item.isDirectory -> R.drawable.ic_folder
    else -> R.drawable.ic_file
}

private fun driveItemIconDescription(item: DriveItem): String = if (item.relativePath == "Trash") "Trash" else if (item.isDirectory) "Folder" else "File"

private fun driveItemTypeLabel(item: DriveItem): String = if (item.relativePath == "Trash") "Trash" else if (item.isDirectory) "Folder" else fileTypeLabel(item.file.name)

@Composable private fun driveItemIconColor(item: DriveItem, folderColor: DriveFolderColor?): Color = if (item.relativePath == "Trash") DriveTrashAccent else driveItemColor(item, folderColor)

@Composable private fun DriveBottomBar(pane: DrivePane, home: () -> Unit, favorites: () -> Unit, files: () -> Unit, expand: () -> Unit, visible: Boolean, modifier: Modifier = Modifier) {
    IslandBottomBar(expand, visible, modifier) {
        IslandNavigationItem("Home", pane == DrivePane.Home, R.drawable.ic_home, home)
        IslandNavigationItem("Favorites", pane == DrivePane.Favorites, if (pane == DrivePane.Favorites) R.drawable.ic_favorite else R.drawable.ic_favorite_border, favorites)
        IslandNavigationItem("Files", pane == DrivePane.Files, R.drawable.ic_folder, files)
    }
}

@Composable private fun IslandBottomBar(expand: () -> Unit, visible: Boolean, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val dragThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    IslandVisibility(visible) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge, color = islandColor(), contentColor = islandContentColor(),
            modifier = modifier.pointerInput(expand) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = { if (dragged < -dragThreshold) expand() },
                )
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(48.dp).height(18.dp).clickable(onClick = expand), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(28.dp).height(3.dp).background(islandContentColor().copy(alpha = 0.65f), CircleShape))
                }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable private fun IslandVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)) + slideInVertically(tween(120)) { -it / 3 },
        exit = fadeOut(tween(80)),
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DriveToolsSheet(
    sort: DriveSort,
    setSort: (DriveSort) -> Unit,
    openTags: () -> Unit,
    openTrash: () -> Unit,
    usage: DriveSpaceUsage?,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Files tools", style = MaterialTheme.typography.titleLarge) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DriveActionTile(R.drawable.ic_tag, "Tags") { openTags() }
                DriveActionTile(R.drawable.ic_delete, "Trash") { openTrash() }
            } }
            item { Text("Sort Files", style = MaterialTheme.typography.titleMedium) }
            item { DriveWideAction(R.drawable.ic_sort_name, if (sort == DriveSort.Name) "Name ✓" else "Name") { setSort(DriveSort.Name); dismiss() } }
            item { DriveWideAction(R.drawable.ic_sort_time, if (sort == DriveSort.Modified) "Date modified ✓" else "Date modified") { setSort(DriveSort.Modified); dismiss() } }
            item { Text("Drive space by file type", style = MaterialTheme.typography.titleMedium) }
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(156.dp)) {
                        val stroke = 16.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2f, stroke / 2f)
                        val total = usage?.totalBytes ?: 0L
                        if (total == 0L) drawArc(Color.Gray.copy(alpha = 0.55f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        else {
                            var start = -90f
                            usage?.bytesByType.orEmpty().forEach { (type, bytes) ->
                                val sweep = bytes.toFloat() / total.toFloat() * 360f
                                drawArc(driveSpaceColor(type), start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                                start += sweep
                            }
                        }
                    }
                    Text(if (usage == null) "…" else Formatter.formatShortFileSize(context, usage.totalBytes), style = MaterialTheme.typography.titleMedium)
                }
            }
            usage?.bytesByType?.entries?.sortedByDescending { it.value }?.forEach { (type, bytes) ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = driveSpaceColor(type), shape = CircleShape, modifier = Modifier.size(12.dp)) {}
                            Text(type)
                        }
                        Text(Formatter.formatShortFileSize(context, bytes))
                    }
                }
            }
            item { Box(Modifier.height(28.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DriveTagsSheet(
    tags: List<String>,
    selectedTag: String?,
    chooseTag: (String?) -> Unit,
    createTag: (String) -> String?,
    dismiss: () -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    var tagError by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tags", style = MaterialTheme.typography.titleLarge)
            Text("Create a tag or select one to filter Files.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(newTag, { newTag = it; tagError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Create a tag") }, singleLine = true, isError = tagError != null, supportingText = tagError?.let { { Text(it) } })
            DriveWideAction(R.drawable.ic_tag, "Create tag") { tagError = createTag(newTag); if (tagError == null) newTag = "" }
            if (selectedTag != null) DriveWideAction(R.drawable.ic_tag, "Clear tag filter") { chooseTag(null) }
            if (tags.isEmpty()) Text("No tags yet. Tags can also be added from an item’s three-dot menu.", style = MaterialTheme.typography.bodyMedium)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags, key = { it }) { tag ->
                    Surface(
                        color = if (selectedTag == tag) driveNavigationSelectedColor() else islandColor(),
                        contentColor = if (selectedTag == tag) driveNavigationSelectedContentColor() else islandContentColor(),
                        shape = CircleShape,
                        modifier = Modifier.clickable { chooseTag(tag) },
                    ) { Text("#$tag", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EmptyTrashSheet(dismiss: () -> Unit, emptyTrash: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Empty Trash?", style = MaterialTheme.typography.titleLarge)
            Text("This permanently deletes every item in Trash and cannot be undone.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            DriveWideAction(R.drawable.ic_delete, "Empty Trash permanently", emptyTrash)
        }
    }
}

private fun driveSpaceColor(type: String): Color = when (type) {
    "Documents" -> Color(0xFF4F86E8)
    "Spreadsheets" -> Color(0xFF55A96B)
    "PDF" -> Color(0xFFE3685F)
    "Text" -> Color(0xFF9AA3A4)
    "Drawings" -> Color(0xFFAD68CF)
    "Pictures" -> Color(0xFF88BA6A)
    "Videos" -> Color(0xFFE887A8)
    else -> Color(0xFFB0B8B8)
}

@Composable private fun IslandNavigationItem(label: String, selected: Boolean, icon: Int, click: () -> Unit) = Surface(
    color = if (selected) driveNavigationSelectedColor() else Color.Transparent,
    contentColor = if (selected) driveNavigationSelectedContentColor() else islandContentColor(),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = click).semantics { this.selected = selected },
) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
        if (selected) Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun driveNavigationSelectedColor(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.72f)
@Composable private fun driveNavigationSelectedContentColor(): Color = if (isSystemInDarkTheme()) Color.Black else Color.White

private fun fileTypeLabel(name: String): String = name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)?.plus(" file") ?: "File"
@Composable private fun driveItemColor(item: DriveItem, folderColor: DriveFolderColor?): Color {
    if (item.isDirectory) return folderColor?.let(::driveFolderAccent) ?: MaterialTheme.colorScheme.onSurfaceVariant
    return when (item.file.extension.lowercase(Locale.ROOT)) {
        "doc", "docx", "odt" -> Color(0xFF4F86E8)
        "xls", "xlsx", "csv" -> Color(0xFF55A96B)
        "pdf" -> Color(0xFFE3685F)
        "txt", "md" -> Color(0xFF9AA3A4)
        "excalidraw" -> Color(0xFFAD68CF)
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif" -> Color(0xFF88BA6A)
        "mp4", "mov", "mkv", "avi", "webm" -> Color(0xFFE887A8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
private fun driveFolderAccent(color: DriveFolderColor): Color = when (color) {
    DriveFolderColor.Blue -> Color(0xFF4F86E8)
    DriveFolderColor.Green -> Color(0xFF55A96B)
    DriveFolderColor.Yellow -> Color(0xFFF2B544)
    DriveFolderColor.Red -> Color(0xFFE3685F)
    DriveFolderColor.Purple -> Color(0xFFAD68CF)
}
private fun formatOpenedTime(time: Long): String = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
@Composable internal fun islandColor(): Color = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.78f)
@Composable internal fun islandContentColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoTab(
    home: () -> Unit,
    pane: PhotosPane,
    setPane: (PhotosPane) -> Unit,
    filter: PhotoFilter,
    setFilter: (PhotoFilter) -> Unit,
    permission: String,
    state: ListState,
    metadataStore: PhotoMetadataStore,
    metadataVersion: Int,
    grant: () -> Unit,
    refresh: () -> Unit,
    moveToTrash: (Set<Uri>) -> Unit,
    setFavorite: (Set<Uri>, Boolean) -> String?,
    addToCollection: (Long, Set<Uri>) -> String?,
    removeFromCollection: (Long, Set<Uri>) -> String?,
    createCollection: (String) -> Result<PhotoCollection>,
    deleteCollection: (Long) -> String?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vault = remember(context) { SecureVault(context.applicationContext) }
    var vaultVersion by remember { mutableStateOf(0) }
    var vaultUnlocked by remember { mutableStateOf(false) }
    var stagedHide by remember { mutableStateOf<Pair<List<VaultItem>, List<Uri>>?>(null) }
    var openMapAfterPermission by remember { mutableStateOf(false) }
    var hasMediaLocationAccess by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var locationVersion by remember { mutableStateOf(0) }
    var mapFocusPhotoKey by remember { mutableStateOf<String?>(null) }
    var hideWarningOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var collectionToolsOpen by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(TimelineScale.Month) }
    var viewerUri by remember { mutableStateOf<Uri?>(null) }
    var viewingFaceGroup by remember { mutableStateOf<FaceGroup?>(null) }
    var selectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var returnToCollections by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var recentTags by remember { mutableStateOf(emptyList<String>()) }
    var collectionSheetFor by remember { mutableStateOf<Set<Uri>?>(null) }
    var newCollectionOpen by remember { mutableStateOf(false) }
    var selectedCollection by remember { mutableStateOf<PhotoCollection?>(null) }
    var collectionPendingDelete by remember { mutableStateOf<PhotoCollection?>(null) }
    var analysisConsentFor by remember { mutableStateOf<PhotoFilter?>(null) }
    var analysisRunning by remember { mutableStateOf(false) }
    var analysisPaused by remember { mutableStateOf(false) }
    val pauseRequested = remember { AtomicBoolean(false) }
    var analysisDone by remember { mutableStateOf(0) }
    var analysisTotal by remember { mutableStateOf(0) }
    var analysisVersion by remember { mutableStateOf(0) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val deleteHiddenOriginals = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val staged = stagedHide
        stagedHide = null
        if (staged != null && result.resultCode != Activity.RESULT_OK) Thread {
            vault.remove(staged.first)
            Handler(Looper.getMainLooper()).post { vaultVersion++; actionError = "Hidden was cancelled; the verified private copies were removed." }
        }.start()
        if (result.resultCode == Activity.RESULT_OK) {
            Thread {
                staged?.first?.let { metadataStore.forgetPhotos(it.map(VaultItem::photoKey)) }
                Handler(Looper.getMainLooper()).post {
                    vaultVersion++
                    analysisVersion++
                    selectedUris = emptySet()
                    refresh()
                }
            }.start()
        }
    }
    val requestMediaLocation = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasMediaLocationAccess = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasMediaLocationAccess && openMapAfterPermission) {
            setFilter(PhotoFilter.Map)
            returnToCollections = true
            setPane(PhotosPane.Timeline)
        } else if (!hasMediaLocationAccess) actionError = "Choose photos and allow their location metadata to use the map."
        openMapAfterPermission = false
    }
    fun requestPhotoLocations() = requestMediaLocation.launch(when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT == 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
    })
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, filter) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            vaultUnlocked = false
            if (filter == PhotoFilter.Hidden) {
                viewerUri = null
                selectedUris = emptySet()
            }
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val timelineState = rememberLazyGridState()
    val allEntries = (state as? ListState.Items)?.entries.orEmpty()
    val vaultItems = remember(vaultVersion) { vault.items() }
    LaunchedEffect(vaultItems) {
        withContext(Dispatchers.IO) { metadataStore.forgetPhotos(vaultItems.map(VaultItem::photoKey)) }
    }
    val vaultEntries = remember(vaultItems, vaultUnlocked) { if (!vaultUnlocked) emptyList() else vaultItems.map { item ->
        Entry(
            name = item.displayName,
            detail = "Hidden · ${item.sizeBytes} bytes",
            relativePath = item.relativePath,
            contentUri = vault.contentUri(item),
            takenMillis = item.takenMillis,
            sizeBytes = item.sizeBytes,
            photoKey = "vault:${item.id}",
            isVideo = item.isVideo,
        )
    } }
    val metadataRevision = metadataVersion + analysisVersion
    val metadata = remember(allEntries, metadataRevision) { metadataStore.states(allEntries.map { it.photoKey }) }
    val collections = remember(metadataRevision) { metadataStore.collections() }
    val collectionPreviews = remember(allEntries, collections, metadataRevision) {
        val entriesByKey = allEntries.associateBy(Entry::photoKey)
        collections.associate { collection -> collection.id to metadataStore.collectionKeys(collection.id).firstNotNullOfOrNull(entriesByKey::get) }
    }
    val documentKeys = remember(metadataRevision) { metadataStore.classifiedKeys("document") }
    val peopleKeys = remember(metadataRevision) { metadataStore.peopleKeys() }
    val labelsByPhoto = remember(allEntries, metadataRevision) { metadataStore.labelsByPhoto(allEntries.map(Entry::photoKey)) }
    val peopleNamesByPhoto = remember(allEntries, metadataRevision) { metadataStore.peopleNamesByPhoto(allEntries.map(Entry::photoKey)) }
    val locationsByPhoto = remember(allEntries, locationVersion) { metadataStore.locations(allEntries.map(Entry::photoKey)) }
    val faceGroups = remember(metadataRevision) { metadataStore.faceGroups() }
    val reviewKeys = remember(metadataRevision) { metadataStore.reviewKeys() }
    val pendingReview = remember(metadataRevision, filter) { if (filter == PhotoFilter.Review) metadataStore.nextReview() else null }
    var hideScreenshots by remember { mutableStateOf(metadataStore.hidesScreenshotsFromGallery()) }
    var hideDocuments by remember { mutableStateOf(metadataStore.hidesDocumentsFromGallery()) }
    var hidePeopleFromCollections by remember { mutableStateOf(metadataStore.hidesPeopleFromCollections()) }
    var hideDocumentsFromCollections by remember { mutableStateOf(metadataStore.hidesDocumentsFromCollections()) }
    val activeCollection = (filter as? PhotoFilter.Collection)?.let { chosen -> collections.firstOrNull { it.id == chosen.id } }
    val collectionKeys = remember(filter, metadataRevision) {
        (filter as? PhotoFilter.Collection)?.let { metadataStore.collectionKeys(it.id) }.orEmpty()
    }
    val entries = if (filter == PhotoFilter.Hidden) vaultEntries else allEntries.filter { entry -> when (filter) {
        PhotoFilter.Timeline -> PhotoMetadataRules.visibleInGallery(entry.isScreenshot(), entry.photoKey in documentKeys, hideScreenshots, hideDocuments)
        PhotoFilter.Favorites -> metadata[entry.photoKey].orDefault().favorite
        PhotoFilter.People -> entry.photoKey in peopleKeys
        PhotoFilter.Documents -> entry.photoKey in documentKeys
        PhotoFilter.Screenshots -> entry.isScreenshot()
        PhotoFilter.Videos -> entry.isVideo
        PhotoFilter.Review -> entry.photoKey in reviewKeys
        PhotoFilter.Map -> true
        PhotoFilter.Hidden -> false
        is PhotoFilter.Collection -> entry.photoKey in collectionKeys
    } }.filter { entry ->
        searchQuery.isBlank() || entry.name.contains(searchQuery, ignoreCase = true) || entry.relativePath.orEmpty().contains(searchQuery, ignoreCase = true) ||
            PhotoSearchRules.matches(searchQuery, labelsByPhoto[entry.photoKey].orEmpty() + peopleNamesByPhoto[entry.photoKey].orEmpty() + listOfNotNull(locationsByPhoto[entry.photoKey]?.placeName))
    }
    val suggestedTags = remember(entries, labelsByPhoto, recentTags) {
        (recentTags + PhotoSearchRules.frequentTags(entries.flatMap { labelsByPhoto[it.photoKey].orEmpty() })).distinct().take(5)
    }
    fun scanUnclassified() {
        if (analysisRunning) return
        val pending = allEntries.filter { !it.isVideo && it.contentUri != null && metadataStore.needsAnalysis(it.photoKey) }
        if (pending.isEmpty()) return
        analysisRunning = true
        analysisDone = 0
        analysisTotal = pending.size
        analysisError = null
        Thread {
            val result = runCatching {
                val quality = metadataStore.searchQuality()
                val modelVersion = "local-v7-${quality.name.lowercase(Locale.ROOT)}"
                PhotoClassifier(context, advancedSceneTags = quality == PhotoSearchQuality.Advanced).use { classifier ->
                    var skipped = 0
                    pending.forEachIndexed { index, entry ->
                        while (pauseRequested.get()) Thread.sleep(100)
                        entry.contentUri?.let { uri -> runCatching {
                            classifier.classify(uri, entry.takenMillis, analyzeFaces = !entry.isScreenshot() && entry.photoKey !in documentKeys).also { result ->
                                metadataStore.recordClassification(entry.photoKey, result.documentConfidence, result.faces, result.labels, modelVersion)
                            }
                        }.onFailure { skipped++ } }
                        Handler(Looper.getMainLooper()).post { analysisDone = index + 1 }
                    }
                    skipped
                }
            }
            Handler(Looper.getMainLooper()).post {
                analysisRunning = false
                analysisPaused = false
                pauseRequested.set(false)
                result.onSuccess { skipped ->
                    analysisVersion++
                    if (skipped > 0) analysisError = "Skipped $skipped photos that could not be read."
                }.onFailure { analysisError = it.message ?: "Could not analyze this gallery." }
            }
        }.start()
    }
    fun performHide(targets: Set<Uri>) {
        val selected = entries.filter { it.contentUri in targets && it.contentUri != null }
        if (selected.isEmpty()) return
        authenticateVault(context as Activity, success = {
            vaultUnlocked = true
            Thread {
                val result = runCatching { vault.stage(selected.map { entry -> VaultImport(
                    photoKey = entry.photoKey,
                    uri = requireNotNull(entry.contentUri),
                    displayName = entry.name,
                    mimeType = context.contentResolver.getType(entry.contentUri) ?: if (entry.isVideo) "video/*" else "image/*",
                    relativePath = entry.relativePath.orEmpty(),
                    takenMillis = entry.takenMillis,
                    isVideo = entry.isVideo,
                ) }) }
                Handler(Looper.getMainLooper()).post {
                    result.onSuccess { hidden ->
                        val uris = selected.mapNotNull(Entry::contentUri)
                        stagedHide = hidden to uris
                        runCatching { MediaStore.createDeleteRequest(context.contentResolver, uris) }
                            .onSuccess { deleteHiddenOriginals.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
                            .onFailure {
                                stagedHide = null
                                Thread { vault.remove(hidden) }.start()
                                actionError = "Could not request removal of the public originals."
                            }
                    }.onFailure { actionError = it.message ?: "Could not create verified private copies." }
                }
            }.start()
        }, failure = { actionError = it })
    }
    fun hideSelected(targets: Set<Uri> = selectedUris) {
        selectedUris = targets
        if (vault.warningAccepted()) performHide(targets) else hideWarningOpen = true
    }
    fun restoreHidden(targets: Set<Uri> = selectedUris) {
        val selected = vaultItems.filter { vault.contentUri(it) in targets }
        if (selected.isEmpty()) return
        Thread {
            val result = runCatching { vault.restore(selected) }
            Handler(Looper.getMainLooper()).post {
                result.onSuccess { vaultVersion++; selectedUris = emptySet(); refresh() }
                    .onFailure {
                        vaultVersion++
                        selectedUris = emptySet()
                        refresh()
                        actionError = it.message ?: "Could not restore all hidden media. Items already verified were restored safely."
                    }
            }
        }.start()
    }
    fun openCollection(chosen: PhotoFilter) {
        if (chosen == PhotoFilter.Hidden) {
            authenticateVault(context as Activity, success = {
                vaultUnlocked = true
                setFilter(chosen)
                returnToCollections = true
                setPane(PhotosPane.Timeline)
            }, failure = { actionError = it })
        } else if (chosen == PhotoFilter.Map) {
            mapFocusPhotoKey = null
            searchOpen = false
            toolsOpen = false
            collectionToolsOpen = false
            setFilter(chosen)
            returnToCollections = true
            setPane(PhotosPane.Timeline)
        } else if ((chosen == PhotoFilter.People || chosen == PhotoFilter.Documents) && !metadataStore.peopleAnalysisEnabled()) {
            analysisConsentFor = chosen
        } else {
            if (chosen == PhotoFilter.People || chosen == PhotoFilter.Documents) scanUnclassified()
            setFilter(chosen)
            returnToCollections = true
            setPane(PhotosPane.Timeline)
        }
    }
    fun navigateBack() {
        when {
            selectedCollection != null -> selectedCollection = null
            viewingFaceGroup != null -> viewingFaceGroup = null
            viewerUri != null -> viewerUri = null
            selectedUris.isNotEmpty() -> selectedUris = emptySet()
            searchOpen -> searchOpen = false
            toolsOpen -> toolsOpen = false
            collectionToolsOpen -> collectionToolsOpen = false
            pane == PhotosPane.Timeline && filter != PhotoFilter.Timeline && returnToCollections -> {
                setFilter(PhotoFilter.Timeline)
                returnToCollections = false
                setPane(PhotosPane.Collections)
            }
            else -> home()
        }
    }
    BackHandler(onBack = ::navigateBack)
    viewingFaceGroup?.let { group ->
        PersonGroupScreen(
            group = group,
            entries = allEntries.filter { it.photoKey in metadataStore.faceGroupKeys(group.id) },
            allGroups = faceGroups,
            entriesByKey = allEntries.associateBy(Entry::photoKey),
            back = { viewingFaceGroup = null },
            openPhoto = { entry ->
                viewingFaceGroup = null
                viewerUri = entry.contentUri
            },
            rename = { name ->
                metadataStore.renameFaceGroup(group.id, name)
                viewingFaceGroup = group.copy(name = PhotoMetadataRules.collectionName(name))
                analysisVersion++
            },
            merge = { source -> metadataStore.mergeFaceGroups(source.id, group.id).also { analysisVersion++ } },
            undoMerge = { undo -> metadataStore.undoFaceMerge(undo); analysisVersion++ },
        )
        return
    }
    val openViewerUri = viewerUri
    if (openViewerUri != null) {
        PhotoViewer(entries, openViewerUri, { uri ->
            viewerUri = uri
            allEntries.firstOrNull { it.contentUri == uri }?.let { recentTags = labelsByPhoto[it.photoKey].orEmpty() }
        }, { viewerUri = null },
            { entriesForAction, favorite -> setFavorite(entriesForAction.mapNotNullTo(mutableSetOf()) { it.contentUri }, favorite) },
            if (activeCollection == null) { uris -> collectionSheetFor = uris } else null,
            if (filter == PhotoFilter.Hidden) { entry ->
                entry.contentUri?.let { uri ->
                    viewerUri = null
                    restoreHidden(setOf(uri))
                }
            } else null,
            activeCollection?.let { collection -> { entry ->
                entry.contentUri?.let { uri ->
                    actionError = removeFromCollection(collection.id, setOf(uri))
                    if (actionError == null) viewerUri = null
                }
            } },
            metadata,
            labelsByPhoto,
            peopleNamesByPhoto,
            metadataStore,
            hasMediaLocationAccess,
            { openMapAfterPermission = true; requestPhotoLocations() },
            { locationVersion++ },
            { entry ->
                viewerUri = null
                mapFocusPhotoKey = entry.photoKey
                setFilter(PhotoFilter.Map)
                returnToCollections = true
                setPane(PhotosPane.Timeline)
            },
        )
        collectionSheetFor?.let { targets -> CollectionPickerSheet(
            collections = collections,
            previews = collectionPreviews,
            onDismiss = { collectionSheetFor = null },
            onHide = {
                collectionSheetFor = null
                viewerUri = null
                hideSelected(targets)
            },
            onAdd = { collectionId ->
                actionError = addToCollection(collectionId, targets)
                if (actionError == null) collectionSheetFor = null
            },
        )
        }
        return
    }
    LaunchedEffect(entries) {
        val available = entries.mapNotNullTo(mutableSetOf()) { it.contentUri }
        selectedUris = selectedUris.intersect(available)
    }
    val back = { navigateBack() }
    val timelineTitle = when (filter) {
        PhotoFilter.Timeline -> null
        PhotoFilter.Favorites -> "Favorites"
        PhotoFilter.People -> "People"
        PhotoFilter.Documents -> "Documents"
        PhotoFilter.Screenshots -> "Screenshots"
        PhotoFilter.Videos -> "Videos"
        PhotoFilter.Review -> "Help organize"
        PhotoFilter.Hidden -> "Hidden"
        PhotoFilter.Map -> "Map"
        is PhotoFilter.Collection -> collections.firstOrNull { it.id == filter.id }?.name
    }
    val timelineIcon = when (filter) {
        PhotoFilter.Favorites -> R.drawable.ic_favorite_border
        PhotoFilter.Documents -> R.drawable.ic_file
        PhotoFilter.Review -> R.drawable.ic_tag
        PhotoFilter.Hidden -> R.drawable.ic_lock
        PhotoFilter.Map -> R.drawable.ic_map
        is PhotoFilter.Collection, PhotoFilter.People -> R.drawable.ic_collections
        else -> R.drawable.ic_gallery
    }
    Box(Modifier.fillMaxSize()) {
        val mapCollection = pane == PhotosPane.Timeline && filter == PhotoFilter.Map
        when (pane) {
            PhotosPane.Timeline -> when {
                filter == PhotoFilter.People -> PeopleGroups(faceGroups, allEntries.associateBy(Entry::photoKey), { viewingFaceGroup = it }, back)
                filter == PhotoFilter.Map -> PhotoMapScreen(
                    entries = allEntries,
                    metadataStore = metadataStore,
                    hasLocationAccess = hasMediaLocationAccess,
                    requestLocationAccess = { openMapAfterPermission = true; requestPhotoLocations() },
                    locationsUpdated = { locationVersion++ },
                    focusPhotoKey = mapFocusPhotoKey,
                    back = back,
                    openPhoto = { viewerUri = it.contentUri },
                )
                filter == PhotoFilter.Hidden && !vaultUnlocked -> LockedVaultScreen(back) {
                    authenticateVault(context as Activity, success = { vaultUnlocked = true }, failure = { actionError = it })
                }
                else -> PhotoTimeline(
                if (state is ListState.Items) ListState.Items(entries) else state,
                scale, { scale = it }, timelineState, selectedUris,
                { entry -> entry.contentUri?.let { selectedUris = selectedUris + it } },
                { entry -> entry.contentUri?.let { uri -> selectedUris = if (uri in selectedUris) selectedUris - uri else selectedUris + uri } },
                { entry ->
                    recentTags = labelsByPhoto[entry.photoKey].orEmpty()
                    viewerUri = entry.contentUri
                },
                refresh,
                title = timelineTitle,
                icon = timelineIcon,
                back = back,
                showTimelineIsland = !searchOpen,
                emptyMessage = when (filter) {
                    PhotoFilter.Documents -> "No local document classifications yet."
                    PhotoFilter.Videos -> "No videos found in this collection."
                    PhotoFilter.Review -> "Nothing needs your review."
                    PhotoFilter.Hidden -> "Hidden is empty. Select photos or videos and tap the lock button to add them."
                    else -> "No photos found in this collection."
                },
            )
            }
            PhotosPane.Collections -> PullToRefreshBox(isRefreshing = state is ListState.Loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(bottom = 76.dp)) {
                    Collections(
                        allEntries, metadata, collections, collectionPreviews, documentKeys, faceGroups.size, reviewKeys, vaultItems.size,
                        !hidePeopleFromCollections, !hideDocumentsFromCollections, analysisRunning, analysisPaused,
                        analysisDone, analysisTotal, {
                            val paused = !analysisPaused
                            analysisPaused = paused
                            pauseRequested.set(paused)
                        }, back, { newCollectionOpen = true }, ::openCollection,
                        selectedCollection?.id,
                        { collection -> selectedCollection = if (selectedCollection?.id == collection.id) null else collection },
                    )
                }
            }
        }
        if (pane == PhotosPane.Timeline && !mapCollection && timelineTitle == null && !searchOpen) Surface(
            color = islandColor(),
            contentColor = islandContentColor(),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 12.dp),
        ) {
            IconButton(onClick = back) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back to Home")
            }
        }
        if (pane == PhotosPane.Timeline && !mapCollection && searchQuery.isNotBlank() && !searchOpen) Surface(
            color = islandColor(),
            contentColor = islandContentColor(),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 12.dp, top = 12.dp),
        ) {
            IconButton(onClick = { Toast.makeText(context, "Ενεργή αναζήτηση", Toast.LENGTH_SHORT).show() }) {
                Icon(
                    painterResource(R.drawable.ic_search_active),
                    contentDescription = "Active search",
                    tint = Color(0xFF65D47E),
                )
            }
        }
        if (selectedUris.isNotEmpty()) {
            if (filter == PhotoFilter.Hidden) HiddenSelectionActions(
                count = selectedUris.size,
                restore = { restoreHidden() },
                cancel = { selectedUris = emptySet() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            ) else SelectionActions(
                count = selectedUris.size,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                share = { sharePhotos(context, entries.filter { it.contentUri in selectedUris }) },
                addToCollection = if (activeCollection == null) ({ collectionSheetFor = selectedUris }) else null,
                removeFromCollection = activeCollection?.let { collection -> {
                    actionError = removeFromCollection(collection.id, selectedUris)
                    if (actionError == null) selectedUris = emptySet()
                } },
                toggleFavorite = {
                    val allFavorite = entries.filter { it.contentUri in selectedUris }.all { metadata[it.photoKey].orDefault().favorite }
                    actionError = setFavorite(selectedUris, !allFavorite)
                    if (actionError == null) selectedUris = emptySet()
                },
                moveToTrash = { moveToTrash(selectedUris) },
                hide = ::hideSelected,
                cancel = { selectedUris = emptySet() },
            )
        } else {
            AnimatedVisibility(
                visible = !mapCollection,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it },
                exit = fadeOut(tween(100)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                PhotoBottomBar(
                    pane = pane,
                    gallery = { setFilter(PhotoFilter.Timeline); returnToCollections = false; setPane(PhotosPane.Timeline) },
                    collections = { setPane(PhotosPane.Collections) },
                    expand = { if (pane == PhotosPane.Timeline) toolsOpen = true else collectionToolsOpen = true },
                    visible = !toolsOpen && !collectionToolsOpen && !searchOpen,
                )
                SearchButton(visible = !toolsOpen && !collectionToolsOpen && !searchOpen && selectedCollection == null) {
                    setFilter(PhotoFilter.Timeline)
                    returnToCollections = false
                    setPane(PhotosPane.Timeline)
                    searchOpen = true
                    if (metadataStore.peopleAnalysisEnabled()) scanUnclassified() else analysisConsentFor = PhotoFilter.Timeline
                }
                IslandVisibility(!toolsOpen && !collectionToolsOpen && !searchOpen && selectedCollection != null) {
                    Surface(color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, shape = CircleShape) {
                        IconButton(onClick = { collectionPendingDelete = selectedCollection }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete selected collection")
                        }
                    }
                }
                }
            }
        }
        AnimatedVisibility(
            visible = searchOpen && !mapCollection,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
            exit = fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth(),
        ) {
            PhotoSearchPanel(
                query = searchQuery,
                setQuery = { searchQuery = it },
                clear = { searchQuery = "" },
                dismiss = { searchOpen = false },
                suggestions = suggestedTags,
            )
        }
    }
    if (toolsOpen) GalleryToolsSheet(
        permission = permission,
        scale = scale,
        allowPhotos = grant,
        refresh = refresh,
        selectScale = { scale = it },
        hideScreenshots = hideScreenshots,
        hideDocuments = hideDocuments,
        setHideScreenshots = { hide -> metadataStore.setHidesScreenshotsFromGallery(hide); hideScreenshots = hide },
        setHideDocuments = { hide -> metadataStore.setHidesDocumentsFromGallery(hide); hideDocuments = hide },
        dismiss = { toolsOpen = false },
    )
    if (collectionToolsOpen) CollectionsToolsSheet(
        hidePeople = hidePeopleFromCollections,
        hideDocuments = hideDocumentsFromCollections,
        setHidePeople = { hide -> metadataStore.setHidesPeopleFromCollections(hide); hidePeopleFromCollections = hide },
        setHideDocuments = { hide -> metadataStore.setHidesDocumentsFromCollections(hide); hideDocumentsFromCollections = hide },
        dismiss = { collectionToolsOpen = false },
    )
    analysisConsentFor?.let { requested ->
        AlertDialog(
            onDismissRequest = { analysisConsentFor = null },
            title = { Text("Analyze your gallery?") },
            text = { Text("No download or internet is needed. The first analysis finds search tags, documents and groups faces locally. It can take time and use battery. Later visits analyze only new or changed photos. Nothing leaves this device.") },
            confirmButton = {
                Button(onClick = {
                    metadataStore.setPeopleAnalysisEnabled(true)
                    metadataStore.setDocumentsAnalysisEnabled(true)
                    analysisConsentFor = null
                    scanUnclassified()
                    if (requested != PhotoFilter.Timeline) {
                        setFilter(requested)
                        returnToCollections = true
                        setPane(PhotosPane.Timeline)
                    }
                }) { Text("Analyze now") }
            },
            dismissButton = { Button(onClick = { analysisConsentFor = null }) { Text("Not now") } },
        )
    }
    collectionSheetFor?.let { targets -> CollectionPickerSheet(
        collections = collections,
        previews = collectionPreviews,
        onDismiss = { collectionSheetFor = null },
        onHide = {
            collectionSheetFor = null
            hideSelected(targets)
        },
        onAdd = { collectionId ->
            actionError = addToCollection(collectionId, targets)
            if (actionError == null) {
                selectedUris = emptySet()
                collectionSheetFor = null
            }
        },
    )
    }
    if (newCollectionOpen) NewCollectionSheet(
        dismiss = { newCollectionOpen = false },
        create = { name ->
            createCollection(name).onSuccess { newCollectionOpen = false }
        },
    )
    collectionPendingDelete?.let { collection -> AlertDialog(
        onDismissRequest = { collectionPendingDelete = null },
        title = { Text("Delete ${collection.name}?") },
        text = { Text("The collection will be removed. Its photos and videos will stay in your gallery and on the device.") },
        confirmButton = { Button(onClick = {
            actionError = deleteCollection(collection.id)
            if (actionError == null) selectedCollection = null
            collectionPendingDelete = null
        }, colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
            Text("Delete collection", modifier = Modifier.padding(start = 8.dp))
        } },
        dismissButton = { Button(onClick = { collectionPendingDelete = null }, colors = neutralButtonColors()) {
            Icon(painterResource(R.drawable.ic_cancel), contentDescription = null)
            Text("Cancel", modifier = Modifier.padding(start = 8.dp))
        } },
    ) }
    if (hideWarningOpen) AlertDialog(
        onDismissRequest = { hideWarningOpen = false },
        title = { Text("Before using Hidden") },
        text = { Text("Hidden photos and videos are stored only inside Local Drive's private storage. Uninstalling the app deletes them. Restore everything from Hidden before uninstalling. The public original is removed only after a verified private copy and Android confirmation.") },
        confirmButton = { Button(onClick = {
            vault.acceptWarning()
            hideWarningOpen = false
                    performHide(selectedUris)
        }, colors = neutralButtonColors()) { Text("I understand") } },
        dismissButton = { Button(onClick = { hideWarningOpen = false }, colors = neutralButtonColors()) { Text("Cancel") } },
    )
    pendingReview?.let { review ->
        allEntries.firstOrNull { it.photoKey == review.photoKey }?.let { entry ->
            val candidate = remember(review) { review.candidateGroupId?.let(metadataStore::faceGroup) }
            ReviewPromptSheet(
                entry = entry,
                review = review,
                candidate = candidate,
                entriesByKey = allEntries.associateBy(Entry::photoKey),
                dismiss = {
                    setFilter(PhotoFilter.Timeline)
                    setPane(PhotosPane.Collections)
                },
                answer = { accepted ->
                    metadataStore.resolveReview(review, accepted)
                    analysisVersion++
                },
                skip = {
                    metadataStore.skipReview(review)
                    analysisVersion++
                },
            )
        }
    }
    actionError?.let { error -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
    analysisError?.let { error -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
}

internal fun analysisProgressFraction(done: Int, total: Int): Float = if (total <= 0) 0f else done.toFloat().coerceIn(0f, total.toFloat()) / total

@Composable
private fun AnalysisProgress(done: Int, total: Int, paused: Boolean, togglePause: () -> Unit, modifier: Modifier = Modifier) = Surface(
    shape = MaterialTheme.shapes.large,
    color = islandColor(),
    contentColor = islandContentColor(),
    modifier = modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Analyzing locally", style = MaterialTheme.typography.titleSmall)
        Text("$done / $total photos · no download or internet", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = { analysisProgressFraction(done, total) }, modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp).clickable(onClick = togglePause)) {
                Icon(painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause), contentDescription = if (paused) "Resume analysis" else "Pause analysis", modifier = Modifier.padding(9.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewPromptSheet(entry: Entry, review: PendingReview, candidate: FaceGroup?, entriesByKey: Map<String, Entry>, dismiss: () -> Unit, answer: (Boolean) -> Unit, skip: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (review.candidateGroupId != null && review.faceSample != null && candidate != null) {
                Text("Compare the two face crops", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FaceReviewCrop("This photo", entry, review.faceSample.bounds, Modifier.weight(1f))
                    FaceReviewCrop(candidate.name, entriesByKey[candidate.photoKey], candidate.bounds(), Modifier.weight(1f))
                }
            } else if (review.candidateGroupId == null) ViewerImage(entry, Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.large))
            if (review.candidateGroupId != null && (review.faceSample == null || candidate == null)) {
                Text("This face comparison is no longer available.", style = MaterialTheme.typography.titleLarge)
                Button(onClick = skip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            } else {
                Text(review.question, style = MaterialTheme.typography.titleLarge)
                if (review.candidateGroupId != null) Text("Compare the two labelled face crops. Confirm only if they are the same person.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { answer(true) }, modifier = Modifier.weight(1f)) { Text("Yes") }
                    Button(onClick = { answer(false) }, modifier = Modifier.weight(1f)) { Text("No") }
                    Button(onClick = skip, modifier = Modifier.weight(1f)) { Text("Skip") }
                }
            }
        }
    }
}

@Composable
private fun FaceReviewCrop(label: String, entry: Entry?, bounds: Rect, modifier: Modifier = Modifier) = Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FaceCrop(entry, bounds, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large))
    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PeopleGroups(groups: List<FaceGroup>, entries: Map<String, Entry>, open: (FaceGroup) -> Unit, back: () -> Unit) {
    if (groups.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilesPageHeader("People", R.drawable.ic_collections, back)
            Text("No people found yet.")
        }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { FilesPageHeader("People", R.drawable.ic_collections, back) }
        items(groups, key = { it.id }) { group ->
            FaceGroupCard(group, entries[group.photoKey]) { open(group) }
        }
    }
}

@Composable
private fun FaceGroupCard(group: FaceGroup, entry: Entry?, open: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.aspectRatio(1f).clickable(onClick = open),
) { Box(Modifier.fillMaxSize()) {
    FaceCrop(entry, group, Modifier.fillMaxSize())
    Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.count.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
} }

@Composable
private fun FaceCrop(entry: Entry?, group: FaceGroup, modifier: Modifier = Modifier) = FaceCrop(entry, group.bounds(), modifier)

@Composable
private fun FaceCrop(entry: Entry?, bounds: Rect, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, entry?.contentUri, bounds) {
        value = entry?.contentUri?.let { uri -> withContext(Dispatchers.IO) { runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = minOf(1f, 1280f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.let { source ->
                bounds.let { rect ->
                    Bitmap.createBitmap(source, rect.left.coerceIn(0, source.width - 1), rect.top.coerceIn(0, source.height - 1), rect.width().coerceAtMost(source.width - rect.left.coerceIn(0, source.width - 1)), rect.height().coerceAtMost(source.height - rect.top.coerceIn(0, source.height - 1)))
                }
            }
        }.getOrNull() } }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier = modifier, contentScale = ContentScale.Crop) }
        ?: Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_collections), null) }
}

@Composable
private fun PersonGroupScreen(group: FaceGroup, entries: List<Entry>, allGroups: List<FaceGroup>, entriesByKey: Map<String, Entry>, back: () -> Unit, openPhoto: (Entry) -> Unit, rename: (String) -> Unit, merge: (FaceGroup) -> FaceMergeUndo, undoMerge: (FaceMergeUndo) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var combining by remember { mutableStateOf(false) }
    var mergeTargets by remember { mutableStateOf<List<FaceGroup>?>(null) }
    var recentMerges by remember { mutableStateOf(emptyList<FaceMergeUndo>()) }
    var name by remember(group.id, group.name) { mutableStateOf(group.name) }
    LaunchedEffect(recentMerges) {
        if (recentMerges.isNotEmpty()) {
            delay(8_000)
            recentMerges = emptyList()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilesPageHeader(group.name, R.drawable.ic_collections, back, trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.medium, modifier = Modifier.clickable { combining = true }) { Text("Combine", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) }
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) { IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Rename") } }
            }
        })
        if (editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { rename(name); editing = false }) { Text("Save") }
        }
        recentMerges.takeIf { it.isNotEmpty() }?.let { undos -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${undos.size} people combined", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Undo", modifier = Modifier.clickable { undos.asReversed().forEach(undoMerge); recentMerges = emptyList() }.padding(8.dp), style = MaterialTheme.typography.labelLarge)
            }
        } }
        LazyVerticalGrid(GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries, key = { it.photoKey }) { entry -> PhotoThumbnail(entry, false) { openPhoto(entry) } }
        }
    }
    if (combining) CombineFaceGroupsSheet(group, allGroups.filter { it.id != group.id }, entriesByKey, dismiss = { combining = false }, combine = { mergeTargets = it })
    mergeTargets?.let { sources -> AlertDialog(
        onDismissRequest = { mergeTargets = null },
        title = { Text("Combine people?") },
        text = { Text("Move every photo from ${sources.size} selected groups into ${group.name}. You can undo this for a few seconds.") },
        confirmButton = { Button(onClick = { recentMerges = sources.map(merge); mergeTargets = null; combining = false }, colors = neutralButtonColors()) { Text("Combine") } },
        dismissButton = { Button(onClick = { mergeTargets = null }, colors = neutralButtonColors()) { Text("Cancel") } },
    ) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CombineFaceGroupsSheet(current: FaceGroup, choices: List<FaceGroup>, entriesByKey: Map<String, Entry>, dismiss: () -> Unit, combine: (List<FaceGroup>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val visibleChoices = remember(choices, query) { choices.filter { it.name.contains(query, ignoreCase = true) } }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Manually combine with ${current.name}", style = MaterialTheme.typography.titleLarge)
            Text("These are all people groups, not AI suggestions. Choose one only when it is the same person.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, label = { Text("Find a person") }, modifier = Modifier.weight(1f), singleLine = true)
                Button(onClick = { combine(choices.filter { it.id in selectedIds }) }, enabled = selectedIds.isNotEmpty(), colors = neutralButtonColors()) { Text("Combine") }
            }
            if (choices.isEmpty()) Text("There are no other people groups yet.")
            else if (visibleChoices.isEmpty()) Text("No people match this name.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleChoices, key = { it.id }) { choice ->
                    val selected = choice.id in selectedIds
                    Surface(shape = MaterialTheme.shapes.large, color = if (selected) driveNavigationSelectedColor() else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (selected) driveNavigationSelectedContentColor() else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().clickable {
                        selectedIds = if (selected) selectedIds - choice.id else selectedIds + choice.id
                    }) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FaceCrop(entriesByKey[choice.photoKey], choice, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                        Column(Modifier.weight(1f)) {
                            Text(choice.name, style = MaterialTheme.typography.titleMedium)
                            Text("${choice.count} face${if (choice.count == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected")
                    }
                } }
            }
        }
    }
}

@Composable
private fun LockedVaultScreen(back: () -> Unit, unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        FilesPageHeader("Hidden", R.drawable.ic_lock, back)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(54.dp))
            Text("Hidden is locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Text("Unlock with biometrics or your phone screen lock.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Button(onClick = unlock, colors = neutralButtonColors(), modifier = Modifier.padding(top = 16.dp)) { Text("Unlock") }
        }
    }
}

@Composable
private fun Collections(entries: List<Entry>, metadata: Map<String, PhotoState>, custom: List<PhotoCollection>, previews: Map<Long, Entry?>, documentKeys: Set<String>, peopleCount: Int, reviewKeys: Set<String>, hiddenCount: Int, showPeople: Boolean, showDocuments: Boolean, analysisRunning: Boolean, analysisPaused: Boolean, analysisDone: Int, analysisTotal: Int, toggleAnalysisPause: () -> Unit, back: () -> Unit, create: () -> Unit, open: (PhotoFilter) -> Unit, selectedCollectionId: Long?, selectCollection: (PhotoCollection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FilesPageHeader("Collections", R.drawable.ic_collections, back, trailing = {
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                IconButton(onClick = create) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Create collection") }
            }
        }) }
        if (analysisRunning) item { AnalysisProgress(analysisDone, analysisTotal, analysisPaused, toggleAnalysisPause) }
        item { Text("System collections", style = MaterialTheme.typography.labelLarge) }
        if (showPeople || showDocuments) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showPeople) SystemCollectionButton("People", peopleCount, R.drawable.ic_collections, { open(PhotoFilter.People) }, Modifier.weight(1f))
            if (showDocuments) SystemCollectionButton("Documents", entries.count { it.photoKey in documentKeys }, R.drawable.ic_file, { open(PhotoFilter.Documents) }, Modifier.weight(1f))
            if (!showPeople || !showDocuments) Box(Modifier.weight(1f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemCollectionButton("Screenshots", entries.count(Entry::isScreenshot), R.drawable.ic_gallery, { open(PhotoFilter.Screenshots) }, Modifier.weight(1f))
            SystemCollectionButton("Videos", entries.count(Entry::isVideo), R.drawable.ic_gallery, { open(PhotoFilter.Videos) }, Modifier.weight(1f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemCollectionButton("Hidden", hiddenCount, R.drawable.ic_lock, { open(PhotoFilter.Hidden) }, Modifier.weight(1f))
            SystemCollectionButton("Map", null, R.drawable.ic_map, { open(PhotoFilter.Map) }, Modifier.weight(1f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemCollectionButton("Help organize", entries.count { it.photoKey in reviewKeys }, R.drawable.ic_tag, { open(PhotoFilter.Review) }, Modifier.weight(1f))
            SystemCollectionButton("Favorites", entries.count { metadata[it.photoKey].orDefault().favorite }, R.drawable.ic_favorite_border, { open(PhotoFilter.Favorites) }, Modifier.weight(1f))
        } }
        item { Text("My collections", style = MaterialTheme.typography.labelLarge) }
        if (custom.isEmpty()) item { Text("Tap + to create your first collection.", style = MaterialTheme.typography.bodyMedium) }
        custom.chunked(2).forEach { row -> item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { collection -> MyCollectionCard(
                    collection = collection,
                    preview = previews[collection.id],
                    selected = selectedCollectionId == collection.id,
                    open = { if (selectedCollectionId == null) open(PhotoFilter.Collection(collection.id)) else selectCollection(collection) },
                    select = { selectCollection(collection) },
                    modifier = Modifier.weight(1f),
                ) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        } }
        item { Text("Hidden media stays in private app storage and unlocks with your phone security. Map coordinates are read locally; the OpenStreetMap background needs internet. Pull the bottom island here to manage system collections.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun RowScope.SystemCollectionButton(name: String, count: Int?, icon: Int, open: () -> Unit, modifier: Modifier = Modifier) = Surface(
    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier.height(68.dp).clickable(onClick = open),
) { Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(painterResource(icon), contentDescription = name, modifier = Modifier.size(22.dp))
    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    count?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall) }
} }

@Composable private fun MyCollectionCard(collection: PhotoCollection, preview: Entry?, selected: Boolean = false, open: () -> Unit, select: () -> Unit = {}, modifier: Modifier = Modifier) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier.aspectRatio(1f).pointerInput(collection.id) {
        detectTapGestures(onTap = { open() }, onLongPress = { select() })
    }.then(if (selected) Modifier.border(3.dp, islandContentColor(), MaterialTheme.shapes.extraLarge) else Modifier),
) { Box(Modifier.fillMaxSize()) {
    CollectionCover(preview, Modifier.fillMaxSize())
    if (selected) Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = "Selected collection", modifier = Modifier.padding(8.dp).size(22.dp))
    }
    Surface(
        color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
    ) { Column(Modifier.padding(12.dp)) {
        Text(collection.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(collection.storedCount.toString(), style = MaterialTheme.typography.labelSmall)
    } }
} }

@Composable private fun CollectionCover(entry: Entry?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, entry?.contentUri) {
        value = entry?.contentUri?.let { uri -> withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(360, 360), null) }.getOrNull() } }
    }
    val image = bitmap
    if (image == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_collections), contentDescription = null, modifier = Modifier.size(44.dp))
    } else Image(bitmap = image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCollectionSheet(dismiss: () -> Unit, create: (String) -> Result<PhotoCollection>) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New collection", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error != null)
            Button(onClick = { create(name).onFailure { error = it.message ?: "Could not create collection." } }, modifier = Modifier.fillMaxWidth()) { Text("Create") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPickerSheet(
    collections: List<PhotoCollection>,
    previews: Map<Long, Entry?>,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onAdd: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add to collection", style = MaterialTheme.typography.titleMedium)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onHide),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null)
                    Column {
                        Text("Hidden", style = MaterialTheme.typography.titleSmall)
                        Text("Lock behind biometrics or screen lock", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_map), contentDescription = null)
                    Column {
                        Text("Map", style = MaterialTheme.typography.titleSmall)
                        Text("Added automatically when the photo has coordinates", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("My collections", style = MaterialTheme.typography.labelLarge)
            if (collections.isEmpty()) Text("No personal collections yet.", style = MaterialTheme.typography.bodySmall)
            collections.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { collection ->
                        MyCollectionCard(collection, previews[collection.id], open = { onAdd(collection.id) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun PhotoState?.orDefault() = this ?: PhotoState()
private fun Entry.isScreenshot() = relativePath?.startsWith("Pictures/Screenshots/") == true || relativePath?.startsWith("DCIM/Screenshots/") == true

@Composable
private fun PhotoBottomBar(
    pane: PhotosPane,
    gallery: () -> Unit,
    collections: () -> Unit,
    expand: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    IslandBottomBar(expand, visible, modifier) {
        IslandNavigationItem("Gallery", pane == PhotosPane.Timeline, R.drawable.ic_gallery, gallery)
        IslandNavigationItem("Collections", pane == PhotosPane.Collections, R.drawable.ic_collections, collections)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryToolsSheet(
    permission: String,
    scale: TimelineScale,
    allowPhotos: () -> Unit,
    refresh: () -> Unit,
    selectScale: (TimelineScale) -> Unit,
    hideScreenshots: Boolean,
    hideDocuments: Boolean,
    setHideScreenshots: (Boolean) -> Unit,
    setHideDocuments: (Boolean) -> Unit,
    dismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gallery tools", style = MaterialTheme.typography.titleLarge)
            Text(permission, style = MaterialTheme.typography.bodyMedium)
            if (permission != "Full photo and video access" && permission != "Photo and video access granted") {
                DriveWideAction(R.drawable.ic_check, "Allow photos and videos") { allowPhotos(); dismiss() }
            }
            DriveWideAction(R.drawable.ic_refresh, "Refresh") { refresh(); dismiss() }
            Text("Hide from Gallery", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { setHideScreenshots(!hideScreenshots) }) {
                Checkbox(checked = hideScreenshots, onCheckedChange = setHideScreenshots, colors = neutralCheckboxColors())
                Text("Screenshots")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { setHideDocuments(!hideDocuments) }) {
                Checkbox(checked = hideDocuments, onCheckedChange = setHideDocuments, colors = neutralCheckboxColors())
                Text("Documents")
            }
            Text("Timeline size", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineScale.entries.forEach { option ->
                    Surface(
                        color = if (scale == option) driveNavigationSelectedColor() else islandColor(),
                        contentColor = if (scale == option) driveNavigationSelectedContentColor() else islandContentColor(),
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).clickable { selectScale(option); dismiss() },
                    ) { Text(option.label, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionsToolsSheet(
    hidePeople: Boolean,
    hideDocuments: Boolean,
    setHidePeople: (Boolean) -> Unit,
    setHideDocuments: (Boolean) -> Unit,
    dismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = islandColor(), contentColor = islandContentColor()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Collections tools", style = MaterialTheme.typography.titleLarge)
            Text("Hide system collections", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { setHidePeople(!hidePeople) }) {
                Checkbox(checked = hidePeople, onCheckedChange = setHidePeople, colors = neutralCheckboxColors())
                Text("People")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { setHideDocuments(!hideDocuments) }) {
                Checkbox(checked = hideDocuments, onCheckedChange = setHideDocuments, colors = neutralCheckboxColors())
                Text("Documents")
            }
        }
    }
}

@Composable
private fun neutralCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = driveNavigationSelectedColor(),
    checkmarkColor = driveNavigationSelectedContentColor(),
    uncheckedColor = islandContentColor().copy(alpha = 0.72f),
)

@Composable
private fun neutralButtonColors() = ButtonDefaults.buttonColors(
    containerColor = driveNavigationSelectedColor(),
    contentColor = driveNavigationSelectedContentColor(),
    disabledContainerColor = islandContentColor().copy(alpha = 0.12f),
    disabledContentColor = islandContentColor().copy(alpha = 0.38f),
)

@Composable
private fun SearchButton(visible: Boolean = true, description: String = "Search photos", open: () -> Unit) {
    IslandVisibility(visible) {
        Surface(
            color = islandColor(),
            contentColor = islandContentColor(),
            shape = CircleShape,
        ) {
            IconButton(onClick = open) {
                Icon(
                    painterResource(R.drawable.ic_search),
                    contentDescription = description,
                    tint = islandContentColor(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(query: String, setQuery: (String) -> Unit, clear: () -> Unit, dismiss: () -> Unit, title: String = "Search photos", hint: String = "Name or folder", suggestions: List<String> = emptyList()) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = setQuery,
                label = { Text(hint) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (suggestions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { suggestion ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { setQuery(suggestion) }) {
                        Text(suggestion, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (query.isNotBlank()) Text("Clear", modifier = Modifier.clickable(onClick = clear).padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PhotoSearchPanel(query: String, setQuery: (String) -> Unit, clear: () -> Unit, dismiss: () -> Unit, suggestions: List<String>) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val glass = islandColor()
    val ink = islandContentColor()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = glass, contentColor = ink, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = setQuery,
                label = { Text("Name, place or English AI label") },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ink.copy(alpha = 0.65f), unfocusedBorderColor = ink.copy(alpha = 0.35f),
                    focusedLabelColor = ink, unfocusedLabelColor = ink.copy(alpha = 0.72f), cursorColor = ink,
                    focusedTextColor = ink, unfocusedTextColor = ink,
                ),
            )
            if (suggestions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { suggestion ->
                    Surface(shape = MaterialTheme.shapes.small, color = Color.White.copy(alpha = 0.62f), modifier = Modifier.clickable { setQuery(suggestion) }) {
                        Text(suggestion, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("English AI tags", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (query.isNotBlank()) Text("Clear", modifier = Modifier.clickable(onClick = clear).padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = dismiss) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Close search") }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    count: Int,
    share: () -> Unit,
    addToCollection: (() -> Unit)?,
    removeFromCollection: (() -> Unit)?,
    toggleFavorite: () -> Unit,
    moveToTrash: () -> Unit,
    hide: () -> Unit,
    cancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = islandColor(),
        contentColor = islandContentColor(),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            removeFromCollection?.let { remove ->
                Button(onClick = remove, colors = neutralButtonColors(), modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_remove_from_collection), contentDescription = null)
                    Text("Remove from this collection", modifier = Modifier.padding(start = 10.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = share) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share selected photos") }
                addToCollection?.let { add -> IconButton(onClick = add) {
                    Icon(painterResource(R.drawable.ic_add_to_collection), contentDescription = "Add selected photos to collection")
                } }
                IconButton(onClick = toggleFavorite) { Icon(painterResource(R.drawable.ic_favorite_border), contentDescription = "Toggle favorite for selected photos") }
                IconButton(onClick = moveToTrash) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Move selected photos to trash") }
                IconButton(onClick = hide) { Icon(painterResource(R.drawable.ic_lock), contentDescription = "Move selected photos to Hidden") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count selected", style = MaterialTheme.typography.labelLarge)
                Text("Cancel", modifier = Modifier.clickable(onClick = cancel).padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HiddenSelectionActions(count: Int, restore: () -> Unit, cancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = restore, colors = neutralButtonColors(), modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_restore), contentDescription = null)
                Text("Restore", modifier = Modifier.padding(start = 10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("$count selected", style = MaterialTheme.typography.labelLarge)
                Text("Cancel", modifier = Modifier.clickable(onClick = cancel).padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PhotoTimeline(
    state: ListState,
    scale: TimelineScale,
    setScale: (TimelineScale) -> Unit,
    gridState: LazyGridState,
    selectedUris: Set<Uri>,
    addSelection: (Entry) -> Unit,
    toggleSelection: (Entry) -> Unit,
    openPhoto: (Entry) -> Unit,
    refresh: () -> Unit,
    title: String?,
    icon: Int,
    back: () -> Unit,
    showTimelineIsland: Boolean,
    emptyMessage: String,
) {
    PullToRefreshBox(isRefreshing = state is ListState.Loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) {
        when (state) {
            ListState.Idle -> TimelineMessage("Choose an action in More to load photos.")
            ListState.Loading -> TimelineMessage("Loading…")
            is ListState.Error -> TimelineMessage(state.message, MaterialTheme.colorScheme.error)
            is ListState.Items -> if (state.entries.isEmpty()) TimelineMessage(emptyMessage) else {
            val grouped = state.entries.groupBy { TimelineRules.groupKey(it.takenMillis, scale) }
            val photosByKey = state.entries.associateBy { "photo:${it.contentUri}" }
            val itemGroupKeys = remember(grouped, scale) {
                val groups = TimelineRules.itemGroupKeys(grouped.map { (key, photos) -> key to photos.size }, firstGroupHasHeader = false)
                listOf(groups.firstOrNull() ?: "unknown") + groups
            }
            val activeGroup = TimelineRules.visibleGroupKey(itemGroupKeys, gridState.firstVisibleItemIndex)
            var scrollbarLabel by remember { mutableStateOf<String?>(null) }
            var scrollbarInteracting by remember { mutableStateOf(false) }
            var scrollbarVisible by remember { mutableStateOf(false) }
            LaunchedEffect(scrollbarLabel) {
                if (scrollbarLabel != null) {
                    delay(900)
                    scrollbarLabel = null
                }
            }
            LaunchedEffect(scrollbarInteracting) {
                if (scrollbarInteracting) scrollbarVisible = true else {
                    delay(700)
                    if (!scrollbarInteracting) scrollbarVisible = false
                }
            }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(scale.columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .timelinePinch(scale, setScale)
                        .timelineSelection(gridState, photosByKey, addSelection),
                ) {
                    if (title == null) item(key = "timeline-top-inset", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.heightIn(min = 140.dp))
                    } else {
                        item(key = "timeline-page-header", span = { GridItemSpan(maxLineSpan) }) {
                            FilesPageHeader(title, icon, back)
                        }
                        item(key = "timeline-title-gap", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.height(64.dp))
                        }
                    }
                    grouped.entries.forEachIndexed { index, (key, entries) ->
                        if (index > 0 || title != null) item(key = "date:$key", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    TimelineRules.groupLabel(key, scale),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                        items(entries, key = { "photo:${it.contentUri}" }) { entry ->
                            val selected = entry.contentUri in selectedUris
                            PhotoThumbnail(entry, selected) {
                                if (selectedUris.isEmpty()) openPhoto(entry) else toggleSelection(entry)
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showTimelineIsland && (title == null || gridState.firstVisibleItemIndex > 0),
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 3 },
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
                ) {
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
                        Text(
                            TimelineRules.groupLabel(activeGroup, scale),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (title != null) AnimatedVisibility(
                    visible = gridState.firstVisibleItemIndex > 0,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 3 },
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 12.dp),
                ) {
                    Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                        IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                    }
                }
                if (itemGroupKeys.size > 8) TimelineFastScrollbar(
                    itemCount = itemGroupKeys.size,
                    firstVisibleItem = gridState.firstVisibleItemIndex,
                    visible = scrollbarVisible,
                    setInteracting = { scrollbarInteracting = it },
                    onScrollTo = { index ->
                        val group = TimelineRules.visibleGroupKey(itemGroupKeys, index)
                        scrollbarLabel = "${TimelineRules.yearForGroup(group)} · ${TimelineRules.groupLabel(group, scale)}"
                        gridState.scrollToItem(index)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                scrollbarLabel?.let { label ->
                    Surface(
                        color = islandColor(),
                        contentColor = islandContentColor(),
                        modifier = Modifier.align(Alignment.Center),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) { Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
                }
            }
            }
        }
    }
}

@Composable
private fun TimelineMessage(message: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.large) {
            Text(message, color = color, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TimelineFastScrollbar(
    itemCount: Int,
    firstVisibleItem: Int,
    visible: Boolean,
    setInteracting: (Boolean) -> Unit,
    onScrollTo: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var lastRequestedIndex by remember(itemCount) { mutableStateOf(-1) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val ratio = firstVisibleItem.coerceIn(0, itemCount - 1).toFloat() / (itemCount - 1).coerceAtLeast(1)
    val thumbHeight = 48.dp
    val maxThumbOffset = with(LocalDensity.current) {
        (size.height - thumbHeight.roundToPx()).coerceAtLeast(0).toDp()
    }
    fun scrollAt(y: Float) {
        val index = ((y / size.height.coerceAtLeast(1)) * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        if (index == lastRequestedIndex) return
        lastRequestedIndex = index
        scrollJob?.cancel()
        scrollJob = scope.launch { onScrollTo(index) }
    }
    Box(
        modifier
            .fillMaxHeight()
            .width(48.dp)
            .onSizeChanged { size = it }
            .semantics { contentDescription = "Fast scroll photos" }
            .pointerInput(itemCount, size) {
                detectTapGestures(onPress = { position ->
                    setInteracting(true)
                    scrollAt(position.y)
                    tryAwaitRelease()
                    setInteracting(false)
                })
            }
            .pointerInput(itemCount, size) {
                detectDragGestures(
                    onDragStart = { setInteracting(true); scrollAt(it.y) },
                    onDragEnd = { setInteracting(false) },
                    onDragCancel = { setInteracting(false) },
                ) { change, _ ->
                    scrollAt(change.position.y)
                    change.consume()
                }
            },
    ) {
        if (visible) {
            Box(
                Modifier.align(Alignment.Center).fillMaxHeight(0.64f).width(12.dp).clip(CircleShape)
                    .background(islandColor()),
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = maxThumbOffset * ratio)
                    .size(width = 20.dp, height = thumbHeight).clip(CircleShape).background(islandContentColor().copy(alpha = 0.88f)),
            )
        }
    }
}

private fun Modifier.timelinePinch(scale: TimelineScale, setScale: (TimelineScale) -> Unit) = pointerInput(scale) {
    awaitEachGesture {
        var firstDistance: Float? = null
        var lastDistance = 0f
        do {
            val event = awaitPointerEvent()
            val fingers = event.changes.filter { it.pressed }
            if (fingers.size >= 2) {
                val distance = (fingers[0].position - fingers[1].position).getDistance()
                if (firstDistance == null) firstDistance = distance
                lastDistance = distance
            }
        } while (event.changes.any { it.pressed })
        val start = firstDistance
        if (start != null && start > 0f && lastDistance / start > 1.15f) setScale(scale.finer())
        if (start != null && start > 0f && lastDistance / start < 0.85f) setScale(scale.broader())
    }
}

@Composable
internal fun PhotoThumbnail(entry: Entry, selected: Boolean, modifier: Modifier = Modifier, open: () -> Unit) {
    val context = LocalContext.current
    val halfPixel = with(LocalDensity.current) { (0.5f / density).dp }
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.contentUri) {
        value = entry.contentUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null) }.getOrNull()
                    ?: runCatching {
                        if (entry.isVideo) MediaMetadataRetriever().let { retriever ->
                            try {
                                retriever.setDataSource(context, uri)
                                retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            } finally { retriever.release() }
                        } else ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                            decoder.setTargetSize(240, 240)
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }.getOrNull()
            }
        }
    }
    Box(
        modifier
            .padding(halfPixel)
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .clickable(onClick = open)
            .semantics {
                contentDescription = "${if (entry.isVideo) "Video" else "Photo"} ${entry.name}"
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (entry.isVideo) Surface(
            color = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.Center),
        ) { Text("▶", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
        if (selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

private fun Modifier.timelineSelection(
    gridState: LazyGridState,
    photosByKey: Map<String, Entry>,
    addSelection: (Entry) -> Unit,
) = pointerInput(gridState, photosByKey) {
    kotlinx.coroutines.coroutineScope {
        var startedOnPhoto = false
        var lastPosition: androidx.compose.ui.geometry.Offset? = null
        var autoScroll: Job? = null
        var autoScrollDirection = 0f
        fun entryAt(position: androidx.compose.ui.geometry.Offset): Entry? = gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { info ->
                val key = info.key as? String
                key != null && key.startsWith("photo:") &&
                    position.x >= info.offset.x && position.x < info.offset.x + info.size.width &&
                    position.y >= info.offset.y && position.y < info.offset.y + info.size.height
            }
            ?.let { photosByKey[it.key as String] }
        fun stopAutoScroll() {
            autoScroll?.cancel()
            autoScroll = null
            autoScrollDirection = 0f
        }
        fun updateAutoScroll(position: androidx.compose.ui.geometry.Offset) {
            val layout = gridState.layoutInfo
            val direction = when {
                position.y < layout.viewportStartOffset + 72f -> -1f
                position.y > layout.viewportEndOffset - 72f -> 1f
                else -> 0f
            }
            if (direction == 0f) {
                stopAutoScroll()
            } else if (autoScroll == null || autoScrollDirection != direction) {
                stopAutoScroll()
                autoScrollDirection = direction
                autoScroll = launch {
                    try {
                        while (isActive) {
                            val moved = gridState.scrollBy(direction * 24f)
                            lastPosition?.let(::entryAt)?.let(addSelection)
                            if (moved == 0f) break
                            delay(16)
                        }
                    } finally {
                        if (autoScrollDirection == direction) {
                            autoScroll = null
                            autoScrollDirection = 0f
                        }
                    }
                }
            }
        }
        detectDragGesturesAfterLongPress(
            onDragStart = {
                val entry = entryAt(it)
                startedOnPhoto = entry != null
                lastPosition = it
                entry?.let(addSelection)
            },
            onDrag = { change, _ ->
                if (startedOnPhoto) {
                    lastPosition = change.position
                    entryAt(change.position)?.let(addSelection)
                    updateAutoScroll(change.position)
                }
                change.consume()
            },
            onDragEnd = { startedOnPhoto = false; lastPosition = null; stopAutoScroll() },
            onDragCancel = { startedOnPhoto = false; lastPosition = null; stopAutoScroll() },
        )
    }
}

private class ViewerZoomState {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoViewer(
    entries: List<Entry>, selectedUri: Uri, select: (Uri) -> Unit, close: () -> Unit,
    setFavorite: (List<Entry>, Boolean) -> String?, addToCollection: ((Set<Uri>) -> Unit)?,
    restore: ((Entry) -> Unit)?,
    removeFromCollection: ((Entry) -> Unit)?,
    metadata: Map<String, PhotoState>, labelsByPhoto: Map<String, List<String>>, peopleNamesByPhoto: Map<String, List<String>>,
    metadataStore: PhotoMetadataStore, hasLocationAccess: Boolean, requestLocationAccess: () -> Unit,
    locationsUpdated: () -> Unit, openMap: (Entry) -> Unit,
) {
    val context = LocalContext.current
    val filmstripState = rememberLazyListState()
    val selected = entries.firstOrNull { it.contentUri == selectedUri }
    if (selected == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = close) { Text("Back") }
            Text("This photo is no longer available.", style = MaterialTheme.typography.titleMedium)
            Text("Refresh Photos after restoring permission or changing the collection.")
        }
        return
    }
    val initialPage = entries.indexOfFirst { it.contentUri == selectedUri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { entries.size }
    val currentPage = pagerState.currentPage
    var detailsOpen by remember(selectedUri) { mutableStateOf(false) }
    var actionError by remember(selectedUri) { mutableStateOf<String?>(null) }
    var manualFullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val videoPlayback = remember { mutableMapOf<String, VideoPlaybackState>() }
    val imageZoom = remember { mutableMapOf<String, ViewerZoomState>() }
    val selectedZoom = imageZoom.getOrPut(selected.photoKey) { ViewerZoomState() }
    val location by produceState<PhotoLocation?>(initialValue = null, selected.photoKey, hasLocationAccess) {
        value = if (hasLocationAccess) withContext(Dispatchers.IO) { loadPhotoLocation(context, selected, metadataStore) } else null
        if (hasLocationAccess) locationsUpdated()
    }
    val zoomMode = !selected.isVideo && selectedZoom.scale > 1f
    val rotatedLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = zoomMode || manualFullscreen || rotatedLandscape
    BackHandler {
        when {
            zoomMode -> selectedZoom.reset()
            manualFullscreen -> manualFullscreen = false
            else -> close()
        }
    }
    LaunchedEffect(zoomMode) { if (!zoomMode) controlsVisible = true }
    val activity = context as? Activity
    androidx.compose.runtime.DisposableEffect(fullscreen, activity) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(selectedUri, entries) {
        val target = entries.indexOfFirst { it.contentUri == selectedUri }
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
    }
    LaunchedEffect(currentPage, entries) {
        entries.getOrNull(currentPage)?.contentUri?.takeIf { it != selectedUri }?.let(select)
    }
    LaunchedEffect(selectedUri, entries) {
        val selectedIndex = entries.indexOfFirst { it.contentUri == selectedUri }
        if (selectedIndex >= 0) {
            filmstripState.scrollToItem(selectedIndex)
            val item = filmstripState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            if (item != null) {
                val viewportCenter = (filmstripState.layoutInfo.viewportStartOffset + filmstripState.layoutInfo.viewportEndOffset) / 2
                filmstripState.scrollBy(item.offset + item.size / 2f - viewportCenter)
            }
        }
    }
    val pager: @Composable (Modifier) -> Unit = { modifier ->
        ViewerPager(
            entries = entries,
            state = pagerState,
            playback = videoPlayback,
            imageZoom = imageZoom,
            zoomMode = zoomMode,
            modifier = modifier,
            background = if (fullscreen) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
            toggleControls = { if (fullscreen && !zoomMode) controlsVisible = !controlsVisible },
            showDetails = { detailsOpen = true },
        )
    }
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        ViewerActionsIsland(
            favorite = metadata[selected.photoKey].orDefault().favorite,
            isVideo = selected.isVideo && (!rotatedLandscape || manualFullscreen),
            fullscreen = fullscreen,
            share = { sharePhotos(context, listOf(selected)) },
            details = { detailsOpen = true },
            toggleFavorite = { actionError = setFavorite(listOf(selected), !metadata[selected.photoKey].orDefault().favorite) },
            add = addToCollection?.let { action -> { selected.contentUri?.let { action(setOf(it)) } } },
            restore = restore?.let { action -> { action(selected) } },
            removeFromCollection = removeFromCollection?.let { action -> { action(selected) } },
            edit = { Toast.makeText(context, "Η επεξεργασία θα προστεθεί αργότερα", Toast.LENGTH_SHORT).show() },
            toggleFullscreen = { manualFullscreen = !manualFullscreen },
            modifier = modifier,
        )
    }
    if (fullscreen) Box(Modifier.fillMaxSize().background(Color.Black)) {
        pager(Modifier.fillMaxSize())
        AnimatedVisibility(controlsVisible && !zoomMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    color = islandColor(), contentColor = islandContentColor(), shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) { IconButton(onClick = { if (manualFullscreen) manualFullscreen = false else close() }) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back")
                } }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    ViewerFilmstrip(entries, selectedUri, filmstripState, select)
                    actions(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    } else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewerHeader(selected, close)
        pager(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp))
        ViewerFilmstrip(entries, selectedUri, filmstripState, select)
        actions(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
    val aiTags = labelsByPhoto[selected.photoKey].orEmpty().joinToString(" · ").ifBlank { "Not analyzed yet" }
    if (detailsOpen) ModalBottomSheet(onDismissRequest = { detailsOpen = false }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            Text("Name: ${selected.name}")
            Text("File: ${selected.relativePath ?: "Unavailable"}")
            if (!hasLocationAccess) Button(onClick = requestLocationAccess) { Text("Allow photo locations") }
            else location?.let { photoLocation ->
                Text("Place: ${photoLocation.placeName ?: "Not named"}")
                PhotoLocationPreview(photoLocation)
                Text("Coordinates: ${"%.5f".format(photoLocation.latitude)}, ${"%.5f".format(photoLocation.longitude)}")
                Button(onClick = { openMap(selected) }) { Text("Show on map") }
            } ?: Text("No embedded location in this photo.")
            Text("Date: ${formatPhotoDateTime(selected.takenMillis)}")
            Text("Size: ${selected.detail.substringAfter(" · ", selected.detail)}")
            Text("AI tags (English): $aiTags")
            peopleNamesByPhoto[selected.photoKey]?.takeIf { it.isNotEmpty() }?.let { Text("People: ${it.joinToString(" · ")}") }
        }
    }
    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
}

@Composable
private fun ViewerHeader(entry: Entry, close: () -> Unit) = Surface(
    color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
        Column(Modifier.weight(1f)) {
            Text(formatPhotoDateTime(entry.takenMillis), style = MaterialTheme.typography.titleSmall)
            Text(entry.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ViewerFilmstrip(entries: List<Entry>, selectedUri: Uri, state: androidx.compose.foundation.lazy.LazyListState, select: (Uri) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(entries, key = { it.contentUri.toString() }) { entry ->
            FilmstripThumbnail(entry, entry.contentUri == selectedUri) { entry.contentUri?.let(select) }
        }
    }
}

@Composable
private fun ViewerPager(
    entries: List<Entry>, state: PagerState, playback: MutableMap<String, VideoPlaybackState>,
    imageZoom: MutableMap<String, ViewerZoomState>, zoomMode: Boolean, modifier: Modifier,
    background: Color, toggleControls: () -> Unit, showDetails: () -> Unit,
) {
    HorizontalPager(state = state, modifier = modifier, userScrollEnabled = !zoomMode, key = { entries[it].contentUri.toString() }) { page ->
        val entry = entries[page]
        if (entry.isVideo) {
            if (page == state.currentPage) ViewerVideo(entry, Modifier.fillMaxSize(), playback.getOrPut(entry.photoKey) { VideoPlaybackState() }, toggleControls)
            else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        } else ViewerImage(entry, Modifier.fillMaxSize(), toggleControls, showDetails, background, imageZoom.getOrPut(entry.photoKey) { ViewerZoomState() })
    }
}

@Composable
private fun ViewerActionsIsland(
    favorite: Boolean, isVideo: Boolean, fullscreen: Boolean,
    share: () -> Unit, details: () -> Unit, toggleFavorite: () -> Unit, add: (() -> Unit)?,
    restore: (() -> Unit)?, removeFromCollection: (() -> Unit)?, edit: () -> Unit,
    toggleFullscreen: () -> Unit, modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(22.dp).clickable { expanded = !expanded }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(islandContentColor().copy(alpha = 0.72f)))
            }
            AnimatedVisibility(expanded) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = share) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                    IconButton(onClick = details) { Icon(painterResource(R.drawable.ic_info), contentDescription = "Details") }
                    IconButton(onClick = toggleFavorite) { Icon(painterResource(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border), contentDescription = "Toggle favorite") }
                    add?.let { action -> IconButton(onClick = action) {
                        Icon(painterResource(R.drawable.ic_add_to_collection), contentDescription = "Add to collection")
                    } }
                    restore?.let { action -> IconButton(onClick = action) { Icon(painterResource(R.drawable.ic_restore), contentDescription = "Restore from Hidden") } }
                    removeFromCollection?.let { action -> IconButton(onClick = action) {
                        Icon(painterResource(R.drawable.ic_remove_from_collection), contentDescription = "Remove from this collection")
                    } }
                    IconButton(onClick = edit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                    if (isVideo) IconButton(onClick = toggleFullscreen) {
                        Icon(painterResource(if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen), contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerImage(
    entry: Entry,
    modifier: Modifier = Modifier,
    toggleControls: () -> Unit = {},
    showDetails: () -> Unit = {},
    background: Color = Color.Unspecified,
    zoom: ViewerZoomState? = null,
) {
    val context = LocalContext.current
    val zoomState = zoom ?: remember(entry.contentUri) { ViewerZoomState() }
    val viewerBackground = if (background == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant else background
    BoxWithConstraints(modifier.background(viewerBackground)) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val targetHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val transform = rememberTransformableState { _, zoomChange, pan, _ ->
            val nextScale = (zoomState.scale * zoomChange).coerceIn(1f, 5f)
            val maxX = (nextScale - 1f) * targetWidth / 2f
            val maxY = (nextScale - 1f) * targetHeight / 2f
            zoomState.scale = nextScale
            zoomState.offset = if (nextScale == 1f) Offset.Zero else Offset(
                (zoomState.offset.x + pan.x).coerceIn(-maxX, maxX),
                (zoomState.offset.y + pan.y).coerceIn(-maxY, maxY),
            )
        }
        val bitmap by produceState<Bitmap?>(initialValue = null, entry.contentUri, targetWidth, targetHeight) {
            value = entry.contentUri?.let { uri ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            val source = info.size
                            val scale = minOf(1f, targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
                            decoder.setTargetSize(
                                (source.width * scale).roundToInt().coerceAtLeast(1),
                                (source.height * scale).roundToInt().coerceAtLeast(1),
                            )
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }.getOrNull()
                }
            }
        }
        val infoGesture = if (zoomState.scale == 1f) Modifier.pointerInput(entry.contentUri) {
            var verticalDrag = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { _, amount -> verticalDrag += amount },
                onDragEnd = { if (verticalDrag < -80f) showDetails() },
            )
        } else Modifier
        val image = bitmap
        if (image == null) Text("Photo unavailable", modifier = Modifier.padding(16.dp)) else androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(),
            contentDescription = entry.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(scaleX = zoomState.scale, scaleY = zoomState.scale, translationX = zoomState.offset.x, translationY = zoomState.offset.y)
                .transformable(state = transform, canPan = { zoomState.scale > 1f })
                .then(infoGesture)
                .pointerInput(entry.contentUri) {
                    detectTapGestures(
                        onTap = { if (zoomState.scale == 1f) toggleControls() },
                        onDoubleTap = {
                            if (zoomState.scale > 1f) zoomState.reset() else zoomState.scale = 2f
                        },
                    )
                },
        )
    }
}

private class VideoPlaybackState(var positionMs: Int = 0, var playWhenReady: Boolean = true)

@Composable
private fun ViewerVideo(entry: Entry, modifier: Modifier = Modifier, playback: VideoPlaybackState, toggleControls: () -> Unit = {}) {
    val uri = entry.contentUri ?: return
    key(uri) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    val controller = MediaController(viewContext)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnClickListener { toggleControls() }
                    setVideoURI(uri)
                    setOnPreparedListener {
                        if (playback.positionMs > 0) seekTo(playback.positionMs)
                        if (playback.playWhenReady) start()
                    }
                    setOnCompletionListener {
                        playback.positionMs = 0
                        playback.playWhenReady = false
                    }
                }
            },
            modifier = modifier.background(Color.Black),
            onRelease = { video ->
                playback.positionMs = video.currentPosition
                playback.playWhenReady = video.isPlaying
                video.stopPlayback()
            },
        )
    }
}

@Composable
private fun FilmstripThumbnail(entry: Entry, selected: Boolean, choose: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.contentUri) {
        value = entry.contentUri?.let { uri ->
            withContext(Dispatchers.IO) { runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(120, 120), null) }.getOrNull() }
        }
    }
    Box(
        Modifier.size(52.dp).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = choose)
            .semantics {
                contentDescription = "View ${entry.name}"
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
    ) {
        val image = bitmap
        if (image != null) androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        if (selected) Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
    }
}

private fun sharePhotos(context: Context, entries: List<Entry>) {
    val uris = entries.mapNotNull { it.contentUri }
    if (uris.isEmpty()) return
    context.startActivity(Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = context.contentResolver.getType(uris.first()) ?: "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri("photo", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.let { Intent.createChooser(it, "Share photos") })
}

private fun formatPhotoDateTime(takenMillis: Long): String = if (takenMillis > 0) {
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(takenMillis).atZone(ZoneId.systemDefault()))
} else "Date unavailable"

private fun listPhotos(context: Context): List<Entry> = (
    listGalleryMedia(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false) +
        listGalleryMedia(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
).sortedByDescending { it.takenMillis }

private fun listGalleryMedia(context: Context, collection: Uri, isVideo: Boolean): List<Entry> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_MODIFIED,
    )
    val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    return context.contentResolver.query(collection, projection, selection,
        arrayOf("DCIM/%", "Pictures/Screenshots/%", "DCIM/Screenshots/%"), null)?.use { cursor ->
        buildList { while (cursor.moveToNext()) {
            val path = cursor.string(2)
            val sizeBytes = cursor.long(3)
            val uri = android.content.ContentUris.withAppendedId(collection, cursor.long(0))
            val takenMillis = cursor.long(4).takeIf { it > 0 } ?: cursor.long(5) * 1000
            add(Entry(
                name = cursor.string(1),
                detail = "$path · $sizeBytes bytes",
                relativePath = path,
                contentUri = uri,
                takenMillis = takenMillis,
                sizeBytes = sizeBytes,
                photoKey = PhotoMetadataRules.stableKey(uri.toString(), path, cursor.string(1), sizeBytes),
                isVideo = isVideo,
            ))
        } }
    } ?: error("MediaStore query failed")
}

private fun Cursor.string(index: Int) = getString(index) ?: "Unnamed"
private fun Cursor.long(index: Int) = if (isNull(index)) 0L else getLong(index)
