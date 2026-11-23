package com.chrogecko.browser.feature.cast

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastProxyServerTest {

    private val client = OkHttpClient()

    private class MockResponse(
        val statusLine: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String = "",
    )

    private class LocalMockHttp(
        private val handler: (port: Int, path: String, headers: Map<String, String>) -> MockResponse,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        private val worker = thread(isDaemon = true) {
            while (!socket.isClosed) {
                val conn = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                conn.use { clientSocket ->
                    val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use
                    val path = requestLine.split(" ").getOrNull(1).orEmpty()
                    val headers = linkedMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        val colon = line.indexOf(':')
                        if (colon > 0) {
                            headers[line.substring(0, colon).trim().lowercase()] =
                                line.substring(colon + 1).trim()
                        }
                    }
                    val resp = handler(port, path, headers)
                    val bodyBytes = resp.body.toByteArray()
                    val out = clientSocket.getOutputStream()
                    out.write("HTTP/1.1 ${resp.statusLine}\r\n".toByteArray())
                    resp.headers.forEach { (k, v) ->
                        out.write("$k: $v\r\n".toByteArray())
                    }
                    out.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray())
                    out.write("Connection: close\r\n\r\n".toByteArray())
                    if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
                    out.flush()
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.join(1000)
        }
    }

    private fun testMedia(url: String, headers: Map<String, String> = emptyMap()) = CastMedia(
        id = url,
        url = url,
        mime = "video/mp4",
        kind = "file",
        title = "Test",
        poster = null,
        durationMs = 0L,
        width = 0,
        height = 0,
        headers = headers,
        pageUrl = url,
        pageTitle = "Test",
        requiresAuth = false,
        detectedAt = 0L,
    )

    private fun loopbackUrl(publishedUrl: String, suffix: String = ""): String {
        val uri = URI(publishedUrl)
        return "http://127.0.0.1:${uri.port}${uri.rawPath}$suffix"
    }

    @Test
    fun healthEndpointResponds() = runBlocking {
        val proxy = CastProxyServer(client)
        try {
            val base = proxy.start()
            val req = Request.Builder().url("http://127.0.0.1:${URI(base).port}/health").build()
            client.newCall(req).execute().use { res ->
                assertEquals(200, res.code)
                assertEquals("chrogecko-cast", res.body?.string())
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun mirrorRoutesRequireActiveTokenAndClearOnStop() = runBlocking {
        val proxy = CastProxyServer(client)
        try {
            val mirrorUrl = proxy.publishMirror()
            val uri = URI(mirrorUrl)
            val port = uri.port
            val token = uri.path.substringAfterLast('/')

            client.newCall(Request.Builder().url("http://127.0.0.1:$port/mirror/wrong").build()).execute().use {
                assertEquals(404, it.code)
            }
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/mirror/wrong/stream").build()).execute().use {
                assertEquals(404, it.code)
            }
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/mirror/$token").build()).execute().use { res ->
                assertEquals(200, res.code)
                assertTrue(res.header("Cache-Control")?.contains("no-store") == true)
                val html = res.body?.string().orEmpty()
                assertTrue(html.contains("/mirror/$token/stream"))
            }

            proxy.clearMirror()
            assertFalse(proxy.isRunning())
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun proxyUnknownTokenReturns404AndOptionsReturnsCors() = runBlocking {
        val proxy = CastProxyServer(client)
        try {
            val published = proxy.publish(testMedia("https://example.com/video.mp4"))
            val port = URI(published).port

            client.newCall(Request.Builder().url("http://127.0.0.1:$port/proxy/doesnotexist").build()).execute().use {
                assertEquals(404, it.code)
            }

            val optionsReq = Request.Builder()
                .url(loopbackUrl(published))
                .method("OPTIONS", null)
                .build()
            client.newCall(optionsReq).execute().use { res ->
                assertEquals(204, res.code)
                assertEquals("*", res.header("Access-Control-Allow-Origin"))
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun proxyRejectsNonPublicChildUrlParam() = runBlocking {
        val proxy = CastProxyServer(client)
        try {
            val published = proxy.publish(testMedia("https://example.com/live.m3u8"))
            val blockedTargets = listOf(
                "http://127.0.0.1:80/secret",
                "http://localhost:8080/secret",
                "http://169.254.169.254/latest",
            )
            for (target in blockedTargets) {
                val encoded = URLEncoder.encode(target, "UTF-8")
                client.newCall(Request.Builder().url(loopbackUrl(published, "?u=$encoded")).build()).execute().use { res ->
                    assertEquals("Expected 403 for $target", 403, res.code)
                }
            }
        } finally {
            proxy.stop()
        }
    }

    @Test
    fun proxyBlocksCrossOriginRedirectToLoopback() = runBlocking {
        val secretHit = AtomicBoolean(false)
        val proxy = CastProxyServer(client)
        LocalMockHttp { port, path, _ ->
            when (path) {
                "/video.mp4" -> MockResponse(
                    statusLine = "302 Found",
                    headers = mapOf("Location" to "http://127.0.0.1:$port/secret"),
                )
                "/secret" -> {
                    secretHit.set(true)
                    MockResponse(statusLine = "200 OK", body = "secret")
                }
                else -> MockResponse(statusLine = "404 Not Found")
            }
        }.use { upstream ->
            try {
                val published = proxy.publish(testMedia("http://localhost:${upstream.port}/video.mp4"))
                client.newCall(Request.Builder().url(loopbackUrl(published)).build()).execute().use { res ->
                    assertEquals(502, res.code)
                }
                assertFalse(secretHit.get())
            } finally {
                proxy.stop()
            }
        }
    }

    @Test
    fun proxyForwardsCapturedHeadersSameOriginAndStripsForbidden() = runBlocking {
        val capturedHeaders = AtomicReference<Map<String, String>>(emptyMap())
        val proxy = CastProxyServer(client)
        LocalMockHttp { _, path, headers ->
            if (path == "/clip.mp4") {
                capturedHeaders.set(headers)
                MockResponse(
                    statusLine = "200 OK",
                    headers = mapOf("Content-Type" to "video/mp4"),
                    body = "video-bytes",
                )
            } else {
                MockResponse(statusLine = "404 Not Found")
            }
        }.use { upstream ->
            try {
                val mockPort = upstream.port
                val published = proxy.publish(
                    testMedia(
                        url = "http://127.0.0.1:$mockPort/clip.mp4",
                        headers = mapOf(
                            "Cookie" to "sid=1",
                            "Referer" to "http://127.0.0.1:$mockPort/page",
                            "User-Agent" to "ChroGeckoTest",
                            "Range" to "bytes=0-10",
                            "Host" to "spoofed.example",
                        ),
                    ),
                )

                client.newCall(Request.Builder().url(loopbackUrl(published)).build()).execute().use { res ->
                    assertEquals(200, res.code)
                    assertEquals("video-bytes", res.body?.string())
                    assertTrue(res.header("Cache-Control")?.contains("no-store") == true)
                }

                val headers = capturedHeaders.get()
                assertEquals("sid=1", headers["cookie"])
                assertEquals("http://127.0.0.1:$mockPort/page", headers["referer"])
                assertEquals("ChroGeckoTest", headers["user-agent"])
                assertFalse(headers.containsKey("range"))
                assertEquals("127.0.0.1:$mockPort", headers["host"])
                assertNotEquals("spoofed.example", headers["host"])
            } finally {
                proxy.stop()
            }
        }
    }
}
