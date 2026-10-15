package com.kalotrapezis.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Insert › Equation and Picture › Edit equation: the equation typed in Word's linear form, drawn above as it will be.
 * The buttons put in a piece where the cursor is.
 */
@Composable
internal fun EquationDialog(start: MathNode?, dismiss: () -> Unit, done: (MathNode) -> Unit) {
    var typed by remember { mutableStateOf(TextFieldValue(start?.let(OfficeMath::linear).orEmpty())) }
    val math = remember(typed.text) { runCatching { OfficeMath.fromLinear(typed.text) }.getOrNull() }
    val pieces = listOf("x²" to "^2", "xᵢ" to "_i", "a/b" to "/", "√" to "sqrt(", "ⁿ√" to "root(n,", "∑" to "∑_(i=1)^n ", "∫" to "∫_a^b ", "∏" to "∏_(i=1)^n ",
        "lim" to "lim_(x→∞) ", "( )" to "(", "x̂" to "hat(", "x⃗" to "vec(", "x̄" to "overline(", "[■]" to "matrix(a&b@c&d)",
        "α" to "α", "β" to "β", "γ" to "γ", "δ" to "δ", "θ" to "θ", "λ" to "λ", "μ" to "μ", "π" to "π", "σ" to "σ", "φ" to "φ", "ω" to "ω", "Δ" to "Δ", "Σ" to "Σ",
        "±" to "±", "×" to "×", "÷" to "÷", "⋅" to "⋅", "≤" to "≤", "≥" to "≥", "≠" to "≠", "≈" to "≈", "→" to "→", "∞" to "∞", "∂" to "∂", "∈" to "∈")
    AlertDialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (start == null) "Insert equation" else "Edit equation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The equation as it will be drawn.
                val drawn = remember(math) { math?.let { MathDrawable(it, 16f) } }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(96.dp).background(Color.White), contentAlignment = Alignment.Center) {
                    if (drawn != null && drawn.widthPt > 1f) {
                        // As big as fits: the box's height, and its width (a long equation is shown smaller, whole).
                        val k = minOf(1.6f, 80f / drawn.heightPt.coerceAtLeast(1f), (maxWidth.value - 24f) / drawn.widthPt)
                        Canvas(Modifier.padding(horizontal = 12.dp).size((drawn.widthPt * k).dp, (drawn.heightPt * k).dp)) {
                            drawIntoCanvas { c -> drawn.setBounds(0, 0, size.width.toInt(), size.height.toInt()); drawn.draw(c.nativeCanvas) }
                        }
                    } else Text("Type below, e.g. x^2 + y^2 = r^2", color = Color(0xFF5F6368))
                }
                OutlinedTextField(typed, { typed = it }, label = { Text("Equation (linear: x^2, a/b, sqrt(x), \\alpha)") }, modifier = Modifier.fillMaxWidth(),
                    // Letters as typed: a keyboard's capitals and corrections turn i into I.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, autoCorrectEnabled = false))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pieces.forEach { (label, piece) ->
                        Choice(label, false) {
                            val t = typed.text; val sel = typed.selection
                            val now = t.substring(0, sel.min) + piece + t.substring(sel.max)
                            typed = TextFieldValue(now, TextRange(sel.min + piece.length))
                        }
                    }
                }
                Text("Brackets after ^ _ / and inside sqrt( ) group without showing; ∑ takes what follows up to + − =.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = math != null && typed.text.isNotBlank(), onClick = { math?.let(done) }) { Text(if (start == null) "Insert" else "Done") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
