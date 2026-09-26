package com.kalotrapezis.drive

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * `columns` is what a phone shows, and `targetDp` is roughly how wide one thumbnail wants to be there — the
 * two agree at a phone's ~400dp. See [TimelineRules.columns].
 */
internal enum class TimelineScale(val columns: Int, val targetDp: Int, val label: String) {
    Week(2, 190, "Week"),
    Month(3, 128, "Month"),
    Year(6, 64, "Year"),
    ;

    fun finer() = entries.getOrElse(ordinal - 1) { this }
    fun broader() = entries.getOrElse(ordinal + 1) { this }
}

internal object TimelineRules {
    /**
     * How many photos across. A tablet is not a big phone: the same two columns that make a Week comfortable
     * on a phone made every photo 582dp wide on the tablet (24 September), which is how it looked — huge and
     * wrong. So the scale sets how big a thumbnail should *feel* and the screen decides how many fit, never
     * fewer than the phone's count, so a narrow phone is left exactly as it was.
     */
    fun columns(scale: TimelineScale, widthDp: Int): Int = columns(scale.columns, scale.targetDp, widthDp)

    /** The same rule for a grid that has no scale: keep the phone's cell size, fit as many as the screen allows. */
    fun columns(phoneColumns: Int, targetDp: Int, widthDp: Int): Int = maxOf(phoneColumns, widthDp / targetDp)

    fun groupKey(timestampMillis: Long, scale: TimelineScale, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (timestampMillis <= 0) return "unknown"
        val date = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
        return when (scale) {
            TimelineScale.Week -> {
                val fields = WeekFields.ISO
                String.format(Locale.ROOT, "%04d-W%02d", date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()))
            }
            TimelineScale.Month -> YearMonth.from(date).toString()
            TimelineScale.Year -> date.year.toString()
        }
    }

    fun groupLabel(key: String, scale: TimelineScale, locale: Locale = Locale.getDefault()): String = when {
        key == "unknown" -> "Unknown date"
        scale == TimelineScale.Week -> "Week ${key.substringAfter("-W")} · ${key.substringBefore("-W")}"
        scale == TimelineScale.Month -> YearMonth.parse(key).atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        else -> key
    }

    /** Each group starts with one date label, except when the first label is intentionally hidden. */
    fun itemGroupKeys(groups: List<Pair<String, Int>>, firstGroupHasHeader: Boolean = true): List<String> = buildList {
        groups.forEachIndexed { index, (key, photoCount) ->
            if (firstGroupHasHeader || index > 0) add(key)
            repeat(photoCount) { add(key) }
        }
    }

    fun visibleGroupKey(itemGroupKeys: List<String>, firstVisibleItemIndex: Int): String =
        itemGroupKeys.getOrElse(firstVisibleItemIndex) { itemGroupKeys.lastOrNull() ?: "unknown" }

    fun yearForGroup(key: String): String = when {
        key == "unknown" -> "Unknown date"
        key.contains("-W") -> key.substringBefore("-W")
        key.length >= 4 -> key.take(4)
        else -> key
    }
}
