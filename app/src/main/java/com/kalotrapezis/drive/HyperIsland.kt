package com.kalotrapezis.drive

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Xiaomi HyperOS shows an ongoing notification in the Dynamic Island when the notification carries a
 * `miui.focus.param` extra: a JSON description of what to draw. That is the whole mechanism — no SDK, no
 * service, no new permission — so this writes the JSON rather than taking a dependency for it. (The
 * `hyperisland_kit` library does the same thing, but declares minSdk 35 and pulls in appcompat, material and
 * kotlinx-serialization, none of which this app has a use for.)
 *
 * Every device that is not a Xiaomi, and every Xiaomi that has not granted focus notifications, simply
 * ignores the extra and shows the ordinary notification, which is why nothing here is allowed to throw.
 */
internal object HyperIsland {
    private const val EXTRA = "miui.focus.param"

    /** One id per kind of island, so a backup and a read of the gallery are two islands, not one fighting itself. */
    const val SYNC = "tetra_sync"
    const val ANALYSIS = "tetra_analysis"
    private const val ACCENT = "#4C8DF6"
    private const val EXPANDED_MS = 2500 // how long the big card stays before it shrinks back to the pill

    fun isSupported(context: Context): Boolean = runCatching {
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) && systemFeature() && canShowFocus(context)
    }.getOrDefault(false)

    /**
     * Adds the island description to a notification that already works on its own. The island itself is only the
     * progress: Pause and Stop stay in the app and in the notification, where they are the platform's own
     * controls rather than MIUI-styled buttons that match nothing else in Tetra.
     */
    fun decorate(builder: NotificationCompat.Builder, title: String, text: String, done: Int, total: Int, announce: Boolean = false, business: String = SYNC) {
        runCatching { builder.addExtras(Bundle().apply { putString(EXTRA, param(title, text, done, total, announce, business)) }) }
    }

    private fun param(title: String, text: String, done: Int, total: Int, announce: Boolean, business: String): String {
        val paramV2 = JSONObject()
            .put("protocol", 3) // the version param_v2 is read as; anything else is parsed to nothing
            .put("business", business)
            .put("updatable", true) // one island that changes, not a new one per progress step
            .put("ticker", title)
            // Only the first post opens the big card; after that the island is a pill that quietly counts up,
            // otherwise every file sent would pop the card open again.
            .put("enableFloat", announce)
            .put("baseInfo", JSONObject().put("type", 1).put("title", title).put("content", text))
            // The island is only a few characters wide, so it gets the app icon and the count, not the sentence.
            .put("param_island", JSONObject()
                .put("islandProperty", 1)
                .put("islandPriority", 1)
                .put("expandedTime", EXPANDED_MS)
                .put("bigIslandArea", JSONObject()
                    .put("imageTextInfoLeft", JSONObject().put("type", 1).put("picInfo", JSONObject().put("type", 1)))
                    .put("imageTextInfoRight", JSONObject().put("type", 2)
                        .put("textInfo", JSONObject().put("title", if (total > 0) "$done/$total" else "…")))))
        if (total > 0) paramV2.put("progressInfo", JSONObject()
            .put("progress", (done.toLong() * 100 / total).coerceIn(0, 100))
            .put("colorProgress", ACCENT))
        return JSONObject().put("param_v2", paramV2).put("title", title).put("content", text).toString()
    }

    /** HyperOS only draws an island where the device has one. */
    private fun systemFeature(): Boolean = Class.forName("android.os.SystemProperties")
        .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        .invoke(null, "persist.sys.feature.island", false) as Boolean

    /** The user can turn focus notifications off per app; MIUI answers for this package. */
    private fun canShowFocus(context: Context): Boolean = context.contentResolver.call(
        Uri.parse("content://miui.statusbar.notification.public"),
        "canShowFocus",
        null,
        Bundle().apply { putString("package", context.packageName) },
    )?.getBoolean("canShowFocus", false) == true
}
