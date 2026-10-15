package com.kalotrapezis.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Galleries in the ribbon's place (asked 15 Oct): Design's Style sets, Colours, Fonts, and Insert's WordArt, Page
 * numbers, Header & footer and Home's Multilevel list — pictures of their choices, each tapped shown on the page.
 */
internal enum class DesignGallery(val title: String) {
    StyleSets("Style sets"), Colours("Colours"), Fonts("Fonts"), WordArt("WordArt"), PageNumbers("Page numbers"),
    HeaderFooter("Header & footer"), Lists("Multilevel list")
}

/** A WordArt look: its colour, and Word's effects on its letters. */
internal data class ArtStyle(val color: Int, val outline: Boolean = false, val shadow: Boolean = false, val glow: Boolean = false)
/** Word's WordArt colours (its theme's accents) plain, then with an outline, a shadow, a glow. */
internal val ART_STYLES = listOf(0x4472C4, 0xED7D31, 0x70AD47, 0xFFC000, 0x000000).map { ArtStyle(it) } +
    listOf(ArtStyle(0x4472C4, outline = true), ArtStyle(0x000000, shadow = true), ArtStyle(0xED7D31, shadow = true),
        ArtStyle(0x4472C4, glow = true), ArtStyle(0xFFC000, outline = true, shadow = true))
/** WordArt's bend (null: straight). */
internal data class ArtBend(val warp: TextWarp?)

/** Where the page number goes and how it reads ([place] null: none). */
internal data class NumberSpot(val label: String, val place: HeaderFooter.NumberPlace?, val align: DocAlign, val format: String = "#", val roman: Boolean = false) {
    fun on(hf: HeaderFooter) = hf.copy(number = place, numberAlign = align, numberFormat = format, roman = roman)
    /** How it reads on page 1 (of 3). */
    val shown get() = format.replace("#", if (roman) "i" else "1").replace("N", if (roman) "iii" else "3")
}
internal val NUMBER_SPOTS = listOf(
    NumberSpot("Top left", HeaderFooter.NumberPlace.Header, DocAlign.Start), NumberSpot("Top centre", HeaderFooter.NumberPlace.Header, DocAlign.Center),
    NumberSpot("Top right", HeaderFooter.NumberPlace.Header, DocAlign.End), NumberSpot("Bottom left", HeaderFooter.NumberPlace.Footer, DocAlign.Start),
    NumberSpot("Bottom centre", HeaderFooter.NumberPlace.Footer, DocAlign.Center), NumberSpot("Bottom right", HeaderFooter.NumberPlace.Footer, DocAlign.End),
    NumberSpot("Page 1 of 3", HeaderFooter.NumberPlace.Footer, DocAlign.Center, "Page # of N"), NumberSpot("1 / 3", HeaderFooter.NumberPlace.Footer, DocAlign.End, "# / N"),
    // Roman (asked 15 Oct): a thesis's front matter, i, ii, iii.
    NumberSpot("Bottom centre, i ii iii", HeaderFooter.NumberPlace.Footer, DocAlign.Center, roman = true), NumberSpot("Bottom right, i ii iii", HeaderFooter.NumberPlace.Footer, DocAlign.End, roman = true),
    NumberSpot("None", null, DocAlign.End),
)

/** A header-and-footer layout, filled with the document's [title]. */
internal data class HeaderLayout(val label: String, val make: (HeaderFooter, String) -> HeaderFooter)
internal val HEADER_LAYOUTS = listOf(
    HeaderLayout("Title, centred") { hf, t -> hf.copy(header = t, headerAlign = DocAlign.Center) },
    HeaderLayout("Title left, page right") { hf, t -> hf.copy(header = t, headerAlign = DocAlign.Start, number = HeaderFooter.NumberPlace.Header, numberAlign = DocAlign.End, numberFormat = "#") },
    HeaderLayout("Title right") { hf, t -> hf.copy(header = t, headerAlign = DocAlign.End) },
    HeaderLayout("Title above, page below") { hf, t -> hf.copy(header = t, headerAlign = DocAlign.Center, number = HeaderFooter.NumberPlace.Footer, numberAlign = DocAlign.Center, numberFormat = "#") },
    HeaderLayout("Book: outer edges") { hf, t -> hf.copy(header = t, headerAlign = DocAlign.End, number = HeaderFooter.NumberPlace.Footer, numberAlign = DocAlign.End, numberFormat = "#", oddEven = true, evenHeader = t) },
    HeaderLayout("None") { _, _ -> HeaderFooter() },
)

/** Home › Multilevel list: Word's everyday schemes, each level's (numFmt, lvlText). */
internal data class ListScheme(val label: String, val levels: List<Pair<String, String>>)
internal val LIST_SCHEMES = listOf(
    ListScheme("1.  2.  3.", (0..8).map { "decimal" to "%${it + 1}." }),
    ListScheme("1.  a.  i.", (0..8).map { listOf("decimal", "lowerLetter", "lowerRoman")[it % 3] to "%${it + 1}." }),
    ListScheme("1.  1.1.  1.1.1.", (0..8).map { l -> "decimal" to (1..l + 1).joinToString("") { "%$it." } }),
    ListScheme("I.  A.  1.", (0..8).map { listOf("upperRoman", "upperLetter", "decimal")[it % 3] to "%${it + 1}." }),
    ListScheme("i.  a.  1.", (0..8).map { listOf("lowerRoman", "lowerLetter", "decimal")[it % 3] to "%${it + 1}." }),
)
/** How a scheme's [level] reads for the [n]th item (1, a, i; 1.1.). */
internal fun ListScheme.marker(level: Int, n: Int): String {
    fun one(fmt: String, k: Int) = when (fmt) { "lowerLetter" -> ('a' + (k - 1) % 26).toString(); "lowerRoman" -> listOf("i", "ii", "iii", "iv", "v")[(k - 1) % 5]; "upperRoman" -> listOf("I", "II", "III", "IV", "V")[(k - 1) % 5]
        "upperLetter" -> ('A' + (k - 1) % 26).toString(); else -> k.toString() }
    val text = levels[level].second
    return Regex("%(\\d)").replace(text) { m -> val l = m.groupValues[1].toInt() - 1; one(levels[l].first, if (l == level) n else 1) }
}

/**
 * A gallery in the ribbon's place (asked 15 Oct: "like a ribbon itself, in its place"): its choices as cards in a strip
 * that scrolls sideways, Cancel and OK beside its name; a card tapped shows on the page above at once.
 */
@Composable
internal fun DesignGalleryStrip(what: DesignGallery, chosen: Any?, cancel: () -> Unit, ok: () -> Unit, pick: (Any) -> Unit,
    more: (() -> Unit)? = null, glow: Boolean = true, title: String = "", modifier: Modifier = Modifier) {
    fun c(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))
    // The phone (asked 15 Oct): one thin row — smaller cards, Cancel and OK at its ends — and no keyboard over it.
    val phone = LocalConfiguration.current.screenWidthDp < 700
    val k = if (phone) 0.7f else 1f
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(what) { keyboard?.hide() }
    // Its name, if it has one, above it on the strip's dark (asked 15 Oct: better contrast than grey on the card).
    @Composable fun card(selected: Boolean, click: () -> Unit, label: String? = null, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) =
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (label != null) Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp * k).padding(start = 4.dp))
            Box(Modifier.width(150.dp * k).height(104.dp * k).clip(RoundedCornerShape(12.dp))
                .background(Color.White).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33888888), RoundedCornerShape(12.dp))
                .clickable(onClick = click).padding(10.dp * k)) { content() }
        }
    @Composable fun face(name: String?, size: Int, weight: FontWeight = FontWeight.Normal, italic: Boolean = false, color: Color = Color(0xFF222222)) =
        TextStyle(fontFamily = OfficeFonts.family(name), fontSize = (size * k).sp, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = color)
    val w = Color.White
    Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(if (phone) 6.dp else 12.dp).clip(MaterialTheme.shapes.extraLarge).background(Color(0xFF16191A)).padding(vertical = 6.dp)) {
        if (!phone) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(what.title, color = w, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = cancel) { Text("Cancel", color = w) }
            TextButton(onClick = ok, enabled = chosen != null) { Text("OK", color = if (chosen != null) w else w.copy(alpha = 0.4f)) }
        }
        val scroll = androidx.compose.foundation.rememberScrollState()
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (phone) IconButton(onClick = cancel) { Icon(painterResource(R.drawable.ic_close), "Cancel", tint = w) }
        Row(Modifier.weight(1f).scrollShade(scroll).horizontalScroll(scroll).padding(horizontal = if (phone) 0.dp else 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 10.dp)) {
            when (what) {
                // Each set as a small page: its title, a heading, a line of text, in their own looks.
                DesignGallery.StyleSets -> StyleSet.entries.forEach { set -> card(chosen == set, click = { pick(set) }, set.label) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        fun look(n: String) = set.changes[n]
                        look("Title")?.let { t -> Text("Title", style = face(t.font, 16, if (t.bold == true) FontWeight.Bold else FontWeight.Normal, t.italic == true, t.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                        look("Heading 1")?.let { h -> Text("Heading 1", style = face(h.font, 12, if (h.bold == true) FontWeight.Bold else FontWeight.Normal, h.italic == true, h.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                        look("Normal")?.let { n -> Text("Text of the paper.", style = face(n.font, 9, color = n.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF444444)), maxLines = 1) }
                    }
                } }
                // Each palette: its six accents as blocks, the dark and light beside them.
                DesignGallery.Colours -> ThemeColors.entries.forEach { set -> card(chosen == set, click = { pick(set) }, set.label) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp * k)) {
                        Row { set.accents.forEach { a -> Box(Modifier.size(21.dp * k, 30.dp * k).background(c(a))) } }
                        Row { listOf(set.dk2, set.lt2, set.link).forEach { a -> Box(Modifier.size(21.dp * k, 10.dp * k).background(c(a))) } }
                    }
                } }
                // Each pair written in itself: a big Aa, the headings' face, the text's face.
                DesignGallery.Fonts -> ThemeFonts.entries.forEach { set -> card(chosen == set, click = { pick(set) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Aa", style = face(set.heading, 24))
                        Text(set.heading, style = face(set.heading, 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(set.body, style = face(set.body, 9, color = Color(0xFF555555)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } }
                // WordArt: its looks, then its bends — a look and a bend make the WordArt put in.
                DesignGallery.WordArt -> {
                    val (style, bend) = (chosen as? Pair<*, *>) ?: (null to null)
                    ART_STYLES.filter { glow || !it.glow }.forEach { a -> card(style == a, click = { pick(a) }) {
                        Text("Aa", modifier = Modifier.align(androidx.compose.ui.Alignment.Center), style = face(null, 40, FontWeight.Bold, color = c(a.color)).copy(
                            shadow = if (a.glow) androidx.compose.ui.graphics.Shadow(c(if (a.color == 0xFFC000) 0x4472C4 else 0xFFC000), androidx.compose.ui.geometry.Offset.Zero, 14f)
                                else if (a.shadow) androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(4f, 4f), 3f) else null,
                            drawStyle = if (a.outline) androidx.compose.ui.graphics.drawscope.Stroke(3f) else null))
                    } }
                    (listOf(null) + TextWarp.entries).forEach { warp -> card(bend == ArtBend(warp), click = { pick(ArtBend(warp)) }, warp?.label ?: "Straight") {
                        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4472C4.toInt(); typeface = android.graphics.Typeface.DEFAULT_BOLD }
                            val w = size.width; val h = size.height * 0.8f
                            drawIntoCanvas { cv ->
                                if (warp == null) { paint.textSize = h * 0.4f; paint.textAlign = android.graphics.Paint.Align.CENTER; cv.nativeCanvas.drawText("Abcde", w / 2, h * 0.65f, paint) }
                                else {
                                    val m = warp.letters(w, h); paint.textSize = m
                                    val path = android.graphics.Path(); warp.line(w, h, m).forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                                    paint.textAlign = android.graphics.Paint.Align.CENTER
                                    cv.nativeCanvas.drawTextOnPath(if (warp == TextWarp.Circle) "Round and round" else "Abcdefgh", path, 0f, 0f, paint)
                                }
                            }
                        }
                    } }
                }
                // Page numbers: a small page with the number where it goes.
                DesignGallery.PageNumbers -> NUMBER_SPOTS.forEach { spot -> card(chosen == spot, click = { pick(spot) }) {
                    // No words under it (asked 15 Oct): the small page says where the number goes.
                    if (spot.place != null) MiniPage(header = if (spot.place == HeaderFooter.NumberPlace.Header) spot.shown to spot.align else null,
                        footer = if (spot.place == HeaderFooter.NumberPlace.Footer) spot.shown to spot.align else null, k = k)
                    if (spot.place == null) Text(spot.label, modifier = Modifier.align(androidx.compose.ui.Alignment.Center), style = MaterialTheme.typography.labelLarge, color = Color(0xFF5F6368))
                } }
                // Headers and footers: a small page with the document's title and number where the layout puts them.
                DesignGallery.HeaderFooter -> HEADER_LAYOUTS.forEach { layout -> card(chosen == layout, click = { pick(layout) }) {
                    // No words under it either: the small page shows the layout ("None" says so).
                    val hf = layout.make(HeaderFooter(), title.ifBlank { "Title" })
                    fun num(at: HeaderFooter.NumberPlace) = if (hf.number == at) "1" to hf.numberAlign else null
                    if (layout != HEADER_LAYOUTS.last()) MiniPage(header = hf.header.takeIf { it.isNotEmpty() }?.let { it to hf.headerAlign } ?: num(HeaderFooter.NumberPlace.Header),
                        footer = num(HeaderFooter.NumberPlace.Footer), k = k, also = if (hf.header.isNotEmpty() && hf.number == HeaderFooter.NumberPlace.Header) "1" to hf.numberAlign else null)
                    if (layout == HEADER_LAYOUTS.last()) Text(layout.label, modifier = Modifier.align(androidx.compose.ui.Alignment.Center), style = MaterialTheme.typography.labelLarge, color = Color(0xFF5F6368))
                } }
                // Multilevel lists: three levels as they read.
                DesignGallery.Lists -> LIST_SCHEMES.forEach { scheme -> card(chosen == scheme, click = { pick(scheme) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp * k)) {
                        listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2).forEach { (level, n) ->
                            Row(Modifier.padding(start = 14.dp * k * level), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(scheme.marker(level, n), style = face(null, 10), modifier = Modifier.width(34.dp * k))
                                Box(Modifier.width(50.dp * k).height(4.dp * k).background(Color(0xFFBDC1C6)))
                            }
                        }
                    }
                } }
            }
            // Its own words, in a window (the header's text, a format of one's own).
            if (more != null) Box(Modifier.width(110.dp * k).height(104.dp * k).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2E30)).clickable(onClick = more).padding(10.dp * k),
                contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Your own…", color = w, style = MaterialTheme.typography.labelLarge) }
        }
        if (phone) IconButton(onClick = ok, enabled = chosen != null) { Icon(painterResource(R.drawable.ic_check), "OK", tint = if (chosen != null) w else w.copy(alpha = 0.4f)) }
        }
    }
}

/**
 * Design › Standards (asked 15 Oct: the APA buttons gathered): what a paper must look like — APA 7's paper (its own
 * window: student or professional, title page, abstract), APA's run-in headings 4 and 5 at the cursor, and the style
 * the citations and their list are written in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StandardsDialog(cite: CiteStyle, dismiss: () -> Unit, paper: () -> Unit, runIn: (Int) -> Unit, citeStyle: (CiteStyle) -> Unit) {
    @Composable fun card(title: String, says: String, click: () -> Unit) = Column(Modifier.width(200.dp).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = click).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(says, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 720.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Standards", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).shadedScroll().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("APA 7", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        card("APA 7 paper", "Times New Roman 12, double spacing, 1-inch margins, page numbers; title page, abstract, running head", paper)
                        card("Heading level 4", "At the cursor: bold, indented, ending in a period, the text running on", { runIn(4) })
                        card("Heading level 5", "At the cursor: bold italic, indented, ending in a period, the text running on", { runIn(5) })
                    }
                    Text("Citations and the reference list", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CiteStyle.entries.forEach { c -> Choice(c.label, c == cite) { citeStyle(c) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = dismiss) { Text("Close") } }
            }
        }
    }
}

/** A page in small: grey lines of text, and a header's and a footer's words where they stand. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MiniPage(header: Pair<String, DocAlign>?, footer: Pair<String, DocAlign>?, k: Float, also: Pair<String, DocAlign>? = null) {
    @Composable fun words(t: Pair<String, DocAlign>, top: Boolean) = Text(t.first, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = (8 * k).sp, color = Color(0xFF202124), fontWeight = FontWeight.Medium),
        modifier = Modifier.align(when (t.second) {
            DocAlign.Center -> if (top) androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.BottomCenter
            DocAlign.End -> if (top) androidx.compose.ui.Alignment.TopEnd else androidx.compose.ui.Alignment.BottomEnd
            else -> if (top) androidx.compose.ui.Alignment.TopStart else androidx.compose.ui.Alignment.BottomStart
        }).padding(bottom = if (top) 0.dp else 10.dp * k).widthIn(max = 80.dp * k))
    Column(Modifier.align(androidx.compose.ui.Alignment.Center).padding(vertical = 14.dp * k), verticalArrangement = Arrangement.spacedBy(4.dp * k)) {
        repeat(4) { Box(Modifier.width(if (it == 3) 60.dp * k else 100.dp * k).height(3.dp * k).background(Color(0xFFDADCE0))) }
    }
    header?.let { words(it, true) }
    also?.let { words(it, true) }
    footer?.let { words(it, false) }
}
