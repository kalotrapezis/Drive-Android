package com.kalotrapezis.drive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.util.Locale

private val MAPS_COLOR = androidx.compose.ui.graphics.Color(0xFF5C6BC0)
/** Greece and its sea: the map holds nothing beyond, so the camera stays within. */
private val GREECE = LatLngBounds.from(42.2, 30.2, 34.2, 18.6)

internal fun placeIcon(icon: String) = when (icon) {
    "star" -> R.drawable.ic_star
    "heart" -> R.drawable.ic_favorite
    "accessible" -> R.drawable.ic_accessible
    "home" -> R.drawable.ic_home
    "work" -> R.drawable.ic_work
    "school" -> R.drawable.ic_school
    "family" -> R.drawable.ic_people
    "friend" -> R.drawable.ic_person
    "pharmacy" -> R.drawable.ic_pharmacy
    "hospital" -> R.drawable.ic_hospital
    "doctor" -> R.drawable.ic_doctor
    "travel" -> R.drawable.ic_flight
    "hotel" -> R.drawable.ic_hotel
    "food" -> R.drawable.ic_restaurant
    "tavern" -> R.drawable.ic_tavern
    "cafe" -> R.drawable.ic_cafe
    "shop" -> R.drawable.ic_shop
    "fuel" -> R.drawable.ic_fuel
    "parking" -> R.drawable.ic_parking
    "beach" -> R.drawable.ic_beach
    "waterfall" -> R.drawable.ic_waterfall
    "river" -> R.drawable.ic_river
    "lake" -> R.drawable.ic_lake
    "mountain" -> R.drawable.ic_mountain
    "forest" -> R.drawable.ic_forest
    "camping" -> R.drawable.ic_camping
    else -> R.drawable.ic_place
}

/**
 * Public things on the map — search results, the map's additions — in the map's own icon style (the user, 21 Oct: "my
 * personal keep our style what I save as public info gets the maps own style"): its sprite where it has one, else drawn as
 * it draws them, a rounded square of a pale tint with a coloured edge and symbol, in its colours (orange food, blue shops
 * and parking, purple learning, green nature and money, pink culture; red for health, which it has none of).
 */
private val SPRITE = mapOf("restaurant" to "restaurant", "cafe" to "cafe", "fast_food" to "fast_food", "supermarket" to "supermarket",
    "kindergarten" to "school", "primary_school" to "school", "secondary_school" to "school", "school" to "school", "university" to "university",
    "museum" to "museum", "beach" to "beach", "toilets" to "toilets")
private val ORANGE = 0xFFF7A157.toInt() to 0xFFFDE7D2.toInt(); private val BLUE = 0xFF4BA3D3.toInt() to 0xFFD9EEF8.toInt()
private val PURPLE = 0xFF8F83BC.toInt() to 0xFFE3E2F3.toInt(); private val GREEN = 0xFF57A26D.toInt() to 0xFFD7EEDF.toInt()
private val RED = 0xFFE5635C.toInt() to 0xFFFADBD9.toInt(); private val SLATE = 0xFF6E8FA8.toInt() to 0xFFDDE7EE.toInt()
private fun drawnLook(kind: String): Triple<Int?, Pair<Int, Int>, String> = when (kind) {
    "pharmacy", "eopyy" -> Triple(R.drawable.ic_pharmacy, RED, ""); "hospital" -> Triple(R.drawable.ic_hospital, RED, "")
    "fuel", "charging" -> Triple(R.drawable.ic_fuel, SLATE, ""); "parking" -> Triple(R.drawable.ic_parking, BLUE, "")
    "disabled_parking" -> Triple(R.drawable.ic_accessible, BLUE, ""); "hotel" -> Triple(R.drawable.ic_hotel, PURPLE, "")
    "bakery" -> Triple(R.drawable.ic_shop, ORANGE, ""); "atm" -> Triple(null, GREEN, "ATM"); "toll" -> Triple(null, ORANGE, "€")
    else -> Triple(R.drawable.ic_place, PURPLE, "")
}

/** The marker for a public [kind]: the map's sprite, or ours drawn in its style; the plain pin for one without (a town, "bar"). */
private fun markerOf(kind: String) = SPRITE[kind] ?: if (MapsSearch.PILLS.any { it.key == kind }) "k-$kind" else "k-pin"

/** The drawn markers ("k-<kind>") and the favourites' own ("e-<icon>", Maps' indigo circle) put into the map's style once. */
private fun addMarkers(context: Context, style: Style) {
    val d = context.resources.displayMetrics.density
    fun square(icon: Int?, colors: Pair<Int, Int>, text: String): android.graphics.Bitmap {
        val px = (20 * d).toInt(); val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b); val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val r = android.graphics.RectF(d, d, px - d, px - d); val corner = px * 0.28f
        paint.color = colors.second; c.drawRoundRect(r, corner, corner, paint)
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 1.6f * d; paint.color = colors.first; c.drawRoundRect(r, corner, corner, paint)
        paint.style = android.graphics.Paint.Style.FILL
        if (icon != null) androidx.core.content.ContextCompat.getDrawable(context, icon)?.mutate()?.let { dr ->
            dr.setTint(colors.first); val m = (px * 0.22f).toInt(); dr.setBounds(m, m, px - m, px - m); dr.draw(c)
        } else {
            paint.textAlign = android.graphics.Paint.Align.CENTER; paint.isFakeBoldText = true
            paint.textSize = px * (if (text.length > 1) 0.34f else 0.6f); c.drawText(text, px / 2f, px / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        }
        return b
    }
    fun round(icon: Int): android.graphics.Bitmap {
        val px = (28 * d).toInt(); val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b); val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE; c.drawCircle(px / 2f, px / 2f, px / 2f, paint)
        paint.color = 0xFF5C6BC0.toInt(); c.drawCircle(px / 2f, px / 2f, px / 2f - px / 14f, paint)
        androidx.core.content.ContextCompat.getDrawable(context, icon)?.mutate()?.let { dr -> dr.setTint(android.graphics.Color.WHITE); val m = px / 4; dr.setBounds(m, m, px - m, px - m); dr.draw(c) }
        return b
    }
    (MapsSearch.PILLS.map { it.key }.filter { it !in SPRITE } + "pin").forEach { k -> val (icon, colors, text) = drawnLook(k); style.addImage("k-$k", square(icon, colors, text)) }
    Place.ICONS.forEach { style.addImage("e-$it", round(placeIcon(it))) }
}

/** [rolling]: Settings › Maps › On foot is Rolling, a wheelchair (the user, 21 Oct: "Walking to be changed the icon to rolling"). */
internal fun modeIcon(mode: String, rolling: Boolean = false) = when (mode) {
    "foot" -> if (rolling) R.drawable.ic_accessible else R.drawable.ic_directions_walk
    "bike" -> R.drawable.ic_directions_bike
    else -> R.drawable.ic_directions_car
}

private fun modeLabel(mode: String, rolling: Boolean = false) = when (mode) { "foot" -> if (rolling) "Roll" else "Walk"; "bike" -> "Bike"; else -> "Car" }

/** The ways there, before starting: [chosen] is the one drawn bold. */
private data class Preview(val target: Place, val mode: String, val routes: List<Route>, val chosen: Int, val from: Location)

/** A navigation running: the route, where on it the last fix was, and what has been said. */
private data class Nav(
    val target: Place, val mode: String, val route: Route, val cum: DoubleArray, val since: Long,
    val index: Int = 0, val p: Progress? = null, val offFixes: Int = 0, val said: Set<String> = emptySet(),
)

/**
 * Settings › Maps. The directions' language, words and voice alike (the user, 21 Oct: "at what loaguage will I be able to
 * change it? Eng or Gr?"); on foot walking or rolling; where the car is parked, kept and opened (the user, 21 Oct: "save
 * park location … save automaticaly and have the map start from location to that parking spot automaticaly at the next open").
 */
internal object MapsPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("maps", Context.MODE_PRIVATE)
    /** The map's own look, apart from the app's (the user, 21 Oct: "I dont like the dark theme on the map"): light unless set. */
    fun mapTheme(c: Context) = prefs(c).getString("map_theme", "light") ?: "light"
    fun setMapTheme(c: Context, theme: String) = prefs(c).edit().putString("map_theme", theme).apply()
    fun rolling(c: Context) = prefs(c).getBoolean("rolling", false)
    fun setRolling(c: Context, on: Boolean) = prefs(c).edit().putBoolean("rolling", on).apply()
    fun autoPark(c: Context) = prefs(c).getBoolean("auto_park", true)
    fun setAutoPark(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_park", on).apply()
    fun openAtCar(c: Context) = prefs(c).getBoolean("open_at_car", false)
    fun setOpenAtCar(c: Context, on: Boolean) = prefs(c).edit().putBoolean("open_at_car", on).apply()
    fun lang(c: Context) = prefs(c).getString("lang", "el") ?: "el"
    fun setLang(c: Context, lang: String) = prefs(c).edit().putString("lang", lang).apply()
    fun ttsLocale(lang: String): Locale = Locale.forLanguageTag(if (lang == "en") "en-GB" else "el-GR")
    fun inDistance(lang: String, metres: Double, car: Boolean, then: String): String {
        val (d, km) = spokenDistance(metres, car)
        val n = if (d % 1.0 == 0.0) d.toLong().toString() else if (lang == "en") d.toString() else d.toString().replace('.', ',')
        return when {
            lang == "en" -> "In $n ${if (km) (if (d == 1.0) "kilometre" else "kilometres") else "metres"}, $then"
            km -> "Σε $n ${if (d == 1.0) "χιλιόμετρο" else "χιλιόμετρα"}, $then"
            else -> "Σε $n μέτρα, $then"
        }
    }
    fun sample(lang: String) = if (lang == "en") "In 300 metres, turn right onto Panepistimiou" else "Σε 300 μέτρα, στρίψτε δεξιά στην Πανεπιστημίου"
}

private fun hasLocation(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Maps (Plan.md § Maps): Greece offline (Tetra/Maps/greece.pmtiles, Protomaps' light or dark style with Greek names), where you are,
 * your saved places (long-press the map to save one), and turn-by-turn directions by car, on foot or by bike, worked out
 * on the phone (MapsRouting) and spoken in Greek. [go] (a place on Home's card) starts its directions as soon as there is a fix.
 */
@SuppressLint("MissingPermission")
@Composable internal fun MapsScreen(root: File, go: Place?, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapFile = MapsFiles.map(root)
    val graphDir = MapsFiles.graph(root)
    var places by remember { mutableStateOf(MapsFiles.places(root)) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var editing by remember { mutableStateOf<Place?>(null) }
    var deleting by remember { mutableStateOf<Place?>(null) }
    var tab by remember { mutableStateOf<MapsTab?>(null) }
    var query by remember { mutableStateOf("") }
    var pill by remember { mutableStateOf<String?>(null) }
    var navQuery by remember { mutableStateOf("") }
    var navMode by remember { mutableStateOf("car") }
    var shownList by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf(MapsFiles.lists(root)) }
    var listEditing by remember { mutableStateOf<PlaceList?>(null) }
    var detail by remember { mutableStateOf<Poi?>(null) } // what the search list knows of the place shown
    var all by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var added by remember { mutableStateOf(runCatching { MapsSearch.added(root) }.getOrDefault(emptyList())) }
    /** After a change to the map's additions: they and the search list read again. */
    fun reloadAdded() = scope.launch {
        added = withContext(Dispatchers.IO) { MapsSearch.added(root) }
        all = withContext(Dispatchers.IO) { runCatching { MapsSearch.load(root) }.getOrDefault(all) }
    }
    var listLoading by remember { mutableStateOf(true) }
    var center by remember { mutableStateOf(LatLng(38.4, 23.8)) }
    var near by remember { mutableStateOf<Place?>(null) } // Nearby: Search around this place instead of the map's middle
    var results by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var onMap by remember { mutableStateOf<Pair<String, List<Poi>>?>(null) } // Search's OK: what it found, left on the map, and its name
    var navResults by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var road by remember { mutableStateOf<Pair<String, String>?>(null) } // the road under me: name, code
    var limit by remember { mutableStateOf<Int?>(null) } // its speed limit, km/h
    var speed by remember { mutableStateOf<Int?>(null) } // mine, km/h
    var limitAsked by remember { mutableStateOf(0L) }
    var lastReal by remember { mutableStateOf<Location?>(null) } // the last fix from the GPS itself
    var lastRealAt by remember { mutableStateOf(0L) } // when it came, by this device's clock (a fix's own stamp can be empty)
    var reckoning by remember { mutableStateOf(false) } // no signal: going on along the route at the last speed
    var address by remember { mutableStateOf<String?>(null) } // of the place shown
    var here by remember { mutableStateOf<Location?>(null) }
    var live by remember { mutableStateOf(false) } // a fix came while the map is open (not Android's stored last one)
    var granted by remember { mutableStateOf(hasLocation(context)) }
    var nav by remember { mutableStateOf<Nav?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var navSheet by remember { mutableStateOf(false) } // the handle's sheet over the directions bar: tolls and the turns ahead
    var finding by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(go) }
    var muted by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasLocation(context) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    // the voice: the directions' language (Settings › Maps)
    val lang = remember { MapsPrefs.lang(context) }
    val rolling = remember { MapsPrefs.rolling(context) }
    fun profile(mode: String) = if (mode == "foot" && rolling) "wheelchair" else mode
    var fast by remember { mutableStateOf(0) } // fixes in a row at car speed
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(context.applicationContext) { ok -> if (ok == TextToSpeech.SUCCESS) { t?.language = MapsPrefs.ttsLocale(lang); tts.value = t } }
        onDispose { t.stop(); t.shutdown() }
    }
    fun say(text: String) { WriterLog.i("Maps: say \"$text\"${if (muted) " (muted)" else ""}"); if (!muted) tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maps") }

    /** The car is here: one place, "My car", saved over the last one. */
    fun park(loc: Location, why: String) {
        WriterLog.i("Maps: parked ($why)")
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { MapsFiles.save(root, Place(MapsFiles.PARKING, "My car", loc.latitude, loc.longitude, "parking", mode = "foot")) } }
            places = withContext(Dispatchers.IO) { MapsFiles.places(root) }
            Toast.makeText(context, "Parking saved", Toast.LENGTH_SHORT).show()
        }
    }
    /** The car moves on: the parking spot goes (on foot or by bike it stays). */
    fun unpark(why: String) {
        if (places.none { it.id == MapsFiles.PARKING }) return
        WriterLog.i("Maps: parking cleared ($why)")
        scope.launch {
            withContext(Dispatchers.IO) { places.firstOrNull { it.id == MapsFiles.PARKING }?.let { MapsFiles.delete(root, it) } }
            places = withContext(Dispatchers.IO) { MapsFiles.places(root) }
        }
    }

    fun stopNav() {
        nav = null
        mapRef?.locationComponent?.takeIf { it.isLocationComponentActivated }?.apply { cameraMode = CameraMode.NONE; renderMode = RenderMode.COMPASS }
    }

    /** The camera on me, turned the way I go; zoom and tilt through the tracking itself (a camera move of ours would end it). */
    fun follow(mode: String) {
        val lc = mapRef?.locationComponent?.takeIf { it.isLocationComponentActivated } ?: return
        lc.renderMode = RenderMode.GPS
        lc.setCameraMode(CameraMode.TRACKING_GPS, object : org.maplibre.android.location.OnLocationCameraTransitionListener {
            override fun onLocationCameraTransitionFinished(cameraMode: Int) {
                lc.zoomWhileTracking(if (mode == "car") 16.5 else 17.5); lc.tiltWhileTracking(if (mode == "car") 45.0 else 0.0)
            }
            override fun onLocationCameraTransitionCanceled(cameraMode: Int) = Unit
        })
    }
    var fix: (Location) -> Unit = {} // onFix, below: it and startNav call each other
    /** Directions on [route], from [from]: spoken, followed, the car's spot cleared for a car. */
    fun begin(target: Place, mode: String, route: Route, from: Location) {
        WriterLog.i("Maps: route by $mode, ${route.metres.toInt()} m, ${route.steps.size} steps, ${route.roads}")
        val rerouted = nav?.target?.id == target.id
        nav = Nav(target, mode, route, cumulative(route), System.currentTimeMillis())
        selected = null; preview = null
        if (!rerouted) route.steps.firstOrNull()?.let { say(it.text) }
        follow(mode)
        fix(from) // the first distance at once, not at the next fix
    }

    /** [choose]: the ways there shown to pick from (a place's Car / Walk / Bike); else the fastest at once (Home's one tap, a new route). */
    fun startNav(target: Place, mode: String, from: Location? = here, choose: Boolean = false) {
        // directions start from a live fix: Android's stored last one can be hours old and towns away
        if (from == null || !live) { pending = target.copy(mode = mode); return }
        if (!File(graphDir, "properties").isFile) {
            Toast.makeText(context, "No routing graph yet: put greece-gh in Tetra/Maps (the computer's tools/maps-greece.sh makes it).", Toast.LENGTH_LONG).show(); return
        }
        if (mode == "car" && target.id != MapsFiles.PARKING) unpark("a car trip started")
        finding = "Finding the way…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { MapsRouting.routes(graphDir, from.latitude, from.longitude, target.lat, target.lng, profile(mode), lang) } }
            finding = null
            r.onSuccess { routes ->
                if (choose && routes.size > 1) {
                    preview = Preview(target, mode, routes, 0, from); selected = null
                    val b = LatLngBounds.Builder()
                    routes.forEach { rt -> rt.lats.indices.forEach { i -> b.include(LatLng(rt.lats[i], rt.lngs[i])) } }
                    runCatching { mapRef?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120)) }
                } else begin(target, mode, routes.first(), from)
            }.onFailure {
                WriterLog.e("Maps: no route by $mode", it)
                Toast.makeText(context, "No way found: ${it.message ?: "the routing graph could not be read"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Each fix: where on the route, what to say, and a new route when off it for three fixes in a row. */
    fun onFix(loc: Location) {
        if (loc.provider != "tetra-reckoning") { lastReal = loc; lastRealAt = android.os.SystemClock.elapsedRealtimeNanos(); reckoning = false }
        here = loc; live = true
        // at car speed (40 km/h) for three fixes: driving, so the car is no longer where it was parked
        fast = if (loc.hasSpeed() && loc.speed > 11f) fast + 1 else 0
        if (fast == 3 && nav?.mode != "foot" && nav?.mode != "bike") unpark("driving")
        mapRef?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(loc)
        road = roadAt(mapRef, loc)
        speed = if (loc.hasSpeed()) Math.round(loc.speed * 3.6f) else null
        // the limit at most every two seconds, off the main thread (the graph is opened the first time)
        if (System.currentTimeMillis() - limitAsked > 2000 && File(graphDir, "properties").isFile) {
            limitAsked = System.currentTimeMillis()
            scope.launch { limit = withContext(Dispatchers.IO) { runCatching { MapsRouting.limitAt(graphDir, loc.latitude, loc.longitude) }.getOrNull() } }
        }
        val n = nav ?: return
        val (p, i) = progress(n.route, n.cum, loc.latitude, loc.longitude, n.index)
        val car = n.mode == "car"
        if (p.off > (if (car) 50.0 else 30.0)) {
            val off = n.offFixes + 1
            nav = n.copy(offFixes = off)
            if (off >= 3 && System.currentTimeMillis() - n.since > 10_000 && finding == null) {
                WriterLog.i("Maps: off the route by ${p.off.toInt()} m, a new one"); startNav(n.target, n.mode, loc)
            }
            return
        }
        var said = n.said
        // there: at the route's end, or near the place itself (a place saved off the road ends its route short of it)
        if (p.left < (if (car) 50.0 else 20.0) || metresBetween(loc.latitude, loc.longitude, n.target.lat, n.target.lng) < (if (car) 60.0 else 25.0)) {
            WriterLog.i("Maps: arrived, ${p.left.toInt()} m left on the route")
            if ("n${n.route.steps.lastIndex}" !in said) say(n.route.steps.lastOrNull()?.text ?: "")
            stopNav()
            if (car && MapsPrefs.autoPark(context)) park(loc, "a car trip ended")
            Toast.makeText(context, "Arrived: ${n.target.name}", Toast.LENGTH_LONG).show(); return
        }
        val step = n.route.steps[p.next]
        when (dueWarning(p.next, p.toNext, if (loc.hasSpeed()) loc.speed.toDouble() else 0.0, car, said)) {
            "n" -> { say(step.text); said = said + "n${p.next}" + "f${p.next}" + "e${p.next}" }
            "f" -> { say(MapsPrefs.inDistance(lang, p.toNext, car, step.text)); said = said + "f${p.next}" + "e${p.next}" }
            "e" -> { say(MapsPrefs.inDistance(lang, p.toNext, car, step.text)); said = said + "e${p.next}" }
        }
        nav = n.copy(index = i, p = p, offFixes = 0, said = said)
    }

    fix = ::onFix
    DisposableEffect(granted) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            // a network fix only until GPS has one, and never over a fresher GPS one
            val h = here
            if (loc.provider == LocationManager.NETWORK_PROVIDER && h != null && h.provider == LocationManager.GPS_PROVIDER && loc.time - h.time < 10_000) return@LocationListener
            // in directions, a cell tower's rough fix does not stand for the GPS for two minutes after its last: in a tunnel
            // the arrow goes on along the route instead of jumping to it
            if (loc.provider == LocationManager.NETWORK_PROVIDER && nav != null && lastReal?.provider == LocationManager.GPS_PROVIDER &&
                android.os.SystemClock.elapsedRealtimeNanos() - lastRealAt < 120_000_000_000L) return@LocationListener
            onFix(loc)
        }
        if (granted && lm != null) {
            runCatching {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }.forEach { pr ->
                    lm.getLastKnownLocation(pr)?.let { if (here == null || it.time > here!!.time) here = it }
                    lm.requestLocationUpdates(pr, 1000L, 0f, listener, context.mainLooper)
                }
            }.onFailure { WriterLog.e("Maps: location updates", it) }
        }
        onDispose { lm?.removeUpdates(listener) }
    }
    // the next open after parking: on foot to the car (Settings › Maps), unless already by it
    LaunchedEffect(Unit) {
        if (go == null && MapsPrefs.openAtCar(context)) places.firstOrNull { it.id == MapsFiles.PARKING }?.let { pending = it.copy(mode = "foot") }
    }
    LaunchedEffect(pending, here, live) {
        val p = pending ?: return@LaunchedEffect
        val h = here ?: return@LaunchedEffect
        if (!live) return@LaunchedEffect
        pending = null
        if (p.id == MapsFiles.PARKING && go == null && metresBetween(h.latitude, h.longitude, p.lat, p.lng) < 80) return@LaunchedEffect
        startNav(p, p.mode)
    }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { runCatching { MapsSearch.load(root) }.onFailure { WriterLog.e("Maps: search list", it) }.getOrDefault(emptyList()) }
        listLoading = false
    }
    // the user's own accessible places count as disabled parking (OSM knows few: none in Xanthi on 21 Oct)
    fun mine(kind: String?) = if (kind == "disabled_parking") places.filter { it.icon == "accessible" }.map { Poi(kind, it.lat, it.lng, it.name, more = "Saved") } else emptyList()
    LaunchedEffect(query, pill, center, all, places, near) {
        val c = near?.let { LatLng(it.lat, it.lng) } ?: center
        results = withContext(Dispatchers.Default) { (mine(pill) + MapsSearch.search(all, query, c.latitude, c.longitude, pill, nearby = near != null)).let { r ->
            if (pill == "disabled_parking") r.sortedBy { metresBetween(c.latitude, c.longitude, it.lat, it.lng) } else r } }
    }
    LaunchedEffect(tab) { if (tab != MapsTab.Search) near = null }
    LaunchedEffect(selected, all) {
        val s = selected ?: run { address = null; return@LaunchedEffect }
        address = null
        address = withContext(Dispatchers.IO) { runCatching { MapsAddresses.describe(root, all, s.lat, s.lng) }.getOrNull() }
        // a dropped pin is named by its address
        if (s.id.isEmpty() && detail == null && address != null && selected == s && (s.name == "Dropped pin" || s.name == road?.first)) selected = s.copy(name = address!!.substringBefore(","))
    }
    LaunchedEffect(navQuery, center, all) {
        navResults = withContext(Dispatchers.Default) { MapsSearch.search(all, navQuery, center.latitude, center.longitude) }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(mapView, lifecycle) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> mapView.onStart()
            Lifecycle.Event.ON_RESUME -> mapView.onResume()
            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
            Lifecycle.Event.ON_STOP -> mapView.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onStop(); mapView.onDestroy() }
    }
    LaunchedEffect(mapView) {
        if (!mapFile.isFile) return@LaunchedEffect
        val json = context.assets.open("maps/${MapsPrefs.mapTheme(context)}.json").bufferedReader().use { it.readText() }
            .replace("pmtiles://MAP_FILE", "pmtiles://file://${mapFile.absolutePath}")
        mapView.getMapAsync { map ->
            map.setLatLngBoundsForCameraTarget(GREECE)
            map.setMinZoomPreference(5.0); map.setMaxZoomPreference(19.0)
            map.cameraPosition = (go ?: here?.let { Place("", "", it.latitude, it.longitude) })
                ?.let { CameraPosition.Builder().target(LatLng(it.lat, it.lng)).zoom(14.0).build() }
                ?: CameraPosition.Builder().target(LatLng(38.4, 23.8)).zoom(5.6).build()
            map.setStyle(Style.Builder().fromJson(json)) { style ->
                style.addSource(GeoJsonSource("alts"))
                style.addLayer(LineLayer("alts-line", "alts").withProperties(PropertyFactory.lineColor("#9E9E9E"), PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND), PropertyFactory.lineOpacity(0.8f)))
                style.addSource(GeoJsonSource("route"))
                style.addSource(GeoJsonSource("tolls"))
                style.addLayer(LineLayer("route-casing", "route").withProperties(PropertyFactory.lineColor("#1A237E"), PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("route-line", "route").withProperties(PropertyFactory.lineColor("#7986CB"), PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                // places put into the map's own data by hand (added-places.tsv): a small dot and the name, as the map's own
                val dark = MapsPrefs.mapTheme(context) == "dark"
                addMarkers(context, style)
                style.addSource(GeoJsonSource("added"))
                style.addLayer(SymbolLayer("added-dot", "added").withProperties(PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconSize(0.8f)))
                style.addLayer(SymbolLayer("added-name", "added").withProperties(PropertyFactory.textField("{name}"), PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f), PropertyFactory.textOffset(arrayOf(0f, 1.1f)), PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textColor(if (dark) "#E0E0E0" else "#4A3A70"), PropertyFactory.textHaloColor(if (dark) "#000000" else "#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.2f), PropertyFactory.textOptional(true)))
                style.addLayer(CircleLayer("tolls-dot", "tolls").withProperties(PropertyFactory.circleRadius(9f), PropertyFactory.circleColor("#F9A825"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2f)))
                style.addLayer(SymbolLayer("tolls-sign", "tolls").withProperties(PropertyFactory.textField("€"), PropertyFactory.textFont(arrayOf("Noto Sans Medium")),
                    PropertyFactory.textSize(12f), PropertyFactory.textColor("#3E2723"), PropertyFactory.textAllowOverlap(true)))
                style.addSource(GeoJsonSource("places"))
                style.addLayer(SymbolLayer("places-dot", "places").withProperties(PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true)))
                style.addLayer(SymbolLayer("places-name", "places").withProperties(PropertyFactory.textField("{name}"),
                    PropertyFactory.textFont(arrayOf("Noto Sans Medium")), PropertyFactory.textSize(13f), PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP), PropertyFactory.textColor("#FFFFFF"), PropertyFactory.textHaloColor("#1A237E"),
                    PropertyFactory.textHaloWidth(1.5f), PropertyFactory.textAllowOverlap(true)))
                style.addSource(GeoJsonSource("results"))
                style.addLayer(SymbolLayer("results-dot", "results").withProperties(PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconSize(0.9f)))
                map.addOnCameraIdleListener { center = map.cameraPosition.target ?: center }
                map.addOnMapLongClickListener { at ->
                    if (nav == null) editing = Place("", "", at.latitude, at.longitude)
                    true
                }
                // a tap: my place, a search result, a place of the map's own, or a dropped pin (the user: "how do I start
                // navigation if cllicking does not add a mark on the read?")
                map.addOnMapClickListener { at ->
                    if (nav != null) return@addOnMapClickListener false
                    val pt = map.projection.toScreenLocation(at)
                    val box = android.graphics.RectF(pt.x - 24, pt.y - 24, pt.x + 24, pt.y + 24)
                    val mineHit = map.queryRenderedFeatures(box, "places-dot").firstOrNull()?.getStringProperty("id")?.let { id -> places.firstOrNull { it.id == id } }
                    val resultHit = map.queryRenderedFeatures(box, "results-dot").firstOrNull()?.let { f -> (f.geometry() as? Point)?.let { g ->
                        (if (tab == MapsTab.Search) results else onMap?.second.orEmpty()).minByOrNull { metresBetween(g.latitude(), g.longitude(), it.lat, it.lng) } } }
                    val addedHit = map.queryRenderedFeatures(box, "added-dot").firstOrNull()?.let { f -> (f.geometry() as? Point)?.let { g ->
                        added.minByOrNull { metresBetween(g.latitude(), g.longitude(), it.lat, it.lng) } } }
                    val mapHit = map.queryRenderedFeatures(box, "pois").firstOrNull { it.geometry() is Point && it.hasProperty("name") }
                    when {
                        mineHit != null -> { selected = mineHit; detail = MapsSearch.near(all, mineHit.lat, mineHit.lng, mineHit.name, 25.0) }
                        resultHit != null -> { detail = resultHit; selected = resultHit.place() }
                        addedHit != null -> { detail = addedHit; selected = addedHit.place() }
                        mapHit != null -> {
                            val g = mapHit.geometry() as Point
                            val name = mapHit.getStringProperty("name").orEmpty()
                            val p = MapsSearch.near(all, g.latitude(), g.longitude(), name)
                                ?: Poi(mapHit.getStringProperty("kind").orEmpty(), g.latitude(), g.longitude(), name)
                            detail = p; selected = p.place()
                        }
                        else -> {
                            val r = roadAtPoint(map, pt)
                            detail = null
                            selected = Place("", r?.first?.ifEmpty { null } ?: "Dropped pin", at.latitude, at.longitude)
                        }
                    }
                    tab = null
                    true
                }
                mapRef = map
            }
        }
    }
    // the location dot, once the map and the permission are there
    LaunchedEffect(mapRef, granted) {
        val map = mapRef ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        if (!granted || map.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        // a small arrow, so the road under it shows (the user, 21 Oct: "The arrow seams too big for the road")
        map.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false)
            .locationComponentOptions(org.maplibre.android.location.LocationComponentOptions.builder(context).maxZoomIconScale(0.6f).minZoomIconScale(0.45f).build()).build())
        map.locationComponent.isLocationComponentEnabled = true
        map.locationComponent.renderMode = RenderMode.COMPASS
        here?.let { map.locationComponent.forceLocationUpdate(it) }
    }
    // the added ones, but not where one of my places stands on it (the five work schools are both)
    LaunchedEffect(mapRef, places) {
        (mapRef?.style?.getSource("added") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(added
            .filter { a -> places.none { metresBetween(it.lat, it.lng, a.lat, a.lng) < 30 } }
            .map { a -> Feature.fromGeometry(Point.fromLngLat(a.lng, a.lat)).apply { addStringProperty("name", a.title); addStringProperty("icon", markerOf(a.kind)) } }))
    }
    LaunchedEffect(mapRef, places) {
        (mapRef?.style?.getSource("places") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(places.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply { addStringProperty("id", p.id); addStringProperty("name", p.name); addStringProperty("icon", "e-${p.icon}") }
        }))
    }
    LaunchedEffect(mapRef, results, tab, selected) {
        val shown = if (tab == MapsTab.Search) results else onMap?.second.orEmpty()
        val pin = selected?.takeIf { s -> s.id.isEmpty() }
        (mapRef?.style?.getSource("results") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
            shown.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).apply { addStringProperty("icon", markerOf(it.kind)) } } +
            listOfNotNull(pin?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).apply { addStringProperty("icon", markerOf(detail?.kind.orEmpty())) } })))
    }
    // the tolls on the route being driven or chosen, dots on the map
    val navTolls = remember(nav?.route, all) { nav?.let { tollsOn(it.route, it.cum, all) }.orEmpty() }
    val previewTolls = remember(preview?.routes, all) { preview?.routes?.map { tollsOn(it, cumulative(it), all) }.orEmpty() }
    LaunchedEffect(mapRef, navTolls, previewTolls, preview?.chosen) {
        val shown = if (nav != null) navTolls else previewTolls.getOrNull(preview?.chosen ?: -1).orEmpty()
        (mapRef?.style?.getSource("tolls") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(shown.map { (t, _) -> Feature.fromGeometry(Point.fromLngLat(t.lng, t.lat)) }))
    }
    LaunchedEffect(mapRef, nav?.route, preview) {
        val src = mapRef?.style?.getSource("route") as? GeoJsonSource ?: return@LaunchedEffect
        fun line(r: Route) = Feature.fromGeometry(LineString.fromLngLats(r.lats.indices.map { Point.fromLngLat(r.lngs[it], r.lats[it]) }))
        (mapRef?.style?.getSource("alts") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
            preview?.let { p -> p.routes.filterIndexed { i, _ -> i != p.chosen }.map(::line) }.orEmpty()))
        val r = nav?.route ?: preview?.let { it.routes[it.chosen] }
        src.setGeoJson(if (r == null) FeatureCollection.fromFeatures(emptyList()) else
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(r.lats.indices.map { Point.fromLngLat(r.lngs[it], r.lats[it]) }))))
    }
    /*
     * A tunnel (the user, 21 Oct: "What if I loose GPS sygnal on a tannel … will it come back after the return of the sygnal"):
     * with no fix for 3 s while directions run at speed, the arrow goes on along the route at the last speed, a second at a
     * time, for at most 10 km; the first real fix takes over again (and a new route only if that fix is off the route).
     */
    LaunchedEffect(nav != null) {
        while (nav != null) {
            delay(1000)
            val n = nav ?: break
            val real = lastReal ?: continue
            val quiet = android.os.SystemClock.elapsedRealtimeNanos() - lastRealAt
            val v = if (real.hasSpeed()) real.speed.toDouble() else 0.0
            val p = n.p ?: continue
            if (quiet < 3_000_000_000L || v < 3 || p.left < 50) { reckoning = false; continue }
            val gone = v * quiet / 1e9
            if (gone > 10_000) { reckoning = false; continue }
            val start = progress(n.route, n.cum, real.latitude, real.longitude).first.along
            val at = (start + gone).coerceAtMost(n.cum.last())
            val i = n.cum.indexOfLast { it <= at }.coerceIn(0, n.cum.size - 2)
            val t = ((at - n.cum[i]) / (n.cum[i + 1] - n.cum[i]).coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
            val lat = n.route.lats[i] + t * (n.route.lats[i + 1] - n.route.lats[i]); val lng = n.route.lngs[i] + t * (n.route.lngs[i + 1] - n.route.lngs[i])
            if (!reckoning) WriterLog.i("Maps: no GPS fix for ${quiet / 1_000_000_000} s, going on along the route at ${(v * 3.6).toInt()} km/h")
            reckoning = true
            fix(Location("tetra-reckoning").apply {
                setLatitude(lat); setLongitude(lng); setSpeed(v.toFloat()); setTime(System.currentTimeMillis())
                setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos()); setAccuracy(50f)
                setBearing(Location("").apply { setLatitude(n.route.lats[i]); setLongitude(n.route.lngs[i]) }
                    .bearingTo(Location("").apply { setLatitude(n.route.lats[i + 1]); setLongitude(n.route.lngs[i + 1]) }))
            })
        }
    }
    // the screen stays on while directions run
    DisposableEffect(nav != null) {
        mapView.keepScreenOn = nav != null
        onDispose { mapView.keepScreenOn = false }
    }
    BackHandler(nav != null || selected != null || tab != null || preview != null) {
        when { preview != null -> { selected = preview?.target; preview = null }; selected != null -> selected = null; tab != null -> tab = null; else -> stopNav() }
    }

    Box(Modifier.fillMaxSize()) {
        if (mapFile.isFile) AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = MAPS_COLOR, modifier = Modifier.size(48.dp))
            Text("The map of Greece is not on this device yet", style = MaterialTheme.typography.titleLarge)
            Text("It comes from the computer: tools/maps-greece.sh makes greece.pmtiles (about 500 MB) and the routing graph greece-gh, " +
                "and Local Sync brings them here into Tetra/Maps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // bottom left: the speed limit as its sign, red when I am over it (by more than 3 km/h), my speed under it
        limit?.takeIf { live }?.let { l ->
            val over = (speed ?: 0) > l + 3
            // clear of the island and the bars below it (the user, 21 Oct: "ακουμπάει πάνω στο νησί … δώσε λίγο αέρα")
            Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = if (nav != null) 150.dp else 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = if (over) androidx.compose.ui.graphics.Color(0xFFD32F2F) else androidx.compose.ui.graphics.Color.White,
                    border = androidx.compose.foundation.BorderStroke(5.dp, androidx.compose.ui.graphics.Color(0xFFD32F2F)), modifier = Modifier.size(58.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$l", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                            color = if (over) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black)
                    }
                }
                speed?.let { Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.padding(top = 4.dp)) {
                    Text("$it km/h", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                } }
            }
        }

        // top left: back and the road I am on, with its code (the user, 21 Oct: "To the top left no title. info about the road the code");
        // no Back while directions run (the user: "the back button remove it when in active navigation")
        val n = nav
        val step = n?.let { nn -> nn.route.steps.getOrNull(((nn.p?.next ?: 1) - 1).coerceAtLeast(0)) }
        val roadName = step?.street?.ifEmpty { null } ?: road?.first?.ifEmpty { null }
        val roadCode = step?.ref?.ifEmpty { null } ?: road?.second?.ifEmpty { null }
        val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
        @Composable fun RoadPill() { if (roadName != null || roadCode != null) Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                roadCode?.let { RoadCode(it) }
                roadName?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp)) }
            }
        } }
        if (n != null && narrow) {
            // a narrow screen: one pill, the road above and the next turn below (the user, 21 Oct)
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                RoadPill()
                NavBanner(n, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (n == null) Island(R.drawable.ic_chevron_left, "Back", back)
                RoadPill()
            }
            // top right: the next turn in a small window (the user: "A small window will open at the top right to tell me what lane to take")
            if (n != null) NavBanner(n, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
        }
        // bottom right, above the island: P and where I am (the user, 21 Oct: at the top they covered the map's compass);
        // away while a panel or a place's page is open
        if (n == null && tab == null && selected == null && preview == null) Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Island(R.drawable.ic_parking, "I parked here") {
                val h = here
                if (h == null || !live) Toast.makeText(context, if (granted) "Waiting for your location…" else "Location is off for Tetra.", Toast.LENGTH_SHORT).show()
                else park(h, "the P button")
            }
            Island(R.drawable.ic_my_location, "Where I am") {
                val h = here
                if (h == null) Toast.makeText(context, if (granted) "Waiting for your location…" else "Location is off for Tetra.", Toast.LENGTH_SHORT).show()
                else mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(h.latitude, h.longitude), 16.0))
            }
        }

        // bottom: directions running; or a place's page; or a panel and the island
        // above the keyboard while one is typing in Search or Directions
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            finding?.let { Pill(it) }
            // what Search's OK left on the map, and its ✕
            onMap?.takeIf { nav == null && tab == null && selected == null }?.let { (name, found) ->
                Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape, modifier = Modifier.clickable { onMap = null }) {
                    Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$name · ${found.size}", style = MaterialTheme.typography.bodyMedium)
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (reckoning && nav != null) Pill("No GPS signal: going on along the route")
            if (pending != null && nav == null && finding == null) Pill(if (granted) "Waiting for your location…" else "Location is off for Tetra: directions need it.")
            val s = selected
            fun away(lat: Double, lng: Double) = here?.let { metresBetween(it.latitude, it.longitude, lat, lng) }
            if (n != null) Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
              Column {
                // the handle, as Tetra's others: drag up (or tap) for the tolls and the turns ahead
                var drag by remember { mutableStateOf(0f) }
                Box(Modifier.fillMaxWidth().clickable { navSheet = !navSheet }.pointerInput(Unit) {
                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onVerticalDrag = { _, d -> drag += d }, onDragEnd = { if (drag < -30) navSheet = true else if (drag > 30) navSheet = false })
                }.padding(top = 8.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(islandContentColor().copy(alpha = 0.5f)))
                }
                if (navSheet) NavSheet(n, navTolls, lang)
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val left = n.p?.left ?: n.route.metres
                        val minutes = Math.round(n.route.millis / 60_000.0 * (left / n.route.metres.coerceAtLeast(1.0)))
                        Text("${metresText(left)} · ${if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"}", style = MaterialTheme.typography.titleMedium)
                        Text(n.target.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { follow(n.mode) }) {
                        Icon(painterResource(R.drawable.ic_navigation), contentDescription = "Follow me")
                    }
                    IconButton(onClick = { muted = !muted; if (muted) tts.value?.stop() }) {
                        Icon(painterResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up), contentDescription = if (muted) "Voice on" else "Voice off")
                    }
                    Button(colors = panelButton(), onClick = {
                        stopNav()
                        if (n.mode == "car" && MapsPrefs.autoPark(context)) here?.let { park(it, "a car trip stopped") }
                    }) { Text("Stop") }
                }
              }
            } else if (preview != null) {
                val pv = preview!!
                RoutesPanel(pv.routes, previewTolls.map { it.size }, pv.chosen, { i -> preview = pv.copy(chosen = i) }, { preview = null; selected = pv.target },
                    { begin(pv.target, pv.mode, pv.routes[pv.chosen], here ?: pv.from) })
            } else if (s != null) {
                val saved = s.id.isNotEmpty() && places.any { it.id == s.id }
                PlaceCard(s, detail, address, saved, away(s.lat, s.lng), rolling, lists, go = { m -> startNav(s, m, choose = true) },
                    save = { editing = s.copy(name = if (s.name == "Dropped pin") "" else s.name) },
                    edit = { editing = s }, delete = { deleting = s }, close = { selected = null; detail = null },
                    remove = detail?.takeIf { d -> added.any { it.kind == d.kind && metresBetween(it.lat, it.lng, d.lat, d.lng) < 2 } }?.let { d -> {
                        selected = null; detail = null
                        scope.launch { withContext(Dispatchers.IO) { runCatching { MapsShare.remove(root, d) } }; reloadAdded() }
                    } }) {
                    // what is around it, in Search: nearest first, the pills to narrow it
                    near = s; query = ""; pill = null; selected = null; detail = null; tab = MapsTab.Search
                    mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lng), 15.0))
                }
            } else if (mapFile.isFile) {
                when (tab) {
                    // Nearby: how far from the place it is around, not from me
                    MapsTab.Search -> SearchPanel(query, { query = it }, pill, { pill = it }, results,
                        { p -> near?.let { n -> metresBetween(n.lat, n.lng, p.lat, p.lng) } ?: away(p.lat, p.lng) }, all.isNotEmpty() || listLoading,
                        near?.name, { near = null }, listLoading, ok = {
                            // what is on the screen and a little round it (half a screen each way), else the nearest found
                            val map = mapRef
                            val b = map?.projection?.visibleRegion?.latLngBounds
                            val c = near?.let { LatLng(it.lat, it.lng) } ?: center
                            scope.launch {
                                val found = withContext(Dispatchers.Default) {
                                    val many = (mine(pill) + MapsSearch.search(all, query, c.latitude, c.longitude, pill, limit = 2000, nearby = near != null))
                                    val inView = if (b == null) many else {
                                        val dLat = (b.latitudeNorth - b.latitudeSouth) / 2; val dLng = (b.longitudeEast - b.longitudeWest) / 2
                                        many.filter { it.lat in (b.latitudeSouth - dLat)..(b.latitudeNorth + dLat) && it.lng in (b.longitudeWest - dLng)..(b.longitudeEast + dLng) }
                                    }
                                    inView.ifEmpty { many.take(20) }.take(300)
                                }
                                val name = pill?.let { k -> MapsSearch.PILLS.firstOrNull { it.key == k }?.let { "${it.emoji} ${it.label}" } } ?: query.trim()
                                onMap = name to found
                                tab = null
                                WriterLog.i("Maps: OK on \"$name\", ${found.size} on the map")
                                if (found.isNotEmpty() && (b == null || found.none { b.contains(LatLng(it.lat, it.lng)) })) {
                                    val bb = LatLngBounds.Builder(); found.forEach { bb.include(LatLng(it.lat, it.lng)) }
                                    if (found.size > 1) runCatching { mapRef?.animateCamera(CameraUpdateFactory.newLatLngBounds(bb.build(), 120)) }
                                    else mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(found[0].lat, found[0].lng), 15.0))
                                }
                            }
                        }) { p ->
                        detail = p; selected = p.place(); tab = null
                        mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), if (MapsSearch.settlement(p.kind)) 13.0 else 16.5))
                    }
                    MapsTab.Favorites -> FavoritesPanel(places, lists, remember(all, here) { all.filter { it.kind == "eopyy" }.sortedBy { p -> away(p.lat, p.lng) ?: 0.0 } },
                        shownList, { shownList = it }, { listEditing = PlaceList("", "", "❤️") }, { listEditing = it }, { away(it.lat, it.lng) }, { p ->
                        selected = p; detail = MapsSearch.near(all, p.lat, p.lng, p.name, 25.0); tab = null
                        mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 16.0))
                    }) { p ->
                        detail = p; selected = p.place(); tab = null
                        mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 16.5))
                    }
                    MapsTab.Navigate -> NavigatePanel(navMode, { navMode = it }, rolling, navQuery, { navQuery = it }, navResults,
                        MapsFiles.slots(places).filterNotNull() + places.filter { it.id == MapsFiles.PARKING }, { away(it.lat, it.lng) },
                        { p -> tab = null; navQuery = ""; startNav(p.place(), navMode) }, { p -> tab = null; startNav(p, navMode) })
                    null -> Unit
                }
                MapsIsland(tab) { t -> tab = if (tab == t) null else t }
            }
        }
    }

    listEditing?.let { l -> ListDialog(l, { listEditing = null }, if (l.id.isEmpty()) null else ({
        listEditing = null; if (shownList == l.id) shownList = null
        scope.launch { withContext(Dispatchers.IO) { MapsFiles.deleteList(root, l) }; lists = withContext(Dispatchers.IO) { MapsFiles.lists(root) } }
    })) { saved ->
        listEditing = null
        scope.launch { withContext(Dispatchers.IO) { runCatching { MapsFiles.saveList(root, saved) } }; lists = withContext(Dispatchers.IO) { MapsFiles.lists(root) }; shownList = saved.id }
    } }
    editing?.let { p -> PlaceDialog(p, places, lists, { editing = null }, onMap = { poi ->
        editing = null
        scope.launch { withContext(Dispatchers.IO) { runCatching { MapsShare.add(root, poi) } }; reloadAdded(); detail = poi; selected = poi.place() }
    }) { saved ->
        editing = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { MapsFiles.save(root, saved) } }
            r.onFailure { Toast.makeText(context, it.message ?: "Could not save the place.", Toast.LENGTH_LONG).show() }
            places = withContext(Dispatchers.IO) { MapsFiles.places(root) }
            selected = r.getOrNull()
        }
    } }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${p.name}?") },
        text = { Text(if (p.slot > 0) "It also leaves Home's Maps card." else "The place is removed on every device.") },
        confirmButton = { TextButton(onClick = {
            deleting = null; selected = null
            scope.launch { withContext(Dispatchers.IO) { MapsFiles.delete(root, p) }; places = withContext(Dispatchers.IO) { MapsFiles.places(root) } }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

/** The road the map draws under [pt] (screen pixels): its name and code (A2, EO1…), from the map's own roads. */
internal fun roadAtPoint(map: MapLibreMap, pt: android.graphics.PointF): Pair<String, String>? {
    val ids = map.style?.layers.orEmpty().map { it.id }.filter { it.startsWith("roads_") && listOf("casing", "labels", "shields", "oneway", "rail", "runway", "taxiway", "pier").none { x -> x in it } }
    if (ids.isEmpty()) return null
    val f = map.queryRenderedFeatures(android.graphics.RectF(pt.x - 12, pt.y - 12, pt.x + 12, pt.y + 12), *ids.toTypedArray())
        .firstOrNull { it.hasProperty("name") || it.hasProperty("ref") } ?: return null
    val name = (f.getStringProperty("name:el")?.takeIf { it.isNotEmpty() } ?: f.getStringProperty("name")).orEmpty()
    val ref = (if (f.hasProperty("ref")) f.getStringProperty("ref") else if (f.hasProperty("shield_text")) f.getStringProperty("shield_text") else null).orEmpty()
    return (name to ref.substringBefore(';')).takeIf { it.first.isNotEmpty() || it.second.isNotEmpty() }
}

private fun roadAt(map: MapLibreMap?, loc: Location): Pair<String, String>? =
    map?.let { m -> runCatching { roadAtPoint(m, m.projection.toScreenLocation(LatLng(loc.latitude, loc.longitude))) }.getOrNull() }

/** A road's code as on its signs: motorways green, the rest blue. */
@Composable private fun RoadCode(code: String) = Surface(shape = MaterialTheme.shapes.small,
    color = if (code.startsWith("A") || code.startsWith("Α")) androidx.compose.ui.graphics.Color(0xFF1B7F3B) else androidx.compose.ui.graphics.Color(0xFF1565C0)) {
    Text(code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable private fun Island(icon: Int, label: String, click: () -> Unit) = Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
    IconButton(onClick = click) { Icon(painterResource(icon), contentDescription = label) }
}

@Composable private fun Pill(text: String) = Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

/** Under the handle: the tolls still ahead (name, operator and price where OSM has them, how far) and the next turns. */
@Composable private fun NavSheet(n: Nav, tolls: List<Pair<Poi, Double>>, lang: String) {
    val along = n.p?.along ?: 0.0
    val ahead = tolls.filter { it.second > along - 20 }
    LazyColumn(Modifier.heightIn(max = 320.dp).padding(horizontal = 16.dp)) {
        item { Text(if (ahead.isEmpty()) "No tolls ahead" else "Tolls ahead: ${ahead.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp)) }
        items(ahead) { (t, at) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💶")
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t.more.isNotEmpty()) Text(t.more, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                }
                Text(metresText(at - along), style = MaterialTheme.typography.labelMedium)
            }
        }
        item { Text("Next turns", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
        val cum = n.cum
        items(n.route.steps.drop(n.p?.next ?: 0).take(8)) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metresText((cum[s.at.coerceAtMost(cum.lastIndex)] - along).coerceAtLeast(0.0)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The next turn: its arrow, how far, and GraphHopper's words for it. */
@Composable private fun NavBanner(n: Nav, modifier: Modifier) {
    val step = n.route.steps.getOrNull(n.p?.next ?: 0) ?: return
    Surface(color = MAPS_COLOR, contentColor = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.widthIn(max = 560.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val turn = when (step.sign) { 1 -> 45f; 2 -> 90f; 3 -> 135f; 7 -> 20f; -1 -> -45f; -2 -> -90f; -3 -> -135f; -7 -> -20f; 8, -8, -98 -> 180f; else -> 0f }
            Icon(painterResource(if (step.sign == 4) R.drawable.ic_place else R.drawable.ic_navigation), contentDescription = null,
                modifier = Modifier.size(40.dp).rotate(if (step.sign == 4) 0f else turn))
            Column(Modifier.weight(1f)) {
                Text(n.p?.let { metresText(it.toNext) } ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(step.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A place's name, icon, Home slot and how its Home tap goes; a new one (no id) gets its id here. */
@Composable private fun PlaceDialog(p: Place, all: List<Place>, lists: List<PlaceList>, dismiss: () -> Unit, onMap: ((Poi) -> Unit)? = null, save: (Place) -> Unit) {
    // a new one: mine, or on the map for every pill and search, with its kind's symbol (the user, 21 Oct)
    var toMap by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("disabled_parking") }
    var note by remember { mutableStateOf("") }
    if (toMap && onMap != null) {
        var mapName by remember { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = dismiss, title = { Text("Add to the map") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(false, { toMap = false }, label = { Text("My place") }); FilterChip(true, {}, label = { Text("On the map") })
                }
                OutlinedTextField(mapName, { mapName = it }, label = { Text("Name (if it has one)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Address or note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("What it is", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MapsSearch.PILLS.filter { it.key != "eopyy" }.forEach { k -> FilterChip(kind == k.key, { kind = k.key }, label = { Text("${k.emoji} ${k.label}") }) }
                }
                Text("It shows on the map, in Search and under its pill, on all your devices; Export shares it.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { onMap(Poi(kind, p.lat, p.lng, mapName.trim(), more = note.trim())) }) { Text("Add") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
        return
    }
    var inLists by remember { mutableStateOf(p.lists.toSet()) }
    var label by remember { mutableStateOf(p.label) }
    var name by remember { mutableStateOf(p.name) }
    var icon by remember { mutableStateOf(p.icon) }
    var slot by remember { mutableStateOf(p.slot) }
    var mode by remember { mutableStateOf(p.mode) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (p.id.isEmpty()) "Save place" else "Place") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (p.id.isEmpty() && onMap != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(true, {}, label = { Text("My place") }); FilterChip(false, { toMap = true }, label = { Text("On the map") })
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            var tab by remember { mutableStateOf(Place.category(p.icon)) }
            androidx.compose.material3.SecondaryScrollableTabRow(Place.CATEGORIES.keys.indexOf(tab), edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                Place.CATEGORIES.keys.forEach { c -> androidx.compose.material3.Tab(tab == c, { tab = c }, text = { Text(c) }) }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Place.CATEGORIES.getValue(tab).forEach { i -> FilterChip(icon == i, { icon = i }, label = { Icon(painterResource(placeIcon(i)), contentDescription = i, modifier = Modifier.size(18.dp)) }) }
            }
            Text("On Home's Maps card", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(slot == 0, { slot = 0 }, label = { Text("No") })
                (1..MapsFiles.SLOTS).forEach { n ->
                    val taken = all.firstOrNull { it.slot == n && it.id != p.id }
                    FilterChip(slot == n, { slot = n }, label = { Text(if (taken != null && slot != n) "$n·" else "$n") })
                }
            }
            all.firstOrNull { it.slot == slot && slot > 0 && it.id != p.id }?.let { Text("Takes slot $slot from ${it.short}.", style = MaterialTheme.typography.bodySmall) }
            if (slot > 0) OutlinedTextField(label, { label = it }, label = { Text("Name on Home (if shorter)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (lists.isNotEmpty()) {
                Text("In lists", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    lists.forEach { l -> FilterChip(l.id in inLists, { inLists = if (l.id in inLists) inLists - l.id else inLists + l.id }, label = { Text("${l.emoji} ${l.name}".trim()) }) }
                }
            }
            Text("Its Home tap goes", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val rolling = MapsPrefs.rolling(LocalContext.current)
                Place.MODES.forEach { m -> FilterChip(mode == m, { mode = m }, label = { Text(modeLabel(m, rolling)) },
                    leadingIcon = { Icon(painterResource(modeIcon(m, rolling)), contentDescription = null, modifier = Modifier.size(16.dp)) }) }
            }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            save(p.copy(id = p.id.ifEmpty { MapsFiles.newId() }, name = name.trim(), icon = icon, slot = slot, mode = mode, lists = lists.map { it.id }.filter { it in inLists }, label = label.trim()))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
