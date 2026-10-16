package com.kalotrapezis.drive

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
    val scope = rememberCoroutineScope()
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    var deck by remember { mutableStateOf<Deck?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(false) }
    var presenting by remember { mutableStateOf<Int?>(null) }
    // editing (a .pptx): the edits apart from the deck as read; Undo the edit before; a slide chosen; a text box's words being typed
    var editing by remember { mutableStateOf(JustMade.take(open)) } // a file just made with New: editing at once
    var edit by remember { mutableStateOf<SlidesEdit.Edit?>(null) }
    val undos = remember { ArrayList<SlidesEdit.Edit>() }
    var saved by remember { mutableStateOf<SlidesEdit.Edit?>(null) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var typing by remember { mutableStateOf<Pair<SlideItem.Shape, androidx.compose.ui.text.input.TextFieldValue>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var layouts by remember { mutableStateOf(false) } // New slide: the deck's layouts to choose from
    suspend fun load(f: File) = withContext(Dispatchers.IO) { runCatching { WriterLog.timed("Slides: deck read") { Slides.read(f) } } }
    LaunchedEffect(open) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Slides: open .${f.extension} of ${f.length() / 1024} KB")
        load(f).onSuccess { deck = it; edit = SlidesEdit.start(it); saved = edit; WriterLog.i("Slides: ${it.slides.size} slides, ${it.slides.sumOf { s -> s.items.size }} shapes") }
            .onFailure { WriterLog.e("Slides: could not open", it); failed = "This presentation could not be opened." }
    }
    val changed = edit != saved
    fun change(to: SlidesEdit.Edit) { edit?.let { undos += it; if (undos.size > 100) undos.removeAt(0) }; edit = to }
    fun save(then: (() -> Unit)? = null) {
        val f = file ?: return; val d = deck ?: return; val e = edit ?: return
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val temp = File(f.parentFile, ".${f.name}.tetra-saving")
                try {
                    WriterLog.timed("Slides: saved ${e.order.size} slides, ${e.texts.size} text boxes") { SlidesEdit.save(f, d, e, temp) }
                    keepPrevious(context, f) // Writer's versions
                    check(temp.renameTo(f)) { "Could not save ${f.name}." }
                } finally { temp.delete() }
                Slides.read(f)
            } }
            saving = false
            result.onSuccess { nd -> deck = nd; edit = SlidesEdit.start(nd); saved = edit; undos.clear(); chosen = null; message = "Saved"; then?.invoke() }
                .onFailure { WriterLog.e("Slides: could not save", it); message = it.message ?: "Could not save." }
        }
    }
    var shapes by remember { mutableStateOf(false) } // Shape: the shapes to choose from
    var item by remember { mutableStateOf<String?>(null) } // a picture or shape put on the chosen slide, to move, size or delete
    /** [a] put on the chosen slide, then chosen. */
    fun put(a: SlidesEdit.Added) {
        val e = edit ?: return; val c = chosen ?: return; val uid = e.order[c].uid
        change(e.copy(added = e.added + (uid to (e.added[uid].orEmpty() + a)))); item = a.id
    }
    /** The chosen added thing changed by [f] (moved, sized), or gone (null). */
    fun alter(f: (SlidesEdit.Added) -> SlidesEdit.Added?) {
        val e = edit ?: return; val c = chosen ?: return; val uid = e.order[c].uid; val id = item ?: return
        change(e.copy(added = e.added + (uid to e.added[uid].orEmpty().mapNotNull { if (it.id == id) f(it) else it })))
    }
    // Picture: the phone's photo picker; a copy kept on the phone until Save puts it in the file
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        val d = deck ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val made = withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                val mime = o.outMimeType ?: ""
                val dir = File(context.cacheDir, "slide-pictures").apply { mkdirs() }
                // PNG and JPEG as they are; another kind (HEIC, WebP) made a JPEG, as PowerPoint can show it
                val (ext, data) = when (mime) { "image/png" -> "png" to bytes; "image/jpeg" -> "jpeg" to bytes
                    else -> "jpeg" to java.io.ByteArrayOutputStream().also { out -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }.toByteArray() }
                val f = File(dir, "${System.nanoTime()}.$ext").apply { writeBytes(data) }
                val k = minOf(d.width * 0.6f / o.outWidth.coerceAtLeast(1), d.height * 0.6f / o.outHeight.coerceAtLeast(1))
                Triple(f, o.outWidth * k, o.outHeight * k) to ext
            }.onFailure { WriterLog.e("Slides: a picture could not be read", it) }.getOrNull() }
            val e = edit; val c = chosen
            if (made == null || e == null || c == null) { message = "That picture could not be read."; return@launch }
            val (f, w, h) = made.first
            put(SlidesEdit.Added(e.newId(e.order[c].uid), (d.width - w) / 2, (d.height - h) / 2, w, h, image = f.path, ext = made.second))
            WriterLog.i("Slides: a picture put on a slide")
        }
    }
    fun leave() { if (changed) leaving = true else back() }
    BackHandler { when { typing != null -> typing = null; layouts -> layouts = false; shapes -> shapes = false; item != null -> item = null; chosen != null -> chosen = null; else -> leave() } }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(2500); message = null } }
    val view = remember(deck, edit) { deck?.let { d -> edit?.let { SlidesEdit.view(d, it) } ?: d } }
    Column(Modifier.fillMaxSize().background(Color(0xFF2B2B2B))) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::leave) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text((file?.name ?: "Presentation") + if (changed) " •" else "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    view?.let { Text(if (editing) "Editing" else "${it.slides.size} slide${if (it.slides.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.7f)) }
                }
                if (editing) {
                    IconButton(onClick = { undos.removeLastOrNull()?.let { edit = it; chosen = chosen?.coerceAtMost((it.order.size - 1).coerceAtLeast(0)) } }, enabled = undos.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo") }
                    IconButton(onClick = { save() }, enabled = changed && !saving) { Icon(painterResource(R.drawable.ic_save), contentDescription = "Save") }
                    IconButton(onClick = { editing = false; chosen = null; typing = null }) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Done") }
                } else {
                    // a deck Tetra can save into: a .pptx of its own, writable
                    if (deck != null && file?.extension?.lowercase() == "pptx" && file.canWrite()) IconButton(onClick = { editing = true; WriterLog.i("Slides: editing") }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                    IconButton(onClick = { notes = !notes }, enabled = deck != null) {
                        Icon(painterResource(R.drawable.ic_comment), contentDescription = if (notes) "Hide the notes" else "Show the notes", tint = if (notes) POWERPOINT_ORANGE else islandContentColor())
                    }
                    IconButton(onClick = { presenting = 0 }, enabled = view?.slides?.isNotEmpty() == true) { Icon(painterResource(R.drawable.ic_play), contentDescription = "Present") }
                    file?.let { f ->
                        IconButton(onClick = { shareDriveFile(context, f) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                        IconButton(onClick = { openElsewhere(context, f) }) { Icon(painterResource(R.drawable.ic_open_with), contentDescription = "Open in another app") }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = view
            when {
                failed != null -> Text(failed!!, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                d == null -> Text("Opening…", color = Color.LightGray, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val list = rememberLazyListState()
                    val at by remember { derivedStateOf { list.firstVisibleItemIndex + if (list.firstVisibleItemScrollOffset > 0 && list.layoutInfo.visibleItemsInfo.size > 1) 1 else 0 } }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(d.slides) { i, s ->
                            Column {
                                Text("${i + 1}${if (s.hidden) " · hidden" else ""}", color = if (chosen == i) POWERPOINT_ORANGE else Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                                // viewing: a slide tapped is presented from there; editing: tapped, it is chosen — tapped again on a text box, its words are typed
                                SlideCanvas(d, s, hints = editing, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    .then(if (chosen == i) Modifier.border(3.dp, POWERPOINT_ORANGE, RoundedCornerShape(4.dp)) else Modifier)
                                    .pointerInput(editing, chosen, i, s) {
                                        detectTapGestures { o ->
                                            if (!editing) { presenting = i; return@detectTapGestures }
                                            if (chosen != i) { chosen = i; typing = null; item = null; return@detectTapGestures }
                                            val x = o.x / size.width * d.width; val y = o.y / size.height * d.height
                                            // a picture or shape put on it: chosen, to move or size
                                            edit?.let { ee -> ee.added[ee.order[i].uid].orEmpty().lastOrNull { a -> x in a.x..(a.x + a.w) && y in a.y..(a.y + a.h) } }?.let { a -> item = a.id; typing = null; return@detectTapGestures }
                                            val hit = s.items.lastOrNull { it is SlideItem.Shape && it.id != null && it.text != null && x in it.x..(it.x + it.w) && y in it.y..(it.y + it.h) } as SlideItem.Shape?
                                            if (hit != null) { val t = if (hit.hint) "" else SlidesEdit.lines(hit).joinToString("\n"); typing = hit to androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)) }
                                            else message = "Tap a text box on the slide to change its words"
                                        }
                                    })
                                if (notes && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFF3A3A3A), RoundedCornerShape(6.dp)).padding(10.dp))
                            }
                        }
                    }
                    if (!editing || chosen == null) Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
                        Text(if (editing) "Tap a slide to change it" else "Slide ${(at + 1).coerceAtMost(d.slides.size)} of ${d.slides.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    // the chosen slide's tools, in a strip that slides up (as Writer's galleries)
                    val e = edit
                    androidx.compose.animation.AnimatedVisibility(editing && chosen != null && typing == null && !layouts && !shapes && item == null && e != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val c = (chosen ?: 0).coerceIn(0, (e?.order?.size ?: 1) - 1)
                        val w = islandContentColor()
                        Surface(color = Color.Black, contentColor = w, modifier = Modifier.fillMaxWidth()) { // solid, as Excel's ribbon
                            Row(Modifier.navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { chosen = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                                Text("Slide ${c + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                                if (e != null) {
                                    val entry = e.order[c]
                                    if (deck?.layouts?.isNotEmpty() == true) RibbonButton("New slide", w, R.drawable.ic_add) { layouts = true }
                                    RibbonButton("Picture", w, R.drawable.ic_gallery) { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    RibbonButton("Shape", w, R.drawable.ic_category) { shapes = true }
                                    RibbonButton("Duplicate", w, R.drawable.ic_copy) { change(e.copy(order = e.order.toMutableList().apply { add(c + 1, SlidesEdit.Entry(entry.src, e.next(), entry.layout)) }, texts = e.texts + e.texts.filterKeys { it.first == entry.uid }.mapKeys { (e.next()) to it.key.second })); chosen = c + 1 }
                                    RibbonButton("Move up", w, R.drawable.ic_keyboard_arrow_up, enabled = c > 0) { change(e.copy(order = e.order.toMutableList().apply { add(c - 1, removeAt(c)) })); chosen = c - 1 }
                                    RibbonButton("Move down", w, R.drawable.ic_keyboard_arrow_down, enabled = c < e.order.lastIndex) { change(e.copy(order = e.order.toMutableList().apply { add(c + 1, removeAt(c)) })); chosen = c + 1 }
                                    val hidden = e.hidden[entry.uid] ?: deck?.slides?.getOrNull(entry.src)?.hidden ?: false // a new slide is shown
                                    RibbonButton(if (hidden) "Show" else "Hide", w, if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off) { change(e.copy(hidden = e.hidden + (entry.uid to !hidden))) }
                                    RibbonButton("Delete", w, R.drawable.ic_delete, enabled = e.order.size > 1) { change(e.copy(order = e.order.filterIndexed { k, _ -> k != c })); chosen = null }
                                }
                            }
                        }
                    }
                    // New slide: the deck's layouts in the strip's place, each a small slide; the one tapped comes after the chosen slide
                    androidx.compose.animation.AnimatedVisibility(editing && layouts && e != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val full = deck
                        Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.navigationBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { layouts = false }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel") }
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 12.dp)) {
                                    if (full != null) itemsIndexed(full.layouts) { l, layout ->
                                        Column(Modifier.width(132.dp).clickable {
                                            val ee = edit ?: return@clickable; val c = (chosen ?: -1) + 1
                                            change(ee.copy(order = ee.order.toMutableList().apply { add(c.coerceIn(0, size), SlidesEdit.Entry(-1, ee.next(), l)) }))
                                            chosen = c; layouts = false; WriterLog.i("Slides: a new slide (${layout.name})")
                                        }) {
                                            Text(layout.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
                                            SlideCanvas(full, layout.preview, Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), hints = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Shape: Office's everyday shapes, each drawn in a card; the one tapped goes in the slide's middle, in the theme's accent
                    androidx.compose.animation.AnimatedVisibility(editing && shapes && e != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.navigationBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { shapes = false }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel") }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (kind in listOf("rect", "roundRect", "ellipse", "triangle", "rightArrow", "star5", "heart")) {
                                        val shape = remember(kind) { ShapePicture(kind, 0xFF4472C4.toInt(), 0xFF2F5597.toInt(), 1.5f) }
                                        Canvas(Modifier.size(64.dp).background(Color.White, RoundedCornerShape(8.dp)).clickable {
                                            val dd = deck ?: return@clickable; val ee = edit ?: return@clickable; val c = chosen ?: return@clickable
                                            val side = dd.height * 0.3f
                                            put(SlidesEdit.Added(ee.newId(ee.order[c].uid), (dd.width - side) / 2, (dd.height - side) / 2, side, side, prst = kind, fill = 0xFF4472C4.toInt()))
                                            shapes = false; WriterLog.i("Slides: a shape put on a slide ($kind)")
                                        }) { drawIntoCanvas { shape.drawAt(it.nativeCanvas, 10f, 10f, size.width - 20f, size.height - 20f) } }
                                    }
                                }
                            }
                        }
                    }
                    // a picture or shape put on the slide: moved, made bigger or smaller, deleted
                    androidx.compose.animation.AnimatedVisibility(editing && item != null && typing == null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val w = islandContentColor(); val dd = deck
                        Surface(color = Color.Black, contentColor = w, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { item = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
                                val step = (dd?.width ?: 720f) / 40
                                RibbonButton("Left", w, R.drawable.ic_chevron_left) { alter { it.copy(x = it.x - step) } }
                                RibbonButton("Right", w, R.drawable.ic_chevron_right) { alter { it.copy(x = it.x + step) } }
                                RibbonButton("Up", w, R.drawable.ic_keyboard_arrow_up) { alter { it.copy(y = it.y - step) } }
                                RibbonButton("Down", w, R.drawable.ic_keyboard_arrow_down) { alter { it.copy(y = it.y + step) } }
                                RibbonButton("Bigger", w, R.drawable.ic_add) { alter { val k = 1.15f; it.copy(x = it.x - it.w * (k - 1) / 2, y = it.y - it.h * (k - 1) / 2, w = it.w * k, h = it.h * k) } }
                                RibbonButton("Smaller", w, R.drawable.ic_remove) { alter { val k = 1 / 1.15f; it.copy(x = it.x + it.w * (1 - k) / 2, y = it.y + it.h * (1 - k) / 2, w = it.w * k, h = it.h * k) } }
                                RibbonButton("Delete", w, R.drawable.ic_delete) { alter { null }; item = null }
                            }
                        }
                    }
                    // a text box's words: a line a paragraph, ✕ leaves them, ✓ puts them in (one step of Undo)
                    androidx.compose.animation.AnimatedVisibility(typing != null, Modifier.align(Alignment.BottomCenter).imePadding(), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val t = typing
                        Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { typing = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave it as it was") }
                                if (t != null) androidx.compose.material3.OutlinedTextField(t.second, { typing = t.first to it }, modifier = Modifier.weight(1f).heightIn(max = 220.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), label = { Text("The words (a line a paragraph)") })
                                IconButton(onClick = {
                                    val ee = edit; val c = chosen
                                    if (t != null && ee != null && c != null) {
                                        val uid = ee.order[c].uid
                                        change(ee.copy(texts = ee.texts + ((uid to t.first.id!!) to t.second.text.split("\n"))))
                                        WriterLog.i("Slides: a text box changed")
                                    }
                                    typing = null
                                }) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Put them in") }
                            }
                        }
                    }
                    message?.let { m -> Surface(color = Color.Black.copy(alpha = 0.8f), contentColor = Color.White, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) { Text(m, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) } }
                }
            }
        }
    }
    if (leaving) androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false },
        title = { Text("Save the changes?") },
        text = { Text("${undos.size} change${if (undos.size == 1) "" else "s"} to ${file?.name ?: "this presentation"} are not saved.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; save { back() } }) { Text("Save") } },
        dismissButton = { Row {
            androidx.compose.material3.TextButton(onClick = { leaving = false; WriterLog.i("Slides: left without saving"); back() }) { Text("Don't save") }
            androidx.compose.material3.TextButton(onClick = { leaving = false }) { Text("Cancel") }
        } })
    val d = view
    presenting?.let { start -> if (d != null) Present(d, start, notes) { presenting = null } }
}

/** One slide, as wide as it is given, its own shape. */
@Composable
private fun SlideCanvas(deck: Deck, slide: Slide, modifier: Modifier, hints: Boolean = false) {
    Canvas(modifier.aspectRatio(deck.width / deck.height).background(Color.White)) {
        drawIntoCanvas { c -> Slides.draw(c.nativeCanvas, deck, slide, size.width / deck.width, hints) }
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
    // PowerPoint's presenting tools: the pen (ink kept per slide while presenting), the laser (a red dot under the finger), a black screen
    var tool by remember { mutableStateOf<String?>(null) }
    var black by remember { mutableStateOf(false) }
    val ink = remember { androidx.compose.runtime.mutableStateMapOf<Int, List<List<androidx.compose.ui.geometry.Offset>>>() }
    var laser by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) } // as a part of the slide (0–1)
    // a TV (HDMI, a cast screen): the slide shown there, the phone the presenter's — the slide and its notes
    val displays = remember { context.getSystemService(android.hardware.display.DisplayManager::class.java) }
    var tvDisplay by remember { mutableStateOf(displays?.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull()) }
    DisposableEffect(displays) {
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            fun again() { tvDisplay = displays?.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull() }
            override fun onDisplayAdded(id: Int) = again()
            override fun onDisplayRemoved(id: Int) = again()
            override fun onDisplayChanged(id: Int) = Unit
        }
        displays?.registerDisplayListener(listener, null)
        onDispose { displays?.unregisterDisplayListener(listener) }
    }
    val tv = remember(tvDisplay) { tvDisplay?.let { d -> runCatching { TvSlides(context, d, deck).also { it.show(); WriterLog.i("Slides: on a second screen") } }
        .onFailure { WriterLog.w("Slides: the second screen could not show", it) }.getOrNull() } }
    DisposableEffect(tv) { onDispose { tv?.dismiss() } }
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
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = tool == null) { page ->
                val s = deck.slides[shown[page]]
                Box(Modifier.fillMaxSize().pointerInput(page, tool) {
                    if (tool == null) detectTapGestures { o -> scope.launch { if (o.x > size.width / 2) { if (page < shown.lastIndex) pager.animateScrollToPage(page + 1) else close() } else if (page > 0) pager.animateScrollToPage(page - 1) } }
                }, contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().aspectRatio(deck.width / deck.height, matchHeightConstraintsFirst = true).pointerInput(page, tool) {
                        // the pen draws (points as parts of the slide, so the ink stays put); the laser follows the finger
                        if (tool != null) detectDragGestures(
                            onDragStart = { o -> if (tool == "pen") ink[page] = ink[page].orEmpty() + listOf(listOf(androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height)))
                                else laser = androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height) },
                            onDragEnd = { laser = null }, onDragCancel = { laser = null },
                        ) { change, _ ->
                            val o = change.position
                            if (tool == "pen") ink[page] = ink[page].orEmpty().let { strokes -> strokes.dropLast(1) + listOf(strokes.last() + androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height)) }
                            else laser = androidx.compose.ui.geometry.Offset(o.x / size.width, o.y / size.height)
                        }
                    }) {
                        SlideCanvas(deck, s, Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            for (stroke in ink[page].orEmpty()) for (i in 1 until stroke.size)
                                drawLine(Color(0xFFE53935), androidx.compose.ui.geometry.Offset(stroke[i - 1].x * size.width, stroke[i - 1].y * size.height),
                                    androidx.compose.ui.geometry.Offset(stroke[i].x * size.width, stroke[i].y * size.height), strokeWidth = size.width / 250, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            if (pager.currentPage == page) laser?.let { l -> val p = androidx.compose.ui.geometry.Offset(l.x * size.width, l.y * size.height)
                                drawCircle(Color(0x55FF1744), size.width / 60, p); drawCircle(Color(0xFFFF1744), size.width / 140, p) }
                        }
                    }
                    if ((notes || tv != null) && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = if (tv != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                        maxLines = if (tv != null) 8 else 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(8.dp))
                }
            }
            Text("${pager.currentPage + 1} / ${shown.size}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
            // the TV shows what the phone does: the slide (black when the phone's is), its ink, the laser
            LaunchedEffect(tv, pager.currentPage, black, ink.toMap(), laser) {
                tv?.apply { slide = if (black) null else deck.slides[shown[pager.currentPage]]; strokes = ink[pager.currentPage].orEmpty(); dot = laser; update() }
            }
            if (tv != null) Text("On the second screen", color = Color(0xFF6FCF97), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
            // the tools, faint in a corner: Pen, Laser, Black screen (a tap on the black brings the slide back)
            Row(Modifier.align(Alignment.TopStart).padding(4.dp)) {
                @Composable fun tool(name: String, icon: Int, on: Boolean, click: () -> Unit) = IconButton(onClick = click) {
                    Icon(painterResource(icon), contentDescription = name, tint = if (on) Color(0xFFFF5252) else Color.White.copy(alpha = 0.45f))
                }
                tool("Pen", R.drawable.ic_ink_pen, tool == "pen") { tool = if (tool == "pen") null else "pen"; WriterLog.i("Slides: pen ${tool != null}") }
                tool("Laser", R.drawable.ic_highlighter, tool == "laser") { tool = if (tool == "laser") null else "laser" }
                if (ink[pager.currentPage].orEmpty().isNotEmpty()) tool("Wipe the ink", R.drawable.ic_ink_eraser, false) { ink.remove(pager.currentPage) }
                tool("Black screen", R.drawable.ic_visibility_off, black) { black = true }
            }
            if (black) Box(Modifier.fillMaxSize().background(Color.Black).clickable { black = false })
        }
    }
}

/** The slides on a second screen (a TV by HDMI or a cast): drawn as the phone shows them, with the pen's ink and the laser. */
private class TvSlides(context: android.content.Context, display: android.view.Display, val deck: Deck) : android.app.Presentation(context, display) {
    var slide: Slide? = null
    var strokes: List<List<androidx.compose.ui.geometry.Offset>> = emptyList()
    var dot: androidx.compose.ui.geometry.Offset? = null
    private val view = object : android.view.View(context) {
        val red = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt(); strokeCap = android.graphics.Paint.Cap.ROUND }
        override fun onDraw(c: android.graphics.Canvas) {
            c.drawColor(android.graphics.Color.BLACK)
            val s = slide ?: return
            val k = minOf(width / deck.width, height / deck.height)
            val w = deck.width * k; val h = deck.height * k
            c.save(); c.translate((width - w) / 2, (height - h) / 2)
            Slides.draw(c, deck, s, k)
            red.strokeWidth = w / 250; red.alpha = 255
            for (st in strokes) for (i in 1 until st.size) c.drawLine(st[i - 1].x * w, st[i - 1].y * h, st[i].x * w, st[i].y * h, red)
            dot?.let { d -> red.alpha = 0x55; c.drawCircle(d.x * w, d.y * h, w / 60, red); red.alpha = 255; c.drawCircle(d.x * w, d.y * h, w / 140, red) }
            c.restore()
        }
    }
    override fun onCreate(savedInstanceState: android.os.Bundle?) { super.onCreate(savedInstanceState); setContentView(view) }
    fun update() = view.invalidate()
}
