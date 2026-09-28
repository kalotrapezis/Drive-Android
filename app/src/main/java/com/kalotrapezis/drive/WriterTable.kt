package com.kalotrapezis.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

/** Colours offered for shading and lines: Word's theme row, light to strong. */
internal val TABLE_COLORS = listOf(0xFFFFFF, 0xF2F2F2, 0xD9D9D9, 0xDEEAF6, 0xE2EFD9, 0xFFF2CC, 0xFBE4D5, 0xEDE2F6,
    0x000000, 0x4472C4, 0x70AD47, 0xFFC000, 0xED7D31, 0xC00000, 0x7030A0, 0x7F7F7F)
private const val CM = 28.3465f

/** Split cells (as Word's): into how many columns and rows. A merged cell starts at what undoes its merge. */
@Composable
internal fun TableSplitDialog(start: Pair<Int, Int>, dismiss: () -> Unit, split: (Int, Int) -> Unit) {
    var columns by remember { mutableIntStateOf(start.first) }
    var rows by remember { mutableIntStateOf(start.second) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Split cell") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("Columns", columns, 1..10) { columns = it }
                Stepper("Rows", rows, 1..10) { rows = it }
                Text("Its text stays in the first new cell.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = columns * rows > 1, onClick = { split(columns, rows) }) { Text("Split") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable private fun Stepper(label: String, value: Int, range: IntRange, set: (Int) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(enabled = value > range.first, onClick = { set(value - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(enabled = value < range.last, onClick = { set(value + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }

private enum class PropTab(val label: String) { Table("Table"), Row("Row"), Column("Column"), Cell("Cell"), Borders("Borders") }

/**
 * Everything else about a table (asked 2026-09-28: the ribbon keeps the everyday tools, ⋯ opens the rest, in tabs by
 * what they change, as Word's Table Properties). Changes are tried on a copy and kept with OK.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TablePropertiesDialog(table: DocTable, row: Int, cell: Int, dismiss: () -> Unit, apply: (DocTable) -> Unit) {
    var draft by remember { mutableStateOf(table) }
    var tab by remember { mutableStateOf(PropTab.Cell) }
    val r = row.coerceIn(0, draft.rows.lastIndex)
    val c = cell.coerceIn(0, (draft.rows[r].cells.lastIndex).coerceAtLeast(0))
    val here = draft.rows[r].cells.getOrNull(c)
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Table properties", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PropTab.entries.forEach { t -> Choice(t.label, tab == t) { tab = t } }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    when (tab) {
                        PropTab.Table -> {
                            Section("Alignment on the page") {
                                listOf("left" to "Left", "center" to "Centre", "right" to "Right").forEach { (v, l) ->
                                    Choice(l, TableEdit.align(draft) == v) { draft = TableEdit.withAlign(draft, v) }
                                }
                            }
                            Section("Style") {
                                TableEdit.LOOKS.forEach { look -> LookChip(look) { draft = TableEdit.withLook(draft, look) } }
                            }
                        }
                        PropTab.Row -> {
                            val props = draft.rows[r].props
                            val height = TableEdit.rowHeight(props)
                            Section("Height of row ${r + 1}") {
                                Choice("As its text", height == null) { draft = rowSet(draft, r) { TableEdit.withRowHeight(it, null, "atLeast") } }
                                Choice("At least", height?.second == "atLeast") { draft = rowSet(draft, r) { TableEdit.withRowHeight(it, height?.first ?: 20f, "atLeast") } }
                                Choice("Exactly", height?.second == "exact") { draft = rowSet(draft, r) { TableEdit.withRowHeight(it, height?.first ?: 20f, "exact") } }
                            }
                            if (height != null) CmField("Height", height.first) { pt -> draft = rowSet(draft, r) { TableEdit.withRowHeight(it, pt, height.second) } }
                            Toggle("Repeat as header row at the top of each page", TableEdit.isHeader(props)) { on -> draft = rowSet(draft, r) { TableEdit.withHeader(it, on) } }
                            Toggle("Keep the row on one page", TableEdit.keepsTogether(props)) { on -> draft = rowSet(draft, r) { TableEdit.withKeepTogether(it, on) } }
                        }
                        PropTab.Column -> {
                            val width = TableEdit.columnWidth(draft, r, c)
                            if (width == null) Text("This table does not say how wide its columns are.")
                            else {
                                CmField("Width of this column", width) { pt -> draft = TableEdit.withColumnWidth(draft, r, c, pt) }
                                Text("The other columns share what it gives or takes, so the table keeps its width.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        PropTab.Cell -> if (here != null) {
                            val whole = TableEdit.wholeCell(draft, r, c)
                            fun set(f: (String) -> String) { draft = TableEdit.withCells(draft, whole) { it.copy(props = f(it.props)) } }
                            Section("Text in the cell") {
                                listOf("top" to "Top", "center" to "Centre", "bottom" to "Bottom").forEach { (v, l) ->
                                    Choice(l, TableEdit.vAlign(here.props) == v) { set { p -> TableEdit.withVAlign(p, v) } }
                                }
                            }
                            Section("Direction") {
                                listOf(null to "Across", "btLr" to "Upwards", "tbRl" to "Downwards").forEach { (v, l) ->
                                    Choice(l, TableEdit.textDirection(here.props) == v) { set { p -> TableEdit.withTextDirection(p, v) } }
                                }
                            }
                            Section("Shading") {
                                ColorDots(TableEdit.shading(here.props)) { fill -> set { p -> TableEdit.withShading(p, fill) } }
                            }
                        }
                        PropTab.Borders -> {
                            var color by remember { mutableIntStateOf(0x000000) }
                            var sz by remember { mutableIntStateOf(4) }
                            val line = TableEdit.Line(color, sz)
                            Section("Whole table") {
                                Choice("All lines", false) { draft = TableEdit.withTableBorders(draft, line, line) }
                                Choice("Outside only", false) { draft = TableEdit.withTableBorders(draft, line, null) }
                                Choice("Rows only", false) { draft = TableEdit.withTableBorders(draft, line, line, insideV = false) }
                                Choice("No lines", false) { draft = TableEdit.withTableBorders(draft, null, null) }
                            }
                            Section("This cell") {
                                val whole = TableEdit.wholeCell(draft, r, c)
                                Choice("Box", false) { draft = TableEdit.withCells(draft, whole) { it.copy(props = TableEdit.withCellBorders(it.props, line)) } }
                                Choice("No lines", false) { draft = TableEdit.withCells(draft, whole) { it.copy(props = TableEdit.withCellBorders(it.props, null)) } }
                                Choice("As the table", false) { draft = TableEdit.withCells(draft, whole) { it.copy(props = TableEdit.cellProp(it.props, "tcBorders", null)) } }
                            }
                            Section("Line colour") { ColorDots(color, none = false) { color = it ?: 0 } }
                            Section("Line width") {
                                listOf(2 to "¼ pt", 4 to "½ pt", 8 to "1 pt", 12 to "1½ pt", 18 to "2¼ pt").forEach { (v, l) -> Choice(l, sz == v) { sz = v } }
                            }
                            Text("Pick the colour and width first, then which lines.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(onClick = { apply(draft) }) { Text("OK") }
                }
            }
        }
    }
}

private fun rowSet(t: DocTable, r: Int, f: (String) -> String) = t.copy(rows = t.rows.mapIndexed { i, row -> if (i == r) row.copy(props = f(row.props)) else row })

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Section(title: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable private fun Choice(label: String, on: Boolean, click: () -> Unit) = Surface(
    shape = CircleShape, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.clip(CircleShape).clickable(onClick = click),
) { Text(label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }

@Composable private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(on, set)
    }

@Composable private fun ColorDots(chosen: Int?, none: Boolean = true, choose: (Int?) -> Unit) {
    if (none) Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, if (chosen == null) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
        .clickable { choose(null) }, contentAlignment = Alignment.Center) { Text("✕", style = MaterialTheme.typography.labelLarge) }
    TABLE_COLORS.forEach { c ->
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF000000 or c.toLong()))
            .border(if (chosen == c) 3.dp else 1.dp, if (chosen == c) MaterialTheme.colorScheme.primary else Color(0x55888888), CircleShape)
            .clickable { choose(c) })
    }
}

/** A table style as a small picture of itself: its header, a banded row, its lines. */
@Composable internal fun LookChip(look: TableEdit.Look, click: () -> Unit) = Column(
    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = click).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
) {
    val line = Color(0xFF000000 or look.line.toLong())
    Column(Modifier.width(56.dp).background(Color.White).border(1.dp, line)) {
        listOf(look.header, null, look.band, null).forEach { fill ->
            Box(Modifier.fillMaxWidth().height(9.dp).background(fill?.let { Color(0xFF000000 or it.toLong()) } ?: Color.White).border(0.5.dp, line.copy(alpha = if (look.inside) 1f else 0f)))
        }
    }
    Spacer(Modifier.height(2.dp))
    Text(look.name, style = MaterialTheme.typography.labelSmall)
}

/** A length in centimetres (Greece measures pages in cm), kept in points. */
@Composable private fun CmField(label: String, pt: Float, set: (Float) -> Unit) {
    var text by remember(label) { mutableStateOf(String.format(Locale.ROOT, "%.2f", pt / CM)) }
    OutlinedTextField(text, { v -> text = v; v.replace(',', '.').toFloatOrNull()?.takeIf { it in 0.1f..60f }?.let { set(it * CM) } },
        label = { Text(label) }, suffix = { Text("cm") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.widthIn(max = 240.dp))
}

/**
 * Formatting the text of a cell being typed in (asked 2026-09-28: "text format" in tables): the runs from [start] to
 * [end] changed by [f]; nothing chosen, the whole cell (on a phone, choosing letters in a small box is fiddly).
 */
internal fun formatCell(cell: DocCell, start: Int, end: Int, f: (DocRun) -> DocRun): DocCell {
    val length = WriterBridge.cellText(cell).length
    val (a, b) = if (start == end) 0 to length else minOf(start, end) to maxOf(start, end)
    var at = 0
    return cell.copy(paras = cell.paras.map { p ->
        val from = at; at += p.text.length + 1
        val lo = (a - from).coerceIn(0, p.text.length); val hi = (b - from).coerceIn(0, p.text.length)
        if (lo >= hi || p.table != null) p else {
            var x = 0
            p.copy(runs = OfficeFormats.tidy(p.runs.flatMap { r ->
                val s = x; x += r.text.length
                if (r.image != null || hi <= s || lo >= x) listOf(r) else listOfNotNull(
                    r.copy(text = r.text.substring(0, (lo - s).coerceAtLeast(0))).takeIf { it.text.isNotEmpty() },
                    f(r.copy(text = r.text.substring((lo - s).coerceAtLeast(0), (hi - s).coerceAtMost(r.text.length)))),
                    r.copy(text = r.text.substring((hi - s).coerceAtMost(r.text.length))).takeIf { it.text.isNotEmpty() })
            }).ifEmpty { p.runs })
        }
    })
}

/** The paragraphs from [start] to [end] (or the whole cell) aligned. */
internal fun alignCell(cell: DocCell, start: Int, end: Int, align: DocAlign): DocCell {
    val all = start == end
    var at = 0
    return cell.copy(paras = cell.paras.map { p ->
        val from = at; at += p.text.length + 1
        if (all || (minOf(start, end) <= from + p.text.length && maxOf(start, end) >= from)) p.copy(align = align) else p
    })
}

/** The look of the letter at [pos] (the one before it, at the end of a stretch). */
internal fun cellRunAt(cell: DocCell, pos: Int): DocRun? {
    var at = 0
    for (p in cell.paras) {
        var x = at
        for (r in p.runs) { if (r.image == null && pos >= x && pos <= x + r.text.length && r.text.isNotEmpty()) return r; x += r.text.length }
        at += p.text.length + 1
    }
    return cell.paras.firstOrNull()?.runs?.firstOrNull()
}

/** The cell's text as the box shows it: each run's look, sizes at the table's scale ([spPerPt]). */
internal fun cellAnnotated(cell: DocCell, spPerPt: Float, sizeOf: (DocPara, DocRun) -> Float): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        cell.paras.forEachIndexed { i, p ->
            if (i > 0) append('\n')
            p.runs.forEach { r ->
                val deco = listOfNotNull(androidx.compose.ui.text.style.TextDecoration.Underline.takeIf { r.underline || r.link != null },
                    androidx.compose.ui.text.style.TextDecoration.LineThrough.takeIf { r.strike })
                withStyle(androidx.compose.ui.text.SpanStyle(
                    fontWeight = if (r.bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    fontStyle = if (r.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    textDecoration = if (deco.isEmpty()) null else androidx.compose.ui.text.style.TextDecoration.combine(deco),
                    color = r.color?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified,
                    background = r.highlight?.let { Color(0xFF000000 or it.toLong()) } ?: Color.Unspecified,
                    fontSize = androidx.compose.ui.unit.TextUnit(sizeOf(p, r) * spPerPt, androidx.compose.ui.unit.TextUnitType.Sp),
                )) { append(r.text) }
            }
        }
    }

/** Home's tools for the text of a cell being typed in: its looks, sizes, colours and alignment. */
internal fun cellHomePages(tint: Color, cell: DocCell, sel: androidx.compose.ui.text.TextRange, sizeOf: (DocPara, DocRun) -> Float,
    change: (DocCell) -> Unit): List<@Composable () -> Unit> {
    val look = cellRunAt(cell, if (sel.collapsed) sel.min else sel.min + 1) ?: DocRun("")
    fun apply(f: (DocRun) -> DocRun) = change(formatCell(cell, sel.min, sel.max, f))
    val para = cell.paras.firstOrNull() ?: DocPara(emptyList())
    val pt = sizeOf(para, look).let { kotlin.math.round(it).toInt() }
    return listOf({
        Tool(R.drawable.ic_format_bold, "Bold", tint, on = look.bold) { apply { it.copy(bold = !look.bold) } }
        Tool(R.drawable.ic_format_italic, "Italic", tint, on = look.italic) { apply { it.copy(italic = !look.italic) } }
        Tool(R.drawable.ic_format_underlined, "Underline", tint, on = look.underline) { apply { it.copy(underline = !look.underline) } }
        Tool(R.drawable.ic_format_strikethrough, "Strikethrough", tint, on = look.strike) { apply { it.copy(strike = !look.strike) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tool(R.drawable.ic_remove, "Smaller text", tint, enabled = pt > 6) { apply { it.copy(sizePt = (pt - 1).toFloat()) } }
            Text("$pt", color = tint, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, modifier = Modifier.width(26.dp))
            Tool(R.drawable.ic_add, "Bigger text", tint, enabled = pt < 72) { apply { it.copy(sizePt = (pt + 1).toFloat()) } }
        }
    }, {
        Swatches(tint, R.drawable.ic_format_color_text, "Text colour", listOf(Color.Black, Color(0xFFC00000), Color(0xFF0070C0), Color(0xFF00B050), Color(0xFF7030A0), Color(0xFFED7D31), Color.White)) { c ->
            apply { it.copy(color = c?.let { k -> k.toArgb() and 0xFFFFFF }) }
        }
        Swatches(tint, R.drawable.ic_highlighter, "Highlight", listOf(Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFFFF00FF))) { c ->
            apply { it.copy(highlight = c?.let { k -> k.toArgb() and 0xFFFFFF }) }
        }
    }, {
        listOf(DocAlign.Start to R.drawable.ic_align_left, DocAlign.Center to R.drawable.ic_align_center, DocAlign.End to R.drawable.ic_align_right, DocAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
            Tool(icon, "Align ${a.name.lowercase(Locale.ROOT)}", tint, on = para.align == a) { change(alignCell(cell, sel.min, sel.max, a)) }
        }
    })
}

/**
 * A picture turned a quarter ([turn] 90 or -90) or flipped (asked 2026-09-28: pictures had few tools): as with Crop,
 * the result is a new picture, as big on the page (width and height swapped when turned), wrapped as it was.
 * ponytail: Word's own rotation (a:xfrm rot) would keep the original; a turned copy is what LibreOffice and Word show alike.
 */
internal fun transformedPicture(img: DocImage, bytes: ByteArray, turn: Int, flip: Boolean): DocImage? {
    val bitmap = decodePicture(bytes, 2400) ?: return null
    val m = android.graphics.Matrix().apply { if (turn != 0) postRotate(turn.toFloat()); if (flip) postScale(-1f, 1f) }
    val out = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    val png = img.extension == "png" || img.part?.endsWith(".png", ignoreCase = true) == true
    val encoded = java.io.ByteArrayOutputStream().also { out.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
    val (w, h) = if (turn != 0) img.heightPt to img.widthPt else img.widthPt to img.heightPt
    return DocImage(img.key, null, w, h, null, encoded, if (png) "png" else "jpg", PictureXml.wrapOf(img))
}
