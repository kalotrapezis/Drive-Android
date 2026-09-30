package com.kalotrapezis.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Insert › Drop Cap › Drop cap options (Word's, and OnlyOffice's Αρχίγραμμα the user showed, 2026-09-30): where it
 * stands, how many lines it drops, how far from the text. [odt]: LibreOffice has no drop cap in the margin.
 */
@Composable
internal fun DropCapDialog(now: DropCap?, odt: Boolean, dismiss: () -> Unit, apply: (DropCap?) -> Unit) {
    var place by remember { mutableIntStateOf(when { now == null -> 0; now.margin && !odt -> 2; else -> 1 }) }
    var lines by remember { mutableIntStateOf(now?.lines ?: 3) }
    var distance by remember { mutableFloatStateOf(now?.distance ?: 0f) } // pt
    val step = if (WriterUnits.inches) 0.05f * 72f else 0.1f * 28.3465f
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Drop cap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Position", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("None", place == 0) { place = 0 }
                    Choice("Dropped", place == 1) { place = 1 }
                    if (!odt) Choice("In margin", place == 2) { place = 2 }
                }
                if (odt) Text("An OpenDocument file has no drop cap in the margin.", style = MaterialTheme.typography.bodySmall)
                if (place != 0) {
                    @Composable fun stepper(label: String, value: String, less: () -> Unit, more: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, modifier = Modifier.width(150.dp))
                        Choice("−", false, less)
                        Text(value, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                        Choice("+", false, more)
                    }
                    stepper("Lines to drop", "$lines", { lines = (lines - 1).coerceAtLeast(2) }, { lines = (lines + 1).coerceAtMost(10) })
                    stepper("Distance from text", "%.2f".format(Locale.ROOT, WriterUnits.of(distance)).trimEnd('0').trimEnd('.') + " " + WriterUnits.name,
                        { distance = (distance - step).coerceAtLeast(0f) }, { distance = (distance + step).coerceAtMost(72f) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { apply(if (place == 0) null else DropCap(lines, place == 2, distance, now?.length ?: 1)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
