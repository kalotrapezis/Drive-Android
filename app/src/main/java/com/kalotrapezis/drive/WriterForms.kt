package com.kalotrapezis.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Forms (asked 2026-10-01: "the next thing missing I have on my words is forms"): Word's content controls in the text —
 * a text field, a drop-down (or combo box), a date, a check box. Each is its letters' [DocRun.control], Word's sdtPr as
 * the file has it; filling one changes its letters and, for a date or a check box, the value the sdtPr holds.
 * ponytail: run-level controls only (inside a paragraph); block-level ones (a table of contents, a control around whole
 * paragraphs or table rows) still make the file copy-only. Controls inside table cells are kept but not filled from
 * the bar.
 */
internal object Forms {
    enum class Kind { Text, List, Date, Check }

    private fun attr(tag: String, name: String) = Regex("""\b$name="([^"]*)"""").find(tag)?.groupValues?.get(1)?.let(::unesc)
    private fun unesc(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    private fun tag(pr: String, name: String) = Regex("""<$name\b[^>]*>""").find(pr)?.value

    fun kind(pr: String) = when {
        tag(pr, "w14:checkbox") != null -> Kind.Check
        tag(pr, "w:dropDownList") != null || tag(pr, "w:comboBox") != null -> Kind.List
        tag(pr, "w:date") != null -> Kind.Date
        else -> Kind.Text
    }
    /** What Word calls it (its title), else its tag. */
    fun name(pr: String) = tag(pr, "w:alias")?.let { attr(it, "w:val") } ?: tag(pr, "w:tag")?.let { attr(it, "w:val") }
    /** A drop-down's choices, as shown. */
    fun items(pr: String) = Regex("""<w:listItem\b[^>]*>""").findAll(pr).mapNotNull { m -> attr(m.value, "w:displayText") ?: attr(m.value, "w:value") }.toList()
    fun checked(pr: String) = tag(pr, "w14:checked")?.let { attr(it, "w14:val") }.let { it == "1" || it == "true" }
    fun date(pr: String): LocalDate? = tag(pr, "w:date")?.let { attr(it, "w:fullDate") }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** A date as the control's own format says (Word's: dddd, MMMM, yyyy…), in its language. */
    fun dateText(pr: String, d: LocalDate): String {
        val format = tag(pr, "w:dateFormat")?.let { attr(it, "w:val") } ?: "d/M/yyyy"
        val lang = tag(pr, "w:lid")?.let { attr(it, "w:val") }
        val java = format.replace("dddd", "EEEE").replace(Regex("(?<!d)ddd(?!d)"), "EEE").replace("'", "''")
        return runCatching { DateTimeFormatter.ofPattern(java, lang?.let(Locale::forLanguageTag) ?: Locale.getDefault()).format(d) }.getOrElse { d.toString() }
    }

    /** The control filled with [text], its sdtPr changed to [pr] (a placeholder's grey goes, as Word's). */
    fun fill(doc: OfficeDoc, old: String, pr: String, text: String): OfficeDoc {
        val plain = pr.replace(Regex("""<w:showingPlcHdr\s*/>"""), "")
        val wasPlaceholder = plain != pr
        return doc.copy(paras = doc.paras.map { p ->
            val i = p.runs.indexOfFirst { it.control == old }
            if (i < 0) p else {
                val first = p.runs[i]
                val runs = p.runs.filterIndexed { k, r -> k == i || r.control != old }.toMutableList()
                runs[i] = first.copy(text = text, control = plain, color = if (wasPlaceholder) null else first.color, image = null)
                p.copy(runs = OfficeFormats.tidy(runs))
            }
        })
    }
    fun checkedTo(pr: String, on: Boolean) = pr.replace(Regex("""(<w14:checked\b[^>]*\bw14:val=")[^"]*"""), "$1${if (on) 1 else 0}")
    /** The check box's letter: its own (checkedState / uncheckedState), else Word's ☒ / ☐. */
    fun box(pr: String, on: Boolean) = tag(pr, if (on) "w14:checkedState" else "w14:uncheckedState")?.let { attr(it, "w14:val") }
        ?.toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: if (on) "☒" else "☐"
    fun dateTo(pr: String, d: LocalDate) = pr.replace(Regex("""(<w:date\b[^>]*\bw:fullDate=")[^"]*"""), "$1${d}T00:00:00Z")
        .let { if (it == pr && "w:fullDate" !in pr) it.replaceFirst("<w:date", "<w:date w:fullDate=\"${d}T00:00:00Z\"") else it }
}

/** The form field the cursor is in, above the ribbon: its name, and what fills it — a choice, a date, a tick. */
@Composable
internal fun FormBar(pr: String, text: String, w: Color, fill: (pr: String, text: String) -> Unit) {
    val context = LocalContext.current
    var typing by remember { mutableStateOf(false) }
    val kind = Forms.kind(pr)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Forms.name(pr) ?: when (kind) { Forms.Kind.Text -> "Text field"; Forms.Kind.List -> "Drop-down"; Forms.Kind.Date -> "Date"; Forms.Kind.Check -> "Check box" },
            color = w, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp))
        when (kind) {
            Forms.Kind.Text -> TextButton(onClick = { typing = true }) { Text("Fill in", color = Color.White) }
            Forms.Kind.List -> Forms.items(pr).forEach { item ->
                TextButton(onClick = { fill(pr, item) }) { Text(item, color = if (item == text) Color(0xFF81C784) else Color.White) }
            }
            Forms.Kind.Date -> TextButton(onClick = {
                val d = Forms.date(pr) ?: LocalDate.now()
                android.app.DatePickerDialog(context, { _, y, m, day -> LocalDate.of(y, m + 1, day).let { fill(Forms.dateTo(pr, it), Forms.dateText(pr, it)) } },
                    d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) { Text("Choose date", color = Color.White) }
            Forms.Kind.Check -> {
                val on = Forms.checked(pr)
                TextButton(onClick = { fill(Forms.checkedTo(pr, !on), Forms.box(pr, !on)) }) { Text(if (on) "Untick" else "Tick", color = Color.White) }
            }
        }
    }
    if (typing) {
        var value by remember { mutableStateOf(if ("w:showingPlcHdr" in pr) "" else text) }
        AlertDialog(onDismissRequest = { typing = false },
            title = { Text(Forms.name(pr) ?: "Text field") },
            text = { OutlinedTextField(value, { value = it }, singleLine = tagOf(pr), modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = value.isNotEmpty(), onClick = { fill(pr, value); typing = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } })
    }
}

/** A plain-text control says whether it may hold more than one line (w:multiLine). */
private fun tagOf(pr: String) = !Regex("""<w:text\b[^>]*w:multiLine="(1|true)"""").containsMatchIn(pr)
