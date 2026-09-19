package com.kalotrapezis.drive

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

internal enum class TimelineScale(val columns: Int, val label: String) {
    Week(2, "Week"),
    Month(3, "Month"),
    Year(6, "Year"),
    ;

    fun finer() = entries.getOrElse(ordinal - 1) { this }
    fun broader() = entries.getOrElse(ordinal + 1) { this }
}

internal object TimelineRules {
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
