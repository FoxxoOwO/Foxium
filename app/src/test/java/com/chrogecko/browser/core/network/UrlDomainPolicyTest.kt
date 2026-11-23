package com.chrogecko.browser.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Captured Cookie/Referer headers are only replayed when the target shares this registrable
 * domain with the page, so a bad answer leaks credentials to a third party.
 */
class UrlDomainPolicyTest {

    @Test
    fun `two label hosts keep the whole host`() {
        assertEquals("example.com", UrlDomainPolicy.registrableDomain("example.com"))
        assertEquals("example.com", UrlDomainPolicy.registrableDomain("cdn.example.com"))
        assertEquals("example.com", UrlDomainPolicy.registrableDomain("a.b.c.example.com"))
    }

    @Test
    fun `multi label public suffixes keep three labels`() {
        assertEquals("bbc.co.uk", UrlDomainPolicy.registrableDomain("news.bbc.co.uk"))
        assertEquals("example.com.au", UrlDomainPolicy.registrableDomain("cdn.example.com.au"))
        assertEquals("example.co.jp", UrlDomainPolicy.registrableDomain("x.example.co.jp"))
    }

    @Test
    fun `bare label and single host have no suffix`() {
        assertEquals("localhost", UrlDomainPolicy.registrableDomain("localhost"))
        assertEquals("", UrlDomainPolicy.registrableDomain(""))
    }

    @Test
    fun `case and trailing dot are normalised`() {
        assertEquals("example.com", UrlDomainPolicy.registrableDomain("CDN.Example.COM."))
    }

    @Test
    fun `empty labels are ignored`() {
        assertEquals("example.com", UrlDomainPolicy.registrableDomain("cdn..example.com"))
    }
}
