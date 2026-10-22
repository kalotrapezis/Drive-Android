package com.kalotrapezis.drive

import android.Manifest
import android.app.Presentation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.AppManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.TimeZone
import androidx.car.app.navigation.model.Step as CarStep

/**
 * Tetra Maps on the car's screen (the user, 22 Oct: "it does not work with Android Auto its not on the list of the available
 * Apps can we add it?"): the offline map with where you are, your places and what is near (fuel, ΑμεΑ parking…), and
 * directions shown and spoken — the phone's own routing (MapsRouting) and guidance (progress, dueWarning), by car.
 * Android Auto lists a navigation app not installed from Play only with its Developer settings › Unknown sources on.
 */
class MapsCarService : CarAppService() {
    // the hosts Google signs (Android Auto, Android Automotive), the library's own list
    override fun createHostValidator() = HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
    override fun onCreateSession() = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = MapsCarScreen(carContext)
    }
}

/** The map, the next turn over it while directions run, and Places / Nearby / Stop. */
private class MapsCarScreen(car: CarContext) : Screen(car), DefaultLifecycleObserver {
    private val root = MapsFiles.root(TetraFolder.root(Environment.getExternalStorageDirectory()))
    private val graphDir = MapsFiles.graph(root)
    private val lang = MapsPrefs.lang(car)
    private val navigation = car.getCarService(NavigationManager::class.java)
    private var here: Location? = null
    private var nav: Nav? = null
    private var finding: String? = null
    private var map: MapLibreMap? = null

    init {
        lifecycle.addObserver(this)
        navigation.setNavigationManagerCallback(object : NavigationManagerCallback { override fun onStopNavigation() = stop() })
        car.getCarService(AppManager::class.java).setSurfaceCallback(MapSurface())
    }

    private fun granted() = ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onGetTemplate(): Template {
        val n = nav; val p = n?.p
        val actions = ActionStrip.Builder()
        if (n == null) {
            actions.addAction(action("Places") { screenManager.push(PlacesScreen()) })
            actions.addAction(action("Nearby") { screenManager.push(KindsScreen()) })
        } else actions.addAction(action("Stop") { stop() })
        val t = NavigationTemplate.Builder().setActionStrip(actions.build())
        val message = when {
            !granted() -> "Open Tetra Maps on the phone once to allow location"
            !MapsFiles.map(root).isFile -> "No map yet: greece.pmtiles comes from the computer into Tetra/Maps"
            else -> finding
        }
        if (message != null) t.setNavigationInfo(MessageInfo.Builder(message).build())
        else if (n != null && p != null) {
            val step = n.route.steps[p.next]
            val type = maneuver(step.sign)
            t.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(CarStep.Builder(step.text)
                .setManeuver(Maneuver.Builder(type).setIcon(turnIcon(step.sign)).build())
                .apply { if (step.street.isNotEmpty()) setRoad(step.street) }.build(), distance(p.toNext)).build())
            val seconds = n.route.millis / 1000 * (p.left / n.route.metres.coerceAtLeast(1.0))
            t.setDestinationTravelEstimate(TravelEstimate.Builder(distance(p.left),
                DateTimeWithZone.create(System.currentTimeMillis() + (seconds * 1000).toLong(), TimeZone.getDefault()))
                .setRemainingTimeSeconds(seconds.toLong()).build())
        }
        return t.build()
    }

    private fun action(title: String, click: () -> Unit) = Action.Builder().setTitle(title).setOnClickListener(click).build()

    // —— the map, drawn on the car's surface: a MapLibre MapView on a virtual display of that surface ——

    private inner class MapSurface : SurfaceCallback {
        private var display: VirtualDisplay? = null
        private var presentation: Presentation? = null
        private var view: MapView? = null
        private var size = 0 to 0

        override fun onSurfaceAvailable(c: SurfaceContainer) {
            val surface = c.surface ?: return
            if (!MapsFiles.map(root).isFile) return
            val d = carContext.getSystemService(DisplayManager::class.java)
                .createVirtualDisplay("Tetra Maps", c.width, c.height, c.dpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            display = d; size = c.width to c.height
            presentation = Presentation(carContext, d.display).apply {
                MapLibre.getInstance(context)
                val v = MapView(context).also { view = it }
                v.onCreate(null)
                setContentView(v)
                show()
                v.onStart(); v.onResume()
                v.getMapAsync { m -> m.setStyle(Style.Builder().fromJson(mapStyle(carContext, MapsFiles.map(root)))) { style -> ready(m, style) } }
            }
        }

        override fun onVisibleAreaChanged(r: Rect) {
            // the arrow in the middle of what the cards leave free
            val (w, h) = size
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }
                ?.paddingWhileTracking(doubleArrayOf(r.left.toDouble(), r.top.toDouble(), (w - r.right).toDouble(), (h - r.bottom).toDouble()))
        }

        override fun onSurfaceDestroyed(c: SurfaceContainer) {
            map = null
            view?.run { onPause(); onStop(); onDestroy() }; view = null
            presentation?.dismiss(); presentation = null
            display?.release(); display = null
        }
    }

    private fun ready(m: MapLibreMap, style: Style) {
        m.uiSettings.isCompassEnabled = false; m.uiSettings.isLogoEnabled = false; m.uiSettings.isAttributionEnabled = false
        addRouteLayer(style)
        map = m
        if (granted()) {
            activateArrow(carContext, m, style)
            m.locationComponent.isLocationComponentEnabled = true
            here?.let { m.locationComponent.forceLocationUpdate(it) }
            follow()
        }
        drawRoute()
    }

    /** The camera on me, turned the way I go: closer and tilted while directions run. */
    private fun follow() {
        val lc = map?.locationComponent?.takeIf { it.isLocationComponentActivated } ?: return
        lc.renderMode = RenderMode.GPS
        lc.cameraMode = CameraMode.TRACKING_GPS
        lc.zoomWhileTracking(if (nav != null) 16.5 else 15.0); lc.tiltWhileTracking(if (nav != null) 45.0 else 0.0)
    }

    private fun drawRoute() { (map?.style?.getSource("route") as? GeoJsonSource)?.setGeoJson(routeFeatures(nav?.route)) }

    // —— where I am ——

    private val listener = LocationListener { loc ->
        // a network fix only until GPS has one, and never over a fresher GPS one
        val h = here
        if (loc.provider == LocationManager.NETWORK_PROVIDER && h != null && h.provider == LocationManager.GPS_PROVIDER && loc.time - h.time < 10_000) return@LocationListener
        onFix(loc)
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (!granted()) return
        val lm = carContext.getSystemService(LocationManager::class.java) ?: return
        runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }.forEach { pr ->
                lm.getLastKnownLocation(pr)?.let { if (here == null || it.time > here!!.time) here = it }
                lm.requestLocationUpdates(pr, 1000L, 0f, listener, carContext.mainLooper)
            }
        }.onFailure { WriterLog.e("Maps car: location updates", it) }
    }

    /** The car disconnected: it is parked where the last fix was (Settings › Maps › Save where I park). */
    override fun onDestroy(owner: LifecycleOwner) {
        carContext.getSystemService(LocationManager::class.java)?.removeUpdates(listener)
        if (nav != null) navigation.navigationEnded()
        here?.takeIf { MapsPrefs.autoPark(carContext) && System.currentTimeMillis() - it.time < 5 * 60_000 }?.let { park(it, "Android Auto disconnected") }
        tts?.shutdown()
    }

    private fun park(loc: Location, why: String) {
        WriterLog.i("Maps car: parked ($why)")
        runCatching { MapsFiles.save(root, Place(MapsFiles.PARKING, "My car", loc.latitude, loc.longitude, "parking", mode = "foot")) }
    }

    private fun onFix(loc: Location) {
        here = loc
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(loc)
        val n = nav ?: return
        val (p, i) = progress(n.route, n.cum, loc.latitude, loc.longitude, n.index)
        if (p.off > 50.0) {
            nav = n.copy(offFixes = n.offFixes + 1)
            if (n.offFixes + 1 >= 3 && System.currentTimeMillis() - n.since > 10_000 && finding == null) {
                WriterLog.i("Maps car: off the route by ${p.off.toInt()} m, a new one"); go(n.target)
            }
            return
        }
        if (p.left < 50.0 || metresBetween(loc.latitude, loc.longitude, n.target.lat, n.target.lng) < 60.0) {
            if ("n${n.route.steps.lastIndex}" !in n.said) say(n.route.steps.lastOrNull()?.text ?: "")
            stop()
            if (MapsPrefs.autoPark(carContext)) park(loc, "a car trip ended")
            CarToast.makeText(carContext, "Arrived: ${n.target.name}", CarToast.LENGTH_LONG).show(); return
        }
        var said = n.said
        val step = n.route.steps[p.next]
        when (dueWarning(p.next, p.toNext, if (loc.hasSpeed()) loc.speed.toDouble() else 0.0, true, said)) {
            "n" -> { say(step.text); said = said + "n${p.next}" + "f${p.next}" + "e${p.next}" }
            "f" -> { say(MapsPrefs.inDistance(lang, p.toNext, true, step.text)); said = said + "f${p.next}" + "e${p.next}" }
            "e" -> { say(MapsPrefs.inDistance(lang, p.toNext, true, step.text)); said = said + "e${p.next}" }
        }
        nav = n.copy(index = i, p = p, offFixes = 0, said = said)
        invalidate()
    }

    // —— directions ——

    /** By car to [target], the fastest way, from the last fix. */
    fun go(target: Place) {
        val from = here ?: run { CarToast.makeText(carContext, "Waiting for the GPS", CarToast.LENGTH_SHORT).show(); return }
        if (!File(graphDir, "properties").isFile) { CarToast.makeText(carContext, "No routing graph yet in Tetra/Maps", CarToast.LENGTH_LONG).show(); return }
        finding = "Finding the way…"; invalidate()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { MapsRouting.route(graphDir, from.latitude, from.longitude, target.lat, target.lng, "car", lang) } }
            finding = null
            r.onSuccess { route ->
                val rerouted = nav?.target == target
                WriterLog.i("Maps car: route, ${route.metres.toInt()} m, ${route.steps.size} steps")
                nav = Nav(target, "car", route, cumulative(route), System.currentTimeMillis())
                if (!rerouted) {
                    navigation.navigationStarted()
                    route.steps.firstOrNull()?.let { say(it.text) }
                    // a car trip: the car is no longer where it was parked
                    if (target.id != MapsFiles.PARKING) MapsFiles.places(root).firstOrNull { it.id == MapsFiles.PARKING }?.let { MapsFiles.delete(root, it) }
                }
                drawRoute(); follow(); onFix(here ?: from)
            }.onFailure {
                WriterLog.e("Maps car: no route", it)
                CarToast.makeText(carContext, it.message ?: "No way found", CarToast.LENGTH_LONG).show()
            }
            invalidate()
        }
    }

    fun stop() {
        if (nav == null) return
        nav = null
        navigation.navigationEnded()
        drawRoute(); follow(); invalidate()
    }

    // —— the voice: over the music, which is lowered meanwhile ——

    private val speech = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(speech).build()
    private val audio = car.getSystemService(AudioManager::class.java)
    private var tts: TextToSpeech? = null
    init {
        var t: TextToSpeech? = null
        t = TextToSpeech(car.applicationContext) { ok ->
            if (ok != TextToSpeech.SUCCESS) return@TextToSpeech
            t?.language = MapsPrefs.ttsLocale(lang); t?.setAudioAttributes(speech)
            t?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { audio.abandonAudioFocusRequest(focus) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
            })
            tts = t
        }
    }

    private fun say(text: String) {
        if (text.isEmpty()) return
        WriterLog.i("Maps car: say \"$text\"")
        audio.requestAudioFocus(focus)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "car")
    }

    // —— what the turn looks like ——

    private fun maneuver(sign: Int) = when (sign) {
        0 -> Maneuver.TYPE_STRAIGHT
        1 -> Maneuver.TYPE_TURN_SLIGHT_RIGHT; -1 -> Maneuver.TYPE_TURN_SLIGHT_LEFT
        2 -> Maneuver.TYPE_TURN_NORMAL_RIGHT; -2 -> Maneuver.TYPE_TURN_NORMAL_LEFT
        3 -> Maneuver.TYPE_TURN_SHARP_RIGHT; -3 -> Maneuver.TYPE_TURN_SHARP_LEFT
        7 -> Maneuver.TYPE_KEEP_RIGHT; -7 -> Maneuver.TYPE_KEEP_LEFT
        8 -> Maneuver.TYPE_U_TURN_RIGHT; -8, -98 -> Maneuver.TYPE_U_TURN_LEFT
        6 -> Maneuver.TYPE_ROUNDABOUT_ENTER_CCW; -6 -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW // driving on the right: round anticlockwise
        4, 5 -> Maneuver.TYPE_DESTINATION
        else -> Maneuver.TYPE_UNKNOWN
    }

    /** The phone's arrow (NavBanner), turned the same way, white. */
    private fun turnIcon(sign: Int): CarIcon {
        val d = ContextCompat.getDrawable(carContext, if (sign == 4) R.drawable.ic_place else R.drawable.ic_navigation)!!.mutate()
        d.setTint(Color.WHITE)
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { if (sign != 4) rotate(turnDegrees(sign), 48f, 48f); d.setBounds(0, 0, 96, 96); d.draw(this) }
        return CarIcon.Builder(IconCompat.createWithBitmap(b)).build()
    }

    private fun distance(m: Double): Distance =
        if (m >= 950) Distance.create(m / 1000, if (m < 9950) Distance.UNIT_KILOMETERS_P1 else Distance.UNIT_KILOMETERS)
        else Distance.create((Math.round(m / 10.0) * 10).toDouble(), Distance.UNIT_METERS)

    private fun away(lat: Double, lng: Double) = here?.let { metresText(metresBetween(it.latitude, it.longitude, lat, lng)) } ?: ""

    private fun limit() = carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

    /** A place picked: back to the map, and on our way. */
    private fun pick(p: Place) { screenManager.popToRoot(); go(p) }

    // —— Places: Home's places first, then the nearest of the rest ——

    private inner class PlacesScreen : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val all = MapsFiles.places(root)
            val home = MapsFiles.slots(all).filterNotNull()
            val h = here
            val rest = (all - home.toSet()).sortedBy { if (h == null) 0.0 else metresBetween(h.latitude, h.longitude, it.lat, it.lng) }
            val items = ItemList.Builder().setNoItemsMessage("No saved places yet")
            (home + rest).take(limit()).forEach { p ->
                items.addItem(Row.Builder().setTitle(p.name).apply { away(p.lat, p.lng).takeIf { it.isNotEmpty() }?.let(::addText) }.setOnClickListener { pick(p) }.build())
            }
            return ListTemplate.Builder().setTitle("Places").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
        }
    }

    // —— Nearby: what a driver looks for, then the nearest of it ——

    private inner class KindsScreen : Screen(carContext) {
        override fun onGetTemplate(): Template {
            val items = ItemList.Builder()
            listOf("disabled_parking", "fuel", "charging", "parking", "pharmacy", "eopyy", "supermarket", "toilets", "hospital").take(limit()).forEach { k ->
                val kind = MapsSearch.PILLS.first { it.key == k }
                items.addItem(Row.Builder().setTitle("${kind.emoji} ${kind.label}").setBrowsable(true).setOnClickListener { screenManager.push(FoundScreen(kind)) }.build())
            }
            return ListTemplate.Builder().setTitle("Nearby").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
        }
    }

    private inner class FoundScreen(private val kind: MapsSearch.Kind) : Screen(carContext) {
        private var found: List<Poi>? = null
        init {
            lifecycleScope.launch {
                val h = here
                found = if (h == null) emptyList() else withContext(Dispatchers.IO) {
                    runCatching { MapsSearch.search(MapsSearch.load(root), "", h.latitude, h.longitude, kind.key, limit = limit()) }.getOrDefault(emptyList())
                }
                invalidate()
            }
        }

        override fun onGetTemplate(): Template {
            val t = ListTemplate.Builder().setTitle(kind.label).setHeaderAction(Action.BACK)
            val f = found ?: return t.setLoading(true).build()
            val items = ItemList.Builder().setNoItemsMessage(if (here == null) "Waiting for the GPS" else "None near here")
            f.forEach { poi ->
                items.addItem(Row.Builder().setTitle(poi.title).apply { listOf(away(poi.lat, poi.lng), poi.more).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let(::addText) }
                    .setOnClickListener { pick(Place("", poi.title, poi.lat, poi.lng)) }.build())
            }
            return t.setSingleList(items.build()).build()
        }
    }
}
