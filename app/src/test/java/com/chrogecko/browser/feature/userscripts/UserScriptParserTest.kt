package com.chrogecko.browser.feature.userscripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptParserTest {

    private val sample = """
        // ==UserScript==
        // @name         Example Enhancer
        // @namespace    com.example.enhancer
        // @version      1.2.3
        // @description  Adds things
        // @match        https://example.com/*
        // @include      /^https?:\/\/alt\.example\.org\//
        // @exclude      https://example.com/private/*
        // @run-at       document-end
        // @grant        GM_setValue
        // @grant        GM_xmlhttpRequest
        // @require      https://cdn.example.com/lib.js
        // @resource     logo https://example.com/logo.png
        // @updateURL    https://example.com/script.user.js
        // ==/UserScript==

        console.log("hello");
    """.trimIndent()

    @Test
    fun `parses metadata and keeps source`() {
        val parsed = UserScriptParser.parse(sample)

        assertEquals("Example Enhancer", parsed.name)
        assertEquals("com.example.enhancer", parsed.namespace)
        assertEquals("1.2.3", parsed.version)
        assertEquals(listOf("https://example.com/*"), parsed.matchPatterns)
        assertEquals(listOf("/^https?:\\/\\/alt\\.example\\.org\\//"), parsed.includePatterns)
        assertEquals(listOf("https://example.com/private/*"), parsed.excludePatterns)
        assertEquals("document-end", parsed.runAt)
        assertEquals(listOf("GM_setValue", "GM_xmlhttpRequest"), parsed.grants)
        assertEquals(listOf("https://cdn.example.com/lib.js"), parsed.requires)
        assertEquals("https://example.com/logo.png", parsed.resources["logo"])
        assertEquals("https://example.com/script.user.js", parsed.updateUrl)
        assertTrue(parsed.source.contains("console.log"))
    }

    /** GM_info.scriptMetaStr is built from this block; it must survive parsing verbatim. */
    @Test
    fun `metadata block is preserved for GM_info`() {
        val block = UserScriptParser.metadataBlock(sample)
        assertNotNull(block)
        assertTrue(block!!.contains("==UserScript=="))
        assertTrue(block.contains("@name         Example Enhancer"))
        assertTrue(block.contains("==/UserScript=="))
        assertFalse(block.contains("console.log"))
    }

    @Test
    fun `run-at defaults to document-idle and namespace to name`() {
        val parsed = UserScriptParser.parse(
            """
            // ==UserScript==
            // @name   Minimal
            // @match  *://*/*
            // ==/UserScript==
            alert(1)
            """.trimIndent(),
        )

        assertEquals("document-idle", parsed.runAt)
        assertEquals("Minimal", parsed.namespace)
    }

    @Test(expected = UserScriptParseException::class)
    fun `rejects source without metadata block`() {
        UserScriptParser.parse("console.log(1)")
    }

    @Test(expected = UserScriptParseException::class)
    fun `rejects script without match or include`() {
        UserScriptParser.parse(
            """
            // ==UserScript==
            // @name No Targets
            // ==/UserScript==
            1
            """.trimIndent(),
        )
    }

    @Test
    fun `compares numeric versions`() {
        assertTrue(UserScriptFeature.compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(UserScriptFeature.compareVersions("2.0", "2.0.0") == 0)
        assertTrue(UserScriptFeature.compareVersions("1.0.0-beta", "1.0.0") != 0)
    }
}
