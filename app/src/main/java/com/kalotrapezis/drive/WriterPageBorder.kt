package com.kalotrapezis.drive

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Word's page border colours: automatic (black), then its standard colours. */
private val BORDER_COLORS = listOf(0x000000, 0x7F7F7F, 0xC00000, 0xFF0000, 0xFFC000, 0x00B050, 0x0070C0, 0x002060, 0x7030A0)

/**
 * Design › Page borders (Word's Borders and Shading › Page Border): the line's style, width and colour, and the sides
 * it goes on — a box, or some sides only. [now]: the page's border as it is.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PageBorderDialog(now: PageBorder?, dismiss: () -> Unit, set: (Border?, String) -> Unit) {
    var style by remember { mutableStateOf(now?.line?.style ?: "single") }
    var width by remember { mutableStateOf(now?.line?.width ?: 0.5f) }
    var color by remember { mutableStateOf(now?.line?.color ?: 0) }
    var sides by remember { mutableStateOf(now?.sides ?: "tlbr") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Page borders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Line", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("single" to "Solid", "double" to "Double", "dotted" to "Dotted", "dashed" to "Dashed").forEach { (s, label) -> Choice(label, style == s) { style = s } }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f to "½ pt", 1f to "1 pt", 1.5f to "1½ pt", 3f to "3 pt", 4.5f to "4½ pt").forEach { (v, label) -> Choice(label, width == v) { width = v } }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BORDER_COLORS.forEach { c ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF000000 or c.toLong()))
                            .then(if (color == c) Modifier.border(3.dp, Color(0xFF1A73E8), CircleShape) else Modifier).clickable { color = c })
                    }
                }
                Text("Sides", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("Box", sides == "tlbr") { sides = "tlbr" }
                    listOf('t' to "Top", 'b' to "Bottom", 'l' to "Left", 'r' to "Right").forEach { (c, label) ->
                        Choice(label, c in sides) { sides = if (c in sides) sides.replace(c.toString(), "") else "tlbr".filter { it in sides + c } }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = sides.isNotEmpty(), onClick = { set(Border(width, color, style), sides) }) { Text("Apply") } },
        dismissButton = { Row { if (now != null) TextButton(onClick = { set(null, "") }) { Text("No border") }; TextButton(onClick = dismiss) { Text("Cancel") } } },
    )
}
