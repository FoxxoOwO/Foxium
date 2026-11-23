package com.chrogecko.browser.feature.cast

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistRewriterTest {

    private val proxy = "http://192.168.1.20:8765/proxy/abc123"
    private val proxyBuilder: (String) -> String =
        { url -> "$proxy?u=" + URLEncoder.encode(url, "UTF-8") }

    @Test
    fun `rewrites relative segments against final manifest url`() {
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segments/one.ts
            #EXTINF:6.0,
            https://cdn.example.com/two.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val rewritten = HlsPlaylistRewriter.rewrite(manifest, "https://cdn.example.com/hls/index.m3u8", proxyBuilder)

        assertTrue(rewritten.contains("#EXTM3U"))
        assertTrue(rewritten.contains("$proxy?u=https%3A%2F%2Fcdn.example.com%2Fhls%2Fsegments%2Fone.ts"))
        assertTrue(rewritten.contains("$proxy?u=https%3A%2F%2Fcdn.example.com%2Ftwo.ts"))
    }

    @Test
    fun `rewrites uri attributes and keeps data urls`() {
        val manifest = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",URI="audio/en.m3u8"
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k"
            #EXT-X-MAP:URI="data:text/plain;base64,AAAA"
        """.trimIndent()

        val rewritten = HlsPlaylistRewriter.rewrite(manifest, "https://cdn.example.com/hls/master.m3u8", proxyBuilder)

        assertTrue(rewritten.contains("URI=\"$proxy?u=https%3A%2F%2Fcdn.example.com%2Fhls%2Faudio%2Fen.m3u8\""))
        assertTrue(rewritten.contains("URI=\"$proxy?u=https%3A%2F%2Fkeys.example.com%2Fk\""))
        assertTrue(rewritten.contains("URI=\"data:text/plain;base64,AAAA\""))
        assertEquals(4, rewritten.lines().count { it.startsWith("#EXT") })
    }

    @Test
    fun `handles uppercase schemes without proxying DATA urls`() {
        val manifest = """
            #EXTM3U
            #EXT-X-MAP:URI="DATA:text/plain;base64,AAAA"
            HTTPS://CDN.EXAMPLE.COM/seg.ts
        """.trimIndent()

        val rewritten = HlsPlaylistRewriter.rewrite(manifest, "https://cdn.example.com/hls/master.m3u8", proxyBuilder)

        assertTrue(rewritten.contains("URI=\"DATA:text/plain;base64,AAAA\""))
        assertTrue(rewritten.contains("$proxy?u=HTTPS%3A%2F%2FCDN.EXAMPLE.COM%2Fseg.ts"))
    }
}
