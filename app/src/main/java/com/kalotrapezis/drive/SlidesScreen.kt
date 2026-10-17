package com.kalotrapezis.drive

import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    remember { OfficeFonts.assets = context.applicationContext.assets } // the fonts with Word's letter widths, as Words' and Tables'

    val scope = rememberCoroutineScope()
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    val odp = file?.extension.equals("odp", true)
    var deck by remember { mutableStateOf<Deck?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(false) }
    var presenting by remember { mutableStateOf<Int?>(null) }
    // editing (a .pptx): the edits apart from the deck as read; Undo the edit before; a slide chosen; a text box's words being typed
    var editing by remember { mutableStateOf(JustMade.take(open)) } // a file just made with New: editing at once
    var edit by remember { mutableStateOf<SlidesEdit.Edit?>(null) }
    val undos = remember { ArrayList<SlidesEdit.Edit>() }
    val redos = remember { ArrayList<SlidesEdit.Edit>() }
    var saved by remember { mutableStateOf<SlidesEdit.Edit?>(null) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var typing by remember { mutableStateOf<Pair<SlideItem.Shape, androidx.compose.ui.text.input.TextFieldValue>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var layouts by remember { mutableStateOf(false) } // New slide: the deck's layouts to choose from
    var source by remember { mutableStateOf<File?>(null) } // File › Versions: the version opened, saved over the file by Save
    var showVersions by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    suspend fun load(f: File) = withContext(Dispatchers.IO) { runCatching { WriterLog.timed("Slides: deck read") { Slides.read(f) } } }
    LaunchedEffect(open) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Slides: open .${f.extension} of ${f.length() / 1024} KB")
        load(f).onSuccess { deck = it; edit = SlidesEdit.start(it); saved = edit; WriterLog.i("Slides: ${it.slides.size} slides, ${it.slides.sumOf { s -> s.items.size }} shapes") }
            .onFailure { WriterLog.e("Slides: could not open", it); failed = "This presentation could not be opened." }
        // Changes not saved when the app was closed: the draft opened in their place, unsaved; one older than the file (saved since) goes.
        val kept = slideDraft(context, f).takeIf { it.isFile } ?: return@LaunchedEffect
        if (deck == null || kept.lastModified() <= f.lastModified()) { kept.delete(); return@LaunchedEffect }
        val copy = withContext(Dispatchers.IO) { kept.copyTo(File(context.cacheDir, "slide-draft-${System.nanoTime()}.${f.extension}"), overwrite = true) }
        load(copy).onSuccess { d -> source = copy; deck = d; edit = SlidesEdit.start(d); saved = null; editing = true
            message = "Your unsaved changes are back. Leave without saving to have the file as saved."; WriterLog.i("Slides: recovery draft opened") }
            .onFailure { WriterLog.e("Slides: recovery draft", it) }
    }
    val changed = edit != saved
    // Design › a theme: a copy of the file with it, read once (the deck shown and saved from it while it is chosen)
    val themed = remember { androidx.compose.runtime.mutableStateMapOf<SlideTheme, Pair<File, Deck>>() }
    val base: Deck? = edit?.theme?.let { themed[it]?.second } ?: deck
    LaunchedEffect(edit?.theme) {
        val t = edit?.theme ?: return@LaunchedEffect
        val src = source ?: file ?: return@LaunchedEffect
        if (t in themed) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching {
            val out = File(context.cacheDir, "theme-${System.nanoTime()}.${src.extension.lowercase()}")
            SlidesThemes.apply(src, t, out); out to Slides.read(out)
        }.onFailure { WriterLog.e("Slides: a theme could not be applied", it) }.getOrNull() }?.let { themed[t] = it; WriterLog.i("Slides: theme ${t.name}") }
            ?: run { message = "That theme could not be applied." }
    }
    fun change(to: SlidesEdit.Edit) { edit?.let { undos += it; if (undos.size > 100) undos.removeAt(0) }; redos.clear(); edit = to }
    fun save(then: (() -> Unit)? = null) {
        val e = edit ?: return
        // with a theme chosen: saved from the themed copy, into the file
        val f = file ?: return; val d = base ?: return; val from = e.theme?.let { themed[it]?.first } ?: source ?: f
        if (e.theme != null && e.theme !in themed) return
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val temp = File(f.parentFile, ".${f.name}.tetra-saving")
                try {
                    WriterLog.timed("Slides: saved ${e.order.size} slides, ${e.texts.size} text boxes") { if (f.extension.equals("odp", true)) SlidesOdpEdit.save(from, d, e, temp) else SlidesEdit.save(from, d, e, temp) }
                    keepPrevious(context, f) // Writer's versions
                    check(temp.renameTo(f)) { "Could not save ${f.name}." }
                } finally { temp.delete() }
                Slides.read(f)
            } }
            saving = false
            result.onSuccess { nd -> slideDraft(context, f).delete(); deck = nd; edit = SlidesEdit.start(nd); saved = edit; source = null; undos.clear(); redos.clear(); chosen = chosen?.coerceAtMost(nd.slides.lastIndex); message = "Saved"; then?.invoke() }
                .onFailure { WriterLog.e("Slides: could not save", it); message = it.message ?: "Could not save." }
        }
    }
    var shapes by remember { mutableStateOf(false) } // Shape: the shapes to choose from
    var tables by remember { mutableStateOf(false) } // Table: the sizes to choose from
    var officeSettings by remember { mutableStateOf(false) } // File › Settings: the ribbon's place and size
    var clip by remember { mutableStateOf<Pair<SlidesEdit.Added, SlidesEdit.Look?>?>(null) } // Copy's: what Paste puts on a slide
    var slideMenu by remember { mutableStateOf<Triple<Boolean, Int, androidx.compose.ui.geometry.Offset>?>(null) } // a right-click's menu: on the sidebar?, which slide, where
    var cellEdit by remember { mutableStateOf<Pair<Int, androidx.compose.ui.text.input.TextFieldValue>?>(null) } // a cell of the chosen table being typed
    var notesTyping by remember { mutableStateOf<Pair<Int, androidx.compose.ui.text.input.TextFieldValue>?>(null) } // a slide's (uid) speaker notes being typed
    var item by remember { mutableStateOf<String?>(null) } // a picture, shape or text box chosen on the chosen slide (its id)
    var ribbonTab by remember { mutableStateOf("Home") } // "Item": the contextual tab of what is chosen on the slide
    var themesPage by remember { mutableStateOf(false) } // Design › Themes: the panel of them
    var effectOptions by remember { mutableStateOf(false) } // Transitions or Animations › Options: the strip of sides and speeds
    var effectKinds by remember { mutableStateOf(false) } // Animations › Effect: the catalog of entrances, emphases and exits, a line each
    LaunchedEffect(ribbonTab, item) { effectOptions = false; effectKinds = false }

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
    /** Item [id] of slide [i] as it is now, a group as its bounds (a finger's drag starts from it). */
    fun current(e: SlidesEdit.Edit, i: Int, id: String): SlideItem? = base?.let { d -> SlidesEdit.box(SlidesEdit.slide(d, e, e.order[i]), id) }
    /** The chosen item moved or sized: [f] gives its x, y, w, h from where it is. */
    fun reframe(f: (SlideItem) -> List<Float>) {
        val e = edit ?: return; val c = chosen ?: return; val id = item ?: return
        val it = current(e, c, id) ?: return
        val (x, y, w, h) = f(it); change(SlidesEdit.framed(e, e.order[c].uid, id, x, y, w, h))
    }
    /** The ids on slide [c] now (its own items', a group's shapes'), which a new item's must not be. */
    fun taken(e: SlidesEdit.Edit, c: Int): List<String> = base?.let { d -> SlidesEdit.slide(d, e, e.order[c]).items.flatMap { listOfNotNull(it.anim, (it as? SlideItem.Shape)?.id) } }.orEmpty()
    /** The chosen item's looks changed by [f] (fill, border, words' colour), saved with the slide. */
    fun restyle(f: (SlidesEdit.Look) -> SlidesEdit.Look) {
        val e = edit ?: return; val c = chosen ?: return; val id = item ?: return; val k = e.order[c].uid to id
        change(e.copy(looks = e.looks + (k to f(e.looks[k] ?: SlidesEdit.Look())))); WriterLog.i("Slides: a box's looks changed")
    }
    // Picture: the phone's photo picker; a copy kept on the phone until Save puts it in the file
    fun putPicture(uri: android.net.Uri) {
        val d = deck ?: return
        scope.launch {
            val made = withContext(Dispatchers.IO) { runCatching {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                val mime = o.outMimeType ?: ""
                val dir = File(context.cacheDir, "slide-pictures").apply { mkdirs() }
                // PNG, JPEG and GIF (animated, it plays) as they are; another kind (HEIC, WebP) made a JPEG, as PowerPoint can show it
                val (ext, data) = when (mime) { "image/png" -> "png" to bytes; "image/jpeg" -> "jpeg" to bytes; "image/gif" -> "gif" to bytes
                    else -> "jpeg" to java.io.ByteArrayOutputStream().also { out -> android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }.toByteArray() }
                val f = File(dir, "${System.nanoTime()}.$ext").apply { writeBytes(data) }
                val k = minOf(d.width * 0.6f / o.outWidth.coerceAtLeast(1), d.height * 0.6f / o.outHeight.coerceAtLeast(1))
                Triple(f, o.outWidth * k, o.outHeight * k) to ext
            }.onFailure { WriterLog.e("Slides: a picture could not be read", it) }.getOrNull() }
            val e = edit; val c = chosen
            if (made == null || e == null || c == null) { message = "That picture could not be read."; return@launch }
            val (f, w, h) = made.first
            put(SlidesEdit.Added(e.newId(e.order[c].uid, taken(e, c)), (d.width - w) / 2, (d.height - h) / 2, w, h, image = f.path, ext = made.second))
            WriterLog.i("Slides: a picture put on a slide")
        }
    }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::putPicture) }
    fun leave() { if (changed) leaving = true else { file?.let { slideDraft(context, it).delete() }; back() } }
    // The app sent to the background with changes not saved (Android may close it then): kept as a draft, as Words' and Tables'.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            val f = file; val e = edit
            if (ev != androidx.lifecycle.Lifecycle.Event.ON_STOP || f == null || e == null || saving) return@LifecycleEventObserver
            val draft = slideDraft(context, f)
            if (e == saved) { draft.delete(); return@LifecycleEventObserver }
            val d = e.theme?.let { themed[it]?.second } ?: deck ?: return@LifecycleEventObserver
            val from = e.theme?.let { themed[it]?.first } ?: source ?: f
            scope.launch(Dispatchers.IO) { runCatching {
                draft.parentFile?.mkdirs()
                val temp = File(draft.parentFile, ".${draft.name}.saving")
                if (f.extension.equals("odp", true)) SlidesOdpEdit.save(from, d, e, temp) else SlidesEdit.save(from, d, e, temp)
                check(temp.renameTo(draft)) { "Could not keep the draft." }
                WriterLog.i("Slides: recovery draft kept, ${draft.length() / 1024} KB")
            }.onFailure { WriterLog.e("Slides: recovery draft", it) } }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    /** Whether [hit]'s words can be typed: the file's own text boxes and shapes with words, and what was put on ([a]) but a picture or table. */
    fun typable(hit: SlideItem?, a: SlidesEdit.Added?) = hit is SlideItem.Shape && !hit.grouped && hit.id != null && (if (a == null) hit.text != null else a.image == null && a.prst != "table")
    fun typeInto(hit: SlideItem.Shape) { val t = if (hit.hint) "" else SlidesEdit.lines(hit).joinToString("\n"); typing = hit to androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)) }
    // a desktop's notes pane let go when a shape or another slide is clicked: the keys are the slide's again (Delete deletes the shape)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(item, chosen) { if (OfficeUi.top && notesTyping != null) focusManager.clearFocus() }
    BackHandler { when { typing != null -> typing = null; cellEdit != null -> cellEdit = null; notesTyping != null && !OfficeUi.top -> notesTyping = null; tables -> tables = false; layouts -> layouts = false; shapes -> shapes = false; item != null -> item = null; chosen != null -> chosen = null; else -> leave() } }
    val view = remember(base, edit) { base?.let { d -> edit?.let { SlidesEdit.view(d, it) } ?: d } }
    /** The chosen box with words of the file's own (a text box, a placeholder, a shape with words), as it shows: what Home's text tools change. */
    fun chosenWords(): SlideItem.Shape? = (view?.slides?.getOrNull(chosen ?: -1)?.items?.lastOrNull { it.anim == item } as? SlideItem.Shape)?.takeIf { it.text != null && it.id != null && !it.grouped }
    var painter by remember { mutableStateOf<SlidesEdit.Look?>(null) } // Format painter: the look taken, put on the next box chosen
    var strip by remember { mutableStateOf<DesignGallery?>(null) } // Home › Font, Insert › Drop cap: Words' strip of cards in the ribbon's place
    var stripBefore by remember { mutableStateOf<SlidesEdit.Edit?>(null) } // …the edit before a card tapped there (Cancel goes back to it)
    var stripPicked by remember { mutableStateOf<Any?>(null) } // …and the card tapped
    var dropOptions by remember { mutableStateOf(false) } // Drop cap › More…: Words' dialog, raised
    var linking by remember { mutableStateOf(false) } // Insert › Link: Words' link dialog
    var footing by remember { mutableStateOf(false) } // Insert › Header & footer
    var charting by remember { mutableStateOf<Pair<String?, DocChart>?>(null) } // Insert › Chart, Edit data: Words' chart editor (the chart's id; null: a new one)
    var dropLines by remember { androidx.compose.runtime.mutableIntStateOf(3) } // Drop cap: how many lines tall (the strip's setting)
    /** The chosen box's words' size before a raised first letter: what Drop cap's lines are counted in. */
    fun wordsSize(box: SlideItem.Shape?): Float {
        val p = box?.text?.paras?.firstOrNull { pp -> pp.runs.any { it.text.isNotBlank() } } ?: return 18f
        val k = p.runs.indexOfFirst { it.text.isNotEmpty() }
        return SlidesEdit.isRaised(p.runs, k) ?: p.runs.getOrNull(k)?.sizePt ?: 18f
    }
    /**
     * Drop cap, dropped (17 Oct): [b] with item [id] of slide [c]'s first letter in a text box of its own, [n] lines tall, beside the paragraph's
     * first [n] lines — the paragraph split where Words' engine breaks its line [n], the lines before standing in by the letter's width and
     * [distance]. PowerPoint and Impress have no drop cap, so it looks right everywhere but doesn't move with the words. Null: nothing to drop.
     */
    fun dropped(b: SlidesEdit.Edit, c: Int, id: String, n: Int, font: String? = null, color: Int? = null, distance: Float = 0f): SlidesEdit.Edit? {
        val d = base ?: return null; val e = b.order[c]
        val box = SlidesEdit.slide(d, b, e).items.lastOrNull { it.anim == id } as? SlideItem.Shape ?: return null
        val t = box.text ?: return null
        val f = t.paras.indexOfFirst { p -> p.runs.any { it.text.isNotBlank() } }.takeIf { it >= 0 } ?: return null
        val p = t.paras[f]; val r = p.runs.first { it.text.isNotEmpty() }
        val words = SlidesEdit.lines(box).toMutableList()
        val letter = words[f].substring(0, words[f].offsetByCodePoints(0, 1)); val rest = words[f].substring(letter.length)
        // the letter's size: its capital as tall as from the first line's capitals to the last line's foot
        val size = wordsSize(box)
        val face = font ?: DocStyles.Plain.fontName(r.font)
        val paint = android.graphics.Paint().apply { typeface = OfficeFonts.styled(OfficeFonts.typeface(face), r.bold == true, r.italic == true); textSize = 100f }
        val cap = android.graphics.Rect().also { paint.getTextBounds("H", 0, 1, it) }.height() / 100f
        val step = SlidesText.drop(t, box.w, f, p.look.left + size * n * 0.7f, n)?.step ?: return null
        val big = (step + cap * size) / cap
        val left = p.look.left + paint.measureText(letter) * big / 100f + distance + size * 0.15f
        val split = SlidesText.drop(t, box.w, f, left, n) ?: return null
        words[f] = rest.substring(0, split.at).trimEnd()
        val more = split.at < rest.length
        if (more) words.add(f + 1, rest.substring(split.at))
        val k = e.uid to id
        val nu = b.newId(e.uid, taken(b, c))
        // the letter's box: a text box (insets 7.2 and 3.6 pt) its left at the words' edge, its letter's foot on line n's
        val made = b.copy(texts = b.texts + (k to words) + ((e.uid to nu) to listOf(letter)),
            looks = b.looks + (k to (b.looks[k] ?: SlidesEdit.Look()).copy(raise = null, drop = left, dropRest = more)) +
                ((e.uid to nu) to SlidesEdit.Look(size = big, font = face, bold = r.bold, italic = r.italic, text = (color ?: r.color)?.or(0xFF000000.toInt()))),
            added = b.added + (e.uid to b.added[e.uid].orEmpty() + SlidesEdit.Added(nu, box.x + t.insets[0] + p.look.left - 7.2f, box.y, paint.measureText(letter) * big / 100f * 1.15f + 14.4f, big * 1.2f, prst = SlidesEdit.TEXT_BOX)))
        // where the words now stand (from the top, middle or bottom of their box), and the letter's baseline in its own box
        val shown = SlidesEdit.slide(d, made, e).items
        val now = (shown.lastOrNull { it.anim == id } as? SlideItem.Shape)?.text ?: return null
        val laid = SlidesText.block(now, box.w); val room = box.h - now.insets[1] - now.insets[3]
        val top = box.y + now.insets[1] + when (now.anchor) { 'c' -> (room - laid.height) / 2; 'b' -> room - laid.height; else -> 0f }
        val base0 = SlidesText.drop(now, box.w, f, left, n)?.top ?: return null
        val mine = (shown.lastOrNull { it.anim == nu } as? SlideItem.Shape)?.text ?: return null
        val foot = SlidesText.block(mine, 1000f).pieces.firstOrNull()?.let { it.y + it.layout.getLineBaseline(0) / 20f } ?: return null
        val y = top + base0 + split.step - mine.insets[1] - foot
        return made.copy(added = made.added + (e.uid to made.added[e.uid].orEmpty().map { if (it.id == nu) it.copy(y = y) else it }))
    }
    // something chosen on the slide: its tab, unless Transitions or Animations is in use (they work on it too); a box with words
    // keeps Home (17 Oct: its text tools are there, as PowerPoint's), and takes the look a Format painter holds
    LaunchedEffect(item) {
        val words = chosenWords()
        if (item != null && ribbonTab in setOf("Home", "Insert")) ribbonTab = if (words != null) "Home" else "Item"
        val pl = painter ?: return@LaunchedEffect
        if (words == null) return@LaunchedEffect
        painter = null
        restyle { it.copy(font = pl.font, size = pl.size, bold = pl.bold, italic = pl.italic, underline = pl.underline, strike = pl.strike, text = pl.text, highlight = pl.highlight, align = pl.align) }
        message = "Look painted"
    }
    /** File › Versions: [v] in the editor, unsaved — Save puts it over the file; leaving without saving keeps the file as it is. */
    fun openVersion(v: File) = scope.launch {
        load(v).onSuccess { d -> themed.clear(); source = v; deck = d; edit = SlidesEdit.start(d); saved = null; undos.clear(); redos.clear(); chosen = null; item = null
            editing = true; message = "This version is open. Save keeps it; leave without saving to go back."; WriterLog.i("Slides: a version opened") }
            .onFailure { WriterLog.e("Slides: a version", it); message = "That version could not be opened." }
    }
    /** File › Export PDF (beside the presentation, never over a file) and Print: the slides as shown, changes included. */
    fun slidesPdf(to: File): Int = view?.let { d -> to.outputStream().use { Slides.pdf(d, it) } } ?: error("Nothing to export.")
    fun exportPdf() {
        val f = file ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val target = generateSequence(1) { it + 1 }.map { File(f.parentFile, if (it == 1) "${f.nameWithoutExtension}.pdf" else "${f.nameWithoutExtension} ($it).pdf") }.first { !it.exists() }
                val n = slidesPdf(target); WriterLog.i("Slides: PDF of $n slides, ${target.length() / 1024} KB"); target
            } }
            message = result.fold({ "Saved ${it.name} beside the presentation" }, { WriterLog.e("Slides: PDF", it); "Could not make the PDF." })
        }
    }
    fun printIt() {
        val title = file?.nameWithoutExtension ?: "Presentation"
        scope.launch {
            val pdf = withContext(Dispatchers.IO) { runCatching { File(context.cacheDir, "print.pdf").also { slidesPdf(it) } }
                .onFailure { WriterLog.e("Slides: print", it) }.getOrNull() } ?: run { message = "Could not make the pages to print."; return@launch }
            context.getSystemService(android.print.PrintManager::class.java)?.print(title, PdfPrintAdapter(pdf, title), null)
            WriterLog.i("Slides: print ${pdf.length() / 1024} KB")
        }
    }
    /** What is chosen on the slide, deleted — its animation with it (the slide's timing then names only what is there). */
    fun deleteItem() {
        val e = edit ?: return; val c = chosen ?: return; val id = item ?: return; val uid = e.order[c].uid
        val sl = view?.slides?.getOrNull(c)
        val a = e.added[uid]?.firstOrNull { it.id == id }
        if (a == null && sl?.items?.none { it.anim == id } != false) return
        val list = e.anims[uid] ?: SlidesEdit.flat(sl?.steps.orEmpty())
        val anims = if (list.any { it.target == id }) e.anims + (uid to list.filter { it.target != id }) else e.anims
        change(if (a != null) e.copy(added = e.added + (uid to e.added[uid].orEmpty().filter { it.id != id }), anims = anims)
            else e.copy(removed = e.removed + (uid to id), anims = anims))
        item = null; WriterLog.i("Slides: an item deleted")
    }
    /** Slide [c] copied after itself: its words, what was put on it, its colours, transition and animations. */
    fun duplicateSlide(c: Int) {
        val e = edit ?: return; val entry = e.order.getOrNull(c) ?: return; val uid = entry.uid
        val nu = e.next()
        fun <V> mine(m: Map<Pair<Int, String>, V>) = m.filterKeys { it.first == uid }.mapKeys { nu to it.key.second }
        change(e.copy(order = e.order.toMutableList().apply { add(c + 1, SlidesEdit.Entry(entry.src, nu, entry.layout)) }, texts = e.texts + mine(e.texts), looks = e.looks + mine(e.looks), frames = e.frames + mine(e.frames), removed = e.removed + e.removed.filter { it.first == uid }.map { nu to it.second },
            added = e.added + (e.added[uid]?.let { mapOf(nu to it) } ?: emptyMap()), transitions = e.transitions + (e.transitions[uid]?.let { mapOf(nu to it) } ?: emptyMap()),
            anims = e.anims + (e.anims[uid]?.let { mapOf(nu to it) } ?: emptyMap()), notes = e.notes + (e.notes[uid]?.let { mapOf(nu to it) } ?: emptyMap())))
        chosen = c + 1
    }
    /** Copy (Cut): the chosen item kept for Paste — what was put here, a picture, a shape with no words; its words on the clipboard for other apps. */
    fun copyItem(): Boolean {
        val e = edit ?: return false; val c = chosen ?: return false; val id = item ?: return false; val uid = e.order[c].uid
        val sl = view?.slides?.getOrNull(c) ?: return false
        val a = e.added[uid]?.firstOrNull { it.id == id }
        val it0 = sl.items.lastOrNull { it.anim == id }
        (it0 as? SlideItem.Shape)?.takeIf { it.text != null && !it.hint }?.let { copy(context, "Slide text", SlidesEdit.lines(it).joinToString("\n")) }
        clip = when {
            a != null -> a to e.looks[uid to id]
            it0 is SlideItem.Picture -> it0.bytes()?.let { b ->
                val png = b.size > 3 && b[1] == 'P'.code.toByte()
                val f = File(File(context.cacheDir, "slide-pictures").apply { mkdirs() }, "${System.nanoTime()}.${if (png) "png" else "jpeg"}").apply { writeBytes(b) }
                SlidesEdit.Added("", it0.x, it0.y, it0.w, it0.h, image = f.path, ext = if (png) "png" else "jpeg") to null }
            it0 is SlideItem.Shape && it0.shape != null && it0.text == null -> SlidesEdit.Added("", it0.x, it0.y, it0.w, it0.h, prst = it0.shape.geometry, fill = it0.shape.fill ?: 0xFF4472C4.toInt()) to null
            else -> null
        }
        message = if (clip != null) "Copied" else if (it0 is SlideItem.Shape && it0.text != null) "Its words copied" else "This one cannot be copied yet"
        WriterLog.i("Slides: an item copied (${if (clip != null) "to paste" else "words only"})")
        return clip != null
    }
    /** Paste: what was copied, on the chosen slide, a little lower and to the right of where it stood. */
    fun pasteItem() {
        val (a, look) = clip ?: return; val e = edit ?: return; val c = chosen ?: return; val uid = e.order[c].uid
        val id = e.newId(uid, taken(e, c))
        put(a.copy(id = id, x = a.x + 12f, y = a.y + 12f))
        look?.let { l -> edit?.let { now -> edit = now.copy(looks = now.looks + ((uid to id) to l)) } }
        WriterLog.i("Slides: an item pasted")
    }
    fun undoEdit() { undos.removeLastOrNull()?.let { edit?.let(redos::add); edit = it; item = null; chosen = chosen?.coerceAtMost((it.order.size - 1).coerceAtLeast(0)) } }
    fun redoEdit() { redos.removeLastOrNull()?.let { edit?.let(undos::add); edit = it; item = null; chosen = chosen?.coerceAtMost((it.order.size - 1).coerceAtLeast(0)) } }
    /** The tools of the ribbon's tab [tab], for slide [c] of [e]. */
    /** Home's text tools for [box] (17 Oct), Words' own: font strip, size, B I U S, colours, painter, clear; lists, alignment, spacing; anchor, autofit. */
    @Composable fun HomeText(box: SlideItem.Shape, w: Color) {
        val t = box.text ?: return
        val r = t.paras.flatMap { it.runs }.firstOrNull { it.text.isNotBlank() } ?: t.paras.firstOrNull()?.runs?.firstOrNull()
        val p = t.paras.firstOrNull { pp -> pp.runs.any { it.text.isNotBlank() } } ?: t.paras.firstOrNull()
        FontButton(DocStyles.Plain.fontName(r?.font), w) { strip = DesignGallery.Font }
        val pt = Math.round(r?.sizePt ?: 18f)
        SizeStepper("$pt", w, pt > 1, pt < 400, { restyle { it.copy(size = (pt - 1).toFloat()) } }, { restyle { it.copy(size = (pt + 1).toFloat()) } })
        RibbonDivider(w)
        Tool(R.drawable.ic_format_bold, "Bold", w, on = r?.bold == true) { restyle { it.copy(bold = r?.bold != true) } }
        Tool(R.drawable.ic_format_italic, "Italic", w, on = r?.italic == true) { restyle { it.copy(italic = r?.italic != true) } }
        Tool(R.drawable.ic_format_underlined, "Underline", w, on = r?.underline == true) { restyle { it.copy(underline = r?.underline != true) } }
        Tool(R.drawable.ic_format_strikethrough, "Strikethrough", w, on = r?.strike == true) { restyle { it.copy(strike = r?.strike != true) } }
        Swatches(w, R.drawable.ic_format_color_text, "Text colour", TEXT_COLOURS) { col -> restyle { it.copy(text = col?.toArgb() ?: 0xFF000000.toInt()) } }
        Swatches(w, R.drawable.ic_highlighter, "Highlight", HIGHLIGHT_COLOURS) { col -> restyle { it.copy(highlight = col?.toArgb() ?: SlidesEdit.NONE) } }
        // Format painter: this box's look taken, put on the next box chosen
        Tool(R.drawable.ic_format_paint, "Format painter", w, on = painter != null) {
            painter = if (painter != null) null else SlidesEdit.Look(font = DocStyles.Plain.fontName(r?.font), size = r?.sizePt, bold = r?.bold, italic = r?.italic, underline = r?.underline,
                strike = r?.strike, text = r?.color?.or(0xFF000000.toInt()), highlight = r?.highlight?.or(0xFF000000.toInt()) ?: SlidesEdit.NONE, align = p?.para?.align)
            if (painter != null) message = "Choose the box to paint"
        }
        // Clear formatting: the letters' own looks gone (the box's fill, border, anchor and autofit stay)
        Tool(R.drawable.ic_format_clear, "Clear formatting", w) { restyle { SlidesEdit.Look(fill = it.fill, line = it.line, anchor = it.anchor, autofit = it.autofit, cleared = true) } }
        RibbonDivider(w)
        val numbered = p?.bullet?.text?.firstOrNull()?.isDigit() == true
        Tool(R.drawable.ic_format_list_bulleted, "Bullets", w, on = p?.bullet != null && !numbered) { restyle { it.copy(list = if (p?.bullet != null && !numbered) 'n' else 'b') } }
        Tool(R.drawable.ic_format_list_numbered, "Numbering", w, on = numbered) { restyle { it.copy(list = if (numbered) 'n' else '1') } }
        RibbonDivider(w)
        listOf(DocAlign.Start to R.drawable.ic_align_left, DocAlign.Center to R.drawable.ic_align_center, DocAlign.End to R.drawable.ic_align_right, DocAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
            Tool(icon, "Align ${a.name.lowercase().replace("start", "left").replace("end", "right")}", w, on = (p?.para?.align ?: DocAlign.Start) == a) { restyle { it.copy(align = a) } }
        }
        RibbonDivider(w)
        // line spacing as Words' (a share of the single line: PowerPoint's 1.2 × the letters' size)
        Icon(painterResource(R.drawable.ic_format_line_spacing), "Line spacing", tint = w, modifier = Modifier.padding(horizontal = 4.dp).size(20.dp))
        LINE_SPACINGS.forEach { m -> RibbonButton(plain(m), w, on = p != null && p.look.lineExact == null && kotlin.math.abs(p.look.lineMultiple - m) < 0.01f) { restyle { it.copy(spacing = m) } } }
        RibbonDivider(w)
        // where the words stand in the box, and how the box takes too many (PowerPoint's Align Text and Autofit)
        listOf('t' to (R.drawable.ic_vertical_align_top to "Top"), 'c' to (R.drawable.ic_vertical_align_center to "Middle"), 'b' to (R.drawable.ic_vertical_align_bottom to "Bottom")).forEach { (a, d) ->
            Tool(d.first, "Words at the ${d.second.lowercase()}", w, on = t.anchor == a) { restyle { it.copy(anchor = a) } }
        }
        val grows = edit?.looks?.get((edit?.order?.getOrNull(chosen ?: -1)?.uid ?: -1) to (item ?: ""))?.autofit == 'g'
        RibbonButton("Don't fit", w, R.drawable.ic_close, on = !t.shrink && !grows) { restyle { it.copy(autofit = 'n') } }
        RibbonButton("Shrink text", w, R.drawable.ic_fit_page, on = t.shrink) { restyle { it.copy(autofit = 's') } }
        RibbonButton("Box fits words", w, R.drawable.ic_unfold_more, on = grows) { restyle { it.copy(autofit = 'g') } }
    }
    @Composable fun SlideRibbon(tab: String, c: Int, e: SlidesEdit.Edit, w: Color) {
        val entry = e.order[c]; val uid = entry.uid
        val sl = view?.slides?.getOrNull(c)
        when (tab) {
            "Item" -> {
                val a = e.added[uid]?.firstOrNull { it.id == item }
                val it0 = sl?.items?.lastOrNull { it.anim == item }
                if (typable(it0, a)) RibbonButton("Edit text", w, R.drawable.ic_edit) { typeInto(it0 as SlideItem.Shape) }
                // a chart put on here: its numbers in Words' chart editor again
                a?.chart?.let { ch -> RibbonButton("Edit data", w, R.drawable.ic_bar_chart) { charting = a.id to ch }; RibbonDivider(w) }
                if (it0 is SlideItem.Shape || a?.prst == "table") {
                    if (typable(it0, a)) RibbonDivider(w)
                    Swatches(w, R.drawable.ic_format_color_fill, "Fill", SHAPE_COLOURS.map { Color(it) }) { col -> restyle { it.copy(fill = col?.toArgb() ?: SlidesEdit.NONE) } }
                    if (it0 is SlideItem.Shape) Swatches(w, R.drawable.ic_border_outer, "Border", SHAPE_COLOURS.map { Color(it) }) { col -> restyle { it.copy(line = col?.toArgb() ?: SlidesEdit.NONE) } }
                    if ((it0 as? SlideItem.Shape)?.text != null || a?.prst == "table") Swatches(w, R.drawable.ic_format_color_text, "Text colour", TEXT_COLOURS) { col -> restyle { it.copy(text = col?.toArgb() ?: 0xFF000000.toInt()) } }
                }
                // moved, made bigger or smaller (the arrows a step a press, for where a finger is not precise); what was put on it here, deleted
                if (it0 != null) {
                    if (it0 is SlideItem.Shape || a?.prst == "table") RibbonDivider(w)
                    val step = (deck?.width ?: 720f) / 40
                    RibbonButton("Left", w, R.drawable.ic_chevron_left) { reframe { listOf(it.x - step, it.y, it.w, it.h) } }
                    RibbonButton("Right", w, R.drawable.ic_chevron_right) { reframe { listOf(it.x + step, it.y, it.w, it.h) } }
                    RibbonButton("Up", w, R.drawable.ic_keyboard_arrow_up) { reframe { listOf(it.x, it.y - step, it.w, it.h) } }
                    RibbonButton("Down", w, R.drawable.ic_keyboard_arrow_down) { reframe { listOf(it.x, it.y + step, it.w, it.h) } }
                    RibbonDivider(w)
                    RibbonButton("Bigger", w, R.drawable.ic_add) { reframe { val k = 1.15f; listOf(it.x - it.w * (k - 1) / 2, it.y - it.h * (k - 1) / 2, it.w * k, it.h * k) } }
                    RibbonButton("Smaller", w, R.drawable.ic_remove) { reframe { val k = 1 / 1.15f; listOf(it.x + it.w * (1 - k) / 2, it.y + it.h * (1 - k) / 2, it.w * k, it.h * k) } }
                }
                if (it0 != null) RibbonDivider(w)
                if (a != null || it0 != null) RibbonButton("Delete", w, R.drawable.ic_delete) { deleteItem() }
            }
            "Home" -> {
                // a box with words chosen: Words' text tools, on all its words (a letter's own needs the rich typing editor, later)
                chosenWords()?.let { box -> HomeText(box, w); RibbonDivider(w) }
                Text("Slide ${c + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                if (deck?.layouts?.isNotEmpty() == true) RibbonButton("New slide", w, R.drawable.ic_add) { layouts = true }
                RibbonButton("Duplicate", w, R.drawable.ic_copy) { duplicateSlide(c) }
                RibbonDivider(w)
                RibbonButton("Move up", w, R.drawable.ic_keyboard_arrow_up, enabled = c > 0) { change(e.copy(order = e.order.toMutableList().apply { add(c - 1, removeAt(c)) })); chosen = c - 1 }
                RibbonButton("Move down", w, R.drawable.ic_keyboard_arrow_down, enabled = c < e.order.lastIndex) { change(e.copy(order = e.order.toMutableList().apply { add(c + 1, removeAt(c)) })); chosen = c + 1 }
                RibbonDivider(w)
                val hidden = e.hidden[uid] ?: deck?.slides?.getOrNull(entry.src)?.hidden ?: false // a new slide is shown
                RibbonButton(if (hidden) "Show" else "Hide", w, if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off) { change(e.copy(hidden = e.hidden + (uid to !hidden))) }
                // the slide's speaker notes typed in a bar of their own (asked 17 Oct); a desktop has them under the slide
                if (!OfficeUi.top) RibbonButton("Speaker notes", w, R.drawable.ic_comment) {
                    val n = view?.slides?.getOrNull(c)?.notes.orEmpty()
                    notesTyping = uid to androidx.compose.ui.text.input.TextFieldValue(n, androidx.compose.ui.text.TextRange(n.length))
                }
                RibbonDivider(w)
                RibbonButton("Delete", w, R.drawable.ic_delete, enabled = e.order.size > 1) { change(e.copy(order = e.order.filterIndexed { k, _ -> k != c })); chosen = c.coerceAtMost(e.order.size - 2) }
            }
            "Insert" -> {
                RibbonButton("Picture", w, R.drawable.ic_gallery) { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                RibbonButton("Shape", w, R.drawable.ic_category) { shapes = true }
                // Drop cap (asked 17 Oct): Words' strip of cards — PowerPoint and Impress have none, so a raised first letter
                RibbonButton("Drop cap", w, R.drawable.ic_drop_cap) { if (chosenWords() != null) strip = DesignGallery.DropCap else message = "Choose a box with words first" }
                // Text box (as PowerPoint's): a box of words alone across the slide's middle, typed into at once
                RibbonButton("Text box", w, R.drawable.ic_text_box) {
                    val d = deck ?: return@RibbonButton; val ee = edit ?: return@RibbonButton
                    val a = SlidesEdit.Added(ee.newId(ee.order[c].uid, taken(ee, c)), d.width * 0.2f, d.height * 0.42f, d.width * 0.6f, 40f, prst = SlidesEdit.TEXT_BOX)
                    put(a); typing = SlideItem.Shape(a.x, a.y, a.w, a.h, 0f, null, null, id = a.id).also { it.anim = a.id } to androidx.compose.ui.text.input.TextFieldValue("")
                    WriterLog.i("Slides: a text box put on")
                }
                RibbonButton("Table", w, R.drawable.ic_table) { tables = true }
                // Chart (as PowerPoint's): Words' chart editor, the chart across most of the slide
                RibbonButton("Chart", w, R.drawable.ic_bar_chart) { charting = null to WORD_SAMPLE_CHART }
                // Link (as Words'): a box's words linked to a web page or a slide; nothing chosen: a text box of words to show
                RibbonButton("Link", w, R.drawable.ic_link) { linking = true }
                // Header & footer (as PowerPoint's): the date, the slide's number and a footer, on this slide or all
                RibbonButton("Header & footer", w, R.drawable.ic_page_header) { footing = true }
            }
            "File" -> {
                // as Words' and Tables' File: the file's own actions (asked 16 Oct: Share in File); a change saved first, so what goes is what shows
                RibbonButton("Save", w, R.drawable.ic_save, enabled = changed && !saving) { save() }
                // the same File as Words' and Tables' (17 Oct)
                RibbonDivider(w)
                if (file != null) RibbonButton("Versions", w, R.drawable.ic_history) { showVersions = true }
                RibbonButton("Export PDF", w, R.drawable.ic_file, enabled = file != null) { exportPdf() }
                RibbonButton("Print", w, R.drawable.ic_print) { printIt() }
                file?.let { f ->
                    RibbonDivider(w)
                    RibbonButton("Share", w, R.drawable.ic_share) { if (changed) save { shareDriveFile(context, f) } else shareDriveFile(context, f) }
                    RibbonButton("Open in another app", w, R.drawable.ic_open_with) { if (changed) save { openElsewhere(context, f) } else openElsewhere(context, f) }
                }
                RibbonDivider(w)
                RibbonButton("About this file", w, R.drawable.ic_info) { about = true }
                RibbonButton("Send log", w, R.drawable.ic_share) { WriterLog.export(context)?.let { shareDriveFile(context, it) } }
                RibbonDivider(w)
                RibbonButton("Settings", w, R.drawable.ic_settings) { officeSettings = true }
            }
            "Slide show" -> {
                // PowerPoint's and LibreOffice's Slide Show tab (asked 16 Oct: where presenting starts)
                RibbonButton("From the beginning", w, R.drawable.ic_play) { presenting = 0 }
                RibbonButton("From this slide", w, R.drawable.ic_play) { presenting = c }
                RibbonDivider(w)
                val hidden = e.hidden[uid] ?: deck?.slides?.getOrNull(entry.src)?.hidden ?: false
                RibbonButton(if (hidden) "Show slide" else "Hide slide", w, if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off) { change(e.copy(hidden = e.hidden + (uid to !hidden))) }
            }
            "Design" -> {
                // PowerPoint's Design: the deck's theme — its colours, fonts and background — light or dark, Tetra's or a template's
                RibbonButton("Themes", w, R.drawable.ic_palette) { themesPage = true }
                Text(e.theme?.let { "Theme: ${it.name}" } ?: "The file's own theme", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                if (e.theme != null) RibbonButton("As the file was", w, R.drawable.ic_restore) { change(e.copy(theme = null)) }
            }
            "Transitions" -> {
                // how this slide comes in when presenting; Options: its side, speed, and when the next comes; Play shows it from the slide before
                val now = sl?.transition
                // LibreOffice has no cut: an .odp leaves it out
                val kinds = listOfNotNull("None" to "", "Fade" to "fade", "Push" to "push", "Wipe" to "wipe", "Cover" to "cover", "Uncover" to "pull", ("Cut" to "cut").takeIf { !odp })
                fun set(t: SlideTransition) { change(e.copy(transitions = e.transitions + (uid to t))); WriterLog.i("Slides: transition ${t.kind} ${t.dir} ${t.ms}") }
                if (effectOptions) {
                    if (now != null && now.kind in setOf("push", "wipe", "cover", "pull"))
                        for ((label, d) in listOf("From the right" to "l", "From the left" to "r", "From below" to "u", "From above" to "d"))
                            RibbonButton(label, w, on = (now.dir ?: "l") == d) { set(now.copy(dir = d)) }
                    if (now != null && now.kind in setOf("push", "wipe", "cover", "pull")) RibbonDivider(w)
                    if (now != null && now.kind.isNotEmpty()) for ((label, ms) in listOf("Fast" to 500, "Medium" to 750, "Slow" to 1000)) RibbonButton(label, w, on = now.ms == ms) { set(now.copy(ms = ms)) }
                    // when the next slide comes: on a click, or by itself after a time (PowerPoint's Advance Slide)
                    if (now != null && now.kind.isNotEmpty()) RibbonDivider(w)
                    Text("Next slide", style = MaterialTheme.typography.labelMedium, color = w.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp))
                    for ((label, after) in listOf("On click" to null, "After 3 s" to 3000, "After 5 s" to 5000, "After 10 s" to 10000, "After 20 s" to 20000))
                        RibbonButton(label, w, on = now?.after == after) { set((now ?: SlideTransition("", null, 0)).copy(after = after)) }
                } else {
                    for ((label, kind) in kinds) RibbonButton(label, w, on = (now?.kind ?: "") == kind) { set(SlideTransition(kind, now?.dir, if (kind.isEmpty()) 0 else now?.ms?.takeIf { it > 0 } ?: 750, now?.after)) }
                    RibbonDivider(w)
                    RibbonButton("Options", w, R.drawable.ic_tune) { effectOptions = true }
                    // a kind PowerPoint names in another namespace (p14's) is not copied: it would be written in the wrong one
                    RibbonButton("Apply to all", w, R.drawable.ic_done_all, enabled = kinds.any { it.second == (now?.kind ?: "") }) { val t = now ?: SlideTransition("", null, 750); change(e.copy(transitions = e.transitions + e.order.associate { it.uid to t })) }
                    RibbonDivider(w)
                    RibbonButton("Play", w, R.drawable.ic_play) { presenting = (c - 1).coerceAtLeast(0) }
                }
            }
            "Animations" -> {
                // the chosen item's entrance and exit, each on its own click, in the slide's order; Options: side and speed
                val list = e.anims[uid] ?: SlidesEdit.flat(sl?.steps.orEmpty())
                val id = item
                fun set(to: List<SlideEffect>) { change(e.copy(anims = e.anims + (uid to to))); WriterLog.i("Slides: ${to.size} animations on a slide") }
                val inK = list.indexOfFirst { it.target == id && !it.exit && !it.emphasis }; val outK = list.indexOfFirst { it.target == id && it.exit }
                val emK = list.indexOfFirst { it.target == id && it.emphasis }
                /** The item's entrance ('i'), emphasis ('m') or exit ('o') made [kind] (null: none), in its place in the clicks or last. */
                fun choose(which: Char, kind: String?) {
                    val k = when (which) { 'o' -> outK; 'm' -> emK; else -> inK }; val id2 = id ?: return
                    val old = list.getOrNull(k)
                    val fx = kind?.let { SlideEffect(id2, it, old?.from ?: 'd', 0, old?.ms ?: if (which == 'm') 1000 else 500, which == 'o') }
                    set(when { fx == null -> list.filterIndexed { i, _ -> i != k }; k >= 0 -> list.toMutableList().also { it[k] = fx }; else -> list + fx })
                }
                when {
                    id == null -> {
                        Text("Choose a text box, shape or picture on the slide", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                        RibbonButton("Play", w, R.drawable.ic_play) { presenting = c }
                    }
                    effectKinds -> {
                        // a catalog: the same kind of choice, so a line each for in, emphasis and out (asked 16 Oct)
                        Column {
                            for ((which, name, kinds) in listOf(
                                Triple('i', "In", listOf("None" to null, "Appear" to "appear", "Fade" to "fade", "Fly" to "fly", "Wipe" to "wipe", "Zoom" to "zoom")),
                                Triple('m', "Emphasis", listOf("None" to null, "Pulse" to "pulse", "Spin" to "spin", "Grow" to "grow")),
                                Triple('o', "Out", listOf("None" to null, "Disappear" to "appear", "Fade" to "fade", "Fly" to "fly", "Wipe" to "wipe", "Zoom" to "zoom")))) {
                                val k = when (which) { 'o' -> outK; 'm' -> emK; else -> inK }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.labelMedium, color = w.copy(alpha = 0.7f), modifier = Modifier.width(72.dp).padding(start = 6.dp))
                                    for ((label, kind) in kinds) RibbonButton(label, w, on = if (k < 0) kind == null else list[k].kind == kind) { choose(which, kind) }
                                }
                            }
                        }
                    }
                    effectOptions && (inK >= 0 || outK >= 0 || emK >= 0) -> {
                        // the side a fly comes from (or goes to), and how long each of the item's effects takes
                        fun all(f: (SlideEffect) -> SlideEffect) = set(list.map { if (it.target == id) f(it) else it })
                        val mineNow = list.filter { it.target == id }
                        val sided = setOf("fly", "wipe")
                        if (mineNow.any { it.kind in sided }) for ((label, d) in listOf("Bottom" to 'd', "Top" to 'u', "Left" to 'l', "Right" to 'r'))
                            RibbonButton(label, w, on = mineNow.first { it.kind in sided }.from == d) { all { if (it.kind in sided) it.copy(from = d) else it } }
                        if (mineNow.any { it.kind in sided }) RibbonDivider(w)
                        for ((label, ms) in listOf("Fast" to 500, "Medium" to 1000, "Slow" to 2000)) RibbonButton(label, w, on = mineNow.first().ms == ms) { all { it.copy(ms = ms) } }
                        // when its first effect starts (PowerPoint's Start)
                        val k = list.indexOfFirst { it.target == id }
                        RibbonDivider(w)
                        for ((label, c) in listOf("On click" to 'c', "With previous" to 'w', "After previous" to 'a'))
                            RibbonButton(label, w, on = list[k].start == c) { set(list.toMutableList().also { it[k] = it[k].copy(start = c) }) }
                        // and how long it waits from then (PowerPoint's Delay)
                        RibbonDivider(w)
                        Text("Delay", style = MaterialTheme.typography.labelMedium, color = w.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp))
                        for ((label, ms) in listOf("None" to 0, "0.5 s" to 500, "1 s" to 1000, "2 s" to 2000, "5 s" to 5000))
                            RibbonButton(label, w, on = list[k].wait == ms) { set(list.toMutableList().also { it[k] = it[k].copy(wait = ms) }) }
                    }
                    else -> {
                        val first = listOf(inK, emK, outK).filter { it >= 0 }.minOrNull() ?: -1
                        // which click brings it (several effects can share one)
                        val groups = SlidesEdit.group(list); val g = groups.indexOfFirst { st -> st.effects.any { it.target == id } }
                        Text(when { first < 0 -> "No animation"; groups[g].auto -> "By itself"; else -> "Click ${groups.take(g + 1).count { !it.auto }} of ${groups.count { !it.auto }}" },
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                        RibbonButton("Effect", w, R.drawable.ic_motion) { effectKinds = true }
                        RibbonButton("Options", w, R.drawable.ic_tune, enabled = first >= 0) { effectOptions = true }
                        if (first >= 0) {
                            RibbonDivider(w)
                            RibbonButton("Earlier", w, R.drawable.ic_keyboard_arrow_up, enabled = first > 0) { set(list.toMutableList().apply { add(first - 1, removeAt(first)) }) }
                            RibbonButton("Later", w, R.drawable.ic_keyboard_arrow_down, enabled = first < list.lastIndex) { set(list.toMutableList().apply { add(first + 1, removeAt(first)) }) }
                        }
                        RibbonDivider(w)
                        RibbonButton("Play", w, R.drawable.ic_play) { presenting = c }
                    }
                }
            }
        }
    }
    // a keyboard's shortcuts (a tablet's or a desktop's, asked 16 Oct): the screen holds the keys while nothing is typed in
    val keys = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing, typing == null, cellEdit == null, notesTyping == null, presenting == null) { if (typing == null && cellEdit == null && notesTyping == null && presenting == null) runCatching { keys.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Color(0xFF2B2B2B)).focusRequester(keys).focusable().onPreviewKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown || typing != null || cellEdit != null || notesTyping != null) return@onPreviewKeyEvent false
        val step = (deck?.width ?: 720f) / (if (k.isShiftPressed) 8 else 40)
        when {
            k.key == Key.F5 -> { presenting = if (k.isShiftPressed) chosen ?: 0 else 0; true }
            !editing -> false
            k.isCtrlPressed && k.key == Key.S -> { if (changed && !saving) save(); true }
            k.isCtrlPressed && (k.key == Key.Y || (k.key == Key.Z && k.isShiftPressed)) -> { redoEdit(); true }
            k.isCtrlPressed && k.key == Key.Z -> { undoEdit(); true }
            // Cut, Copy and Paste as Words' and Tables' (17 Oct): what is chosen on the slide
            k.isCtrlPressed && item != null && k.key == Key.C -> { copyItem(); true }
            k.isCtrlPressed && item != null && k.key == Key.X -> { if (copyItem()) deleteItem(); true }
            k.isCtrlPressed && clip != null && k.key == Key.V -> { pasteItem(); true }
            item != null && (k.key == Key.Delete || k.key == Key.Backspace) -> { deleteItem(); true }
            item != null && k.key == Key.DirectionLeft -> { reframe { listOf(it.x - step, it.y, it.w, it.h) }; true }
            item != null && k.key == Key.DirectionRight -> { reframe { listOf(it.x + step, it.y, it.w, it.h) }; true }
            item != null && k.key == Key.DirectionUp -> { reframe { listOf(it.x, it.y - step, it.w, it.h) }; true }
            item != null && k.key == Key.DirectionDown -> { reframe { listOf(it.x, it.y + step, it.w, it.h) }; true }
            item != null && k.key == Key.Escape -> { item = null; true }
            // with nothing chosen on it, the arrows go from slide to slide
            item == null && (k.key == Key.DirectionUp || k.key == Key.PageUp) -> { chosen = ((chosen ?: 0) - 1).coerceAtLeast(0); true }
            item == null && (k.key == Key.DirectionDown || k.key == Key.PageDown) -> { chosen = ((chosen ?: -1) + 1).coerceAtMost((view?.slides?.size ?: 1) - 1); true }
            else -> false
        }
    }) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::leave) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text((if (changed) "• " else "") + (file?.name ?: "Presentation"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    view?.let { Text(if (editing) "Editing" else "${it.slides.size} slide${if (it.slides.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = islandContentColor().copy(alpha = 0.7f)) }
                }
                if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700) view?.let { v ->
                    val hidden = v.slides.count { it.hidden }
                    TopInfo(listOfNotNull(chosen?.let { "Slide ${it + 1} of ${v.slides.size}" } ?: "${v.slides.size} slide${if (v.slides.size == 1) "" else "s"}",
                        "$hidden hidden".takeIf { hidden > 0 }, if (editing) saveState(saving, changed) else null, file?.extension?.let { ".$it" }), islandContentColor())
                }
                if (editing) {
                    IconButton(onClick = ::undoEdit, enabled = undos.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo") }
                    IconButton(onClick = ::redoEdit, enabled = redos.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_redo), contentDescription = "Redo") }
                    IconButton(onClick = { save() }, enabled = changed && !saving) { Icon(painterResource(R.drawable.ic_save), contentDescription = "Save") }
                    IconButton(onClick = { editing = false; chosen = null; typing = null }) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Done") }
                } else {
                    IconButton(onClick = { notes = !notes }, enabled = deck != null) {
                        Icon(painterResource(R.drawable.ic_comment), contentDescription = if (notes) "Hide the notes" else "Show the notes", tint = if (notes) POWERPOINT_ORANGE else islandContentColor())
                    }
                    IconButton(onClick = { presenting = 0 }, enabled = view?.slides?.isNotEmpty() == true) { Icon(painterResource(R.drawable.ic_play), contentDescription = "Present") }
                    file?.let { f ->
                        IconButton(onClick = { shareDriveFile(context, f) }) { Icon(painterResource(R.drawable.ic_share), contentDescription = "Share") }
                        IconButton(onClick = { openElsewhere(context, f) }) { Icon(painterResource(R.drawable.ic_open_with), contentDescription = "Open in another app") }
                    }
                    // Edit at the far right, where Done is while editing — the same place in Words, Tables and Slides
                    if (deck != null && file?.extension?.lowercase() in setOf("pptx", "odp") && file!!.canWrite()) IconButton(onClick = { editing = true; WriterLog.i("Slides: editing") }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
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
                    // the slide in the middle of the screen, where a swipe leaves it
                    val at by remember { derivedStateOf { list.layoutInfo.let { l -> val mid = (l.viewportStartOffset + l.viewportEndOffset) / 2
                        l.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: 0 } } }
                    // a wide screen (a tablet held sideways): each slide no wider than fits the screen's height, so a whole slide shows
                    val fit = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 200).coerceAtLeast(200).dp * (d.width / d.height)
                    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
                    // a tablet edits a slide at a time: the first is chosen to begin with
                    LaunchedEffect(editing, wide, chosen) { if (editing && wide && chosen == null) chosen = 0 } // the ribbon stays (asked 17 Oct: it went after Save)
                    /**
                     * A desktop's notes pane (the ribbon at the top, asked 17 Oct): slide [i]'s speaker notes under it as PowerPoint's,
                     * small, typed in where they are, a scroll bar beside them; what is typed goes in as it is (one step of Undo a visit).
                     */
                    @Composable fun NotesPane(i: Int, s: Slide) {
                        val uid = edit?.order?.getOrNull(i)?.uid ?: return
                        var draft by remember(uid) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(s.notes)) }
                        var stepped by remember(uid) { mutableStateOf(false) } // this visit's step of Undo made
                        // Undo (or another slide's notes) shown while not typed in
                        LaunchedEffect(s.notes) { if (notesTyping?.first != uid && draft.text != s.notes) draft = androidx.compose.ui.text.input.TextFieldValue(s.notes) }
                        val scroll = rememberScrollState()
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(88.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3A))) {
                            androidx.compose.foundation.text.BasicTextField(draft, { v ->
                                    draft = v
                                    val ee = edit ?: return@BasicTextField
                                    if (v.text != (ee.notes[uid] ?: s.notes)) { val next = ee.copy(notes = ee.notes + (uid to v.text)); if (stepped) edit = next else { change(next); stepped = true } }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 12.dp, top = 10.dp, end = 20.dp, bottom = 10.dp).heightIn(min = 60.dp)
                                    .onFocusChanged { f -> if (f.isFocused) { notesTyping = uid to draft; if (chosen != i) chosen = i } else if (notesTyping?.first == uid) { notesTyping = null; stepped = false } },
                                decorationBox = { inner -> Box { if (draft.text.isEmpty()) Text("Click to add speaker notes", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium); inner() } })
                            // the scroll bar: its thumb as long as the part shown, dragged with the mouse
                            if (scroll.maxValue > 2) Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp)
                                .pointerInput(Unit) { detectVerticalDragGestures { ch, dy -> ch.consume(); scroll.dispatchRawDelta(dy * (size.height + scroll.maxValue) / size.height) } }
                                .drawBehind {
                                    val h = size.height; val th = (h * h / (h + scroll.maxValue)).coerceAtLeast(16.dp.toPx())
                                    drawRoundRect(Color.White.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), scroll.value.toFloat() / scroll.maxValue * (h - th)),
                                        androidx.compose.ui.geometry.Size(4.dp.toPx(), th), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                })
                        }
                    }
                    /** Slide [i] as it is edited: chosen with a tap, its items tapped, moved and sized (the phone's list, the tablet's big slide). */
                    @Composable fun SlideBox(i: Int, s: Slide) {
                        Box(Modifier.fillMaxWidth().aspectRatio(d.width / d.height)
                            // a picture dragged in from another app goes on this slide (in its middle)
                            .dropPictures(editing) { uri -> chosen = i; item = null; putPicture(uri) }
                            // a mouse's right button (a desktop's, asked 16 Oct): what is under it chosen, and its menu; before the taps see it
                            .pointerInput(editing, i, s) {
                                if (!editing) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    if (!currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                                    down.consume()
                                    val x = down.position.x / size.width * d.width; val y = down.position.y / size.height * d.height
                                    if (chosen != i) { chosen = i; typing = null }
                                    item = s.items.lastOrNull { it.anim != null && x in it.x..(it.x + it.w) && y in it.y..(it.y + it.h) }?.anim
                                    slideMenu = Triple(false, i, down.position)
                                }
                            }
                            // what is chosen, dragged with a finger: moved, or sized from its corner's handle (one step of Undo); elsewhere the list scrolls
                            .pointerInput(editing, chosen, i, item) {
                                if (!editing || chosen != i || item == null) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val ee = edit ?: return@awaitEachGesture; val uid = ee.order[i].uid; val id = item ?: return@awaitEachGesture
                                    val a = current(ee, i, id) ?: return@awaitEachGesture
                                    val k = d.width / size.width // points a pixel
                                    val px = down.position.x * k; val py = down.position.y * k
                                    val reach = 28.dp.toPx() * k
                                    val handle = kotlin.math.hypot(px - (a.x + a.w), py - (a.y + a.h)) < reach
                                    if (!handle && !(px in a.x..(a.x + a.w) && py in a.y..(a.y + a.h))) return@awaitEachGesture
                                    down.consume()
                                    undos += ee; if (undos.size > 100) undos.removeAt(0)
                                    var x = a.x; var y = a.y; var w = a.w; var h = a.h
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        val dx = (ch.position.x - ch.previousPosition.x) * k; val dy = (ch.position.y - ch.previousPosition.y) * k
                                        if (handle) { w = maxOf(12f, w + dx); h = w * a.h / a.w.coerceAtLeast(1f) } else { x += dx; y += dy }
                                        val now = edit ?: break
                                        edit = SlidesEdit.framed(now, uid, id, x, y, w, h)
                                        ch.consume()
                                    }
                                    WriterLog.i("Slides: ${if (handle) "sized" else "moved"} by a finger")
                                }
                            }) {
                        SlideCanvas(d, s, hints = editing, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                            .then(if (chosen == i) Modifier.border(3.dp, POWERPOINT_ORANGE, RoundedCornerShape(4.dp)) else Modifier)
                            .pointerInput(editing, chosen, i, s) {
                                detectTapGestures { o ->
                                    if (!editing) { presenting = i; return@detectTapGestures }
                                    if (chosen != i) { chosen = i; typing = null; item = null; return@detectTapGestures }
                                    val x = o.x / size.width * d.width; val y = o.y / size.height * d.height
                                    // what is tapped is chosen (its tools in the ribbon); tapped again, a text box's words are typed, a table's cell
                                    val hit = s.items.lastOrNull { it.anim != null && x in it.x..(it.x + it.w) && y in it.y..(it.y + it.h) }
                                    val a = hit?.anim?.let { id -> edit?.let { ee -> ee.added[ee.order[i].uid]?.firstOrNull { it.id == id } } }
                                    when {
                                        hit == null -> { item = null; message = "Tap a text box, shape or picture on the slide" }
                                        a?.prst == "table" && item == a.id -> {
                                            val r = ((y - a.y) / (a.h / a.rows)).toInt().coerceIn(0, a.rows - 1); val c = ((x - a.x) / (a.w / a.cols)).toInt().coerceIn(0, a.cols - 1)
                                            val t = a.cells.getOrNull(r * a.cols + c).orEmpty()
                                            cellEdit = (r * a.cols + c) to androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length))
                                        }
                                        item == hit.anim && typable(hit, a) -> typeInto(hit as SlideItem.Shape)
                                        else -> { item = hit.anim; typing = null }
                                    }
                                }
                            })
                        // what is chosen: an outline, and (put on it here) the handle its size is dragged from
                        val sel = if (editing && chosen == i) item?.let { SlidesEdit.box(s, it) } else null
                        sel?.let { a -> Canvas(Modifier.matchParentSize()) {
                            val k = size.width / d.width
                            drawRect(POWERPOINT_ORANGE, androidx.compose.ui.geometry.Offset(a.x * k, a.y * k), androidx.compose.ui.geometry.Size(a.w * k, a.h * k), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            val c = androidx.compose.ui.geometry.Offset((a.x + a.w) * k, (a.y + a.h) * k)
                            drawCircle(Color.White, 9.dp.toPx(), c); drawCircle(POWERPOINT_ORANGE, 7.dp.toPx(), c)
                        } }
                        slideMenu?.takeIf { !it.first && it.second == i }?.let { (_, _, at) -> OfficeMenu(at, { slideMenu = null }) {
                                @Composable fun entry(label: String, icon: Int, action: () -> Unit) = OfficeMenuItem(label, icon) { slideMenu = null; WriterLog.i("Slides: menu › $label"); action() }
                                val hit = item?.let { id -> s.items.lastOrNull { it.anim == id } }
                                val ownWords = typable(hit, edit?.let { ee -> ee.added[ee.order[i].uid]?.firstOrNull { it.id == item } })
                                if (ownWords) entry("Edit text", R.drawable.ic_edit) { typeInto(hit as SlideItem.Shape) }
                                if (item != null) {
                                    entry("Cut", R.drawable.ic_content_cut) { if (copyItem()) deleteItem() }
                                    entry("Copy", R.drawable.ic_copy) { copyItem() }
                                }
                                if (clip != null) entry("Paste", R.drawable.ic_content_paste) { pasteItem() }
                                if (item != null) entry("Delete", R.drawable.ic_delete) { deleteItem() }
                                if (item == null) {
                                    if (deck?.layouts?.isNotEmpty() == true) entry("New slide", R.drawable.ic_add) { layouts = true }
                                    entry("Duplicate slide", R.drawable.ic_copy) { duplicateSlide(i) }
                                }
                        } }
                        }
                    }
                    // a tablet editing (asked 16 Oct): the slides in a sidebar, the chosen one big beside it, as PowerPoint's and LibreOffice's
                    if (wide && editing) Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.width(200.dp).fillMaxHeight().background(Color(0xFF1F1F1F)), contentPadding = PaddingValues(12.dp, if (OfficeUi.top) 150.dp else 12.dp, 12.dp, if (OfficeUi.top) 24.dp else 150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(d.slides) { i, s ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("${i + 1}", color = if (chosen == i) POWERPOINT_ORANGE else Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(22.dp))
                                    SlideCanvas(d, s, modifier = Modifier.fillMaxWidth().aspectRatio(d.width / d.height).clip(RoundedCornerShape(3.dp))
                                        .then(if (chosen == i) Modifier.border(2.dp, POWERPOINT_ORANGE, RoundedCornerShape(3.dp)) else Modifier)
                                        .then(if (s.hidden) Modifier.alpha(0.5f) else Modifier)
                                        .clickable { if (chosen != i) { chosen = i; typing = null; item = null } }
                                        .pointerInput(i) { awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                            if (!currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
                                            down.consume(); chosen = i; typing = null; item = null; slideMenu = Triple(true, i, down.position)
                                        } })
                                    slideMenu?.takeIf { it.first && it.second == i }?.let { (_, _, at) -> OfficeMenu(at, { slideMenu = null }) {
                                            @Composable fun entry(label: String, icon: Int, enabled: Boolean = true, action: () -> Unit) = OfficeMenuItem(label, icon, enabled) { slideMenu = null; WriterLog.i("Slides: slide menu › $label"); action() }
                                            val ee = edit
                                            if (deck?.layouts?.isNotEmpty() == true) entry("New slide", R.drawable.ic_add) { layouts = true }
                                            entry("Duplicate", R.drawable.ic_copy) { duplicateSlide(i) }
                                            if (clip != null) entry("Paste", R.drawable.ic_content_paste) { pasteItem() }
                                            if (ee != null) {
                                                val uid = ee.order[i].uid
                                                val hidden = ee.hidden[uid] ?: deck?.slides?.getOrNull(ee.order[i].src)?.hidden ?: false
                                                entry(if (hidden) "Show slide" else "Hide slide", if (hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off) { change(ee.copy(hidden = ee.hidden + (uid to !hidden))) }
                                                entry("Delete slide", R.drawable.ic_delete, enabled = ee.order.size > 1) { change(ee.copy(order = ee.order.filterIndexed { k, _ -> k != i })); chosen = i.coerceAtMost(ee.order.size - 2) }
                                            }
                                    } }
                                }
                            }
                        }
                        // the chosen slide as big as the room above the floating ribbon lets it be (and above an on-screen keyboard, the notes pane typed in)
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().imePadding().padding(start = 24.dp, end = 24.dp, top = if (OfficeUi.top) 150.dp else 16.dp, bottom = if (OfficeUi.top) 16.dp else 150.dp), contentAlignment = Alignment.TopCenter) {
                            val c = (chosen ?: 0).coerceIn(0, d.slides.lastIndex)
                            // as big as it can be; on a desktop, room kept under it for its notes pane
                            // with little room (an on-screen keyboard up) the slide gives way and the pane keeps its height, at least a readable width
                            val slideW = minOf(maxWidth, (maxHeight - if (OfficeUi.top) 96.dp else 0.dp).coerceAtLeast(if (OfficeUi.top) 0.dp else 120.dp) * (d.width / d.height))
                            Column(Modifier.width(if (OfficeUi.top) maxOf(slideW, minOf(maxWidth, 480.dp)) else slideW), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.width(slideW)) { SlideBox(c, d.slides[c]) }; if (OfficeUi.top) NotesPane(c, d.slides[c]) }
                        }
                    } else {
                    // page by page (asked 17 Oct): a swipe ends with the next or the one before in the middle, however hard it is flung
                    val oneAtATime = remember(list) {
                        val base = androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(list, androidx.compose.foundation.gestures.snapping.SnapPosition.Center)
                        object : androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider by base {
                            override fun calculateApproachOffset(velocity: Float, decayOffset: Float) = 0f
                        }
                    }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(oneAtATime), contentPadding = PaddingValues(12.dp, if (OfficeUi.top) 130.dp else 12.dp, 12.dp, if (OfficeUi.top) 24.dp else 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(d.slides) { i, s ->
                            Column(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = fit)) {
                                Text("${i + 1}${if (s.hidden) " · hidden" else ""}", color = if (chosen == i) POWERPOINT_ORANGE else Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                                // viewing: a slide tapped is presented from there; editing: tapped, it is chosen — tapped again on a text box, its words are typed
                                SlideBox(i, s)
                                if (editing && chosen == i && OfficeUi.top) NotesPane(i, s)
                                else if (notes && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0xFF3A3A3A), RoundedCornerShape(6.dp)).padding(10.dp))
                            }
                        }
                    }
                    }
                    if (!editing || chosen == null) Surface(color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White, shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomCenter).bottomBarPadding().padding(bottom = 16.dp)) {
                        Text(if (editing) "Tap a slide to change it" else "Slide ${(at + 1).coerceAtMost(d.slides.size)} of ${d.slides.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    // the chosen slide's ribbon, its tabs as Tables' (asked 16 Oct): what is chosen on it, the slide, Insert, Transitions, Animations
                    val e = edit
                    androidx.compose.animation.AnimatedVisibility(editing && chosen != null && typing == null && cellEdit == null && (notesTyping == null || OfficeUi.top) && !layouts && !shapes && !tables && e != null,
                        // at the bottom for a finger; at the top while a mouse is in use (or as Settings say), as a PC's PowerPoint
                        Modifier.align(if (OfficeUi.top) Alignment.TopCenter else Alignment.BottomCenter),
                        enter = if (OfficeUi.top) androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn() else SLIDE_IN,
                        exit = if (OfficeUi.top) androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut() else SLIDE_OUT) {
                        val c = (chosen ?: 0).coerceIn(0, (e?.order?.size ?: 1) - 1)
                        val w = islandContentColor()
                        // tabs as Words' (17 Oct): File, Home, Insert, …; what is chosen on the slide last, named by what it is
                        val tabs = listOfNotNull("File", "Home", "Insert", "Design", "Transitions", "Animations", "Slide show", "Item".takeIf { item != null })
                        val shown = ribbonTab.takeIf { it in tabs } ?: "Home"
                        val itemKind = e?.let { e1 ->
                            val a = e1.added[e1.order[c].uid]?.firstOrNull { it.id == item }
                            when (val it0 = view?.slides?.getOrNull(c)?.items?.lastOrNull { it.anim == item }) {
                                is SlideItem.Table -> "Table"; is SlideItem.Picture -> "Image"; is SlideItem.Chart -> "Chart"
                                is SlideItem.Shape -> if (it0.shape == null && it0.text != null) "Text box" else "Shape"
                                null -> if (a?.prst == "table") "Table" else if (a?.image != null) "Image" else "Shape"
                            }
                        } ?: "Shape"
                        // a phone: solid across the bottom, as Excel's ribbon; a tablet: Words' floating island
                        Surface(color = Color.Black, contentColor = w, modifier = if (wide) Modifier.fillMaxWidth().bottomBarPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge) else Modifier.fillMaxWidth()) {
                            // Options and Effect take the ribbon's place, ‹ back, as Tables' Borders and Number (17 Oct)
                            val strip = when { shown == "Transitions" && effectOptions -> "Transition options"; shown == "Animations" && item != null && effectKinds -> "Effect"
                                shown == "Animations" && item != null && effectOptions -> "Effect options"; else -> null }
                            androidx.compose.animation.AnimatedContent(strip, label = "strip", transitionSpec = { SLIDE_IN togetherWith SLIDE_OUT }) { st ->
                            if (st != null) Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).then(if (wide) Modifier else Modifier.bottomBarPadding()), verticalAlignment = Alignment.CenterVertically) {
                                StripHead(st, w) { effectOptions = false; effectKinds = false }
                                RibbonLine(androidx.compose.foundation.rememberScrollState(), Modifier.padding(vertical = 2.dp)) { if (e != null) SlideRibbon(shown, c, e, w) }
                            } else
                            Column(if (wide) Modifier.padding(vertical = 4.dp) else Modifier.bottomBarPadding()) {
                                OfficeTabRow(tabs, shown, w, POWERPOINT_ORANGE, { ribbonTab = it }, contextual = "Item", label = { if (it == "Item") itemKind else it }) {
                                    // a tablet always has a slide chosen: ✕ lets go of what is chosen on it only
                                    if (!wide || item != null) IconButton(onClick = { if (item != null) item = null else chosen = null }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", modifier = Modifier.size(16.dp)) }
                                }
                                // the tab's tools on one sliding line, as Words' and Tables' (only catalogs, as colours or effects, take 2–3 lines)
                                key(shown) {
                                    // one sliding line; on a desktop, rows of whole groups (asked 16 Oct)
                                    RibbonLine(androidx.compose.foundation.rememberScrollState(), Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        if (e != null) SlideRibbon(shown, c, e, w)
                                    }
                                }
                            }
                            }
                        }
                    }
                    // New slide: the deck's layouts in the strip's place, each a small slide; the one tapped comes after the chosen slide
                    // Home › Font and Insert › Drop cap: Words' strips, a card tapped shown at once; Cancel puts the words back
                    var stripShown by remember { mutableStateOf(DesignGallery.Font) } // kept while it slides away
                    strip?.let { stripShown = it }
                    androidx.compose.animation.AnimatedVisibility(editing && strip != null && e != null && item != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val fonts = remember(base) { ((base?.slides.orEmpty().flatMap { s -> s.items.filterIsInstance<SlideItem.Shape>().flatMap { sh -> sh.text?.paras.orEmpty().flatMap { pp -> pp.runs.mapNotNull { r -> DocStyles.Plain.fontName(r.font) } } } })
                            .distinct().sorted() + STYLE_FONTS).distinctBy { it.lowercase() } }
                        val now = chosenWords()?.text?.paras?.flatMap { it.runs }?.firstOrNull { it.text.isNotBlank() }?.font?.let(DocStyles.Plain::fontName)
                        fun close() { stripBefore = null; stripPicked = null; strip = null }
                        DesignGalleryStrip(stripShown, stripPicked, cancel = { stripBefore?.let { edit = it }; close() }, ok = {
                            val made = edit; stripBefore?.let { b -> edit = b; if (made != null && made != b) change(made) }; close(); WriterLog.i("Slides: ${stripShown.title} chosen")
                        }, pick = { ch ->
                            val b = stripBefore ?: edit ?: return@DesignGalleryStrip; val cc = chosen ?: return@DesignGalleryStrip; val id = item ?: return@DesignGalleryStrip
                            val k = b.order[cc].uid to id; val l = b.looks[k] ?: SlidesEdit.Look()
                            val size = wordsSize(chosenWords())
                            stripBefore = b; stripPicked = ch
                            edit = if (ch == DropKind.Dropped) dropped(b, cc, id, dropLines) ?: b else b.copy(looks = b.looks + (k to when (ch) {
                                is FontChoice -> l.copy(font = ch.name)
                                DropKind.Raised -> l.copy(raise = size * dropLines)
                                else -> l.copy(raise = 0f) // None
                            }))
                        }, more = if (stripShown == DesignGallery.DropCap) { { stripBefore?.let { edit = it }; close(); dropOptions = true } } else null,
                            fonts = fonts, now = now, drops = listOf(DropKind.None, DropKind.Dropped, DropKind.Raised), dropLines = dropLines, dropLinesChange = { n ->
                                dropLines = n
                                // the letter tapped shown again at its new height
                                val b = stripBefore ?: return@DesignGalleryStrip; val cc = chosen ?: return@DesignGalleryStrip; val id = item ?: return@DesignGalleryStrip
                                val k = b.order[cc].uid to id
                                if (stripPicked == DropKind.Raised) edit = b.copy(looks = b.looks + (k to (b.looks[k] ?: SlidesEdit.Look()).copy(raise = SlidesEdit.slide(base ?: return@DesignGalleryStrip, b, b.order[cc]).items.lastOrNull { it.anim == id }.let { wordsSize(it as? SlideItem.Shape) } * n)))
                                if (stripPicked == DropKind.Dropped) dropped(b, cc, id, n)?.let { edit = it }
                            })
                    }
                    if (footing) {
                        val ee = edit; val cc = chosen
                        if (ee == null || cc == null) footing = false else {
                            // what the slide shows now: its choices here, else its own date, footer and number
                            val sl = view?.slides?.getOrNull(cc)
                            val start = ee.footers[ee.order[cc].uid] ?: sl?.let { v ->
                                fun words(k: String) = v.items.filterIsInstance<SlideItem.Shape>().firstOrNull { v.footerIds[it.id] == k }?.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { it.text } }
                                val date = words("dt")
                                SlidesEdit.Footers(date != null, date?.takeIf { it != SlidesEdit.today() }, words("sldNum") != null, words("ftr"))
                            } ?: SlidesEdit.Footers()
                            FootersDialog(start, { footing = false }) { f, all, notOnTitle ->
                                footing = false
                                val base = view
                                fun mine(i: Int, e: SlidesEdit.Entry) = if (notOnTitle && base?.slides?.getOrNull(i)?.titleSlide == true) SlidesEdit.Footers() else f
                                change(ee.copy(footers = ee.footers + if (all) ee.order.mapIndexed { i, e -> e.uid to mine(i, e) } else listOf(ee.order[cc].uid to mine(cc, ee.order[cc]))))
                                WriterLog.i("Slides: header and footer on ${if (all) "all slides" else "a slide"}")
                            }
                        }
                    }
                    if (linking) {
                        val box = chosenWords()
                        val now = box?.text?.paras?.flatMap { it.runs }?.firstNotNullOfOrNull { it.link }
                        val ee = edit
                        // a slide's link shown as its place in the deck
                        val shownAt = now?.let { l -> (if (l.startsWith("slide:")) ee?.order?.indexOfFirst { it.uid == l.removePrefix("slide:").toIntOrNull() } else view?.slides?.indexOfFirst { it.part == l && l.isNotEmpty() })?.takeIf { it >= 0 }?.let { "#$it" } } ?: now
                        val names = view?.slides.orEmpty().map { sl -> sl.items.filterIsInstance<SlideItem.Shape>().firstNotNullOfOrNull { sh -> sh.text?.paras?.joinToString(" ") { p -> p.runs.joinToString("") { it.text } }?.trim()?.takeIf { it.isNotEmpty() && !sh.hint } }?.take(40) ?: "Slide" }
                        LinkDialog(box == null, shownAt, null, now != null, { linking = false }, { linking = false; restyle { it.copy(link = "") } }, names) { text, to ->
                            linking = false
                            val target = to.takeIf { it.startsWith("#") }?.drop(1)?.toIntOrNull()?.let { i -> ee?.order?.getOrNull(i)?.let { "slide:${it.uid}" } } ?: to
                            if (box != null) restyle { it.copy(link = target) }
                            else { val d0 = deck ?: return@LinkDialog; val e0 = edit ?: return@LinkDialog; val cc = chosen ?: return@LinkDialog
                                val a = SlidesEdit.Added(e0.newId(e0.order[cc].uid, taken(e0, cc)), d0.width * 0.2f, d0.height * 0.42f, d0.width * 0.6f, 40f, prst = SlidesEdit.TEXT_BOX)
                                val k = e0.order[cc].uid to a.id
                                change(e0.copy(added = e0.added + (k.first to e0.added[k.first].orEmpty() + a), texts = e0.texts + (k to listOf(text)), looks = e0.looks + (k to SlidesEdit.Look(link = target))))
                                item = a.id }
                            WriterLog.i("Slides: a link put on")
                        }
                    }
                    charting?.let { (id, start) ->
                        ChartDialog(start, id != null, { charting = null }) { ch ->
                            charting = null
                            if (id != null) { alter { it.copy(chart = ch) }; WriterLog.i("Slides: a chart's data changed") }
                            else { val d = deck ?: return@ChartDialog; val ee = edit ?: return@ChartDialog; val cc = chosen ?: return@ChartDialog
                                put(SlidesEdit.Added(ee.newId(ee.order[cc].uid, taken(ee, cc)), d.width * 0.15f, d.height * 0.18f, d.width * 0.7f, d.height * 0.7f, prst = "chart", chart = ch))
                                WriterLog.i("Slides: a chart put on") }
                        }
                    }
                    if (dropOptions) chosenWords()?.let { box ->
                        val p = box.text?.paras?.firstOrNull { pp -> pp.runs.any { it.text.isNotBlank() } }
                        val k = p?.runs?.indexOfFirst { it.text.isNotEmpty() } ?: -1
                        val size = wordsSize(box)
                        val letter = p?.runs?.getOrNull(k)?.takeIf { SlidesEdit.isRaised(p.runs, k) != null }
                        DropCapDialog(letter?.let { DropCap(lines = Math.round((it.sizePt ?: size) / size).coerceIn(2, 10), raised = true) }, true, letter?.font?.let(DocStyles.Plain::fontName), letter?.color,
                            { dropOptions = false }, raised = true) { d, font, color ->
                            dropOptions = false
                            val ee = edit; val cc = chosen; val id = item
                            if (d != null && !d.raised) { if (ee != null && cc != null && id != null) dropped(ee, cc, id, d.lines, font, color?.or(0xFF000000.toInt()), d.distance)?.let { change(it); WriterLog.i("Slides: a drop cap dropped") } }
                            else restyle { it.copy(raise = d?.let { dc -> size * dc.lines } ?: 0f, raiseFont = font, raiseColor = color?.or(0xFF000000.toInt())) }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(editing && layouts && e != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val full = deck
                        Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.bottomBarPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    // Shape: Words' panel (asked 16 Oct: more than seven) — Word's shapes in their groups, Shape Styles and colours; the one tapped goes in the slide's middle
                    if (editing && shapes && e != null) ShapesDialog(odt = true, dismiss = { shapes = false }) { kind, fill, line ->
                        shapes = false
                        val dd = deck; val ee = edit; val c = chosen
                        if (dd != null && ee != null && c != null) {
                            val side = dd.height * 0.3f; val uid = ee.order[c].uid; val id = ee.newId(uid, taken(ee, c))
                            put(SlidesEdit.Added(id, (dd.width - side) / 2, (dd.height - side) / 2, side, side, prst = kind, fill = fill ?: 0xFF4472C4.toInt()))
                            // its fill and outline as chosen (none for either kept as none)
                            edit?.let { now -> edit = now.copy(looks = now.looks + ((uid to id) to SlidesEdit.Look(fill = fill?.or(0xFF000000.toInt()) ?: SlidesEdit.NONE, line = line?.or(0xFF000000.toInt()) ?: SlidesEdit.NONE))) }
                            WriterLog.i("Slides: a shape put on a slide ($kind)")
                        }
                    }
                    // Table: its size; the table goes in the middle, four fifths of the slide wide, a row 36 pt high
                    androidx.compose.animation.AnimatedVisibility(editing && tables && e != null, Modifier.align(Alignment.BottomCenter), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val w = islandContentColor()
                        Surface(color = Color.Black, contentColor = w, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.bottomBarPadding().fillMaxWidth().shadedRow().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { tables = false }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel") }
                                for ((rows, cols) in listOf(2 to 2, 3 to 2, 3 to 3, 4 to 3, 4 to 4, 5 to 4)) RibbonButton("$rows × $cols", w) {
                                    val dd = deck ?: return@RibbonButton; val ee = edit ?: return@RibbonButton; val c = chosen ?: return@RibbonButton
                                    val tw = dd.width * 0.8f; val th = rows * 36f
                                    put(SlidesEdit.Added(ee.newId(ee.order[c].uid, taken(ee, c)), (dd.width - tw) / 2, (dd.height - th) / 2, tw, th, prst = "table", rows = rows, cols = cols, cells = List(rows * cols) { "" }))
                                    tables = false; message = "Tap a cell to type in it"; WriterLog.i("Slides: a table put on a slide ($rows × $cols)")
                                }
                            }
                        }
                    }
                    // a text box's words: a line a paragraph, ✕ leaves them, ✓ puts them in (one step of Undo)
                    androidx.compose.animation.AnimatedVisibility(typing != null || cellEdit != null || (notesTyping != null && !OfficeUi.top), Modifier.align(Alignment.BottomCenter).imePadding(), enter = SLIDE_IN, exit = SLIDE_OUT) {
                        val cell = cellEdit
                        // a slide's notes: ✕ leaves them, ✓ puts them in (one step of Undo)
                        val nt = notesTyping?.takeIf { !OfficeUi.top }
                        if (nt != null) Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.bottomBarPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { notesTyping = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave them as they were") }
                                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                                LaunchedEffect(nt.first) { focus.requestFocus() }
                                androidx.compose.material3.OutlinedTextField(nt.second, { notesTyping = nt.first to it }, minLines = 3, modifier = Modifier.weight(1f).heightIn(max = 260.dp).focusRequester(focus),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), label = { Text("Speaker notes") })
                                IconButton(onClick = {
                                    edit?.let { ee -> change(ee.copy(notes = ee.notes + (nt.first to nt.second.text.trimEnd()))); WriterLog.i("Slides: notes changed") }
                                    notesTyping = null
                                }) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Put them in") }
                            }
                        }
                        if (cell != null) Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.bottomBarPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { cellEdit = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave it as it was") }
                                // the keyboard at once: the box opened to be typed in
                                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                                LaunchedEffect(cell.first) { focus.requestFocus() }
                                androidx.compose.material3.OutlinedTextField(cell.second, { cellEdit = cell.first to it }, singleLine = true, modifier = Modifier.weight(1f).focusRequester(focus),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), label = { Text("The cell's words") })
                                IconButton(onClick = { alter { a -> a.copy(cells = a.cells.toMutableList().also { it[cell.first] = cell.second.text }) }; cellEdit = null }) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = "Put them in") }
                            }
                        }
                        val t = typing
                        if (cell == null && nt == null)
                        Surface(color = Color.Black, contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.bottomBarPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { typing = null }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave it as it was") }
                                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                                LaunchedEffect(t?.first) { if (t != null) focus.requestFocus() }
                                if (t != null) androidx.compose.material3.OutlinedTextField(t.second, { typing = t.first to it }, modifier = Modifier.weight(1f).heightIn(max = 220.dp).focusRequester(focus),
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
                    OfficeMessage(message, top = 12.dp) { message = null }
                }
            }
        }
    }
    if (themesPage) ThemesDialog(edit?.theme, { themesPage = false }) { t -> themesPage = false; edit?.let { change(it.copy(theme = t)) } }
    if (leaving) androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = false },
        title = { Text("Save the changes?") },
        text = { Text(if (undos.isEmpty()) "Your changes to ${file?.name ?: "this presentation"} are not saved." else "${undos.size} change${if (undos.size == 1) "" else "s"} to ${file?.name ?: "this presentation"} are not saved.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { leaving = false; save { back() } }) { Text("Save") } },
        dismissButton = { Row {
            androidx.compose.material3.TextButton(onClick = { leaving = false; file?.let { slideDraft(context, it).delete() }; WriterLog.i("Slides: left without saving"); back() }) { Text("Don't save") }
            androidx.compose.material3.TextButton(onClick = { leaving = false }) { Text("Cancel") }
        } })
    val d = view
    presenting?.let { start -> if (d != null) Present(d, start, notes, jump = { l ->
        // a link's slide: one linked here (its uid), or the deck's own part a file's link names
        (if (l.startsWith("slide:")) edit?.order?.indexOfFirst { it.uid == l.removePrefix("slide:").toIntOrNull() } else d.slides.indexOfFirst { it.part == l })?.takeIf { it >= 0 }
    }) { presenting = null } }
    if (officeSettings) OfficeSettingsDialog { officeSettings = false }
    if (showVersions) file?.let { f -> VersionsDialog(f, "leaving without saving goes back", { showVersions = false }) { v -> showVersions = false; openVersion(v) } }
    if (about) androidx.compose.material3.AlertDialog(onDismissRequest = { about = false },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { about = false }) { Text("Close") } },
        title = { Text(file?.name ?: "Presentation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (odp) "OpenDocument presentation (.odp)" else "PowerPoint presentation (.pptx)")
                file?.let { Text("${"%.1f".format(it.length() / 1024.0 / 1024.0)} MB · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified()))) }
                view?.let { d ->
                    val hidden = d.slides.count { it.hidden }
                    Text("${d.slides.size} slide${if (d.slides.size == 1) "" else "s"}" + (if (hidden > 0) ", $hidden hidden" else "") +
                        " · ${if (kotlin.math.abs(d.width / d.height - 16f / 9) < 0.05f) "16:9" else if (kotlin.math.abs(d.width / d.height - 4f / 3) < 0.05f) "4:3" else "%.1f × %.1f cm".format(d.width / 72 * 2.54, d.height / 72 * 2.54)}")
                    val notes = d.slides.count { it.notes.isNotBlank() }
                    if (notes > 0) Text("Speaker notes on $notes slide${if (notes == 1) "" else "s"}")
                }
            }
        })
}

/** One slide, as wide as it is given, its own shape. */
@Composable
private fun SlideCanvas(deck: Deck, slide: Slide, modifier: Modifier, hints: Boolean = false) {
    // a slide with a GIF is drawn again every frame while it shows, so it plays
    val moves = remember(slide) { Slides.moves(slide) }
    val frame by androidx.compose.runtime.produceState(0L, moves) { if (moves) while (true) androidx.compose.runtime.withFrameNanos { value = it } }
    Canvas(modifier.aspectRatio(deck.width / deck.height).background(Color.White)) {
        frame // read: a new frame, a new drawing
        drawIntoCanvas { c -> Slides.draw(c.nativeCanvas, deck, slide, size.width / deck.width, hints) }
    }
}

/**
 * Present: the slides full screen and sideways on black, one at a time; a tap or a swipe to the left goes on (the next
 * click's effects, else the next slide with its transition), a swipe the other way back (the slide before, shown whole);
 * two taps bring out the tools, three turn the screen black and back; the shown slides only (hidden ones skipped); back
 * to the list with Back.
 */
@Composable
private fun Present(deck: Deck, start: Int, notes: Boolean, jump: (String) -> Int? = { null }, close: () -> Unit) {
    val context = LocalContext.current
    val shown = remember(deck) { deck.slides.indices.filter { !deck.slides[it].hidden || it == start } }
    var page by remember { mutableStateOf(shown.indexOf(start).coerceAtLeast(0)) }
    // the slide's clicks done; the one playing (and when it began); the slide it came from, and when, for its transition
    var step by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var from by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val now by androidx.compose.runtime.produceState(System.nanoTime()) { while (true) androidx.compose.runtime.withFrameNanos { value = System.nanoTime() } }
    fun slide(p: Int) = deck.slides[shown[p]]
    fun ms(t: Long) = (now - t) / 1_000_000
    // a group of effects lasts as long as its latest one
    fun length(g: SlideStep) = g.effects.maxOfOrNull { it.delay + it.ms } ?: 0
    fun play(g: Int) { playing = g to System.nanoTime(); WriterLog.i("Slides: click ${g + 1}") }
    // the groups that start by themselves: one after the other, at the slide's start and after a click
    LaunchedEffect(page, step, playing, from) {
        val steps = slide(page).steps
        playing?.let { (g, t) -> val left = length(steps[g]) - (System.nanoTime() - t) / 1_000_000; if (left > 0) kotlinx.coroutines.delay(left); step = g + 1; playing = null; return@LaunchedEffect }
        from?.let { (_, t) -> val left = (slide(page).transition?.ms ?: 0) - (System.nanoTime() - t) / 1_000_000; if (left > 0) kotlinx.coroutines.delay(left); from = null; return@LaunchedEffect }
        if (step < steps.size && steps[step].auto) play(step)
    }
    fun next() {
        val steps = slide(page).steps
        when {
            playing != null -> { step = playing!!.first + 1; playing = null } // a click while it plays: it ends at once, as PowerPoint's
            from != null -> from = null
            step < steps.size -> play(step)
            page < shown.lastIndex -> { from = page to System.nanoTime(); page++; step = 0; playing = null; WriterLog.i("Slides: slide ${page + 1}${slide(page).transition?.let { ", ${it.kind}" } ?: ""}") }
            else -> close()
        }
    }
    fun back() { if (page > 0) { page--; step = slide(page).steps.size; playing = null; from = null } }
    /** A tap at [x], [y] in a [w] × [h] window on words with a link (Insert › Link, a file's own): a web page opened, a slide gone to. True: it was one. */
    fun follow(x: Float, y: Float, w: Float, h: Float): Boolean {
        val k = minOf(w / deck.width, h / deck.height); val px = (x - (w - deck.width * k) / 2) / k; val py = (y - (h - deck.height * k) / 2) / k
        val link = slide(page).items.filterIsInstance<SlideItem.Shape>().lastOrNull { px in it.x..it.x + it.w && py in it.y..it.y + it.h && it.text?.paras?.any { p -> p.runs.any { r -> r.link != null } } == true }
            ?.text?.paras?.flatMap { it.runs }?.firstNotNullOfOrNull { it.link } ?: return false
        jump(link)?.let { i -> shown.indexOf(i).takeIf { it >= 0 }?.let { from = page to System.nanoTime(); page = it; step = 0; playing = null; WriterLog.i("Slides: link to slide ${i + 1}") }; return true }
        if (Regex("^[a-zA-Z][a-zA-Z+.-]*:").containsMatchIn(link)) { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }; WriterLog.i("Slides: link opened"); return true }
        return false
    }
    // a slide that goes on by itself: its time once its effects are done (and its transition in), then the next
    // shortcut: PowerPoint counts the time from the slide's start; here it runs after its last effect, the same when it has none
    // Pause (the presenter's notes, asked 17 Oct): a moment aside — slides that go on by themselves wait, the teleprompter stops
    var paused by remember { mutableStateOf(false) }
    LaunchedEffect(page, step, playing, from, paused) {
        val after = slide(page).transition?.after ?: return@LaunchedEffect
        if (paused || playing != null || from != null || step < slide(page).steps.size) return@LaunchedEffect
        kotlinx.coroutines.delay(after.toLong()); next()
    }
    /**
     * An item's look now: hidden until its entrance's click, then appearing, fading in or flying in from its side; an
     * exit the other way (disappearing, fading out, flying out to its side), hidden after it.
     */
    fun look(sl: Slide, p: Int): (SlideItem) -> FloatArray? {
        val mine = HashMap<String, MutableList<Pair<Int, SlideEffect>>>()
        sl.steps.forEachIndexed { g, st -> st.effects.forEach { e -> mine.getOrPut(e.target) { ArrayList() } += g to e } }
        val done = if (p == page) step else sl.steps.size
        val play = if (p == page) playing else null
        val shown = floatArrayOf(1f, 0f, 0f)
        // an emphasis turns or scales about the middle of what it names (a group's, for a group)
        val middle = HashMap<String, Pair<Float, Float>>()
        fun about(id: String) = middle.getOrPut(id) { SlidesEdit.box(sl, id)?.let { it.x + it.w / 2 to it.y + it.h / 2 } ?: (0f to 0f) }
        return { item ->
            val list = item.anim?.let(mine::get)
            if (list == null) shown else {
                val (cx, cy) = about(item.anim!!)
                fun emph(scale: Float, deg: Float) = floatArrayOf(1f, 0f, 0f, scale, deg, cx, cy)
                // before its first effect: hidden if that brings it in, else there
                val first = list.first().second
                var r: FloatArray? = if (first.exit || first.emphasis) shown else null
                for ((g, e) in list) {
                    // done: in, gone, or (grown) bigger
                    if (g < done) { r = if (e.exit) null else if (e.kind == "grow") emph(1.5f, 0f) else shown; continue }
                    if (play?.first == g) {
                        val t = (ms(play.second) - e.delay).toFloat() / e.ms.coerceAtLeast(1)
                        if (t >= 0f) {
                            val k = t.coerceAtMost(1f)
                            // an entrance eases in to where it stands; an exit eases out from there
                            val away = if (e.exit) k * k else (1 - k) * (1 - k)
                            val ease = 1 - (1 - k) * (1 - k)
                            r = when (e.kind) {
                                "pulse" -> emph(1f + 0.1f * kotlin.math.sin(Math.PI * k).toFloat(), 0f)
                                "spin" -> emph(1f, 360f * ease)
                                "grow" -> emph(1f + 0.5f * ease, 0f)
                                "appear" -> if (e.exit) null else shown
                                "zoom" -> (1 - away).let { s -> floatArrayOf(s, 0f, 0f, s.coerceAtLeast(0.01f), 0f, cx, cy) }
                                "wipe" -> {
                                    val f = if (e.exit) 1 - k else k; val (l, t, rr, b) = listOf(item.x - 4, item.y - 4, item.x + item.w + 4, item.y + item.h + 4)
                                    when (e.from) { 'u' -> floatArrayOf(1f, 0f, 0f, 1f, 0f, cx, cy, l, t, rr, t + (b - t) * f); 'l' -> floatArrayOf(1f, 0f, 0f, 1f, 0f, cx, cy, l, t, l + (rr - l) * f, b)
                                        'r' -> floatArrayOf(1f, 0f, 0f, 1f, 0f, cx, cy, rr - (rr - l) * f, t, rr, b); else -> floatArrayOf(1f, 0f, 0f, 1f, 0f, cx, cy, l, b - (b - t) * f, rr, b) }
                                }
                                "fly" -> when (e.from) {
                                    'l' -> floatArrayOf(1f, -(item.x + item.w) * away, 0f); 'r' -> floatArrayOf(1f, (deck.width - item.x) * away, 0f)
                                    'u' -> floatArrayOf(1f, 0f, -(item.y + item.h) * away); else -> floatArrayOf(1f, 0f, (deck.height - item.y) * away)
                                }
                                else -> floatArrayOf(if (e.exit) 1 - k else k, 0f, 0f)
                            }
                        }
                    }
                    break
                }
                r
            }
        }
    }
    /** The slide now, in a [w]×[h] box: the one before under it while its transition plays. */
    fun render(c: android.graphics.Canvas, w: Float, h: Float) {
        val k = minOf(w / deck.width, h / deck.height)
        val sw = deck.width * k; val sh = deck.height * k
        c.save(); c.translate((w - sw) / 2, (h - sh) / 2); c.clipRect(0f, 0f, sw, sh)
        val cur = slide(page)
        val tr = cur.transition?.takeIf { it.kind.isNotEmpty() } // going on by itself only: no transition to draw
        val f = from
        val t = if (f != null && tr != null) (ms(f.second).toFloat() / tr.ms.coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
        val e = 1 - (1 - t) * (1 - t)
        if (f != null && tr != null && t < 1f) {
            val old = slide(f.first)
            fun drawOld(dx: Float = 0f, dy: Float = 0f) { c.save(); c.translate(dx, dy); Slides.draw(c, deck, old, k, look = look(old, f.first)); c.restore() }
            fun drawNew(dx: Float = 0f, dy: Float = 0f) { c.save(); c.translate(dx, dy); Slides.draw(c, deck, cur, k, look = look(cur, page)); c.restore() }
            // push and gallery: both move; cover: the new one over; pull: the old one away; wipe: the new one uncovered; the rest fade
            val (ux, uy) = when (tr.dir) { "r" -> 1f to 0f; "u" -> 0f to -1f; "d" -> 0f to 1f; else -> -1f to 0f }
            when (tr.kind) {
                "push", "gallery", "conveyor", "pan" -> { drawOld(ux * sw * e, uy * sh * e); drawNew(-ux * sw * (1 - e), -uy * sh * (1 - e)) }
                "cover" -> { drawOld(); drawNew(-ux * sw * (1 - e), -uy * sh * (1 - e)) }
                "pull" -> { drawNew(); drawOld(ux * sw * e, uy * sh * e) }
                "wipe" -> { drawOld(); c.save()
                    if (ux != 0f) { if (ux < 0) c.clipRect(sw * (1 - e), 0f, sw, sh) else c.clipRect(0f, 0f, sw * e, sh) } else { if (uy < 0) c.clipRect(0f, sh * (1 - e), sw, sh) else c.clipRect(0f, 0f, sw, sh * e) }
                    drawNew(); c.restore() }
                "cut" -> drawNew()
                else -> { drawOld(); c.saveLayerAlpha(0f, 0f, sw, sh, (e * 255).toInt()); drawNew(); c.restore() }
            }
        } else Slides.draw(c, deck, cur, k, look = look(cur, page))
        c.restore()
    }
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
    // sideways on one screen; with a second screen the presenter's phone turns as it is held (asked 17 Oct: upright, the slide over the notes)
    val activity = context as? Activity
    val was = remember { activity?.requestedOrientation }
    LaunchedEffect(tv != null) { activity?.requestedOrientation = if (tv != null) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }
    DisposableEffect(Unit) {
        WriterLog.i("Slides: presenting from ${start + 1}")
        onDispose { if (was != null) activity?.requestedOrientation = was }
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
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val s = slide(page)
            val upright = tv != null && maxHeight > maxWidth
            // on one screen the tools sit in a dock at the left edge that hides by itself a few seconds after its last use (asked 17 Oct)
            var dock by remember { mutableStateOf(false) }
            var dockUsed by remember { mutableStateOf(0) }
            // it stays while the pen or laser is on (taps draw then, so two taps could not bring it back to turn them off)
            LaunchedEffect(dock, dockUsed, tool) { if (dock && tool == null) { kotlinx.coroutines.delay(4000); dock = false } }
            // the tools: Pen, Laser, and with a second screen Black screen (on one screen a triple tap does it: a button there has no use)
            val toolButtons: @Composable () -> Unit = {
                @Composable fun tool(name: String, icon: Int, on: Boolean, click: () -> Unit) = IconButton(onClick = { click(); dockUsed++ }) {
                    Icon(painterResource(icon), contentDescription = name, tint = if (on) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f))
                }
                tool("Pen", R.drawable.ic_ink_pen, tool == "pen") { tool = if (tool == "pen") null else "pen"; WriterLog.i("Slides: pen ${tool != null}") }
                tool("Laser", R.drawable.ic_highlighter, tool == "laser") { tool = if (tool == "laser") null else "laser" }
                if (ink[page].orEmpty().isNotEmpty()) tool("Wipe the ink", R.drawable.ic_ink_eraser, false) { ink.remove(page) }
                if (tv != null) tool("Black screen", R.drawable.ic_visibility_off, black) { black = !black }
            }
            // the slide, its taps and swipes, the pen and the laser
            val slideArea: @Composable (Modifier) -> Unit = { m -> Box(m.pointerInput(tool) {
                // one tap: on; a swipe: to the left on, to the right back; two quick taps: the tools (one screen); three: the screen black, or back (asked 17 Oct)
                // (a tap waits a quarter second for another, to tell it from a triple tap)
                if (tool == null) awaitEachGesture {
                    var taps = 0; var at = androidx.compose.ui.geometry.Offset.Zero
                    while (taps < 3) {
                        val down = if (taps == 0) awaitFirstDown() else withTimeoutOrNull(250) { awaitFirstDown() } ?: break
                        if (taps == 0) at = down.position
                        var moved = 0f; var end = down
                        while (true) { val ev = awaitPointerEvent(); val ch = ev.changes.firstOrNull { it.id == down.id } ?: break; moved = ch.position.x - down.position.x; end = ch; if (!ch.pressed) break }
                        if (kotlin.math.abs(moved) > 60.dp.toPx()) { if (taps == 0 && !black) { if (moved < 0) next() else back() }; return@awaitEachGesture }
                        taps++
                    }
                    if (taps == 3) { black = !black; WriterLog.i("Slides: black $black") }
                    else if (!black) { if (taps == 2) { if (tv == null) dock = true } else if (!follow(at.x, at.y, size.width.toFloat(), size.height.toFloat())) next() }
                }
            }, contentAlignment = Alignment.Center) {
            // the whole slide in the window it is shown in, black bars on the sides that are left over (fillMaxSize first made it as tall as the window and cut its sides on a 3:2 tablet)
            Box(Modifier.aspectRatio(deck.width / deck.height).alpha(if (black && tv != null) 0.3f else 1f).pointerInput(page, tool) {
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
                Canvas(Modifier.fillMaxSize().background(Color.White)) {
                    now // a new frame: the effects move on
                    drawIntoCanvas { render(it.nativeCanvas, size.width, size.height) }
                    for (stroke in ink[page].orEmpty()) for (i in 1 until stroke.size)
                        drawLine(Color(0xFFE53935), androidx.compose.ui.geometry.Offset(stroke[i - 1].x * size.width, stroke[i - 1].y * size.height),
                            androidx.compose.ui.geometry.Offset(stroke[i].x * size.width, stroke[i].y * size.height), strokeWidth = size.width / 250, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    laser?.let { l -> val p = androidx.compose.ui.geometry.Offset(l.x * size.width, l.y * size.height)
                        drawCircle(Color(0x55FF1744), size.width / 60, p); drawCircle(Color(0xFFFF1744), size.width / 140, p) }
                }
            }
            } }
            val presenter: @Composable (Modifier) -> Unit = { m -> PresenterNotes(s.notes, page, paused, m) {
                toolButtons()
                IconButton(onClick = { paused = !paused; WriterLog.i("Slides: paused $paused") }) {
                    Icon(painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause), contentDescription = if (paused) "Go on" else "Pause", tint = if (paused) POWERPOINT_ORANGE else Color.White.copy(alpha = 0.85f))
                }
                Text(when { black -> "Second screen black"; paused -> "Paused"; else -> "On the second screen" }, color = if (black || paused) POWERPOINT_ORANGE else Color(0xFF6FCF97),
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                Text("${page + 1} / ${shown.size}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            } }
            // with a second screen: held upright (a phone), the slide across the top and the notes under it; sideways, the slide small
            // beside them (half the width while pointing or drawing); clear of a camera's cutout
            val around = if (tv != null) Modifier.fillMaxSize().displayCutoutPadding() else Modifier.fillMaxSize()
            if (upright) Column(around) { slideArea(Modifier.fillMaxWidth().aspectRatio(deck.width / deck.height)); presenter(Modifier.weight(1f).fillMaxWidth()) }
            else Row(around, verticalAlignment = Alignment.CenterVertically) {
                slideArea(Modifier.weight(1f).fillMaxHeight())
                if (tv != null) presenter(Modifier.weight(if (tool != null) 1f else 1.4f).fillMaxHeight())
            }
            if (notes && tv == null && s.notes.isNotEmpty()) Text(s.notes, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(8.dp))
            // the TV shows what the phone does, frame by frame: the slide (black when the phone's is), its effects, ink and laser
            LaunchedEffect(tv) { tv?.render = { c, w, h -> if (!black) render(c, w, h) } }
            // the TV drawn again every frame (transitions and effects move there too), with the pen's ink and the laser
            LaunchedEffect(tv) { if (tv != null) while (true) androidx.compose.runtime.withFrameNanos { tv.apply { strokes = if (black) emptyList() else ink[page].orEmpty(); dot = laser.takeIf { !black }; update() } } }
            // black on one screen: the slide covered, taps passing to it (three bring it back); with a second screen only that one goes black
            if (black && tv == null) Box(Modifier.fillMaxSize().background(Color.Black))
            // the dock at the left edge: the tools and the slide count, brought out by two taps; kept from Android's Back swipe there (asked 17 Oct: it conflicted)
            if (tv == null) Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.animation.AnimatedVisibility(dock, enter = androidx.compose.animation.slideInHorizontally { -it }, exit = androidx.compose.animation.slideOutHorizontally { -it }) {
                    Column(Modifier.systemGestureExclusion().padding(start = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6202020)).padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${page + 1} / ${shown.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                        toolButtons()
                    }
                }
            }
        }
    }
}

/**
 * The presenter's notes beside the slide while a second screen shows it (asked 17 Oct): Full — all of them, to read and
 * scroll; Teleprompter — large, rolling up by themselves at the chosen speed, from the top again on each slide.
 */
/** Insert › Header & footer (PowerPoint's): the date (updated or fixed words), the slide's number, a footer; Apply to the slide or to all. */
@Composable
private fun FootersDialog(start: SlidesEdit.Footers, dismiss: () -> Unit, apply: (SlidesEdit.Footers, Boolean, Boolean) -> Unit) {
    var date by remember { mutableStateOf(start.date) }
    var fixed by remember { mutableStateOf(start.fixed != null) }
    var fixedText by remember { mutableStateOf(start.fixed ?: SlidesEdit.today()) }
    var number by remember { mutableStateOf(start.number) }
    var footer by remember { mutableStateOf(start.text != null) }
    var footerText by remember { mutableStateOf(start.text.orEmpty()) }
    var notOnTitle by remember { mutableStateOf(true) }
    fun made() = SlidesEdit.Footers(date, fixedText.takeIf { fixed }, number, footerText.takeIf { footer })
    @Composable fun check(label: String, on: Boolean, change: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { change(!on) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(on, change); Text(label) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Header & footer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                check("Date and time", date) { date = it }
                if (date) Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Update automatically", !fixed) { fixed = false }; Choice("Fixed", fixed) { fixed = true } }
                if (date && fixed) androidx.compose.material3.OutlinedTextField(fixedText, { fixedText = it }, singleLine = true, modifier = Modifier.padding(start = 40.dp), label = { Text("Date") })
                check("Slide number", number) { number = it }
                check("Footer", footer) { footer = it }
                if (footer) androidx.compose.material3.OutlinedTextField(footerText, { footerText = it }, singleLine = true, modifier = Modifier.padding(start = 40.dp), label = { Text("Footer") })
                check("Don't show on title slide", notOnTitle) { notOnTitle = it }
            }
        },
        confirmButton = { Row {
            androidx.compose.material3.TextButton(onClick = { apply(made(), false, notOnTitle) }) { Text("Apply") }
            androidx.compose.material3.TextButton(onClick = { apply(made(), true, notOnTitle) }) { Text("Apply to all") } } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PresenterNotes(notes: String, page: Int, paused: Boolean, modifier: Modifier, header: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val context = LocalContext.current
    var prompter by remember { mutableStateOf(false) }
    var rolling by remember { mutableStateOf(true) }
    // the teleprompter's speed (dp a second) and words' size (sp) kept for the next presentation; 13 the user's pick (17 Oct)
    val prefs = remember { context.getSharedPreferences("slides", android.content.Context.MODE_PRIVATE) }
    var speed by remember { mutableStateOf(prefs.getFloat("prompterSpeed", 13f)) }
    var size by remember { mutableStateOf(prefs.getInt("prompterSize", 28)) }
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(page) { scroll.scrollTo(0) }
    LaunchedEffect(prompter, rolling, speed, page, paused) {
        if (!prompter || !rolling || paused) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) { val t = androidx.compose.runtime.withFrameNanos { it }; scroll.scrollBy(with(density) { speed.dp.toPx() } * (t - last) / 1e9f); last = t }
    }
    val chip = androidx.compose.material3.FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = POWERPOINT_ORANGE, selectedLabelColor = Color.White)
    Column(modifier.padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 12.dp)) {
        // the presenting tools, Pause, where the slide is shown and the slide count
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = header)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(!prompter, { prompter = false }, { Text("Full") }, colors = chip)
            androidx.compose.material3.FilterChip(prompter, { prompter = true; WriterLog.i("Slides: teleprompter") }, { Text("Teleprompter") }, colors = chip)
        }
        // the teleprompter's own on a row of their own, so they fit a phone's width
        if (prompter) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { rolling = !rolling }) { Icon(painterResource(if (rolling) R.drawable.ic_pause else R.drawable.ic_play), contentDescription = if (rolling) "Pause" else "Roll", tint = Color.White) }
                Text("Speed", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(speed, { speed = it }, Modifier.weight(1f), valueRange = 5f..120f,
                    onValueChangeFinished = { prefs.edit().putFloat("prompterSpeed", speed).apply() })
                fun resize(by: Int) { size = (size + by).coerceIn(16, 72); prefs.edit().putInt("prompterSize", size).apply() }
                IconButton(onClick = { resize(-4) }, enabled = size > 16) { Icon(painterResource(R.drawable.ic_remove), contentDescription = "Smaller text", tint = Color.White) }
                Text("${size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { resize(4) }, enabled = size < 72) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Larger text", tint = Color.White) }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1C))) {
            val box = maxHeight
            // the teleprompter's words start a third of the way down and roll on until the last line has gone off the top
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
                if (prompter) androidx.compose.foundation.layout.Spacer(Modifier.height(box / 3))
                Text(notes.ifEmpty { "No notes on this slide." }, color = if (notes.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White,
                    style = if (prompter) MaterialTheme.typography.headlineMedium.copy(fontSize = size.sp, lineHeight = (size * 1.3f).sp) else MaterialTheme.typography.bodyLarge)
                if (prompter) androidx.compose.foundation.layout.Spacer(Modifier.height(box))
            }
        }
    }
}

/** The slides on a second screen (a TV by HDMI or a cast): drawn as the phone shows them, frame by frame, with the pen's ink and the laser. */
private class TvSlides(context: android.content.Context, display: android.view.Display, val deck: Deck) : android.app.Presentation(context, display) {
    var render: ((android.graphics.Canvas, Float, Float) -> Unit)? = null
    var strokes: List<List<androidx.compose.ui.geometry.Offset>> = emptyList()
    var dot: androidx.compose.ui.geometry.Offset? = null
    private val view = object : android.view.View(context) {
        val red = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt(); strokeCap = android.graphics.Paint.Cap.ROUND }
        override fun onDraw(c: android.graphics.Canvas) {
            c.drawColor(android.graphics.Color.BLACK)
            render?.invoke(c, width.toFloat(), height.toFloat())
            val k = minOf(width / deck.width, height / deck.height)
            val w = deck.width * k; val h = deck.height * k
            c.save(); c.translate((width - w) / 2, (height - h) / 2)
            red.strokeWidth = w / 250; red.alpha = 255
            for (st in strokes) for (i in 1 until st.size) c.drawLine(st[i - 1].x * w, st[i - 1].y * h, st[i].x * w, st[i].y * h, red)
            dot?.let { d -> red.alpha = 0x55; c.drawCircle(d.x * w, d.y * h, w / 60, red); red.alpha = 255; c.drawCircle(d.x * w, d.y * h, w / 140, red) }
            c.restore()
        }
    }
    override fun onCreate(savedInstanceState: android.os.Bundle?) { super.onCreate(savedInstanceState); setContentView(view) }
    fun update() = view.invalidate()
}

/** Design › Themes (asked 16 Oct): Tetra's light and dark ones and the templates in Documents/Templates, each a small slide in its colours and fonts. */
@Composable
private fun ThemesDialog(now: SlideTheme?, dismiss: () -> Unit, pick: (SlideTheme) -> Unit) {
    val templates by androidx.compose.runtime.produceState(emptyList<SlideTheme>()) {
        value = withContext(Dispatchers.IO) { SlidesTemplates.files().mapNotNull { SlidesThemes.fromFile(it) } }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 720.dp).then(Modifier.padding(12.dp)), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Themes", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    @Composable fun group(title: String, list: List<SlideTheme>) {
                        if (list.isEmpty()) return
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                            list.forEach { t -> ThemeCard(t, t == now, Modifier.weight(1f)) { pick(t) } }
                            if (list.size % 2 == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) // a last one alone keeps its size
                        }
                    }
                    group("Light", SlidesThemes.BUILT_IN.filter { !it.dark })
                    group("Dark", SlidesThemes.BUILT_IN.filter { it.dark })
                    group("Your templates", templates)
                    if (templates.isEmpty()) Text("Templates (.potx or .pptx) in Documents/Templates show here — their colours and fonts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { androidx.compose.material3.TextButton(onClick = dismiss) { Text("Close") } }
            }
        }
    }
}

/** A theme as a small slide: its background, its band, a title and words in its fonts and colours, its six accents. */
@Composable
internal fun ThemeCard(t: SlideTheme, chosen: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    fun c(i: Int) = Color(0xFF000000.toInt() or t.colors[i])
    Column(modifier.clip(RoundedCornerShape(10.dp)).border(if (chosen) 3.dp else 1.dp, if (chosen) POWERPOINT_ORANGE else Color(0x55888888), RoundedCornerShape(10.dp)).clickable(onClick = click)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c(1))) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val a = c(4)
                when (t.band) {
                    'l' -> drawRect(a, size = androidx.compose.ui.geometry.Size(size.width * 0.03f, size.height))
                    'r' -> drawRect(a, androidx.compose.ui.geometry.Offset(size.width * 0.94f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.06f, size.height))
                    't' -> drawRect(a, size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.03f))
                    'b' -> drawRect(a, androidx.compose.ui.geometry.Offset(0f, size.height * 0.94f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.06f))
                }
                drawIntoCanvas { cv ->
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = (0xFF000000.toInt() or t.colors[0]); textSize = size.height * 0.2f
                        typeface = OfficeFonts.typeface(t.major) ?: android.graphics.Typeface.DEFAULT }
                    cv.nativeCanvas.drawText(t.name, size.width * 0.08f, size.height * 0.42f, p)
                    p.textSize = size.height * 0.11f; p.typeface = OfficeFonts.typeface(t.minor) ?: android.graphics.Typeface.DEFAULT; p.color = 0xFF000000.toInt() or t.colors[2]
                    cv.nativeCanvas.drawText("${t.major} · ${t.minor}", size.width * 0.08f, size.height * 0.62f, p)
                }
                // its accents
                for (i in 0 until 6) drawCircle(c(4 + i), size.height * 0.05f, androidx.compose.ui.geometry.Offset(size.width * (0.1f + i * 0.08f), size.height * 0.8f))
            }
        }
    }
}

/** Changes not yet saved, kept when the app went to the background: one per presentation (by its path), in the app's storage. */
private fun slideDraft(context: android.content.Context, file: File) =
    File(File(context.filesDir, "slide-drafts"), "${file.absolutePath.hashCode().toUInt()}.${file.extension}")
