package com.kalotrapezis.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** An APA 7 student paper's title page: what it says, top to bottom (blank lines are left out). */
internal data class ApaTitle(val title: String = "", val author: String = "", val department: String = "", val university: String = "",
    val course: String = "", val instructor: String = "", val date: String = "")

/**
 * Design › APA 7 paper (asked 2026-09-30: an APA paper in one tap, the user's assignments): the student paper of APA's
 * 7th edition — Times New Roman 12, 1-inch margins, double spacing with no space between paragraphs, first lines
 * indented half an inch, the page number top right on every page, APA's five heading levels as Heading 1–5, the title
 * page's title bold and centred; references on a page of their own ([Citations]).
 */
internal object ApaPaper {
    const val INCH = 72f
    private const val FONT = "Times New Roman"
    private fun body(align: DocAlign, bold: Boolean, italic: Boolean, firstLine: Float) =
        StyleChange(font = FONT, sizePt = 12f, bold = bold, italic = italic, color = -1, align = align, line = 2f, before = 0f, after = 0f, left = 0f, firstLine = firstLine)
    /** Level 1: centred, bold · 2: left, bold · 3: left, bold italic · 4: indented, bold · 5: indented, bold italic. */
    val headings = mapOf(
        1 to body(DocAlign.Center, true, false, 0f), 2 to body(DocAlign.Start, true, false, 0f), 3 to body(DocAlign.Start, true, true, 0f),
        4 to body(DocAlign.Start, true, false, INCH / 2), 5 to body(DocAlign.Start, true, true, INCH / 2))
    val normal = body(DocAlign.Start, false, false, INCH / 2)
    val title = body(DocAlign.Center, true, false, 0f)

    /** The file's side: the styles, the page number in the header, margins of an inch (an ODT's header room allowed for). */
    fun file(source: Map<String, ByteArray>, kind: OfficeKind, styles: DocStyles): LinkedHashMap<String, ByteArray> {
        var parts = LinkedHashMap(source)
        val path = stylesPath(parts, kind)
        var xml = String(parts.getValue(path), Charsets.UTF_8)
        val entries = styles.paragraphStyles()
        fun edit(e: StyleEntry, c: StyleChange) {
            val key = styles.styleKey(e) ?: return
            xml = if (kind == OfficeKind.Docx) StyleEdit.docx(xml, key, e.heading, styles.normalId, c) else StyleEdit.odt(xml, key, e.heading, c)
        }
        edit(entries[0], normal)
        // LibreOffice's text: Standard and Text body both (the one inherits from the other, with spacing of its own).
        if (kind == OfficeKind.Odt) for (name in listOf("Standard", "Text_20_body")) if ("style:name=\"$name\"" in xml) xml = StyleEdit.odt(xml, name, 0, normal)
        edit(entries[1], title)
        for ((n, c) in headings) edit(StyleEntry("Heading $n", null, n), c)
        parts[path] = xml.toByteArray()
        // The page number top right on every page; no running head (APA's student paper).
        val hf = HeaderFooter.of(styles.section).copy(header = "", number = HeaderFooter.NumberPlace.Header, numberAlign = DocAlign.End, numberFormat = "#", firstDifferent = false)
        parts = if (kind == OfficeKind.Docx) HeaderFooterEdit.docx(parts, hf, styles.page) else HeaderFooterEdit.odt(parts, hf, styles.page)
        val mid = DocStyles.of(kind, OfficeFormats.zip(parts, if (kind == OfficeKind.Odt) "mimetype" else null)).page
        val inch = mid.copy(top = INCH, bottom = INCH, left = INCH, right = INCH)
        return if (kind == OfficeKind.Docx) PageSetup.docx(parts, inch) else PageSetup.odt(parts, inch, mid)
    }

    /**
     * What was set by hand on single paragraphs and letters, taken off so the styles decide (the file's own spacing,
     * indents, justification; the letters' fonts and sizes). A reference list keeps its hanging indent; tables stay.
     */
    fun clean(doc: OfficeDoc, kind: OfficeKind): OfficeDoc = doc.copy(paras = doc.paras.map { p ->
        if (p.table != null || p.keep?.style == Citations.entryStyle(kind)) p else {
            val keep = p.keep?.let { k -> k.copy(parts = k.parts.filter { (name, xml) ->
                name !in setOf("spacing", "ind", "contextualSpacing") &&
                    !(name == "@" && Regex("""^ (fo:margin-(top|bottom|left|right)|fo:line-height|fo:text-indent|style:line-spacing|style:line-height-at-least|style:auto-text-indent)=""").containsMatchIn(xml))
            }).takeIf { !it.isEmpty } }
            p.copy(keep = keep, align = if (p.align == DocAlign.Justify) DocAlign.Start else p.align, runs = p.runs.map { r ->
                if (r.image != null) r else r.copy(sizePt = null, font = r.font?.let { f ->
                    (if (kind == OfficeKind.Docx) f.replace(Regex("""<w:(rFonts|sz|szCs)\b[^>]*/>"""), "")
                        else f.replace(Regex(""" (style:font-name|fo:font-family|style:font-family-generic|style:font-pitch|fo:font-size)="[^"]*""""), "")).ifBlank { null }
                })
            })
        }
    })

    /** The title page's paragraphs (APA: the title three or four lines down, bold; a blank line; the rest), centred. */
    fun titlePage(t: ApaTitle, kind: OfficeKind, titleStyle: String?): List<DocPara> {
        fun centred(text: String, style: String? = null) = DocPara(if (text.isEmpty()) emptyList() else listOf(DocRun(text)), align = DocAlign.Center,
            keep = OfficeFormats.withSpacing(DocKeep(style = style), kind, firstLine = 0f))
        val rest = listOf(t.author, t.department + (if (t.department.isNotBlank() && t.university.isNotBlank()) ", " else "") + t.university, t.course, t.instructor, t.date)
            .filter { it.isNotBlank() }.map { centred(it.trim()) }
        return List(3) { centred("") } + centred(t.title.trim(), titleStyle) + centred("") + rest
    }
}

/** The APA 7 paper page: what it sets, whether to clear what was set by hand, and a title page to put first. */
@Composable
internal fun ApaPaperDialog(hasText: Boolean, today: String, dismiss: () -> Unit, apply: (clean: Boolean, title: ApaTitle?) -> Unit) {
    var clean by remember { mutableStateOf(hasText) }
    var withTitle by remember { mutableStateOf(!hasText) }
    var t by remember { mutableStateOf(ApaTitle(date = today)) }
    @Composable fun field(label: String, value: String, set: (String) -> Unit) = OutlinedTextField(value, set, singleLine = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
    @Composable fun check(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().clickable { set(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, set); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { Text("APA 7 student paper") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).shadedScroll(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Times New Roman 12 · margins of 1 inch · double spacing, no extra space between paragraphs · first lines indented 0.5 inch · " +
                    "the page number top right · APA's heading levels as Heading 1–5 · references on a new page.", style = MaterialTheme.typography.bodyMedium)
                check("Also clear fonts, sizes and spacing set by hand", clean) { clean = it }
                check("Add a title page first", withTitle) { withTitle = it }
                if (withTitle) {
                    field("Title of the paper", t.title) { t = t.copy(title = it) }
                    field("Your name", t.author) { t = t.copy(author = it) }
                    field("Department", t.department) { t = t.copy(department = it) }
                    field("University", t.university) { t = t.copy(university = it) }
                    field("Course (number and name)", t.course) { t = t.copy(course = it) }
                    field("Instructor", t.instructor) { t = t.copy(instructor = it) }
                    field("Due date", t.date) { t = t.copy(date = it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { apply(clean, if (withTitle && t.title.isNotBlank()) t else null) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
