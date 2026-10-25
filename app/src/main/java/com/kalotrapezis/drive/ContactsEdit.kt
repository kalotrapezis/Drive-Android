package com.kalotrapezis.drive

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The labels offered for each kind of value (Google's own); any other is typed. */
private val PHONE_LABELS = listOf("Mobile", "Home", "Work", "Main", "Home fax", "Work fax", "Pager", "Other")
private val PLACE_LABELS = listOf("Home", "Work", "Other")
private val DATE_LABELS = listOf("Anniversary", "Other")
private val URL_LABELS = listOf("Home page", "Blog", "Profile", "Work", "Revolut")

/** "23/10/1985", "23/10", "23.10.85" → a date; null when it is not one. */
internal fun dateFromText(s: String): VDate? {
    val p = s.trim().split(Regex("[/.\\- ]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
    if (p.size !in 2..3) return null
    val y = p.getOrNull(2)?.let { if (it < 100) (if (it + 2000 > java.time.LocalDate.now().year) 1900 + it else 2000 + it) else it }
    return VDate.parse(if (y != null) "%04d-%02d-%02d".format(y, p[1], p[0]) else "--%02d-%02d".format(p[1], p[0]))
}
internal fun dateToText(d: VDate) = if (d.year != null) "%d/%d/%d".format(d.day, d.month, d.year) else "%d/%d".format(d.day, d.month)

/**
 * A contact's Edit and New (the iPhone's Edit at the top right, Tetra's ✓/× there): one page, the fields grouped; More
 * shows the rest of the name (Google's Show more). Many fields: a full page, not a popup (the Office rule).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ContactEditor(start: ContactInfo, isNew: Boolean, allLabels: List<String>, cancel: () -> Unit, delete: (() -> Unit)?, photo: @Composable (ContactInfo, ((ContactInfo) -> ContactInfo) -> Unit) -> Unit, save: (ContactInfo) -> Unit) {
    var i by remember { mutableStateOf(start) }
    var more by remember { mutableStateOf(listOf(start.prefix, start.middle, start.suffix, start.nickname).any { it.isNotBlank() }) }
    var birthday by remember { mutableStateOf(start.birthday?.let(::dateToText) ?: "") }
    var dates by remember { mutableStateOf(start.dates.map { it.label to (VDate.parse(it.value)?.let(::dateToText) ?: it.value) }) }
    var newLabel by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf<Pair<List<String>, (String) -> Unit>?>(null) }
    var asking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    BackHandler { cancel() }
    fun done() {
        val b = birthday.takeIf { it.isNotBlank() }?.let { dateFromText(it) ?: run { problem = "The birthday is not a date: write it as 23/10/1985, or 23/10 without the year."; return } }
        val ds = dates.filter { it.second.isNotBlank() }.map { (l, v) -> VField(l, (dateFromText(v) ?: run { problem = "\"$v\" is not a date."; return }).value()) }
        val names = listOf(i.prefix, i.given, i.middle, i.family, i.suffix) != listOf(start.prefix, start.given, start.middle, start.family, start.suffix)
        val out = i.copy(birthday = b, dates = ds, formatted = if (names || i.formatted.isBlank()) i.composed() else i.formatted,
            phones = i.phones.filter { it.value.isNotBlank() }, emails = i.emails.filter { it.value.isNotBlank() }, urls = i.urls.filter { it.value.isNotBlank() },
            related = i.related.filter { it.value.isNotBlank() }, addresses = i.addresses.filter { a -> a.parts.any { it.isNotBlank() } })
        if (out.name.isBlank()) { problem = "Give a name, a company, a phone or an e-mail."; return }
        save(out)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { photo(i) { change -> i = change(i) } }
                    Group("Name") {
                        if (more) Text1("Prefix", i.prefix) { i = i.copy(prefix = it) }
                        Text1("First name", i.given) { i = i.copy(given = it) }
                        if (more) Text1("Middle name", i.middle) { i = i.copy(middle = it) }
                        Text1("Last name", i.family) { i = i.copy(family = it) }
                        if (more) { Text1("Suffix", i.suffix) { i = i.copy(suffix = it) }; Text1("Nickname", i.nickname) { i = i.copy(nickname = it) } }
                        if (!more) TextButton(onClick = { more = true }) { Text("More name fields") }
                    }
                    Group("Work") {
                        Text1("Company", i.org) { i = i.copy(org = it) }
                        Text1("Department", i.department) { i = i.copy(department = it) }
                        Text1("Job title", i.title) { i = i.copy(title = it) }
                    }
                    Group("Phone") {
                        i.phones.forEachIndexed { n, f -> Labelled(f.label, { choosing = PHONE_LABELS to { l -> i = i.copy(phones = i.phones.toMutableList().also { it[n] = f.copy(label = l) }) } },
                            { i = i.copy(phones = i.phones.filterIndexed { k, _ -> k != n }) }) {
                            Text1("Phone", f.value, KeyboardType.Phone) { v -> i = i.copy(phones = i.phones.toMutableList().also { it[n] = f.copy(value = v) }) }
                        } }
                        TextButton(onClick = { i = i.copy(phones = i.phones + VField(if (i.phones.isEmpty()) "Mobile" else "Home", "")) }) { Text("Add phone") }
                    }
                    Group("Email") {
                        i.emails.forEachIndexed { n, f -> Labelled(f.label, { choosing = PLACE_LABELS to { l -> i = i.copy(emails = i.emails.toMutableList().also { it[n] = f.copy(label = l) }) } },
                            { i = i.copy(emails = i.emails.filterIndexed { k, _ -> k != n }) }) {
                            Text1("Email", f.value, KeyboardType.Email) { v -> i = i.copy(emails = i.emails.toMutableList().also { it[n] = f.copy(value = v) }) }
                        } }
                        TextButton(onClick = { i = i.copy(emails = i.emails + VField("Home", "")) }) { Text("Add email") }
                    }
                    Group("Dates") {
                        Text1("Birthday (23/10/1985, or 23/10)", birthday) { birthday = it }
                        dates.forEachIndexed { n, (l, v) -> Labelled(l, { choosing = DATE_LABELS to { x -> dates = dates.toMutableList().also { it[n] = x to v } } }, { dates = dates.filterIndexed { k, _ -> k != n } }) {
                            Text1("Date", v) { x -> dates = dates.toMutableList().also { it[n] = l to x } }
                        } }
                        TextButton(onClick = { dates = dates + ("Anniversary" to "") }) { Text("Add date") }
                    }
                    Group("Address") {
                        i.addresses.forEachIndexed { n, a -> Labelled(a.label, { choosing = PLACE_LABELS to { l -> i = i.copy(addresses = i.addresses.toMutableList().also { it[n] = a.copy(label = l) }) } },
                            { i = i.copy(addresses = i.addresses.filterIndexed { k, _ -> k != n }) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                fun set(k: Int, v: String) { i = i.copy(addresses = i.addresses.toMutableList().also { it[n] = a.copy(parts = a.parts.toMutableList().also { p -> p[k] = v }) }) }
                                Text1("Street", a.street) { set(2, it) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f)) { Text1("Postcode", a.postcode) { set(5, it) } }
                                    Box(Modifier.weight(2f)) { Text1("City", a.city) { set(3, it) } }
                                }
                                Text1("Region", a.region) { set(4, it) }
                                Text1("Country", a.country) { set(6, it) }
                            }
                        } }
                        TextButton(onClick = { i = i.copy(addresses = i.addresses + VAddress("Home", List(7) { "" })) }) { Text("Add address") }
                    }
                    Group("Website") {
                        i.urls.forEachIndexed { n, f -> Labelled(f.label.ifEmpty { "Website" }, { choosing = URL_LABELS to { l -> i = i.copy(urls = i.urls.toMutableList().also { it[n] = f.copy(label = l) }) } },
                            { i = i.copy(urls = i.urls.filterIndexed { k, _ -> k != n }) }) {
                            Text1(if (f.label == "Revolut") "Revolut username" else "Website", f.value, KeyboardType.Uri) { v -> i = i.copy(urls = i.urls.toMutableList().also { it[n] = f.copy(value = v) }) }
                        } }
                        TextButton(onClick = { i = i.copy(urls = i.urls + VField("", "")) }) { Text("Add website") }
                    }
                    Group("Related people") {
                        i.related.forEachIndexed { n, f -> Labelled(f.label.ifEmpty { "Related" }, { choosing = VCard.RELATIONS to { l -> i = i.copy(related = i.related.toMutableList().also { it[n] = f.copy(label = l) }) } },
                            { i = i.copy(related = i.related.filterIndexed { k, _ -> k != n }) }) {
                            Text1("Name", f.value) { v -> i = i.copy(related = i.related.toMutableList().also { it[n] = f.copy(value = v) }) }
                        } }
                        TextButton(onClick = { i = i.copy(related = i.related + VField("Spouse", "")) }) { Text("Add person") }
                    }
                    Group("Labels") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(i.favourite, { i = i.favourite(!i.favourite) }, label = { Text("Favourite") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = null) }, colors = chipColors())
                            (allLabels + i.labels).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.forEach { l ->
                                val on = i.labels.any { it.equals(l, true) }
                                FilterChip(on, { i = i.copy(categories = if (on) i.categories.filterNot { it.equals(l, true) } else i.categories + l) }, label = { Text(l) }, colors = chipColors())
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Text1("New label", newLabel) { newLabel = it } }
                            TextButton(enabled = newLabel.isNotBlank(), onClick = { i = i.copy(categories = i.categories + newLabel.trim()); newLabel = "" }) { Text("Add") }
                        }
                    }
                    Group("Notes") { Text1("Notes", i.note, single = false) { i = i.copy(note = it) } }
                    if (delete != null) TextButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete contact", color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    IconButton(onClick = cancel) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel") }
                }
                Spacer(Modifier.weight(1f))
                // the whole pill is the button (its word as well as its ✓)
                Surface(onClick = ::done, color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isNew) "Save new" else "Save")
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    }
                }
            }
        }
    }
    // a label's choices in a strip from the bottom (no popup lists on a touch screen), any other typed there
    choosing?.let { (labels, pick) ->
        ModalBottomSheet(onDismissRequest = { choosing = null }, containerColor = SheetColor, contentColor = islandContentColor()) {
            var custom by remember { mutableStateOf("") }
            Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Label", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEach { l -> FilterChip(false, { pick(l); choosing = null }, label = { Text(l) }, colors = chipColors()) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(custom, { custom = it }, singleLine = true, placeholder = { Text("Another label") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = custom.isNotBlank(), onClick = { pick(custom.trim()); choosing = null }) { Text("Use") }
                }
            }
        }
    }
    problem?.let { p -> AlertDialog(onDismissRequest = { problem = null }, text = { Text(p) }, confirmButton = { TextButton(onClick = { problem = null }) { Text("OK") } }) }
    if (asking && delete != null) AlertDialog(onDismissRequest = { asking = false }, title = { Text("Delete ${start.name}?") },
        text = { Text("The contact goes to Tetra's Trash, and can be restored from there (Contacts › Trash).") },
        confirmButton = { TextButton(onClick = { asking = false; delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } })
}

@Composable private fun chipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = CONTACTS_COLOR, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)

@Composable private fun Group(title: String, content: @Composable () -> Unit) =
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = CONTACTS_COLOR)
            content()
        }
    }

@Composable private fun Text1(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, single: Boolean = true, change: (String) -> Unit) =
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = single, minLines = if (single) 1 else 3, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = if (keyboard == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None))

/** A value with its label button before it and × after it. */
@Composable private fun Labelled(label: String, choose: () -> Unit, remove: () -> Unit, field: @Composable () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = choose, modifier = Modifier.width(110.dp)) { Text(label.ifEmpty { "Label" }, maxLines = 1, color = CONTACTS_COLOR) }
        Box(Modifier.weight(1f)) { field() }
        IconButton(onClick = remove) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove") }
    }
