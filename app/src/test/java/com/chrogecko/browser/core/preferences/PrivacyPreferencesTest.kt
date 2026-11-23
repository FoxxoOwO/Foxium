package com.chrogecko.browser.core.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyPreferencesTest {

    @Test
    fun httpsOnlyModeParsesAndFallsBackToPrivateOnly() {
        assertEquals(HttpsOnlyMode.OFF, HttpsOnlyMode.fromId("off"))
        assertEquals(HttpsOnlyMode.PRIVATE_ONLY, HttpsOnlyMode.fromId("private"))
        assertEquals(HttpsOnlyMode.ALL, HttpsOnlyMode.fromId("all"))
        assertEquals(HttpsOnlyMode.PRIVATE_ONLY, HttpsOnlyMode.fromId(null))
        assertEquals(HttpsOnlyMode.PRIVATE_ONLY, HttpsOnlyMode.fromId("unknown"))
    }

    @Test
    fun cookiePolicyParsesAndFallsBackToIsolateCrossSite() {
        assertEquals(CookiePolicy.ISOLATE_CROSS_SITE, CookiePolicy.fromId("isolate"))
        assertEquals(CookiePolicy.BLOCK_TRACKERS, CookiePolicy.fromId("trackers"))
        assertEquals(CookiePolicy.BLOCK_THIRD_PARTY, CookiePolicy.fromId("third_party"))
        assertEquals(CookiePolicy.BLOCK_ALL, CookiePolicy.fromId("none"))
        assertEquals(CookiePolicy.ALLOW_ALL, CookiePolicy.fromId("all"))
        assertEquals(CookiePolicy.ISOLATE_CROSS_SITE, CookiePolicy.fromId(null))
        assertEquals(CookiePolicy.ISOLATE_CROSS_SITE, CookiePolicy.fromId("invalid"))
    }

    @Test
    fun cookieBannerModeParsesAndFallsBackToReject() {
        assertEquals(CookieBannerMode.DISABLED, CookieBannerMode.fromId("off"))
        assertEquals(CookieBannerMode.REJECT, CookieBannerMode.fromId("reject"))
        assertEquals(CookieBannerMode.REJECT_OR_ACCEPT, CookieBannerMode.fromId("reject_or_accept"))
        assertEquals(CookieBannerMode.REJECT, CookieBannerMode.fromId(null))
        assertEquals(CookieBannerMode.REJECT, CookieBannerMode.fromId("unknown"))
    }

    @Test
    fun dohModeParsesAndFallsBackToOff() {
        assertEquals(DohMode.OFF, DohMode.fromId("off"))
        assertEquals(DohMode.INCREASED, DohMode.fromId("first"))
        assertEquals(DohMode.MAX, DohMode.fromId("only"))
        assertEquals(DohMode.OFF, DohMode.fromId(null))
        assertEquals(DohMode.OFF, DohMode.fromId("bogus"))
    }

    @Test
    fun dohProviderParsesAndFallsBackToCloudflare() {
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.fromId("cloudflare"))
        assertEquals(DohProvider.QUAD9, DohProvider.fromId("quad9"))
        assertEquals(DohProvider.NEXTDNS, DohProvider.fromId("nextdns"))
        assertEquals(DohProvider.CUSTOM, DohProvider.fromId("custom"))
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.fromId(null))
        assertEquals(DohProvider.CLOUDFLARE, DohProvider.fromId("unknown"))
    }

    @Test
    fun dohUriValidatorNormalizesValidHttpsUrls() {
        assertEquals(
            "https://dns.example/dns-query",
            DohUriValidator.normalize("https://dns.example/dns-query"),
        )
        assertEquals(
            "HTTPS://dns.quad9.net/dns-query",
            DohUriValidator.normalize("  HTTPS://dns.quad9.net/dns-query  "),
        )
    }

    @Test
    fun dohUriValidatorRejectsUnsafeOrMalformedUrls() {
        assertNull(DohUriValidator.normalize(""))
        assertNull(DohUriValidator.normalize("   "))
        assertNull(DohUriValidator.normalize("http://dns.example/dns-query"))
        assertNull(DohUriValidator.normalize("ftp://dns.example/dns-query"))
        assertNull(DohUriValidator.normalize("https:///dns-query"))
        assertNull(DohUriValidator.normalize("https://dns.example/dns query"))
        assertNull(DohUriValidator.normalize("https://user:pass@dns.example/dns-query"))
        assertNull(DohUriValidator.normalize("not a valid uri ::"))
        assertNull(DohUriValidator.normalize("https://dns.example/\u0000"))
    }
}
