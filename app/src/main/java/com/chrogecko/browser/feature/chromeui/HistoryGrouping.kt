package com.chrogecko.browser.feature.chromeui

import com.chrogecko.browser.core.database.entity.HistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun dayFormatter(locale: Locale): DateTimeFormatter =
    if (locale == Locale.US) {
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    } else {
        DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
    }

/** Splits history into Chrome-style day sections, keeping the incoming (newest first) order. */
fun groupHistoryByDay(
    entries: List<HistoryEntity>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
    todayLabel: String = "Today",
    yesterdayLabel: String = "Yesterday",
    locale: Locale = Locale.US,
): List<Pair<String, List<HistoryEntity>>> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val formatter = dayFormatter(locale)
    val sections = LinkedHashMap<String, MutableList<HistoryEntity>>()
    entries.forEach { entry ->
        val day = Instant.ofEpochMilli(entry.visitedAt).atZone(zone).toLocalDate()
        sections.getOrPut(dayLabel(day, today, todayLabel, yesterdayLabel, formatter)) { mutableListOf() } += entry
    }
    return sections.map { (label, rows) -> label to rows.toList() }
}

private fun dayLabel(
    day: LocalDate,
    today: LocalDate,
    todayLabel: String,
    yesterdayLabel: String,
    formatter: DateTimeFormatter,
): String = when (day) {
    today -> todayLabel
    today.minusDays(1) -> yesterdayLabel
    else -> formatter.format(day)
}
