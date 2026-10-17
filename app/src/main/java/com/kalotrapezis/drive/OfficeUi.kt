package com.kalotrapezis.drive

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.layoutId
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Words', Tables' and Slides' own settings (asked 16 Oct): where the ribbon stands and how big its tools are — by
 * themselves, at the top while a mouse is in use (as a PC's Office) and at the bottom for a finger (in reach of a hand).
 */
internal object OfficeUi {
    /** "auto": at the top while a mouse is in use, at the bottom for a finger; "bottom"; "top". */
    var place by mutableStateOf("auto"); private set
    /** "auto": compact while a mouse is in use; "normal"; "compact". */
    var size by mutableStateOf("auto"); private set
    /** A mouse moved or clicked last (a finger's or a pen's touch sets it back). */
    var mouse by mutableStateOf(false)
    /** The ribbon's chosen tab: "bubble" behind it or "dash" under it (asked 17 Oct); compact always the dash, for the room. */
    var marker by mutableStateOf("bubble"); private set
    val top get() = place == "top" || (place == "auto" && mouse)
    val compact get() = size == "compact" || (size == "auto" && mouse)

    private fun prefs(context: Context) = context.getSharedPreferences("office", Context.MODE_PRIVATE)
    fun load(context: Context) {
        val p = prefs(context)
        place = p.getString("ribbonPlace", "auto") ?: "auto"; size = p.getString("ribbonSize", "auto") ?: "auto"
        marker = p.getString("tabMarker", "bubble") ?: "bubble"
    }
    fun set(context: Context, place: String = this.place, size: String = this.size, marker: String = this.marker) {
        this.place = place; this.size = size; this.marker = marker
        prefs(context).edit().putString("ribbonPlace", place).putString("ribbonSize", size).putString("tabMarker", marker).apply()
        WriterLog.i("Office settings: ribbon $place, $size, tab marker $marker")
    }
}

/** A mouse or a finger noticed anywhere inside, before the screen's own handling sees it (nothing is consumed). */
internal fun Modifier.noticePointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            when (e.changes.firstOrNull()?.type) {
                PointerType.Mouse -> if (!OfficeUi.mouse) { OfficeUi.mouse = true; WriterLog.i("a mouse in use") }
                PointerType.Touch, PointerType.Stylus -> if (OfficeUi.mouse && e.type == PointerEventType.Press) { OfficeUi.mouse = false; WriterLog.i("a finger in use") }
                else -> Unit
            }
        }
    }
}

/** File › Settings: a centred panel — where the ribbon stands and how big its tools are. */
@Composable internal fun OfficeSettingsDialog(dismiss: () -> Unit) {
    val context = LocalContext.current
    @Composable fun Choices(title: String, now: String, choices: List<Pair<String, String>>, pick: (String) -> Unit) = Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, label) in choices) FilterChip(selected = now == key, onClick = { pick(key) }, label = { Text(label) })
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Settings") }, text = {
        Column {
            Choices("The ribbon", OfficeUi.place, listOf("auto" to "By itself", "bottom" to "At the bottom", "top" to "At the top")) { OfficeUi.set(context, place = it) }
            Choices("Its tools", OfficeUi.size, listOf("auto" to "By itself", "normal" to "Normal", "compact" to "Compact")) { OfficeUi.set(context, size = it) }
            Choices("Tab marker", OfficeUi.marker, listOf("bubble" to "Bubble", "dash" to "Dash")) { OfficeUi.set(context, marker = it) }
            Text("By itself: at the top and smaller while a mouse is in use, as on a computer; at the bottom, in reach of a hand, for a finger. Compact tools always mark the tab with the dash. " +
                "Office's other settings (AutoSave, lengths, read aloud) are in Tetra's Settings › Office.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}

/** The ribbon's tab row, one for Words, Tables and Slides: the chosen tab under a bubble or over a dash (OfficeUi.marker),
 *  scrolled clear of the edge's shade. [contextual] (Picture, Column, …) has a colour of its own; [lead] goes before the tabs. */
@Composable internal fun <T> OfficeTabRow(tabs: List<T>, shown: T, w: Color, dash: Color, pick: (T) -> Unit, contextual: T? = null,
    // the contextual tab: last, in a lighter tone of the app's colour, in every app (17 Oct)
    contextualColour: Color = androidx.compose.ui.graphics.lerp(dash, Color.White, 0.45f), label: (T) -> String = { it.toString() }, lead: @Composable () -> Unit = {}) {
    val bubble = OfficeUi.marker == "bubble" && !OfficeUi.compact
    Row(Modifier.fillMaxWidth().shadedRow().padding(start = 6.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        lead()
        tabs.forEach { t ->
            val on = t == shown
            val mark = if (t == contextual) contextualColour else dash
            // The chosen tab scrolls clear of the edge's shade (Design sat half under it on a phone).
            val seen = remember { BringIntoViewRequester() }
            var wide by remember { mutableStateOf(0) }
            val shade = with(LocalDensity.current) { 48.dp.toPx() }
            LaunchedEffect(on, wide) { if (on && wide > 0) seen.bringIntoView(Rect(-shade, 0f, wide + shade, 1f)) }
            val m = Modifier.bringIntoViewRequester(seen).onSizeChanged { wide = it.width }
            // the bubble in the app's colour, as its dash (asked 17 Oct); the light contextual colour takes dark letters
            if (bubble) Text(label(t), color = if (!on) w else if (t == contextual) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                modifier = m.padding(2.dp).clip(CircleShape).background(if (on) mark else Color.Transparent)
                    .clickable { pick(t) }.padding(horizontal = 14.dp, vertical = 7.dp))
            else Column(m.clickable { pick(t) }.padding(horizontal = 10.dp, vertical = if (OfficeUi.compact) 3.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label(t), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) w else w.copy(alpha = 0.7f), maxLines = 1)
                Box(Modifier.padding(top = 3.dp).width(if (on) 24.dp else 0.dp).height(3.dp).background(mark))
            }
        }
    }
}

/** File › Versions in Tables and Slides: the file as it was before each save (the last 3, [keepPrevious]); [open] one. */
@Composable internal fun VersionsDialog(file: java.io.File, goBack: String, dismiss: () -> Unit, open: (java.io.File) -> Unit) {
    val context = LocalContext.current
    val list = remember { versions(context, file) }
    val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Versions") },
        text = {
            Column {
                Text(if (list.isEmpty()) "None yet. Each save keeps the file as it was before it, the last 3." else "The file as it was before a save. Opening one puts it in the editor; Save keeps it, $goBack.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                list.forEach { v ->
                    Row(Modifier.fillMaxWidth().clickable { open(v) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(format.format(java.util.Date(v.name.substringBefore('.').toLongOrNull() ?: v.lastModified())) + " · ${(v.length() + 1023) / 1024} KB",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Text("Open", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

/** A right-click's menu where it was clicked ([at], from the parent's top left): rounded, its items smaller with a mouse. */
@Composable internal fun OfficeMenu(at: Offset, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    Box(Modifier.offset { IntOffset(at.x.toInt(), at.y.toInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = dismiss, shape = RoundedCornerShape(12.dp), content = content)
    }

/** An item of [OfficeMenu]: its icon and words. */
@Composable internal fun OfficeMenuItem(label: String, icon: Int, enabled: Boolean = true, click: () -> Unit) {
    val small = OfficeUi.mouse
    DropdownMenuItem(text = { Text(label, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(painterResource(icon), null, Modifier.size(if (small) 18.dp else 24.dp)) }, enabled = enabled, onClick = click,
        modifier = if (small) Modifier.height(34.dp) else Modifier,
        contentPadding = if (small) PaddingValues(horizontal = 12.dp) else MenuDefaults.DropdownMenuItemContentPadding)
}

/** The ribbon's groups' separator ([RibbonDivider]): where [RibbonRows] may go on to the next row. */
internal const val RIBBON_DIVIDER = "ribbon-divider"

/** A desktop's ribbon (asked 16 Oct): at the top, on a wide screen — its tools on rows, not one sliding line. */
@Composable internal fun ribbonRows() = OfficeUi.top && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840

/**
 * A ribbon's tools on as many rows as they take, a group (the tools between two [RIBBON_DIVIDER]s) kept whole: one
 * that does not fit goes to the next row, its separator left out there (asked 16 Oct: "stacked properly", not cut mid-group).
 */
@Composable internal fun RibbonRows(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    androidx.compose.ui.layout.Layout(content, modifier) { ms, c ->
        val loose = androidx.compose.ui.unit.Constraints(maxHeight = c.maxHeight)
        val ps = ms.map { it.measure(loose) }
        val divider = ms.map { it.layoutId == RIBBON_DIVIDER }
        // the groups, each with the separator before it
        val groups = ArrayList<Pair<Int?, List<Int>>>(); var sep: Int? = null; var items = ArrayList<Int>()
        ps.indices.forEach { i -> if (divider[i]) { if (items.isNotEmpty()) groups += sep to items; sep = i; items = ArrayList() } else items += i }
        if (items.isNotEmpty()) groups += sep to items
        val rows = ArrayList<ArrayList<Pair<Int?, List<Int>>>>(); var x = 0
        for ((s, g) in groups) {
            val w = g.sumOf { ps[it].width }; val sw = s?.let { ps[it].width } ?: 0
            if (rows.isEmpty() || (x > 0 && x + sw + w > c.maxWidth)) { rows += arrayListOf(null to g); x = w } else { rows.last() += s to g; x += sw + w }
        }
        val heights = rows.map { r -> r.maxOf { (s, g) -> maxOf(s?.let { ps[it].height } ?: 0, g.maxOf { ps[it].height }) } }
        layout(c.maxWidth, heights.sum()) {
            var y = 0
            rows.forEachIndexed { k, r ->
                var px = 0
                for ((s, g) in r) for (i in listOfNotNull(s) + g) { ps[i].placeRelative(px, y + (heights[k] - ps[i].height) / 2); px += ps[i].width }
                y += heights[k]
            }
        }
    }

/** A ribbon's tools: rows of whole groups on a desktop ([ribbonRows]), else one sliding line, faded where there is more. */
@Composable internal fun RibbonLine(scroll: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    if (ribbonRows()) RibbonRows(Modifier.fillMaxWidth().then(modifier), content)
    else androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().scrollShade(scroll).horizontalScroll(scroll).then(modifier),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp, androidx.compose.ui.Alignment.CenterHorizontally)) { content() }

/** navigationBarsPadding, plus room for the window's handle: a floating or split window has no navigation bar of its
 *  own, but the system draws its handle over the window's bottom (asked 16 Oct: it sat on the ribbon's tools). */
@Composable internal fun Modifier.bottomBarPadding(): Modifier {
    androidx.compose.ui.platform.LocalConfiguration.current // entering and leaving a window comes as a configuration change
    val windowed = (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.isInMultiWindowMode == true
    val noBar = WindowInsets.navigationBars.getBottom(androidx.compose.ui.platform.LocalDensity.current) == 0
    return navigationBarsPadding().then(if (windowed && noBar) Modifier.padding(bottom = 12.dp) else Modifier)
}
