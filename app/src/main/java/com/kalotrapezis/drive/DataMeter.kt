package com.kalotrapezis.drive

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

/** The mobile signal now: the reference signal's power (RSRP, dBm), Android's 0–4 bars, the generation, and the band when known. */
internal data class MobileReading(val dbm: Int, val level: Int, val gen: String, val detail: String, val band: String?, val operator: String)

/** One speed and loss test to the computer, with how each end was connected (to compare Ethernet, Wi-Fi, the hotspot). */
internal data class SpeedTest(val at: String, val here: String, val there: String, val down: Double, val up: Double, val ms: Long, val loss: Int?)

/** A piece of advice from the Analyzer: information, never a change made for the user (the user, 25 Oct: "giving info, not … auto fix"). */
internal data class Advice(val title: String, val text: String)

/**
 * Data › Signal › Meter and Analyzer (Plan.md § Data): what the phone, the tablet and the computer are connected by, and how
 * well. Android gives the mobile signal's strength without a permission; the band (and the cell) needs the location right.
 */
internal object DataMeter {
    fun mobile(c: Context): MobileReading? {
        val tm = c.getSystemService(TelephonyManager::class.java) ?: return null
        val ss = runCatching { tm.signalStrength }.getOrNull() ?: return null
        val nr = ss.getCellSignalStrengths(CellSignalStrengthNr::class.java).firstOrNull { it.ssRsrp != CellInfo.UNAVAILABLE }
        val lte = ss.getCellSignalStrengths(CellSignalStrengthLte::class.java).firstOrNull { it.rsrp != CellInfo.UNAVAILABLE }
        val gen = when { nr != null && lte != null -> "5G (with 4G)"; nr != null -> "5G"; lte != null -> "4G"; else -> "" }
        val dbm = nr?.ssRsrp ?: lte?.rsrp ?: ss.cellSignalStrengths.firstOrNull()?.dbm ?: return null
        val detail = when {
            nr != null -> listOfNotNull(nr.ssRsrq.takeIf { it != CellInfo.UNAVAILABLE }?.let { "RSRQ $it" }, nr.ssSinr.takeIf { it != CellInfo.UNAVAILABLE }?.let { "SINR $it dB" }).joinToString(" · ")
            lte != null -> listOfNotNull(lte.rsrq.takeIf { it != CellInfo.UNAVAILABLE }?.let { "RSRQ $it" }, lte.rssnr.takeIf { it != CellInfo.UNAVAILABLE }?.let { "SINR $it dB" }).joinToString(" · ")
            else -> ""
        }
        // the band: from the registered cell, which Android gives only with the location right
        val band = if (!DataSignal.located(c)) null else runCatching {
            tm.allCellInfo.orEmpty().filter { it.isRegistered }.firstNotNullOfOrNull { info ->
                when (info) {
                    is CellInfoNr -> (info.cellIdentity as? CellIdentityNr)?.bands?.firstOrNull()?.let { "n$it" }
                    is CellInfoLte -> (info.cellIdentity as CellIdentityLte).bands.firstOrNull()?.let { "B$it" }
                    else -> null
                }
            }
        }.getOrNull()
        return MobileReading(dbm, ss.level, gen, detail, band, tm.networkOperatorName.orEmpty())
    }

    /** How this device is connected now: Ethernet, a phone's hotspot (a metered Wi-Fi), Wi-Fi, mobile, or nothing. */
    fun link(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "Hotspot"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            else -> "Other"
        }
    }

    /** The computer's own connection as its /data/wifi says it: "Ethernet 1000 Mb/s" or "Wi-Fi −58 dBm". */
    fun computerLink(o: JSONObject?): String = when {
        o == null -> "?"
        o.optJSONObject("ethernet") != null -> "Ethernet" + (o.optJSONObject("ethernet")!!.optInt("speed").takeIf { it > 0 }?.let { " $it Mb/s" } ?: "")
        // right beside the router a driver may say a positive dBm: then just Wi-Fi
        o.has("rssi") -> "Wi-Fi" + (o.optInt("rssi").takeIf { it < 0 }?.let { " $it dBm" } ?: "")
        else -> "?"
    }

    /** "20 packets transmitted, 19 received, 5% packet loss … rtt min/avg/max/mdev = 1.2/3.4/9.9/1.1 ms" → (5, 3.4). */
    fun parsePing(out: String): Pair<Int, Double>? {
        val loss = Regex("""([\d.]+)% packet loss""").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val avg = Regex("""= [\d.]+/([\d.]+)/""").find(out)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return Math.round(loss).toInt() to avg
    }

    /** Lost packets to [host]: Android's own ping, 20 of them over 4 s (an app may ping, it may not send raw packets itself). */
    fun ping(host: String): Pair<Int, Double>? = runCatching {
        val p = ProcessBuilder("ping", "-c", "20", "-i", "0.2", "-W", "1", host).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText(); p.waitFor()
        parsePing(out)
    }.getOrNull()

    // ---- the tests, kept on this device (the last ten) ----

    private fun prefs(c: Context) = c.getSharedPreferences("data", Context.MODE_PRIVATE)

    fun tests(c: Context): List<SpeedTest> = runCatching {
        val a = JSONArray(prefs(c).getString("speedTests", "[]"))
        List(a.length()) { a.getJSONObject(it) }.map { o ->
            SpeedTest(o.getString("at"), o.optString("here"), o.optString("there"), o.optDouble("down"), o.optDouble("up"), o.optLong("ms"), if (o.has("loss")) o.getInt("loss") else null)
        }
    }.getOrDefault(emptyList())

    fun addTest(c: Context, t: SpeedTest) {
        val all = (listOf(t) + tests(c)).take(10)
        prefs(c).edit().putString("speedTests", JSONArray(all.map { s ->
            JSONObject().put("at", s.at).put("here", s.here).put("there", s.there).put("down", s.down).put("up", s.up).put("ms", s.ms).apply { s.loss?.let { put("loss", it) } }
        }).toString()).apply()
    }

    // ---- the Analyzer's advice ----

    /**
     * Short advice from what is heard around and the signal here. [mine] is this device's Wi-Fi now, [hotspot] whether it is
     * a phone's hotspot, [heard] each network as (name, MHz, dBm). Only information: Tetra changes nothing.
     */
    fun advice(mine: WifiReading?, hotspot: Boolean, heard: List<Triple<String, Int, Int>>): List<Advice> {
        val out = mutableListOf<Advice>()
        val others = heard.filter { mine?.ssid == null || it.first != mine.ssid }
        if (mine != null) {
            val ch = DataSignal.channel(mine.mhz)
            val nets = others.map { DataSignal.channel(it.second) to it.third }
            val best = DataSignal.crowding(nets).firstOrNull { it.first in 1..14 }
            val here = DataSignal.crowdingAt(nets, ch)
            if (ch in 1..14 && best != null && best.first != ch && here - best.second >= 1.0) {
                val near = others.count { DataSignal.channel(it.second) in 1..14 && kotlin.math.abs(DataSignal.channel(it.second) - ch) < 5 }
                out += Advice("Channel ${best.first} is quieter", "Your network is on channel $ch, with $near ${if (near == 1) "network" else "networks"} on it or next to it. " +
                    (if (hotspot) "A phone's hotspot picks its own channel; turning it off and on may pick another." else "Channel ${best.first} is the least crowded: it is changed in the router's settings."))
            }
            if (ch in 1..14) out += Advice("2.4 GHz", "It reaches farther and through more walls; 5 GHz is much faster in the same room or the next. " +
                (if (hotspot) "In the phone's hotspot settings, 5 GHz can be chosen if every device has it." else "Most routers offer both: use 5 GHz where it reaches."))
            when {
                mine.rssi < -75 -> out += Advice("Weak here (${mine.rssi} dBm)", "Walls, floors and large furniture between you and the " + (if (hotspot) "phone" else "router") +
                    " weaken it, concrete and metal most. " + (if (hotspot) "Keep the phone high and uncovered — not in a pocket, a bag or under a pillow — and nearer." else
                    "Raise the router, move it toward the middle of the home, out of cabinets and away from the TV, mirrors, metal, aquariums and the microwave."))
                mine.rssi < -67 -> out += Advice("Fair here (${mine.rssi} dBm)", "Enough for the web; video calls may stutter. A room nearer, or the " + (if (hotspot) "phone" else "router") + " higher, helps.")
                else -> out += Advice("Good here (${mine.rssi} dBm)", "Strong enough for anything at this spot.")
            }
        }
        val crowded24 = others.count { DataSignal.channel(it.second) in 1..14 && it.third > -80 }
        if (crowded24 >= 10) out += Advice("Busy air", "$crowded24 networks around on 2.4 GHz. 5 GHz has many more channels and is usually calmer.")
        return out
    }
}
