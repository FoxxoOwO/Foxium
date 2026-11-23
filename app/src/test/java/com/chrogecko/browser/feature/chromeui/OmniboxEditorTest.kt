package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmniboxEditorTest {

    @Test
    fun firstCharacterAfterFocusKeepsOnlyWhatWasTyped() {
        assertEquals("x", firstEditReplacement("https://www.mozilla.org/en-US/", "https://www.moxzilla.org/en-US/"))
        assertEquals("X", firstEditReplacement("abcdef", "abXcdef"))
    }

    @Test
    fun deletionsAndUnchangedTextAreAppliedVerbatim() {
        assertNull(firstEditReplacement("xgecko", "xgeck"))
        assertNull(firstEditReplacement("abc", "abc"))
    }

    @Test
    fun emptyFieldJustTakesTheTypedText() {
        assertEquals("gecko", firstEditReplacement("", "gecko"))
    }
}
