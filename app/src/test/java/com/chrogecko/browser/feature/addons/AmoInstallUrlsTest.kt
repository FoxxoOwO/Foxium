package com.chrogecko.browser.feature.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmoInstallUrlsTest {

    @Test
    fun extractsSlugFromAmoDetailPage() {
        assertEquals(
            "ublock-origin",
            AmoInstallUrls.addonSlug("https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"),
        )
        assertEquals(
            "dark-reader",
            AmoInstallUrls.addonSlug("https://www.addons.mozilla.org/cs/firefox/addon/dark-reader/?src=search"),
        )
    }

    @Test
    fun rejectsOtherPages() {
        assertNull(AmoInstallUrls.addonSlug("https://example.com/firefox/addon/ublock-origin/"))
        assertNull(AmoInstallUrls.addonSlug("https://addons.mozilla.org/en-US/firefox/"))
        assertNull(AmoInstallUrls.addonSlug("not a url"))
    }

    @Test
    fun buildsLatestXpiUrl() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
            AmoInstallUrls.xpiUrl("ublock-origin"),
        )
    }
}
