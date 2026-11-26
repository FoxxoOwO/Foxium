package com.chrogecko.browser.feature.favicons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconHrefsTest {

    @Test
    fun resolvesRelativeAndAbsoluteIconLinks() {
        val html = """
            <html><head>
            <link rel="icon" href="/static/favicon.png">
            <link rel="apple-touch-icon" href="https://cdn.example.com/touch.png">
            <link rel="stylesheet" href="/app.css">
            </head></html>
        """.trimIndent()

        assertEquals(
            listOf("https://example.com/static/favicon.png", "https://cdn.example.com/touch.png"),
            iconHrefs(html, "https://example.com/"),
        )
    }

    @Test
    fun keepsOrderDropsDuplicatesAndCapsAtFour() {
        val html = (1..6).joinToString("") { "<link rel='shortcut icon' href='/i$it.ico'>" } +
            "<link rel='icon' href='/i1.ico'>"

        val hrefs = iconHrefs(html, "https://example.com/")

        assertEquals(4, hrefs.size)
        assertEquals("https://example.com/i1.ico", hrefs[0])
    }

    @Test
    fun acceptsUppercaseSchemeInAbsoluteIcons() {
        val html = "<link rel='icon' href='HTTPS://cdn.example.com/icon.png'>"
        assertEquals(
            listOf("HTTPS://cdn.example.com/icon.png"),
            iconHrefs(html, "https://example.com/"),
        )
    }

    @Test
    fun ignoresPagesWithoutIconsAndBadBases() {
        assertTrue(iconHrefs("<html><body>hi</body></html>", "https://example.com/").isEmpty())
        assertTrue(iconHrefs("<link rel='icon' href='/a.png'>", "not a url").isEmpty())
    }

    @Test
    fun supportsUnquotedHrefAndRelAttributes() {
        val html = "<html><head><link rel=icon href=/favicon.png></head></html>"
        assertEquals(
            listOf("https://example.com/favicon.png"),
            iconHrefs(html, "https://example.com/"),
        )
    }
}
