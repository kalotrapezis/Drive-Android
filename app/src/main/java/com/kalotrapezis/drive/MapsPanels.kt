package com.kalotrapezis.drive

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The bottom island's three (the user, 21 Oct: "an Island at the bottom with navigation, favorites, search"). */
internal enum class MapsTab(val label: String, val icon: Int) {
    Navigate("Directions", R.drawable.ic_navigation), Favorites("Saved", R.drawable.ic_favorite), Search("Search", R.drawable.ic_search)
}

@Composable internal fun MapsIsland(tab: MapsTab?, pick: (MapsTab) -> Unit) = Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MapsTab.entries.forEach { t ->
            val on = t == tab
            Surface(shape = CircleShape, color = if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent, modifier = Modifier.clickable { pick(t) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(t.icon), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(t.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** The panels above the island: dark glass like the other islands, at most half the screen. */
@Composable internal fun MapsPanel(content: @Composable () -> Unit) = Surface(color = islandColor(), contentColor = islandContentColor(),
    shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) { Column(Modifier.padding(vertical = 12.dp)) { content() } }

@Composable private fun chipColors() = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Color.White.copy(alpha = 0.22f),
    selectedLabelColor = Color.White, iconColor = Color.White, selectedLeadingIconColor = Color.White)

@Composable internal fun MapsSearchField(query: String, hint: String, change: (String) -> Unit) = OutlinedTextField(query, change, singleLine = true,
    placeholder = { Text(hint) }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") } },
    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent, focusedPlaceholderColor = Color.White.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
        focusedLeadingIconColor = Color.White, unfocusedLeadingIconColor = Color.White, focusedTrailingIconColor = Color.White, unfocusedTrailingIconColor = Color.White),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))

/** Search: words, or a pill of a kind, nearest the map's middle first (the area or town on the screen). */
@Composable internal fun SearchPanel(query: String, setQuery: (String) -> Unit, pill: String?, setPill: (String?) -> Unit, results: List<Poi>,
    distance: (Poi) -> Double?, hasList: Boolean, open: (Poi) -> Unit) = MapsPanel {
    MapsSearchField(query, "Search places and towns", setQuery)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MapsSearch.PILLS.forEach { k -> FilterChip(pill == k.key, { setPill(if (pill == k.key) null else k.key) }, label = { Text("${k.emoji} ${k.label}") }, colors = chipColors()) }
    }
    when {
        !hasList -> Text("The search list is not on this device yet: tools/maps-greece.sh makes greece-places.tsv on the computer, and Local Sync brings it into Tetra/Maps.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        query.isBlank() && pill == null -> Text("Type a name or a town, or pick a kind: the nearest to the middle of the map come first.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        results.isEmpty() -> Text("Nothing found here.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        else -> LazyColumn { items(results) { p -> PoiRow(p, distance(p)) { open(p) } } }
    }
}

@Composable internal fun PoiRow(p: Poi, metres: Double?, click: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(MapsSearch.emoji(p.kind), style = MaterialTheme.typography.titleMedium)
    Column(Modifier.weight(1f)) {
        Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOf(MapsSearch.label(p.kind), p.more).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.7f))
    }
    metres?.let { Text(metresText(it), style = MaterialTheme.typography.labelMedium) }
}

/** Saved: all places by emblem tab, or one list; lists with a name and an emoji, a long press to change one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun FavoritesPanel(places: List<Place>, lists: List<PlaceList>, eopyy: List<Poi>, shown: String?, show: (String?) -> Unit, newList: () -> Unit,
    editList: (PlaceList) -> Unit, distance: (Place) -> Double?, open: (Place) -> Unit, openPoi: (Poi) -> Unit) = MapsPanel {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(shown == null, { show(null) }, label = { Text("All") }, colors = chipColors())
        lists.forEach { l ->
            Box(Modifier.combinedClickable(onClick = { show(l.id) }, onLongClick = { editList(l) })) {
                FilterChip(shown == l.id, { show(l.id) }, label = { Text("${l.emoji} ${l.name}".trim()) }, colors = chipColors())
            }
        }
        // a list of the app's own: EOPYY's pharmacies, nearest first
        if (eopyy.isNotEmpty()) FilterChip(shown == "eopyy", { show("eopyy") }, label = { Text("⚕️ EOPYY pharmacies") }, colors = chipColors())
        TextButton(onClick = newList) { Text("+ New list", color = Color.White) }
    }
    if (shown == "eopyy") {
        LazyColumn { items(eopyy) { p -> PoiRow(p, distance(p.place())) { openPoi(p) } } }
        return@MapsPanel
    }
    val inList = if (shown == null) places else places.filter { shown in it.lists }
    if (inList.isEmpty()) Text(if (shown == null) "No saved places yet. Tap the map, or a place on it, then Save." else "Nothing in this list yet: a place's Save adds it.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
    else LazyColumn {
        val groups = if (shown == null) inList.groupBy { Place.category(it.icon) }.toSortedMap(compareBy { Place.CATEGORIES.keys.indexOf(it) }) else mapOf("" to inList)
        groups.forEach { (cat, ps) ->
            if (cat.isNotEmpty() && groups.size > 1) item { Text(cat, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
            items(ps, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { open(p) }.padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(placeIcon(p.icon)), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    lists.filter { it.id in p.lists }.forEach { Text(it.emoji) }
                    if (p.slot > 0) Text("Home ${p.slot}", style = MaterialTheme.typography.labelSmall)
                    distance(p)?.let { Text(metresText(it), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/** Directions: how to go, then where — a search, or one of Home's places and the car. */
@Composable internal fun NavigatePanel(mode: String, setMode: (String) -> Unit, rolling: Boolean, query: String, setQuery: (String) -> Unit, results: List<Poi>,
    quick: List<Place>, distance: (Poi) -> Double?, goPoi: (Poi) -> Unit, goPlace: (Place) -> Unit) = MapsPanel {
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Place.MODES.forEach { m -> FilterChip(mode == m, { setMode(m) }, label = { Text(modeLabelOf(m, rolling)) }, colors = chipColors(),
            leadingIcon = { Icon(painterResource(modeIcon(m, rolling)), contentDescription = null, modifier = Modifier.size(16.dp)) }) }
    }
    MapsSearchField(query, "Where to?", setQuery)
    if (query.isNotBlank()) LazyColumn { items(results) { p -> PoiRow(p, distance(p)) { goPoi(p) } } }
    else if (quick.isEmpty()) Text("Tap the map or a place on it for directions there, or search above.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
    else LazyColumn {
        items(quick, key = { it.id }) { p ->
            Row(Modifier.fillMaxWidth().clickable { goPlace(p) }.padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(placeIcon(p.icon)), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun modeLabelOf(mode: String, rolling: Boolean) = when (mode) { "foot" -> if (rolling) "Roll" else "Walk"; "bike" -> "Bike"; else -> "Car" }

/**
 * A place's page (the user, 21 Oct: "If I click touch on a place there is no info page for that? Atlist when I am online?"):
 * what OSM knows of it offline — kind, address, hours, phone, website, wheelchair access — directions, Save, and Google
 * Maps for reviews and photos when online.
 */
@Composable internal fun PlaceCard(place: Place, poi: Poi?, address: String?, saved: Boolean, metres: Double?, rolling: Boolean, lists: List<PlaceList>,
    go: (String) -> Unit, save: () -> Unit, edit: () -> Unit, delete: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    fun open(uri: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (saved) Icon(painterResource(placeIcon(place.icon)), contentDescription = null) else Text(poi?.let { MapsSearch.emoji(it.kind) } ?: "📍", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(poi?.let { MapsSearch.label(it.kind) }, metres?.let { metresText(it) + " away" }, lists.filter { it.id in place.lists }.joinToString(" ") { it.emoji }.ifEmpty { null })
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                }
                if (saved) {
                    IconButton(onClick = edit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                    IconButton(onClick = delete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                }
                IconButton(onClick = close) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close") }
            }
            // the place's own address when OSM has it, else the nearest one's
            (poi?.more?.takeIf { it.any(Char::isDigit) } ?: address)?.let { Text("📍 $it", style = MaterialTheme.typography.bodyMedium) }
            Text("%.6f, %.6f".format(java.util.Locale.ROOT, place.lat, place.lng), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            poi?.let { p ->
                if (p.more.isNotEmpty() && !p.more.any(Char::isDigit)) Text(p.more, style = MaterialTheme.typography.bodyMedium)
                if (p.hours.isNotEmpty()) Text("🕒 ${p.hours}", style = MaterialTheme.typography.bodyMedium)
                if (p.phone.isNotEmpty()) Text("📞 ${p.phone}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9FA8DA),
                    modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.phone.substringBefore(';').replace(" ", "")))) } })
                if (p.web.isNotEmpty()) Text("🌐 ${p.web.removePrefix("https://").removePrefix("http://").removeSuffix("/")}", style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9FA8DA), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { open(if (p.web.startsWith("http")) p.web else "https://${p.web}") })
                if (p.note.isNotEmpty()) Text("⚠️ ${p.note}", style = MaterialTheme.typography.bodyMedium)
                when (p.wheelchair) {
                    "yes" -> Text("♿ Wheelchair accessible", style = MaterialTheme.typography.bodyMedium)
                    "limited" -> Text("♿ Partly wheelchair accessible", style = MaterialTheme.typography.bodyMedium)
                    "no" -> Text("♿ Not wheelchair accessible", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Place.MODES.forEach { m ->
                    Button(onClick = { go(m) }, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(modeIcon(m, rolling)), contentDescription = null, modifier = Modifier.size(18.dp)); Text(" " + modeLabelOf(m, rolling))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!saved) OutlinedButton(onClick = save) { Text("Save", color = Color.White) }
                // online: Google's page of the place, with its reviews and photos
                OutlinedButton(onClick = {
                    val q = if (poi != null && poi.title.isNotEmpty() && poi.name.isNotEmpty()) "${poi.name}, ${poi.more.ifEmpty { "%.6f,%.6f".format(java.util.Locale.ROOT, place.lat, place.lng) }}"
                        else "%.6f,%.6f".format(java.util.Locale.ROOT, place.lat, place.lng)
                    open("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))
                }) { Text("Google Maps", color = Color.White) }
            }
        }
    }
}

/** A list's name and emoji (the user, 21 Oct: "make lists with names and emojes"). */
@Composable internal fun ListDialog(l: PlaceList, dismiss: () -> Unit, delete: (() -> Unit)?, save: (PlaceList) -> Unit) {
    var name by remember { mutableStateOf(l.name) }
    var emoji by remember { mutableStateOf(l.emoji) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (delete == null) "New list" else "List") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(emoji, { emoji = it.take(8) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("❤️", "⭐", "🚩", "✈️", "🍽️", "☕", "🏖️", "⛰️", "🌊", "🏛️", "🛍️", "🏠", "👨‍👩‍👧", "♿", "🅿️", "💊", "🎉", "🌳").forEach { e ->
                    Text(e, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { emoji = e }.padding(6.dp))
                }
            }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { save(l.copy(id = l.id.ifEmpty { MapsFiles.newId() }, name = name.trim(), emoji = emoji.trim())) }) { Text("Save") } },
        dismissButton = { Row {
            delete?.let { TextButton(onClick = it) { Text("Delete list") } }
            TextButton(onClick = dismiss) { Text("Cancel") }
        } })
}
