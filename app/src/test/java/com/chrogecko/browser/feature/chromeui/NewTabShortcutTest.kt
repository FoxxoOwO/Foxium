package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Test

class NewTabShortcutTest {

    @Test
    fun `bookmarks come first and duplicate hosts collapse`() {
        val shortcuts = buildNewTabShortcuts(
            bookmarks = listOf("https://www.example.com/a" to "Example"),
            history = listOf(
                "https://example.com/b" to "Example Domain",
                "https://news.example.org/story" to "News",
            ),
        )

        assertEquals(
            listOf(
                NewTabShortcut("https://www.example.com/a", "Example", "example.com"),
                NewTabShortcut("https://news.example.org/story", "News", "news.example.org"),
            ),
            shortcuts,
        )
    }

    @Test
    fun `hostless urls are skipped and blank titles fall back to the host`() {
        val shortcuts = buildNewTabShortcuts(
            bookmarks = listOf("about:blank" to "Blank", "file:///sdcard/a.html" to "File"),
            history = listOf("https://docs.example.com/" to ""),
        )

        assertEquals(listOf(NewTabShortcut("https://docs.example.com/", "docs.example.com", "docs.example.com")), shortcuts)
    }

    @Test
    fun `limit caps the tile count`() {
        val history = (1..20).map { "https://site$it.com/" to "Site $it" }
        assertEquals(8, buildNewTabShortcuts(emptyList(), history).size)
    }
}
