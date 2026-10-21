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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** [rolling]: Settings › Maps › On foot is Rolling, a wheelchair (the user, 21 Oct: "Walking to be changed the icon to rolling"). */
internal fun modeIcon(mode: String, rolling: Boolean = false) = when (mode) {
    "foot" -> if (rolling) R.drawable.ic_accessible else R.drawable.ic_directions_walk
    "bike" -> R.drawable.ic_directions_bike
    else -> R.drawable.ic_directions_car
}

private fun modeLabel(mode: String, rolling: Boolean = false) = when (mode) { "foot" -> if (rolling) "Roll" else "Walk"; "bike" -> "Bike"; else -> "Car" }

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
    fun rolling(c: Context) = prefs(c).getBoolean("rolling", false)
    fun setRolling(c: Context, on: Boolean) = prefs(c).edit().putBoolean("rolling", on).apply()
    fun autoPark(c: Context) = prefs(c).getBoolean("auto_park", true)
    fun setAutoPark(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto_park", on).apply()
    fun openAtCar(c: Context) = prefs(c).getBoolean("open_at_car", false)
    fun setOpenAtCar(c: Context, on: Boolean) = prefs(c).edit().putBoolean("open_at_car", on).apply()
    fun lang(c: Context) = prefs(c).getString("lang", "el") ?: "el"
    fun setLang(c: Context, lang: String) = prefs(c).edit().putString("lang", lang).apply()
    fun ttsLocale(lang: String): Locale = Locale.forLanguageTag(if (lang == "en") "en-GB" else "el-GR")
    fun inMetres(lang: String, m: Long, then: String) = if (lang == "en") "In $m metres, $then" else "Σε $m μέτρα, $then"
    fun sample(lang: String) = if (lang == "en") "In 300 metres, turn right onto Panepistimiou" else "Σε 300 μέτρα, στρίψτε δεξιά στην Πανεπιστημίου"
}

private fun hasLocation(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Maps (Plan.md § Maps): Greece offline (Tetra/Maps/greece.pmtiles, Protomaps' dark style with Greek names), where you are,
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
    var showList by remember { mutableStateOf(false) }
    var listTab by remember { mutableStateOf("All") }
    var here by remember { mutableStateOf<Location?>(null) }
    var live by remember { mutableStateOf(false) } // a fix came while the map is open (not Android's stored last one)
    var granted by remember { mutableStateOf(hasLocation(context)) }
    var nav by remember { mutableStateOf<Nav?>(null) }
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
    fun startNav(target: Place, mode: String, from: Location? = here) {
        // directions start from a live fix: Android's stored last one can be hours old and towns away
        if (from == null || !live) { pending = target.copy(mode = mode); return }
        if (!File(graphDir, "properties").isFile) {
            Toast.makeText(context, "No routing graph yet: put greece-gh in Tetra/Maps (the computer's tools/maps-greece.sh makes it).", Toast.LENGTH_LONG).show(); return
        }
        if (mode == "car" && target.id != MapsFiles.PARKING) unpark("a car trip started")
        finding = "Finding the way…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { MapsRouting.route(graphDir, from.latitude, from.longitude, target.lat, target.lng, profile(mode), lang) } }
            finding = null
            r.onSuccess { route ->
                WriterLog.i("Maps: route by $mode, ${route.metres.toInt()} m, ${route.steps.size} steps")
                val rerouted = nav?.target?.id == target.id
                nav = Nav(target, mode, route, cumulative(route), System.currentTimeMillis())
                selected = null
                if (!rerouted) route.steps.firstOrNull()?.let { say(it.text) }
                follow(mode)
                fix(from) // the first distance at once, not at the next fix
            }.onFailure {
                WriterLog.e("Maps: no route by $mode", it)
                Toast.makeText(context, "No way found: ${it.message ?: "the routing graph could not be read"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Each fix: where on the route, what to say, and a new route when off it for three fixes in a row. */
    fun onFix(loc: Location) {
        here = loc; live = true
        // at car speed (40 km/h) for three fixes: driving, so the car is no longer where it was parked
        fast = if (loc.hasSpeed() && loc.speed > 11f) fast + 1 else 0
        if (fast == 3 && nav?.mode != "foot" && nav?.mode != "bike") unpark("driving")
        mapRef?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(loc)
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
        val far = if (car) 500.0 else 150.0; val near = if (car) 80.0 else 20.0
        if (p.toNext <= near && "n${p.next}" !in said) { say(step.text); said = said + "n${p.next}" + "f${p.next}" }
        else if (p.toNext <= far && p.toNext > near * 2 && "f${p.next}" !in said) {
            val round = if (car) 50 else 10
            say(MapsPrefs.inMetres(lang, Math.round(p.toNext / round) * round, step.text)); said = said + "f${p.next}"
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
        val json = context.assets.open("maps/dark.json").bufferedReader().use { it.readText() }
            .replace("pmtiles://MAP_FILE", "pmtiles://file://${mapFile.absolutePath}")
        mapView.getMapAsync { map ->
            map.setLatLngBoundsForCameraTarget(GREECE)
            map.setMinZoomPreference(5.0); map.setMaxZoomPreference(19.0)
            map.cameraPosition = (go ?: here?.let { Place("", "", it.latitude, it.longitude) })
                ?.let { CameraPosition.Builder().target(LatLng(it.lat, it.lng)).zoom(14.0).build() }
                ?: CameraPosition.Builder().target(LatLng(38.4, 23.8)).zoom(5.6).build()
            map.setStyle(Style.Builder().fromJson(json)) { style ->
                style.addSource(GeoJsonSource("route"))
                style.addLayer(LineLayer("route-casing", "route").withProperties(PropertyFactory.lineColor("#1A237E"), PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addLayer(LineLayer("route-line", "route").withProperties(PropertyFactory.lineColor("#7986CB"), PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
                style.addSource(GeoJsonSource("places"))
                style.addLayer(CircleLayer("places-dot", "places").withProperties(PropertyFactory.circleRadius(8f), PropertyFactory.circleColor("#5C6BC0"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f)))
                style.addLayer(SymbolLayer("places-name", "places").withProperties(PropertyFactory.textField("{name}"),
                    PropertyFactory.textFont(arrayOf("Noto Sans Medium")), PropertyFactory.textSize(13f), PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP), PropertyFactory.textColor("#FFFFFF"), PropertyFactory.textHaloColor("#1A237E"),
                    PropertyFactory.textHaloWidth(1.5f), PropertyFactory.textAllowOverlap(true)))
                map.addOnMapLongClickListener { at ->
                    if (nav == null) editing = Place("", "", at.latitude, at.longitude)
                    true
                }
                map.addOnMapClickListener { at ->
                    val hit = map.queryRenderedFeatures(map.projection.toScreenLocation(at), "places-dot").firstOrNull()?.getStringProperty("id")
                    selected = places.firstOrNull { it.id == hit }
                    showList = false
                    hit != null
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
    LaunchedEffect(mapRef, places) {
        (mapRef?.style?.getSource("places") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(places.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply { addStringProperty("id", p.id); addStringProperty("name", p.name) }
        }))
    }
    LaunchedEffect(mapRef, nav?.route) {
        val src = mapRef?.style?.getSource("route") as? GeoJsonSource ?: return@LaunchedEffect
        val r = nav?.route
        src.setGeoJson(if (r == null) FeatureCollection.fromFeatures(emptyList()) else
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(r.lats.indices.map { Point.fromLngLat(r.lngs[it], r.lats[it]) }))))
    }
    // the screen stays on while directions run
    DisposableEffect(nav != null) {
        mapView.keepScreenOn = nav != null
        onDispose { mapView.keepScreenOn = false }
    }
    BackHandler(nav != null || selected != null || showList) {
        when { showList -> showList = false; selected != null -> selected = null; else -> stopNav() }
    }

    Box(Modifier.fillMaxSize()) {
        if (mapFile.isFile) AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        else Column(Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = MAPS_COLOR, modifier = Modifier.size(48.dp))
            Text("The map of Greece is not on this device yet", style = MaterialTheme.typography.titleLarge)
            Text("It comes from the computer: tools/maps-greece.sh makes greece.pmtiles (about 500 MB) and the routing graph greece-gh, " +
                "and Local Sync brings them here into Tetra/Maps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // top: back, and (no directions running) the places list and where I am
        val n = nav
        if (n == null) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Island(R.drawable.ic_chevron_left, "Back", back)
            Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
                Text("Maps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Box(Modifier.weight(1f))
            Island(R.drawable.ic_parking, "I parked here") {
                val h = here
                if (h == null || !live) Toast.makeText(context, if (granted) "Waiting for your location…" else "Location is off for Tetra.", Toast.LENGTH_SHORT).show()
                else park(h, "the P button")
            }
            Island(R.drawable.ic_view_list, "Saved places") { showList = !showList; selected = null }
            Island(R.drawable.ic_my_location, "Where I am") {
                val h = here
                if (h == null) Toast.makeText(context, if (granted) "Waiting for your location…" else "Location is off for Tetra.", Toast.LENGTH_SHORT).show()
                else mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(h.latitude, h.longitude), 16.0))
            }
        } else NavBanner(n, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp))

        if (showList && n == null) Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 12.dp).widthIn(max = 340.dp).heightIn(max = 420.dp)) {
            if (places.isEmpty()) Text("No saved places yet. Long-press the map to save one.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            else Column {
                // tabs: All, then each category that has places
                val tabs = listOf("All") + Place.CATEGORIES.keys.filter { c -> places.any { Place.category(it.icon) == c } }
                if (listTab !in tabs) listTab = "All"
                if (tabs.size > 2) androidx.compose.material3.SecondaryScrollableTabRow(tabs.indexOf(listTab), edgePadding = 8.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = islandContentColor()) {
                    tabs.forEach { c -> androidx.compose.material3.Tab(listTab == c, { listTab = c }, text = { Text(c) }) }
                }
            LazyColumn(Modifier.padding(vertical = 8.dp)) {
                items(places.filter { listTab == "All" || Place.category(it.icon) == listTab }, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable {
                        showList = false; selected = p
                        mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 16.0))
                    }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(placeIcon(p.icon)), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.slot > 0) Text("Home ${p.slot}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            }
        }

        // bottom: the chosen place, directions running, or a hint
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            finding?.let { Pill(it) }
            if (pending != null && nav == null && finding == null) Pill(if (granted) "Waiting for your location…" else "Location is off for Tetra: directions need it.")
            val s = selected
            // the road I am on: the street of the leg being driven (the user: "will I be able to see on what road I am on?")
            n?.let { nn -> nn.route.steps.getOrNull(((nn.p?.next ?: 1) - 1).coerceAtLeast(0))?.street?.takeIf { it.isNotBlank() } }?.let { Pill(it) }
            if (n != null) Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Button(onClick = {
                        stopNav()
                        if (n.mode == "car" && MapsPrefs.autoPark(context)) here?.let { park(it, "a car trip stopped") }
                    }) { Text("Stop") }
                }
            } else if (s != null) Surface(color = islandColor(), contentColor = islandContentColor(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(painterResource(placeIcon(s.icon)), contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            here?.let { Text(metresText(metresBetween(it.latitude, it.longitude, s.lat, s.lng)) + " away", style = MaterialTheme.typography.bodySmall) }
                        }
                        IconButton(onClick = { editing = s }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
                        IconButton(onClick = { deleting = s }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Place.MODES.forEach { m ->
                            Button(onClick = { startNav(s, m) }, modifier = Modifier.weight(1f)) {
                                Icon(painterResource(modeIcon(m, rolling)), contentDescription = null, modifier = Modifier.size(18.dp)); Text(" " + modeLabel(m, rolling))
                            }
                        }
                    }
                }
            } else if (mapFile.isFile && places.isEmpty()) Pill("Long-press the map to save a place.")
        }
    }

    editing?.let { p -> PlaceDialog(p, places, { editing = null }) { saved ->
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

@Composable private fun Island(icon: Int, label: String, click: () -> Unit) = Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
    IconButton(onClick = click) { Icon(painterResource(icon), contentDescription = label) }
}

@Composable private fun Pill(text: String) = Surface(color = islandColor(), contentColor = islandContentColor(), shape = CircleShape) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

/** The next turn: its arrow, how far, and GraphHopper's words for it. */
@Composable private fun NavBanner(n: Nav, modifier: Modifier) {
    val step = n.route.steps.getOrNull(n.p?.next ?: 0) ?: return
    Surface(color = MAPS_COLOR, contentColor = androidx.compose.ui.graphics.Color.White, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val turn = when (step.sign) { 1 -> 45f; 2 -> 90f; 3 -> 135f; 7 -> 20f; -1 -> -45f; -2 -> -90f; -3 -> -135f; -7 -> -20f; 8, -8, -98 -> 180f; else -> 0f }
            Icon(painterResource(if (step.sign == 4) R.drawable.ic_place else R.drawable.ic_navigation), contentDescription = null,
                modifier = Modifier.size(40.dp).rotate(if (step.sign == 4) 0f else turn))
            Column(Modifier.weight(1f)) {
                Text(n.p?.let { metresText(it.toNext) } ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(step.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A place's name, icon, Home slot and how its Home tap goes; a new one (no id) gets its id here. */
@Composable private fun PlaceDialog(p: Place, all: List<Place>, dismiss: () -> Unit, save: (Place) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var icon by remember { mutableStateOf(p.icon) }
    var slot by remember { mutableStateOf(p.slot) }
    var mode by remember { mutableStateOf(p.mode) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (p.id.isEmpty()) "Save place" else "Place") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            var tab by remember { mutableStateOf(Place.category(p.icon)) }
            androidx.compose.material3.SecondaryScrollableTabRow(Place.CATEGORIES.keys.indexOf(tab), edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                Place.CATEGORIES.keys.forEach { c -> androidx.compose.material3.Tab(tab == c, { tab = c }, text = { Text(c) }) }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Place.CATEGORIES.getValue(tab).forEach { i -> FilterChip(icon == i, { icon = i }, label = { Icon(painterResource(placeIcon(i)), contentDescription = i, modifier = Modifier.size(18.dp)) }) }
            }
            Text("On Home's Maps card", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(slot == 0, { slot = 0 }, label = { Text("No") })
                (1..MapsFiles.SLOTS).forEach { n ->
                    val taken = all.firstOrNull { it.slot == n && it.id != p.id }
                    FilterChip(slot == n, { slot = n }, label = { Text(if (taken != null && slot != n) "$n·" else "$n") })
                }
            }
            all.firstOrNull { it.slot == slot && slot > 0 && it.id != p.id }?.let { Text("Takes slot $slot from ${it.name}.", style = MaterialTheme.typography.bodySmall) }
            Text("Its Home tap goes", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val rolling = MapsPrefs.rolling(LocalContext.current)
                Place.MODES.forEach { m -> FilterChip(mode == m, { mode = m }, label = { Text(modeLabel(m, rolling)) },
                    leadingIcon = { Icon(painterResource(modeIcon(m, rolling)), contentDescription = null, modifier = Modifier.size(16.dp)) }) }
            }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            save(p.copy(id = p.id.ifEmpty { MapsFiles.newId() }, name = name.trim(), icon = icon, slot = slot, mode = mode))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
