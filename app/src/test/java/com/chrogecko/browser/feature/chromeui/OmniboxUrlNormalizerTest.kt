package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Test

class OmniboxUrlNormalizerTest {

    private val search = "https://duckduckgo.com/?q=%s"

    @Test
    fun blankInputStaysBlank() {
        assertEquals("about:blank", normalizeOmniboxInput("   "))
    }

    @Test
    fun keepsExplicitSchemes() {
        assertEquals("https://example.com/a", normalizeOmniboxInput("https://example.com/a"))
        assertEquals("http://example.com/", normalizeOmniboxInput("http://example.com/"))
        assertEquals("about:config", normalizeOmniboxInput("about:config"))
    }

    @Test
    fun fileUrlIsNotLoadedDirectly() {
        assertEquals(
            "https://duckduckgo.com/?q=file%3A%2F%2F%2Fsdcard%2Fx.html",
            normalizeOmniboxInput("file:///sdcard/x.html", search),
        )
    }

    @Test
    fun addsHttpsToBareHost() {
        assertEquals("https://example.com", normalizeOmniboxInput("example.com"))
    }

    @Test
    fun handlesLocalhostWithPort() {
        assertEquals("https://localhost:8080/x", normalizeOmniboxInput("localhost:8080/x"))
    }

    @Test
    fun searchesFreeText() {
        assertEquals("https://duckduckgo.com/?q=gecko+view", normalizeOmniboxInput("gecko view"))
    }

    /** A pasted `data:` page renders without any browser chrome, so it must not load directly. */
    @Test
    fun pastedDataUrlIsSearched() {
        val input = "data:text/html,<h1>Sign in to continue</h1>"
        assertEquals(
            "https://duckduckgo.com/?q=data%3Atext%2Fhtml%2C%3Ch1%3ESign+in+to+continue%3C%2Fh1%3E",
            normalizeOmniboxInput(input, search),
        )
    }

    @Test
    fun viewSourceAndBlobAreNotLoadedDirectly() {
        assertEquals(
            "https://duckduckgo.com/?q=view-source%3Ahttps%3A%2F%2Fexample.com",
            normalizeOmniboxInput("view-source:https://example.com", search),
        )
        assertEquals(
            "https://duckduckgo.com/?q=blob%3Ahttps%3A%2F%2Fexample.com%2Fabc",
            normalizeOmniboxInput("blob:https://example.com/abc", search),
        )
    }

    /** The context-menu "Search the web" action must not turn host-like text into navigation. */
    @Test
    fun searchWebAlwaysSearchesHostLikeText() {
        assertEquals(
            "https://duckduckgo.com/?q=example.com",
            buildSearchUrl("example.com", search),
        )
        assertEquals(
            "https://duckduckgo.com/?q=https%3A%2F%2Fexample.com%2Fa",
            buildSearchUrl(" https://example.com/a ", search),
        )
    }

    @Test
    fun searchWebTrimsAndEncodesQuery() {
        assertEquals(
            "https://duckduckgo.com/?q=gecko+view+%26+more",
            buildSearchUrl("  gecko view & more  ", search),
        )
        assertEquals("about:blank", buildSearchUrl("   ", search))
    }
}
