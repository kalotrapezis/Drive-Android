package com.kalotrapezis.drive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp

/**
 * References › a source (Word's Create Source, APA 7's fields): its kind, then what that kind needs — authors (a
 * person's surname and given names; an organisation's name alone), year, title, the journal / web site / book it is
 * in, publisher, volume, issue, pages, edition, a chapter's book's editors, DOI or address.
 */
@Composable
internal fun SourceDialog(start: DocSource?, dismiss: () -> Unit, save: (DocSource) -> Unit) {
    var type by remember { mutableStateOf(start?.type ?: DocSource.Type.Book) }
    val authors = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(start?.authors?.map { it.last to it.first }.orEmpty().ifEmpty { listOf("" to "") }) } }
    val editors = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(start?.editors?.map { it.last to it.first }.orEmpty().ifEmpty { listOf("" to "") }) } }
    var year by remember { mutableStateOf(start?.year.orEmpty()) }
    var title by remember { mutableStateOf(start?.title.orEmpty()) }
    var container by remember { mutableStateOf(start?.container.orEmpty()) }
    var publisher by remember { mutableStateOf(start?.publisher.orEmpty()) }
    var volume by remember { mutableStateOf(start?.volume.orEmpty()) }
    var issue by remember { mutableStateOf(start?.issue.orEmpty()) }
    var pages by remember { mutableStateOf(start?.pages.orEmpty()) }
    var edition by remember { mutableStateOf(start?.edition.orEmpty()) }
    var doi by remember { mutableStateOf(start?.doi.orEmpty()) }
    @Composable fun field(label: String, value: String, set: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) =
        OutlinedTextField(value, set, singleLine = true, label = { Text(label) }, modifier = modifier)
    @Composable fun people(label: String, list: MutableList<Pair<String, String>>) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        list.forEachIndexed { i, (last, first) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                field("Surname (or organisation)", last, { list[i] = it to list[i].second }, Modifier.weight(1f))
                field("Given names", first, { list[i] = list[i].first to it }, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("+ Add", false) { list += "" to "" }
            if (list.size > 1) Choice("− Last", false) { list.removeAt(list.lastIndex) }
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false), // a form half filled is not lost to a stray tap
        title = { Text(if (start == null) "New source" else "Edit source") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocSource.Type.entries.forEach { t -> Choice(t.label, type == t) { type = t } }
                }
                people("Authors", authors)
                field("Year", year, { year = it })
                field(if (type == DocSource.Type.Chapter) "Chapter title" else if (type == DocSource.Type.Web) "Page title" else "Title", title, { title = it })
                when (type) {
                    DocSource.Type.Article -> {
                        field("Journal", container, { container = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            field("Volume", volume, { volume = it }, Modifier.weight(1f)); field("Issue", issue, { issue = it }, Modifier.weight(1f)); field("Pages", pages, { pages = it }, Modifier.weight(1f))
                        }
                    }
                    DocSource.Type.Chapter -> {
                        field("Book title", container, { container = it })
                        people("Editors of the book", editors)
                        field("Pages", pages, { pages = it }); field("Publisher", publisher, { publisher = it })
                    }
                    DocSource.Type.Web -> field("Web site", container, { container = it })
                    DocSource.Type.Book -> { field("Edition (e.g. 2nd)", edition, { edition = it }); field("Publisher", publisher, { publisher = it }) }
                }
                field("DOI or web address", doi, { doi = it })
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() || authors.any { it.first.isNotBlank() }, onClick = {
                fun list(l: List<Pair<String, String>>) = l.filter { it.first.isNotBlank() }.map { DocSource.Person(it.first.trim(), it.second.trim()) }
                save((start ?: DocSource("")).copy(type = type, authors = list(authors), year = year.trim(), title = title.trim(), container = container.trim(),
                    publisher = publisher.trim(), volume = volume.trim(), issue = issue.trim(), pages = pages.trim(), edition = edition.trim(), doi = doi.trim(),
                    editors = if (type == DocSource.Type.Chapter) list(editors) else emptyList()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

/**
 * References › Insert citation: one or more of the document's sources (a new one added here), and a page (Word
 * files: APA's "p. 12"). [multiple]: several sources in one citation (Word's; an ODT's mark holds one).
 */
@Composable
internal fun CitationDialog(sources: List<DocSource>, pages: Boolean, multiple: Boolean, dismiss: () -> Unit, newSource: () -> Unit, insert: (List<DocSource>, String) -> Unit) {
    val chosen = remember { mutableStateListOf<String>() }
    var page by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Insert citation") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sources.isEmpty()) Text("No sources yet.", style = MaterialTheme.typography.bodyMedium)
                Apa.sorted(sources).forEach { s ->
                    fun pick(on: Boolean) { if (!on) chosen -= s.tag else { if (!multiple) chosen.clear(); chosen += s.tag } }
                    Row(Modifier.fillMaxWidth().clickable { pick(s.tag !in chosen) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.tag in chosen, { pick(it) })
                        Column(Modifier.weight(1f)) {
                            Text(Apa.parenthetical(listOf(s)), style = MaterialTheme.typography.bodyLarge)
                            Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
                Choice("+ New source…", false, newSource)
                if (pages && chosen.size == 1) OutlinedTextField(page, { page = it }, singleLine = true, label = { Text("Page(s), for a quotation (e.g. 12 or 12-14)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = chosen.isNotEmpty(), onClick = { insert(chosen.mapNotNull { t -> sources.firstOrNull { it.tag == t } }, page.trim()) }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

/** References › Sources: the document's sources — edit one, add one, take one away (a cited one says so). */
@Composable
internal fun SourcesDialog(sources: List<DocSource>, cited: Set<String>, dismiss: () -> Unit, edit: (DocSource?) -> Unit, import: () -> Unit = {},
    paste: () -> Unit = {}, askClaude: () -> Unit = {}, delete: (DocSource) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Sources") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sources.isEmpty()) Text("No sources yet.", style = MaterialTheme.typography.bodyMedium)
                Apa.sorted(sources).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { edit(s) }) {
                            Text(Apa.entry(s).joinToString("") { it.text }, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            if (s.tag in cited) Text("Cited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { edit(s) }) { Text("Edit") }
                        if (s.tag !in cited) TextButton(onClick = { delete(s) }) { Text("Delete") }
                    }
                }
                Choice("+ New source…", false) { edit(null) }
                // A Tetra sources file (TETRA_SOURCES_FORMAT.md): the research's references, into this draft at once.
                Choice("Import sources file…", false) { import() }
                // With Claude (the tetra-sources skill): ask in the Claude app, copy its answer, paste it here.
                Choice("Ask Claude for sources…", false) { askClaude() }
                Choice("Paste sources (copied from Claude)", false) { paste() }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

