package com.chrogecko.browser.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIntentHandlerTest {

    @Test
    fun detectsIntentSchemeCaseInsensitively() {
        assertTrue(ExternalIntentHandler.isIntentUri("intent://pay.google.com/#Intent;scheme=https;end"))
        assertTrue(ExternalIntentHandler.isIntentUri("INTENT://bank.example/challenge#Intent;end"))
        assertFalse(ExternalIntentHandler.isIntentUri("https://pay.google.com/"))
        assertFalse(ExternalIntentHandler.isIntentUri("about:blank"))
    }

    @Test
    fun extractsSafeHttpFallbackAndRejectsUnsafeSchemes() {
        val valid = "intent://wallet#Intent;scheme=https;package=com.google.android.apps.walletnfcrel;" +
            "S.browser_fallback_url=https%3A%2F%2Fpay.google.com%2Fgp%2Fp%2Fui%2Fpay%3Fa%3Db%2Bc;end"
        assertEquals(
            "https://pay.google.com/gp/p/ui/pay?a=b+c",
            ExternalIntentHandler.extractFallbackUrlFromIntentUri(valid),
        )

        val jsFallback = "intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"
        assertNull(ExternalIntentHandler.extractFallbackUrlFromIntentUri(jsFallback))

        val fileFallback = "intent://x#Intent;S.browser_fallback_url=file%3A%2F%2F%2Fetc%2Fpasswd;end"
        assertNull(ExternalIntentHandler.extractFallbackUrlFromIntentUri(fileFallback))

        val missing = "intent://x#Intent;scheme=https;package=com.example.bank;end"
        assertNull(ExternalIntentHandler.extractFallbackUrlFromIntentUri(missing))
    }
}
