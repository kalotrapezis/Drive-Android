package com.kalotrapezis.drive

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.document.RichTextBlock
import com.mohamedrejeb.richeditor.document.RichTextBlockType
import com.mohamedrejeb.richeditor.document.RichTextDocument
import com.mohamedrejeb.richeditor.document.RichTextSpanMark
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Office (asked 2026-09-27): an orange group of its own. Its home starts a new Word document (Excel and PowerPoint
 * come later) and lists the documents in Tetra's Files; Writer edits one, what you see is what the file holds.
 */

/** Each app's own colour, as across Office and its look-alikes (asked 2026-09-27); the Office group stays orange. */
internal val WORD_BLUE = Color(0xFF185ABD)
internal val EXCEL_GREEN = Color(0xFF107C41)
internal val POWERPOINT_ORANGE = Color(0xFFB7472A) // deeper than the Office group's orange (asked 2026-09-27)

/** The editor's normal text size; the document's own normal size is shown at this size, the rest in proportion. */
private const val BASE_SP = 16f

/** Pictures are drawn at most this wide (sp) — about the page's width on a phone; the file keeps their real size. */
private const val MAX_PICTURE_SP = 300f
/** How the editor names a picture: this and the picture's key. */
internal const val PICTURE_URL = "tetra-picture:"

/** OfficeDoc ↔ the editor's document. Kept apart from the screen so the two directions are easy to compare. */
internal object WriterBridge {
    /** How a picture shows in the editor: its size, scaled with the text, and never wider than the page. */
    fun shownSize(img: DocImage, scale: Float): Pair<Float, Float> {
        val w = img.widthPt * scale
        val fit = (MAX_PICTURE_SP / w).coerceAtMost(1f)
        return w * fit to img.heightPt * scale * fit
    }

    /** A picture's own run as the file had it (its letter formatting), by the picture's key. */
    val pictureRuns = HashMap<String, DocRun>()

    /** [pictures] learns every picture shown, by the key the editor knows it by. */
    fun toEditor(doc: OfficeDoc, pictures: MutableMap<String, DocImage> = HashMap()): RichTextDocument {
        val scale = BASE_SP / doc.basePt
        return RichTextDocument(doc.paras.map { p ->
            val marks = ArrayList<RichTextSpanMark>()
            var at = 0
            for (r in OfficeFormats.tidy(p.runs)) {
                val range = at until at + r.text.length
                if (r.bold) marks += RichTextSpanMark.Bold(range)
                if (r.italic) marks += RichTextSpanMark.Italic(range)
                if (r.underline) marks += RichTextSpanMark.Underline(range)
                if (r.strike) marks += RichTextSpanMark.Strikethrough(range)
                r.sizePt?.let { marks += RichTextSpanMark.FontSize(range, (it * scale).sp) }
                r.color?.let { marks += RichTextSpanMark.TextColor(range, 0xFF000000L or it.toLong()) }
                r.highlight?.let { marks += RichTextSpanMark.Highlight(range, 0xFF000000L or it.toLong()) }
                r.link?.let { marks += RichTextSpanMark.Link(range, it) }
                r.image?.let { img ->
                    pictures[img.key] = img
                    pictureRuns[img.key] = r
                    val (w, h) = shownSize(img, scale)
                    marks += RichTextSpanMark.Image(range, PICTURE_URL + img.key, w.sp, h.sp, "Picture")
                }
                at += r.text.length
            }
            // The editor cannot draw pictures (only the library's read-only text can); Writer draws them over the text,
            // and a paragraph with one gets lines tall enough for it.
            val tallest = p.runs.mapNotNull { r -> r.image?.let { shownSize(it, scale).second } }.maxOrNull()
            RichTextBlock(
                text = p.text,
                type = p.list?.let { RichTextBlockType.ListItem(it.ordered, it.level, it.start) } ?: RichTextBlockType.Paragraph,
                spans = marks,
                headingLevel = p.heading,
                textAlign = when (p.align) { DocAlign.Start -> TextAlign.Unspecified; DocAlign.Center -> TextAlign.Center; DocAlign.End -> TextAlign.End; DocAlign.Justify -> TextAlign.Justify },
                lineHeight = tallest?.let { (it + 6).sp } ?: TextUnit.Unspecified,
            )
        })
    }

    fun fromEditor(rich: RichTextDocument, basePt: Float, pictures: Map<String, DocImage> = emptyMap()): List<DocPara> {
        val scale = BASE_SP / basePt
        return rich.blocks.map { b ->
            val cuts = sortedSetOf(0, b.text.length)
            b.spans.forEach { cuts += it.range.first.coerceIn(0, b.text.length); cuts += (it.range.last + 1).coerceIn(0, b.text.length) }
            val runs = cuts.toList().zipWithNext().filter { (a, z) -> z > a }.map { (a, z) ->
                var r = DocRun(b.text.substring(a, z))
                for (m in b.spans) if (a in m.range) r = when (m) {
                    is RichTextSpanMark.Bold -> r.copy(bold = true)
                    is RichTextSpanMark.FontWeight -> r.copy(bold = m.weight >= 600)
                    is RichTextSpanMark.Italic -> r.copy(italic = true)
                    is RichTextSpanMark.Underline -> r.copy(underline = true)
                    is RichTextSpanMark.Strikethrough -> r.copy(strike = true)
                    is RichTextSpanMark.FontSize -> r.copy(sizePt = spOf(m.size)?.let { ((it / scale) * 2).roundToInt() / 2f })
                    is RichTextSpanMark.TextColor -> r.copy(color = (m.argb and 0xFFFFFF).toInt())
                    is RichTextSpanMark.Highlight -> r.copy(highlight = (m.argb and 0xFFFFFF).toInt())
                    is RichTextSpanMark.Link -> r.copy(link = m.url)
                    is RichTextSpanMark.Image -> pictures[m.url.removePrefix(PICTURE_URL)]?.let { img ->
                        return@map (pictureRuns[img.key] ?: DocRun(OBJECT_MARK.toString())).copy(text = OBJECT_MARK.toString(), image = img)
                    } ?: r
                    else -> r
                }
                r
            }
            val list = (b.type as? RichTextBlockType.ListItem)?.let { DocList(it.ordered, it.indent.coerceIn(0, 8), it.startNumber.takeIf { _ -> it.ordered }) }
            val align = when (b.textAlign) { TextAlign.Center -> DocAlign.Center; TextAlign.End, TextAlign.Right -> DocAlign.End; TextAlign.Justify -> DocAlign.Justify; else -> DocAlign.Start }
            DocPara(OfficeFormats.tidy(runs), b.headingLevel.coerceIn(0, 6), list, align)
        }
    }

    /** A size in sp: the editor gives some (a heading's, carried on to the next line) relative to the text, in em. */
    fun spOf(size: TextUnit): Float? = when {
        size.isSp -> size.value
        size.isEm -> size.value * BASE_SP
        else -> null
    }

    /**
     * The editor draws every heading bold, so it cannot say which words of a heading the file has bold. A heading left
     * as it was keeps the file's own runs; an edited one takes its heading style's weight.
     */
    fun keepHeadingRuns(old: List<DocPara>, new: List<DocPara>): List<DocPara> {
        val unused = old.filter { it.heading > 0 }.groupBy { it.text }.mapValues { ArrayDeque(it.value) }
        return new.map { p ->
            if (p.heading == 0) return@map p
            val o = unused[p.text]?.removeFirstOrNull() ?: return@map p
            if (o.heading == p.heading && unbold(o.runs) == unbold(p.runs)) p.copy(runs = o.runs) else p
        }
    }
    private fun unbold(runs: List<DocRun>) = OfficeFormats.tidy(runs.map { it.copy(bold = false, font = null) })

    /** Paragraphs as they compare: a list's start number counts only where a list begins; bold in a heading, not at all. */
    fun comparable(paras: List<DocPara>) = paras.mapIndexed { i, p ->
        val firstOfList = p.list != null && (i == 0 || paras[i - 1].list == null)
        p.copy(runs = if (p.heading > 0) unbold(p.runs) else OfficeFormats.tidy(p.runs.map { it.copy(font = null) }), keep = null, list = p.list?.let { l -> l.copy(start = if (firstOfList && l.ordered && l.start != 1) l.start else null) })
    }
}

/** A document in Tetra's Files, for the Office home. */
internal data class OfficeFile(val file: File, val relative: String)

internal fun officeFiles(root: File): List<OfficeFile> = root.walkTopDown()
    .onEnter { it == root || (!it.name.startsWith(".") && it.name != "Trash") }
    .filter { it.isFile && OfficeKind.of(it.name) != null && !it.name.startsWith("~$") }
    .map { OfficeFile(it, it.relativeTo(root).path) }
    .sortedByDescending { it.file.lastModified() }
    .toList()

@Composable
internal fun OfficeHome(back: () -> Unit, hasAccess: Boolean, grant: () -> Unit, open: (String?) -> Unit) {
    val root = remember { TetraFolder.root(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf<List<OfficeFile>?>(null) }
    LaunchedEffect(hasAccess) { if (hasAccess) files = withContext(Dispatchers.IO) { officeFiles(root) } }
    BackHandler { back() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 136.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NewDocCard("Word", "New document", WORD_BLUE, true, Modifier.weight(1f)) { open(null) }
                    NewDocCard("Excel", "Coming later", EXCEL_GREEN, false, Modifier.weight(1f)) {}
                    NewDocCard("PowerPoint", "Coming later", POWERPOINT_ORANGE, false, Modifier.weight(1f)) {}
                }
            }
            item { Text("Documents in Files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) }
            if (!hasAccess) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tetra needs access to all files to list your documents.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = grant) { Text("Allow access") }
                }
            } else when (val list = files) {
                null -> item { Text("Looking…", style = MaterialTheme.typography.bodyMedium) }
                else -> if (list.isEmpty()) item { Text("No Word or OpenDocument files in Tetra's Files yet.", style = MaterialTheme.typography.bodyMedium) }
                else items(list, key = { it.relative }) { f ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().clickable { open(Uri.fromFile(f.file).toString()) }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(painterResource(R.drawable.ic_file_text), contentDescription = null, tint = WORD_BLUE, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(f.relative.substringBeforeLast('/', "Files").let { "$it · " } + java.text.DateFormat.getDateInstance().format(java.util.Date(f.file.lastModified())),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        ModuleHeader("Office", back, Modifier.align(Alignment.TopCenter), art = R.drawable.office_art)
    }
}

@Composable private fun NewDocCard(app: String, label: String, color: Color, enabled: Boolean, modifier: Modifier, click: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.extraLarge, color = color,
    contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.7f),
    modifier = modifier.height(104.dp).then(if (enabled) Modifier.clickable(onClick = click) else Modifier),
) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(painterResource(if (enabled) R.drawable.ic_note_add else R.drawable.ic_office), contentDescription = null, modifier = Modifier.size(24.dp))
        Column {
            Text(app, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Unsaved changes are kept as a file in the app's storage while typing, so closing the app never loses them. */
private fun draftFile(context: Context, key: String, kind: OfficeKind) =
    File(File(context.filesDir, "writer-drafts"), "${key.hashCode().toUInt()}.${kind.extension}")

/** A document's earlier versions: a folder per document (by its path), a file per version named by its time. */
private fun versionsDir(context: Context, file: File) = File(File(context.filesDir, "writer-history"), file.absolutePath.hashCode().toUInt().toString())

internal fun versions(context: Context, file: File): List<File> =
    versionsDir(context, file).listFiles()?.filter { it.isFile }?.sortedByDescending { it.name.substringBefore('.').toLongOrNull() ?: 0 }.orEmpty()

/** Before Writer saves over a document, the file as it was is kept (the last 3), so a save can be undone. */
private fun keepPrevious(context: Context, file: File) {
    val dir = versionsDir(context, file).apply { mkdirs() }
    file.copyTo(File(dir, "${System.currentTimeMillis()}.${file.extension}"), overwrite = true)
    versions(context, file).drop(3).forEach { it.delete() }
}

/** A name not yet taken in [dir]: "name (copy).docx", then "name (copy 2).docx"… */
internal fun freeCopyName(dir: File, stem: String, ext: String): File =
    generateSequence(1) { it + 1 }.map { File(dir, if (it == 1) "$stem (copy).$ext" else "$stem (copy $it).$ext") }.first { !it.exists() }

private enum class WriterTab(val label: String) { File("File"), Home("Home"), Insert("Insert"), View("View") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WriterScreen(back: () -> Unit, open: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { RichTextState() }
    var uri by remember { mutableStateOf(open) }
    val file: File? = uri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path?.let(::File)
    var kind by remember { mutableStateOf(OfficeKind.of(open ?: "") ?: OfficeKind.Docx) }
    var name by remember { mutableStateOf(file?.name ?: "New document.docx") }
    var original by remember { mutableStateOf<ByteArray?>(null) }
    var meta by remember { mutableStateOf(OfficeDoc(emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(WriterTab.Home) }
    var message by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var zoom by remember { mutableIntStateOf(100) }
    var finding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    val draftKey = open ?: "new"
    var reload by remember { mutableIntStateOf(0) } // a restored version is read in again
    var showVersions by remember { mutableStateOf(false) }
    val pictures = remember { HashMap<String, DocImage>() } // every picture in the editor, by its key
    var packageParts by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) } // the file's parts, for its pictures

    // The editor does not hold each paragraph's own formatting (spacing, indents…); it is carried over from the file.
    fun current() = meta.copy(paras = OfficeFormats.carryFonts(meta.paras, WriterBridge.keepHeadingRuns(meta.paras,
        OfficeFormats.carryKeep(meta.paras, WriterBridge.fromEditor(state.toRichTextDocument(), meta.basePt, pictures)))))

    LaunchedEffect(reload) {
        loaded = false
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = file?.readBytes()
                val doc = bytes?.let { OfficeFormats.read(it, kind) } ?: OfficeDoc(listOf(DocPara(emptyList())), basePt = 11f)
                val draft = draftFile(context, draftKey, kind).takeIf { it.isFile }?.let { d -> runCatching {
                    val draftBytes = d.readBytes()
                    val draftParts = OfficeFormats.unzip(draftBytes)
                    val originalParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                    // A picture added before the app closed lives only in the draft: it is new again, with its bytes.
                    OfficeFormats.read(draftBytes, kind).let { dd -> dd.copy(paras = dd.paras.map { p -> p.copy(runs = p.runs.map { r ->
                        val img = r.image
                        if (img?.part != null && img.part !in originalParts) r.copy(image = DocImage("d" + img.key, null, img.widthPt, img.heightPt, null, draftParts[img.part], img.part.substringAfterLast('.'))) else r
                    }) }) }
                }.getOrNull() }
                packageParts = bytes?.let { OfficeFormats.unzip(it) }.orEmpty()
                Triple(bytes, doc, draft)
            }
        }
        result.onSuccess { (bytes, doc, draft) ->
            original = bytes
            pictures.clear()
            state.setRichTextDocument(WriterBridge.toEditor(doc, pictures))
            // What the editor shows, read back, must be what the file holds; if not, saving over it could change
            // what was not touched — so that file too is saved only as a copy.
            val shown = WriterBridge.comparable(WriterBridge.fromEditor(state.toRichTextDocument(), doc.basePt, pictures))
            val same = shown == WriterBridge.comparable(doc.paras)
            if (!same) Log.w("Writer", "shown differs: ${shown.zip(WriterBridge.comparable(doc.paras)).firstOrNull { it.first != it.second }}")
            meta = if (same) doc else doc.copy(unsupported = doc.unsupported + "formatting it cannot show exactly")
            dirty = false
            if (draft != null) { state.setRichTextDocument(WriterBridge.toEditor(draft.copy(basePt = doc.basePt), pictures)); dirty = true; message = "Your unsaved changes are back" }
            state.history.clear()
            loaded = true
        }.onFailure { failed = it.message ?: "This file could not be read." }
    }
    // Any change marks the document unsaved, and is kept as a draft after a short pause.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { state.annotatedString }.drop(1).collectLatest {
            dirty = true
            delay(1500)
            val doc = current()
            withContext(Dispatchers.IO) { runCatching { draftFile(context, draftKey, kind).apply { parentFile?.mkdirs() }.writeBytes(OfficeFormats.write(doc, kind, original)) } }
        }
    }
    // Enter after a heading starts a Normal paragraph, as in Word: the editor carries the heading's look on as a size
    // relative to the text (em), which only a heading gives — so that look is taken off at once.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { state.annotatedString to state.selection }.collect {
            val carried = state.currentSpanStyle.fontSize
            if (state.currentHeadingStyle == HeadingStyle.Normal && carried.isEm) state.removeSpanStyle(SpanStyle(fontSize = carried, fontWeight = FontWeight.Bold))
        }
    }

    fun finishSave(target: File, bytes: ByteArray, saved: OfficeDoc) {
        draftFile(context, draftKey, kind).delete()
        original = bytes
        uri = Uri.fromFile(target).toString()
        name = target.name
        val back = OfficeFormats.read(bytes, kind)
        // A picture added in Writer is in the file now: from here on it goes back as the file has it, not added again.
        val before = saved.paras.flatMap { it.runs }.mapNotNull { it.image }
        val after = back.paras.flatMap { it.runs }.mapNotNull { it.image }
        if (before.size == after.size) before.zip(after).forEach { (b, a) -> if (b.raw == null) pictures[b.key] = DocImage(b.key, a.part, a.widthPt, a.heightPt, a.raw) }
        packageParts = OfficeFormats.unzip(bytes)
        meta = meta.copy(unsupported = back.unsupported)
        dirty = false
    }
    /** Save over the file — only when nothing in it would be lost; otherwise a copy beside it. */
    fun save(asCopy: Boolean = false, then: () -> Unit = {}) {
        val f = file
        if (f == null) { naming = true; return }
        scope.launch {
            val doc = current()
            val copy = asCopy || meta.blocking.isNotEmpty()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = OfficeFormats.write(doc, kind, original)
                    val target = if (copy) freeCopyName(f.parentFile!!, f.nameWithoutExtension, kind.extension) else f
                    // Written beside it, then renamed over it: the file is either the old one or the new one, never half.
                    val temp = File(target.parentFile, ".${target.name}.tetra-saving")
                    temp.writeBytes(bytes)
                    if (!copy) keepPrevious(context, f)
                    check(temp.renameTo(target)) { "Could not save ${target.name}." }
                    target to bytes
                }
            }
            result.onSuccess { (target, bytes) ->
                finishSave(target, bytes, doc)
                message = if (copy) "Saved as ${target.name} — the original is as it was" else "Saved"
                then()
            }.onFailure { message = it.message ?: "Could not save." }
        }
    }
    fun saveNew(typed: String) {
        val clean = typed.trim().removeSuffix(".${kind.extension}")
        if (clean.isEmpty() || '/' in clean) { message = "That is not a file name."; return }
        scope.launch {
            val dir = TextEditorStore(context).saveDirectory()
            val target = File(dir, "$clean.${kind.extension}")
            val doc = current()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(dir.isDirectory || dir.mkdirs()) { "Could not make ${dir.name}." }
                    check(!target.exists()) { "${target.name} is already there. Choose another name." }
                    OfficeFormats.write(doc, kind).also { target.writeBytes(it) }
                }
            }
            result.onSuccess { bytes -> naming = false; draftFile(context, draftKey, kind).delete(); finishSave(target, bytes, doc); message = "Saved in ${TextEditorStore(context).saveFolder}" }
                .onFailure { message = it.message }
        }
    }
    /** Export › PDF: beside the document (or in the save folder for a new one), never over another file. */
    fun exportPdf() {
        scope.launch {
            val doc = current()
            val dir = file?.parentFile ?: TextEditorStore(context).saveDirectory()
            val stem = name.substringBeforeLast('.')
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(dir.isDirectory || dir.mkdirs()) { "Could not make ${dir.name}." }
                    val target = generateSequence(1) { it + 1 }.map { File(dir, if (it == 1) "$stem.pdf" else "$stem ($it).pdf") }.first { !it.exists() }
                    target.outputStream().use { OfficePdf.write(doc, it) { img -> img.bytes ?: img.part?.let { p -> packageParts[p] } } }
                    target
                }
            }
            result.onSuccess { message = "Saved ${it.name} in ${dir.name}" }.onFailure { message = it.message ?: "Could not make the PDF." }
        }
    }
    /** A version comes back: the document as it is now is kept as a version first, so restoring is undone the same way. */
    fun restore(version: File) {
        val f = file ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(f.parentFile, ".${f.name}.tetra-saving")
                    version.copyTo(temp, overwrite = true)
                    keepPrevious(context, f)
                    check(temp.renameTo(f))
                    draftFile(context, draftKey, kind).delete()
                }.isSuccess
            }
            if (ok) { reload++; message = "Restored the file as it was before ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(version.name.substringBefore('.').toLong()))}" }
            else message = "Could not restore it."
        }
    }
    // Insert › Picture: from the phone's photos, at the cursor.
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) scope.launch {
            val img = withContext(Dispatchers.IO) { runCatching { preparePicture(context, picked) }.getOrNull() }
            if (img == null) { message = "Could not add that picture."; return@launch }
            pictures[img.key] = img
            val (w, h) = WriterBridge.shownSize(img, BASE_SP / meta.basePt)
            state.insertHtmlAfterSelection("""<img src="$PICTURE_URL${img.key}" width="${w.roundToInt()}" height="${h.roundToInt()}">""")
            // Read back in, so its paragraph gets lines tall enough for it (as a file's pictures do); the cursor stays.
            val at = state.selection
            state.setRichTextDocument(WriterBridge.toEditor(current(), pictures), at)
        }
    }
    fun leave() { if (dirty) leaving = true else back() }
    BackHandler { leave() }

    val ink = Color.Black
    val w = islandContentColor()
    Box(Modifier.fillMaxSize().background(Color(0xFF1B1D1E)).statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // Find and Replace (Word's magnifier): the matches are found in the text as the editor holds it, so a
            // match's place is where the editor selects it.
            if (finding) {
                val text = state.annotatedString.text
                val matches = remember(text, query) { if (query.isEmpty()) emptyList() else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList() }
                val at = matches.indexOfFirst { it.first >= state.selection.min }.let { if (it < 0) 0 else it }
                fun go(i: Int) { matches.getOrNull(Math.floorMod(i, matches.size.coerceAtLeast(1)))?.let { state.selection = androidx.compose.ui.text.TextRange(it.first, it.last + 1) } }
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tool(R.drawable.ic_close, "Close find", w) { finding = false }
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Find") }, modifier = Modifier.weight(1f).focusRequester(focus),
                            suffix = { Text(if (query.isEmpty()) "" else if (matches.isEmpty()) "0" else "${at + 1}/${matches.size}") })
                        Tool(R.drawable.ic_keyboard_arrow_up, "Previous", w, enabled = matches.isNotEmpty()) { go(at - 1) }
                        Box(Modifier.graphicsLayer { rotationZ = 180f }) { Tool(R.drawable.ic_keyboard_arrow_up, "Next", w, enabled = matches.isNotEmpty()) { go(if (state.selection.collapsed) at else at + 1) } }
                        Tool(R.drawable.ic_swap_horiz, "Replace", w, on = replacing) { replacing = !replacing }
                    }
                    if (replacing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(replacement, { replacement = it }, singleLine = true, placeholder = { Text("Replace with") }, modifier = Modifier.weight(1f))
                        RibbonButton("Replace", w, enabled = matches.isNotEmpty()) {
                            val m = matches.getOrNull(at) ?: return@RibbonButton
                            state.replaceTextRange(androidx.compose.ui.text.TextRange(m.first, m.last + 1), replacement)
                        }
                        RibbonButton("All", w, enabled = matches.isNotEmpty()) {
                            state.history.group { matches.asReversed().forEach { m -> state.replaceTextRange(androidx.compose.ui.text.TextRange(m.first, m.last + 1), replacement) } }
                            message = "Replaced ${matches.size}"
                        }
                    }
                }
            } else
            // Word's top bar: back, the name (a dot while unsaved), find, undo and redo.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool(R.drawable.ic_chevron_left, "Back", w) { leave() }
                Text((if (dirty) "• " else "") + name, color = w, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                Tool(R.drawable.ic_search, "Find and replace", w, enabled = loaded) { finding = true }
                Tool(R.drawable.ic_undo, "Undo", w, enabled = state.history.canUndo) { state.history.undo() }
                Tool(R.drawable.ic_redo, "Redo", w, enabled = state.history.canRedo) { state.history.redo() }
                Tool(R.drawable.ic_save, "Save", w, enabled = loaded && (dirty || file == null)) { save() }
            }
            if (loaded && meta.unsupported.isNotEmpty()) {
                val copyOnly = meta.blocking.isNotEmpty()
                Surface(color = if (copyOnly) Category.Office.color.copy(alpha = 0.22f) else Color(0xFF2A2E30), contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { details = true }) {
                    Text(if (copyOnly) "Writer does not show everything in this document (${meta.blocking.take(3).joinToString()}${if (meta.blocking.size > 3) "…" else ""}). Saving makes a copy; the original stays as it is."
                        else "Saving here simplifies ${meta.simplified.joinToString()} to Writer's own.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            // The page: white, as the document will print — its own colours must show as they are.
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 520.dp)) {
                    when {
                        failed != null -> Text(failed!!, color = ink, modifier = Modifier.padding(24.dp))
                        !loaded -> Text("Opening…", color = ink.copy(alpha = 0.5f), modifier = Modifier.padding(24.dp))
                        else -> Box(Modifier.fillMaxWidth()) {
                            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            BasicRichTextEditor(state, textStyle = TextStyle(color = ink, fontSize = (BASE_SP * zoom / 100).sp, lineHeight = (BASE_SP * 1.4f * zoom / 100).sp),
                                cursorBrush = SolidColor(Color(0xFF1A73E8)), onTextLayout = { layout = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 520.dp).padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 180.dp))
                            // The pictures, drawn where their character sits: its left, standing on its line's bottom.
                            val keys by remember { derivedStateOf { state.annotatedString.let { state.toRichTextDocument().blocks.flatMap { b -> b.spans.filterIsInstance<RichTextSpanMark.Image>().sortedBy { it.range.first }.map { it.url.removePrefix(PICTURE_URL) } } } } }
                            layout?.let { l ->
                                val text = l.layoutInput.text.text
                                val density = LocalDensity.current
                                val scale = BASE_SP / meta.basePt
                                // Where each picture goes, worked out first; then drawn (no skipping inside the drawing).
                                val placed = text.indices.filter { text[it] == OBJECT_MARK }.mapIndexedNotNull { n, i ->
                                    keys.getOrNull(n)?.let { pictures[it] }?.let { img -> Triple(img, l.getBoundingBox(i).left, l.getLineBottom(l.getLineForOffset(i))) }
                                }
                                Box(Modifier.matchParentSize().padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 180.dp)) {
                                    placed.forEach { (img, left, bottom) ->
                                        val (w, h) = WriterBridge.shownSize(img, scale)
                                        val y = with(density) { (bottom - h.sp.toPx() - 3.dp.toPx()).toInt() }
                                        val size = with(density) { Modifier.size(w.sp.toDp(), h.sp.toDp()) }
                                        Image(picturePainter(img, packageParts), contentDescription = "Picture", contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.offset { IntOffset(left.toInt(), y) }.then(size))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // The ribbon, like Word's, at the bottom: a line of tabs and a line of that tab's tools, scrolling sideways
        // when they do not fit. A floating island with the keyboard down; a flat strip on the keyboard when it is up.
        val imeOpen = WindowInsets.isImeVisible
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(
            if (imeOpen) Modifier.background(Color(0xFF16191A))
            else Modifier.navigationBarsPadding().padding(12.dp).clip(MaterialTheme.shapes.extraLarge).background(Color(0xFF16191A)).padding(vertical = 4.dp))) { // solid: the page is white
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                WriterTab.entries.forEach { t ->
                    Text(t.label, color = if (tab == t) driveNavigationSelectedContentColor() else w, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp).clip(CircleShape).background(if (tab == t) driveNavigationSelectedColor() else Color.Transparent)
                            .clickable { tab = t }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            // The tools of a tab in pages that fit the width, swiped one page at a time (asked 2026-09-27), with dots.
            val span = state.currentSpanStyle
            val para = state.currentParagraphStyle
            val pages: List<@Composable () -> Unit> = when (tab) {
                WriterTab.File -> listOf({
                    RibbonButton("Save", w, R.drawable.ic_save, enabled = loaded && (dirty || file == null)) { save() }
                    RibbonButton("Save a copy", w, R.drawable.ic_copy, enabled = loaded && file != null) { save(asCopy = true) }
                }, {
                    RibbonButton("Export PDF", w, R.drawable.ic_file, enabled = loaded) { exportPdf() }
                    RibbonButton("Versions", w, R.drawable.ic_history, enabled = file != null) { showVersions = true }
                }, {
                    RibbonButton("Share", w, R.drawable.ic_share, enabled = file?.isFile == true) {
                        file?.let { shareDriveFile(context, it) }
                        if (dirty) message = "Shared as last saved — your newest changes are not in it yet"
                    }
                    RibbonButton("About this file", w, R.drawable.ic_info, enabled = loaded) { details = true }
                })
                WriterTab.Home -> listOf({
                    Tool(R.drawable.ic_format_bold, "Bold", w, on = span.fontWeight == FontWeight.Bold) { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
                    Tool(R.drawable.ic_format_italic, "Italic", w, on = span.fontStyle == FontStyle.Italic) { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
                    Tool(R.drawable.ic_format_underlined, "Underline", w, on = span.textDecoration?.contains(TextDecoration.Underline) == true) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                    Tool(R.drawable.ic_format_strikethrough, "Strikethrough", w, on = span.textDecoration?.contains(TextDecoration.LineThrough) == true) { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
                    Divider(w)
                    val size = WriterBridge.spOf(span.fontSize) ?: BASE_SP
                    Tool(R.drawable.ic_remove, "Smaller text", w) { state.addSpanStyle(SpanStyle(fontSize = (size - 2).coerceAtLeast(8f).sp)) }
                    Text("${(size / BASE_SP * meta.basePt).roundToInt()}", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 2.dp))
                    Tool(R.drawable.ic_add, "Bigger text", w) { state.addSpanStyle(SpanStyle(fontSize = (size + 2).coerceAtMost(72f).sp)) }
                }, {
                    Swatches(w, R.drawable.ic_format_color_text, "Text colour", listOf(Color.Black, Color(0xFFC00000), Color(0xFF0070C0), Color(0xFF00B050), Color(0xFF7030A0), Color(0xFFED7D31))) { c ->
                        if (c == null) span.color.takeIf { it != Color.Unspecified }?.let { state.removeSpanStyle(SpanStyle(color = it)) } else state.addSpanStyle(SpanStyle(color = c))
                    }
                    Swatches(w, R.drawable.ic_highlighter, "Highlight", listOf(Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFFFF00FF))) { c ->
                        if (c == null) span.background.takeIf { it != Color.Unspecified }?.let { state.removeSpanStyle(SpanStyle(background = it)) } else state.addSpanStyle(SpanStyle(background = c))
                    }
                }, {
                    listOf(HeadingStyle.Normal to "Normal", HeadingStyle.H1 to "H1", HeadingStyle.H2 to "H2", HeadingStyle.H3 to "H3").forEach { (h, label) ->
                        RibbonButton(label, w, on = state.currentHeadingStyle == h) { state.setHeadingStyle(h) }
                    }
                }, {
                    Tool(R.drawable.ic_format_list_bulleted, "Bullets", w, on = state.isUnorderedList) { state.toggleUnorderedList() }
                    Tool(R.drawable.ic_format_list_numbered, "Numbering", w, on = state.isOrderedList) { state.toggleOrderedList() }
                    Tool(R.drawable.ic_format_indent_decrease, "Decrease indent", w, enabled = state.canDecreaseListLevel) { state.decreaseListLevel() }
                    Tool(R.drawable.ic_format_indent_increase, "Increase indent", w, enabled = state.canIncreaseListLevel) { state.increaseListLevel() }
                }, {
                    listOf(TextAlign.Start to R.drawable.ic_align_left, TextAlign.Center to R.drawable.ic_align_center, TextAlign.End to R.drawable.ic_align_right, TextAlign.Justify to R.drawable.ic_align_justify).forEach { (a, icon) ->
                        val on = para.textAlign == a || (a == TextAlign.Start && (para.textAlign == TextAlign.Unspecified || para.textAlign == TextAlign.Left))
                        Tool(icon, "Align ${a.toString().lowercase(Locale.ROOT)}", w, on = on) { state.addParagraphStyle(ParagraphStyle(textAlign = a)) }
                    }
                })
                WriterTab.Insert -> listOf({
                    RibbonButton(if (state.isLink) "Edit link" else "Link", w, R.drawable.ic_link) { linking = true }
                    // A new line in the same paragraph — Shift+Enter in Word, which a phone keyboard does not have.
                    RibbonButton("Line break", w) { state.addTextAfterSelection(LINE_BREAK.toString()) }
                }, {
                    RibbonButton("Table · later", w, enabled = false) {}
                    RibbonButton("Picture", w, R.drawable.ic_gallery, enabled = loaded) { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                })
                WriterTab.View -> listOf({
                    Tool(R.drawable.ic_remove, "Zoom out", w, enabled = zoom > 70) { zoom -= 10 }
                    Text("$zoom%", color = w, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 4.dp))
                    Tool(R.drawable.ic_add, "Zoom in", w, enabled = zoom < 200) { zoom += 10 }
                })
            }
            androidx.compose.runtime.key(tab) {
                val pager = rememberPagerState { pages.size }
                HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { pages[page]() }
                }
                if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i -> Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape).background(w.copy(alpha = if (i == pager.currentPage) 0.9f else 0.3f))) }
                }
            }
        }
        message?.let { m ->
            LaunchedEffect(m) { delay(2800); message = null }
            Surface(color = Color.Black, contentColor = Color.White, shape = CircleShape, modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 16.dp, end = 16.dp)) {
                Text(m, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }

    if (naming) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save as") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("File name") }, suffix = { Text(".${kind.extension}") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfficeKind.entries.forEach { k -> RibbonButton(if (k == OfficeKind.Docx) "Word (.docx)" else "OpenDocument (.odt)", MaterialTheme.colorScheme.onSurface, on = kind == k) { kind = k } }
                    }
                    Text("In ${TextEditorStore(context).saveFolder} (Settings › Text editor).", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { saveNew(typed) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("Save $name?") },
        text = { Text(if (meta.blocking.isNotEmpty() && file != null) "It has changes that are not saved. Saving makes a copy; the original stays as it is." else "It has changes that are not saved.") },
        confirmButton = { TextButton(onClick = { leaving = false; if (file == null) naming = true else save { back() } }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { leaving = false }) { Text("Cancel") }
                TextButton(onClick = { leaving = false; draftFile(context, draftKey, kind).delete(); back() }) { Text("Don't save", color = Color(0xFFE35A4F)) }
            }
        },
    )
    if (linking) {
        var url by remember { mutableStateOf(state.selectedLinkUrl ?: "https://") }
        var text by remember { mutableStateOf(state.selectedLinkText ?: "") }
        val selection = state.selection
        AlertDialog(
            onDismissRequest = { linking = false },
            title = { Text("Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selection.collapsed && !state.isLink) OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Text to show") })
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Address") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    linking = false
                    when {
                        state.isLink -> state.updateLink(url)
                        !selection.collapsed -> state.addLinkToSelection(url)
                        else -> state.addLink(text.ifBlank { url }, url)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (state.isLink) TextButton(onClick = { linking = false; state.removeLink() }) { Text("Remove link") }
                    TextButton(onClick = { linking = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (showVersions) {
        val list = remember(reload, dirty) { file?.let { versions(context, it) }.orEmpty() }
        val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
        AlertDialog(
            onDismissRequest = { showVersions = false },
            title = { Text("Versions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (list.isEmpty()) "Writer keeps the file as it was before each save — the last 3. None yet: this document has not been saved over here."
                        else "The file as it was before each of Writer's saves. Restoring one keeps the document as it is now as a version too.", style = MaterialTheme.typography.bodySmall)
                    list.forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Before the save at " + format.format(java.util.Date(v.name.substringBefore('.').toLongOrNull() ?: v.lastModified())), style = MaterialTheme.typography.titleSmall)
                                Text("${(v.length() + 1023) / 1024} KB", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { showVersions = false; restore(v) }, enabled = !dirty) { Text("Restore") }
                        }
                    }
                    if (dirty && list.isNotEmpty()) Text("Save or undo your changes first.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showVersions = false }) { Text("Close") } },
        )
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (kind == OfficeKind.Docx) "Word document (.docx)" else "OpenDocument text (.odt)", style = MaterialTheme.typography.bodyMedium)
                val all = remember { current().paras.joinToString("\n") { it.text } }
                val words = Regex("""[\p{L}\p{N}]+(['’-][\p{L}\p{N}]+)*""").findAll(all).count()
                Text("$words words · ${all.count { !it.isWhitespace() }} characters (${all.count { it != '\n' && it != LINE_BREAK }} with spaces) · ${current().paras.count { it.text.isNotBlank() }} paragraphs", style = MaterialTheme.typography.bodyMedium)
                if (meta.blocking.isNotEmpty()) Text("Writer does not show: ${meta.blocking.joinToString()}. Saving makes a copy beside it; the original is never overwritten.", style = MaterialTheme.typography.bodyMedium)
                if (meta.simplified.isNotEmpty()) Text("Simplified on saving: ${meta.simplified.joinToString()}.", style = MaterialTheme.typography.bodyMedium)
                if (meta.unsupported.isEmpty()) Text("Writer keeps everything in this document.", style = MaterialTheme.typography.bodyMedium)
                Text("Page size, margins, headers and footers are kept as the file has them.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text("OK") } },
    )
}

/** A picture of the document, from the file's own parts or a new picture's bytes. A chart or shape is a grey box. */
@Composable
private fun picturePainter(img: DocImage, parts: Map<String, ByteArray>): Painter {
    val bitmap by produceState<ImageBitmap?>(null, img, parts) {
        val bytes = img.bytes ?: img.part?.let { parts[it] }
        value = bytes?.let { withContext(Dispatchers.IO) { runCatching { decodePicture(it, 1400)?.asImageBitmap() }.getOrNull() } }
    }
    return bitmap?.let { BitmapPainter(it) } ?: ColorPainter(Color(0xFFE3E3E3))
}

/** A picture's bytes as a bitmap no wider or taller than [max] pixels (a phone photo is far bigger than the page needs). */
internal fun decodePicture(bytes: ByteArray, max: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * A picture from the phone, ready to go into a document: a JPEG or PNG no bigger than 2400 pixels is kept as it is;
 * anything bigger (or of another kind) is made a JPEG of that size. As wide as it is (96 dpi), at most the page's width.
 */
private fun preparePicture(context: Context, uri: Uri): DocImage {
    val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val mime = bounds.outMimeType ?: ""
    val keep = (mime == "image/jpeg" || mime == "image/png") && maxOf(bounds.outWidth, bounds.outHeight) <= 2400
    val bytes: ByteArray; val w: Int; val h: Int; val ext: String
    if (keep) { bytes = raw; w = bounds.outWidth; h = bounds.outHeight; ext = if (mime == "image/png") "png" else "jpg" }
    else {
        val b = decodePicture(raw, 2400) ?: error("Not a picture.")
        val scale = (2400f / maxOf(b.width, b.height)).coerceAtMost(1f)
        val s = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
        bytes = java.io.ByteArrayOutputStream().also { s.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        w = s.width; h = s.height; ext = "jpg"
    }
    val widthPt = (w * 0.75f).coerceAtMost(450f)
    return DocImage("n" + java.util.UUID.randomUUID(), null, widthPt, widthPt * h / w, null, bytes, ext)
}

@Composable private fun Divider(tint: Color) = Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(tint.copy(alpha = 0.3f)))

/** A named tool of the ribbon (Save, Heading 1…). */
@Composable private fun RibbonButton(label: String, tint: Color, icon: Int? = null, enabled: Boolean = true, on: Boolean = false, click: () -> Unit) = Row(
    Modifier.height(40.dp).clip(CircleShape).then(if (on) Modifier.background(tint.copy(alpha = 0.18f)) else Modifier)
        .clickable(enabled = enabled, onClick = click).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    val c = if (enabled) tint else tint.copy(alpha = 0.35f)
    icon?.let { Icon(painterResource(it), contentDescription = null, tint = c, modifier = Modifier.size(18.dp)) }
    Text(label, color = c, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** A colour tool: tapped, its swatches open beside it on the same line; the crossed-out one removes the colour. */
@Composable private fun Swatches(tint: Color, icon: Int, label: String, colors: List<Color>, choose: (Color?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Tool(icon, label, tint, on = open) { open = !open }
    if (open) {
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClickLabel = "No $label") { choose(null); open = false }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_no_color), contentDescription = "No ${label.lowercase(Locale.ROOT)}", tint = tint, modifier = Modifier.size(22.dp))
        }
        colors.forEach { c ->
            Box(Modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(c).border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                .clickable(onClickLabel = label) { choose(c); open = false })
        }
        Spacer(Modifier.width(4.dp))
    }
}
