package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabClosePolicyTest {

    @Test
    fun closingLastIncognitoTabSwitchesToNormalTab() {
        val result = TabClosePolicy.decide(
            closingIsPrivate = true,
            remaining = listOf("normal-1" to false, "normal-2" to false),
            allowReplacement = false,
        )
        assertEquals(TabReplacement.Select("normal-1"), result)
    }

    @Test
    fun closingLastIncognitoTabWithNoNormalTabCreatesNormalTab() {
        val result = TabClosePolicy.decide(
            closingIsPrivate = true,
            remaining = emptyList(),
            allowReplacement = false,
        )
        assertEquals(TabReplacement.Create(false), result)
    }

    @Test
    fun closingASiblingKeepsTheSameProfile() {
        val result = TabClosePolicy.decide(
            closingIsPrivate = true,
            remaining = listOf("normal" to false, "private-2" to true),
            allowReplacement = false,
        )
        assertEquals(TabReplacement.Select("private-2"), result)
    }

    @Test
    fun closingLastNormalTabAlwaysCreatesANewNormalTab() {
        assertEquals(
            TabReplacement.Create(false),
            TabClosePolicy.decide(false, emptyList(), allowReplacement = false),
        )
    }

    @Test
    fun explicitReplacementStillRecreatesIncognitoWhenRequested() {
        assertEquals(
            TabReplacement.Create(true),
            TabClosePolicy.decide(true, emptyList(), allowReplacement = true),
        )
    }
}
