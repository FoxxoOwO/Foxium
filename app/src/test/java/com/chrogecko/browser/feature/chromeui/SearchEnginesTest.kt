package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEnginesTest {

    @Test
    fun defaultEngineIsDuckDuckGo() {
        assertEquals("duckduckgo", SearchEngines.byId(null).id)
        assertEquals("duckduckgo", SearchEngines.byId("").id)
        assertEquals("duckduckgo", SearchEngines.byId("nope").id)
    }

    @Test
    fun everyTemplateHasOnePlaceholder() {
        assertTrue(SearchEngines.all.count { it.id == SearchEngines.DEFAULT_ID } == 1)
        SearchEngines.all.forEach { engine ->
            assertEquals(engine.id, 1, engine.template.split("%s").size - 1)
        }
    }

    @Test
    fun freeTextUsesTheSelectedTemplate() {
        val template = SearchEngines.byId("google").template
        assertEquals(
            "https://www.google.com/search?q=gecko+view",
            normalizeOmniboxInput("gecko view", template),
        )
    }

    @Test
    fun urlInputIgnoresTheTemplate() {
        val template = SearchEngines.byId("bing").template
        assertEquals("https://example.com", normalizeOmniboxInput("example.com", template))
        assertEquals("about:blank", normalizeOmniboxInput("  ", template))
    }

    @Test
    fun customTemplateValidationRejectsInsecureOrMissingPlaceholder() {
        assertEquals(
            "https://kagi.com/search?q=%s",
            SearchEngines.normalizeCustomTemplate("  https://kagi.com/search?q=%s  "),
        )
        assertNull(SearchEngines.normalizeCustomTemplate(""))
        assertNull(SearchEngines.normalizeCustomTemplate("https://kagi.com/search?q=test"))
        assertNull(SearchEngines.normalizeCustomTemplate("http://kagi.com/search?q=%s"))
        assertNull(SearchEngines.normalizeCustomTemplate("https://kagi.com/search?q= %s"))
        assertNull(SearchEngines.normalizeCustomTemplate("https://user:pass@kagi.com/search?q=%s"))
        assertNull(SearchEngines.normalizeCustomTemplate("not a url %s"))
    }

    @Test
    fun resolveCustomEngineUsesNameOrFallsBackToHost() {
        val named = SearchEngines.resolve(
            id = SearchEngines.CUSTOM_ID,
            customName = "Kagi",
            customTemplate = "https://kagi.com/search?q=%s",
        )
        assertEquals(SearchEngines.CUSTOM_ID, named.id)
        assertEquals("Kagi", named.name)
        assertEquals("https://kagi.com/search?q=%s", named.template)

        val unnamed = SearchEngines.resolve(
            id = SearchEngines.CUSTOM_ID,
            customName = "   ",
            customTemplate = "https://www.startpage.com/do/search?q=%s",
        )
        assertEquals("startpage.com", unnamed.name)

        val invalid = SearchEngines.resolve(
            id = SearchEngines.CUSTOM_ID,
            customName = "Broken",
            customTemplate = "http://bad.example/?q=%s",
        )
        assertEquals(SearchEngines.DEFAULT_ID, invalid.id)
    }
}
