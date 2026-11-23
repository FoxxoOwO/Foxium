package com.chrogecko.browser.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverMergerTest {

    private fun item(title: String, url: String) =
        DiscoverItem(title, url, url.substringAfter("://").substringBefore('/'))

    @Test
    fun mergesFeedsAndRemovesDuplicateUrls() {
        val a = listOf(item("One", "https://a.example.com/1"))
        val b = listOf(item("One again", "https://a.example.com/1"), item("Two", "https://b.example.com/2"))
        assertEquals(2, DiscoverMerger.merge(listOf(a, b), emptySet(), 10).size)
    }

    @Test
    fun hiddenArticlesAreExcluded() {
        val feed = listOf(item("One", "https://a.example.com/1"))
        assertEquals(emptyList<DiscoverItem>(), DiscoverMerger.merge(listOf(feed), setOf("https://a.example.com/1"), 10))
    }

    @Test
    fun resultIsCapped() {
        val feed = (1..20).map { item("Item $it", "https://example$it.com/") }
        assertEquals(5, DiscoverMerger.merge(listOf(feed), emptySet(), 5).size)
    }

    @Test
    fun interleavesRssFeedsAndAllowsMoreItemsFromSingleFeed() {
        val feedA = (1..6).map { item("A$it", "https://blog-a.example.com/$it") }
        val feedB = (1..6).map { item("B$it", "https://blog-b.example.com/$it") }

        val single = DiscoverMerger.merge(listOf(feedA), emptySet(), limit = 5, maxPerHost = 5, interleave = true)
        assertEquals(5, single.size)
        assertEquals("https://blog-a.example.com/5", single.last().url)

        val interleaved = DiscoverMerger.merge(
            listOf(feedA, feedB),
            emptySet(),
            limit = 4,
            maxPerHost = 2,
            interleave = true,
        )
        assertEquals(
            listOf(
                "https://blog-a.example.com/1",
                "https://blog-b.example.com/1",
                "https://blog-a.example.com/2",
                "https://blog-b.example.com/2",
            ),
            interleaved.map { it.url },
        )
    }
}
