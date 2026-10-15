package com.kalotrapezis.drive

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
    val file = remember(open) { Uri.parse(open).path?.let(::File) }
    var book by remember { mutableStateOf<Workbook?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(-1) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val views = remember { HashMap<Int, SheetView>() }
    remember { OfficeFonts.assets = context.applicationContext.assets }

    // Search: the words, the cells found (in the order drawn), the one shown.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Long>>(emptyList()) }
    var current by remember { mutableIntStateOf(0) }
    var columnMenu by remember { mutableStateOf<Pair<Int, Float>?>(null) } // the sheet column, and where its letter was tapped
    var filtering by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    BackHandler { if (searching) { searching = false; query = "" } else back() }

    LaunchedEffect(open) {
        WriterLog.init(context)
        val f = file ?: run { failed = "This file cannot be opened."; return@LaunchedEffect }
        WriterLog.i("Sheets: open .${f.extension} of ${f.length() / 1024} KB")
        withContext(Dispatchers.IO) {
            runCatching {
                val sym = DecimalFormatSymbols.getInstance()
                WriterLog.timed("Sheets: workbook read") { Sheets.read(f, sym.decimalSeparator, sym.groupingSeparator) }
            }
        }.onSuccess { b ->
            book = b
            index = if (b.tabs[b.active].hidden) b.tabs.indexOfFirst { !it.hidden }.coerceAtLeast(0) else b.active
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

    // The order rows and columns are drawn in: worked out away from the screen (a sort of 300,000 rows takes a moment).
    // Kept with the sheet it was made for, so a new sheet is never drawn in the last one's order.
    var laid by remember { mutableStateOf<Pair<Sheet, SheetLayout>?>(null) }
    val layout = laid?.takeIf { it.first === sheet }?.second
    val pins = view.pins.toList(); val filters = view.filters.toMap(); val sort = view.sort
    LaunchedEffect(sheet, pins, filters, sort) {
        val s = sheet ?: return@LaunchedEffect
        laid = s to withContext(Dispatchers.Default) { WriterLog.timed("Sheets: layout (${pins.size} pinned, ${filters.size} filtered, sorted ${sort != null})") { SheetLayout.of(s, pins, filters, sort) } }
    }
    LaunchedEffect(sheet, query, searching, layout) {
        val s = sheet; val l = layout
        if (!searching || s == null || l == null || query.isBlank()) { found = emptyList(); return@LaunchedEffect }
        delay(250)
        val start = System.nanoTime()
        found = withContext(Dispatchers.Default) {
            SheetSearch.find(s, query).filter { l.row(Sheet.row(it)) >= 0 && l.col(Sheet.col(it)) >= 0 }
                .sortedWith(compareBy({ l.row(Sheet.row(it)) }, { l.col(Sheet.col(it)) }))
        }
        current = 0
        WriterLog.i("Sheets: search of ${query.length} letters, ${found.size} found in ${(System.nanoTime() - start) / 1_000_000} ms")
        found.firstOrNull()?.let { view.row = Sheet.row(it); view.col = Sheet.col(it); view.reveal++ }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.statusBarsPadding()) {
                if (searching) SearchBar(query, { query = it }, found.size, current,
                    move = { d -> if (found.isNotEmpty()) { current = (current + d).mod(found.size); found[current].let { view.row = Sheet.row(it); view.col = Sheet.col(it); view.reveal++ } } },
                    close = { searching = false; query = "" })
                else Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text(file?.name ?: "Spreadsheet", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("View only", style = MaterialTheme.typography.labelSmall, color = islandContentColor().copy(alpha = 0.7f))
                    }
                    if (sheet != null) IconButton(onClick = { searching = true }) { Icon(painterResource(R.drawable.ic_search), contentDescription = "Search") }
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
                            DropdownMenuItem(text = { Text("About this file") }, onClick = { menu = false; about = true })
                            DropdownMenuItem(text = { Text("Send log") }, onClick = { menu = false; WriterLog.export(context)?.let { shareDriveFile(context, it) } })
                        }
                    }
                }
                sheet?.let { CellBar(it, view, ::follow) }
                if (pins.isNotEmpty() || filters.isNotEmpty() || sort != null) ToolChips(view)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            val s = sheet; val b = book; val l = layout
            when {
                failed != null -> Text(failed!!, color = Color.Black, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                s != null && b != null && l != null -> {
                    SheetGrid(s, b.styles, l, view, SheetMarks(found.toHashSet(), found.getOrNull(current) ?: -1L)) { col, x -> columnMenu = col to x }
                    columnMenu?.let { (col, x) -> ColumnMenu(col, x, view, { columnMenu = null }, { filtering = col }) }
                }
                else -> Text("Opening…", color = Color.DarkGray, modifier = Modifier.align(Alignment.Center))
            }
        }
        book?.let { b -> SheetTabs(b, index, sheet) { i -> WriterLog.i("Sheets: tab ${i + 1} of ${b.tabs.size}"); failed = null; index = i } }
    }
    val s = sheet
    filtering?.let { col -> if (s != null) FilterDialog(s, col, view) { filtering = null } }
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
private fun openElsewhere(context: android.content.Context, file: File) {
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
    var sort by mutableStateOf<SheetSort?>(null)
}

/** What search found, to be drawn: the cells, and the one shown now. */
internal class SheetMarks(val found: Set<Long>, val current: Long)

@Composable
private fun SearchBar(query: String, change: (String) -> Unit, count: Int, current: Int, move: (Int) -> Unit, close: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close search") }
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search the sheet", style = MaterialTheme.typography.titleMedium, color = islandContentColor().copy(alpha = 0.5f))
            BasicTextField(query, change, singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = islandContentColor()),
                cursorBrush = SolidColor(islandContentColor()), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { move(1) }), modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
        if (query.isNotBlank()) Text(if (count == 0) "None" else "${current + 1} of $count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(onClick = { move(-1) }, enabled = count > 0) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = "Previous") }
        IconButton(onClick = { move(1) }, enabled = count > 0) { Icon(painterResource(R.drawable.ic_keyboard_arrow_up), contentDescription = "Next", modifier = Modifier.rotate(180f)) }
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

/** A tap on a column's letter: its filter, sort and pin, as Excel's header menu. */
@Composable
private fun ColumnMenu(col: Int, x: Float, view: SheetView, dismiss: () -> Unit, filter: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.offset { IntOffset(x.roundToInt(), (28 * density.density).roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = dismiss) {
            Text("Column ${Sheets.colName(col)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            DropdownMenuItem(text = { Text(if (col in view.filters) "Change filter…" else "Filter…") }, leadingIcon = { Icon(painterResource(R.drawable.ic_filter), null) },
                onClick = { dismiss(); filter() })
            if (col in view.filters) DropdownMenuItem(text = { Text("Clear filter") }, onClick = { dismiss(); WriterLog.i("Sheets: filter cleared"); view.filters.remove(col) })
            DropdownMenuItem(text = { Text("Sort A → Z") }, leadingIcon = { Icon(painterResource(R.drawable.ic_sort_name), null) },
                onClick = { dismiss(); WriterLog.i("Sheets: sort up"); view.sort = SheetSort(col, true) })
            DropdownMenuItem(text = { Text("Sort Z → A") }, leadingIcon = { Icon(painterResource(R.drawable.ic_sort_name), null, modifier = Modifier.rotate(180f)) },
                onClick = { dismiss(); WriterLog.i("Sheets: sort down"); view.sort = SheetSort(col, false) })
            if (col in view.pins) DropdownMenuItem(text = { Text("Unpin column") }, leadingIcon = { Icon(painterResource(R.drawable.ic_push_pin), null) },
                onClick = { dismiss(); WriterLog.i("Sheets: unpinned"); view.pins.remove(col) })
            else DropdownMenuItem(text = { Text("Pin to the start") }, leadingIcon = { Icon(painterResource(R.drawable.ic_push_pin), null) },
                onClick = { dismiss(); view.pins.add(col); WriterLog.i("Sheets: pinned, ${view.pins.size} now"); view.x = 0f })
        }
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

/** The chosen cell's name and what is in it: the formula when it has one, the words in full. */
@Composable
private fun CellBar(sheet: Sheet, view: SheetView, follow: (String) -> Unit) {
    val merge = sheet.merge(view.row, view.col)
    val r = merge?.r1 ?: view.row; val c = merge?.c1 ?: view.col
    val cell = sheet[r, c]
    val content = cell?.formula ?: cell?.text.orEmpty()
    Row(Modifier.fillMaxWidth().clickable { view.opened = !view.opened }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
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
private fun SheetTabs(book: Workbook, index: Int, open: Sheet?, pick: (Int) -> Unit) {
    if (book.tabs.count { !it.hidden } < 2) return
    Surface(color = islandColor(), contentColor = islandContentColor(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            book.tabs.forEachIndexed { i, tab ->
                if (tab.hidden) return@forEachIndexed
                val on = i == index
                val mark = open?.takeIf { on }?.tab?.let { Color(it) }
                Surface(
                    shape = RoundedCornerShape(10.dp), color = if (on) EXCEL_GREEN else Color.Transparent,
                    contentColor = if (on) Color.White else islandContentColor(),
                    modifier = Modifier.clickable { if (!on) pick(i) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        if (mark != null) Box(Modifier.width(24.dp).heightIn(min = 3.dp, max = 3.dp).background(mark))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(sheet: Sheet, styles: List<SheetStyle>, layout: SheetLayout, view: SheetView, marks: SheetMarks, column: (Int, Float) -> Unit) {
    val density = LocalDensity.current.density
    val painter = remember(sheet, layout) { SheetPainter(sheet, styles, density, layout, view.pins.toList(), view.filters.keys.toSet(), view.sort) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var fling by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(painter) { onDispose { painter.report() } }
    LaunchedEffect(view.reveal, painter, size) { if (view.reveal > 0 && size != IntSize.Zero) painter.reveal(view, size.width.toFloat(), size.height.toFloat()) }
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it; painter.clamp(view, it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(painter) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fling?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false; var pinched = false; var travel = Offset.Zero
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                        val zoom = e.calculateZoom(); val pan = e.calculatePan()
                        if (!moved) { travel += pan; if (travel.getDistance() > viewConfiguration.touchSlop || zoom != 1f) moved = true }
                        if (moved) {
                            if (zoom != 1f) { pinched = true; painter.zoomAt(view, zoom, e.calculateCentroid(useCurrent = true), size.width.toFloat(), size.height.toFloat()) }
                            painter.pan(view, pan, size.width.toFloat(), size.height.toFloat())
                            e.changes.forEach { it.consume() }
                        }
                        e.changes.firstOrNull { it.id == down.id && it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    }
                    if (!moved) painter.tap(view, down.position)?.let { col -> column(col, down.position.x) }
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
        val x = view.x; val y = view.y; val zoom = view.zoom; val row = view.row; val col = view.col
        drawIntoCanvas { painter.draw(it.nativeCanvas, this.size.width, this.size.height, x, y, zoom, row, col, marks) }
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
) {
    private val rAt = layout.rows; private val cAt = layout.cols
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
    private val pictures = HashMap<SheetDrawing, android.graphics.Bitmap?>()
    private val charts = HashMap<SheetDrawing, ChartDrawable>()
    private fun at(r: Int, c: Int) = sheet[rAt[r], cAt[c]]

    private val headerH = 24 * density
    // Not in apply: TextPaint has a density of its own (1), which `density` there would mean.
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.textSize = 12 * density; it.color = 0xFF5F5F5F.toInt(); it.textAlign = Paint.Align.CENTER }
    private val headerW = max(36 * density, headerPaint.measureText(((rAt.maxOrNull() ?: 0) + 1).toString()) + 14 * density)
    private val fill = Paint()
    private val line = Paint().apply { style = Paint.Style.STROKE }
    private val grid = Paint().apply { color = 0xFFD9D9D9.toInt(); style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val dash = DashPathEffect(floatArrayOf(3f, 2f), 0f)
    private val dots = DashPathEffect(floatArrayOf(1f, 1f), 0f)
    private val faces = HashMap<String, Typeface>()
    private val wrapped = HashMap<Long, StaticLayout>()
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
    fun tap(view: SheetView, at: Offset): Int? {
        val s = scale(view.zoom)
        val sx = if (at.x < headerW + colX[fc] * s) (at.x - headerW) / s else colX[fc] + view.x + (at.x - headerW - colX[fc] * s) / s
        val sy = if (at.y < headerH + rowY[fr] * s) (at.y - headerH) / s else rowY[fr] + view.y + (at.y - headerH - rowY[fr] * s) / s
        val c = find(colX, max(0f, sx), cols); val r = find(rowY, max(0f, sy), rows)
        if (at.y < headerH && at.x > headerW) { view.col = cAt[c]; WriterLog.i("Sheets: column ${Sheets.colName(cAt[c])} menu"); return cAt[c] }
        val g = merge(r, c)
        view.row = rAt[g?.r1 ?: r]; view.col = cAt[g?.c1 ?: c]; view.opened = false
        val cell = sheet[view.row, view.col]
        WriterLog.i("Sheets: tap ${Sheets.colName(view.col)}${view.row + 1} (${cell?.kind ?: "empty"}${if (cell?.formula != null) ", formula" else ""})")
        return null
    }

    fun draw(c: android.graphics.Canvas, w: Float, h: Float, x: Float, y: Float, zoom: Float, row: Int, col: Int, marks: SheetMarks) {
        val start = System.nanoTime()
        val s = scale(zoom)
        val fw = colX[fc] * s; val fh = rowY[fr] * s
        val bx = headerW + fw; val by = headerH + fh
        // The chosen cell as drawn (a merged one's whole range).
        val dr = layout.row(row); val dc = layout.col(col)
        val sel = if (dr < 0 || dc < 0) null else merge(dr, dc) ?: SheetRange(dr, dc, dr, dc)
        pane(c, bx, by, w - bx, h - by, colX[fc] + x, rowY[fr] + y, s, sel, marks)
        if (fr > 0) pane(c, bx, headerH, w - bx, fh, colX[fc] + x, 0f, s, sel, marks)
        if (fc > 0) pane(c, headerW, by, fw, h - by, 0f, rowY[fr] + y, s, sel, marks)
        if (fr > 0 && fc > 0) pane(c, headerW, headerH, fw, fh, 0f, 0f, s, sel, marks)
        headers(c, w, h, x, y, s, sel)
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
    private fun look(i: Int): SheetStyle? = styles.getOrNull(i) ?: sheet.looks.getOrNull(i - styles.size)

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
    private fun pane(c: android.graphics.Canvas, ox: Float, oy: Float, w: Float, h: Float, sx0: Float, sy0: Float, s: Float, sel: SheetRange?, marks: SheetMarks) {
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
        if (marks.found.isNotEmpty()) {
            fill.color = 0xFFFFE97A.toInt()
            for (r in r0..r1) for (k in c0..c1) if (Sheet.key(rAt[r], cAt[k]) in marks.found) {
                val g = merge(r, k) ?: SheetRange(r, k, r, k)
                c.drawRect(colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], fill)
            }
        }

        // The grid: line by line, none inside a merged range, under long words, or between two filled cells.
        if (sheet.grid) {
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

        // A comment's red corner, as Excel marks one.
        fill.color = 0xFFE81123.toInt()
        val u = 7 * density / s
        for (r in r0..r1) for (k in c0..c1) {
            if (at(r, k)?.note == null) continue
            val g = merge(r, k) ?: SheetRange(r, k, r, k)
            val x = colX[g.c2 + 1]; val y = rowY[g.r1]
            funnel.reset(); funnel.moveTo(x - u, y); funnel.lineTo(x, y); funnel.lineTo(x, y + u); funnel.close()
            c.drawPath(funnel, fill)
        }

        for (wd in words) cellText(c, wd.cell, wd.st, wd.left, wd.top, wd.right, wd.bottom, wd.clipL, wd.clipR, wd.r, wd.c)
        for (g in shown) {
            val cell = at(g.r1, g.c1) ?: continue
            if (cell.text.isEmpty()) continue
            cellText(c, cell, styleOf(g.r1, g.c1, cell), colX[g.c1], rowY[g.r1], colX[g.c2 + 1], rowY[g.r2 + 1], colX[g.c1], colX[g.c2 + 1], g.r1, g.c1)
        }

        // Pictures, charts and text boxes over the cells.
        for ((d, box) in drawn) if (box.right >= sx0 && box.left <= sx1 && box.bottom >= sy0 && box.top <= sy1) drawing(c, d, box, s)

        // The chosen cell in Excel's green, search's current find in orange over it.
        line.pathEffect = null
        if (sel != null) {
            line.color = EXCEL_GREEN_ARGB; line.strokeWidth = 2.5f * density / s
            c.drawRect(colX[sel.c1], rowY[sel.r1], colX[min(sel.c2 + 1, cols)], rowY[min(sel.r2 + 1, rows)], line)
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
                            val want = max(1f, box.width() * density * 2)
                            var sample = 1; while (o.outWidth / (sample * 2) >= want) sample *= 2
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                        }.onFailure { WriterLog.w("Sheets: a picture could not be read", it) }.getOrNull()
                    }
                }
                if (bmp != null) c.drawBitmap(bmp, null, box, fill.apply { color = 0xFFFFFFFF.toInt(); isFilterBitmap = true })
            }
            else -> {
                d.fill?.let { fill.color = it; c.drawRect(box, fill) }
                line.color = d.line ?: 0xFF7F7F7F.toInt(); line.strokeWidth = 1f / s; line.pathEffect = null
                if (d.line != null || d.fill == null) c.drawRect(box, line)
                d.text?.takeIf { it.isNotEmpty() }?.let { t ->
                    text.typeface = Typeface.SANS_SERIF; text.textSize = 11 * 4 / 3f; text.color = 0xFF000000.toInt(); text.isUnderlineText = false; text.isStrikeThruText = false
                    val l = StaticLayout.Builder.obtain(t, 0, t.length, TextPaint(text), max(1, (box.width() - 8).toInt())).setIncludePad(false).build()
                    c.save(); c.clipRect(box); c.translate(box.left + 4, box.top + 4); l.draw(c); c.restore()
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
        if (st.wrap || st.align == SheetAlign.Justify) {
            val width = max(1f, right - left - 4f).toInt()
            val layout = wrapped.getOrPut(Sheet.key(rAt[r], cAt[k])) {
                val a = when (align) { SheetAlign.Center -> Layout.Alignment.ALIGN_CENTER; SheetAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
                StaticLayout.Builder.obtain(cell.text, 0, cell.text.length, TextPaint(text), width).setAlignment(a).setIncludePad(false).build()
            }
            val ty = when (st.valign) { SheetVAlign.Top -> top + 1f; SheetVAlign.Center -> (top + bottom - layout.height) / 2; SheetVAlign.Bottom -> bottom - 2f - layout.height }
            c.translate(left + 2f, ty)
            layout.draw(c)
        } else {
            var shown = cell.text.substringBefore('\n')
            var tw = text.measureText(shown)
            // A number that does not fit is ####, never cut.
            if (cell.kind == SheetKind.Number && tw > right - left - 4f) {
                val hash = text.measureText("#").coerceAtLeast(1f)
                shown = "#".repeat(max(1, ((right - left - 4f) / hash).toInt())); tw = text.measureText(shown)
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

    companion object { val EXCEL_GREEN_ARGB = 0xFF107C41.toInt() }
}
