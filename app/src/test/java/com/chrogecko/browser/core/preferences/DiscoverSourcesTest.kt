package com.chrogecko.browser.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverSourcesTest {

    @Test
    fun togglingCanCombineSources() {
        assertEquals(
            setOf("top", "best"),
            DiscoverSources.toggle(setOf("top"), "best", true),
        )
    }

    @Test
    fun turningOffTheLastSourceFallsBackToTop() {
        assertEquals(setOf("top"), DiscoverSources.toggle(setOf("top"), "top", false))
    }

    @Test
    fun unknownSourcesAreIgnored() {
        assertEquals(setOf("new"), DiscoverSources.normalize(setOf("new", "hacker-news")))
        assertEquals(setOf("top"), DiscoverSources.normalize(setOf("hacker-news")))
    }
}
