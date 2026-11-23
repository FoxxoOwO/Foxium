package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarAutoHidePolicyTest {

    @Test
    fun scrollingDownFarEnoughHidesTheToolbar() {
        val result = ToolbarAutoHidePolicy.next(currentY = 300, lastScrollY = 100, hidden = false)
        assertTrue(result.hidden)
        assertEquals(300, result.lastScrollY)
    }

    @Test
    fun scrollingUpAlwaysRevealsTheToolbar() {
        val result = ToolbarAutoHidePolicy.next(currentY = 250, lastScrollY = 300, hidden = true)
        assertFalse(result.hidden)
    }

    @Test
    fun backAtTopAlwaysRevealsTheToolbar() {
        val result = ToolbarAutoHidePolicy.next(currentY = 0, lastScrollY = 500, hidden = true)
        assertFalse(result.hidden)
        assertEquals(0, result.lastScrollY)
    }

    @Test
    fun tinyScrollDoesNotChangeState() {
        val result = ToolbarAutoHidePolicy.next(currentY = 102, lastScrollY = 100, hidden = false)
        assertFalse(result.hidden)
        assertEquals(100, result.lastScrollY)
    }

    @Test
    fun smallDownwardScrollNearTopKeepsToolbarVisible() {
        val result = ToolbarAutoHidePolicy.next(currentY = 50, lastScrollY = 10, hidden = false)
        assertFalse(result.hidden)
    }
}
