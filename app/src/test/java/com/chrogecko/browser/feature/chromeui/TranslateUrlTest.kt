package com.chrogecko.browser.feature.chromeui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslateUrlTest {

    @Test
    fun mapsHostAndKeepsPathAndQuery() {
        assertEquals(
            "https://example-com.translate.goog/a/b?x=1&_x_tr_sl=auto&_x_tr_tl=cs&_x_tr_hl=cs",
            translateUrl("https://example.com/a/b?x=1", "cs-CZ"),
        )
    }

    @Test
    fun escapesHyphensInHostLabels() {
        assertEquals(
            "https://my--site-example-com.translate.goog/a?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
            translateUrl("https://my-site.example.com/a", "en"),
        )
    }

    @Test
    fun addsSlashWhenPathIsEmpty() {
        assertEquals(
            "https://example-com.translate.goog/?_x_tr_sl=auto&_x_tr_tl=en&_x_tr_hl=en",
            translateUrl("https://example.com", "en"),
        )
    }

    @Test
    fun rejectsNonHttpUrls() {
        assertNull(translateUrl("about:blank", "cs"))
        assertNull(translateUrl("ftp://example.com/", "cs"))
        assertNull(translateUrl("not a url", "cs"))
    }

    @Test
    fun rejectsAlreadyTranslatedPages() {
        assertNull(
            translateUrl(
                "https://example-com.translate.goog/x?_x_tr_sl=auto&_x_tr_tl=cs",
                "cs",
            ),
        )
    }
}
