package com.kalotrapezis.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Draw (asked 2026-09-29, Word's and OnlyOffice's Draw tab — a drawing box first): a box to draw in with a finger or
 * the tablet's pen — two pens and a highlighter, each its colour and width in mm, an eraser that takes a whole stroke —
 * put in the text as a picture (a PNG, see-through where nothing is drawn), so Word and LibreOffice show it too.
 * Strokes are kept in the box's own units: its width is 1.
 */
internal class InkStroke(val points: List<Offset>, val color: Int, val widthMm: Float, val highlighter: Boolean)

internal object InkPicture {
    /** The drawing as a PNG [px] wide ([aspect] = height / width), strokes at their width for a box [widthPt] wide. */
    fun png(strokes: List<InkStroke>, aspect: Float, widthPt: Float, px: Int = 1600): ByteArray {
        val w = px; val h = (px * aspect).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val perMm = px / (widthPt / 72f * 25.4f) // pixels in a millimetre of the page
        for (s in strokes) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE; strokeCap = if (s.highlighter) android.graphics.Paint.Cap.SQUARE else android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND; strokeWidth = s.widthMm * perMm
                color = if (s.highlighter) (s.color and 0xFFFFFF) or 0x66000000 else s.color
            }
            val path = android.graphics.Path()
            s.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * w, p.y * w) else path.lineTo(p.x * w, p.y * w) }
            if (s.points.size == 1) path.lineTo(s.points[0].x * w + 0.1f, s.points[0].y * w)
            canvas.drawPath(path, paint)
        }
        return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}

private enum class Tool { Pen, Pen2, Highlighter, Eraser }

@Composable
internal fun DrawDialog(widthPt: Float, dismiss: () -> Unit, insert: (ByteArray, Float, Float) -> Unit) {
    val aspect = 0.6f
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var tool by remember { mutableStateOf(Tool.Pen) }
    // Each pen keeps its own colour and width (Word's pens).
    val colors = remember { mutableStateListOf(0xFF000000.toInt(), 0xFF1A73E8.toInt(), 0xFFFFC114.toInt()) }
    val widths = remember { mutableStateListOf(0.5f, 1f, 5f) }
    val palette = listOf(0xFF000000, 0xFF1A73E8, 0xFFE53935, 0xFF43A047, 0xFF8E24AA, 0xFFFF9800, 0xFFFFC114, 0xFF00BCD4).map { it.toInt() }
    val slot = when (tool) { Tool.Pen -> 0; Tool.Pen2 -> 1; Tool.Highlighter -> 2; Tool.Eraser -> -1 }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.widthIn(max = 900.dp).systemBarsPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Draw", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Choice("Pen", tool == Tool.Pen) { tool = Tool.Pen }
                    Choice("Pen 2", tool == Tool.Pen2) { tool = Tool.Pen2 }
                    Choice("Highlighter", tool == Tool.Highlighter) { tool = Tool.Highlighter }
                    Choice("Eraser", tool == Tool.Eraser) { tool = Tool.Eraser }
                    Choice("Undo", false) { strokes.removeLastOrNull() }
                }
                if (slot >= 0) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    palette.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(3.dp, if (colors[slot] == c) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).clickable { colors[slot] = c }) }
                    // Width in mm, as Word's (− 1 mm +).
                    Choice("−", false) { widths[slot] = (widths[slot] - if (widths[slot] <= 1f) 0.25f else 1f).coerceAtLeast(0.25f) }
                    Text("${if (widths[slot] % 1f == 0f) widths[slot].toInt().toString() else widths[slot].toString()} mm", style = MaterialTheme.typography.labelLarge)
                    Choice("+", false) { widths[slot] = (widths[slot] + if (widths[slot] < 1f) 0.25f else 1f).coerceAtMost(12f) }
                }
                // As big as fits with the tools and the buttons (a landscape tablet is short).
                val room = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 360.dp).coerceAtLeast(160.dp)
                Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = room / aspect).fillMaxWidth().aspectRatio(1 / aspect).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(8.dp))) {
                    Canvas(Modifier.matchParentSize().pointerInput(tool) {
                        detectDraw(
                            start = { p -> val q = Offset(p.x / size.width, p.y / size.width)
                                if (tool == Tool.Eraser) erase(strokes, q, 12f / size.width) else current = listOf(q) },
                            move = { p -> val q = Offset(p.x / size.width, p.y / size.width)
                                if (tool == Tool.Eraser) erase(strokes, q, 12f / size.width) else current = current + q },
                            end = { if (tool != Tool.Eraser && current.isNotEmpty()) strokes += InkStroke(current, colors[slot], widths[slot], tool == Tool.Highlighter); current = emptyList() })
                    }) {
                        val perMm = size.width / (widthPt / 72f * 25.4f)
                        fun draw(points: List<Offset>, color: Int, mm: Float, highlighter: Boolean) {
                            if (points.isEmpty()) return
                            val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.width) else lineTo(p.x * size.width, p.y * size.width) }
                                if (points.size == 1) lineTo(points[0].x * size.width + 0.1f, points[0].y * size.width) }
                            drawPath(path, if (highlighter) Color(color).copy(alpha = 0.4f) else Color(color),
                                style = Stroke(mm * perMm, cap = if (highlighter) StrokeCap.Square else StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        strokes.forEach { draw(it.points, it.color, it.widthMm, it.highlighter) }
                        if (slot >= 0) draw(current, colors[slot], widths[slot], tool == Tool.Highlighter)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Cancel") }
                    TextButton(enabled = strokes.isNotEmpty(), onClick = { insert(InkPicture.png(strokes, aspect, widthPt), widthPt, widthPt * aspect) }) { Text("Insert") }
                }
            }
        }
    }
}

/** The eraser takes a whole stroke it touches (Word's stroke eraser). */
private fun erase(strokes: MutableList<InkStroke>, at: Offset, radius: Float) {
    strokes.removeAll { s -> s.points.any { (it - at).getDistance() < radius + s.widthMm * 0.002f } }
}

/** Down, move, up of one finger or pen — the drawing's own, so the dialog does not scroll or close under it. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDraw(start: (Offset) -> Unit, move: (Offset) -> Unit, end: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
            start(down.position); down.consume()
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) { end(); break }
                move(c.position); c.consume()
            }
        }
    }
}
