package com.chrogecko.browser.core.preferences

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLocaleManagerTest {

    @Test
    fun unknownTagFallsBackToSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.CZECH, AppLanguage.fromTag("cs"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
    }

    @Test
    fun explicitLanguagesMapToGeckoLocaleLists() {
        assertNull(AppLocaleManager.geckoLocalesFor(AppLanguage.SYSTEM))
        assertEquals("en-US", AppLocaleManager.geckoLocalesFor(AppLanguage.ENGLISH)?.first())
        assertEquals("cs-CZ", AppLocaleManager.geckoLocalesFor(AppLanguage.CZECH)?.first())
    }

    @Test
    fun explicitLanguagesResolveToLocale() {
        assertEquals(Locale.ENGLISH, AppLocaleManager.resolveLocale("en"))
        assertEquals(Locale.forLanguageTag("cs"), AppLocaleManager.resolveLocale("cs"))
    }
}
