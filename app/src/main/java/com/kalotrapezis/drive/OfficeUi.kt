package com.kalotrapezis.drive

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
    val top get() = place == "top" || (place == "auto" && mouse)
    val compact get() = size == "compact" || (size == "auto" && mouse)

    private fun prefs(context: Context) = context.getSharedPreferences("office", Context.MODE_PRIVATE)
    fun load(context: Context) {
        val p = prefs(context)
        place = p.getString("ribbonPlace", "auto") ?: "auto"; size = p.getString("ribbonSize", "auto") ?: "auto"
    }
    fun set(context: Context, place: String = this.place, size: String = this.size) {
        this.place = place; this.size = size
        prefs(context).edit().putString("ribbonPlace", place).putString("ribbonSize", size).apply()
        WriterLog.i("Office settings: ribbon $place, $size")
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
            Text("By itself: at the top and smaller while a mouse is in use, as on a computer; at the bottom, in reach of a hand, for a finger. " +
                "Office's other settings (AutoSave, lengths, read aloud) are in Tetra's Settings › Office.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
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
