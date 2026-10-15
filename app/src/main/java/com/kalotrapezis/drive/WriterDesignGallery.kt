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
import androidx.compose.foundation.layout.heightIn
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

/** Design's galleries (Word's Style sets, Colours, Fonts), each a window of pictures of its choices, not a list (asked 15 Oct). */
internal enum class DesignGallery(val title: String) { StyleSets("Style sets"), Colours("Colours"), Fonts("Fonts") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesignGalleryDialog(what: DesignGallery, chosen: Any?, cancel: () -> Unit, ok: () -> Unit, pickSet: (StyleSet) -> Unit, pickColors: (ThemeColors) -> Unit, pickFonts: (ThemeFonts) -> Unit) {
    fun c(rgb: Int) = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF))
    @Composable fun card(selected: Boolean = false, click: () -> Unit, content: @Composable () -> Unit) = Box(Modifier.width(156.dp).clip(RoundedCornerShape(12.dp))
        .background(Color.White).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33888888), RoundedCornerShape(12.dp))
        .clickable(onClick = click).padding(12.dp)) { content() }
    @Composable fun face(name: String?, size: Int, weight: FontWeight = FontWeight.Normal, italic: Boolean = false, color: Color = Color(0xFF222222)) =
        TextStyle(fontFamily = OfficeFonts.family(name), fontSize = size.sp, fontWeight = weight, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, color = color)
    Dialog(onDismissRequest = cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // At the bottom, the page not dimmed above it: a choice tapped shows on the document at once (a preview).
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect { (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w -> w.setDimAmount(0.05f); w.setGravity(android.view.Gravity.BOTTOM) } }
        Surface(Modifier.widthIn(max = 720.dp).heightIn(max = 460.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text(what.title, style = MaterialTheme.typography.titleLarge)
                Text("Tap one to see it on the page; OK keeps it.", style = MaterialTheme.typography.bodySmall)
                FlowRow(Modifier.weight(1f, fill = false).fillMaxWidth().shadedScroll().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (what) {
                        // Each set as a small page: its title, a heading, a line of text, in their own looks.
                        DesignGallery.StyleSets -> StyleSet.entries.forEach { set -> card(chosen == set, click = { pickSet(set) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                fun look(n: String) = set.changes[n]
                                look("Title")?.let { t -> Text("Title", style = face(t.font, 18, if (t.bold == true) FontWeight.Bold else FontWeight.Normal, t.italic == true, t.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                                look("Heading 1")?.let { h -> Text("Heading 1", style = face(h.font, 13, if (h.bold == true) FontWeight.Bold else FontWeight.Normal, h.italic == true, h.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF222222)), maxLines = 1) }
                                look("Normal")?.let { n -> Text("Text of the paper, in its own face.", style = face(n.font, 9, color = n.color?.takeIf { it >= 0 }?.let(::c) ?: Color(0xFF444444)), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Text(set.label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF5F6368), modifier = Modifier.padding(top = 4.dp))
                            }
                        } }
                        // Each palette: its six accents as blocks, the dark and light beside them.
                        DesignGallery.Colours -> ThemeColors.entries.forEach { set -> card(chosen == set, click = { pickColors(set) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row { set.accents.forEach { a -> Box(Modifier.size(22.dp, 30.dp).background(c(a))) } }
                                Row { listOf(set.dk2, set.lt2, set.link).forEach { a -> Box(Modifier.size(22.dp, 10.dp).background(c(a))) } }
                                Text(set.label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF5F6368))
                            }
                        } }
                        // Each pair written in itself: a big Aa, the headings' face, the text's face.
                        DesignGallery.Fonts -> ThemeFonts.entries.forEach { set -> card(chosen == set, click = { pickFonts(set) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Aa", style = face(set.heading, 26))
                                Text(set.heading, style = face(set.heading, 13), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(set.body, style = face(set.body, 10, color = Color(0xFF555555)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = cancel) { Text("Cancel") }
                    TextButton(onClick = ok, enabled = chosen != null) { Text("OK") }
                }
            }
        }
    }
}
