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

/** Design's galleries (Word's Style sets, Colours, Fonts): pictures of their choices, in the ribbon's place (asked 15 Oct). */
internal enum class DesignGallery(val title: String) { StyleSets("Style sets"), Colours("Colours"), Fonts("Fonts") }

/**
 * A gallery in the ribbon's place (asked 15 Oct: "like a ribbon itself, in its place"): its choices as cards in a strip
 * that scrolls sideways, Cancel and OK beside its name; a card tapped shows on the page above at once.
 */
@Composable
internal fun DesignGalleryStrip(what: DesignGallery, chosen: Any?, cancel: () -> Unit, ok: () -> Unit, pickSet: (StyleSet) -> Unit, pickColors: (ThemeColors) -> Unit,
    pickFonts: (ThemeFonts) -> Unit, modifier: Modifier = Modifier) {
    fun c(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))
    // The phone (asked 15 Oct): one thin row — smaller cards, Cancel and OK at its ends — and no keyboard over it.
    val phone = LocalConfiguration.current.screenWidthDp < 700
    val k = if (phone) 0.7f else 1f
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(what) { keyboard?.hide() }
    @Composable fun card(selected: Boolean, click: () -> Unit, content: @Composable () -> Unit) = Box(Modifier.width(150.dp * k).height(104.dp * k).clip(RoundedCornerShape(12.dp))
        .background(Color.White).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33888888), RoundedCornerShape(12.dp))
        .clickable(onClick = click).padding(10.dp * k)) { content() }
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
                DesignGallery.StyleSets -> StyleSet.entries.forEach { set -> card(chosen == set, click = { pickSet(set) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        fun look(n: String) = set.changes[n]
                        look("Title")?.let { t -> Text("Title", style = face(t.font, 16, if (t.bold == true) FontWeight.Bold else FontWeight.Normal, t.italic == true, t.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                        look("Heading 1")?.let { h -> Text("Heading 1", style = face(h.font, 12, if (h.bold == true) FontWeight.Bold else FontWeight.Normal, h.italic == true, h.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                        look("Normal")?.let { n -> Text("Text of the paper.", style = face(n.font, 9, color = n.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF444444)), maxLines = 1) }
                        Text(set.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5F6368), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } }
                // Each palette: its six accents as blocks, the dark and light beside them.
                DesignGallery.Colours -> ThemeColors.entries.forEach { set -> card(chosen == set, click = { pickColors(set) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp * k)) {
                        Row { set.accents.forEach { a -> Box(Modifier.size(21.dp * k, 30.dp * k).background(c(a))) } }
                        Row { listOf(set.dk2, set.lt2, set.link).forEach { a -> Box(Modifier.size(21.dp * k, 10.dp * k).background(c(a))) } }
                        Text(set.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF5F6368), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } }
                // Each pair written in itself: a big Aa, the headings' face, the text's face.
                DesignGallery.Fonts -> ThemeFonts.entries.forEach { set -> card(chosen == set, click = { pickFonts(set) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Aa", style = face(set.heading, 24))
                        Text(set.heading, style = face(set.heading, 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(set.body, style = face(set.body, 9, color = Color(0xFF555555)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } }
            }
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
