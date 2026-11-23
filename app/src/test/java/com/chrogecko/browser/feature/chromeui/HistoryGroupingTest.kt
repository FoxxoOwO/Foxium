package com.chrogecko.browser.feature.chromeui

import com.chrogecko.browser.core.database.entity.HistoryEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupingTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2025-03-10T12:00:00Z").toEpochMilli()

    private fun entry(url: String, minutesAgo: Long) = HistoryEntity(
        url = url,
        title = url,
        visitedAt = now - minutesAgo * 60_000L,
    )

    private fun labels(entries: List<HistoryEntity>) =
        groupHistoryByDay(entries, now = now, zone = zone).map { it.first }

    @Test
    fun `same day entries share one section`() {
        val sections = groupHistoryByDay(
            listOf(entry("a", 5), entry("b", 300)),
            now = now,
            zone = zone,
        )
        assertEquals(listOf("Today"), sections.map { it.first })
        assertEquals(listOf("a", "b"), sections.single().second.map { it.url })
    }

    @Test
    fun `previous day becomes yesterday`() {
        assertEquals(listOf("Yesterday"), labels(listOf(entry("a", 60 * 24))))
    }

    @Test
    fun `older days are dated`() {
        assertEquals(listOf("Mar 8, 2025"), labels(listOf(entry("a", 60 * 48))))
    }

    @Test
    fun `sections keep the incoming order`() {
        val sections = groupHistoryByDay(
            listOf(entry("new", 5), entry("older", 60 * 24), entry("oldest", 60 * 48)),
            now = now,
            zone = zone,
        )
        assertEquals(listOf("Today", "Yesterday", "Mar 8, 2025"), sections.map { it.first })
    }
}
