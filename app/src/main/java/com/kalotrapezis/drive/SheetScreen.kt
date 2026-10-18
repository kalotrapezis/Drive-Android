package com.kalotrapezis.drive

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.togetherWith
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormatSymbols
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Excel's viewer (the user, 15 Oct: "start with the viewer and let's complete it like we did with Word"): a sheet as
 * the file draws it — its widths, heights, fills, borders, fonts, merged cells, frozen rows and columns — on white
 * paper with Excel's letters and numbers round it; pinch to zoom, fling to scroll, a tap shows the cell (its formula,
 * all its words) in the bar above; the sheets as tabs below. And the viewer's own tools (asked 30 Sep): search that
 * forgives typos and accents, a column's filter and sort from a tap on its letter, columns pinned side by side.
 */
@Composable
internal fun SheetScreen(back: () -> Unit, open: String) {
    val context = LocalContext.current
    // the file (null: a new spreadsheet, named on its first Save)
    var file by remember(open) { mutableStateOf(if (open == NEW_SHEET) null else Uri.parse(open).path?.let(::File)) }
    var book by remember { mutableStateOf<Workbook?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(-1) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val views = remember { HashMap<Int, SheetView>() }
    remember { OfficeFonts.assets = context.applicationContext.assets }

    // Search: the words, the cells found (in the order drawn), the one shown.
    var searching by remember { mutableStateOf(false) }
    var officeSettings by remember { mutableStateOf(false) } // File › Settings: the ribbon's place and size
    var cellMenu by remember { mutableStateOf<Offset?>(null) } // a right-click's menu, where it was clicked
    // Search in every sheet (asked for the user's tables: a sheet for each specialty): the finds, how far it has got.
    var everywhere by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<SheetHit>>(emptyList()) }
    var searched by remember { mutableIntStateOf(0) }
    var showHits by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Long?>(null) } // a find chosen in the list: shown once its sheet is searched
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Long>>(emptyList()) }
    var current by remember { mutableIntStateOf(0) }
    // A column's letter ('c', its sheet column) or a row's number ('r', the row as drawn) tapped: the ribbon shows its tools
    var header by remember { mutableStateOf<Pair<Char, Int>?>(null) }
    var group by remember { mutableStateOf<String?>(null) } // the ribbon's group opened beside its button (Borders, Number, Font)
    var tab by remember { mutableStateOf("Home") } // the ribbon's tab
    var filtering by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var formulas by remember { mutableStateOf(false) } // ⋯ › Show formulas: the cells show their formulas
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    var about by remember { mutableStateOf(false) }
    var copiedLook by remember { mutableStateOf<SheetStyle?>(null) } // Copy formatting (Words' pair): the look Paste formatting puts on
    var symbols by remember { mutableStateOf(false) } // Insert › Symbol: Words' dialog
    var symbol by remember { mutableStateOf<String?>(null) } // the one chosen, put in once the dialog has gone
    var inserting by remember { mutableStateOf(false) } // Formulas › Insert function
    var evaluating by remember { mutableStateOf<Pair<String, List<String>>?>(null) } // Formulas › Evaluate formula: the cell and its steps
    var inserted by remember { mutableStateOf<String?>(null) } // the function chosen, put in once the dialog has gone
    var charting by remember { mutableStateOf<Pair<SheetRange, DocChart>?>(null) }
    var linking by remember { mutableStateOf(false) } // Insert › Link: Words' dialog on the chosen cell
    var noting by remember { mutableStateOf(false) } // Insert › Note: Words' comment dialog on the chosen cell
    var splitting by remember { mutableStateOf<SheetRange?>(null) } // Data › Text to columns: the cells (one column, the sheet's)
    var ruling by remember { mutableStateOf<Pair<String, SheetRange>?>(null) } // Home › Conditional formatting: a highlight rule's kind and the cells (the sheet's)
    var equating by remember { mutableStateOf(false) } // Insert › Equation: Words' editor
    var artStrip by remember { mutableStateOf<Pair<ArtStyle, ArtBend>?>(null) } // Insert › WordArt: Words' strip, the look and bend tapped
    var artWords by remember { mutableStateOf<Pair<ArtStyle, ArtBend>?>(null) } // then its words asked
    var protectingBook by remember { mutableStateOf(false) } // Review › Protect / Unprotect workbook: its password asked
    var protecting by remember { mutableStateOf(false) } // the sheet tab › Protect / Unprotect: its password asked
    var footing by remember { mutableStateOf<String?>(null) } // Page layout › Header or Footer › Custom…: "header" or "footer"
    var boxing by remember { mutableStateOf(false) } // Insert › Text box: its words asked
    var sortingRows by remember { mutableStateOf<SheetRange?>(null) } // Data › Sort: the cells (the sheet's), their levels asked
    var namesFor by remember { mutableStateOf<SheetRange?>(null) } // Formulas › Names: the cells a new name would stand for
    var allNotes by remember { mutableStateOf(false) } // Review › Show all notes
    var breaks by remember { mutableStateOf(false) } // View › Page break preview
    var traced by remember { mutableStateOf<Set<Long>>(emptySet()) } // Formulas › Trace: the cells marked (precedents, dependents, errors)
    var totalling by remember { mutableStateOf<SheetRange?>(null) } // Data › Subtotal: the cells (the sheet's; headings first)
    var shaping by remember { mutableStateOf(false) } // Insert › Shape: Words' shapes dialog
    var figureSizing by remember { mutableStateOf<SheetDrawing?>(null) } // a drawing's Size: its width and height asked
    var sourcing by remember { mutableStateOf(false) } // Insert › Source: Words' source dialog
    var counting by remember { mutableStateOf(false) } // Review › Workbook statistics
    var spellRun by remember { mutableStateOf<SpellRun?>(null) } // Review › Spelling: what is left to look at
    val speller = remember { WriterSpelling(context) }
    // View › Formula bar and Sheet tabs: shown or not (the viewer's, as Excel's View options)
    var showBar by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var showTabs by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) } // Data › Goal seek: its three boxes asked
    var fileNaming by remember { mutableStateOf<String?>(null) } // File › Save as ("as") or Rename ("rename"): the name asked
    var autoSave by remember { mutableStateOf(TextEditorStore(context).autoSave) } // File › AutoSave: Words' own setting
    var listing by remember { mutableStateOf<SheetRange?>(null) } // Data › Drop-down list: the cells (the sheet's)
    var stepping by remember { mutableStateOf<SheetRange?>(null) } // Home › Series: the cells (as drawn)
    var deduping by remember { mutableStateOf<SheetRange?>(null) } // Data › Remove duplicates: the cells (the sheet's), asked about headers first
    var sizing by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // Width (a column, true) or Height (a row): the sheet's index, asked in Words' unit // Insert › Chart: the cells (the sheet's) and Words' chart editor's start
    // Editing (asked 16 Oct): the steps to undo, a count that redraws after each, what the cell bar holds while typing.
    var editing by remember { mutableStateOf(JustMade.take(open)) } // a file just made with New: editing at once
    val history = remember { SheetHistory() }
    var version by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<TextFieldValue?>(null) }
    var saving by remember { mutableStateOf(false) }
    // The working copy after rows or columns were inserted or deleted (null: the file itself, with edits in memory);
    // copies in the app's cache, gone when the screen is.
    var work by remember { mutableStateOf<File?>(null) }
    val workDir = remember { File(context.cacheDir, "sheet-work").apply { deleteRecursively(); mkdirs() } }
    DisposableEffect(Unit) { onDispose { workDir.deleteRecursively() } }
    var leaving by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) } // a new spreadsheet's first Save: its name
    var renamingSheet by remember { mutableStateOf(false) }
    var deletingSheet by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf<SheetPaste.Clip?>(null) } // cells copied or cut here, for Paste
    var fontStrip by remember { mutableStateOf<String?>(null) } // Home › Font's gallery open: the font in use ("" none)
    var fontChosen by remember { mutableStateOf<FontChoice?>(null) } // the one shown on the cells, one step of Undo
    val barFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun leave() { if (history.unsaved != 0) leaving = true else { file?.let { sheetDraft(context, it).delete() }; back() } } // a draft of changes since undone: gone
    BackHandler {
        when {
            draft != null -> { draft = null; focusManager.clearFocus() }
            searching -> { searching = false; query = "" }
            else -> leave()
        }
    }

    // The app sent to the background with changes not saved (Android may close it then): they are kept as a draft.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            val f = file; val b = book
            if (e != androidx.lifecycle.Lifecycle.Event.ON_STOP || f == null || b == null || saving) return@LifecycleEventObserver
            if (index >= 0) views[index]?.let { SheetPlaces.save(context, f, index, it) } // where it was, should Android close it
            val unsaved = history.unsaved != 0; val from = work ?: f
            scope.launch(Dispatchers.IO) { runCatching {
                val d = sheetDraft(context, f)
                if (!unsaved) { d.delete(); return@runCatching }
                d.parentFile?.mkdirs()
                val temp = File(d.parentFile, ".${d.name}.saving")
                if (b.edits.isNotEmpty() || b.kind == "csv") SheetSave.save(b, from, temp) else from.copyTo(temp, overwrite = true)
                check(temp.renameTo(d)) { "Could not keep the draft." }
                WriterLog.i("Sheets: recovery draft kept, ${d.length() / 1024} KB")
            }.onFailure { WriterLog.e("Sheets: recovery draft", it) } }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    /** A workbook ready for edits: a sheet read again has the formulas that read other sheets' edits worked out. */
    fun prepare(b: Workbook) {
        b.laid = { i, s -> SheetRecalc.run(b, i, s, b.edits[i]?.keys.orEmpty()).forEach { (k, c) -> s.put(k, c); b.edits.getOrPut(i) { LinkedHashMap() }[k] = c } }
    }

    LaunchedEffect(open) {
        WriterLog.init(context)
        if (open == NEW_SHEET) {
            // a new spreadsheet: Excel's empty workbook in a working copy, ready to type in
            val f = File(workDir, "New spreadsheet.xlsx").apply { writeBytes(SheetBook.empty()) }
            work = f; editing = true
            val b = withContext(Dispatchers.IO) { DecimalFormatSymbols.getInstance().let { Sheets.read(f, it.decimalSeparator, it.groupingSeparator) } }
            prepare(b); book = b; index = 0
            WriterLog.i("Sheets: a new spreadsheet")
            return@LaunchedEffect
        }
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Sheets: open .${f.extension} of ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) {
            runCatching {
                val sym = DecimalFormatSymbols.getInstance()
                WriterLog.timed("Sheets: workbook read") { Sheets.read(f, sym.decimalSeparator, sym.groupingSeparator) }
            }
        }.onSuccess { b0 ->
            // Changes not saved when the app was closed: the draft opened in their place, one step Undo takes back.
            val kept = sheetDraft(context, f).takeIf { it.isFile }
            val b = kept?.takeIf { it.lastModified() > f.lastModified() }?.let { d -> withContext(Dispatchers.IO) { runCatching {
                val copy = d.copyTo(File(workDir, "${System.nanoTime()}.${f.extension}"))
                DecimalFormatSymbols.getInstance().let { Sheets.read(copy, it.decimalSeparator, it.groupingSeparator) } to copy
            }.onFailure { WriterLog.e("Sheets: recovery draft", it) }.getOrNull() } }?.let { (rb, copy) ->
                work = copy; editing = true
                message = "Your unsaved changes are back. Undo shows the file as saved."
                WriterLog.i("Sheets: recovery draft opened")
                rb
            } ?: b0.also { kept?.delete() } // a draft older than the file (saved since): gone
            prepare(b)
            book = b
            index = if (b.tabs[b.active].hidden) b.tabs.indexOfFirst { !it.hidden }.coerceAtLeast(0) else b.active
            // Where it was left, when it was opened before.
            SheetPlaces.load(context, f)?.let { (i, v) -> if (i in b.tabs.indices && !b.tabs[i].hidden) { index = i; views[i] = v; WriterLog.i("Sheets: back where it was left") } }
            if (b !== b0) history.file(SheetHistory.FileStep(index, f, emptyMap(), work!!, 0L))
        }.onFailure {
            WriterLog.e("Sheets: could not open", it)
            failed = if (it is OutOfMemoryError) "This spreadsheet is too big to open on this device." else "This spreadsheet could not be opened."
        }
    }
    LaunchedEffect(book, index) {
        val b = book ?: return@LaunchedEffect
        if (index < 0) return@LaunchedEffect
        sheet = null
        sheet = withContext(Dispatchers.IO) {
            runCatching { b.sheet(index) }.onFailure { WriterLog.e("Sheets: sheet ${index + 1} could not be read", it) }.getOrNull()
        }
        if (sheet == null) failed = "This sheet could not be read."
    }

    val view = views.getOrPut(index) { SheetView() }
    // the file's AutoFilter, its values the view's filter (once, when the sheet first shows)
    LaunchedEffect(sheet) { val s = sheet ?: return@LaunchedEffect
        if (!view.fileFilter) { view.fileFilter = true; if (view.filters.isEmpty()) view.filters.putAll(s.filterKeep) } }
    DisposableEffect(Unit) { onDispose { file?.let { f -> if (index >= 0) views[index]?.let { SheetPlaces.save(context, f, index, it) } } } }

    /** A cell's link: a web or mail address opens in its app; "#Sheet!B3" goes to that sheet's cell. */
    fun follow(link: String) {
        val b = book ?: return
        if (link.startsWith("#")) {
            val target = link.removePrefix("#")
            val name = target.substringBeforeLast('!', "").trim('\'')
            val i = if (name.isEmpty()) index else b.tabs.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            WriterLog.i("Sheets: link inside the workbook, ${if (i < 0) "no such sheet" else "sheet ${i + 1}"}")
            if (i < 0) return
            val v = views.getOrPut(i) { SheetView() }
            Sheets.cellRef(target.substringAfterLast('!').substringBefore(':'))?.let { (r, c) -> v.row = r; v.col = c; v.reveal++ }
            index = i
        } else {
            WriterLog.i("Sheets: link out")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { WriterLog.w("Sheets: no app for the link", it) }
        }
    }

    /** A cell's look and number format, for what is typed into it (a new cell takes its row's or column's look). */
    fun lookAt(s: Sheet, r: Int, c: Int): Pair<Int, String> {
        val cell = s[r, c]
        val i = cell?.style ?: s.rowStyles[r] ?: s.colStyles[c] ?: 0
        return i to (cell?.format ?: s.look(i, book?.styles.orEmpty())?.format ?: "General")
    }

    /** The chosen cell (a merged range's first). */
    fun chosen(s: Sheet) = s.merge(view.row, view.col)?.let { it.r1 to it.c1 } ?: (view.row to view.col)

    /** Starts typing in the chosen cell: the bar holds what it shows for it, the cursor at the end. */
    fun startTyping() {
        val s = sheet ?: return; val b = book ?: return
        val (r, c) = chosen(s)
        if (history.locked(b, s, Sheet.key(r, c))) { message = PROTECTED; focusManager.clearFocus(); WriterLog.i("Sheets: a protected cell not typed in"); return }
        val t = SheetInput.text(s[r, c], lookAt(s, r, c).second, b.format)
        draft = TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)); view.reveal++
    }

    // The order rows and columns are drawn in: worked out away from the screen (a sort of 300,000 rows takes a moment).
    // Kept with the sheet it was made for, so a new sheet is never drawn in the last one's order.
    var laid by remember { mutableStateOf<Pair<Sheet, SheetLayout>?>(null) }
    val layout = laid?.takeIf { it.first === sheet }?.second
    val pins = view.pins.toList(); val filters = view.filters.toMap(); val sort = view.sort; val freeze = view.freeze
    val extent = sheet?.let { it.rows.toLong() * 100_000 + it.cols } ?: 0L // an edit past the used cells lays the sheet out again
    var reapplied by remember { mutableIntStateOf(0) } // Data › Reapply: the filter and sort worked out again on the cells as they are now
    LaunchedEffect(sheet, pins, filters, sort, freeze, extent, reapplied) {
        val s = sheet ?: return@LaunchedEffect
        laid = s to withContext(Dispatchers.Default) { WriterLog.timed("Sheets: layout (${pins.size} pinned, ${filters.size} filtered, sorted ${sort != null}, frozen ${freeze ?: "as the file"})") { SheetLayout.of(s, pins, filters, sort, freeze) } }
        view.range = null
    }
    /** Find and Replace: in [keys]' cells (their words, or their formulas), the words looked for made [with] — one step of Undo. */
    fun replaceFound(keys: List<Long>, with: String) {
        val b = book ?: return; val s = sheet ?: return
        val changes = keys.mapNotNull { k -> val r = Sheet.row(k); val c = Sheet.col(k); val (look, format) = lookAt(s, r, c)
            SheetInput.replaced(SheetInput.text(s[r, c], format, b.format), query, with)?.let { k to SheetInput.cell(it, s[r, c], look, format, b.format) } }.toMap()
        if (changes.isEmpty()) { message = "Nothing here to replace"; return }
        history.apply(b, index, changes); version++
        message = "Replaced ${changes.size}"; WriterLog.i("Sheets: replaced in ${changes.size} cells")
    }
    /** What was typed goes into the chosen cell (and, [move]: the cell below is chosen, as Excel's Enter). */
    fun commit(move: Boolean) {
        val d = draft ?: return; val b = book ?: return; val s = sheet ?: return
        val (r, c) = chosen(s)
        val (look, format) = lookAt(s, r, c)
        if (d.text != SheetInput.text(s[r, c], format, b.format)) {
            // a drop-down list's cell takes only its values (Excel's stop)
            val list = s.listAt(r, c)?.items.orEmpty()
            if (d.text.isNotEmpty() && list.isNotEmpty() && list.none { it.equals(d.text.trim(), true) }) {
                message = "This cell takes one of its list's values: ${list.joinToString(", ")}"; draft = null; WriterLog.i("Sheets: a value not in the list refused"); return }
            history.apply(b, index, mapOf(Sheet.key(r, c) to SheetInput.cell(d.text, s[r, c], look, format, b.format)))
            version++
        }
        draft = null
        if (move) {
            val below = s.merge(r, c)?.r2 ?: r
            view.row = layout?.let { l -> l.row(below).takeIf { it >= 0 && it + 1 < l.rows.size }?.let { l.rows[it + 1] } } ?: (below + 1)
            view.col = c; view.reveal++
        }
    }

    /** A working copy read as the workbook, with [edits] not yet in it (an undone file step's). */
    suspend fun reread(f: File, edits: Map<Int, LinkedHashMap<Long, SheetCell>>) {
        val nb = withContext(Dispatchers.IO) { DecimalFormatSymbols.getInstance().let { Sheets.read(f, it.decimalSeparator, it.groupingSeparator) } }
        edits.forEach { (i, m) -> nb.edits[i] = LinkedHashMap(m) }
        prepare(nb); book = nb; version++
    }

    /**
     * A change to the whole file ([op] from the copy with the edits in it to a new copy — rows, columns, sheets): the
     * edits written into a copy, [op] done, the new copy read again; one step to undo; [then] after, with it.
     */
    fun fileStep(what: String, op: (baked: File, next: File, b: Workbook) -> Unit, unprotecting: Boolean = false, structure: Boolean = false, then: (Workbook) -> Unit = {}) {
        val b = book ?: return; val i = index
        // one at a time: a second started before the first is done would start from the file without it
        if (saving) { message = "One moment — the last change is still being made"; return }
        if (b.sheet(i).protected && !unprotecting) { message = PROTECTED; return }
        // Review › Protect workbook: no sheet added, deleted, renamed, moved or hidden
        if (structure && b.structureLocked) { message = "This workbook's structure is protected: Review › Unprotect workbook first"; return }
        val from = work ?: file ?: return
        commit(false)
        saving = true
        scope.launch {
            val ext = from.extension.lowercase()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // never the file itself: a save changes it, and this step's Undo goes back to what it was
                    val src = if (from == file) File(workDir, "${System.nanoTime()}.opened.$ext").also { from.copyTo(it, overwrite = true) } else from
                    val baked = File(workDir, "${System.nanoTime()}.edits.$ext")
                    val next = File(workDir, "${System.nanoTime()}.$ext")
                    try {
                        if (b.edits.isNotEmpty() || b.kind == "csv") SheetSave.save(b, src, baked) else src.copyTo(baked, overwrite = true)
                        WriterLog.timed("Sheets: $what") { op(baked, next, b) }
                    } finally { baked.delete() }
                    src to next
                }
            }
            saving = false
            result.onSuccess { (src, next) ->
                history.file(SheetHistory.FileStep(i, src, b.edits.mapValues { LinkedHashMap(it.value) }, next, Sheet.key(view.row, view.col)))
                work = next
                reread(next, emptyMap())
                view.range = null
                book?.let(then)
            }.onFailure { WriterLog.e("Sheets: $what could not be done", it); message = it.message ?: "Could not do that." }
        }
    }

    /** Rows ([rows]) or columns inserted ([count] > 0) or deleted at [at] of this sheet, every reference following. */
    fun shift(rows: Boolean, at: Int, count: Int) {
        if (view.filters.isNotEmpty() || view.sort != null) { message = "Clear the filter and the sort first: rows are moved as the file has them."; return }
        val i = index
        fileStep("${if (count > 0) "inserted" else "deleted"} ${kotlin.math.abs(count)} ${if (rows) "rows" else "columns"}", { baked, next, b ->
            val sh = SheetShift(b.tabs[i].name, rows, at, count)
            when (b.kind) {
                "xlsx" -> sh.xlsx(baked, next, i, b.parts, b.tabs.map { it.name })
                "ods" -> sh.ods(baked, next, i, b.tabs.map { it.name })
                else -> {
                    val old = DecimalFormatSymbols.getInstance().let { Sheets.read(baked, it.decimalSeparator, it.groupingSeparator) }.sheet(0)
                    val moved = Sheet(old.name, sh.cells(old), maxOf(0, old.rows + if (rows) count else 0), maxOf(0, old.cols + if (rows) 0 else count), emptyMap(), emptyMap(), 64f, 20f, emptySet(), emptySet(), emptyList(), 0, 0)
                    SheetSave.save(Workbook(b.tabs, b.styles, kind = "csv", format = b.format, csv = b.csv) { moved }, baked, next)
                }
            }
        })
    }

    /** A column's width or a row's height ([px] sheet pixels; null: as it is) and hiding it ([hidden]): [cols] or rows [from]..[to], the sheet's. */
    fun resize(cols: Boolean, from: Int, to: Int, px: Float?, hidden: Boolean?) {
        val i = index
        fileStep(listOfNotNull(px?.let { "sized" }, hidden?.let { if (it) "hidden" else "shown" }).joinToString(" and ") + " ${to - from + 1} ${if (cols) "columns" else "rows"}", { baked, next, b ->
            when (b.kind) { "xlsx" -> SheetSizes.xlsx(baked, next, b.parts.getValue("$i"), cols, from, to, px, hidden); "ods" -> SheetSizes.ods(baked, next, i, cols, from, to, px, hidden)
                else -> error("A CSV file keeps no widths: save it as a workbook first.") } })
    }
    /** AutoFit (Excel's): column [c] as wide as its longest words, or row [r] as tall as its biggest letters (sheet pixels). */
    fun fitted(s: Sheet, b: Workbook, cols: Boolean, at: Int): Float {
        val paint = android.text.TextPaint()
        var most = 0f
        if (cols) for (r in 0 until minOf(s.rows, 5000)) { val cell = s[r, at] ?: continue; val st = s.look(cell.style, b.styles) ?: SheetStyle()
            paint.textSize = st.size * 4 / 3; paint.isFakeBoldText = st.bold; most = maxOf(most, cell.text.split('\n').maxOf { paint.measureText(it) }) }
        else for (c in 0 until minOf(s.cols, 1000)) { val cell = s[at, c] ?: continue; val st = s.look(cell.style, b.styles) ?: SheetStyle()
            most = maxOf(most, st.size * 4 / 3 * 1.3f * (cell.text.count { it == '\n' } + 1)) }
        return if (cols) maxOf(most + 10f, 20f) else maxOf(most + 4f, 15f)
    }

    /** Home › Merge & centre (Excel's): [g] (the sheet's rows and columns) one cell, its words centred; or ([on] false) its cells again. */
    fun merge(g: SheetRange, on: Boolean) {
        if (view.filters.isNotEmpty() || view.sort != null) { message = "Clear the filter and the sort first: cells are merged as the file has them."; return }
        val i = index
        fileStep(if (on) "merged ${g.r2 - g.r1 + 1}×${g.c2 - g.c1 + 1}" else "unmerged", { baked, next, b ->
            when (b.kind) { "xlsx" -> SheetMerge.xlsx(baked, next, b.parts.getValue("$i"), g, on); "ods" -> SheetMerge.ods(baked, next, i, g, on); else -> error("A CSV file has no merged cells.") }
        }, then = { b -> if (on) { val s = b.sheet(i)
            SheetLook.change(b, s, listOf(Sheet.key(g.r1, g.c1))) { st, _ -> st.copy(align = SheetAlign.Center) }.takeIf { it.isNotEmpty() }?.let { history.apply(b, i, it); version++ } } })
    }

    /**
     * The fill handle let go: [to] (as drawn, [src] in it) filled from [src] — each column carried down or up, or each row
     * right or left — one step of Undo.
     */
    LaunchedEffect(history) { history.refused = { message = PROTECTED } }
    fun fillCells(src: SheetRange, to: SheetRange) {
        val s = sheet ?: return; val b = book ?: return; val l = layout ?: return
        val vertical = to.r1 != src.r1 || to.r2 != src.r2
        val forward = if (vertical) to.r2 > src.r2 else to.c2 > src.c2
        // the source line and the new cells, as the sheet has them, in the fill's direction
        val from = (if (vertical) src.r1..src.r2 else src.c1..src.c2).let { if (forward) it.toList() else it.reversed().toList() }
        val fresh = if (vertical) (if (forward) (src.r2 + 1..to.r2).toList() else (to.r1 until src.r1).reversed().toList()) else (if (forward) (src.c2 + 1..to.c2).toList() else (to.c1 until src.c1).reversed().toList())
        fun sheetIndex(i: Int) = if (vertical) l.rows[i] else l.cols[i]
        fun codeOf(cell: SheetCell) = cell.format ?: s.look(cell.style, b.styles)?.format ?: "General"
        val changes = LinkedHashMap<Long, SheetCell>()
        for (across in if (vertical) src.c1..src.c2 else src.r1..src.r2) {
            fun key(along: Int) = if (vertical) Sheet.key(l.rows[along], l.cols[across]) else Sheet.key(l.rows[across], l.cols[along])
            val made = SheetFill.series(from.map { s.cells[key(it)] }, fresh.size, b.format, ::codeOf) { fm, j, i ->
                val d = sheetIndex(fresh[i]) - sheetIndex(from[j])
                SheetFill.moved(fm, if (vertical) d else 0, if (vertical) 0 else d)
            }
            fresh.forEachIndexed { i, along -> changes[key(along)] = made[i] }
        }
        if (changes.isEmpty()) return
        history.apply(b, index, changes); version++
        WriterLog.i("Sheets: filled ${changes.size} cells ${if (vertical) (if (forward) "down" else "up") else (if (forward) "right" else "left")}")
    }

    /** Excel's Fill ▸ Series on cells [g] (as drawn): down its columns (or along its row), from each first cell's number or date. */
    fun fillSeries(g: SheetRange, type: String, step: Double) {
        val s = sheet ?: return; val b = book ?: return; val l = layout ?: return
        val vertical = g.r2 > g.r1
        val changes = LinkedHashMap<Long, SheetCell>()
        for (across in if (vertical) g.c1..g.c2 else g.r1..g.r2) {
            fun key(along: Int) = if (vertical) Sheet.key(l.rows[along], l.cols[across]) else Sheet.key(l.rows[across], l.cols[along])
            val along = if (vertical) g.r1..g.r2 else g.c1..g.c2
            val first = s.cells[key(along.first)]?.takeIf { it.formula == null && it.kind == SheetKind.Number && it.value != null } ?: continue
            val code = first.format ?: s.look(first.style, b.styles)?.format ?: "General"
            SheetFill.stepped(first.value!!, along.count(), step, type).drop(1).forEachIndexed { i, v ->
                changes[key(along.first + 1 + i)] = b.format.format(v, code).let { first.copy(value = v, text = it.text, color = it.color, note = null, link = null) } }
        }
        if (changes.isEmpty()) { message = "Start the series with a number or a date in the first cell"; return }
        history.apply(b, index, changes); version++
        WriterLog.i("Sheets: series ($type, step $step) in ${changes.size} cells")
    }

    /**
     * Home › Format as table on cells [g] (the sheet's; its first row the headings) in Excel's [style]: Excel's table (the
     * headings made its column names first, as Excel makes them), or for LibreOffice the table's looks put on the cells.
     */
    fun formatAsTable(g: SheetRange, style: String) {
        val s = sheet ?: return; val b = book ?: return
        if (g.r2 <= g.r1) { message = "Choose the cells with their headings first (a long press, then drag)"; return }
        if ((g.r1..minOf(g.r2, g.r1 + 2000)).any { r -> (g.c1..g.c2).any { c -> s.merge(r, c) != null } }) { message = "A table can't take merged cells: unmerge them first"; return }
        if (b.kind == "xlsx") {
            val names = SheetTables.names((g.c1..g.c2).map { c -> s[g.r1, c]?.text.orEmpty() })
            val headings = (g.c1..g.c2).mapIndexedNotNull { i, c -> val was = s[g.r1, c]
                if (was != null && was.kind == SheetKind.Text && was.formula == null && was.text == names[i]) null
                else Sheet.key(g.r1, c) to (was ?: SheetCell("", SheetKind.Empty)).copy(text = names[i], kind = SheetKind.Text, value = null, formula = null) }.toMap()
            if (headings.isNotEmpty()) { history.apply(b, index, headings); version++ }
            val i = index
            fileStep("table made", { baked, next, bb -> SheetTables.xlsx(baked, next, bb.parts.getValue("$i"), g, names, style) })
        } else {
            val laid = SheetTables.builtIn(style, SheetTables.OFFICE)?.let { p -> SheetTables.lay(p, g, 1, 0) { it == "showRowStripes" } }?.toMap() ?: return
            val changes = SheetLook.change(b, s, laid.keys) { st, k -> SheetTables.over(st, laid.getValue(k)) }
            if (changes.isNotEmpty()) { history.apply(b, index, changes); version++ }
        }
        WriterLog.i("Sheets: formatted as table ($style)")
    }

    /** The chosen cells as drawn: the range, else the chosen cell. */
    fun chosenDrawn(l: SheetLayout): SheetRange? = view.range ?: run {
        val r = l.row(view.row); val c = l.col(view.col)
        if (r < 0 || c < 0) null else SheetRange(r, c, r, c)
    }

    /** Copy or Cut: the chosen cells kept for Paste here, and their text on the phone's clipboard for other apps. */
    fun copyCells(cut: Boolean) {
        val s = sheet ?: return; val l = layout ?: return; val g = chosenDrawn(l) ?: return
        val rows = (g.r1..minOf(g.r2, l.rows.lastIndex)).take(5000).map { r -> (g.c1..minOf(g.c2, l.cols.lastIndex)).map { c -> Triple(l.rows[r], l.cols[c], s.cells[Sheet.key(l.rows[r], l.cols[c])]) } }
        val text = sheetText(s, l, g)
        copy(context, "Cells", text)
        clip = SheetPaste.Clip(index, rows, text, cut)
        message = if (cut) "Cut: choose where they go, then Paste" else "Copied"
        WriterLog.i("Sheets: ${rows.sumOf { it.size }} cells ${if (cut) "cut" else "copied"}")
    }

    /**
     * Paste at the chosen cell (the chosen cells' first): cells copied here with their formulas moved as Excel moves them
     * (one cell onto many chosen: each one gets it), else the clipboard's text typed in as a table. Cut cells leave their
     * place empty. One step of Undo.
     */
    // shortcut: cut cells pasted on another sheet are copied (their place stays); move them across sheets if asked
    fun paste() {
        val s = sheet ?: return; val b = book ?: return; val l = layout ?: return; val g = chosenDrawn(l) ?: return
        val text = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        val c = clip?.takeIf { text == null || it.text == text }
        val changes = LinkedHashMap<Long, SheetCell>()
        fun target(i: Int, j: Int): Pair<Int, Int>? {
            val r = g.r1 + i; val k = g.c1 + j
            return if (r > l.rows.lastIndex || k > l.cols.lastIndex) null else l.rows[r] to l.cols[k]
        }
        if (c != null && c.cells.isNotEmpty()) {
            val h = c.cells.size; val w = c.cells[0].size
            val one = h == 1 && w == 1 && view.range != null
            for (i in 0 until if (one) g.r2 - g.r1 + 1 else h) for (j in 0 until if (one) g.c2 - g.c1 + 1 else w) {
                val (tr, tc) = target(i, j) ?: continue
                val (sr, sc, cell) = c.cells[i % h][j % w]
                changes[Sheet.key(tr, tc)] = cell?.let { if (it.formula != null) it.copy(formula = SheetFill.moved(it.formula, tr - sr, tc - sc), text = "", kind = SheetKind.Empty, value = null) else it }
                    ?: SheetCell("", SheetKind.Empty)
            }
            if (c.cut && c.sheet == index) c.cells.flatten().forEach { (sr, sc, cell) -> if (cell != null) changes.putIfAbsent(Sheet.key(sr, sc), SheetCell("", SheetKind.Empty)) }
            if (c.cut) clip = null
        } else if (!text.isNullOrEmpty()) {
            SheetPaste.table(text).take(5000).forEachIndexed { i, row -> row.forEachIndexed { j, t ->
                val (tr, tc) = target(i, j) ?: return@forEachIndexed
                val (look, format) = lookAt(s, tr, tc)
                changes[Sheet.key(tr, tc)] = SheetInput.cell(t, s[tr, tc], look, format, b.format)
            } }
        } else { message = "Nothing to paste"; return }
        if (changes.isEmpty()) return
        history.apply(b, index, changes); version++
        WriterLog.i("Sheets: pasted ${changes.size} cells${if (c != null) "" else " from the clipboard"}")
    }

    /** File › Versions: the file as it was before a save, in the editor in place of what is there (Undo goes back). */
    fun openVersion(v: File) = fileStep("version opened", { _, next, _ -> v.copyTo(next, overwrite = true) }) { message = "This version is open. Save keeps it, Undo goes back." }

    /** A sheet added after this one, renamed, deleted, or moved to place [to]. */
    fun sheetStep(add: String? = null, rename: String? = null, delete: Boolean = false, to: Int? = null, copy: Boolean = false) {
        val i = index
        fileStep(when { copy -> "sheet copied"; add != null -> "sheet added"; rename != null -> "sheet renamed"; delete -> "sheet deleted"; else -> "sheet moved" }, { baked, next, b ->
            if (b.kind == "xlsx") SheetBook.xlsx(baked, next, b.parts, b.tabs.map { it.name }, i, add, rename, delete, to, copy)
            else SheetBook.ods(baked, next, b.tabs.map { it.name }, i, add, rename, delete, to, copy)
        }, structure = true) { nb ->
            views.clear(); header = null
            index = when { add != null -> i + 1; delete -> maxOf(0, i - 1); to != null -> to; else -> i }.coerceIn(0, nb.tabs.lastIndex)
        }
    }

    /** A note on cell [r], [c] (the sheet's) by [author]: made or changed; [text] null takes it away. */
    fun putNote(r: Int, c: Int, author: String, text: String?) { val i = index
        fileStep(if (text == null) "note taken away" else "note put on", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetNotes.xlsx(baked, next, b.parts.getValue("$i"), r, c, author, text); "ods" -> SheetNotes.ods(baked, next, i, r, c, author, text)
            else -> error("A CSV file keeps no notes: save it as a workbook first.") } })
    }
    /** Page layout: this sheet's print settings made [next] (a change to the file). */
    fun printStep(next: SheetPrint) { val i = index
        if (book?.kind == "csv") { message = "A CSV file keeps no page settings: save it as a workbook first."; return }
        fileStep("page layout", { baked, out, bb -> if (bb.kind == "xlsx") SheetPageSetup.xlsx(baked, out, bb.parts.getValue("$i"), i, bb.tabs[i].name, next)
            else SheetPageSetup.ods(baked, out, i, bb.tabs[i].name, next) })
    }
    /** A workbook name [name] made to stand for cells [g] of this sheet (null: taken away). */
    fun nameStep(name: String, g: SheetRange?) { val i = index
        fileStep(if (g == null) "name taken away" else "name defined", { baked, next, bb -> val sheetName = bb.tabs[i].name
            if (bb.kind == "xlsx") SheetNames.xlsx(baked, next, bb.parts.getValue("workbook"), name, g?.let { SheetNames.refersTo(sheetName, it) })
            else SheetNames.ods(baked, next, name, sheetName.takeIf { g != null }, g) }) { message = if (g == null) "Name $name taken away" else "$name stands for ${SheetNames.refersTo(it.tabs[i].name, g)}" }
    }
    /** Protect ([protect]) or unprotect this sheet, with [password] (none: ""). */
    fun protectSheet(protect: Boolean, password: String) { val i = index
        fileStep(if (protect) "sheet protected" else "sheet unprotected", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetProtect.xlsx(baked, next, b.parts.getValue("$i"), protect, password); "ods" -> SheetProtect.ods(baked, next, i, protect, password)
            else -> error("A CSV file can't be protected: save it as a workbook first.") } }, unprotecting = !protect) { message = if (protect) "Sheet protected" else "Sheet unprotected" }
    }
    /** Review › Protect workbook (its structure), with [password] or none, or Unprotect it (the password, if it has one, checked). */
    fun protectBook(protect: Boolean, password: String) {
        fileStep(if (protect) "workbook protected" else "workbook unprotected", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetProtect.bookXlsx(baked, next, b.parts.getValue("workbook"), protect, password); "ods" -> SheetProtect.bookOds(baked, next, protect, password)
            else -> error("A CSV file can't be protected: save it as a workbook first.") } }, unprotecting = true) { message = if (protect) "Workbook protected" else "Workbook unprotected" }
    }
    /** Conditional formatting on cells [d] (the sheet's): [rule] made, null: the rules over them cleared. */
    fun putRule(d: SheetRange, rule: SheetConditional.Rule?) { val i = index
        fileStep(if (rule == null) "rules cleared" else "rule made (${rule.kind})", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetConditional.xlsx(baked, next, b.parts.getValue("$i"), b.parts["styles"] ?: error("No styles."), d, rule)
            "ods" -> SheetConditional.ods(baked, next, i, b.tabs[i].name, d, rule)
            else -> error("A CSV file keeps no rules: save it as a workbook first.") } })
    }
    /** A drop-down list on cells [d] (the sheet's): [items] its values, null takes it away. */
    fun putList(d: SheetRange, items: List<String>?) { val i = index
        fileStep(if (items == null) "drop-down list taken away" else "drop-down list made", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetLists.xlsx(baked, next, b.parts.getValue("$i"), d, items); "ods" -> SheetLists.ods(baked, next, i, d, items)
            else -> error("A CSV file keeps no lists: save it as a workbook first.") } })
    }
    /** Hide (a sheet's tab, Excel's) or Unhide sheet [at]: the file's; hidden, the next shown sheet opened. */
    fun showSheet(at: Int, hidden: Boolean) {
        fileStep(if (hidden) "sheet hidden" else "sheet shown", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetVisibility.xlsx(baked, next, at, hidden); "ods" -> SheetVisibility.ods(baked, next, at, hidden); else -> error("A CSV file has one sheet.") } }, structure = true) { nb ->
            views.clear(); header = null
            index = if (hidden) nb.tabs.indices.firstOrNull { !nb.tabs[it].hidden && it >= at } ?: nb.tabs.indices.first { !nb.tabs[it].hidden } else at
        }
    }

    fun undoOrRedo(redo: Boolean) {
        val b = book ?: return
        draft = null
        val step = (if (redo) history.redo(b) else history.undo(b)) ?: return
        version++
        // a whole-file step: its copy read again (undone: the copy before, with the edits that were not in it)
        if (step is SheetHistory.FileStep) scope.launch {
            if (redo) { work = step.to; reread(step.to, emptyMap()) } else { work = step.from; reread(step.from, step.edits) }
        }
        WriterLog.i("Sheets: ${if (redo) "redo" else "undo"}")
        val k = step.after.keys.firstOrNull() ?: return
        views.getOrPut(step.sheet) { SheetView() }.let { v -> v.row = Sheet.row(k); v.col = Sheet.col(k); v.reveal++ }
        if (step.sheet != index) index = step.sheet
    }

    /**
     * Save: the edits written into a new file beside it, the file as it was kept as a version (File › Versions, as
     * Writer's), then the new one put in its place; [then] after. What is open stays as it is, Undo too (as Word's, and
     * AutoSave saves after every change): the edits are written onto the working copy, else onto a copy of the file as
     * it was opened, kept as the working copy from then on — so each save writes them afresh, never twice over.
     */
    fun save(then: (() -> Unit)? = null) {
        val b = book ?: return
        val f = file ?: run { naming = true; return } // a new spreadsheet: named first
        if (saving) return
        commit(false)
        saving = true
        val was = work
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val base = was ?: File(workDir, "${System.nanoTime()}.opened.${f.extension}").also { f.copyTo(it, overwrite = true) }
                    val temp = File(f.parentFile, ".${f.name}.tetra-saving")
                    try {
                        WriterLog.timed("Sheets: saved ${b.edits.values.sumOf { it.size }} cells in ${b.edits.size} sheets") { SheetSave.save(b, base, temp) }
                        if (f.exists()) keepPrevious(context, f) // a new spreadsheet has none
                        check(temp.renameTo(f)) { "Could not save ${f.name}." }
                    } finally { temp.delete() }
                    base
                }
            }
            saving = false
            result.onSuccess { base -> sheetDraft(context, f).delete(); work = base; history.markSaved(); version++; message = "Saved"; then?.invoke() }
                .onFailure { WriterLog.e("Sheets: could not save", it); message = it.message ?: "Could not save." }
        }
    }

    /** File › Save a copy: the spreadsheet with its edits as "<name> (copy)" beside it; the one open stays as it is. */
    fun saveCopy() { val b = book ?: return; val f = file ?: return; commit(false)
        scope.launch {
            val copy = generateSequence(1) { it }.mapIndexed { k, _ -> File(f.parentFile, "${f.nameWithoutExtension} (copy${if (k == 0) "" else " ${k + 1}"}).${f.extension}") }.first { !it.exists() }
            val ok = withContext(Dispatchers.IO) { runCatching { SheetSave.save(b, work ?: f, copy) }.onFailure { WriterLog.e("Sheets: could not save a copy", it) }.isSuccess }
            message = if (ok) "Saved a copy: ${copy.name}" else "Could not save a copy."
        }
    }
    /** File › Save as: the spreadsheet with its edits as [target]; that one is open from then on (the old file as it was). */
    fun saveAs(target: File) { val b = book ?: return; val f = file ?: return; if (saving) return; commit(false); saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SheetSave.save(b, work ?: f, target); DecimalFormatSymbols.getInstance().let { Sheets.read(target, it.decimalSeparator, it.groupingSeparator) } } }
            saving = false
            result.onSuccess { nb -> sheetDraft(context, f).delete(); file = target; prepare(nb); history.saved(); work = null; book = nb; version++; message = "Saved as ${target.name}" }
                .onFailure { WriterLog.e("Sheets: could not save as", it); message = it.message ?: "Could not save." }
        }
    }
    /** File › Rename: the file itself, its versions and its unsaved draft with it. */
    fun renameFile(target: File) { val f = file ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching {
                val draft = sheetDraft(context, f); val versions = versionsDir(context, f)
                check(f.renameTo(target)) { "Could not rename ${f.name}." }
                if (draft.isFile) draft.renameTo(sheetDraft(context, target))
                if (versions.isDirectory) versions.renameTo(versionsDir(context, target))
            }.onFailure { WriterLog.e("Sheets: rename", it) }.isSuccess }
            if (ok) { file = target; message = "Renamed ${target.name}" } else message = "Could not rename it."
        }
    }
    // File › AutoSave: a few seconds after a change (not while a cell is being typed in), the file saved
    LaunchedEffect(version, autoSave, editing, draft == null) {
        if (!autoSave || !editing || file == null || draft != null || history.unsaved == 0) return@LaunchedEffect
        delay(4000)
        if (!saving && draft == null && history.unsaved != 0) { WriterLog.i("Sheets: autosaved"); save() }
    }

    LaunchedEffect(sheet, query, searching, layout) {
        val s = sheet; val l = layout
        if (!searching || s == null || l == null || query.isBlank()) { found = emptyList(); return@LaunchedEffect }
        delay(250)
        // A cell's name ("B200") goes to that cell, as Excel's Go To.
        if (Regex("""^[A-Za-z]{1,3}\d{1,7}$""").matches(query.trim())) Sheets.cellRef(query.trim())?.let { (r, c) ->
            view.row = r; view.col = c; view.reveal++; WriterLog.i("Sheets: go to a cell")
        }
        val start = System.nanoTime()
        found = withContext(Dispatchers.Default) {
            SheetSearch.find(s, query).filter { l.row(Sheet.row(it)) >= 0 && l.col(Sheet.col(it)) >= 0 }
                .sortedWith(compareBy({ l.row(Sheet.row(it)) }, { l.col(Sheet.col(it)) }))
        }
        current = pending?.let { p -> found.indexOf(p).takeIf { it >= 0 } } ?: 0
        pending = null
        WriterLog.i("Sheets: search of ${query.length} letters, ${found.size} found in ${(System.nanoTime() - start) / 1_000_000} ms")
        found.getOrNull(current)?.let { view.row = Sheet.row(it); view.col = Sheet.col(it); view.reveal++ }
    }
    LaunchedEffect(book, query, searching, everywhere) {
        val b = book
        hits = emptyList(); searched = 0
        if (!searching || !everywhere || b == null || query.isBlank()) return@LaunchedEffect
        delay(300)
        val start = System.nanoTime()
        val shown = b.tabs.indices.filter { !b.tabs[it].hidden }
        for ((n, i) in shown.withIndex()) {
            val more = withContext(Dispatchers.Default) {
                runCatching {
                    // an .xlsx's sheets are scanned, not read (the user's 46 tables: seconds, not most of a minute)
                    b.scan?.invoke(i, query)?.take(300)?.map { SheetHit(i, it.key, it.text, it.context) } ?: b.sheet(i).let { s ->
                        SheetSearch.find(s, query).take(300).map { k -> SheetHit(i, k, s[Sheet.row(k), Sheet.col(k)]?.text.orEmpty(), SheetSearch.context(s, k)) }
                    }
                }.onFailure { WriterLog.w("Sheets: sheet ${i + 1} could not be searched", it) }.getOrDefault(emptyList())
            }
            hits = hits + more; searched = n + 1
            if (hits.size >= 1000) break
        }
        WriterLog.i("Sheets: search in ${searched} sheets, ${hits.size} found in ${(System.nanoTime() - start) / 1_000_000} ms")
    }

    /** The cells the ribbon's tools work on: the chosen ones (as the file has them), else the chosen cell (a merged one whole). */
    fun targets(s: Sheet, l: SheetLayout): List<Long> {
        view.range?.let { g -> return (g.r1..minOf(g.r2, l.rows.lastIndex)).flatMap { r -> (g.c1..minOf(g.c2, l.cols.lastIndex)).map { c -> Sheet.key(l.rows[r], l.cols[c]) } }.take(100_000) }
        val g = s.merge(view.row, view.col) ?: SheetRange(view.row, view.col, view.row, view.col)
        return (g.r1..g.r2).flatMap { r -> (g.c1..g.c2).map { c -> Sheet.key(r, c) } }
    }
    /** The targets' range as the file has it (for borders round it). */
    fun box(keys: List<Long>) = SheetRange(keys.minOf { Sheet.row(it) }, keys.minOf { Sheet.col(it) }, keys.maxOf { Sheet.row(it) }, keys.maxOf { Sheet.col(it) })

    /** File › Export PDF: the sheet as it prints, beside the spreadsheet, never over a file. */
    fun exportPdf() {
        val f = file ?: return; val s = sheet ?: return; val b = book ?: return; val l = layout ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val stem = f.nameWithoutExtension + if (b.tabs.count { !it.hidden } > 1) " - ${s.name}" else ""
                val target = generateSequence(1) { it + 1 }.map { File(f.parentFile, if (it == 1) "$stem.pdf" else "$stem ($it).pdf") }.first { !it.exists() }
                val n = target.outputStream().use { SheetPainter.pdf(s, b.styles, l, it, density, file?.name.orEmpty()) }
                WriterLog.i("Sheets: PDF of $n pages, ${target.length() / 1024} KB")
                target
            } }
            message = result.fold({ "Saved ${it.name} beside the spreadsheet" }, { WriterLog.e("Sheets: PDF", it); "Could not make the PDF." })
        }
    }

    /** File › Print: the pages as Export PDF makes them, to Android's printing. */
    fun printIt() {
        val s = sheet ?: return; val b = book ?: return; val l = layout ?: return
        val title = file?.nameWithoutExtension ?: "Spreadsheet"
        scope.launch {
            val pdf = withContext(Dispatchers.IO) { runCatching {
                File(context.cacheDir, "print.pdf").also { out -> out.outputStream().use { SheetPainter.pdf(s, b.styles, l, it, density, file?.name.orEmpty()) } }
            }.onFailure { WriterLog.e("Sheets: print", it) }.getOrNull() } ?: run { message = "Could not make the pages to print."; return@launch }
            context.getSystemService(android.print.PrintManager::class.java)?.print(title, PdfPrintAdapter(pdf, title), null)
            WriterLog.i("Sheets: print ${pdf.length() / 1024} KB")
        }
    }

    /**
     * Excel's quick functions (Formulas, Home): the chosen cell given =SUM(…) of the numbers above it (else those to its
     * left), in the cell bar, to be put in with Enter as Excel's AutoSum.
     */
    fun autoFunction(name: String) {
        val s = sheet ?: return
        val (r, c) = chosen(s)
        fun number(rr: Int, cc: Int) = s[rr, cc]?.kind == SheetKind.Number
        var top = r - 1
        while (top >= 0 && number(top, c)) top--
        val ref = if (top < r - 1) "${Sheets.colName(c)}${top + 2}:${Sheets.colName(c)}$r" else {
            var left = c - 1
            while (left >= 0 && number(r, left)) left--
            if (left < c - 1) "${Sheets.colName(left + 1)}${r + 1}:${Sheets.colName(c - 1)}${r + 1}" else ""
        }
        val t = "=$name($ref)"
        barFocus.requestFocus() // first: taking focus starts typing with the cell's own text, which this replaces
        draft = TextFieldValue(t, androidx.compose.ui.text.TextRange(if (ref.isEmpty()) t.length - 1 else t.length))
        TextEditorStore(context).usedFunction(name); WriterLog.i("Sheets: $name")
    }

    /**
     * Writer's way (asked 16 Oct): no menu over the sheet — a ribbon above the sheet tabs, its tabs as Word's (Selection, File,
     * Home, Insert, Formulas, Data, View) while editing, and a tab for what is chosen (a column, a row, the sheet)
     * that comes by itself, as Writer's Selection tab does.
     */
    /** Clear: the chosen cells' contents gone, their looks kept (one step of Undo). */
    /** The view's filter kept in the file as its AutoFilter ([keepIt]), or the file's taken away (every row shown, the view's filter cleared). */
    fun filterStep(s: Sheet, keepIt: Boolean) { val i = index
        val header = SheetLayout.header(s)
        val range = SheetRange(header, s.autoFilter?.c1 ?: 0, s.autoFilter?.r2 ?: (s.rows - 1), s.autoFilter?.c2 ?: (s.cols - 1))
        val keep = view.filters.toMap()
        val hidden = if (!keepIt) emptySet() else (header + 1..range.r2).filter { r -> !keep.all { (c, values) -> SheetLayout.value(s[r, c]) in values } }.toSet()
        fileStep(if (keepIt) "filter kept in the file" else "file filter taken away", { baked, next, bb -> val name = bb.tabs[i].name
            if (bb.kind == "xlsx") SheetAutoFilter.xlsx(baked, next, bb.parts.getValue("$i"), i, name, range.takeIf { keepIt }, keep, hidden, old = s.autoFilter)
            else SheetAutoFilter.ods(baked, next, i, name, range.takeIf { keepIt }, keep, hidden, old = s.autoFilter) }) { if (!keepIt) view.filters.clear() }
    }
    /** Rows [rows] (the sheet's) grouped a level deeper ([by] 1) or less (-1), or their group collapsed or expanded ([collapse]). */
    fun outlineStep(rows: IntRange, by: Int, collapse: Boolean?) { val i = index
        fileStep(when { by > 0 -> "rows grouped"; by < 0 -> "rows ungrouped"; collapse == true -> "group collapsed"; else -> "group expanded" }, { baked, next, bb ->
            if (bb.kind == "xlsx") SheetOutline.xlsx(baked, next, bb.parts.getValue("$i"), rows.first, rows.last, by, collapse) else SheetOutline.ods(baked, next, i, rows.first, rows.last, by, collapse) }) {
            if (collapse == true) { view.row = rows.last + 1; view.reveal++ } }
    }
    /** A drawing on this sheet taken away, moved to a cell or sized ([change]); chosen no more after (its place among the file's may change). */
    fun figureStep(d: SheetDrawing, change: SheetDrawings.Change) { val i = index; val ordinal = d.ordinal
        fileStep(when (change) { SheetDrawings.Delete -> "drawing taken away"; is SheetDrawings.Move -> "drawing moved"; is SheetDrawings.Size -> "drawing sized" }, { baked, next, bb ->
            if (bb.kind == "xlsx") SheetDrawings.xlsx(baked, next, bb.parts.getValue("$i"), ordinal, change) else SheetDrawings.ods(baked, next, i, ordinal, change) }) {
            view.figure = if (change is SheetDrawings.Size) ordinal else -1 }
    }
    /** Review › Spelling: the sheet's words checked (Words' dictionaries, on the phone); the first not known shown, with its suggestions. */
    fun checkSpelling() { val s = sheet ?: return
        message = "Checking spelling…"
        scope.launch {
            val words = SheetSpelling.words(s)
            val wrong = speller.check(words.map { it.third })
            val queue = words.filter { it.third in wrong }
            if (queue.isEmpty()) { message = "No spelling mistakes found"; return@launch }
            message = null; spellRun = SpellRun(queue, wrong, emptySet())
            WriterLog.i("Sheets: spelling, ${queue.size} words to look at")
        }
    }
    /** Cells [g] (the sheet's) inserted, the rest shifted down ([rows]) or right, or deleted, the rest shifted up or left: one step of Undo. */
    fun shiftCells(g: SheetRange, rows: Boolean, insert: Boolean) {
        val s = sheet ?: return; val b = book ?: return
        if (s.merges.any { m -> if (rows) m.c1 <= g.c2 && g.c1 <= m.c2 && m.r2 >= g.r1 else m.r1 <= g.r2 && g.r1 <= m.r2 && m.c2 >= g.c1 }) { message = "Merged cells are in the way: unmerge them first"; return }
        val changes = SheetCellShift.changes(s, g, rows, insert)
        if (changes.isEmpty()) return
        history.apply(b, index, changes); version++; view.range = null
        WriterLog.i("Sheets: cells ${if (insert) "inserted" else "deleted"}, shifted ${if (rows) (if (insert) "down" else "up") else (if (insert) "right" else "left")}")
    }
    /** Insert › Picture: the picked one (Words' preparing: a phone-sized copy) on the sheet at the chosen cell, at most 300 pt wide. */
    val pickPicture = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { picked -> picked ?: return@rememberLauncherForActivityResult
        scope.launch {
            val img = withContext(Dispatchers.IO) { runCatching { preparePicture(context, picked) }.getOrNull() }
            val bytes = img?.bytes ?: run { message = "Could not add that picture."; return@launch }
            val s = sheet ?: return@launch; val (r, c) = chosen(s); val i = index
            val k = (300f / img.widthPt).coerceAtMost(1f)
            fileStep("picture put in", { baked, next, bb -> if (bb.kind == "xlsx") SheetChart.pictureXlsx(baked, next, bb.parts.getValue("$i"), r, c, img.widthPt * k, img.heightPt * k, bytes, img.extension)
                else SheetChart.pictureOds(baked, next, i, r, c, img.widthPt * k, img.heightPt * k, bytes, img.extension) })
        }
    }
    /** Data › From text/CSV: the file's table typed into the sheet from the chosen cell (numbers and dates read as typing reads them), one step of Undo. */
    val importCsv = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { picked -> picked ?: return@rememberLauncherForActivityResult
        scope.launch {
            val s = sheet ?: return@launch; val b = book ?: return@launch; val (r0, c0) = chosen(s)
            val table = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(picked)?.use { it.readBytes() }?.let { DecimalFormatSymbols.getInstance().let { sym -> Sheets.csv(it, sym.decimalSeparator, sym.groupingSeparator).sheet(0) } } }.getOrNull() }
                ?: run { message = "That file could not be read"; return@launch }
            val changes = LinkedHashMap<Long, SheetCell>()
            table.cells.forEach { k, c -> if (c.text.isNotEmpty()) { val r = r0 + Sheet.row(k); val col = c0 + Sheet.col(k); val (look, format) = lookAt(s, r, col)
                changes[Sheet.key(r, col)] = SheetInput.cell(c.text, s[r, col], look, format, b.format) } }
            if (changes.isEmpty()) { message = "Nothing in that file"; return@launch }
            history.apply(b, index, changes); version++; message = "Imported ${table.rows} rows"; WriterLog.i("Sheets: a CSV of ${changes.size} cells imported")
        }
    }
    /** Excel's Clear ▾ on the chosen cells: [what] "contents", "formats", "all" (both), "notes" or "links" (changes to the file). */
    fun clearCells(what: String = "contents") {
        val s = sheet ?: return; val b = book ?: return; val l = layout ?: return
        val keys = targets(s, l); val i = index
        val changes = LinkedHashMap<Long, SheetCell>()
        when (what) {
            "contents" -> for (k in keys) { val old = s.cells[k] ?: continue; if (old.kind != SheetKind.Empty || old.formula != null) changes[k] = SheetInput.cell("", old, old.style, "General", b.format) }
            "formats" -> changes += SheetLook.change(b, s, keys) { _, _ -> b.styles.getOrNull(0) ?: SheetStyle() }
            "all" -> for (k in keys) { val old = s.cells[k] ?: continue; changes[k] = SheetCell("", SheetKind.Empty, style = 0, note = old.note, link = old.link) }
            else -> {
                // notes and links live in the file's own parts: one change to it, a step for each cell
                val cells = keys.filter { k -> s.cells[k]?.let { if (what == "notes") it.note != null else it.link != null } == true }
                if (cells.isEmpty()) { message = if (what == "notes") "No notes here" else "No links here"; return }
                fileStep(if (what == "notes") "notes cleared" else "links cleared", { baked, next, bb ->
                    var from = baked
                    cells.forEachIndexed { n, k -> val to = if (n == cells.lastIndex) next else File(next.parentFile, "${next.name}.$n.part")
                        val r = Sheet.row(k); val c = Sheet.col(k)
                        when {
                            bb.kind == "xlsx" && what == "notes" -> SheetNotes.xlsx(from, to, bb.parts.getValue("$i"), r, c, "", null)
                            bb.kind == "xlsx" -> SheetLinks.xlsx(from, to, bb.parts.getValue("$i"), r, c, null)
                            bb.kind == "ods" && what == "notes" -> SheetNotes.ods(from, to, i, r, c, "", null)
                            bb.kind == "ods" -> SheetLinks.ods(from, to, i, r, c, null)
                            else -> error("A CSV file keeps no notes or links.")
                        }
                        if (from != baked) from.delete(); from = to }
                })
                return
            }
        }
        if (changes.isNotEmpty()) { history.apply(b, index, changes); version++ }
    }

    @Composable fun Ribbon(s: Sheet, b: Workbook, l: SheetLayout) {
        val w = islandContentColor()
        fun look(change: (SheetStyle, Long) -> SheetStyle) {
            val keys = targets(s, l)
            val changed = SheetLook.change(b, s, keys, change)
            if (changed.isNotEmpty()) { history.apply(b, index, changed); version++; WriterLog.i("Sheets: ${changed.size} cells formatted") }
        }
        val (cr, cc) = chosen(s)
        val now = (s[cr, cc]?.fileStyle ?: (s.rowStyles[cr] ?: s.colStyles[cc] ?: 0)).let { b.styles.getOrNull(it) } ?: SheetStyle()
        val h = header
        val g = view.range
        // the tab for what is chosen, named by it; the others only while editing
        // a drawing chosen while editing: its own tab, named for what it is
        val fig = s.drawings.firstOrNull { it.ordinal == view.figure && view.figure >= 0 }
        val figName = fig?.let { d -> when { d.chart != null -> "Chart"; d.image != null -> "Picture"; d.shape == null && d.text != null -> "Text box"; else -> "Shape" } }
        val ctx = when (h?.first) { 'c' -> "Column ${Sheets.colName(h.second)}"; 'r' -> "Row ${l.rows[h.second] + 1}"; 's' -> "Sheet"; else -> figName }
        // Selection first, as in Words: Cut, Copy, Paste and Clear out of the crowded Home (asked 16 Oct); a cell is
        // always chosen while editing, so it is always there then, but Home stays the tab in use
        val base = listOf("Selection", "File", "Home", "Insert", "Page layout", "Formulas", "Data", "Review", "View")
        val tabs = (if (editing) base else listOfNotNull("Selection".takeIf { g != null })) + listOfNotNull(ctx)
        if (tabs.isEmpty()) return
        LaunchedEffect(ctx) { tab = ctx ?: tab.takeIf { it in base } ?: "Home" }
        val shown = tab.takeIf { it in tabs } ?: tabs.first()
        // A group with choices of its own (Borders, Number) takes the ribbon's place, ‹ back (asked 16 Oct, as Page numbers).
        @Composable fun Strip(name: String) = Row(Modifier.fillMaxWidth().shadedRow().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StripHead(name, w) { group = null }
            val thin = SheetLine(0xFF000000.toInt(), 1f)
            if (name == "Borders") {
                                    val keys = targets(s, l); val g2 = box(keys)
                                    RibbonButton("All", w, R.drawable.ic_border_all) { look { st, k -> SheetLook.edges(st, k, g2, thin, all = true) } }
                                    RibbonButton("Outside", w, R.drawable.ic_border_outer) { look { st, k -> SheetLook.edges(st, k, g2, thin, all = false) } }
                                    RibbonButton("Bottom", w, R.drawable.ic_border_bottom) { look { st, k -> SheetLook.edges(st, k, g2, thin, all = false, onlyBottom = true) } }
                                    RibbonButton("Thick bottom", w, R.drawable.ic_border_bottom) { look { st, k -> SheetLook.edges(st, k, g2, SheetLine(0xFF000000.toInt(), 2f), all = false, onlyBottom = true) } }
                                    RibbonButton("Double bottom", w, R.drawable.ic_border_bottom) { look { st, k -> SheetLook.edges(st, k, g2, SheetLine(0xFF000000.toInt(), 3f, double = true), all = false, onlyBottom = true) } }
                                    RibbonButton("None", w, R.drawable.ic_border_clear) { look { st, k -> SheetLook.edges(st, k, g2, null, all = true) } }
                                            } else if (name == "Cell styles") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // each drawn in its look, as Excel's gallery
                SheetLook.STYLES.forEach { cs ->
                    Text(cs.name, color = Color(cs.color ?: 0xFF000000.toInt()), fontWeight = if (cs.bold) FontWeight.Bold else null,
                        fontStyle = if (cs.italic) androidx.compose.ui.text.font.FontStyle.Italic else null, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(4.dp)).background(Color(cs.fill ?: 0xFFFFFFFF.toInt()))
                            .then(cs.box?.let { Modifier.border(1.dp, Color(it.color), RoundedCornerShape(4.dp)) } ?: cs.bottom?.let { e -> Modifier.drawBehind {
                                drawRect(Color(e.color), Offset(0f, size.height - e.width.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, e.width.dp.toPx())) } } ?: Modifier)
                            .clickable { look { st, _ -> SheetLook.styled(st, cs, b.styles.getOrNull(0) ?: SheetStyle()) }; WriterLog.i("Sheets: cell style ${cs.name}") }
                            .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 12.dp))
                } } else if (name == "Conditional formatting") {
                // Excel's menu: highlight rules and top/bottom ask their values and look; bars and scales go on at once
                val d = targets(s, l).let { ks -> if (ks.isEmpty()) null else box(ks) }
                fun rule(kind: String) { if (d == null) return; group = null; if (kind in setOf("dataBar", "colorScale")) putRule(d, SheetConditional.Rule(kind)) else ruling = kind to d }
                RibbonButton("Greater than", w, R.drawable.ic_keyboard_arrow_up) { rule("greaterThan") }
                RibbonButton("Less than", w, R.drawable.ic_keyboard_arrow_down) { rule("lessThan") }
                RibbonButton("Between", w, R.drawable.ic_unfold_less) { rule("between") }
                RibbonButton("Equal to", w, R.drawable.ic_drag_handle) { rule("equal") }
                RibbonButton("Text that contains", w, R.drawable.ic_text_fields) { rule("contains") }
                RibbonButton("Duplicate values", w, R.drawable.ic_copy) { rule("duplicate") }
                RibbonDivider(w)
                RibbonButton("Top 10", w, R.drawable.ic_format_list_numbered) { rule("top10") }
                RibbonButton("Above average", w, R.drawable.ic_horizontal_rule) { rule("aboveAverage") }
                RibbonDivider(w)
                RibbonButton("Data bars", w, R.drawable.ic_bar_chart) { rule("dataBar") }
                RibbonButton("Colour scale", w, R.drawable.ic_gradient) { rule("colorScale") }
                RibbonDivider(w)
                RibbonButton("Clear rules", w, R.drawable.ic_format_clear) { if (d != null) { group = null; putRule(d, null) } }
            } else if (name == "Format as table") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // each a small table in its colours: Excel's gallery
                val d = view.range?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) }
                SheetTables.OFFERED.forEach { style -> val base = Color(SheetTables.colour(style, b.theme)); val light = style.startsWith("TableStyleLight")
                    Column(Modifier.size(56.dp, 44.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
                        .semantics { contentDescription = style.removePrefix("TableStyle").replace(Regex("(\\d+)"), " $1") }
                        .clickable { group = null; if (d == null) message = "Choose the cells with their headings first (a long press, then drag)" else formatAsTable(d, style) }) {
                        Box(Modifier.fillMaxWidth().height(11.dp).background(base))
                        repeat(3) { k -> Box(Modifier.fillMaxWidth().height(11.dp).background(if (k % 2 == 0 && !light) base.copy(alpha = 0.3f) else Color.White)
                            .then(if (light) Modifier.drawBehind { drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) } else Modifier)) }
                    }
                } } else listOf("General" to "General", "1.234,50" to "#,##0.00", "€" to "#,##0.00 \"€\"", "%" to "0%", "Date" to "d/m/yyyy", "Time" to "h:mm", "Text" to "@")
                .forEach { (label, code) -> RibbonButton(label, w, on = now.format == code) { look { st, _ -> st.copy(format = code) } } }
        }
        // a phone: solid across the bottom; a tablet: floating, as Words' and Slides' (asked 16 Oct)
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
        Surface(color = Color(0xFF16191A), contentColor = w, modifier = if (wide) Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(MaterialTheme.shapes.extraLarge) else Modifier.fillMaxWidth()) {
            androidx.compose.animation.AnimatedContent(group.takeIf { it == "Borders" || it == "Number" || it == "Cell styles" || it == "Format as table" || it == "Conditional formatting" }, label = "ribbon",
                transitionSpec = { SLIDE_IN togetherWith SLIDE_OUT }) { strip -> if (strip != null) Strip(strip) else
            Column {
                OfficeTabRow(tabs, shown, w, EXCEL_GREEN, { tab = it; group = null }, contextual = ctx) {
                    if (ctx != null || g != null) IconButton(onClick = { header = null; view.range = null; group = null; view.figure = -1 }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", modifier = Modifier.size(16.dp)) }
                }
                val scroll = remember(shown, header, g == null) { androidx.compose.foundation.ScrollState(0) }
                // one sliding line; on a desktop, rows of whole groups (asked 16 Oct)
                RibbonLine(scroll, Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                    @Composable fun opener(name: String, icon: Int) = RibbonButton(name, w, icon, on = group == name) { group = if (group == name) null else name }
                    val thin = SheetLine(0xFF000000.toInt(), 1f)
                    @Composable fun copyClear() {
                        if (editing) RibbonButton("Cut", w, R.drawable.ic_content_cut) { copyCells(cut = true) }
                        RibbonButton("Copy", w, R.drawable.ic_copy) { copyCells(cut = false) }
                        if (editing) RibbonButton("Paste", w, R.drawable.ic_content_paste) { paste() }
                        // Excel's Clear ▾
                        if (editing) Box { var open by remember { mutableStateOf(false) }
                            RibbonButton("Clear", w, R.drawable.ic_delete) { open = true }
                            DropdownMenu(open, { open = false }) {
                                listOf("Clear all" to "all", "Clear formats" to "formats", "Clear contents" to "contents", "Clear notes" to "notes", "Clear hyperlinks" to "links")
                                    .forEach { (name, what) -> if (b.kind != "csv" || what == "contents" || what == "all") DropdownMenuItem(text = { Text(name) }, onClick = { open = false; clearCells(what) }) } } }
                        // Excel's Insert ▾ and Delete ▾ cells: the chosen cells, the rest shifted
                        if (editing) for (insert in listOf(true, false)) Box { var open by remember { mutableStateOf(false) }
                            RibbonButton(if (insert) "Insert" else "Delete", w, if (insert) R.drawable.ic_add_row_above else R.drawable.ic_delete_row) { open = true }
                            DropdownMenu(open, { open = false }) {
                                val d = targets(s, l).let { ks -> if (ks.isEmpty()) null else box(ks) }
                                listOf(true, false).forEach { rows -> DropdownMenuItem(text = { Text(if (insert) (if (rows) "Shift cells down" else "Shift cells right") else (if (rows) "Shift cells up" else "Shift cells left")) },
                                    onClick = { open = false; d?.let { shiftCells(it, rows, insert) } }) } } }
                        // Words' Copy formatting / Paste formatting (Home's Format painter in Excel): one cell's look onto the cells chosen
                        if (editing) { RibbonDivider(w)
                            RibbonButton("Copy formatting", w, R.drawable.ic_format_paint) { copiedLook = now; message = "Formatting copied — choose cells, then Paste formatting" }
                            RibbonButton("Paste formatting", w, R.drawable.ic_format_paint, enabled = copiedLook != null) { copiedLook?.let { c -> look { _, _ -> c } } } }
                    }
                    @Composable fun sortFilter(col: Int) {
                        RibbonButton(if (col in view.filters) "Change filter" else "Filter", w, R.drawable.ic_filter) { filtering = col }
                        if (col in view.filters) RibbonButton("Clear filter", w, R.drawable.ic_filter_off) { view.filters.remove(col) }
                        RibbonDivider(w)
                        RibbonButton("A → Z", w, R.drawable.ic_sort_name) { view.sort = SheetSort(col, true) }
                        RibbonButton("Z → A", w, R.drawable.ic_sort_name) { view.sort = SheetSort(col, false) }
                        if (view.sort != null) RibbonButton("Clear sort", w, R.drawable.ic_cancel) { view.sort = null }
                        RibbonDivider(w)
                        if (col in view.pins) RibbonButton("Unpin", w, R.drawable.ic_push_pin) { view.pins.remove(col) }
                        else RibbonButton("Pin to the start", w, R.drawable.ic_push_pin) { view.pins.add(col); view.x = 0f }
                    }
                    when {
                        // a drawing's tab: dragged to move it; its size; taken away
                        fig != null && shown == figName -> {
                            Text("Drag it to another cell to move it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
                            RibbonButton("Size", w, R.drawable.ic_aspect_ratio) { figureSizing = fig }
                            RibbonButton("Delete", w, R.drawable.ic_delete) { figureStep(fig, SheetDrawings.Delete) }
                        }
                        shown == "Sheet" -> {
                            Text(b.tabs[index].name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                            RibbonButton("Rename", w, R.drawable.ic_edit) { renamingSheet = true }
                            RibbonDivider(w)
                            RibbonButton("Move left", w, R.drawable.ic_chevron_left, enabled = index > 0) { sheetStep(to = index - 1) }
                            RibbonButton("Move right", w, R.drawable.ic_chevron_right, enabled = index < b.tabs.lastIndex) { sheetStep(to = index + 1) }
                            // Excel's Move or Copy › Create a copy: "Name (2)" after it
                            if (b.kind != "csv") RibbonButton("Copy", w, R.drawable.ic_copy) { val base = b.tabs[index].name.take(26)
                                sheetStep(add = generateSequence(2) { it + 1 }.map { "$base ($it)" }.first { n -> b.tabs.none { it.name.equals(n, true) } }, copy = true) }
                            // Excel's Tab Color
                            if (b.kind != "csv") Swatches(w, R.drawable.ic_palette, "Tab colour", CELL_FILLS) { c -> val i = index; val argb = c?.let { (it.value shr 32).toInt() }
                                fileStep("tab colour", { baked, next, bb -> if (bb.kind == "xlsx") SheetVisibility.colourXlsx(baked, next, bb.parts.getValue("$i"), argb) else SheetVisibility.colourOds(baked, next, i, argb) }) }
                            RibbonDivider(w)
                            RibbonButton("Delete", w, R.drawable.ic_delete, enabled = b.tabs.count { !it.hidden } > 1) { deletingSheet = true }
                            // Excel's Hide and Unhide on a sheet's tab
                            RibbonDivider(w)
                            RibbonButton("Hide", w, R.drawable.ic_visibility_off, enabled = b.tabs.count { !it.hidden } > 1) { showSheet(index, true) }
                            // Excel's Review › Protect Sheet: its locked cells kept as they are
                            if (b.kind != "csv") RibbonButton(if (s.protected) "Unprotect" else "Protect", w, R.drawable.ic_lock, on = s.protected) {
                                if (s.protected && !s.guarded) protectSheet(false, "") else protecting = true }
                            if (b.tabs.any { it.hidden }) Box {
                                var open by remember { mutableStateOf(false) }
                                RibbonButton("Unhide", w, R.drawable.ic_visibility) { open = true }
                                DropdownMenu(open, { open = false }) { b.tabs.forEachIndexed { k, t -> if (t.hidden) DropdownMenuItem(text = { Text(t.name) }, onClick = { open = false; showSheet(k, false) }) } }
                            }
                        }
                        shown.startsWith("Column") && h != null -> {
                            val col = h.second
                            RibbonButton("Select", w, R.drawable.ic_check_box_outline_blank) { val c = l.col(col); if (c >= 0) view.range = SheetRange(0, c, l.rows.lastIndex, c); header = null }
                            RibbonDivider(w)
                            sortFilter(col)
                            if (editing) {
                                RibbonDivider(w)
                                RibbonButton("Insert left", w, R.drawable.ic_add_column_left) { header = null; shift(false, col, 1) }
                                RibbonButton("Insert right", w, R.drawable.ic_add_column_right) { header = null; shift(false, col + 1, 1) }
                                RibbonButton("Delete", w, R.drawable.ic_delete_column) { header = null; shift(false, col, -1) }
                                // Excel's Format: its width (typed, or AutoFit), hidden; the hidden ones beside it shown again
                                RibbonDivider(w)
                                RibbonButton("Width", w, R.drawable.ic_aspect_ratio) { sizing = true to col }
                                RibbonButton("AutoFit", w, R.drawable.ic_fit_page) { header = null; resize(true, col, col, fitted(s, b, true, col), null) }
                                RibbonButton("Hide", w, R.drawable.ic_visibility_off) { header = null; resize(true, col, col, null, true) }
                                val near = listOf(col - 1, col + 1).filter { it >= 0 && it in s.hiddenCols }
                                if (near.isNotEmpty()) RibbonButton("Unhide", w, R.drawable.ic_visibility) { header = null
                                    val lo = generateSequence(near.min()) { it - 1 }.takeWhile { it >= 0 && it in s.hiddenCols }.last(); val hi = generateSequence(near.max()) { it + 1 }.takeWhile { it in s.hiddenCols }.last()
                                    resize(true, lo, hi, null, false) }
                            }
                        }
                        shown.startsWith("Row") && h != null -> {
                            val row = h.second; val sheetRow = l.rows[row]
                            RibbonButton("Select", w, R.drawable.ic_check_box_outline_blank) { view.range = SheetRange(row, 0, row, l.cols.lastIndex); header = null }
                            RibbonDivider(w)
                            RibbonButton("Keep at the top", w, R.drawable.ic_push_pin) { view.freeze = row + 1; view.y = 0f }
                            if (l.frozenRows > 0) RibbonButton("Let scroll", w, R.drawable.ic_push_pin) { view.freeze = 0 }
                            if (editing) {
                                RibbonDivider(w)
                                RibbonButton("Insert above", w, R.drawable.ic_add_row_above) { header = null; shift(true, sheetRow, 1) }
                                RibbonButton("Insert below", w, R.drawable.ic_add_row_below) { header = null; shift(true, sheetRow + 1, 1) }
                                RibbonButton("Delete", w, R.drawable.ic_delete_row) { header = null; shift(true, sheetRow, -1) }
                                RibbonDivider(w)
                                RibbonButton("Height", w, R.drawable.ic_aspect_ratio) { sizing = false to sheetRow }
                                RibbonButton("AutoFit", w, R.drawable.ic_fit_page) { header = null; resize(false, sheetRow, sheetRow, fitted(s, b, false, sheetRow), null) }
                                RibbonButton("Hide", w, R.drawable.ic_visibility_off) { header = null; resize(false, sheetRow, sheetRow, null, true) }
                                val near = listOf(sheetRow - 1, sheetRow + 1).filter { it >= 0 && it in s.hiddenRows }
                                if (near.isNotEmpty()) RibbonButton("Unhide", w, R.drawable.ic_visibility) { header = null
                                    val lo = generateSequence(near.min()) { it - 1 }.takeWhile { it >= 0 && it in s.hiddenRows }.last(); val hi = generateSequence(near.max()) { it + 1 }.takeWhile { it in s.hiddenRows }.last()
                                    resize(false, lo, hi, null, false) }
                            }
                        }
                        shown == "Selection" -> {
                            // Excel's status bar, at the strip's start
                            if (g != null) {
                                val stats = remember(s, l, g, version) { SheetStats.of(s, l, g) }
                                Text(buildString {
                                    if (stats.numbers > 0) append("Sum ${b.format.general(stats.sum)} · Average ${b.format.general(stats.average)} · ")
                                    append("Count ${stats.count}")
                                }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
                                RibbonDivider(w)
                            }
                            copyClear()
                            RibbonButton("Select all", w, R.drawable.ic_select_all) { header = null; view.range = SheetRange(0, 0, l.rows.lastIndex, l.cols.lastIndex) }
                            // whole rows or columns chosen: put in or taken out together
                            if (g != null && editing && g.c1 == 0 && g.c2 == l.cols.lastIndex && view.filters.isEmpty() && view.sort == null) {
                                RibbonDivider(w)
                                RibbonButton("Insert ${g.r2 - g.r1 + 1} rows", w, R.drawable.ic_add_row_below) { shift(true, l.rows[g.r1], g.r2 - g.r1 + 1) }
                                RibbonButton("Delete rows", w, R.drawable.ic_delete_row) { shift(true, l.rows[g.r1], -(g.r2 - g.r1 + 1)) }
                            }
                            if (g != null && editing && g.r1 == 0 && g.r2 == l.rows.lastIndex && view.pins.isEmpty()) {
                                RibbonDivider(w)
                                RibbonButton("Insert ${g.c2 - g.c1 + 1} columns", w, R.drawable.ic_add_column_right) { shift(false, l.cols[g.c1], g.c2 - g.c1 + 1) }
                                RibbonButton("Delete columns", w, R.drawable.ic_delete_column) { shift(false, l.cols[g.c1], -(g.c2 - g.c1 + 1)) }
                            }
                        }
                        shown == "File" -> {
                            // File as in Words and Slides (17 Oct): Save · Versions, Export PDF, Print · Share, another app · About, log · Settings
                            RibbonButton("Save", w, R.drawable.ic_save, enabled = !saving) { save() }
                            // as Words' File: a copy beside it, AutoSave (Words' own setting), Save as, Rename
                            if (file != null) RibbonButton("Save a copy", w, R.drawable.ic_copy, enabled = !saving) { saveCopy() }
                            if (editing) RibbonButton("AutoSave", w, R.drawable.ic_history, on = autoSave) { autoSave = !autoSave; TextEditorStore(context).autoSave = autoSave
                                message = if (autoSave) "AutoSave on — saved a few seconds after each change" else "AutoSave off — Save when you want the file changed" }
                            if (file != null) RibbonButton("Save as", w, R.drawable.ic_save, enabled = !saving) { fileNaming = "as" }
                            if (file != null) RibbonButton("Rename", w, R.drawable.ic_edit, enabled = !saving) { fileNaming = "rename" }
                            RibbonDivider(w)
                            if (file != null) RibbonButton("Versions", w, R.drawable.ic_history) { showVersions = true }
                            RibbonButton("Export PDF", w, R.drawable.ic_file, enabled = file != null) { exportPdf() }
                            RibbonButton("Print", w, R.drawable.ic_print) { printIt() }
                            file?.let { f ->
                                RibbonDivider(w)
                                RibbonButton("Share", w, R.drawable.ic_share) { shareDriveFile(context, f) }
                                RibbonButton("Open in another app", w, R.drawable.ic_open_with) { openElsewhere(context, f) }
                            }
                            RibbonDivider(w)
                            RibbonButton("About this file", w, R.drawable.ic_info) { about = true }
                            RibbonButton("Send log", w, R.drawable.ic_share) { WriterLog.export(context)?.let { shareDriveFile(context, it) } }
                            RibbonDivider(w)
                            RibbonButton("Settings", w, R.drawable.ic_settings) { officeSettings = true }
                        }
                        shown == "Insert" -> {
                            RibbonButton("Row above", w, R.drawable.ic_add_row_above) { shift(true, cr, 1) }
                            RibbonButton("Row below", w, R.drawable.ic_add_row_below) { shift(true, cr + 1, 1) }
                            RibbonDivider(w)
                            RibbonButton("Column left", w, R.drawable.ic_add_column_left) { shift(false, cc, 1) }
                            RibbonButton("Column right", w, R.drawable.ic_add_column_right) { shift(false, cc + 1, 1) }
                            RibbonDivider(w)
                            if (b.kind != "csv") RibbonButton("Sheet", w, R.drawable.ic_note_add) {
                                sheetStep(add = generateSequence(b.tabs.size + 1) { it + 1 }.map { "Sheet$it" }.first { n -> b.tabs.none { it.name.equals(n, true) } })
                            }
                            RibbonButton("Function", w, R.drawable.ic_functions) { barFocus.requestFocus(); draft = TextFieldValue("=", androidx.compose.ui.text.TextRange(1)) }
                            RibbonButton("Symbol", w, R.drawable.ic_special_character) { symbols = true }
                            RibbonButton("Link", w, R.drawable.ic_link) { linking = true }
                            // Excel's Insert › Pictures (Words' picker) and Text Box: on the sheet at the chosen cell
                            if (b.kind != "csv") RibbonButton("Picture", w, R.drawable.ic_gallery) { pickPicture.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            if (b.kind != "csv") RibbonButton("Text box", w, R.drawable.ic_text_fields) { boxing = true }
                            if (b.kind != "csv") RibbonButton("Shape", w, R.drawable.ic_category) { shaping = true }
                            if (b.kind != "csv") RibbonButton("Equation", w, R.drawable.ic_functions) { equating = true }
                            if (b.kind != "csv") RibbonButton("WordArt", w, R.drawable.ic_format_color_text) { artStrip = ART_STYLES[0] to ArtBend(null) }
                            // References: where the data came from — Words' source dialog, the reference in APA 7 on the Sources sheet
                            if (b.kind != "csv") RibbonButton("Source", w, R.drawable.ic_menu_book) { sourcing = true }
                            // References: a first sheet listing every sheet as a link (made again when there is one)
                            if (b.kind != "csv" && b.tabs.size > 1) RibbonButton("Contents sheet", w, R.drawable.ic_toc) {
                                fileStep("contents sheet", { baked, next, bb -> DecimalFormatSymbols.getInstance().let { sym -> SheetContents.apply(baked, next, bb, sym.decimalSeparator, sym.groupingSeparator) } }, structure = true) { views.clear(); header = null; index = 0 } }
                            // Excel's notes (LibreOffice's comments): made or changed in Words' comment dialog; taken away
                            val hasNote = s[cr, cc]?.note != null
                            RibbonButton(if (hasNote) "Edit note" else "Note", w, R.drawable.ic_comment) { noting = true }
                            if (hasNote) RibbonButton("Delete note", w, R.drawable.ic_delete) { putNote(cr, cc, "", null) }
                            // Excel's Insert › Chart: the chosen cells into Words' chart editor (its series from them), then on the sheet beside them
                            RibbonButton("Chart", w, R.drawable.ic_bar_chart) {
                                val d = g ?: run { message = "Choose the cells to chart first (a long press, then drag)"; return@RibbonButton }
                                val rows = (d.r1..d.r2).map { r -> DocRow("", (d.c1..d.c2).map { c -> DocCell("", listOf(DocPara(listOf(DocRun(s[l.rows[r], l.cols[c]]?.text.orEmpty()))))) }) }
                                OfficeCharts.fromTable(DocTable("", "", rows))?.let { charting = SheetRange(l.rows[d.r1], l.cols[d.c1], l.rows[d.r2], l.cols[d.c2]) to it } ?: run { message = "The chosen cells have no numbers to chart." }
                            }
                        }
                        shown == "Page layout" -> {
                            // Excel's Page Layout: how the sheet prints (Export PDF and Print use it), kept in the file
                            val p = s.print
                            fun setPrint(change: (SheetPrint) -> SheetPrint) = printStep(change(p))
                            @Composable fun menu(label: String, icon: Int, choices: List<Pair<String, Boolean>>, pick: (Int) -> Unit) = Box {
                                var open by remember { mutableStateOf(false) }
                                RibbonButton(label, w, icon) { open = true }
                                DropdownMenu(open, { open = false }) { choices.forEachIndexed { k, (name, on) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; pick(k) },
                                    trailingIcon = { if (on) Icon(painterResource(R.drawable.ic_check), null) }) } }
                            }
                            menu("Orientation", if (p.landscape) R.drawable.ic_crop_landscape else R.drawable.ic_crop_portrait, listOf("Portrait" to !p.landscape, "Landscape" to p.landscape)) { k -> setPrint { it.copy(landscape = k == 1) } }
                            menu("Size", R.drawable.ic_fit_page, listOf("A4" to !p.letter, "Letter" to p.letter)) { k -> setPrint { it.copy(letter = k == 1) } }
                            val marginSets = listOf("Normal" to listOf(0.7f, 0.7f, 0.75f, 0.75f), "Wide" to listOf(1f, 1f, 1f, 1f), "Narrow" to listOf(0.25f, 0.25f, 0.75f, 0.75f))
                            menu("Margins", R.drawable.ic_margin, marginSets.map { (n, m) -> n to (p.margins.zip(m).all { (a, c) -> Math.abs(a - c) < 0.01f }) }) { k -> setPrint { it.copy(margins = marginSets[k].second) } }
                            menu("Scale", R.drawable.ic_aspect_ratio, listOf("Actual size" to (p.fitWidth == null && p.scale == 100), "All columns on one page" to (p.fitWidth == 1 && (p.fitHeight ?: 0) == 0), "Sheet on one page" to (p.fitWidth == 1 && p.fitHeight == 1))) { k ->
                                setPrint { when (k) { 0 -> it.copy(scale = 100, fitWidth = null, fitHeight = null); 1 -> it.copy(fitWidth = 1, fitHeight = 0); else -> it.copy(fitWidth = 1, fitHeight = 1) } } }
                            RibbonButton("Print gridlines", w, R.drawable.ic_view_grid, on = p.grid) { setPrint { it.copy(grid = !p.grid) } }
                            RibbonDivider(w)
                            RibbonButton("Set print area", w, R.drawable.ic_print, enabled = g != null) { g?.let { d -> setPrint { it.copy(area = SheetRange(l.rows[d.r1], l.cols[d.c1], l.rows[d.r2], l.cols[d.c2])) } } }
                            if (p.area != null) RibbonButton("Clear print area", w, R.drawable.ic_close) { setPrint { it.copy(area = null) } }
                            // Excel's Header & Footer: its ready-made ones, or Custom… (left, centre, right)
                            val presets = SheetHeaderFooter.PRESETS
                            for ((kind, now) in listOf("header" to p.header, "footer" to p.footer))
                                menu(if (kind == "header") "Header" else "Footer", if (kind == "header") R.drawable.ic_vertical_align_top else R.drawable.ic_vertical_align_bottom,
                                    presets.map { (n, c) -> n to (now == c) } + ("Custom…" to (now != null && presets.none { it.second == now }))) { k ->
                                    if (k < presets.size) setPrint { if (kind == "header") it.copy(header = presets[k].second) else it.copy(footer = presets[k].second) } else footing = kind }
                            // Excel's Print Titles: the chosen rows printed at the top of every page
                            RibbonButton("Print titles", w, R.drawable.ic_title, enabled = g != null, on = p.titles != null) { g?.let { d -> setPrint { it.copy(titles = l.rows[d.r1]..l.rows[d.r2]) } } }
                            if (p.titles != null) RibbonButton("Clear print titles", w, R.drawable.ic_close) { setPrint { it.copy(titles = null) } }
                        }
                        shown == "Formulas" -> {
                            RibbonButton("Insert function", w, R.drawable.ic_functions) { inserting = true }
                            // Excel's Recently Used: the last functions put in, one tapped goes in as Insert function's
                            Box { var open by remember { mutableStateOf(false) }
                                val recent = remember(open, inserted, draft == null) { TextEditorStore(context).recentFunctions }
                                RibbonButton("Recently used", w, R.drawable.ic_history, enabled = recent.isNotEmpty()) { open = true }
                                DropdownMenu(open, { open = false }) { recent.forEach { f -> DropdownMenuItem(text = { Text(f) }, onClick = { open = false; inserted = f }) } } }
                            // Excel's Formula Auditing: the cells a formula reads, those reading this one, the errors one by one; marked on the sheet
                            RibbonDivider(w)
                            RibbonButton("Precedents", w, R.drawable.ic_arrow_upward) { traced = SheetTrace.precedents(s, cr, cc, b.names)
                                message = if (traced.isEmpty()) "${Sheets.colName(cc)}${cr + 1} has no formula reading cells here" else "${Sheets.colName(cc)}${cr + 1} reads ${traced.size} cells (marked)" }
                            RibbonButton("Dependents", w, R.drawable.ic_arrow_downward) { traced = SheetTrace.dependents(s, cr, cc, b.names)
                                message = if (traced.isEmpty()) "No formula here reads ${Sheets.colName(cc)}${cr + 1}" else "${traced.size} cells read ${Sheets.colName(cc)}${cr + 1} (marked)" }
                            RibbonButton("Error checking", w, R.drawable.ic_error) { val errors = SheetTrace.errors(s); traced = errors.toSet()
                                if (errors.isEmpty()) message = "No errors on this sheet" else {
                                    val next = errors.firstOrNull { Sheet.row(it) > cr || (Sheet.row(it) == cr && Sheet.col(it) > cc) } ?: errors.first()
                                    view.row = Sheet.row(next); view.col = Sheet.col(next); view.reveal++
                                    message = "${s[Sheet.row(next), Sheet.col(next)]?.text} in ${Sheets.colName(Sheet.col(next))}${Sheet.row(next) + 1} (${errors.indexOf(next) + 1} of ${errors.size})" } }
                            if (traced.isNotEmpty()) RibbonButton("Remove marks", w, R.drawable.ic_close) { traced = emptySet() }
                            // Excel's Evaluate Formula: the chosen cell's formula worked out a step at a time
                            RibbonButton("Evaluate formula", w, R.drawable.ic_play, enabled = s[cr, cc]?.formula != null) {
                                evaluating = (Sheets.colName(cc) + (cr + 1)) to SheetEvaluate.steps(b, index, Sheet.key(cr, cc)); WriterLog.i("Sheets: a formula evaluated") }
                            // Excel's Name Manager: the workbook's names, one for the chosen cells, one taken away
                            if (editing && b.kind != "csv") RibbonButton("Names", w, R.drawable.ic_label) { namesFor = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) } ?: SheetRange(cr, cc, cr, cc) }
                            // Excel's Calculate Sheet: every formula here worked out again (NOW and TODAY move on), one step of Undo.
                            // shortcut: this sheet only (others are worked out as their cells change), and no manual mode — Tetra always works out on a change
                            if (editing) RibbonButton("Calculate sheet", w, R.drawable.ic_refresh) {
                                val keys = ArrayList<Long>().also { ks -> s.cells.forEach { k, c -> if (c.formula != null) ks += k } }
                                val moved = SheetRecalc.run(b, index, s, keys).filter { (k, c) -> s[Sheet.row(k), Sheet.col(k)].let { it?.text != c.text || it?.value != c.value } }
                                if (moved.isEmpty()) message = "Every formula's result here is up to date" else { history.apply(b, index, moved); version++; message = if (moved.size == 1) "1 result changed" else "${moved.size} results changed" }
                                WriterLog.i("Sheets: calculated, ${moved.size} changed") }
                            RibbonDivider(w)
                            RibbonButton("AutoSum", w, R.drawable.ic_functions) { autoFunction("SUM") }
                            RibbonButton("Average", w, R.drawable.ic_functions) { autoFunction("AVERAGE") }
                            RibbonButton("Count", w, R.drawable.ic_numbers) { autoFunction("COUNT") }
                            RibbonButton("Max", w, R.drawable.ic_keyboard_arrow_up) { autoFunction("MAX") }
                            RibbonButton("Min", w, R.drawable.ic_keyboard_arrow_down) { autoFunction("MIN") }
                            RibbonDivider(w)
                            RibbonButton("Show formulas", w, R.drawable.ic_code, on = formulas) { formulas = !formulas }
                        }
                        shown == "Data" -> {
                            sortFilter(cc)
                            // Excel's Remove duplicates: in the chosen cells (else the cell's whole table of filled cells), the rows alike after the first gone
                            if (editing) { RibbonDivider(w)
                                // Excel's AutoFilter kept in the file: the view's filter (its buttons, its values, the rows it hides), or taken away
                                if (b.kind != "csv" && view.filters.isNotEmpty()) RibbonButton("Keep filter in file", w, R.drawable.ic_filter) { filterStep(s, keepIt = true) }
                                if (b.kind != "csv" && s.autoFilter != null) RibbonButton("Remove file filter", w, R.drawable.ic_filter_off) { filterStep(s, keepIt = false) }
                                // Excel's Outline: the chosen rows grouped a level deeper or less; a group's rows hidden (Collapse) or shown (Expand)
                                if (b.kind != "csv") { val rows = g?.let { l.rows[it.r1]..l.rows[it.r2] } ?: (cr..cr); val group = SheetOutline.groupAt(s.rowLevels, cr)
                                    RibbonButton("Group", w, R.drawable.ic_unfold_less) { outlineStep(rows, 1, null) }
                                    if ((rows.first..rows.last).any { (s.rowLevels[it] ?: 0) > 0 }) RibbonButton("Ungroup", w, R.drawable.ic_unfold_more) { outlineStep(rows, -1, null) }
                                    if (group != null) { val shut = group.all { it in s.hiddenRows }
                                        RibbonButton(if (shut) "Expand" else "Collapse", w, if (shut) R.drawable.ic_add else R.drawable.ic_remove) { outlineStep(group, 0, !shut) } } }
                                // Excel's Reapply: the filter and sort again, on the cells as they are now
                                if (view.filters.isNotEmpty() || view.sort != null) RibbonButton("Reapply", w, R.drawable.ic_filter) { reapplied++ }
                                // Excel's What-If › Goal Seek, and Get Data › From Text/CSV (into the sheet at the chosen cell)
                                RibbonButton("Goal seek", w, R.drawable.ic_functions) { seeking = true }
                                RibbonButton("From text/CSV", w, R.drawable.ic_note_add) { importCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values")) }
                                // Excel's Subtotal: a total row after each group of rows alike in a column, a Grand Total, the groups outlined
                                if (b.kind != "csv") RibbonButton("Subtotal", w, R.drawable.ic_functions) {
                                    totalling = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) } ?: SheetSortRows.region(s, cr, cc, s.rows, s.cols) }
                                // Excel's Sort dialog: the rows themselves reordered (the file changes), by one column then others
                                RibbonButton("Sort", w, R.drawable.ic_sort_name) {
                                    sortingRows = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) } ?: SheetSortRows.region(s, cr, cc, s.rows, s.cols) }
                                RibbonButton("Remove duplicates", w, R.drawable.ic_filter_off) {
                                    val d = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) }
                                    if (d == null || d.r2 <= d.r1) message = "Choose the rows to look in first (a long press, then drag)" else deduping = d }
                                // Excel's Text to columns: one column's words cut at a separator into the columns beside it
                                RibbonButton("Text to columns", w, R.drawable.ic_view_column) {
                                    val d = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c1]) } ?: SheetRange(cr, cc, cr, cc)
                                    splitting = d }
                                // Excel's Data Validation › List: the cells take one of the values given
                                if (b.kind != "csv") RibbonButton("Drop-down list", w, R.drawable.ic_checklist) {
                                    listing = g?.let { SheetRange(l.rows[it.r1], l.cols[it.c1], l.rows[it.r2], l.cols[it.c2]) } ?: SheetRange(cr, cc, cr, cc) } }
                        }
                        // Excel's Review: Spelling (Words' dictionaries), the notes one by one, the sheet's protection, the workbook's counts
                        shown == "Review" -> {
                            RibbonButton("Spelling", w, R.drawable.ic_spellcheck) { checkSpelling() }
                            RibbonDivider(w)
                            val notes = remember(version, s) { ArrayList<Long>().also { l -> s.cells.forEach { k, c -> if (c.note != null) l += k } }.sortedWith(compareBy({ Sheet.row(it) }, { Sheet.col(it) })) }
                            fun goNote(forward: Boolean) { if (notes.isEmpty()) { message = "No notes on this sheet"; return }
                                val here = Sheet.key(cr, cc)
                                val k = if (forward) notes.firstOrNull { it > here } ?: notes.first() else notes.lastOrNull { it < here } ?: notes.last()
                                view.row = Sheet.row(k); view.col = Sheet.col(k); view.reveal++; message = s.cells[k]?.note }
                            RibbonButton("Previous note", w, R.drawable.ic_keyboard_arrow_up, enabled = notes.isNotEmpty()) { goNote(false) }
                            RibbonButton("Next note", w, R.drawable.ic_keyboard_arrow_down, enabled = notes.isNotEmpty()) { goNote(true) }
                            RibbonButton("Show all notes", w, R.drawable.ic_comment, on = allNotes, enabled = notes.isNotEmpty() || allNotes) { allNotes = !allNotes }
                            if (editing) RibbonButton(if (s[cr, cc]?.note != null) "Edit note" else "New note", w, R.drawable.ic_comment) { noting = true }
                            RibbonDivider(w)
                            if (editing && b.kind != "csv") RibbonButton(if (s.protected) "Unprotect sheet" else "Protect sheet", w, R.drawable.ic_lock, on = s.protected) {
                                if (s.protected && !s.guarded) protectSheet(false, "") else protecting = true }
                            if (editing && b.kind != "csv") RibbonButton(if (b.structureLocked) "Unprotect workbook" else "Protect workbook", w, R.drawable.ic_lock, on = b.structureLocked) { protectingBook = true }
                            RibbonButton("Workbook statistics", w, R.drawable.ic_numbers) { counting = true }
                        }
                        shown == "View" -> {
                            if (editing && b.kind != "csv") {
                                // Excel's Freeze panes, kept in the file: the rows above and columns left of the chosen cell, the top row, the first column
                                fun freeze(rows: Int, cols: Int) { val i = index
                                    fileStep(if (rows + cols > 0) "panes frozen" else "panes unfrozen", { baked, next, bb -> if (bb.kind == "xlsx") SheetVisibility.freezeXlsx(baked, next, bb.parts.getValue("$i"), rows, cols)
                                        else SheetVisibility.freezeOds(baked, next, bb.tabs[i].name, rows, cols) }) { view.freeze = null; view.y = 0f; view.x = 0f } }
                                RibbonButton("Freeze panes", w, R.drawable.ic_push_pin) { freeze(cr, cc) }
                                RibbonButton("Freeze top row", w, R.drawable.ic_push_pin) { freeze(1, 0) }
                                RibbonButton("Freeze first column", w, R.drawable.ic_push_pin) { freeze(0, 1) }
                                if (s.frozenRows > 0 || s.frozenCols > 0) RibbonButton("Unfreeze", w, R.drawable.ic_close) { freeze(0, 0) }
                            } else {
                                RibbonButton("Keep rows above at the top", w, R.drawable.ic_push_pin) { l.row(cr).takeIf { it >= 0 }?.let { view.freeze = it; view.y = 0f } }
                                if (l.frozenRows > 0) RibbonButton("Let them scroll", w, R.drawable.ic_push_pin) { view.freeze = 0 }
                            }
                            RibbonDivider(w)
                            RibbonButton("Show formulas", w, R.drawable.ic_code, on = formulas) { formulas = !formulas }
                            RibbonDivider(w)
                            // Excel's View › Gridlines: kept in the file (a change to it, while editing)
                            if (editing && b.kind != "csv") RibbonButton("Gridlines", w, R.drawable.ic_view_grid, on = s.grid) { val i = index; val on = !s.grid
                                fileStep(if (on) "gridlines shown" else "gridlines hidden", { baked, next, bb -> if (bb.kind == "xlsx") SheetVisibility.gridXlsx(baked, next, bb.parts.getValue("$i"), on)
                                    else SheetVisibility.gridOds(baked, next, bb.tabs[i].name, on) }) }
                            // Excel's View › Headings: the column letters and row numbers (LibreOffice's are the whole file's)
                            if (editing && b.kind != "csv") RibbonButton("Headings", w, R.drawable.ic_title, on = s.headings) { val i = index; val on = !s.headings
                                fileStep(if (on) "headings shown" else "headings hidden", { baked, next, bb -> if (bb.kind == "xlsx") SheetVisibility.gridXlsx(baked, next, bb.parts.getValue("$i"), on, "showRowColHeaders")
                                    else SheetVisibility.gridOds(baked, next, bb.tabs[i].name, on, "HasColumnRowHeaders", perSheet = false) }) }
                            RibbonButton("100%", w, R.drawable.ic_aspect_ratio) { view.zoom = 1f }
                            // Excel's Zoom to Selection: the chosen cells fill the screen
                            // Excel's Page Break Preview: the pages as they print, outlined over the sheet, what is not printed greyed
                            RibbonButton("Page break preview", w, R.drawable.ic_insert_page_break, on = breaks) { breaks = !breaks; WriterLog.i("Sheets: page break preview ${if (breaks) "on" else "off"}") }
                            RibbonButton("Zoom to selection", w, R.drawable.ic_zoom_in) { view.fitTo = g ?: l.row(cr).takeIf { it >= 0 }?.let { rr -> l.col(cc).takeIf { it >= 0 }?.let { SheetRange(rr, it, rr, it) } } }
                            RibbonDivider(w)
                            RibbonButton("Formula bar", w, R.drawable.ic_functions, on = showBar) { showBar = !showBar }
                            RibbonButton("Sheet tabs", w, R.drawable.ic_tab, on = showTabs) { showTabs = !showTabs }
                            RibbonButton("Search", w, R.drawable.ic_search) { searching = true }
                        }
                        else -> {
                            // Home: the cell's looks, one tool each
                            if (b.kind != "csv") {
                                RibbonButton("", w, R.drawable.ic_format_bold, on = now.bold) { look { st, _ -> st.copy(bold = !now.bold) } }
                                RibbonButton("", w, R.drawable.ic_format_italic, on = now.italic) { look { st, _ -> st.copy(italic = !now.italic) } }
                                RibbonButton("", w, R.drawable.ic_format_underlined, on = now.underline) { look { st, _ -> st.copy(underline = !now.underline) } }
                                RibbonButton("", w, R.drawable.ic_format_strikethrough, on = now.strike) { look { st, _ -> st.copy(strike = !now.strike) } }
                                RibbonDivider(w)
                                Swatches(w, R.drawable.ic_format_color_text, "Text colour", TEXT_COLOURS) { c ->
                                    look { st, _ -> st.copy(color = c?.let { (it.value shr 32).toInt() }) }
                                }
                                Swatches(w, R.drawable.ic_format_color_fill, "Fill", CELL_FILLS) { c ->
                                    look { st, _ -> st.copy(fill = c?.let { (it.value shr 32).toInt() }) }
                                }
                                opener("Borders", R.drawable.ic_border_outer)
                                opener("Cell styles", R.drawable.ic_style)
                                if (b.kind != "csv") opener("Format as table", R.drawable.ic_table)
                                if (b.kind != "csv") opener("Conditional formatting", R.drawable.ic_highlighter)
                                // Excel's Format › Lock Cell: off, the cell can be typed in while the sheet is protected
                                if (b.kind != "csv") RibbonButton("Lock cell", w, R.drawable.ic_lock, on = now.locked) { look { st, _ -> st.copy(locked = !now.locked) } }
                                RibbonDivider(w)
                                RibbonButton("", w, R.drawable.ic_align_left, on = now.align == SheetAlign.Left) { look { st, _ -> st.copy(align = if (now.align == SheetAlign.Left) SheetAlign.General else SheetAlign.Left) } }
                                RibbonButton("", w, R.drawable.ic_align_center, on = now.align == SheetAlign.Center) { look { st, _ -> st.copy(align = if (now.align == SheetAlign.Center) SheetAlign.General else SheetAlign.Center) } }
                                RibbonButton("", w, R.drawable.ic_align_right, on = now.align == SheetAlign.Right) { look { st, _ -> st.copy(align = if (now.align == SheetAlign.Right) SheetAlign.General else SheetAlign.Right) } }
                                RibbonButton("Wrap", w, R.drawable.ic_wrap_text, on = now.wrap) { look { st, _ -> st.copy(wrap = !now.wrap) } }
                                // Excel's Alignment group: top, middle, bottom; indent; orientation
                                RibbonButton("", w, R.drawable.ic_vertical_align_top, on = now.valign == SheetVAlign.Top) { look { st, _ -> st.copy(valign = SheetVAlign.Top) } }
                                RibbonButton("", w, R.drawable.ic_vertical_align_center, on = now.valign == SheetVAlign.Center) { look { st, _ -> st.copy(valign = SheetVAlign.Center) } }
                                RibbonButton("", w, R.drawable.ic_vertical_align_bottom, on = now.valign == SheetVAlign.Bottom) { look { st, _ -> st.copy(valign = SheetVAlign.Bottom) } }
                                RibbonButton("", w, R.drawable.ic_format_indent_decrease, enabled = now.indent > 0) { look { st, _ -> st.copy(indent = maxOf(0, st.indent - 1)) } }
                                RibbonButton("", w, R.drawable.ic_format_indent_increase) { look { st, _ -> st.copy(indent = minOf(15, st.indent + 1), align = if (st.align == SheetAlign.Right || st.align == SheetAlign.Left) st.align else SheetAlign.Left) } }
                                Box { var open by remember { mutableStateOf(false) }
                                    RibbonButton("Orientation", w, R.drawable.ic_rotate_left, on = now.rotation != 0) { open = true }
                                    DropdownMenu(open, { open = false }) {
                                        listOf("Horizontal" to 0, "Angle counterclockwise" to 45, "Angle clockwise" to 135, "Rotate text up" to 90, "Rotate text down" to 180).forEach { (name, r) ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = { open = false; look { st, _ -> st.copy(rotation = r) } },
                                                trailingIcon = { if (now.rotation == r) Icon(painterResource(R.drawable.ic_check), null) }) } } }
                                // Excel's Fill ▸ Down and Right: the chosen cells' first row (or column) carried through them, as the fill handle carries it
                                if (g != null && (g.r2 > g.r1 || g.c2 > g.c1)) {
                                    if (g.r2 > g.r1) RibbonButton("Fill down", w, R.drawable.ic_keyboard_arrow_down) { fillCells(SheetRange(g.r1, g.c1, g.r1, g.c2), g) }
                                    if (g.c2 > g.c1) RibbonButton("Fill right", w, R.drawable.ic_chevron_right) { fillCells(SheetRange(g.r1, g.c1, g.r2, g.c1), g) }
                                    RibbonButton("Series", w, R.drawable.ic_format_list_numbered) { stepping = g }
                                }
                                // Excel's Merge & Centre: the chosen cells one cell; a merged one chosen: Unmerge
                                s.merge(cr, cc)?.let { m -> RibbonButton("Unmerge", w, R.drawable.ic_cell_merge) { merge(m, false) } }
                                    ?: g?.takeIf { it.r2 > it.r1 || it.c2 > it.c1 }?.let { d -> RibbonButton("Merge & centre", w, R.drawable.ic_cell_merge) { merge(SheetRange(l.rows[d.r1], l.cols[d.c1], l.rows[d.r2], l.cols[d.c2]), true) } }
                                RibbonDivider(w)
                                opener("Number", R.drawable.ic_functions)
                                Tool(R.drawable.ic_decimal_decrease, "Fewer decimals", w) { look { st, _ -> st.copy(format = SheetLook.decimals(st.format, -1)) } }
                                Tool(R.drawable.ic_decimal_increase, "More decimals", w) { look { st, _ -> st.copy(format = SheetLook.decimals(st.format, 1)) } }
                                RibbonDivider(w)
                                // the font, then its size, as Words' Home (17 Oct)
                                FontButton(now.font, w) { group = null; fontChosen = null; fontStrip = now.font.orEmpty() }
                                SizeStepper(now.size.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }, w, now.size > 6f, now.size < 72f,
                                    { look { st, _ -> st.copy(size = maxOf(6f, st.size - 1)) } }, { look { st, _ -> st.copy(size = minOf(72f, st.size + 1)) } })
                                RibbonDivider(w)
                                RibbonButton("Clear formatting", w, R.drawable.ic_format_clear) { look { _, _ -> b.styles[0] } }
                                // AutoSum on Home too, as Excel has it
                                RibbonButton("AutoSum", w, R.drawable.ic_functions) { autoFunction("SUM") }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // a keyboard's shortcuts (a tablet's or a desktop's, asked 16 Oct): the screen holds the keys while no cell is typed in
    val keys = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing, draft == null, searching) { if (draft == null && !searching) runCatching { keys.requestFocus() } }
    Box(Modifier.fillMaxSize().focusRequester(keys).focusable().onPreviewKeyEvent { k ->
        if (k.type != KeyEventType.KeyDown || sheet == null) return@onPreviewKeyEvent false
        // Delete empties the chosen cells, as Excel's (and Slides' Delete takes what is chosen; 17 Oct)
        if (!k.isCtrlPressed) return@onPreviewKeyEvent if (k.key == Key.Delete && editing && draft == null) { clearCells(); true } else false
        when {
            k.key == Key.F -> { searching = true; true }
            draft != null -> false // the cell bar's own copy, paste and undo
            k.key == Key.C -> { copyCells(cut = false); true }
            !editing -> false
            k.key == Key.S -> { if (!saving) save(); true }
            k.key == Key.Y || (k.key == Key.Z && k.isShiftPressed) -> { undoOrRedo(true); true }
            k.key == Key.Z -> { undoOrRedo(false); true }
            k.key == Key.X -> { copyCells(cut = true); true }
            k.key == Key.V -> { paste(); true }
            else -> false
        }
    }) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding()) {
                if (searching) SearchBar(query, { query = it }, found.size, current,
                    everywhere = everywhere.takeIf { (book?.tabs?.count { !it.hidden } ?: 0) > 1 }, hits = hits.size,
                    toggle = { if (!everywhere) { everywhere = true; showHits = true } else if (!showHits) showHits = true else { everywhere = false; showHits = false }; WriterLog.i("Sheets: search in all sheets $everywhere") },
                    move = { d -> if (found.isNotEmpty()) { current = (current + d).mod(found.size); found[current].let { view.row = Sheet.row(it); view.col = Sheet.col(it); view.reveal++ } } },
                    replace = if (editing) { with, all -> replaceFound(if (all) found else listOfNotNull(found.getOrNull(current)), with) } else null,
                    close = { searching = false; query = "" })
                else Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::leave) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                    val unsaved = version >= 0 && history.unsaved != 0
                    Column(Modifier.weight(1f)) {
                        Text((if (unsaved) "• " else "") + (file?.name ?: "New spreadsheet"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (saving) "Saving…" else if (editing) "Editing" else "View only", style = MaterialTheme.typography.labelSmall, color = islandContentColor().copy(alpha = 0.7f))
                    }
                    val b0 = book
                    if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700 && b0 != null && index in b0.tabs.indices) {
                        val shownTabs = b0.tabs.count { !it.hidden }
                        TopInfo(listOfNotNull(if (shownTabs > 1) "${b0.tabs[index].name} (sheet ${b0.tabs.take(index + 1).count { !it.hidden }} of $shownTabs)" else b0.tabs[index].name,
                            remember(sheet, version) { sheet?.let { sh -> var n = 0; sh.cells.forEach { _, c -> if (c.kind != SheetKind.Empty) n++ }; "%,d cells filled".format(n) } },
                            if (editing) saveState(saving, unsaved || draft != null) else null, file?.extension?.let { ".$it" }), islandContentColor())
                    }
                    if (editing) {
                        IconButton(onClick = { undoOrRedo(false) }, enabled = version >= 0 && history.canUndo) { Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo") }
                        IconButton(onClick = { undoOrRedo(true) }, enabled = version >= 0 && history.canRedo) { Icon(painterResource(R.drawable.ic_redo), contentDescription = "Redo") }
                        IconButton(onClick = { save() }, enabled = (unsaved || draft != null) && !saving) { Icon(painterResource(R.drawable.ic_save), contentDescription = "Save") }
                        IconButton(onClick = { commit(false); focusManager.clearFocus(); editing = false; WriterLog.i("Sheets: editing done") }) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Done") }
                    } else {
                        if (sheet != null) IconButton(onClick = { searching = true }) { Icon(painterResource(R.drawable.ic_search), contentDescription = "Search") }
                    }
                    if (!editing) 
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val f = file
                            if (f != null) {
                                DropdownMenuItem(text = { Text("Open in another app") }, leadingIcon = { Icon(painterResource(R.drawable.ic_open_with), null) },
                                    onClick = { menu = false; WriterLog.i("Sheets: open in another app"); openElsewhere(context, f) })
                                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                                    onClick = { menu = false; WriterLog.i("Sheets: share"); shareDriveFile(context, f) })
                            }
                            if (f != null && sheet != null) {
                                DropdownMenuItem(text = { Text("Export PDF") }, leadingIcon = { Icon(painterResource(R.drawable.ic_file), null) }, onClick = { menu = false; exportPdf() })
                                DropdownMenuItem(text = { Text("Print") }, onClick = { menu = false; printIt() })
                                DropdownMenuItem(text = { Text("Versions") }, leadingIcon = { Icon(painterResource(R.drawable.ic_history), null) }, onClick = { menu = false; showVersions = true })
                            }
                            DropdownMenuItem(text = { Text("Show formulas") }, leadingIcon = { Icon(painterResource(R.drawable.ic_functions), null) },
                                trailingIcon = { if (formulas) Icon(painterResource(R.drawable.ic_check), null) },
                                onClick = { menu = false; formulas = !formulas; WriterLog.i("Sheets: formulas ${if (formulas) "shown" else "hidden"}") })
                            DropdownMenuItem(text = { Text("About this file") }, onClick = { menu = false; about = true })
                            DropdownMenuItem(text = { Text("Send log") }, onClick = { menu = false; WriterLog.export(context)?.let { shareDriveFile(context, it) } })
                        }
                    }
                    // Edit at the far right, where Done is while editing — the same place in Words, Tables and Slides
                    if (!editing && sheet != null && file?.canWrite() == true && book?.kind in setOf("xlsx", "ods", "csv"))
                        IconButton(onClick = { editing = true; WriterLog.i("Sheets: editing") }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                }
                sheet?.takeIf { showBar || editing && draft != null }?.let { s ->
                    if (editing) {
                        val (r, c) = chosen(s)
                        EditBar(Sheets.colName(c) + (r + 1), version.let { SheetInput.text(s[r, c], lookAt(s, r, c).second, book!!.format) }, draft, barFocus,
                            change = { draft = it }, start = ::startTyping, commit = { commit(true) },
                            cancel = { draft = null; focusManager.clearFocus() })
                    } else CellBar(s, view, ::follow) { text -> copy(context, "Cell", text); WriterLog.i("Sheets: a cell copied") }
                }
                if (pins.isNotEmpty() || filters.isNotEmpty() || sort != null) ToolChips(view)
            }
        }
        // the ribbon at the top while a mouse is in use (or as Settings say): under the cell bar, as a PC's Excel has it
        run {
            val rs = sheet; val rb = book; val rl = layout
            androidx.compose.animation.AnimatedVisibility(OfficeUi.top && rs != null && rb != null && rl != null && draft == null && !searching && fontStrip == null && artStrip == null && (header != null || view.range != null || editing),
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
                if (rs != null && rb != null && rl != null) Ribbon(rs, rb, rl)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            val s = sheet; val b = book; val l = layout
            when {
                searching && everywhere && showHits && b != null -> HitsList(b, hits, searched, b.tabs.count { !it.hidden }, query) { h ->
                    WriterLog.i("Sheets: a find in sheet ${h.sheet + 1} chosen")
                    views.getOrPut(h.sheet) { SheetView() }.let { v -> v.row = Sheet.row(h.key); v.col = Sheet.col(h.key); v.reveal++ }
                    pending = h.key; showHits = false; failed = null; index = h.sheet
                }
                failed != null -> Text(failed!!, color = Color.Black, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                s != null && b != null && l != null -> {
                    SheetGrid(s, b.styles, l, view, SheetMarks(found.toHashSet() + traced, found.getOrNull(current) ?: -1L, allNotes, breaks), formulas, version,
                        column = { col, _ -> header = 'c' to col; group = null }, row = { r, _ -> header = 'r' to r; group = null },
                        pick = { r, c ->
                            header = null
                            val d = draft
                            when {
                                // a formula being typed: the tapped cell's name goes in at the cursor (Excel's pointing)
                                d != null && d.text.startsWith("=") -> {
                                    val ref = Sheets.colName(c) + (r + 1)
                                    draft = TextFieldValue(d.text.substring(0, d.selection.min) + ref + d.text.substring(d.selection.max), androidx.compose.ui.text.TextRange(d.selection.min + ref.length))
                                    true
                                }
                                // what was typed goes in, then the tap chooses; the keyboard goes (asked 16 Oct: a tap chooses, the bar edits)
                                d != null -> { commit(false); focusManager.clearFocus(); false }
                                else -> false
                            }
                        }, filled = if (editing && draft == null) ::fillCells else null, menu = { at -> if (draft != null) { commit(false); focusManager.clearFocus() }; header = null; group = null; cellMenu = at },
                        figures = if (editing && b.kind != "csv") { d -> view.figure = d?.ordinal ?: -1; if (d != null) { header = null; view.range = null; group = null; WriterLog.i("Sheets: a drawing chosen") } } else null,
                        dropped = if (editing && b.kind != "csv") { d, r, c -> figureStep(d, SheetDrawings.Move(r, c)) } else null)
                    // a right-click's menu, where it was clicked: the cells' Cut, Copy, Paste, Clear, and their rows and columns
                    cellMenu?.let { at -> OfficeMenu(at, { cellMenu = null }) {
                            @Composable fun item(label: String, icon: Int, action: () -> Unit) = OfficeMenuItem(label, icon) { cellMenu = null; WriterLog.i("Sheets: menu › $label"); action() }
                            val g = view.range
                            val (cr, cc) = chosen(s)
                            val rows = g?.let { l.rows[it.r1] to it.r2 - it.r1 + 1 } ?: (cr to 1)
                            val cols = g?.let { l.cols[it.c1] to it.c2 - it.c1 + 1 } ?: (cc to 1)
                            if (editing) item("Cut", R.drawable.ic_content_cut) { copyCells(cut = true) }
                            item("Copy", R.drawable.ic_copy) { copyCells(cut = false) }
                            if (editing) {
                                item("Paste", R.drawable.ic_content_paste) { paste() }
                                item("Clear", R.drawable.ic_delete) { clearCells() }
                                HorizontalDivider()
                                item("Insert row above", R.drawable.ic_add_row_above) { shift(true, rows.first, rows.second) }
                                item("Insert row below", R.drawable.ic_add_row_below) { shift(true, rows.first + rows.second, rows.second) }
                                item(if (rows.second > 1) "Delete ${rows.second} rows" else "Delete row", R.drawable.ic_delete_row) { shift(true, rows.first, -rows.second) }
                                HorizontalDivider()
                                item("Insert column left", R.drawable.ic_add_column_left) { shift(false, cols.first, cols.second) }
                                item("Insert column right", R.drawable.ic_add_column_right) { shift(false, cols.first + cols.second, cols.second) }
                                item(if (cols.second > 1) "Delete ${cols.second} columns" else "Delete column", R.drawable.ic_delete_column) { shift(false, cols.first, -cols.second) }
                                HorizontalDivider()
                                // Excel's Insert ▸ Shift cells down / right, Delete ▸ Shift cells up / left: the chosen cells only
                                val cells = SheetRange(rows.first, cols.first, rows.first + rows.second - 1, cols.first + cols.second - 1)
                                item("Insert cells, shift down", R.drawable.ic_add_row_above) { shiftCells(cells, rows = true, insert = true) }
                                item("Insert cells, shift right", R.drawable.ic_add_column_left) { shiftCells(cells, rows = false, insert = true) }
                                item("Delete cells, shift up", R.drawable.ic_delete_row) { shiftCells(cells, rows = true, insert = false) }
                                item("Delete cells, shift left", R.drawable.ic_delete_column) { shiftCells(cells, rows = false, insert = false) }
                            }
                    } }
                }
                else -> Text("Opening…", color = Color.DarkGray, modifier = Modifier.align(Alignment.Center))
            }
        }
        val rs = sheet; val rb = book; val rl = layout
        // The ribbon slides down and up, as Writer's; Home › Font's gallery takes its place (asked 16 Oct, as Page numbers).
        androidx.compose.animation.AnimatedVisibility(!OfficeUi.top && rs != null && rb != null && rl != null && draft == null && !searching && fontStrip == null && artStrip == null && (header != null || view.range != null || editing),
            enter = SLIDE_IN, exit = SLIDE_OUT) { if (rs != null && rb != null && rl != null) Ribbon(rs, rb, rl) }
        // A drop-down list's cell: its values to tap, as Excel's arrow on the cell opens them
        val listed = rs?.takeIf { editing && !searching }?.let { s -> chosen(s).let { (r, c) -> s.listAt(r, c) } }?.items.orEmpty()
        androidx.compose.animation.AnimatedVisibility(listed.isNotEmpty() && draft?.text.orEmpty().startsWith("=").not(), enter = SLIDE_IN, exit = SLIDE_OUT) {
            val w = islandContentColor()
            Surface(color = islandColor(), contentColor = w, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listed.size) { k -> val v = listed[k]
                        Text(v, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(w.copy(alpha = 0.08f)).clickable {
                            val s = sheet ?: return@clickable; val b = book ?: return@clickable; val (r, c) = chosen(s); val (look, format) = lookAt(s, r, c)
                            draft = null; history.apply(b, index, mapOf(Sheet.key(r, c) to SheetInput.cell(v, s[r, c], look, format, b.format))); version++
                            WriterLog.i("Sheets: a value from the drop-down list")
                        }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 16.dp)) }
                }
            }
        }
        // Typing in the cell bar: the functions for the letters typed (asked 16 Oct), or what the one the cursor is in takes.
        val d = draft
        val fns = remember(d?.text, d?.selection) { d?.let { SheetFunctions.matching(it.text, it.selection.min) }.orEmpty() }
        val within = remember(d?.text, d?.selection) { d?.takeIf { fns.isEmpty() }?.let { SheetFunctions.inside(it.text, it.selection.min) } }
        androidx.compose.animation.AnimatedVisibility(d != null && (fns.isNotEmpty() || within != null), enter = SLIDE_IN, exit = SLIDE_OUT) {
            val w = islandContentColor()
            Surface(color = islandColor(), contentColor = w, modifier = Modifier.fillMaxWidth()) {
                if (within != null) Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(within.name) }; append("(${within.args})   ")
                    withStyle(SpanStyle(color = w.copy(alpha = 0.7f))) { append(within.says) }
                }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                else {
                    val list = remember(fns.firstOrNull()?.name, fns.size) { androidx.compose.foundation.lazy.LazyListState() }
                    androidx.compose.foundation.lazy.LazyRow(state = list, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(fns.size, key = { fns[it].name }) { i ->
                            val f = fns[i]
                            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(w.copy(alpha = 0.08f)).clickable {
                                val cur = draft ?: return@clickable
                                val (t, at) = SheetFunctions.insert(cur.text, cur.selection.min, f)
                                draft = TextFieldValue(t, androidx.compose.ui.text.TextRange(at))
                                TextEditorStore(context).usedFunction(f.name); WriterLog.i("Sheets: function ${f.name} from the row")
                            }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(f.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(f.says, style = MaterialTheme.typography.labelSmall, color = w.copy(alpha = 0.7f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        // Typing in a cell: Words' bar on the keyboard (Words' [KeyboardBar]), its looks on the cell, as Excel's phone app
        // has them; ⋯ puts the typing in and brings the ribbon back
        val said = rememberDictation { t -> draft?.let { v -> val at = v.selection.min
            draft = TextFieldValue(v.text.substring(0, at) + t + v.text.substring(v.selection.max), androidx.compose.ui.text.TextRange(at + t.length)) } }
        androidx.compose.animation.AnimatedVisibility(d != null && rs != null && rb != null && rl != null, enter = SLIDE_IN, exit = SLIDE_OUT) {
            val s = rs ?: return@AnimatedVisibility; val b = rb ?: return@AnimatedVisibility; val l = rl ?: return@AnimatedVisibility
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            val (cr, cc) = chosen(s)
            val now = version.let { (s[cr, cc]?.fileStyle ?: (s.rowStyles[cr] ?: s.colStyles[cc] ?: 0)).let { b.styles.getOrNull(it) } ?: SheetStyle() }
            fun look(change: (SheetStyle) -> SheetStyle) {
                val changed = SheetLook.change(b, s, targets(s, l)) { st, _ -> change(st) }
                if (changed.isNotEmpty()) { history.apply(b, index, changed); version++; WriterLog.i("Sheets: ${changed.size} cells formatted from the keyboard bar") }
            }
            Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
                KeyboardBar(islandContentColor(), now.bold, now.italic, now.underline,
                    { look { it.copy(bold = !now.bold) } }, { look { it.copy(italic = !now.italic) } }, { look { it.copy(underline = !now.underline) } },
                    { c -> look { it.copy(fill = c?.let { (it.value shr 32).toInt() }) } },
                    { said?.invoke() ?: run { message = "This phone has no voice typing" } }, { commit(false); focusManager.clearFocus() }, { keyboard?.hide() })
            }
        }
        // Insert › WordArt: Words' strip of looks and bends; OK asks for its words
        androidx.compose.animation.AnimatedVisibility(artStrip != null, enter = SLIDE_IN, exit = SLIDE_OUT) {
            DesignGalleryStrip(DesignGallery.WordArt, artStrip, cancel = { artStrip = null }, ok = { artWords = artStrip; artStrip = null }, pick = { ch ->
                artStrip = artStrip?.let { (st, bd) -> (ch as? ArtStyle ?: st) to (ch as? ArtBend ?: bd) } },
                glow = book?.kind != "ods", modifier = Modifier.consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.navigationBars))
        }
        androidx.compose.animation.AnimatedVisibility(fontStrip != null && rs != null && rb != null && rl != null, enter = SLIDE_IN, exit = SLIDE_OUT) {
            // a font tapped shows on the cells at once, in place of the one tapped before; ✕ puts the cells back as they were
            fun unshow() { if (fontChosen != null) { rb?.let { history.undo(it) }; version++; fontChosen = null } }
            val fonts = remember { (rb?.styles.orEmpty().mapNotNull { it.font } + listOf("Calibri", "Cambria", "Arial", "Times New Roman", "Courier New")).distinct() }
            DesignGalleryStrip(DesignGallery.Font, fontChosen, cancel = { unshow(); fontStrip = null }, ok = { fontStrip = null; fontChosen = null }, pick = { c ->
                val s = rs ?: return@DesignGalleryStrip; val b = rb ?: return@DesignGalleryStrip; val l = rl ?: return@DesignGalleryStrip
                unshow()
                val changed = SheetLook.change(b, s, targets(s, l)) { st, _ -> st.copy(font = (c as FontChoice).name) }
                if (changed.isNotEmpty()) { history.apply(b, index, changed); version++; fontChosen = c as FontChoice; WriterLog.i("Sheets: font on ${changed.size} cells") }
            }, modifier = Modifier.consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.navigationBars), fonts = fonts, now = fontStrip)
        }
        book?.takeIf { showTabs }?.let { b ->
            SheetTabs(b, index, sheet, add = if (editing && b.kind != "csv") ({
                val name = generateSequence(b.tabs.size + 1) { it + 1 }.map { "Sheet$it" }.first { n -> b.tabs.none { it.name.equals(n, true) } }
                sheetStep(add = name)
            }) else null, tools = if (editing && b.kind != "csv") ({ header = 's' to index; group = null }) else null) { i ->
                WriterLog.i("Sheets: tab ${i + 1} of ${b.tabs.size}"); failed = null; header = null; index = i
            }
        }
    }
    // under the top bar and the cell bar, as Words' and Slides' messages
    Box(Modifier.fillMaxSize().statusBarsPadding()) { OfficeMessage(message, top = 108.dp) { message = null } }
    }
    val s = sheet
    filtering?.let { col -> if (s != null) FilterDialog(s, col, view) { filtering = null } }
    if (officeSettings) OfficeSettingsDialog { officeSettings = false }
    // File › Save as and Rename: the new name, beside the file
    fileNaming?.let { how -> file?.let { f ->
        var typed by remember(how) { mutableStateOf(if (how == "as") "${f.nameWithoutExtension} (2)" else f.nameWithoutExtension) }
        AlertDialog(onDismissRequest = { fileNaming = null }, title = { Text(if (how == "as") "Save as" else "Rename") },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("File name") }, suffix = { Text(".${f.extension}") }) },
            confirmButton = { TextButton(onClick = { val name = typed.trim().removeSuffix(".${f.extension}"); val target = File(f.parentFile, "$name.${f.extension}")
                when {
                    name.isEmpty() || '/' in name -> message = "That is not a file name."
                    target == f -> fileNaming = null
                    target.exists() -> message = "${target.name} is already there. Choose another name."
                    how == "as" -> { fileNaming = null; saveAs(target) }
                    else -> { fileNaming = null; renameFile(target) }
                } }) { Text(if (how == "as") "Save" else "Rename") } },
            dismissButton = { TextButton(onClick = { fileNaming = null }) { Text("Cancel") } }) } }
    if (naming) {
        var typed by remember { mutableStateOf("New spreadsheet") }
        val dir = remember { TextEditorStore(context).saveDirectory() }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text("Save as") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("File name") }, suffix = { Text(".xlsx") })
                    Text("In ${TextEditorStore(context).saveFolder} (Settings › Text editor).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = {
                val name = typed.trim().removeSuffix(".xlsx")
                val target = File(dir, "$name.xlsx")
                when {
                    name.isEmpty() || '/' in name -> message = "That is not a file name."
                    target.exists() -> message = "${target.name} is already there. Choose another name."
                    else -> { naming = false; dir.mkdirs(); file = target; WriterLog.i("Sheets: new spreadsheet named"); save() }
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } })
    }
    if (renamingSheet) book?.let { b ->
        var typed by remember { mutableStateOf(b.tabs[index].name) }
        val problem = SheetBook.valid(typed.trim(), b.tabs.map { it.name }.filterIndexed { i, _ -> i != index })
        AlertDialog(onDismissRequest = { renamingSheet = false }, title = { Text("Rename the sheet") },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, isError = problem != null, supportingText = { problem?.let { Text(it) } }) },
            confirmButton = { TextButton(enabled = problem == null, onClick = { renamingSheet = false; if (typed.trim() != b.tabs[index].name) sheetStep(rename = typed.trim()) }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renamingSheet = false }) { Text("Cancel") } })
    }
    if (deletingSheet) book?.let { b ->
        AlertDialog(onDismissRequest = { deletingSheet = false }, title = { Text("Delete ${b.tabs[index].name}?") },
            text = { Text("Its cells go, and formulas that read them show #REF!. Undo brings it back.") },
            confirmButton = { TextButton(onClick = { deletingSheet = false; sheetStep(delete = true) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deletingSheet = false }) { Text("Cancel") } })
    }
    if (showVersions) file?.let { f -> VersionsDialog(f, "Undo goes back", { showVersions = false }) { v -> showVersions = false; editing = true; openVersion(v) } }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false },
        title = { Text("Save the changes?") },
        text = { Text("${kotlin.math.abs(history.unsaved)} change${if (kotlin.math.abs(history.unsaved) == 1) "" else "s"} to ${file?.name ?: "this spreadsheet"} are not saved.") },
        confirmButton = { TextButton(onClick = { leaving = false; save { back() } }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { leaving = false; file?.let { sheetDraft(context, it).delete() }; WriterLog.i("Sheets: left without saving"); back() }) { Text("Don't save") }
                TextButton(onClick = { leaving = false }) { Text("Cancel") }
            }
        })
    // Insert › Link (Words' dialog): a web page or another sheet (its A1); the words shown typed in the cell first, the link a change to the file
    if (linking) sheet?.let { s -> val (r, c) = chosen(s); val cell = s[r, c]; val tabs = book?.tabs.orEmpty()
        fun put(url: String?) { val i = index
            fileStep(if (url == null) "link taken away" else "link put on", { baked, next, b -> when (b.kind) {
                "xlsx" -> SheetLinks.xlsx(baked, next, b.parts.getValue("$i"), r, c, url); "ods" -> SheetLinks.ods(baked, next, i, r, c, url)
                else -> error("A CSV file keeps no links: save it as a workbook first.") } }) }
        val now = cell?.link?.let { l -> if (l.startsWith("#")) tabs.indexOfFirst { it.name.equals(l.drop(1).substringBefore('!').trim('\''), true) }.takeIf { it >= 0 }?.let { "#$it" } else l }
        LinkDialog(true, now, cell?.text, cell?.link != null, { linking = false }, { linking = false; put(null) }, slides = tabs.map { it.name }, place = "Sheet in this workbook") { text, to ->
            linking = false; val b = book ?: return@LinkDialog
            if (text.isNotBlank() && text != cell?.text) { val (look, format) = lookAt(s, r, c); history.apply(b, index, mapOf(Sheet.key(r, c) to SheetInput.cell(text, cell, look, format, b.format))); version++ }
            put(if (to.startsWith("#")) to.drop(1).toIntOrNull()?.let { k -> tabs.getOrNull(k)?.name }?.let { n -> "#'${n.replace("'", "''")}'!A1" } ?: to else to)
        } }
    // Data › Text to columns: Excel's separators; the pieces typed into the cells to the right, as if typed there (numbers stay numbers)
    splitting?.let { d -> val s = sheet; val b = book
        AlertDialog(onDismissRequest = { splitting = null }, title = { Text("Text to columns") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Cut each cell's words at:")
                listOf("Comma" to ",", "Semicolon" to ";", "Tab" to "\t", "Space" to " ", "Slash" to "/", "Dash" to "-").forEach { (label, by) ->
                    TextButton(onClick = { splitting = null; if (s == null || b == null) return@TextButton
                        val changes = LinkedHashMap<Long, SheetCell>()
                        for (r in d.r1..d.r2) { val cell = s[r, d.c1] ?: continue; if (cell.formula != null || cell.kind != SheetKind.Text) continue
                            SheetInput.split(cell.text, by).takeIf { it.size > 1 }?.forEachIndexed { k, piece -> val c = d.c1 + k; val (look, format) = lookAt(s, r, c)
                                changes[Sheet.key(r, c)] = SheetInput.cell(piece, s[r, c], look, format, b.format) } }
                        if (changes.isEmpty()) message = "Nothing to cut at the ${label.lowercase()}" else { history.apply(b, index, changes); version++; WriterLog.i("Sheets: text to columns, ${changes.size} cells") }
                    }) { Text(label) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { splitting = null }) { Text("Cancel") } }) }
    // Data › Remove duplicates: Excel's question (headers?), then the rows alike after the first gone, the rest moved up (one step of Undo)
    deduping?.let { d -> val s = sheet; val b = book
        var headers by remember(d) { mutableStateOf(true) }
        AlertDialog(onDismissRequest = { deduping = null }, title = { Text("Remove duplicates") },
            text = { Column { Text("Rows ${d.r1 + 1}–${d.r2 + 1}, columns ${Sheets.colName(d.c1)}–${Sheets.colName(d.c2)}: a row is a duplicate when every cell is alike.")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { headers = !headers }) { Checkbox(headers, { headers = it }); Text("The first row is headers") } } },
            confirmButton = { TextButton(onClick = { deduping = null; if (s == null || b == null) return@TextButton
                if ((d.r1..d.r2).any { r -> (d.c1..d.c2).any { c -> s[r, c]?.formula != null } }) { message = "These cells hold formulas: remove duplicates works on values"; return@TextButton }
                val rows = (d.r1..d.r2).map { r -> (d.c1..d.c2).map { c -> s[r, c]?.text.orEmpty() } }
                val kept = SheetInput.unique(rows, headers)
                if (kept.size == rows.size) { message = "No duplicates"; return@TextButton }
                val changes = LinkedHashMap<Long, SheetCell>()
                for (k in rows.indices) for (c in d.c1..d.c2) { val r = d.r1 + k; val was = s[r, c]
                    changes[Sheet.key(r, c)] = kept.getOrNull(k)?.let { from -> s[d.r1 + from, c] } ?: SheetCell("", SheetKind.Empty, style = was?.style ?: 0) }
                history.apply(b, index, changes); version++; message = "Removed ${rows.size - kept.size} duplicates, ${kept.size - (if (headers) 1 else 0)} left"
                WriterLog.i("Sheets: ${rows.size - kept.size} duplicates removed") }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deduping = null }) { Text("Cancel") } }) }
    // Home › Series: Excel's Fill ▸ Series — its types and step value (the stop: the chosen cells' end)
    stepping?.let { g -> val first = layout?.let { l -> sheet?.cells?.get(Sheet.key(l.rows[g.r1], l.cols[g.c1])) }
        val date = first?.let { c -> book?.let { b -> b.format.isDate(c.format ?: sheet?.look(c.style, b.styles)?.format ?: "General") } } == true
        var type by remember(g) { mutableStateOf(if (date) "Day" else "Linear") }
        var step by remember(g) { mutableStateOf("1") }
        AlertDialog(onDismissRequest = { stepping = null }, title = { Text("Series") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (g.r2 > g.r1) "Down each column, from its first cell." else "Along the row, from its first cell.")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Linear", "Growth") + if (date) listOf("Day", "Weekday", "Month", "Year") else emptyList()).forEach { t ->
                        androidx.compose.material3.FilterChip(type == t, { type = t }, { Text(t) }) } }
                OutlinedTextField(step, { step = it }, label = { Text("Step value") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) } },
            confirmButton = { TextButton(enabled = step.replace(',', '.').toDoubleOrNull() != null, onClick = { stepping = null
                fillSeries(g, type, step.replace(',', '.').toDouble()) }) { Text("Fill") } },
            dismissButton = { TextButton(onClick = { stepping = null }) { Text("Cancel") } }) }
    // Page layout › Header or Footer › Custom…: Excel's three parts, the fields put in where the cursor is
    footing?.let { kind -> sheet?.let { s -> val now = if (kind == "header") s.print.header else s.print.footer
        val parts = remember(kind) { androidx.compose.runtime.mutableStateListOf(*SheetHeaderFooter.parts(now).map { TextFieldValue(it, androidx.compose.ui.text.TextRange(it.length)) }.toTypedArray()) }
        var focused by remember(kind) { mutableStateOf(1) }
        AlertDialog(onDismissRequest = { footing = null }, title = { Text(if (kind == "header") "Header" else "Footer") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Left", "Centre", "Right").forEachIndexed { k, label -> OutlinedTextField(parts[k], { parts[k] = it }, label = { Text(label) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focused = k }) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetHeaderFooter.FIELDS.forEach { (field, _) -> androidx.compose.material3.AssistChip({ val t = parts[focused]; val at = t.selection.min
                        parts[focused] = TextFieldValue(t.text.substring(0, at) + field + t.text.substring(t.selection.max), androidx.compose.ui.text.TextRange(at + field.length)) },
                        { Text(field.trim('[', ']')) }) } } } },
            confirmButton = { TextButton(onClick = { footing = null; val code = SheetHeaderFooter.code(parts.map { it.text })
                printStep(if (kind == "header") s.print.copy(header = code) else s.print.copy(footer = code)) }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { footing = null }) { Text("Cancel") } }) } }
    // the sheet tab › Protect (a password twice, or none) or Unprotect (its password)
    if (protecting) sheet?.let { s -> val unprotect = s.protected
        var password by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
        val hidden = androidx.compose.ui.text.input.PasswordVisualTransformation()
        val keys = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
        AlertDialog(onDismissRequest = { protecting = false }, title = { Text(if (unprotect) "Unprotect sheet" else "Protect sheet") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (unprotect) "This sheet's password:" else "Its locked cells (all, unless Home › Lock cell is off) can't be changed. A password is optional.")
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = hidden, keyboardOptions = keys)
                if (!unprotect && password.isNotEmpty()) OutlinedTextField(again, { again = it }, label = { Text("Password again") }, singleLine = true, visualTransformation = hidden, keyboardOptions = keys,
                    isError = again.isNotEmpty() && again != password) } },
            confirmButton = { TextButton(enabled = unprotect && password.isNotEmpty() || !unprotect && (password.isEmpty() || again == password), onClick = { protecting = false
                protectSheet(!unprotect, password) }) { Text(if (unprotect) "Unprotect" else "Protect") } },
            dismissButton = { TextButton(onClick = { protecting = false }) { Text("Cancel") } }) }
    if (protectingBook) book?.let { b -> val unprotect = b.structureLocked
        var password by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
        val hidden = androidx.compose.ui.text.input.PasswordVisualTransformation()
        val keys = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
        AlertDialog(onDismissRequest = { protectingBook = false }, title = { Text(if (unprotect) "Unprotect workbook" else "Protect workbook") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (unprotect) "Its password, if it has one:" else "Its sheets can't be added, deleted, renamed, moved or hidden. A password is optional.")
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = hidden, keyboardOptions = keys)
                if (!unprotect && password.isNotEmpty()) OutlinedTextField(again, { again = it }, label = { Text("Password again") }, singleLine = true, visualTransformation = hidden, keyboardOptions = keys,
                    isError = again.isNotEmpty() && again != password) } },
            confirmButton = { TextButton(enabled = unprotect || password.isEmpty() || again == password, onClick = { protectingBook = false
                protectBook(!unprotect, password) }) { Text(if (unprotect) "Unprotect" else "Protect") } },
            dismissButton = { TextButton(onClick = { protectingBook = false }) { Text("Cancel") } }) }
    // Data › Sort: Excel's dialog — headers?, then each level's column and order (up to three)
    sortingRows?.let { d -> sheet?.let { s -> val b = book
        val guess = remember(d) { (d.c1..d.c2).all { s[d.r1, it]?.kind == SheetKind.Text } && (d.c1..d.c2).any { s[d.r1 + 1, it]?.kind == SheetKind.Number } }
        var headers by remember(d) { mutableStateOf(guess) }
        val levels = remember(d) { androidx.compose.runtime.mutableStateListOf(view.col.coerceIn(d.c1, d.c2) to true) }
        fun named(c: Int) = if (headers) s[d.r1, c]?.text?.takeIf { it.isNotBlank() } ?: "Column ${Sheets.colName(c)}" else "Column ${Sheets.colName(c)}"
        AlertDialog(onDismissRequest = { sortingRows = null }, title = { Text("Sort") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${Sheets.colName(d.c1)}${d.r1 + 1}:${Sheets.colName(d.c2)}${d.r2 + 1}")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { headers = !headers }) { Checkbox(headers, { headers = it }); Text("My data has headers") }
                levels.forEachIndexed { k, (col, up) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (k == 0) "Sort by" else "Then by", modifier = Modifier.width(64.dp))
                        Box { var open by remember { mutableStateOf(false) }
                            OutlinedButton(onClick = { open = true }) { Text(named(col), maxLines = 1) }
                            DropdownMenu(open, { open = false }) { (d.c1..d.c2).forEach { c -> DropdownMenuItem(text = { Text(named(c)) }, onClick = { open = false; levels[k] = c to up }) } } }
                        androidx.compose.material3.FilterChip(up, { levels[k] = col to true }, { Text("A → Z") })
                        androidx.compose.material3.FilterChip(!up, { levels[k] = col to false }, { Text("Z → A") })
                        if (k > 0) IconButton(onClick = { levels.removeAt(k) }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove level") }
                    }
                }
                if (levels.size < 3) TextButton(onClick = { levels += d.c1 to true }) { Text("Add level") } } },
            confirmButton = { TextButton(onClick = { sortingRows = null; if (b == null) return@TextButton
                if (s.merges.any { m -> m.r1 <= d.r2 && d.r1 <= m.r2 && m.c1 <= d.c2 && d.c1 <= m.c2 }) { message = "Merged cells are in the way: unmerge them first"; return@TextButton }
                val changes = SheetSortRows.changes(s, d, levels.toList(), headers)
                if (changes.isEmpty()) message = "Already in that order" else { history.apply(b, index, changes); version++; view.sort = null; WriterLog.i("Sheets: sorted by ${levels.size} levels") } }) { Text("Sort") } },
            dismissButton = { TextButton(onClick = { sortingRows = null }) { Text("Cancel") } }) } }
    // Formulas › Names: Excel's Name Manager — the names and what they stand for, Delete; a new one for the chosen cells
    namesFor?.let { g -> book?.let { b -> val list = remember(version, b) { b.nameList() }
        var name by remember(g) { mutableStateOf("") }
        val problem = SheetNames.valid(name.trim(), list.map { it.first })
        AlertDialog(onDismissRequest = { namesFor = null }, title = { Text("Names") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (list.isEmpty()) Text("This workbook has no names yet.")
                list.forEach { (n, to) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(n, fontWeight = FontWeight.Bold); Text(to, style = MaterialTheme.typography.bodySmall) }
                    IconButton(onClick = { namesFor = null; nameStep(n, null) }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete $n") } } }
                HorizontalDivider()
                Text("A new name for ${SheetNames.refersTo(b.tabs[index].name, g)}:")
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, isError = name.isNotEmpty() && problem != null,
                    supportingText = { if (name.isNotEmpty() && problem != null) Text(problem) }) } },
            confirmButton = { TextButton(enabled = problem == null, onClick = { namesFor = null; nameStep(name.trim(), g) }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { namesFor = null }) { Text("Close") } }) } }
    // Data › Subtotal: Excel's dialog — at each change in, the function, the columns to total
    totalling?.let { d -> sheet?.let { s -> val b = book
        fun named(c: Int) = s[d.r1, c]?.text?.takeIf { it.isNotBlank() } ?: "Column ${Sheets.colName(c)}"
        var by by remember(d) { mutableStateOf(d.c1) }
        var fn by remember(d) { mutableStateOf(9) }
        val cols = remember(d) { androidx.compose.runtime.mutableStateListOf<Int>().apply { addAll((d.c1..d.c2).filter { c -> (d.r1 + 1..d.r2).any { s[it, c]?.kind == SheetKind.Number } }) } }
        AlertDialog(onDismissRequest = { totalling = null }, title = { Text("Subtotal") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${Sheets.colName(d.c1)}${d.r1 + 1}:${Sheets.colName(d.c2)}${d.r2 + 1}, its first row the headings")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("At each change in")
                    Box { var open by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { open = true }) { Text(named(by), maxLines = 1) }
                        DropdownMenu(open, { open = false }) { (d.c1..d.c2).forEach { c -> DropdownMenuItem(text = { Text(named(c)) }, onClick = { open = false; by = c }) } } } }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SheetSubtotal.FUNCTIONS.forEach { (n, k) -> androidx.compose.material3.FilterChip(fn == k, { fn = k }, { Text(n) }) } }
                Text("Add a subtotal to:")
                (d.c1..d.c2).filter { it != by }.forEach { c -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (c in cols) cols.remove(c) else cols.add(c) }) {
                    Checkbox(c in cols, { if (it) cols.add(c) else cols.remove(c) }); Text(named(c)) } } } },
            confirmButton = { TextButton(enabled = cols.any { it != by }, onClick = { totalling = null; if (b == null) return@TextButton; val i = index; val chosen = cols.filter { it != by }.sorted()
                if (s.merges.any { m -> m.r1 <= d.r2 && d.r1 <= m.r2 && m.c1 <= d.c2 && d.c1 <= m.c2 }) { message = "Merged cells are in the way: unmerge them first"; return@TextButton }
                fileStep("subtotals", { baked, next, bb -> DecimalFormatSymbols.getInstance().let { sym -> SheetSubtotal.apply(baked, next, bb, i, d, by, chosen, fn, sym.decimalSeparator, sym.groupingSeparator) } }) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { totalling = null }) { Text("Cancel") } }) } }
    // Insert › Shape: Words' and Slides' shapes dialog; the shape at the chosen cell, an inch square (long ones twice as wide), as Words sizes them
    if (shaping) sheet?.let { s -> ShapesDialog(odt = book?.kind == "ods", dismiss = { shaping = false }) { kind, fill, line ->
        shaping = false; val (r, c) = chosen(s); val i = index
        val (w, h) = if (kind == "line" || kind.startsWith("lineArrow")) 144f to 0.75f else if (Regex("(?i)^(right|left|leftRight|striped|notched)Arrow$|^(homePlate|chevron|ribbon2?|ellipseRibbon2?|horizontalScroll|wave|doubleWave)$").matches(kind)) 108f to 54f else 72f to 72f
        fileStep("shape put in", { baked, next, bb -> if (bb.kind == "xlsx") SheetChart.shapeXlsx(baked, next, bb.parts.getValue("$i"), r, c, w, h, kind, fill, line)
            else SheetChart.shapeOds(baked, next, i, r, c, w, h, kind, fill, line) }) } }
    // Insert › Source: Words' source dialog; the reference (APA 7) onto the Sources sheet, which is shown
    if (sourcing) SourceDialog(null, { sourcing = false }) { src -> sourcing = false
        fileStep("source added", { baked, next, bb -> DecimalFormatSymbols.getInstance().let { sym -> SheetSources.add(baked, next, bb, CiteStyle.APA.entry(src), sym.decimalSeparator, sym.groupingSeparator) } },
            structure = book?.tabs?.none { it.name.equals(SheetSources.NAME, true) } == true) { nb ->
            views.clear(); header = null; index = nb.tabs.indexOfFirst { it.name.equals(SheetSources.NAME, true) }.coerceAtLeast(0); message = "Added to the Sources sheet" } }
    // Review › Spelling: Excel's dialog — the word not in the dictionary, its cell, the suggestions; Change, Ignore, Ignore all
    spellRun?.let { run -> sheet?.let { s -> val b = book
        val (k, r, word) = run.queue.first()
        LaunchedEffect(k) { view.row = Sheet.row(k); view.col = Sheet.col(k); view.reveal++ }
        val offered = run.suggestions[word].orEmpty()
        var choice by remember(run) { mutableStateOf(offered.firstOrNull()) }
        fun next(ignored: Set<String> = run.ignored) {
            // what is left after this word: on in reading order, words ignored left out
            val rest = run.queue.drop(1).filter { it.third !in ignored }
            spellRun = if (rest.isEmpty()) null.also { message = "Spelling check complete" } else run.copy(queue = rest, ignored = ignored)
        }
        AlertDialog(onDismissRequest = { spellRun = null }, title = { Text("Spelling") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Not in the dictionary: $word", fontWeight = FontWeight.Bold)
                Text("${Sheets.colName(Sheet.col(k))}${Sheet.row(k) + 1}: ${s.cells[k]?.text.orEmpty()}", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                if (offered.isEmpty()) Text("No suggestions")
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    offered.forEach { o -> androidx.compose.material3.FilterChip(choice == o, { choice = o }, { Text(o) }) } } } },
            confirmButton = { Row {
                TextButton(enabled = choice != null && editing, onClick = { val cell = s.cells[k]; val c = choice
                    if (cell != null && c != null && b != null && cell.text.regionMatches(r.first, word, 0, word.length)) {
                        history.apply(b, index, mapOf(k to SheetSpelling.changed(cell, r, c))); version++
                        // on to the next, this cell's later words moved by the change
                        val shift = c.length - word.length
                        val rest = run.queue.drop(1).map { q -> if (q.first == k && q.second.first > r.last) Triple(k, q.second.first + shift..q.second.last + shift, q.third) else q }.filter { it.third !in run.ignored }
                        spellRun = if (rest.isEmpty()) null.also { message = "Spelling check complete" } else run.copy(queue = rest)
                    } else next() }) { Text("Change") }
                TextButton(onClick = { next() }) { Text("Ignore") }
                TextButton(onClick = { next(run.ignored + word) }) { Text("Ignore all") } } },
            dismissButton = { TextButton(onClick = { spellRun = null }) { Text("Close") } }) } }
    // Review › Workbook statistics: as Words' counts — the workbook's sheets, this sheet's cells, words, formulas, notes, drawings
    if (counting) book?.let { b -> sheet?.let { s -> var filled = 0; var numbers = 0; var formulas2 = 0; var words = 0; var notes = 0
        s.cells.forEach { _, c -> if (c.kind != SheetKind.Empty) filled++; if (c.kind == SheetKind.Number) numbers++; if (c.formula != null) formulas2++
            if (c.kind == SheetKind.Text) words += WriterSpelling.words(c.text).size; if (c.note != null) notes++ }
        AlertDialog(onDismissRequest = { counting = false }, title = { Text("Workbook statistics") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Workbook", fontWeight = FontWeight.Bold); Text("Sheets: ${b.tabs.size}" + (b.tabs.count { it.hidden }.takeIf { it > 0 }?.let { " ($it hidden)" } ?: ""))
                Text(s.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("Cells with data: $filled"); Text("Numbers: $numbers"); Text("Formulas: $formulas2"); Text("Words: $words")
                Text("Notes: $notes"); Text("Pictures, charts and shapes: ${s.drawings.size}")
                Text("Used range: A1:${Sheets.colName(maxOf(0, s.cols - 1))}${maxOf(1, s.rows)}") } },
            confirmButton = { TextButton(onClick = { counting = false }) { Text("Close") } }) } }
    // Data › Goal seek: Excel's dialog — the formula cell, the value it should give, the cell to change
    if (seeking) sheet?.let { s -> val b = book; val (r0, c0) = chosen(s)
        var setRef by remember { mutableStateOf("${Sheets.colName(c0)}${r0 + 1}") }; var toValue by remember { mutableStateOf("") }; var byRef by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { seeking = false }, title = { Text("Goal seek") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(setRef, { setRef = it }, label = { Text("Set cell (a formula)") }, singleLine = true)
                OutlinedTextField(toValue, { toValue = it }, label = { Text("To value") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(byRef, { byRef = it }, label = { Text("By changing cell") }, singleLine = true) } },
            confirmButton = { TextButton(onClick = { seeking = false; if (b == null) return@TextButton
                val set = Sheets.cellRef(setRef.trim().uppercase().replace("$", "")); val by = Sheets.cellRef(byRef.trim().uppercase().replace("$", "")); val to = toValue.trim().replace(',', '.').toDoubleOrNull()
                when {
                    set == null || by == null || to == null -> message = "Give two cells and a number"
                    s[set.first, set.second]?.formula == null -> message = "${setRef.uppercase()} has no formula"
                    s[by.first, by.second]?.formula != null -> message = "${byRef.uppercase()} holds a formula: choose a cell with a value"
                    else -> { val x = SheetGoalSeek.solve(b, index, Sheet.key(set.first, set.second), to, Sheet.key(by.first, by.second))
                        if (x == null) message = "Goal seek found no value"
                        else { val k = Sheet.key(by.first, by.second); val old = s.cells[k] ?: SheetCell("", SheetKind.Empty); val code = lookAt(s, by.first, by.second).second
                            history.apply(b, index, mapOf(k to b.format.format(x, code).let { old.copy(text = it.text, kind = SheetKind.Number, value = x, formula = null, color = it.color) })); version++
                            message = "${byRef.uppercase()} set to ${b.format.general(x)}"; WriterLog.i("Sheets: goal sought") } }
                } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { seeking = false }) { Text("Cancel") } }) }
    // Insert › Text box: its words, then a box 3 columns × 4 rows big at the chosen cell
    if (boxing) sheet?.let { s -> var words by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { boxing = false }, title = { Text("Text box") },
            text = { OutlinedTextField(words, { words = it }, label = { Text("Words") }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = words.isNotBlank(), onClick = { boxing = false; val (r, c) = chosen(s); val i = index; val t = words.trim()
                fileStep("text box put in", { baked, next, bb -> if (bb.kind == "xlsx") SheetChart.textBoxXlsx(baked, next, bb.parts.getValue("$i"), r, c, 144f, 60f, t)
                    else SheetChart.textBoxOds(baked, next, i, r, c, 144f, 60f, t) }) }) { Text("Put in") } },
            dismissButton = { TextButton(onClick = { boxing = false }) { Text("Cancel") } }) }
    // Home › Conditional formatting: Excel's dialog — the value(s) and the look
    ruling?.let { (kind, d) ->
        val count = when (kind) { "between" -> 2; "duplicate", "aboveAverage" -> 0; else -> 1 }
        val typed = remember(kind, d) { androidx.compose.runtime.mutableStateListOf(*Array(count) { if (kind == "top10") "10" else "" }) }
        var look by remember(kind, d) { mutableStateOf(0) }
        val title = mapOf("greaterThan" to "Greater than", "lessThan" to "Less than", "between" to "Between", "equal" to "Equal to", "contains" to "Text that contains",
            "duplicate" to "Duplicate values", "top10" to "Top 10", "aboveAverage" to "Above average").getValue(kind)
        AlertDialog(onDismissRequest = { ruling = null }, title = { Text(title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (kind) { "duplicate" -> "Cells that hold a value met more than once:"; "aboveAverage" -> "Cells above the average of the chosen cells:"
                    "top10" -> "The cells that rank in the top:"; else -> "Cells whose value is:" })
                typed.indices.forEach { k -> OutlinedTextField(typed[k], { typed[k] = it }, singleLine = true,
                    label = { Text(when { kind == "between" -> if (k == 0) "From" else "To"; kind == "top10" -> "How many"; kind == "contains" -> "Words"; else -> "Value" }) }) }
                Text("With:")
                SheetConditional.LOOKS.forEachIndexed { k, (name, fill, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { look = k }.heightIn(min = 48.dp)) {
                        androidx.compose.material3.RadioButton(look == k, { look = k })
                        Text(name, color = Color(color), modifier = Modifier.background(Color(fill)).padding(horizontal = 8.dp, vertical = 4.dp)) } } } },
            confirmButton = { TextButton(enabled = typed.all { it.isNotBlank() }, onClick = { ruling = null; putRule(d, SheetConditional.Rule(kind, typed.toList(), look)) }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { ruling = null }) { Text("Cancel") } }) }
    // Data › Drop-down list: the values, comma-separated (Excel's Source box); Take away when the cells have one
    listing?.let { d -> val was = sheet?.listAt(d.r1, d.c1)
        var typed by remember(d) { mutableStateOf(was?.items?.joinToString(", ").orEmpty()) }
        AlertDialog(onDismissRequest = { listing = null }, title = { Text("Drop-down list") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cells ${Sheets.colName(d.c1)}${d.r1 + 1}" + (if (d.r2 > d.r1 || d.c2 > d.c1) ":${Sheets.colName(d.c2)}${d.r2 + 1}" else "") + " take one of these values.")
                if (was?.source != null && was.items.isEmpty()) Text("Now: the values in ${was.source}")
                OutlinedTextField(typed, { typed = it }, label = { Text("Values, separated by commas") }, singleLine = true) } },
            confirmButton = { TextButton(enabled = typed.isNotBlank(), onClick = { listing = null
                putList(d, typed.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()) }) { Text("Done") } },
            dismissButton = { Row { if (was != null) TextButton(onClick = { listing = null; putList(d, null) }) { Text("Take away") }
                TextButton(onClick = { listing = null }) { Text("Cancel") } } }) }
    // Insert › Note: Words' comment dialog (its remembered name), on the chosen cell
    if (noting) sheet?.let { s -> val (r, c) = chosen(s); val was = s[r, c]?.note
        val prefs = context.getSharedPreferences("writer", android.content.Context.MODE_PRIVATE)
        NewCommentDialog(remember { prefs.getString("author", "") ?: "" }, { noting = false }, title = if (was != null) "Edit note" else "Note",
            start = was?.substringAfter(":", was)?.trim().orEmpty(), confirm = if (was != null) "Done" else "Add", label = "Note") { author, text ->
            noting = false; prefs.edit().putString("author", author).apply(); if (text.isNotBlank()) putNote(r, c, author.ifBlank { "Tetra" }, text.trim()) } }
    // a column's Width or a row's Height, typed in Words' unit (cm or inches)
    sizing?.let { (cols, at) -> val s = sheet
        val now = s?.let { if (cols) it.width(at) else it.height(at) } ?: 0f
        var typed by remember(sizing) { mutableStateOf("%.2f".format(java.util.Locale.ROOT, now * 3 / 4 / WriterUnits.pt)) }
        AlertDialog(onDismissRequest = { sizing = null }, title = { Text(if (cols) "Column width" else "Row height") },
            text = { OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text(WriterUnits.name) }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) },
            confirmButton = { TextButton(onClick = { val v = typed.replace(',', '.').toFloatOrNull()
                if (v != null && v > 0f) { sizing = null; header = null; resize(cols, at, at, v * WriterUnits.pt * 4 / 3, null) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { sizing = null }) { Text("Cancel") } }) }
    // a drawing's Size: its width and height in Words' unit
    figureSizing?.let { d -> val s = sheet
        // as drawn: its own size, or its cells' (an anchor from one cell to another)
        val (wPx, hPx) = if (d.toRow < 0 || s == null) d.width to d.height
            else ((d.col until d.toCol).sumOf { s.width(it).toDouble() }.toFloat() - d.dx + d.toDx) to ((d.row until d.toRow).sumOf { s.height(it).toDouble() }.toFloat() - d.dy + d.toDy)
        fun shown(px: Float) = "%.2f".format(java.util.Locale.ROOT, px * 3 / 4 / WriterUnits.pt)
        var w by remember(d) { mutableStateOf(shown(wPx)) }; var h by remember(d) { mutableStateOf(shown(hPx)) }
        AlertDialog(onDismissRequest = { figureSizing = null }, title = { Text("Size") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(w, { w = it }, singleLine = true, label = { Text("Width (${WriterUnits.name})") }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(h, { h = it }, singleLine = true, label = { Text("Height (${WriterUnits.name})") }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)) } },
            confirmButton = { TextButton(onClick = { val a = w.replace(',', '.').toFloatOrNull(); val b2 = h.replace(',', '.').toFloatOrNull()
                if (a != null && b2 != null && a > 0f && b2 > 0f) { figureSizing = null; figureStep(d, SheetDrawings.Size(a * WriterUnits.pt, b2 * WriterUnits.pt)) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { figureSizing = null }) { Text("Cancel") } }) }
    // Insert › Chart: Words' chart editor; done, the chart on the sheet to the right of the cells (Excel's default size, 8 × 15 cells)
    charting?.let { (g, start) -> ChartDialog(start, false, { charting = null }) { ch -> charting = null; val i = index
        fileStep("chart put on", { baked, next, b -> when (b.kind) {
            "xlsx" -> SheetChart.xlsx(baked, next, b.parts.getValue("$i"), g.r1, g.c2 + 2, 15, 8, ch)
            "ods" -> SheetChart.ods(baked, next, i, g.r1, g.c2 + 2, 360f, 216f, ch)
            else -> error("A CSV file cannot hold a chart: save it as a workbook first.") } }) } }
    // Insert › Symbol (Words'): put in where one types in the cell, typing begun if it was not
    if (symbols) SymbolDialog({ symbols = false }) { sym -> symbols = false; symbol = sym }
    if (inserting) FunctionsDialog({ inserting = false }) { f -> inserting = false; inserted = f }
    // Insert › WordArt: its words (Words' "Your text here"), put on at the chosen cell in the look and bend chosen, sized to its words as Slides'
    artWords?.let { (style, bend) ->
        var words by remember { mutableStateOf("Your text here") }
        AlertDialog(onDismissRequest = { artWords = null }, title = { Text("WordArt") },
            text = { OutlinedTextField(words, { words = it }, label = { Text("Its words") }, singleLine = true) },
            confirmButton = { TextButton(enabled = words.isNotBlank(), onClick = { artWords = null
                val s = sheet ?: return@TextButton; val (r, c) = chosen(s); val i = index
                val fx = TextEffects(if (style.outline) artOutline(style.color) else null, style.shadow, if (style.glow && book?.kind != "ods") artGlow(style.color) else null)
                val (w0, h0) = android.graphics.Paint().apply { textSize = PictureXml.ART_PT; typeface = android.graphics.Typeface.DEFAULT_BOLD }.measureText(words).let { it * 1.1f + 30f to PictureXml.ART_PT * 1.25f + 10f }
                val (aw, ah) = when (bend.warp) { null -> w0 to h0; TextWarp.Circle -> maxOf(w0 * 0.6f, 144f).let { it to it }; else -> w0 to maxOf(h0, w0 * 0.35f) }
                fileStep("WordArt put in", { baked, next, bb -> if (bb.kind == "xlsx") SheetChart.wordArtXlsx(baked, next, bb.parts.getValue("$i"), r, c, aw, ah, words, style.color, PictureXml.ART_PT, fx, bend.warp)
                    else SheetChart.wordArtOds(baked, next, i, r, c, aw, ah, words, style.color, PictureXml.ART_PT, fx, bend.warp) }) }) { Text("Insert") } },
            dismissButton = { TextButton(onClick = { artWords = null }) { Text("Cancel") } })
    }
    // Insert › Equation: Words' equation editor, the equation put on the sheet as a picture at the chosen cell (its letters 16 pt).
    // shortcut: a picture, not Excel's own equation (OMML in a text box): edited again as a picture; upgrade if equations are kept editable
    if (equating) EquationDialog(null, { equating = false }) { m -> equating = false
        val s = sheet ?: return@EquationDialog; val (r, c) = chosen(s); val i = index
        val made = runCatching {
            val dr = MathDrawable(m, 16f); val w = dr.widthPt.coerceAtLeast(1f); val h = dr.heightPt.coerceAtLeast(1f)
            val bmp = android.graphics.Bitmap.createBitmap((w * 4).toInt().coerceAtLeast(1), (h * 4).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, bmp.width, bmp.height); dr.draw(android.graphics.Canvas(bmp))
            Triple(java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray(), w, h)
        }.onFailure { WriterLog.e("Sheets: an equation could not be drawn", it) }.getOrNull() ?: run { message = "Could not draw that equation"; return@EquationDialog }
        val (bytes, w, h) = made
        fileStep("equation put in", { baked, next, bb -> if (bb.kind == "xlsx") SheetChart.pictureXlsx(baked, next, bb.parts.getValue("$i"), r, c, w, h, bytes, "png")
            else SheetChart.pictureOds(baked, next, i, r, c, w, h, bytes, "png") })
    }
    evaluating?.let { (at, steps) ->
        var shown by remember(steps) { mutableStateOf(1) }
        AlertDialog(onDismissRequest = { evaluating = null }, title = { Text("Evaluate formula: $at") }, text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) { items(shown) { k ->
                Text(steps[k], fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = if (k == shown - 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (k == shown - 1) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp)) } }
        }, confirmButton = { if (shown < steps.size) TextButton(onClick = { shown++ }) { Text("Evaluate") } else TextButton(onClick = { shown = 1 }) { Text("Restart") } },
            dismissButton = { TextButton(onClick = { evaluating = null }) { Text("Close") } })
    }
    // a function chosen: a new formula in the cell, or (typing a formula) put in where the cursor is
    LaunchedEffect(inserted) { val f = inserted ?: return@LaunchedEffect
        delay(150)
        val d = draft
        if (d != null && d.text.startsWith("=")) draft = TextFieldValue(d.text.substring(0, d.selection.min) + "$f(" + d.text.substring(d.selection.max), androidx.compose.ui.text.TextRange(d.selection.min + f.length + 1))
        else { runCatching { barFocus.requestFocus() }; draft = TextFieldValue("=$f(", androidx.compose.ui.text.TextRange(f.length + 2)) }
        runCatching { barFocus.requestFocus() }
        inserted = null; TextEditorStore(context).usedFunction(f); WriterLog.i("Sheets: function $f") }
    LaunchedEffect(symbol) { val sym = symbol ?: return@LaunchedEffect
        delay(150) // the dialog gone: the bar can take focus (which starts typing with the cell's own words, as AutoSum's)
        runCatching { barFocus.requestFocus() }
        if (draft == null) startTyping()
        draft?.let { d -> draft = TextFieldValue(d.text.substring(0, d.selection.min) + sym + d.text.substring(d.selection.max), androidx.compose.ui.text.TextRange(d.selection.min + sym.length)) }
        symbol = null }
    if (about) {
        val b = book
        AlertDialog(onDismissRequest = { about = false }, confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
            title = { Text(file?.name ?: "Spreadsheet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    file?.let { Text("${"%.1f".format(it.length() / 1024.0 / 1024.0)} MB · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified()))) }
                    b?.let { Text("${it.tabs.size} sheet${if (it.tabs.size == 1) "" else "s"}" + it.tabs.count { t -> t.hidden }.let { h -> if (h > 0) ", $h hidden" else "" }) }
                    s?.let { Text("This sheet: ${it.rows} rows × ${it.cols} columns, ${it.cells.size} cells" + if (it.merges.isNotEmpty()) ", ${it.merges.size} merged ranges" else "") }
                    Text("View only: open it in another app to change it.", style = MaterialTheme.typography.bodySmall)
                }
            })
    }
}

/** The file in an app that can open (and change) it: Android's chooser, the file given to read and write. */
/** [SheetScreen]'s open for a new spreadsheet (the Office home's Excel card). */
internal const val NEW_SHEET = "tetra:new-spreadsheet"

internal fun openElsewhere(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION), "Open with"))
    }.onFailure { WriterLog.w("Sheets: could not open elsewhere", it) }
}

/** Where one sheet is looked at: scrolled (in the sheet's pixels, past the frozen panes), zoomed, the cell chosen. */
internal class SheetView {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    var row by mutableIntStateOf(0)
    var col by mutableIntStateOf(0)
    var opened by mutableStateOf(false) // the cell bar shows all the cell's words
    var reveal by mutableIntStateOf(0) // counted up to scroll the chosen cell into sight
    val pins = mutableStateListOf<Int>()
    val filters = mutableStateMapOf<Int, Set<String>>()
    var fileFilter = false // the file's AutoFilter taken as [filters] once
    var fitTo by mutableStateOf<SheetRange?>(null) // View › Zoom to selection: the cells (as drawn) to fill the screen with
    var figure by mutableIntStateOf(-1) // the drawing chosen while editing ([SheetDrawing.ordinal]); -1: none
    var figureShift by mutableStateOf(Offset.Zero) // how far it is being dragged (on the screen)
    var sort by mutableStateOf<SheetSort?>(null)
    var freeze by mutableStateOf<Int?>(null) // the rows kept at the top, as chosen here (null: the file's)
    var range by mutableStateOf<SheetRange?>(null) // cells chosen by a long press and a drag, as drawn
    var fill by mutableStateOf<SheetRange?>(null) // the fill handle being dragged: the cells it would fill, as drawn
}

/** Copies [text] to Android's clipboard. */
internal fun copy(context: android.content.Context, label: String, text: String) {
    context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

/**
 * Where a file was left (asked of Word too: a document opens where it was): the sheet, the place, the zoom, the cell.
 * Kept by the file's path, in the app's own settings.
 */
private object SheetPlaces {
    private fun prefs(context: android.content.Context) = context.getSharedPreferences("sheet-places", android.content.Context.MODE_PRIVATE)
    fun save(context: android.content.Context, file: File, index: Int, v: SheetView) =
        prefs(context).edit().putString(file.absolutePath, "$index,${v.x},${v.y},${v.zoom},${v.row},${v.col}").apply()
    fun load(context: android.content.Context, file: File): Pair<Int, SheetView>? {
        val p = prefs(context).getString(file.absolutePath, null)?.split(',') ?: return null
        if (p.size < 6) return null
        return runCatching { p[0].toInt() to SheetView().apply { x = p[1].toFloat(); y = p[2].toFloat(); zoom = p[3].toFloat(); row = p[4].toInt(); col = p[5].toInt() } }.getOrNull()
    }
}

/** A find in any sheet: the sheet, the cell, its words, and its row's other words. */
internal class SheetHit(val sheet: Int, val key: Long, val text: String, val context: String)

/** Search's finds in every sheet, sheet by sheet as they come; a tap goes to the cell. */
@Composable
private fun HitsList(book: Workbook, hits: List<SheetHit>, searched: Int, sheets: Int, query: String, pick: (SheetHit) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Text(when {
                    query.isBlank() -> "Type to search every sheet"
                    searched < sheets -> "${hits.size} found · searching sheet ${searched + 1} of $sheets…"
                    else -> "${hits.size} found in ${hits.map { it.sheet }.distinct().size} of $sheets sheets"
                }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(4.dp))
            }
            items(hits, key = { "${it.sheet}:${it.key}" }) { h ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable { pick(h) }) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.tabs[h.sheet].name, style = MaterialTheme.typography.labelMedium, color = Color(0xFF6FCF97), maxLines = 1)
                            Text(Sheets.colName(Sheet.col(h.key)) + (Sheet.row(h.key) + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(h.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (h.context.isNotEmpty()) Text(h.context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** What search found, to be drawn: the cells, and the one shown now. */
/** What a protected sheet's refusal says (Excel's: unprotect it there first). */
private const val PROTECTED = "This sheet is protected: unprotect it (tap its tab › Unprotect) to change these cells"

internal class SheetMarks(val found: Set<Long>, val current: Long, /** Review › Show all notes */ val notes: Boolean = false, /** View › Page break preview */ val breaks: Boolean = false)

@Composable
private fun SearchBar(query: String, change: (String) -> Unit, count: Int, current: Int, everywhere: Boolean?, hits: Int, toggle: () -> Unit, move: (Int) -> Unit, close: () -> Unit,
    /** Replace (while editing; null: viewing): Words' replace row under the find */ replace: ((String, Boolean) -> Unit)? = null) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var replacing by remember { mutableStateOf(false) }; var replacement by remember { mutableStateOf("") }
    Column {
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close search") }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search the sheet", style = MaterialTheme.typography.titleMedium, color = islandContentColor().copy(alpha = 0.5f))
            BasicTextField(query, change, singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = islandContentColor()),
                cursorBrush = SolidColor(islandContentColor()), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { move(1) }), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
        // A cell's name is a jump (Go To), not words to find: no "None" for it.
        val goTo = Regex("""^[A-Za-z]{1,3}\d{1,7}$""").matches(query.trim())
        // In every sheet, or this one: a chip that turns it on, shows the list again, or turns it off.
        if (everywhere != null) Surface(shape = RoundedCornerShape(50), color = if (everywhere) EXCEL_GREEN else Color.Transparent, contentColor = islandContentColor(),
            modifier = Modifier.padding(end = 4.dp).clickable(onClick = toggle)) {
            Text(if (everywhere) "All sheets · $hits" else "All sheets", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        if (query.isNotBlank() && !(goTo && count == 0)) Text(if (count == 0) "None" else "${current + 1} of $count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = { move(-1) }, enabled = count > 0) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = "Previous") }
        IconButton(onClick = { move(1) }, enabled = count > 0) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = "Next", modifier = Modifier.rotate(180f)) }
        if (replace != null) Tool(R.drawable.ic_swap_horiz, "Replace", islandContentColor(), on = replacing) { replacing = !replacing }
    }
    if (replace != null && replacing) Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        ReplaceRow(islandContentColor(), replacement, { replacement = it }, count > 0, { replace(replacement, false) }, { replace(replacement, true) })
    }
    }
}

/** What the viewer is doing to the sheet, each with a ✕ to stop it: pinned columns, filters, the sort. */
@Composable
private fun ToolChips(view: SheetView) {
    @Composable fun chip(icon: Int, label: String, clear: () -> Unit) = Surface(shape = RoundedCornerShape(50), color = EXCEL_GREEN, contentColor = Color.White, modifier = Modifier.clickable(onClick = clear)) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Stop", modifier = Modifier.size(14.dp))
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (view.pins.isNotEmpty()) chip(R.drawable.ic_push_pin, "Pinned " + view.pins.joinToString(", ") { Sheets.colName(it) }) { WriterLog.i("Sheets: pins cleared"); view.pins.clear() }
        view.filters.keys.sorted().forEach { c -> chip(R.drawable.ic_filter, "Filter " + Sheets.colName(c)) { WriterLog.i("Sheets: filter cleared"); view.filters.remove(c) } }
        view.sort?.let { s -> chip(R.drawable.ic_sort_name, "Sorted " + Sheets.colName(s.col) + if (s.ascending) " A→Z" else " Z→A") { WriterLog.i("Sheets: sort cleared"); view.sort = null } }
    }
}

/** A column's filter, as Excel's: every value under the header with how many rows have it; the ticked ones are kept. */
@Composable
private fun FilterDialog(sheet: Sheet, col: Int, view: SheetView, close: () -> Unit) {
    var values by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    val keep = remember { mutableStateMapOf<String, Boolean>() }
    var narrow by remember { mutableStateOf("") }
    LaunchedEffect(col) {
        val v = withContext(Dispatchers.Default) { SheetLayout.values(sheet, col) }
        val now = view.filters[col]
        v.forEach { (value, _) -> keep[value] = now?.contains(value) ?: true }
        values = v
    }
    val header = sheet[SheetLayout.header(sheet), col]?.text?.takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Filter ${Sheets.colName(col)}" + (header?.let { " · $it" } ?: ""), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val list = values
            if (list == null) Text("Reading the column…")
            else Column {
                OutlinedTextField(narrow, { narrow = it }, singleLine = true, placeholder = { Text("Search the values") }, modifier = Modifier.fillMaxWidth())
                val words = SheetSearch.words(narrow)
                val shown = if (words.isEmpty()) list else list.filter { SheetSearch.matches(SheetSearch.norm(it.first), words) }
                val all = shown.all { keep[it.first] == true }
                Row(Modifier.fillMaxWidth().clickable { shown.forEach { keep[it.first] = !all } }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { v -> shown.forEach { keep[it.first] = v } })
                    Text(if (words.isEmpty()) "Select all" else "Select all found", style = MaterialTheme.typography.bodyLarge)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.first }) { (value, count) ->
                        Row(Modifier.fillMaxWidth().clickable { keep[value] = keep[value] != true }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(keep[value] == true, { keep[value] = it })
                            Text(value.ifEmpty { "(Empty)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val list = values ?: return@TextButton close()
                val kept = list.map { it.first }.filter { keep[it] == true }.toSet()
                if (kept.size == list.size) view.filters.remove(col) else view.filters[col] = kept
                WriterLog.i("Sheets: filter keeps ${kept.size} of ${list.size} values")
                view.y = 0f
                close()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/**
 * The cell bar while editing: the cell's name, and a field with what the cell holds as typed (its formula, its value);
 * typing in it changes the cell when Done is pressed (the next cell below is chosen, as Excel's Enter), ✕ leaves it.
 */
@Composable
private fun EditBar(name: String, shown: String, draft: TextFieldValue?, focus: FocusRequester, change: (TextFieldValue) -> Unit, start: () -> Unit, commit: () -> Unit, cancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Text("fx", style = MaterialTheme.typography.labelLarge, color = islandContentColor().copy(alpha = 0.6f), fontFamily = FontFamily.Serif, modifier = Modifier.padding(end = 10.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = if (draft != null) 0.14f else 0.06f), modifier = Modifier.weight(1f)) {
            BasicTextField(draft ?: TextFieldValue(shown), { if (draft == null) start(); change(it) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = islandContentColor()), cursorBrush = SolidColor(islandContentColor()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp).focusRequester(focus)
                    .onFocusChanged { if (it.isFocused && draft == null) start() })
        }
        if (draft != null) {
            IconButton(onClick = cancel) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave it as it was") }
            IconButton(onClick = commit) { Icon(painterResource(R.drawable.ic_check), contentDescription = "Enter") }
        }
    }
}

/** The chosen cell's name and what is in it: the formula when it has one, the words in full. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun CellBar(sheet: Sheet, view: SheetView, follow: (String) -> Unit, copy: (String) -> Unit) {
    val merge = sheet.merge(view.row, view.col)
    val r = merge?.r1 ?: view.row; val c = merge?.c1 ?: view.col
    val cell = sheet[r, c]
    val content = cell?.formula ?: cell?.text.orEmpty()
    Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { if (content.isNotEmpty()) copy(cell?.text ?: content) }) { view.opened = !view.opened }
        .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(Sheets.colName(c) + (r + 1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
        Text("fx", style = MaterialTheme.typography.labelLarge, color = islandContentColor().copy(alpha = 0.6f), fontFamily = FontFamily.Serif, modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            if (view.opened) Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { Text(content, style = MaterialTheme.typography.bodyMedium) }
            else Text(content, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The cell's comment, in Excel's note yellow.
            cell?.note?.let { n ->
                Text(n, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFE97A), maxLines = if (view.opened) 12 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        cell?.link?.let { link ->
            Surface(shape = RoundedCornerShape(50), color = EXCEL_GREEN, contentColor = Color.White, modifier = Modifier.padding(start = 8.dp).clickable { follow(link) }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(if (link.startsWith("#")) R.drawable.ic_link else R.drawable.ic_open_in_browser), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (link.startsWith("#")) "Go" else "Open", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** The sheets as Excel's tabs along the bottom (the file's hidden ones left out), the open one in Excel's green. */
@Composable
private fun SheetTabs(book: Workbook, index: Int, open: Sheet?, add: (() -> Unit)? = null, tools: (() -> Unit)? = null, pick: (Int) -> Unit) {
    if (book.tabs.count { !it.hidden } < 2 && add == null) return
    Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.bottomBarPadding().fillMaxWidth().shadedRow().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            book.tabs.forEachIndexed { i, tab ->
                if (tab.hidden) return@forEachIndexed
                val on = i == index
                val mark = open?.takeIf { on }?.tab?.let { Color(it) }
                Surface(
                    shape = RoundedCornerShape(10.dp), color = if (on) EXCEL_GREEN else Color.Transparent,
                    contentColor = if (on) Color.White else islandContentColor(),
                    // the open sheet tapped while editing: its tools in the strip (rename, move, delete)
                    modifier = Modifier.clickable { if (!on) pick(i) else tools?.invoke() },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        if (mark != null) Box(Modifier.width(24.dp).heightIn(min = 3.dp, max = 3.dp).background(mark))
                    }
                }
            }
            add?.let { a -> IconButton(onClick = a) { Text("+", style = MaterialTheme.typography.titleLarge) } }
        }
    }
}

@Composable
private fun SheetGrid(sheet: Sheet, styles: List<SheetStyle>, layout: SheetLayout, view: SheetView, marks: SheetMarks, formulas: Boolean, version: Int,
                      column: (Int, Float) -> Unit, row: (Int, Float) -> Unit, pick: ((Int, Int) -> Boolean)? = null,
                      filled: ((SheetRange, SheetRange) -> Unit)? = null, menu: ((Offset) -> Unit)? = null,
                      /** While editing: a drawing tapped (null: a cell), and the chosen one dragged to a cell. */
                      figures: ((SheetDrawing?) -> Unit)? = null, dropped: ((SheetDrawing, Int, Int) -> Unit)? = null) {
    val density = LocalDensity.current.density
    val haptic = LocalHapticFeedback.current
    val painter = remember(sheet, layout, formulas, version) { SheetPainter(sheet, styles, density, layout, view.pins.toList(), view.filters.keys.toSet(), view.sort, formulas) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var fling by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(painter) { onDispose { painter.report() } }
    LaunchedEffect(view.reveal, painter, size) { if (view.reveal > 0 && size != IntSize.Zero) painter.reveal(view, size.width.toFloat(), size.height.toFloat()) }
    LaunchedEffect(view.fitTo, painter, size) { view.fitTo?.let { g -> if (size != IntSize.Zero) { painter.fit(view, g, size.width.toFloat(), size.height.toFloat()); view.fitTo = null } } }
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it; painter.clamp(view, it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(painter) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fling?.cancel()
                    // A mouse's right button (a desktop's, asked 16 Oct): the cell under it chosen (unless it is among those
                    // chosen already), and its menu
                    if (menu != null && currentEvent.buttons.isSecondaryPressed) {
                        down.consume()
                        painter.sheetCellAt(view, down.position)?.let { (r, c) ->
                            val g = view.range
                            val inside = if (g != null) layout.row(r) in g.r1..g.r2 && layout.col(c) in g.c1..g.c2 else r == view.row && c == view.col
                            if (!inside) painter.tap(view, down.position)
                        }
                        menu(down.position)
                        return@awaitEachGesture
                    }
                    // The fill handle (Excel's black cross; here a dot at the chosen cells' corner, while editing): dragged, it fills.
                    val src = if (filled != null) painter.chosen(view) else null
                    if (src != null && painter.onHandle(view, src, down.position)) {
                        down.consume(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!p.pressed) break
                            view.fill = painter.fillTarget(view, src, p.position); p.consume()
                        }
                        val to = view.fill; view.fill = null
                        if (to != null) filled?.invoke(src, to)
                        return@awaitEachGesture
                    }
                    // the chosen drawing under the finger: dragged to another cell
                    val grabbed = if (dropped != null && view.figure >= 0) painter.drawingAt(view, down.position)?.takeIf { it.ordinal == view.figure } else null
                    if (grabbed != null) {
                        down.consume(); var shift = Offset.Zero
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!p.pressed) break
                            shift += p.positionChange(); view.figureShift = shift; p.consume()
                        }
                        view.figureShift = Offset.Zero
                        if (shift.getDistance() > viewConfiguration.touchSlop) painter.dropCell(view, grabbed, shift)?.let { (r, c) -> dropped?.invoke(grabbed, r, c) }
                        return@awaitEachGesture
                    }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false; var pinched = false; var travel = Offset.Zero; var released = false
                    // Held still a moment: a long press, which chooses cells as the finger then moves.
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.changes.none { it.pressed }) { released = true; return@withTimeoutOrNull false }
                            if (e.changes.size > 1) return@withTimeoutOrNull false
                            travel += e.calculatePan()
                            if (travel.getDistance() > viewConfiguration.touchSlop) { moved = true; return@withTimeoutOrNull false }
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    } == null
                    val choosing = held && painter.startRange(view, down.position)
                    if (choosing) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    while (!released) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                        if (choosing) { e.changes.firstOrNull { it.pressed }?.let { painter.extendRange(view, it.position) }; e.changes.forEach { it.consume() }; continue }
                        val zoom = e.calculateZoom(); val pan = e.calculatePan()
                        if (!moved) { travel += pan; if (travel.getDistance() > viewConfiguration.touchSlop || zoom != 1f) moved = true }
                        if (moved) {
                            if (zoom != 1f) { pinched = true; painter.zoomAt(view, zoom, e.calculateCentroid(useCurrent = true), size.width.toFloat(), size.height.toFloat()) }
                            painter.pan(view, pan, size.width.toFloat(), size.height.toFloat())
                            e.changes.forEach { it.consume() }
                        }
                        e.changes.firstOrNull { it.id == down.id && it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    }
                    if (choosing) WriterLog.i("Sheets: range chosen")
                    else if (!moved) {
                        // a cell tapped while editing may be taken by the edit (a formula's reference, typing again)
                        val figure = if (figures != null) painter.drawingAt(view, down.position) else null
                        if (figure != null) { figures?.invoke(figure); return@awaitEachGesture }
                        figures?.invoke(null)
                        val cell = painter.sheetCellAt(view, down.position)
                        if (cell == null || pick?.invoke(cell.first, cell.second) != true)
                            painter.tap(view, down.position)?.let { (kind, k) -> if (kind == 'c') column(k, down.position.x) else row(k, down.position.y) }
                    }
                    else if (!pinched) {
                        val v = tracker.calculateVelocity()
                        fling = scope.launch {
                            var last = Offset.Zero
                            Animatable(Offset.Zero, Offset.VectorConverter).animateDecay(Offset(v.x, v.y), exponentialDecay(frictionMultiplier = 1.4f)) {
                                painter.pan(view, value - last, size.width.toFloat(), size.height.toFloat()); last = value
                            }
                        }
                    }
                }
            },
    ) {
        // Read here, so a scroll or a zoom redraws without recomposing.
        val x = view.x; val y = view.y; val zoom = view.zoom; val r = view.row; val col = view.col; val range = view.range
        painter.handle = filled != null; painter.filling = view.fill; painter.figure = view.figure; painter.figureShift = view.figureShift
        drawIntoCanvas { painter.draw(it.nativeCanvas, this.size.width, this.size.height, x, y, zoom, r, col, marks, range) }
    }
}

/**
 * Draws a sheet on an Android canvas, its rows and columns in [layout]'s order. Everything in the cells is drawn in the
 * sheet's own pixels with the canvas scaled, so text is measured and wrapped once, not again at every step of a pinch.
 * Indices here are the drawn ones; [rAt] and [cAt] give the sheet's.
 */
internal class SheetPainter(
    private val sheet: Sheet, private val styles: List<SheetStyle>, private val density: Float, private val layout: SheetLayout,
    private val pins: List<Int> = emptyList(), private val filtered: Set<Int> = emptySet(), private val sort: SheetSort? = null,
    /** ⋯ › Show formulas: a cell with a formula shows it, from the left, as Excel's Show Formulas. */
    private val formulas: Boolean = false,
) {
    private val rAt = layout.rows; private val cAt = layout.cols
    /** The pages as printed (View › Page break preview). */
    private val pages by lazy { sheetPages(sheet, layout) }
    /** The cells with notes, as drawn (Review › Show all notes). */
    private val noted by lazy { ArrayList<Pair<Int, Int>>().also { l -> sheet.cells.forEach { k, c -> if (c.note != null) {
        val r = layout.row(Sheet.row(k)); val col = layout.col(Sheet.col(k)); if (r >= 0 && col >= 0) l += r to col } } } }
    private val cols = cAt.size; private val rows = rAt.size
    private val colX = FloatArray(cols + 1).also { a -> for (c in 0 until cols) a[c + 1] = a[c] + sheet.width(cAt[c]) }
    private val rowY = FloatArray(rows + 1).also { a -> for (r in 0 until rows) a[r + 1] = a[r] + sheet.height(rAt[r]) }
    private val fc = min(layout.frozenCols, cols); private val fr = min(layout.frozenRows, rows)

    /** Merged ranges as drawn: only those whose rows and columns still stand together (a filter or a pin may part them). */
    private val mergeAt = HashMap<Long, SheetRange>()
    private val merges = sheet.merges.mapNotNull { g ->
        if (layout.row(g.r1) < 0 || layout.col(g.c1) < 0) return@mapNotNull null
        val rs = (g.r1..g.r2).map(layout::row).filter { it >= 0 }; val cs = (g.c1..g.c2).map(layout::col).filter { it >= 0 }
        if (rs.max() - rs.min() + 1 != rs.size || cs.max() - cs.min() + 1 != cs.size) return@mapNotNull null
        SheetRange(rs.min(), cs.min(), rs.max(), cs.max()).also { d -> for (r in d.r1..d.r2) for (c in d.c1..d.c2) mergeAt[Sheet.key(r, c)] = d }
    }
    private fun merge(r: Int, c: Int) = mergeAt[Sheet.key(r, c)]

    /** Each picture, chart and text box where it is drawn (one whose cells a filter hid is not). */
    private val drawn = sheet.drawings.mapNotNull { d ->
        val r = layout.row(d.row); val k = layout.col(d.col)
        if (r < 0 || k < 0) return@mapNotNull null
        val left = colX[k] + d.dx; val top = rowY[r] + d.dy
        if (d.toRow < 0) return@mapNotNull d to android.graphics.RectF(left, top, left + d.width, top + d.height)
        val r2 = layout.row(d.toRow); val k2 = layout.col(d.toCol)
        if (r2 < 0 || k2 < 0) null else d to android.graphics.RectF(left, top, colX[k2] + d.toDx, rowY[r2] + d.toDy)
    }
    var figure = -1; var figureShift = Offset.Zero // the chosen drawing, and how far it is being dragged (screen)

    /** The point of the sheet (its pixels) under a point of the screen. */
    private fun sheetAt(view: SheetView, at: Offset): Pair<Float, Float> {
        val s = scale(view.zoom)
        val sx = if (at.x < headerW + colX[fc] * s) (at.x - headerW) / s else colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val sy = if (at.y < headerH + rowY[fr] * s) (at.y - headerH) / s else rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        return sx to sy
    }
    /** The drawing under a point of the screen (the topmost); null: none. */
    fun drawingAt(view: SheetView, at: Offset): SheetDrawing? {
        if (at.x < headerW || at.y < headerH) return null
        val (sx, sy) = sheetAt(view, at)
        return drawn.lastOrNull { (_, box) -> box.contains(sx, sy) }?.first
    }
    /** The sheet's cell (row, column) a drawing dragged by [shift] (screen) would start in. */
    fun dropCell(view: SheetView, d: SheetDrawing, shift: Offset): Pair<Int, Int>? {
        val box = drawn.firstOrNull { it.first === d }?.second ?: return null
        val s = scale(view.zoom)
        val sx = box.left + shift.x / s; val sy = box.top + shift.y / s
        return rAt[find(rowY, max(0f, sy), rows)] to cAt[find(colX, max(0f, sx), cols)]
    }
    /** A drawing's size as drawn (sheet pixels). */
    fun drawnSize(d: SheetDrawing) = drawn.firstOrNull { it.first === d }?.second?.let { it.width() to it.height() }
    private val pictures = HashMap<SheetDrawing, android.graphics.Bitmap?>()
    private val charts = HashMap<SheetDrawing, ChartDrawable>()
    private fun at(r: Int, c: Int) = sheet[rAt[r], cAt[c]]?.let { cell -> if (formulas && cell.formula != null) cell.copy(text = cell.formula, kind = SheetKind.Text) else cell }

    // View › Headings off: no letters and numbers, the cells from the edge (as Excel)
    private val headerH = if (sheet.headings) 24 * density else 0f
    // Not in apply: TextPaint has a density of its own (1), which `density` there would mean.
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.textSize = 12 * density; it.color = 0xFF5F5F5F.toInt(); it.textAlign = Paint.Align.CENTER }
    private val headerW = if (!sheet.headings) 0f else max(36 * density, headerPaint.measureText(((rAt.maxOrNull() ?: 0) + 1).toString()) + 14 * density)
    private val fill = Paint()
    private val line = Paint().apply { style = Paint.Style.STROKE }
    private val grid = Paint().apply { color = 0xFFD9D9D9.toInt(); style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val dash = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    private val dots = DashPathEffect(floatArrayOf(1f, 1f), 0f)
    private val faces = HashMap<String, Typeface>()
    private val general = java.text.DecimalFormatSymbols.getInstance().let { SheetFormat(it.decimalSeparator, it.groupingSeparator) }
    private val laidCells = HashMap<Long, Block>()
    /** A cell's paragraphs: single lines, no room between them (not Word's Normal). */
    private val CELL_LOOK = object : DocStyles() { override fun look(p: DocPara) = ParaLook() }

    /**
     * A cell's words laid out by the Office engine (step 6, as a slide's box is: Words' paragraphs and runs): its rich
     * text's runs (else its words in its style's look), a paragraph a line, [width] pt wide when it wraps, else as wide
     * as its longest line, standing where its alignment says.
     */
    private fun cellBlock(cell: SheetCell, st: SheetStyle, align: SheetAlign, width: Float): Block {
        val base = DocRun("", bold = st.bold, italic = st.italic, underline = st.underline, strike = st.strike, sizePt = st.size,
            color = (cell.color ?: st.color ?: if (cell.link != null && st.color == null) 0xFF0563C1.toInt() else 0xFF000000.toInt()) and 0xFFFFFF, font = st.font?.let(Slides::face))
        val runs = cell.rich?.map { r -> r.copy(sizePt = r.sizePt ?: base.sizePt, color = r.color ?: base.color, font = r.font ?: base.font) } ?: listOf(base.copy(text = cell.text))
        val a = when (align) { SheetAlign.Center -> DocAlign.Center; SheetAlign.Right -> DocAlign.End; SheetAlign.Justify -> DocAlign.Justify; else -> DocAlign.Start }
        // a paragraph a line (Alt+Enter's line breaks), each run cut where they fall
        val paras = ArrayList<DocPara>(); var now = ArrayList<DocRun>()
        for (r in runs) r.text.split('\n').forEachIndexed { i, piece -> if (i > 0) { paras += DocPara(now, align = a); now = ArrayList() }; if (piece.isNotEmpty()) now += r.copy(text = piece) }
        paras += DocPara(now.ifEmpty { listOf(base) }, align = a)
        val doc = OfficeDoc(paras, basePt = st.size)
        fun stack(w: Float) = OfficePages.stack(doc, paras, CELL_LOOK, DocPage(), w, { null }, null, 0f, 0f)
        if (st.wrap || align == SheetAlign.Justify) return stack(width)
        val wide = stack(100_000f).pieces.maxOfOrNull { pc -> (pc.firstLine..pc.lastLine).maxOf { pc.layout.getLineWidth(it) } / 20f + pc.x } ?: 0f
        val w = maxOf(width, wide + 1f)
        return stack(w).moved(when (a) { DocAlign.Center -> (width - w) / 2; DocAlign.End -> width - w; else -> 0f }, 0f)
    }
    private val funnel = Path()
    private var lines = FloatArray(4096)

    // What the log is told about drawing: the first frame, and how many were slow.
    private var frames = 0; private var slow = 0; private var first = -1L; private var worst = 0L

    private fun scale(zoom: Float) = zoom * density
    private fun find(a: FloatArray, v: Float, n: Int): Int {
        var lo = 0; var hi = n - 1
        while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (a[mid] <= v) lo = mid else hi = mid - 1 }
        return lo
    }

    fun clamp(view: SheetView, w: Float, h: Float) {
        val s = scale(view.zoom)
        val bodyW = (w - headerW) / s - colX[fc]; val bodyH = (h - headerH) / s - rowY[fr]
        view.x = view.x.coerceIn(0f, max(0f, colX[cols] - colX[fc] - bodyW))
        view.y = view.y.coerceIn(0f, max(0f, rowY[rows] - rowY[fr] - bodyH))
    }

    fun pan(view: SheetView, by: Offset, w: Float, h: Float) {
        val s = scale(view.zoom)
        view.x -= by.x / s; view.y -= by.y / s
        clamp(view, w, h)
    }

    /** Zoom about the fingers: the sheet's point under them stays under them. */
    fun zoomAt(view: SheetView, factor: Float, at: Offset, w: Float, h: Float) {
        val s = scale(view.zoom)
        val px = colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val py = rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        view.zoom = (view.zoom * factor).coerceIn(0.25f, 4f)
        val s2 = scale(view.zoom)
        view.x = px - colX[fc] - (at.x - headerW - colX[fc] * s2) / s2
        view.y = py - rowY[fr] - (at.y - headerH - rowY[fr] * s2) / s2
        clamp(view, w, h)
    }

    /** Scrolls so the chosen cell is in sight (search's next one), in the middle when it was not. */
    /** Excel's Zoom to Selection: cells [g] (as drawn) zoomed to fill the screen, at its top left. */
    fun fit(view: SheetView, g: SheetRange, w: Float, h: Float) {
        val gw = colX[min(g.c2 + 1, cols)] - colX[g.c1]; val gh = rowY[min(g.r2 + 1, rows)] - rowY[g.r1]
        if (gw <= 0f || gh <= 0f) return
        view.zoom = (min((w - headerW) / gw, (h - headerH) / gh) / density * 0.95f).coerceIn(0.25f, 4f)
        if (g.c1 >= fc) view.x = colX[g.c1] - colX[fc]
        if (g.r1 >= fr) view.y = rowY[g.r1] - rowY[fr]
        clamp(view, w, h)
        WriterLog.i("Sheets: zoomed to the chosen cells (${"%.2f".format(view.zoom)})")
    }

    fun reveal(view: SheetView, w: Float, h: Float) {
        val r = layout.row(view.row); val c = layout.col(view.col)
        if (r < 0 || c < 0) return
        val s = scale(view.zoom)
        val bodyW = (w - headerW) / s - colX[fc]; val bodyH = (h - headerH) / s - rowY[fr]
        if (c >= fc) {
            val l = colX[c] - colX[fc]; val rt = colX[c + 1] - colX[fc]
            if (l < view.x || rt > view.x + bodyW) view.x = l - max(0f, (bodyW - (rt - l)) / 2)
        }
        if (r >= fr) {
            val t = rowY[r] - rowY[fr]; val b = rowY[r + 1] - rowY[fr]
            if (t < view.y || b > view.y + bodyH) view.y = t - max(0f, (bodyH - (b - t)) / 2)
        }
        clamp(view, w, h)
    }

    /**
     * The cell under a tap (a merged one's first cell) is chosen; a tap on a row's number chooses that row's cell. A
     * tap on a column's letter returns that (sheet) column, for its menu.
     */
    fun tap(view: SheetView, at: Offset): Pair<Char, Int>? {
        val (r, c) = cellAt(view, at)
        view.range = null
        if (at.y < headerH && at.x > headerW) { view.col = cAt[c]; WriterLog.i("Sheets: column ${Sheets.colName(cAt[c])} menu"); return 'c' to cAt[c] }
        if (at.x < headerW && at.y > headerH) { view.row = rAt[r]; WriterLog.i("Sheets: row menu"); return 'r' to r }
        val g = merge(r, c)
        view.row = rAt[g?.r1 ?: r]; view.col = cAt[g?.c1 ?: c]; view.opened = false
        val cell = sheet[view.row, view.col]
        WriterLog.i("Sheets: tap ${Sheets.colName(view.col)}${view.row + 1} (${cell?.kind ?: "empty"}${if (cell?.formula != null) ", formula" else ""})")
        return null
    }

    /** The sheet's cell under a point of the screen; null on the letters and numbers. */
    fun sheetCellAt(view: SheetView, at: Offset): Pair<Int, Int>? {
        if (at.x < headerW || at.y < headerH) return null
        val (r, c) = cellAt(view, at)
        return rAt[r] to cAt[c]
    }

    /** The cell (as drawn) under a point of the screen. */
    private fun cellAt(view: SheetView, at: Offset): Pair<Int, Int> {
        val s = scale(view.zoom)
        val sx = if (at.x < headerW + colX[fc] * s) (at.x - headerW) / s else colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val sy = if (at.y < headerH + rowY[fr] * s) (at.y - headerH) / s else rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        return find(rowY, max(0f, sy), rows) to find(colX, max(0f, sx), cols)
    }

    private var anchor = 0 to 0

    var handle = false // the fill handle drawn at the chosen cells' corner
    var filling: SheetRange? = null // where it is being dragged to

    /** The chosen cells as drawn: the range, else the chosen cell (a merged one whole). */
    fun chosen(view: SheetView): SheetRange? = view.range ?: run {
        val dr = layout.row(view.row); val dc = layout.col(view.col)
        if (dr < 0 || dc < 0) null else merge(dr, dc) ?: SheetRange(dr, dc, dr, dc)
    }

    /** A drawn column's or row's edge on the screen. */
    private fun screenX(view: SheetView, c: Int): Float { val s = scale(view.zoom); return headerW + if (c <= fc) colX[c] * s else (colX[c] - view.x) * s }
    private fun screenY(view: SheetView, r: Int): Float { val s = scale(view.zoom); return headerH + if (r <= fr) rowY[r] * s else (rowY[r] - view.y) * s }

    /** A touch on the fill handle: near [g]'s bottom right corner (a finger's width). */
    fun onHandle(view: SheetView, g: SheetRange, at: Offset): Boolean =
        Offset(screenX(view, min(g.c2 + 1, cols)), screenY(view, min(g.r2 + 1, rows))).minus(at).getDistance() < 24f * density

    /** The fill handle dragged to [at]: [g] with the cells it reaches down, up, right or left (the farther way). */
    fun fillTarget(view: SheetView, g: SheetRange, at: Offset): SheetRange? {
        val (r, c) = cellAt(view, at)
        val dr = if (r > g.r2) r - g.r2 else if (r < g.r1) r - g.r1 else 0
        val dc = if (c > g.c2) c - g.c2 else if (c < g.c1) c - g.c1 else 0
        return when {
            dr != 0 && kotlin.math.abs(dr) >= kotlin.math.abs(dc) -> SheetRange(min(g.r1, r), g.c1, max(g.r2, r), g.c2)
            dc != 0 -> SheetRange(g.r1, min(g.c1, c), g.r2, max(g.c2, c))
            else -> null
        }
    }

    /** A long press on a cell starts a range there (not on the letters or numbers). */
    fun startRange(view: SheetView, at: Offset): Boolean {
        if (at.x < headerW || at.y < headerH) return false
        anchor = cellAt(view, at)
        val (r, c) = anchor
        view.range = SheetRange(r, c, r, c); view.row = rAt[r]; view.col = cAt[c]
        return true
    }

    fun extendRange(view: SheetView, at: Offset) {
        val (r, c) = cellAt(view, at)
        view.range = SheetRange(min(anchor.first, r), min(anchor.second, c), max(anchor.first, r), max(anchor.second, c))
    }

    fun draw(c: android.graphics.Canvas, w: Float, h: Float, x: Float, y: Float, zoom: Float, row: Int, col: Int, marks: SheetMarks, range: SheetRange? = null) {
        val start = System.nanoTime()
        val s = scale(zoom)
        val fw = colX[fc] * s; val fh = rowY[fr] * s
        val bx = headerW + fw; val by = headerH + fh
        // The chosen cell as drawn (a merged one's whole range).
        val dr = layout.row(row); val dc = layout.col(col)
        val sel = range ?: if (dr < 0 || dc < 0) null else merge(dr, dc) ?: SheetRange(dr, dc, dr, dc)
        pane(c, bx, by, w - bx, h - by, colX[fc] + x, rowY[fr] + y, s, sel, marks, range != null)
        if (fr > 0) pane(c, bx, headerH, w - bx, fh, colX[fc] + x, 0f, s, sel, marks, range != null)
        if (fc > 0) pane(c, headerW, by, fw, h - by, 0f, rowY[fr] + y, s, sel, marks, range != null)
        if (fr > 0 && fc > 0) pane(c, headerW, headerH, fw, fh, 0f, 0f, s, sel, marks, range != null)
        if (sheet.headings) headers(c, w, h, x, y, s, sel)
        // The frozen panes' edges, as Excel draws them (green when the viewer pinned the columns).
        line.strokeWidth = 1.5f * density; line.pathEffect = null
        line.color = 0xFFA0A0A0.toInt()
        if (fr > 0) c.drawLine(0f, by, w, by, line)
        if (pins.isNotEmpty()) { line.color = EXCEL_GREEN_ARGB; line.strokeWidth = 2.5f * density }
        if (fc > 0) c.drawLine(bx, 0f, bx, h, line)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (first < 0) { first = ms; WriterLog.i("Sheets: first draw $ms ms (${sheet.rows}×${sheet.cols}, ${sheet.cells.size} cells, $rows×$cols drawn)") }
        frames++; if (ms > 32) slow++; worst = max(worst, ms)
    }

    /** One printed page ([SheetPages]): its title rows, then its rows, at the pages' scale, from the margins. */
    fun printPage(c: android.graphics.Canvas, pages: SheetPages, page: SheetPage, grid: Boolean) {
        val k = pages.k
        val x0 = colX[page.cols.first]; val w = (colX[page.cols.last + 1] - x0) * k
        var y = pages.top
        page.titles?.let { t ->
            val h = (rowY[t.last + 1] - rowY[t.first]) * k
            pane(c, pages.left, y, w, h, x0, rowY[t.first], k, null, NO_MARKS, false, printing = true, gridLines = grid); y += h
        }
        pane(c, pages.left, y, w, (rowY[page.rows.last + 1] - rowY[page.rows.first]) * k, x0, rowY[page.rows.first], k, null, NO_MARKS, false, printing = true, gridLines = grid)
    }

    fun report() { if (frames > 1) WriterLog.i("Sheets: $frames frames drawn, $slow over 32 ms, the slowest $worst ms") }

    private fun headers(c: android.graphics.Canvas, w: Float, h: Float, x: Float, y: Float, s: Float, sel: SheetRange?) {
        fill.color = 0xFFF3F3F3.toInt()
        c.drawRect(0f, 0f, w, headerH, fill); c.drawRect(0f, 0f, headerW, h, fill)
        grid.strokeWidth = 1f; grid.color = 0xFFC8C8C8.toInt()
        val fw = colX[fc] * s; val fh = rowY[fr] * s
        val base = headerH / 2 - (headerPaint.ascent() + headerPaint.descent()) / 2
        fun col(k: Int, left: Float, right: Float) {
            if (right - left < 1f) return
            val on = sel != null && k in sel.c1..sel.c2
            val sheetCol = cAt[k]
            if (on) { fill.color = 0xFFD3E9DC.toInt(); c.drawRect(left, 0f, right, headerH, fill); fill.color = EXCEL_GREEN_ARGB; c.drawRect(left, headerH - 2 * density, right, headerH, fill) }
            else if (sheetCol in pins) { fill.color = 0xFFE4F1E9.toInt(); c.drawRect(left, 0f, right, headerH, fill) }
            c.drawLine(right, 0f, right, headerH, grid)
            val name = Sheets.colName(sheetCol)
            val marked = sheetCol in filtered || sort?.col == sheetCol
            val tw = headerPaint.measureText(name)
            val cx = if (marked) (left + right) / 2 - 6 * density else (left + right) / 2
            if (tw < right - left - (if (marked) 12 * density else 0f)) {
                headerPaint.color = if (on || sheetCol in pins) EXCEL_GREEN_ARGB else 0xFF5F5F5F.toInt()
                c.drawText(name, cx, base, headerPaint)
                // A filtered column's funnel, a sorted one's arrow, beside its letter.
                fill.color = EXCEL_GREEN_ARGB
                val mx = cx + tw / 2 + 3 * density; val my = headerH / 2
                if (sheetCol in filtered) {
                    val u = 3.5f * density
                    funnel.reset(); funnel.moveTo(mx, my - u); funnel.lineTo(mx + 2 * u, my - u); funnel.lineTo(mx + 1.25f * u, my + 0.2f * u)
                    funnel.lineTo(mx + 1.25f * u, my + u); funnel.lineTo(mx + 0.75f * u, my + u); funnel.lineTo(mx + 0.75f * u, my + 0.2f * u); funnel.close()
                    c.drawPath(funnel, fill)
                } else if (sort?.col == sheetCol) {
                    val u = 3.5f * density
                    funnel.reset()
                    if (sort.ascending) { funnel.moveTo(mx, my + u); funnel.lineTo(mx + 2 * u, my + u); funnel.lineTo(mx + u, my - u) }
                    else { funnel.moveTo(mx, my - u); funnel.lineTo(mx + 2 * u, my - u); funnel.lineTo(mx + u, my + u) }
                    funnel.close(); c.drawPath(funnel, fill)
                }
            }
        }
        fun row(k: Int, top: Float, bottom: Float) {
            if (bottom - top < 1f) return
            val on = sel != null && k in sel.r1..sel.r2
            if (on) { fill.color = 0xFFD3E9DC.toInt(); c.drawRect(0f, top, headerW, bottom, fill); fill.color = EXCEL_GREEN_ARGB; c.drawRect(headerW - 2 * density, top, headerW, bottom, fill) }
            c.drawLine(0f, bottom, headerW, bottom, grid)
            if (bottom - top >= headerPaint.textSize * 0.9f) {
                // A filtered or sorted sheet's numbers are the file's rows, blue as Excel shows them then.
                headerPaint.color = if (on) EXCEL_GREEN_ARGB else if (filtered.isNotEmpty() || sort != null) 0xFF2F5597.toInt() else 0xFF5F5F5F.toInt()
                c.drawText((rAt[k] + 1).toString(), headerW / 2, (top + bottom) / 2 - (headerPaint.ascent() + headerPaint.descent()) / 2, headerPaint)
            }
        }
        // Column letters: the frozen ones, then the scrolled ones beside them.
        c.save(); c.clipRect(headerW, 0f, w, headerH)
        for (k in 0 until fc) col(k, headerW + colX[k] * s, headerW + colX[k + 1] * s)
        c.clipRect(headerW + fw, 0f, w, headerH)
        val x0 = colX[fc] + x
        var k = find(colX, x0, cols)
        while (k < cols) { val l = headerW + fw + (colX[k] - x0) * s; if (l > w) break; col(k, l, headerW + fw + (colX[k + 1] - x0) * s); k++ }
        c.restore()
        c.save(); c.clipRect(0f, headerH, headerW, h)
        for (r in 0 until fr) row(r, headerH + rowY[r] * s, headerH + rowY[r + 1] * s)
        c.clipRect(0f, headerH + fh, headerW, h)
        val y0 = rowY[fr] + y
        var r = find(rowY, y0, rows)
        while (r < rows) { val t = headerH + fh + (rowY[r] - y0) * s; if (t > h) break; row(r, t, headerH + fh + (rowY[r + 1] - y0) * s); r++ }
        c.restore()
        c.drawLine(0f, headerH, w, headerH, grid); c.drawLine(headerW, 0f, headerW, h, grid)
    }

    private fun styleOf(r: Int, c: Int, cell: SheetCell?): SheetStyle =
        look(cell?.style ?: sheet.rowStyles[rAt[r]] ?: sheet.colStyles[cAt[c]] ?: 0) ?: SheetStyle()

    /** A style by its index: the workbook's, then the sheet's own (an Excel Table's). */
    private fun look(i: Int): SheetStyle? = sheet.look(i, styles)

    private fun face(st: SheetStyle): Typeface {
        val key = "${st.font}|${st.bold}|${st.italic}"
        return faces.getOrPut(key) {
            val base = OfficeFonts.typeface(st.font) ?: Typeface.SANS_SERIF
            Typeface.create(base, (if (st.bold) Typeface.BOLD else 0) or (if (st.italic) Typeface.ITALIC else 0))
        }
    }

    private fun textPaint(st: SheetStyle, cell: SheetCell) {
        text.typeface = face(st)
        text.textSize = st.size * 4 / 3
        text.color = cell.color ?: st.color ?: if (cell.link != null && st.color == null) 0xFF0563C1.toInt() else 0xFF000000.toInt()
        text.isUnderlineText = st.underline
        text.isStrikeThruText = st.strike
    }

    /** One pane: the sheet from ([sx0], [sy0]) drawn at ([ox], [oy]) on the screen, [w]×[h] of it, [s] screen pixels a sheet pixel. */
    private fun pane(c: android.graphics.Canvas, ox: Float, oy: Float, w: Float, h: Float, sx0: Float, sy0: Float, s: Float, sel: SheetRange?, marks: SheetMarks, ranged: Boolean,
                     printing: Boolean = false, gridLines: Boolean = sheet.grid) {
        if (w <= 0 || h <= 0) return
        c.save()
        c.clipRect(ox, oy, ox + w, oy + h)
        c.translate(ox, oy); c.scale(s, s); c.translate(-sx0, -sy0)
        val sx1 = sx0 + w / s; val sy1 = sy0 + h / s
        val c0 = find(colX, sx0, cols); val c1 = find(colX, sx1, cols)
        val r0 = find(rowY, sy0, rows); val r1 = find(rowY, sy1, rows)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawRect(sx0, sy0, sx1, sy1, fill)
        val shown = merges.filter { it.r2 >= r0 && it.r1 <= r1 && it.c2 >= c0 && it.c1 <= c1 }

        // The words first worked out, so the grid knows which lines long words cover (Excel draws no line under them).
        class Words(val cell: SheetCell, val st: SheetStyle, val left: Float, val top: Float, val right: Float, val bottom: Float, val clipL: Float, val clipR: Float, val r: Int, val c: Int)
        val words = ArrayList<Words>()
        val covered = HashSet<Long>() // key(row, column whose left line is covered)
        val reach = 40
        for (r in r0..r1) {
            if (rowY[r + 1] - rowY[r] <= 0f) continue
            for (k in max(0, c0 - reach)..min(cols - 1, c1 + reach)) {
                val cell = at(r, k) ?: continue
                if (cell.text.isEmpty() || merge(r, k) != null) continue
                val st = styleOf(r, k, cell)
                val l = colX[k]; val rt = colX[k + 1]
                if (rt - l <= 0f) continue
                var clipL = l; var clipR = rt
                val align = align(st, cell)
                if (!st.wrap && cell.kind == SheetKind.Text && st.rotation == 0) {
                    textPaint(st, cell)
                    val need = text.measureText(cell.text.substringBefore('\n')) + 4f + st.indent * 9f
                    if (need > rt - l) {
                        // Into the empty cells beside it, as far as the words need.
                        fun free(n: Int) = n in 0 until cols && merge(r, n) == null && at(r, n)?.text.isNullOrEmpty()
                        if (align == SheetAlign.Left || align == SheetAlign.General) {
                            var n = k + 1; while (clipR - l < need && free(n)) { covered += Sheet.key(r, n); clipR = colX[n + 1]; n++ }
                        } else if (align == SheetAlign.Right) {
                            var n = k - 1; while (rt - clipL < need && free(n)) { covered += Sheet.key(r, n + 1); clipL = colX[n]; n-- }
                        } else if (align == SheetAlign.Center) {
                            var a = k - 1; var b = k + 1
                            while (clipR - clipL < need && (free(a) || free(b))) {
                                if (free(a)) { covered += Sheet.key(r, a + 1); clipL = colX[a]; a-- } else a = -1
                                if (free(b)) { covered += Sheet.key(r, b); clipR = colX[b + 1]; b++ } else b = cols
                            }
                        }
                    }
                }
                if (clipR < sx0 || clipL > sx1) continue
                words += Words(cell, st, l, rowY[r], rt, rowY[r + 1], clipL, clipR, r, k)
            }
        }

        // Fills: each cell's, then each merged range's as one; search's finds over them.
        for (r in r0..r1) for (k in c0..c1) {
            if (merge(r, k) != null) continue
            val cell = at(r, k)
            val f = styleOf(r, k, cell).fill ?: continue
            fill.color = f; c.drawRect(colX[k], rowY[r], colX[k + 1], rowY[r + 1], fill)
        }
        for (g in shown) styleOf(g.r1, g.c1, at(g.r1, g.c1)).fill?.let { fill.color = it; c.drawRect(colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], fill) }
        // Conditional formatting's data bars: from the cell's left, Excel's colour fading out.
        if (sheet.bars.isNotEmpty()) for (r in r0..r1) for (k in c0..c1) {
            val b = sheet.bars[Sheet.key(rAt[r], cAt[k])] ?: continue
            val l = colX[k] + 2f; val rt = l + (colX[k + 1] - colX[k] - 4f) * b.fraction
            fill.shader = android.graphics.LinearGradient(l, 0f, rt, 0f, b.color, (b.color and 0xFFFFFF) or 0x33000000, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(l, rowY[r] + 2f, rt, rowY[r + 1] - 2f, fill)
            fill.shader = null
            line.color = b.color; line.strokeWidth = 1f / s; line.pathEffect = null
            c.drawRect(l, rowY[r] + 2f, rt, rowY[r + 1] - 2f, line)
        }
        if (marks.found.isNotEmpty()) {
            fill.color = 0xFFFFE97A.toInt()
            for (r in r0..r1) for (k in c0..c1) if (Sheet.key(rAt[r], cAt[k]) in marks.found) {
                val g = merge(r, k) ?: SheetRange(r, k, r, k)
                c.drawRect(colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], fill)
            }
        }

        // The grid: line by line, none inside a merged range, under long words, or between two filled cells.
        if (gridLines) {
            var n = 0
            fun add(x1: Float, y1: Float, x2: Float, y2: Float) {
                if (n + 4 > lines.size) lines = lines.copyOf(lines.size * 2)
                lines[n++] = x1; lines[n++] = y1; lines[n++] = x2; lines[n++] = y2
            }
            fun filled(r: Int, k: Int) = r in 0 until rows && k in 0 until cols && (merge(r, k)?.let { styleOf(it.r1, it.c1, at(it.r1, it.c1)) } ?: styleOf(r, k, at(r, k))).fill != null
            fun same(r1: Int, k1: Int, r2: Int, k2: Int): Boolean { val a = merge(r1, k1) ?: return false; return a == merge(r2, k2) }
            for (r in r0..r1) {
                if (rowY[r + 1] - rowY[r] <= 0f) continue
                for (k in c0..c1 + 1) {
                    if (k > cols || Sheet.key(r, k) in covered || same(r, k - 1, r, k) || (filled(r, k - 1) && filled(r, k))) continue
                    add(colX[k], rowY[r], colX[k], rowY[r + 1])
                }
            }
            for (r in r0..r1 + 1) {
                if (r > rows) continue
                for (k in c0..c1) {
                    if (colX[k + 1] - colX[k] <= 0f || same(r - 1, k, r, k) || (filled(r - 1, k) && filled(r, k))) continue
                    add(colX[k], rowY[r], colX[k + 1], rowY[r])
                }
            }
            grid.color = 0xFFD9D9D9.toInt(); grid.strokeWidth = 1f / s
            c.drawLines(lines, 0, n, grid)
        }

        // Borders: each cell's four edges (a merged range's cells carry its outline).
        for (r in r0..r1) for (k in c0..c1) {
            val cell = at(r, k) ?: continue
            val st = look(cell.style) ?: continue
            if (st.left == null && st.top == null && st.right == null && st.bottom == null) continue
            val l = colX[k]; val t = rowY[r]; val rt = colX[k + 1]; val b = rowY[r + 1]
            st.left?.let { edge(c, it, l, t, l, b, s) }; st.right?.let { edge(c, it, rt, t, rt, b, s) }
            st.top?.let { edge(c, it, l, t, rt, t, s) }; st.bottom?.let { edge(c, it, l, b, rt, b, s) }
        }

        // A comment's red corner, as Excel marks one (not on paper).
        fill.color = 0xFFE81123.toInt()
        val u = 7 * density / s
        if (!printing) for (r in r0..r1) for (k in c0..c1) {
            if (at(r, k)?.note == null) continue
            val g = merge(r, k) ?: SheetRange(r, k, r, k)
            val x = colX[g.c2 + 1]; val y = rowY[g.r1]
            funnel.reset(); funnel.moveTo(x - u, y); funnel.lineTo(x, y); funnel.lineTo(x, y + u); funnel.close()
            c.drawPath(funnel, fill)
        }

        // Conditional formatting's icons, at the cell's left.
        if (sheet.icons.isNotEmpty()) for (r in r0..r1) for (k in c0..c1) sheet.icons[Sheet.key(rAt[r], cAt[k])]?.let { icon(c, it, colX[k], rowY[r], rowY[r + 1]) }

        for (wd in words) {
            val ic = sheet.icons[Sheet.key(rAt[wd.r], cAt[wd.c])]
            if (ic != null && !ic.showValue) continue
            // Words aligned left move over for the icon.
            val shift = if (ic != null && align(wd.st, wd.cell) != SheetAlign.Right) min(16f, wd.bottom - wd.top - 4f) + 4f else 0f
            cellText(c, wd.cell, wd.st, wd.left + shift, wd.top, wd.right, wd.bottom, wd.clipL, wd.clipR, wd.r, wd.c)
        }
        for (g in shown) {
            val cell = at(g.r1, g.c1) ?: continue
            if (cell.text.isEmpty()) continue
            cellText(c, cell, styleOf(g.r1, g.c1, cell), colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], colX[g.c1], colX[g.c2 + 1], g.r1, g.c1)
        }

        // Pictures, charts and text boxes over the cells.
        for ((d, box) in drawn) if (box.right >= sx0 && box.left <= sx1 && box.bottom >= sy0 && box.top <= sy1) drawing(c, d, box, s)
        // the drawing chosen while editing: Excel's frame round it, where it is being dragged to
        if (!printing && figure >= 0) drawn.firstOrNull { it.first.ordinal == figure }?.second?.let { box ->
            line.pathEffect = null; line.color = EXCEL_GREEN_ARGB; line.strokeWidth = 2.5f * density / s
            c.drawRect(box.left + figureShift.x / s, box.top + figureShift.y / s, box.right + figureShift.x / s, box.bottom + figureShift.y / s, line) }

        // The chosen cell in Excel's green, search's current find in orange over it.
        line.pathEffect = null
        if (sel != null) {
            // A chosen range is shaded, as Excel's.
            if (ranged) { fill.color = 0x2E107C41; c.drawRect(colX[sel.c1], rowY[sel.r1], colX[min(sel.c2 + 1, cols)], rowY[min(sel.r2 + 1, rows)], fill) }
            line.color = EXCEL_GREEN_ARGB; line.strokeWidth = 2.5f * density / s
            c.drawRect(colX[sel.c1], rowY[sel.r1], colX[min(sel.c2 + 1, cols)], rowY[min(sel.r2 + 1, rows)], line)
            filling?.let { g ->
                line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f * density / s, 3f * density / s), 0f)
                c.drawRect(colX[g.c1], rowY[g.r1], colX[min(g.c2 + 1, cols)], rowY[min(g.r2 + 1, rows)], line)
                line.pathEffect = null
            }
            if (handle) {
                val hx = colX[min(sel.c2 + 1, cols)]; val hy = rowY[min(sel.r2 + 1, rows)]
                fill.color = android.graphics.Color.WHITE; c.drawCircle(hx, hy, 8f * density / s, fill)
                fill.color = EXCEL_GREEN_ARGB; c.drawCircle(hx, hy, 6f * density / s, fill)
            }
        }
        // Review › Show all notes: each in Excel's yellow box beside its cell, its first lines (as Excel's 144 × 79 box)
        // (from every noted cell: one in a frozen row or column has its box over the scrolling cells)
        if (marks.notes && !printing) for ((r, k) in noted) {
            val n = at(r, k)?.note ?: continue
            val g = merge(r, k) ?: SheetRange(r, k, r, k)
            val x = colX[g.c2 + 1] + 8f; val y = rowY[g.r1]
            if (x > sx1 || x + 144f < sx0 || y > sy1 || y + 80f < sy0) continue
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = 0xFF000000.toInt() }
            val lay = StaticLayout.Builder.obtain(n, 0, n.length, tp, 136).setMaxLines(5).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            fill.color = 0xFFFFFFE1.toInt(); c.drawRect(x, y, x + 144f, y + maxOf(lay.height + 8f, 40f), fill)
            line.color = 0xFF808080.toInt(); line.strokeWidth = 1f; c.drawRect(x, y, x + 144f, y + maxOf(lay.height + 8f, 40f), line)
            c.save(); c.translate(x + 4f, y + 4f); lay.draw(c); c.restore()
        }
        // View › Page break preview (Excel's): each page's cells in a blue frame with its number across them, the rest greyed
        if (marks.breaks && !printing) {
            val pg = pages.pages
            fun box(p: SheetPage) = android.graphics.RectF(colX[p.cols.first], rowY[p.rows.first], colX[p.cols.last + 1], rowY[p.rows.last + 1])
            c.save(); pg.forEach { c.clipOutRect(box(it)) }
            fill.color = 0x55808080; c.drawRect(sx0, sy0, sx1, sy1, fill); c.restore()
            val word = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40808080; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            line.color = 0xFF2F5597.toInt(); line.strokeWidth = 2.5f * density / s; line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f * density / s, 4f * density / s), 0f)
            pg.forEachIndexed { n, p -> val b = box(p)
                if (b.right < sx0 || b.left > sx1 || b.bottom < sy0 || b.top > sy1) return@forEachIndexed
                c.drawRect(b, line)
                word.textSize = min(b.width() / 4, b.height() / 3).coerceIn(12f, 120f)
                c.drawText("Page ${n + 1}", b.centerX(), b.centerY() + word.textSize / 3, word) }
            line.pathEffect = null
        }
        if (marks.current >= 0) {
            val r = layout.row(Sheet.row(marks.current)); val k = layout.col(Sheet.col(marks.current))
            if (r >= 0 && k >= 0) {
                val g = merge(r, k) ?: SheetRange(r, k, r, k)
                line.color = 0xFFF08C00.toInt(); line.strokeWidth = 4f * density / s
                c.drawRect(colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], line)
            }
        }
        c.restore()
    }

    /** An icon set's icon (Excel's arrows, lights, symbols, flags, stars, triangles, ratings, quarters, boxes), drawn. */
    private fun icon(c: android.graphics.Canvas, ic: SheetIcon, left: Float, top: Float, bottom: Float) {
        val size = min(16f, bottom - top - 4f)
        if (size <= 3f) return
        val n = ic.set.firstOrNull()?.digitToIntOrNull() ?: 3
        val t = if (n > 1) ic.index.toFloat() / (n - 1) else 1f // 0: the least, 1: the most
        val cx = left + 3f + size / 2; val cy = (top + bottom) / 2; val r = size / 2
        fun mix(a: Int, b: Int, f: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(a shr 16 and 255, b shr 16 and 255) shl 16) or (ch(a shr 8 and 255, b shr 8 and 255) shl 8) or ch(a and 255, b and 255)
        }
        val red = 0xFFE0393E.toInt(); val yellow = 0xFFFFC000.toInt(); val green = 0xFF00A651.toInt(); val gray = 0xFF8C8C8C.toInt(); val black = 0xFF262626.toInt()
        val ryg = if (t < 0.5f) mix(red, yellow, t * 2) else mix(yellow, green, (t - 0.5f) * 2)
        fill.color = ryg; fill.style = Paint.Style.FILL
        val p = funnel; p.reset()
        val set = ic.set
        when {
            "Arrows" in set -> {
                // pointing down for the least, up for the most, through the diagonals
                fill.color = if ("Gray" in set) gray else ryg
                c.save(); c.rotate(90f - 180f * t, cx, cy)
                p.moveTo(cx + r, cy); p.lineTo(cx, cy - r * 0.8f); p.lineTo(cx, cy - r * 0.3f); p.lineTo(cx - r, cy - r * 0.3f)
                p.lineTo(cx - r, cy + r * 0.3f); p.lineTo(cx, cy + r * 0.3f); p.lineTo(cx, cy + r * 0.8f); p.close()
                c.drawPath(p, fill); c.restore()
            }
            "Triangles" in set -> when (ic.index) {
                0 -> { fill.color = red; p.moveTo(cx - r, cy - r * 0.5f); p.lineTo(cx + r, cy - r * 0.5f); p.lineTo(cx, cy + r * 0.6f); p.close(); c.drawPath(p, fill) }
                n - 1 -> { fill.color = green; p.moveTo(cx - r, cy + r * 0.5f); p.lineTo(cx + r, cy + r * 0.5f); p.lineTo(cx, cy - r * 0.6f); p.close(); c.drawPath(p, fill) }
                else -> { fill.color = yellow; c.drawRect(cx - r, cy - r * 0.2f, cx + r, cy + r * 0.2f, fill) }
            }
            "Stars" in set -> {
                for (i in 0 until 10) { val a = Math.PI * (i / 5.0) - Math.PI / 2; val rr = if (i % 2 == 0) r else r * 0.45f
                    val x = cx + (rr * kotlin.math.cos(a)).toFloat(); val y = cy + (rr * kotlin.math.sin(a)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                p.close()
                fill.color = 0xFFFFC000.toInt()
                if (t >= 1f) c.drawPath(p, fill) else if (t > 0f) { c.save(); c.clipRect(cx - r, cy - r, cx, cy + r); c.drawPath(p, fill); c.restore() }
                line.color = 0xFFD9A400.toInt(); line.strokeWidth = 1f; line.pathEffect = null; c.drawPath(p, line)
            }
            "Flags" in set -> {
                fill.color = ryg
                c.drawRect(cx - r * 0.7f, cy - r, cx - r * 0.5f, cy + r, fill.apply { color = black })
                fill.color = ryg; p.moveTo(cx - r * 0.5f, cy - r); p.lineTo(cx + r, cy - r * 0.45f); p.lineTo(cx - r * 0.5f, cy + r * 0.1f); p.close(); c.drawPath(p, fill)
            }
            "Symbols" in set -> {
                c.drawCircle(cx, cy, r, fill)
                line.color = 0xFFFFFFFF.toInt(); line.strokeWidth = size / 8; line.pathEffect = null
                when (ic.index) {
                    0 -> { c.drawLine(cx - r * 0.4f, cy - r * 0.4f, cx + r * 0.4f, cy + r * 0.4f, line); c.drawLine(cx + r * 0.4f, cy - r * 0.4f, cx - r * 0.4f, cy + r * 0.4f, line) }
                    n - 1 -> { p.moveTo(cx - r * 0.45f, cy); p.lineTo(cx - r * 0.1f, cy + r * 0.4f); p.lineTo(cx + r * 0.5f, cy - r * 0.4f); c.drawPath(p, line) }
                    else -> { c.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.1f, line); c.drawCircle(cx, cy + r * 0.45f, size / 14, fill.apply { color = 0xFFFFFFFF.toInt() }) }
                }
            }
            "Rating" in set -> {
                val bars = n; val w = size / (bars * 1.5f)
                for (i in 0 until bars) {
                    fill.color = if (i <= ic.index) 0xFF2F5597.toInt() else 0xFFD0D7E5.toInt()
                    val x = cx - r + i * w * 1.5f; val h = size * (i + 1) / bars
                    c.drawRect(x, cy + r - h, x + w, cy + r, fill)
                }
            }
            "Quarters" in set -> {
                fill.color = 0xFF404040.toInt(); line.color = 0xFF404040.toInt(); line.strokeWidth = 1f; line.pathEffect = null
                c.drawCircle(cx, cy, r, line)
                c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * t, true, fill)
            }
            "Boxes" in set -> {
                val h = r * 0.9f
                for (i in 0 until 4) {
                    fill.color = if (i < ic.index) 0xFF2F5597.toInt() else 0xFFD0D7E5.toInt()
                    val x = cx - r + (i % 2) * (h + 1.5f); val y = cy - r + (i / 2) * (h + 1.5f)
                    c.drawRect(x, y, x + h, y + h, fill)
                }
            }
            else -> {
                // lights and signs: red, yellow, green (four lights: black the least; red to black: shades)
                fill.color = when {
                    "RedToBlack" in set -> mix(black, red, t)
                    n == 4 && ic.index == 0 -> black
                    n == 4 -> listOf(red, yellow, green)[ic.index - 1]
                    else -> ryg
                }
                c.drawCircle(cx, cy, r, fill)
                line.color = 0x40000000; line.strokeWidth = 1f; line.pathEffect = null; c.drawCircle(cx, cy, r, line)
            }
        }
    }

    private fun drawing(c: android.graphics.Canvas, d: SheetDrawing, box: android.graphics.RectF, s: Float) {
        when {
            d.chart != null -> charts.getOrPut(d) { ChartDrawable(d.chart) }.apply { setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()) }.draw(c)
            d.image != null -> {
                // shortcut: decoded on the drawing thread the first time it shows (pictures in sheets are small); upgrade to a background decode if one is slow.
                val bmp = pictures.getOrPut(d) {
                    WriterLog.timed("Sheets: picture decoded") {
                        runCatching {
                            val bytes = d.image.invoke() ?: return@runCatching null
                            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                            val want = max(1f, box.width() * density * 2 / (1 - (d.crop?.let { it[0] + it[2] } ?: 0f)).coerceAtLeast(0.05f)) // the part shown fills the box
                            var sample = 1; while (o.outWidth / (sample * 2) >= want) sample *= 2
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.let { Slides.cropped(it, d.crop) }
                        }.onFailure { WriterLog.w("Sheets: a picture could not be read", it) }.getOrNull()
                    }
                }
                if (bmp != null) c.drawBitmap(bmp, null, box, fill.apply { color = 0xFFFFFFFF.toInt(); isFilterBitmap = true })
            }
            else -> {
                val art = d.warp != null || d.effects.any || d.bold && (d.textPt ?: 0f) >= PictureXml.ART_PT // WordArt
                if (d.shape != null) d.shape.drawAt(c, box.left, box.top, box.width(), box.height())
                else {
                    d.fill?.let { fill.color = it; c.drawRect(box, fill) }
                    line.color = d.line ?: 0xFF7F7F7F.toInt(); line.strokeWidth = 1f / s; line.pathEffect = null
                    // a text box with neither shows a thin grey line (to be found); WordArt none
                    if (d.line != null || d.fill == null && !art) c.drawRect(box, line)
                }
                d.text?.takeIf { it.isNotEmpty() }?.let { t ->
                    text.typeface = if (d.bold) Typeface.DEFAULT_BOLD else Typeface.SANS_SERIF; text.textSize = (d.textPt ?: 11f) * 4 / 3f; text.color = d.textColor ?: 0xFF000000.toInt()
                    text.isUnderlineText = false; text.isStrikeThruText = false
                    // WordArt bent: along its bend, as Words draws it
                    val bend = d.warp
                    if (bend != null) { TextBoxDrawable.bent(c, bend, t.replace('\n', ' '), TextPaint(text), box.left, box.top, box.width(), box.height()); return@let }
                    // its letters' outline, shadow and glow: Words' EffectSpan on each word
                    val words: CharSequence = if (!d.effects.any) t else android.text.SpannableString(t).also { sp ->
                        Regex("\\S+").findAll(t).forEach { m -> sp.setSpan(EffectSpan(d.effects, text.color, 1f), m.range.first, m.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) } }
                    val l = StaticLayout.Builder.obtain(words, 0, words.length, TextPaint(text), max(1, (box.width() - 8).toInt()))
                        .setAlignment(if (d.centred) android.text.Layout.Alignment.ALIGN_CENTER else android.text.Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                    val top = if (d.centred) box.top + max(4f, (box.height() - l.height) / 2) else box.top + 4
                    c.save(); c.clipRect(box); c.translate(box.left + 4, top); l.draw(c); c.restore()
                }
            }
        }
    }

    private fun edge(c: android.graphics.Canvas, e: SheetLine, x1: Float, y1: Float, x2: Float, y2: Float, s: Float) {
        line.color = e.color
        line.pathEffect = if (e.dots) dots else if (e.dashed) dash else null
        if (e.double) {
            line.strokeWidth = max(1f, 1f / s)
            val dx = if (x1 == x2) 1f else 0f; val dy = if (y1 == y2) 1f else 0f
            c.drawLine(x1 - dx, y1 - dy, x2 - dx, y2 - dy, line); c.drawLine(x1 + dx, y1 + dy, x2 + dx, y2 + dy, line)
        } else {
            line.strokeWidth = max(e.width, 1f / s)
            c.drawLine(x1, y1, x2, y2, line)
        }
    }

    private fun align(st: SheetStyle, cell: SheetCell): SheetAlign = when (st.align) {
        SheetAlign.General -> when (cell.kind) { SheetKind.Number -> SheetAlign.Right; SheetKind.Bool, SheetKind.Error -> SheetAlign.Center; else -> SheetAlign.Left }
        else -> st.align
    }

    /** A cell's words in its box ([left]…[bottom]), clipped to [clipL]…[clipR] (wider when they run into empty cells). */
    private fun cellText(c: android.graphics.Canvas, cell: SheetCell, st: SheetStyle, left: Float, top: Float, right: Float, bottom: Float, clipL: Float, clipR: Float, r: Int, k: Int) {
        textPaint(st, cell)
        val pad = 2f + st.indent * 9f
        val align = align(st, cell)
        c.save()
        c.clipRect(clipL, top, clipR, bottom)
        if (cell.rich != null || st.wrap || st.align == SheetAlign.Justify) {
            // rich text, wrapped or justified: the Office engine, in points (a sheet pixel is ¾ pt)
            val px = 4f / 3f
            val block = laidCells.getOrPut(Sheet.key(rAt[r], cAt[k])) { cellBlock(cell, st, align, max(1f, right - left - 4f) / px) }
            c.scale(px, px)
            TextBoxDrawable.framed(c, block, (left + 2f) / px, top / px, (right - left - 4f) / px, (bottom - top) / px, 0f, 1f / px, 0f, 2f / px,
                when (st.valign) { SheetVAlign.Top -> 't'; SheetVAlign.Center -> 'c'; else -> 'b' }, false)
        } else {
            var shown = cell.text.substringBefore('\n')
            var tw = text.measureText(shown)
            // Shrink to fit (Excel's, LibreOffice's): smaller letters until the words fit the cell's width
            if (st.shrink && cell.kind == SheetKind.Text && tw > right - left - 4f - pad) { text.textSize *= (right - left - 4f - pad) / tw; tw = text.measureText(shown) }
            // A number that does not fit is never cut: General drops decimals to fit (Excel's way), else ####.
            if (cell.kind == SheetKind.Number && tw > right - left - 4f) {
                val fitted = if (st.format.equals("General", true) && cell.value != null)
                    general.general(cell.value, ((right - left - 4f) / text.measureText("0").coerceAtLeast(1f)).toInt())?.takeIf { text.measureText(it) <= right - left - 4f }
                else null
                val hash = text.measureText("#").coerceAtLeast(1f)
                shown = fitted ?: "#".repeat(max(1, ((right - left - 4f) / hash).toInt())); tw = text.measureText(shown)
            }
            val fm = text.fontMetrics
            val baseline = when (st.valign) {
                SheetVAlign.Top -> top + 1f - fm.ascent
                SheetVAlign.Center -> (top + bottom) / 2 - (fm.ascent + fm.descent) / 2
                SheetVAlign.Bottom -> bottom - 2f - fm.descent
            }
            if (st.rotation != 0) {
                val deg = if (st.rotation in 91..180) -(st.rotation - 90) else st.rotation
                c.rotate(-deg.toFloat(), (left + right) / 2, (top + bottom) / 2)
                c.drawText(shown, (left + right) / 2 - tw / 2, (top + bottom) / 2 - (fm.ascent + fm.descent) / 2, text)
            } else {
                val x = when (align) {
                    SheetAlign.Right -> right - pad - tw
                    SheetAlign.Center -> (left + right) / 2 - tw / 2
                    else -> left + pad
                }
                c.drawText(shown, x, baseline, text)
            }
        }
        c.restore()
    }

    companion object {
        val EXCEL_GREEN_ARGB = 0xFF107C41.toInt()

        /** The page's header and footer as Excel prints them: 10 pt, 0.3" from the edge, left, centre and right of the margins. */
        fun headerFooter(c: android.graphics.Canvas, pages: SheetPages, sheet: Sheet, page: Int, count: Int, file: String) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF000000.toInt() }
            val right = pages.width - sheet.print.margins[1] * 72
            for ((code, top) in listOf(sheet.print.header to true, sheet.print.footer to false)) {
                SheetHeaderFooter.parts(code ?: continue).forEachIndexed { k, part ->
                    val lines = SheetHeaderFooter.shown(part, page, count, sheet.name, file).split('\n').filter { it.isNotEmpty() }
                    lines.forEachIndexed { n, line ->
                        val y = if (top) 0.3f * 72 + 10f + n * 12f else pages.height - 0.3f * 72 - (lines.size - 1 - n) * 12f
                        val x = when (k) { 0 -> pages.left; 1 -> pages.width / 2 - paint.measureText(line) / 2; else -> right - paint.measureText(line) }
                        c.drawText(line, x, y, paint)
                    }
                }
            }
        }
        private val NO_MARKS = SheetMarks(emptySet(), -1L)

        /** The sheet as Excel prints it, one PDF page a page (Export PDF and Print). */
        fun pdf(sheet: Sheet, styles: List<SheetStyle>, layout: SheetLayout, out: java.io.OutputStream, density: Float, file: String = ""): Int {
            val pages = sheetPages(sheet, layout)
            val painter = SheetPainter(sheet, styles, density, layout)
            val doc = android.graphics.pdf.PdfDocument()
            pages.pages.forEachIndexed { i, p ->
                val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pages.width.toInt(), pages.height.toInt(), i + 1).create())
                painter.printPage(page.canvas, pages, p, sheet.print.grid)
                headerFooter(page.canvas, pages, sheet, i + 1, pages.pages.size, file)
                doc.finishPage(page)
            }
            doc.writeTo(out); doc.close()
            return pages.pages.size
        }
    }
}

/** Review › Spelling's run: the words left to look at, each word's suggestions, the words ignored. */
internal data class SpellRun(val queue: List<Triple<Long, IntRange, String>>, val suggestions: Map<String, List<String>>, val ignored: Set<String>)

/** Changes not yet saved, kept when the app went to the background: one per spreadsheet (by its path), in the app's storage. */
private fun sheetDraft(context: android.content.Context, file: File) =
    File(File(context.filesDir, "sheet-drafts"), "${file.absolutePath.hashCode().toUInt()}.${file.extension}")
