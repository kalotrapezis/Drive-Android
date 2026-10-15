package com.kalotrapezis.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Arrange › Rotate: any angle (Word's free rotation), by the slider, the steps or typed; 0 straightens it. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RotateDialog(now: Float, dismiss: () -> Unit, set: (Float) -> Unit) {
    var deg by remember { mutableFloatStateOf(if (now > 180f) now - 360f else now) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Rotate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${deg.roundToInt()}° clockwise")
                Slider(deg, { deg = it.roundToInt().toFloat() }, valueRange = -180f..180f)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-90, -15, -1, 1, 15, 90).forEach { step -> Choice(if (step > 0) "+$step°" else "$step°", false) { deg = ((deg + step + 540f) % 360f) - 180f } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Choice("0°", deg == 0f) { deg = 0f }; Choice("180°", deg == 180f || deg == -180f) { deg = 180f } }
                OutlinedTextField(deg.roundToInt().toString(), { t -> t.toIntOrNull()?.let { deg = (((it % 360) + 540) % 360 - 180).toFloat() } }, singleLine = true,
                    label = { Text("Degrees") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { set(deg) }) { Text("Rotate") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
