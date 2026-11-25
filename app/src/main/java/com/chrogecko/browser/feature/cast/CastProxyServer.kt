package com.chrogecko.browser.feature.cast

import com.chrogecko.browser.core.network.NetworkTargetPolicy
import com.chrogecko.browser.core.network.UrlDomainPolicy
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Local LAN proxy for Chromecast. Cast receivers cannot replay the Gecko session's cookies or
 * referer, so streams are republished through this server with the captured headers attached.
 * Playlists are rewritten to keep every child request inside the proxy.
 */
@Singleton
class CastProxyServer @Inject constructor(
    private val client: OkHttpClient,
) {

    private data class Stream(
        val media: CastMedia,
        val createdAt: Long = System.currentTimeMillis(),
    )

    private val streams = ConcurrentHashMap<String, Stream>()
    private val startMutex = Mutex()

    @Volatile
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var boundPort: Int = 0

    /** Resolved once at start: enumerating interfaces per HLS segment costs seconds. */
    @Volatile
    private var boundAddress: String? = null

    @Volatile
    private var mirrorToken: String? = null

    /** Starts the proxy if needed and returns a receiver-ready URL for [media]. */
    suspend fun publish(media: CastMedia): String {
        val base = start()
        pruneExpired()
        if (streams.size >= MAX_STREAMS) {
            streams.entries.minByOrNull { it.value.createdAt }?.key?.let(streams::remove)
        }
        val token = UUID.randomUUID().toString().replace("-", "")
        streams[token] = Stream(media)
        return "$base/proxy/$token"
    }

    suspend fun start(): String {
        if (boundPort > 0 && server != null) return currentBaseUrl()
        return startMutex.withLock {
            if (boundPort > 0 && server != null) return@withLock currentBaseUrl()
            var lastError: Exception? = null
            for (port in PORT_RANGE) {
                // Ktor CIO reports a bind failure asynchronously, which would crash the app as an
                // uncaught coroutine exception, so the port is probed first.
                if (!portIsFree(port)) continue
                val candidate = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    installProxyRoutes()
                }
                try {
                    candidate.start(wait = false)
                    server = candidate
                    boundPort = port
                    return@withLock currentBaseUrl()
                } catch (error: Exception) {
                    lastError = error
                    runCatching { candidate.stop(0, 0) }
                }
            }
            throw IOException("No free LAN proxy port in $PORT_RANGE", lastError)
        }
    }

    private fun currentBaseUrl(): String {
        val cached = boundAddress
        if (cached != null) return "http://$cached:$boundPort"
        val address = localIpv4Address()
            ?: throw IOException("No routable LAN address for Chromecast")
        boundAddress = address
        return "http://$address:$boundPort"
    }

    private fun sameOrigin(source: URI, target: URI): Boolean =
        source.scheme.equals(target.scheme, ignoreCase = true) &&
            source.host.equals(target.host, ignoreCase = true) &&
            source.port == target.port

    private fun portIsFree(port: Int): Boolean = runCatching {
        java.net.ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(java.net.InetSocketAddress("0.0.0.0", port))
        }
    }.isSuccess

    fun stop() {
        runCatching { server?.stop(500, 1500) }
        server = null
        boundPort = 0
        boundAddress = null
        mirrorToken = null
        streams.clear()
    }

    fun isRunning(): Boolean = server != null && boundPort > 0

    /** Starts the LAN server if needed and returns a token-protected mirror viewer URL. */
    suspend fun publishMirror(): String {
        val base = start()
        val token = UUID.randomUUID().toString().replace("-", "")
        mirrorToken = token
        return "$base/mirror/$token"
    }

    /** Invalidates the mirror URL and stops the LAN server when no cast streams are active. */
    fun clearMirror() {
        mirrorToken = null
        if (streams.isEmpty()) stop()
    }

    private fun io.ktor.server.application.Application.installProxyRoutes() {
        routing {
            get("/health") { call.respondText("chrogecko-cast") }

            options("/proxy/{token}") {
                appendCors(call)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/proxy/{token}") { handle(call, headOnly = false) }
            head("/proxy/{token}") { handle(call, headOnly = true) }

            get("/mirror/{token}") {
                val token = call.parameters["token"]
                val active = mirrorToken
                if (token == null || active == null || token != active) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                call.respondText(mirrorPage(token), ContentType.Text.Html)
            }
            get("/mirror/{token}/stream") {
                val token = call.parameters["token"]
                val active = mirrorToken
                if (token == null || active == null || token != active) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                serveMirror(call)
            }
        }
    }

    /**
     * Screen mirroring is served as MJPEG. Chromecast's Default Media Receiver only plays
     * container formats, so this URL is for browsers on the same network.
     * shortcut: no WebM encoder, serve MJPEG until a fragmented-MP4 streamer exists
     */
    private suspend fun serveMirror(call: ApplicationCall) {
        call.respondOutputStream(
            contentType = ContentType.parse("multipart/x-mixed-replace; boundary=$MIRROR_BOUNDARY"),
            status = HttpStatusCode.OK,
        ) {
            MirrorFrames.viewerJoined()
            android.util.Log.i("ChroGeckoMirror", "viewer joined, frame=${MirrorFrames.latest()?.size}")
            try {
                var sent = 0
                var waitedWithoutFrame = 0L
                while (true) {
                    // Never spin forever when capture stopped before the first frame.
                    if (!MirrorFrames.running.value && sent == 0) break
                    // MediaProjection emits a frame only when the screen changes, so resend the
                    // last one: viewers treat a repeated frame as a still picture.
                    val frame = MirrorFrames.latest()
                    if (frame == null) {
                        if (sent > 0) break
                        waitedWithoutFrame += MIRROR_TICK_MS
                        if (waitedWithoutFrame > 10_000) break
                        delay(MIRROR_TICK_MS)
                        continue
                    }
                    withContext(Dispatchers.IO) {
                        write(
                            "--$MIRROR_BOUNDARY\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                                .toByteArray(),
                        )
                        write(frame)
                        write("\r\n".toByteArray())
                        flush()
                    }
                    sent++
                    delay(MIRROR_TICK_MS)
                }
            } catch (io: IOException) {
                // The viewer closed the tab.
            } finally {
                MirrorFrames.viewerLeft()
            }
        }
    }

    /**
     * Blocks loopback, link-local, RFC1918, CGNAT and other non-public ranges. Used for child
     * URLs injected through `?u=` and redirect hops so a hostile playlist cannot make the phone
     * fetch internal HTTP endpoints from the LAN. Fails closed when the host cannot be resolved.
     */
    private fun isBlockedLiteralHost(host: String): Boolean {
        val lower = host.trim().lowercase().removeSurrounding("[", "]")
        return lower.isEmpty() || lower == "localhost" || lower.endsWith(".localhost") ||
            lower == "0.0.0.0" || lower == "::" || lower == "::1"
    }

    private fun isBlockedHostSync(host: String): Boolean {
        val lower = host.trim().lowercase().removeSurrounding("[", "]")
        if (isBlockedLiteralHost(lower)) return true
        return runCatching {
            InetAddress.getAllByName(lower).any(NetworkTargetPolicy::isNonPublic)
        }.getOrDefault(true)
    }

    private suspend fun isBlockedLocalTarget(host: String): Boolean = withContext(Dispatchers.IO) {
        isBlockedHostSync(host)
    }

    /**
     * Builds a call client sharing the underlying pool while enforcing [NetworkTargetPolicy]
     * inside OkHttp's DNS resolver and disabling automatic redirects so [executeFollowingSafeRedirects]
     * also validates IP-literal redirect targets (which OkHttp's DNS hook skips) and strips
     * credentials on cross-site redirects.
     */
    private fun clientFor(originHost: String?): OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(
            object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val allowSameOrigin = originHost != null && originHost.equals(hostname, ignoreCase = true)
                    if (!allowSameOrigin && isBlockedLiteralHost(hostname)) {
                        throw UnknownHostException("Blocked non-public host: $hostname")
                    }
                    val resolved = Dns.SYSTEM.lookup(hostname)
                    if (!allowSameOrigin && resolved.any(NetworkTargetPolicy::isNonPublic)) {
                        throw UnknownHostException("Blocked non-public address for $hostname")
                    }
                    return resolved
                }
            },
        )
        .build()

    /** Follows HTTP redirects explicitly so IP-literal hops and cross-site credentials are checked. */
    private fun executeFollowingSafeRedirects(
        upstreamClient: OkHttpClient,
        initialRequest: Request,
        originHost: String?,
    ): Response {
        var current = initialRequest
        repeat(MAX_REDIRECTS + 1) {
            val response = upstreamClient.newCall(current).execute()
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            if (location.isNullOrBlank()) return response
            val nextUrl = current.url.resolve(location)
            if (nextUrl == null || (nextUrl.scheme != "http" && nextUrl.scheme != "https")) {
                response.close()
                throw IOException("Blocked redirect target")
            }
            val allowSameOrigin = originHost != null && originHost.equals(nextUrl.host, ignoreCase = true)
            if (!allowSameOrigin && isBlockedHostSync(nextUrl.host)) {
                response.close()
                throw IOException("Blocked redirect to non-public host: ${nextUrl.host}")
            }
            val previous = current.url
            // Never carry credentials across a scheme, host or port change (including https→http).
            val sameOrigin = previous.scheme.equals(nextUrl.scheme, ignoreCase = true) &&
                previous.host.equals(nextUrl.host, ignoreCase = true) && previous.port == nextUrl.port
            response.close()
            current = current.newBuilder()
                .url(nextUrl)
                .apply {
                    if (!sameOrigin) {
                        removeHeader("Cookie")
                        removeHeader("Authorization")
                    }
                }
                .build()
        }
        throw IOException("Too many redirects")
    }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - STREAM_TTL_MS
        streams.entries.removeAll { it.value.createdAt < cutoff }
    }

    /**
     * Tokens live in a URL on the LAN, so they expire: a leaked link stops working on its own, and
     * the captured Cookie/Referer headers do not sit in memory forever.
     */
    private fun liveStream(token: String?): Stream? {
        if (token == null) return null
        val stream = streams[token] ?: return null
        if (System.currentTimeMillis() - stream.createdAt > STREAM_TTL_MS) {
            streams.remove(token)
            return null
        }
        return stream
    }

    private suspend fun handle(call: ApplicationCall, headOnly: Boolean) {
        val token = call.parameters["token"]
        val stream = liveStream(token)
        if (token == null || stream == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val explicitChild = call.request.queryParameters["u"]
        val target = explicitChild ?: stream.media.url
        val targetUri = runCatching { URI(target) }.getOrNull()
        val targetHost = targetUri?.host
        if (targetUri == null || targetHost.isNullOrBlank() || (targetUri.scheme != "http" && targetUri.scheme != "https")) {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val origin = runCatching { URI(stream.media.url) }.getOrNull()
        val originHost = origin?.host
        // Credentials may only go to the exact origin they were captured from. A heuristic
        // registrable-domain match can leak cookies between tenants sharing a public suffix, and
        // an https→http child would send them in plaintext.
        val sameOrigin = origin != null && sameOrigin(origin, targetUri)
        val sameHost = origin != null && origin.host.equals(targetHost, ignoreCase = true)
        if (explicitChild != null && !sameHost && isBlockedLocalTarget(targetHost)) {
            call.respond(HttpStatusCode.Forbidden)
            return
        }

        val isHls = target.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val isDash = target.substringBefore('?').endsWith(".mpd", ignoreCase = true)

        val request = runCatching {
            Request.Builder()
                .url(target)
                .apply {
                    stream.media.headers.forEach { (name, value) ->
                        val lower = name.lowercase()
                        if (lower in FORBIDDEN_REQUEST_HEADERS || value.isBlank()) return@forEach
                        if ((lower == "cookie" || lower == "authorization") && !sameOrigin) return@forEach
                        // Captured headers can contain invalid characters; skip them instead of
                        // throwing from the request builder and failing the whole proxy response.
                        runCatching { header(name, value) }
                    }
                    header("Accept-Encoding", "identity")
                    call.request.headers[HttpHeaders.Range]?.let { range ->
                        if (!isHls && !isDash) header("Range", range)
                    }
                    if (headOnly) head() else get()
                }
                .build()
        }.getOrElse {
            call.respond(HttpStatusCode.BadRequest)
            return
        }

        val upstreamClient = clientFor(originHost)
        val response = try {
            withContext(Dispatchers.IO) {
                executeFollowingSafeRedirects(upstreamClient, request, originHost)
            }
        } catch (error: IOException) {
            call.respondText(error.message ?: "Upstream fetch failed", status = HttpStatusCode.BadGateway)
            return
        }

        try {
            if (!response.isSuccessful && response.code != 206) {
                call.respondText(
                    "Upstream HTTP ${response.code}",
                    status = HttpStatusCode.fromValue(response.code),
                )
                return
            }

            if (isHls || (response.header("Content-Type")?.contains("mpegurl", true) == true)) {
                val body = try {
                    withContext(Dispatchers.IO) {
                        response.body?.let { readBoundedUtf8(it.byteStream(), MAX_MANIFEST_BYTES) }.orEmpty()
                    }
                } catch (error: IOException) {
                    call.respondText(error.message ?: "Manifest too large", status = HttpStatusCode.BadGateway)
                    return
                }
                val rewritten = HlsPlaylistRewriter.rewrite(
                    playlist = body,
                    baseUrl = response.request.url.toString(),
                ) { child -> proxiedUrl(token, child) }
                appendCors(call)
                if (headOnly) {
                    call.respondText("", ContentType.parse(HLS_CONTENT_TYPE))
                } else {
                    call.respondText(rewritten, ContentType.parse(HLS_CONTENT_TYPE))
                }
                return
            }

            if (isDash || (response.header("Content-Type")?.contains("dash", true) == true)) {
                val body = try {
                    withContext(Dispatchers.IO) {
                        response.body?.let { readBoundedUtf8(it.byteStream(), MAX_MANIFEST_BYTES) }.orEmpty()
                    }
                } catch (error: IOException) {
                    call.respondText(error.message ?: "Manifest too large", status = HttpStatusCode.BadGateway)
                    return
                }
                val rewritten = rewriteDash(body, response.request.url.toString()) { child ->
                    proxiedUrl(token, child)
                }
                appendCors(call)
                if (headOnly) {
                    call.respondText("", ContentType.parse(DASH_CONTENT_TYPE))
                } else {
                    call.respondText(rewritten, ContentType.parse(DASH_CONTENT_TYPE))
                }
                return
            }

            appendCors(call)
            response.header("Content-Range")?.let { call.response.headers.append("Content-Range", it) }
            response.header("Accept-Ranges")?.let {
                call.response.headers.append("Accept-Ranges", it)
            } ?: call.response.headers.append("Accept-Ranges", "bytes")
            response.header("Content-Disposition")?.let {
                call.response.headers.append("Content-Disposition", it)
            }

            val contentType = response.header("Content-Type")?.let { header ->
                runCatching { ContentType.parse(header) }.getOrNull()
            } ?: ContentType.parse(contentTypeFor(stream.media))
            val length = response.body?.contentLength()?.takeIf { it >= 0 }
            val status = HttpStatusCode.fromValue(response.code)

            if (headOnly) {
                call.respondText("", contentType, status)
            } else {
                call.respondOutputStream(contentType, status, length) {
                    withContext(Dispatchers.IO) {
                        response.body?.byteStream()?.use { input -> input.copyTo(this@respondOutputStream) }
                    }
                }
            }
        } finally {
            response.close()
        }
    }

    private fun proxiedUrl(token: String, target: String): String {
        val encoded = URLEncoder.encode(target, "UTF-8")
        return "${currentBaseUrl()}/proxy/$token?u=$encoded"
    }

    private fun appendCors(call: ApplicationCall) {
        call.response.headers.append("Access-Control-Allow-Origin", "*")
        call.response.headers.append("Cache-Control", "no-store")
        call.response.headers.append("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        call.response.headers.append(
            "Access-Control-Allow-Headers",
            "Range, Origin, Accept, Content-Type",
        )
        call.response.headers.append(
            "Access-Control-Expose-Headers",
            "Content-Length, Content-Range, Accept-Ranges",
        )
    }

    private fun rewriteDash(
        xml: String,
        baseUrl: String,
        proxy: (String) -> String,
    ): String {
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return xml
        val withAttributes = DASH_ATTRIBUTE.replace(xml) { match ->
            val attribute = match.groupValues[1]
            val reference = decodeXmlEntities(match.groupValues[2].trim())
            if (reference.contains("$") || reference.startsWith("data:")) return@replace match.value
            val resolved = runCatching { base.resolve(reference).toString() }.getOrNull()
                ?: return@replace match.value
            if (!resolved.startsWith("http")) return@replace match.value
            "$attribute=\"${proxy(resolved)}\""
        }
        return DASH_BASE_URL.replace(withAttributes) { match ->
            val reference = decodeXmlEntities(match.groupValues[1].trim())
            if (reference.contains("$")) return@replace match.value
            val resolved = runCatching { base.resolve(reference).toString() }.getOrNull()
                ?: return@replace match.value
            if (!resolved.startsWith("http")) return@replace match.value
            "<BaseURL>${proxy(resolved)}</BaseURL>"
        }
    }

    private fun decodeXmlEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    private fun contentTypeFor(media: CastMedia): String = when {
        media.isHls -> HLS_CONTENT_TYPE
        media.isDash -> DASH_CONTENT_TYPE
        media.mime.isNotBlank() -> media.mime
        media.url.substringBefore('?').endsWith(".webm", true) -> "video/webm"
        else -> "video/mp4"
    }

    private fun localIpv4Address(): String? {
        val candidates = mutableListOf<Pair<String, String>>()
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in networkInterface.inetAddresses.toList()) {
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    candidates += networkInterface.name to address.hostAddress.orEmpty()
                }
            }
        }
        val preferred = candidates.firstOrNull { (name, _) ->
            name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth")
        }
        return (preferred ?: candidates.firstOrNull())?.second
    }

    /** Debug helper exposed for logs/tests. */
    fun activeStreams(): Map<String, JSONObject> = streams.mapValues { (_, stream) ->
        JSONObject()
            .put("url", stream.media.url)
            .put("kind", stream.media.kind)
            .put("title", stream.media.title)
    }

    private companion object {
        const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
        const val DASH_CONTENT_TYPE = "application/dash+xml"
        const val MAX_STREAMS = 64
        const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        const val MAX_REDIRECTS = 10

        fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IOException("Manifest exceeds $maxBytes bytes")
                }
                out.write(buffer, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        }
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val STREAM_TTL_MS = 12 * 60 * 60 * 1000L
        val PORT_RANGE = 8765..8785

        val FORBIDDEN_REQUEST_HEADERS = setOf(
            "connection",
            "host",
            "content-length",
            "transfer-encoding",
            "accept-encoding",
            "upgrade",
            "range",
        )

        const val MIRROR_BOUNDARY = "chrogeckoframe"
        const val MIRROR_TICK_MS = 100L

        fun mirrorPage(token: String): String = """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Foxium mirror</title>
            <style>body{margin:0;background:#202124;color:#e8eaed;font:14px system-ui,sans-serif;text-align:center}
            img{width:100%;max-width:720px;display:block;margin:0 auto}</style></head>
            <body><img id="s" src="/mirror/$token/stream" alt="Mirrored screen">
            <script>document.getElementById('s').onerror=function(){var img=this;setTimeout(function(){img.src='/mirror/$token/stream?t='+Date.now()},1500)}</script>
            </body></html>
        """.trimIndent()

        val DASH_ATTRIBUTE = Regex("""(media|initialization|sourceURL)\s*=\s*"([^"]*)"""")
        val DASH_BASE_URL = Regex("""<BaseURL\b[^>]*>\s*([^<]+?)\s*</BaseURL>""")
    }
}
