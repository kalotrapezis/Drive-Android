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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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

/** The line spacings offered (Word's everyday ones). */
internal val LINE_SPACINGS = listOf(1f, 1.15f, 1.5f, 2f)
/** A number as people write it: 1, 1.15, 6.5. */
internal fun plain(v: Float) = if (v % 1f == 0f) v.toInt().toString() else v.toString()

/**
 * Home's ⋯ page (asked 2026-09-27: "set everything once, then type"): the chosen paragraphs' line spacing, space
 * before and after (pt, as Word counts them) and indents (cm). [apply] gets the new look; what was not touched stays.
 */
@Composable
internal fun ParagraphDialog(look: ParaLook, dismiss: () -> Unit, apply: (ParaLook) -> Unit) {
    var draft by remember { mutableStateOf(look) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().imePadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Paragraph", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Section("Line spacing") {
                        LINE_SPACINGS.forEach { m ->
                            Choice(plain(m), draft.lineExact == null && kotlin.math.abs(draft.lineMultiple - m) < 0.01f) {
                                draft = draft.copy(lineMultiple = m, lineExact = null, lineAtLeast = false)
                            }
                        }
                        // What the paragraph has when it is none of these (Word's 1.08, an exact height): shown, and back with a tap.
                        val own = look.lineExact?.let { "${if (look.lineAtLeast) "At least" else "Exactly"} ${plain(Math.round(it * 10) / 10f)} pt" }
                            ?: plain(Math.round(look.lineMultiple * 100) / 100f).takeIf { LINE_SPACINGS.none { m -> kotlin.math.abs(look.lineMultiple - m) < 0.01f } }
                        own?.let { Choice(it, draft.lineExact == look.lineExact && draft.lineAtLeast == look.lineAtLeast && draft.lineMultiple == look.lineMultiple) {
                            draft = draft.copy(lineMultiple = look.lineMultiple, lineExact = look.lineExact, lineAtLeast = look.lineAtLeast)
                        } }
                    }
                    Section("Space between paragraphs") {
                        PtField("Before", draft.before) { draft = draft.copy(before = it) }
                        PtField("After", draft.after) { draft = draft.copy(after = it) }
                    }
                    Section("Indent") {
                        CmField("Left", draft.left, least = 0f) { draft = draft.copy(left = it) }
                        CmField("Right", draft.right, least = 0f) { draft = draft.copy(right = it) }
                    }
                    Section("First line") {
                        Choice("None", draft.firstLine == 0f) { draft = draft.copy(firstLine = 0f) }
                        Choice("Indented", draft.firstLine > 0f) { draft = draft.copy(firstLine = if (draft.firstLine > 0f) draft.firstLine else 1.25f * CM) }
                        Choice("Hanging", draft.firstLine < 0f) { draft = draft.copy(firstLine = if (draft.firstLine < 0f) draft.firstLine else -1.25f * CM) }
                    }
                    if (draft.firstLine != 0f) CmField(if (draft.firstLine > 0f) "Indented by" else "Hanging by", kotlin.math.abs(draft.firstLine)) { v ->
                        draft = draft.copy(firstLine = if (draft.firstLine < 0f) -v else v)
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

/** The fonts offered for a style: Word's everyday ones (Writer draws them with their metric twins). */
internal val STYLE_FONTS = listOf("Calibri", "Cambria", "Times New Roman", "Arial", "Courier New")
private val STYLE_COLORS = listOf(0x000000, 0x404040, 0x595959, 0x1F3864, 0x2F5496, 0x4472C4, 0xC00000, 0x538135, 0x7030A0)

/**
 * A style's own page (asked 2026-09-27: a style's font, size, bold, italic, colour, alignment, line spacing, space
 * before and after, indents — changes the document's style, so every paragraph with it changes, in Word and
 * LibreOffice too). [apply] gets only what was changed.
 */
@Composable
internal fun StyleDialog(entry: StyleEntry, look: ParaLook, basePt: Float, dismiss: () -> Unit, apply: (StyleChange) -> Unit) {
    var d by remember { mutableStateOf(look.copy(sizePt = look.sizePt ?: basePt, align = look.align ?: DocAlign.Start)) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().imePadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Style: ${entry.name}", style = MaterialTheme.typography.titleLarge)
                Text("Every paragraph in this style changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Section("Font") {
                        (STYLE_FONTS + listOfNotNull(d.font?.takeIf { f -> STYLE_FONTS.none { it.equals(f, true) } })).forEach { f -> Choice(f, d.font.equals(f, true)) { d = d.copy(font = f) } }
                    }
                    Section("Size and look") {
                        PtField("Size", d.sizePt ?: basePt) { if (it >= 1f) d = d.copy(sizePt = it) }
                        Choice("Bold", d.bold == true) { d = d.copy(bold = d.bold != true) }
                        Choice("Italic", d.italic == true) { d = d.copy(italic = d.italic != true) }
                    }
                    Section("Colour") {
                        Choice("Automatic", d.color == null) { d = d.copy(color = null) }
                        STYLE_COLORS.forEach { c ->
                            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF000000 or c.toLong()))
                                .border(if (d.color == c) 3.dp else 1.dp, if (d.color == c) MaterialTheme.colorScheme.primary else Color(0x55888888), CircleShape)
                                .clickable { d = d.copy(color = c) })
                        }
                    }
                    Section("Alignment") {
                        listOf(DocAlign.Start to "Left", DocAlign.Center to "Centre", DocAlign.End to "Right", DocAlign.Justify to "Justify").forEach { (a, l) -> Choice(l, d.align == a) { d = d.copy(align = a) } }
                    }
                    Section("Line spacing") {
                        LINE_SPACINGS.forEach { m -> Choice(plain(m), d.lineExact == null && kotlin.math.abs(d.lineMultiple - m) < 0.01f) { d = d.copy(lineMultiple = m, lineExact = null) } }
                        if (look.lineExact == null && LINE_SPACINGS.none { kotlin.math.abs(look.lineMultiple - it) < 0.01f })
                            Choice(plain(Math.round(look.lineMultiple * 100) / 100f), d.lineExact == null && d.lineMultiple == look.lineMultiple) { d = d.copy(lineMultiple = look.lineMultiple) }
                    }
                    Section("Space between paragraphs") {
                        PtField("Before", d.before) { d = d.copy(before = it) }
                        PtField("After", d.after) { d = d.copy(after = it) }
                    }
                    Section("Indent") {
                        CmField("Left", d.left, least = 0f) { d = d.copy(left = it) }
                        Choice("First line: none", d.firstLine == 0f) { d = d.copy(firstLine = 0f) }
                        Choice("Indented", d.firstLine > 0f) { d = d.copy(firstLine = if (d.firstLine > 0f) d.firstLine else 1.25f * CM) }
                        Choice("Hanging", d.firstLine < 0f) { d = d.copy(firstLine = if (d.firstLine < 0f) d.firstLine else -1.25f * CM) }
                    }
                    if (d.firstLine != 0f) CmField(if (d.firstLine > 0f) "Indented by" else "Hanging by", kotlin.math.abs(d.firstLine)) { v -> d = d.copy(firstLine = if (d.firstLine < 0f) -v else v) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(onClick = {
                        fun <T> changed(a: T, b: T) = b.takeIf { it != a }
                        apply(StyleChange(font = changed(look.font, d.font), sizePt = changed(look.sizePt ?: basePt, d.sizePt), bold = changed(look.bold == true, d.bold == true),
                            italic = changed(look.italic == true, d.italic == true), color = if (d.color != look.color) d.color ?: -1 else null,
                            align = changed(look.align ?: DocAlign.Start, d.align), line = d.lineMultiple.takeIf { d.lineExact == null && (look.lineExact != null || it != look.lineMultiple) },
                            before = changed(look.before, d.before), after = changed(look.after, d.after), left = changed(look.left, d.left), firstLine = changed(look.firstLine, d.firstLine)))
                    }) { Text("OK") }
                }
            }
        }
    }
}

/**
 * Insert › Header & footer, Page numbers (asked 2026-09-27): a line of text at the top and at the bottom of every
 * page, where each stands, and the page number — for APA, top right on every page.
 */
@Composable
internal fun HeaderFooterDialog(now: HeaderFooter, rich: Boolean, dismiss: () -> Unit, apply: (HeaderFooter) -> Unit) {
    var d by remember { mutableStateOf(now) }
    @Composable fun aligns(on: DocAlign, set: (DocAlign) -> Unit) =
        listOf(DocAlign.Start to "Left", DocAlign.Center to "Centre", DocAlign.End to "Right").forEach { (a, l) -> Choice(l, on == a) { set(a) } }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().imePadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Header, footer and page numbers", style = MaterialTheme.typography.titleLarge)
                if (rich) Text("This document's header or footer has more than a line of text (a logo, a table…). Setting it here puts this in its place.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    OutlinedTextField(d.header, { d = d.copy(header = it.replace("\n", " ")) }, label = { Text("Header") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Section("Header stands") { aligns(d.headerAlign) { d = d.copy(headerAlign = it) } }
                    OutlinedTextField(d.footer, { d = d.copy(footer = it.replace("\n", " ")) }, label = { Text("Footer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Section("Footer stands") { aligns(d.footerAlign) { d = d.copy(footerAlign = it) } }
                    Section("Page number") {
                        Choice("None", d.number == null) { d = d.copy(number = null) }
                        Choice("At the top", d.number == HeaderFooter.NumberPlace.Header) { d = d.copy(number = HeaderFooter.NumberPlace.Header) }
                        Choice("At the bottom", d.number == HeaderFooter.NumberPlace.Footer) { d = d.copy(number = HeaderFooter.NumberPlace.Footer) }
                    }
                    if (d.number != null) {
                        Section("Number stands") { aligns(d.numberAlign) { d = d.copy(numberAlign = it) } }
                        Section("Written as") { HeaderFooter.FORMATS.forEach { f -> Choice(f.replace("#", "1").replace("N", "3"), d.numberFormat == f) { d = d.copy(numberFormat = f) } } }
                    }
                    Toggle("A different first page (a title page without header, footer or number)", d.firstDifferent) { d = d.copy(firstDifferent = it) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(onClick = { apply(d) }) { Text("OK") }
                }
            }
        }
    }
}

/** Space in points (Word's unit for it), 0 allowed. */
@Composable private fun PtField(label: String, pt: Float, set: (Float) -> Unit) {
    var text by remember(label) { mutableStateOf(plain(pt)) }
    OutlinedTextField(text, { v -> text = v; v.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..200f }?.let(set) },
        label = { Text(label) }, suffix = { Text("pt") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.widthIn(max = 160.dp))
}

private fun rowSet(t: DocTable, r: Int, f: (String) -> String) = t.copy(rows = t.rows.mapIndexed { i, row -> if (i == r) row.copy(props = f(row.props)) else row })

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun Section(title: String, content: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable internal fun Choice(label: String, on: Boolean, click: () -> Unit) = Surface(
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

/** A length in the chosen unit (centimetres, or inches — Writer › View), kept in points. */
@Composable internal fun CmField(label: String, pt: Float, least: Float = 0.1f, set: (Float) -> Unit) {
    val unit = WriterUnits.name
    var text by remember(label, unit) { mutableStateOf(WriterUnits.text(pt)) }
    OutlinedTextField(text, { v -> text = v; v.replace(',', '.').toFloatOrNull()?.let(WriterUnits::toPt)?.takeIf { it in least * CM..60f * CM }?.let(set) },
        label = { Text(label) }, suffix = { Text(unit) }, singleLine = true,
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

/** Insert › Symbol (Word's Ω): what a phone keyboard lacks, a tap puts it at the cursor. */
private val SYMBOLS = listOf(
    "Punctuation" to "«»“”‘’„…–—•·¶§†‡‰′″",
    "Money" to "€$£¥¢₿",
    "Signs" to "©®™°№℮✓✗★☆",
    "Maths" to "±×÷≠≈≤≥∞√∑∏∫∂∆πµΩ∈∉∩∪⊂⊃∅∀∃¬∧∨½¼¾⅓²³¹",
    "Arrows" to "←→↑↓↔⇐⇒⇔↵",
)

@Composable
internal fun SymbolDialog(dismiss: () -> Unit, insert: (String) -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Symbol", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SYMBOLS.forEach { (title, chars) -> Section(title) {
                        chars.codePoints().toArray().forEach { c -> val s = String(Character.toChars(c))
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { insert(s) },
                                contentAlignment = Alignment.Center) { Text(s, style = MaterialTheme.typography.titleLarge) }
                        }
                    } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = dismiss) { Text("Close") } }
            }
        }
    }
}

/**
 * Insert › Date and time: today as it will stand in the text. The long forms are the document's words, not the app's,
 * so they come in the phone's language, and in English too (APA papers are often written in English).
 */
internal fun dateForms(now: java.time.LocalDateTime = java.time.LocalDateTime.now(), locale: java.util.Locale = java.util.Locale.getDefault()): List<String> {
    fun f(p: String, l: java.util.Locale = locale) = now.format(java.time.format.DateTimeFormatter.ofPattern(p, l))
    val long = listOf(locale, java.util.Locale.UK).distinctBy { it.language }.flatMap { l ->
        listOf(f("d MMMM yyyy", l), now.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(l))) }
    return (listOf(f("dd/MM/yyyy"), f("yyyy-MM-dd")) + long + listOf(f("HH:mm"), f("dd/MM/yyyy HH:mm"))).distinct()
}

@Composable
internal fun DateDialog(dismiss: () -> Unit, insert: (String) -> Unit) {
    val forms = remember { dateForms() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Date and time", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.padding(vertical = 12.dp)) { Section("Put in the text") { forms.forEach { s -> Choice(s, false) { insert(s) } } } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = dismiss) { Text("Cancel") } }
            }
        }
    }
}

/** Insert › Comment: who writes it (remembered) and what it says. */
@Composable
internal fun NewCommentDialog(name: String, dismiss: () -> Unit, add: (String, String) -> Unit) {
    var author by remember { mutableStateOf(name) }
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Comment") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(author, { author = it.replace("\n", " ") }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(text, { text = it }, label = { Text("Comment") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = author.isNotBlank() && text.isNotBlank(), onClick = { add(author.trim(), text.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

/** A comment's date as the phone says dates, or null. */
internal fun commentDate(c: DocComment): String? = c.date?.let { d -> runCatching { java.time.OffsetDateTime.parse(if (d.endsWith("Z") || d.contains('+')) d else d + "Z") }.getOrNull()
    ?.atZoneSameInstant(java.time.ZoneId.systemDefault())?.format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)) }

/** One comment as a card: who, when, what; tapping it goes to its words; Delete takes it off (the words stay). */
@Composable
internal fun CommentCard(c: DocComment, here: Boolean, open: () -> Unit, delete: () -> Unit, modifier: Modifier = Modifier) = Surface(
    color = Color.White, contentColor = Color(0xFF202124), shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(if (here) 2.dp else 1.dp, if (here) Color(0xFFFFB300) else Color(0xFFDADCE0)),
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = open)) {
    Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFFFB300)), contentAlignment = Alignment.Center) {
                Text(c.author.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(c.author.ifBlank { "Comment" }, style = MaterialTheme.typography.labelLarge)
                commentDate(c)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5F6368)) }
            }
        }
        Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = delete) { Text("Delete", color = Color(0xFF1A73E8)) } }
    }
}

/**
 * A tablet's comments beside the page (as Word shows them): each card at the height of its words, pushed down only
 * as far as the one above needs.
 */
@Composable
internal fun CommentPane(items: List<Pair<DocComment, androidx.compose.ui.unit.Dp>>, here: DocComment?, modifier: Modifier, open: (DocComment) -> Unit, delete: (DocComment) -> Unit) {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val tops = items.map { with(androidx.compose.ui.platform.LocalDensity.current) { it.second.roundToPx() } }
    androidx.compose.ui.layout.Layout(content = { items.forEach { (c, _) -> CommentCard(c, c == here, { open(c) }, { delete(c) }) } }, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)) }
        var bottom = 0
        val ys = placeables.mapIndexed { i, p -> maxOf(tops[i], bottom).also { bottom = it + p.height + gap } }
        layout(constraints.maxWidth, bottom) { placeables.forEachIndexed { i, p -> p.place(0, ys[i]) } }
    }
}

/** A phone's comments: a sidebar over the page, every comment in the order of its words. */
@Composable
internal fun CommentSidebar(comments: List<DocComment>, here: DocComment?, dismiss: () -> Unit, open: (DocComment) -> Unit, delete: (DocComment) -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = dismiss)) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.86f).clickable(interactionSource = null, indication = null) {}) {
                Column(Modifier.systemBarsPadding().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Comments", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = dismiss) { Text("Close") }
                    }
                    if (comments.isEmpty()) Text("No comments", modifier = Modifier.padding(top = 12.dp))
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        comments.forEach { c -> CommentCard(c, c == here, { open(c) }, { delete(c) }) }
                    }
                }
            }
        }
    }
}

/**
 * Insert › Shapes (asked 2026-09-29, OnlyOffice's and Word's gallery): each kind drawn as it will be, in the colours
 * chosen above it — a fill (or none, for a circle around a word) and an outline. A tap puts it at the cursor.
 */
@Composable
internal fun ShapesDialog(odt: Boolean, dismiss: () -> Unit, insert: (kind: String, fill: Int?, line: Int?) -> Unit) {
    val colors = listOf(0xFF4472C4, 0xFFFFC114, 0xFFE53935, 0xFF43A047, 0xFF8E24AA, 0xFF000000).map { it.toInt() }
    var fill by remember { mutableStateOf<Int?>(0xFF4472C4.toInt()) }
    var line by remember { mutableStateOf<Int?>(0xFF2F528F.toInt()) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Shapes", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    @Composable fun swatches(title: String, chosen: Int?, set: (Int?) -> Unit) = Section(title) {
                        Choice("None", chosen == null) { set(null) }
                        colors.forEach { c -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .border(3.dp, if (chosen?.let { it or 0xFF000000.toInt() } == c) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                            .clickable { set(c) }) }
                    }
                    swatches("Fill", fill?.let { it or 0xFF000000.toInt() }) { fill = it }
                    swatches("Outline", line) { line = it }
                    Section("Shape") {
                        ShapePicture.KINDS.filter { !odt || it != "line" }.forEach { kind ->
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { insert(kind, fill, line ?: if (fill == null) 0xFF000000.toInt() else null) }.padding(8.dp)) {
                                val preview = ShapePicture(kind, fill, line ?: if (fill == null) 0xFF000000.toInt() else null, 2f)
                                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                                    drawIntoCanvas { c -> preview.drawAt(c.nativeCanvas, 2f, 2f, size.width - 4f, if (kind == "line") size.height - 4f else size.height - 4f) }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = dismiss) { Text("Close") } }
            }
        }
    }
}
