package com.kalotrapezis.drive

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PowerPoint's viewer: the slides one under the other (a slide tapped: presented from there), their speaker notes under
 * them when asked, Present — full screen, sideways, a swipe or a tap on the right for the next slide, on the left the one
 * before. Animations and transitions: later, with the user watching.
 */
@Composable
internal fun SlidesScreen(back: () -> Unit, open: String) {
    val context = LocalContext.current
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    var deck by remember { mutableStateOf<Deck?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(false) }
    var presenting by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(open) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Slides: open .${f.extension} of ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) { runCatching { WriterLog.timed("Slides: deck read") { Slides.read(f) } } }
            .onSuccess { deck = it; WriterLog.i("Slides: ${it.slides.size} slides, ${it.slides.sumOf { s -> s.items.size }} shapes") }
            .onFailure { WriterLog.e("Slides: could not open", it); failed = "This presentation could not be opened." }
    }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().background(Color(0xFF2B2B2B))) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(file?.name ?: "Presentation", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    deck?.let { Text("${it.slides.size} slide${if (it.slides.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.7f)) }
                }
                IconButton(onClick = { notes = !notes }, enabled = deck != null) {
                    Icon(painterResource(R.drawable.ic_comment), contentDescription = if (notes) "Hide the notes" else "Show the notes", tint = if (notes) POWERPOINT_ORANGE else islandContentColor())
                }
                IconButton(onClick = { presenting = 0 }, enabled = deck?.slides?.isNotEmpty() == true) { Icon(painterResource(R.drawable.ic_play), contentDescription = "Present") }
                file?.let { f ->
                    IconButton(onClick = { shareDriveFile(context, f) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                    IconButton(onClick = { openElsewhere(context, f) }) { Icon(painterResource(R.drawable.ic_open_with), contentDescription = "Open in another app") }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = deck
            when {
                failed != null -> Text(failed!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                d == null -> Text("Opening…", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val list = rememberLazyListState()
                    val at by remember { derivedStateOf { list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0 && list.layoutInfo.visibleItemsInfo.size > 1) 1 else 0 } }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(d.slides) { i, s ->
                            Column {
                                Text("${i + 1}${if (s.hidden) " · hidden" else ""}", color = Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                                SlideCanvas(d, s, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { presenting = i })
                                if (notes && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFF3A3A3A), RoundedCornerShape(6.dp)).padding(10.dp))
                            }
                        }
                    }
                    Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
                        Text("Slide ${(at + 1).coerceAtMost(d.slides.size)} of ${d.slides.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
    val d = deck
    presenting?.let { start -> if (d != null) Present(d, start, notes) { presenting = null } }
}

/** One slide, as wide as it is given, its own shape. */
@Composable
private fun SlideCanvas(deck: Deck, slide: Slide, modifier: Modifier) {
    Canvas(modifier.aspectRatio(deck.width / deck.height).background(Color.White)) {
        drawIntoCanvas { c -> Slides.draw(c.nativeCanvas, deck, slide, size.width / deck.width) }
    }
}

/**
 * Present: the slides full screen and sideways on black, one at a time — swiped, or tapped on the right (next) and the
 * left (back); the shown slides only (hidden ones skipped); back to the list with Back.
 */
@Composable
private fun Present(deck: Deck, start: Int, notes: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    val shown = remember(deck) { deck.slides.indices.filter { !deck.slides[it].hidden || it == start } }
    val pager = rememberPagerState(initialPage = shown.indexOf(start).coerceAtLeast(0)) { shown.size }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val was = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WriterLog.i("Slides: presenting from ${start + 1}")
        onDispose { if (was != null) activity.requestedOrientation = was }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // nothing over the slides: the status and gesture bars hidden (a swipe from the edge shows them a moment)
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val s = deck.slides[shown[page]]
                Box(Modifier.fillMaxSize().pointerInput(page) {
                    detectTapGestures { o -> scope.launch { if (o.x > size.width / 2) { if (page < shown.lastIndex) pager.animateScrollToPage(page + 1) else close() } else if (page > 0) pager.animateScrollToPage(page - 1) } }
                }, contentAlignment = Alignment.Center) {
                    SlideCanvas(deck, s, Modifier.fillMaxSize().aspectRatio(deck.width / deck.height, matchHeightConstraintsFirst = true))
                    if (notes && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(8.dp))
                }
            }
            Text("${pager.currentPage + 1} / ${shown.size}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
    }
}
