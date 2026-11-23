package com.chrogecko.browser.feature.favicons

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.chrogecko.browser.core.database.dao.FaviconDao
import com.chrogecko.browser.core.database.entity.FaviconEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private val LINK_ICON_REGEX = Regex(
    "<link\\b[^>]*rel\\s*=\\s*[\"']?[^\"'>]*icon[^\"'>]*[\"']?[^>]*>",
    RegexOption.IGNORE_CASE,
)
private val HREF_REGEX = Regex(
    "href\\s*=\\s*(?:[\"']([^\"']+)[\"']|([^\\s>]+))",
    RegexOption.IGNORE_CASE,
)

/** Resolves `<link rel=icon>` hrefs found in [html] against [base]; http(s) only, at most four. */
internal fun iconHrefs(html: String, base: String): List<String> {
    val root = runCatching { java.net.URI(base) }.getOrNull() ?: return emptyList()
    return LINK_ICON_REGEX
        .findAll(html)
        .mapNotNull { tag ->
            HREF_REGEX.find(tag.value)?.let { match ->
                match.groupValues[1].ifEmpty { match.groupValues[2] }
            }
        }
        .mapNotNull { href -> runCatching { root.resolve(href.trim()).toString() }.getOrNull() }
        .filter { it.startsWith("http") }
        .distinct()
        .take(4)
        .toList()
}

/** Host key used for favicon lookups: lower-cased, no `www.`, `null` for non-web URLs. */
fun faviconHost(url: String): String? =
    runCatching { java.net.URI(url) }.getOrNull()
        ?.host
        ?.lowercase()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }

/**
 * Downloads and caches site icons.
 *
 * GeckoView 146 has neither a favicon nor a JavaScript-evaluation API, so the icon links are read
 * from the site's own HTML (no cookies) and fall back to `/favicon.ico`. Icons live in Room and are
 * exposed as [icons] keyed by [faviconHost].
 *
 * shortcut: pages that only declare an icon after JavaScript runs, or sit behind a login, get no
 * icon (letter badge) — revisit if GeckoView ships evaluateJS or the native port starts working.
 */
@Singleton
class FaviconRepository @Inject constructor(
    private val dao: FaviconDao,
    baseClient: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    private val failedHosts = ConcurrentHashMap<String, Long>()

    init {
        scope.launch { loadCached() }
    }

    private val inFlight = mutableSetOf<String>()

    /** Fetches the icon for [pageUrl]'s host unless it is already cached or being fetched. */
    fun ensure(pageUrl: String) {
        val host = faviconHost(pageUrl) ?: return
        if (_icons.value.containsKey(host)) return
        val failedAt = failedHosts[host]
        if (failedAt != null && System.currentTimeMillis() - failedAt < NEGATIVE_TTL_MS) return
        synchronized(inFlight) { if (!inFlight.add(host)) return }
        scope.launch {
            try {
                fetch(host)
            } finally {
                synchronized(inFlight) { inFlight.remove(host) }
            }
        }
    }

    private suspend fun loadCached() {
        val cached = runCatching { dao.all() }.getOrDefault(emptyList())
        val decoded = cached.mapNotNull { entity ->
            decode(entity.bytes)?.let { entity.host to it }
        }.toMap()
        if (decoded.isNotEmpty()) _icons.update { decoded + it }
    }

    private suspend fun fetch(host: String) {
        // HTTPS only: a plaintext favicon request announces every visited host to the network.
        val root = "https://$host/"
        val declared = runCatching { declaredIcons(root) }.getOrDefault(emptyList())
        for (url in declared + "${root}favicon.ico") {
            if (url.substringBefore('#').substringBefore('?').endsWith(".svg", ignoreCase = true)) {
                continue
            }
            val bytes = download(url, root) ?: continue
            val bitmap = decode(bytes) ?: continue
            runCatching {
                dao.put(FaviconEntity(host, url, bytes, System.currentTimeMillis()))
                dao.trim()
            }
            failedHosts.remove(host)
            _icons.update { it + (host to bitmap) }
            return
        }
        failedHosts[host] = System.currentTimeMillis()
    }

    /** Reads `<link rel=icon>` hrefs from a site root; empty when the page cannot be fetched. */
    private suspend fun declaredIcons(rootUrl: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(rootUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body ?: return@use emptyList()
            val source = body.byteStream().bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                while (out.length < MAX_ICON_HTML) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    val startSearch = (out.length - 10).coerceAtLeast(0)
                    out.append(buffer, 0, read)
                    if (out.indexOf("</head>", startSearch, ignoreCase = true) >= 0) break
                }
                out.toString()
            }
            iconHrefs(source, rootUrl)
        }
    }

    private suspend fun download(url: String, referer: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!url.startsWith("https://", ignoreCase = true)) return@withContext null
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use { stream ->
                        val buffer = ByteArray(8192)
                        val out = java.io.ByteArrayOutputStream()
                        while (out.size() < MAX_BYTES) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        if (out.size() == 0 || out.size() >= MAX_BYTES) null else out.toByteArray()
                    }
                }
            }.getOrNull()
        }

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > MAX_ICON_PX * 2 || bounds.outHeight / sample > MAX_ICON_PX * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }.getOrNull()

    companion object {
        private const val MAX_BYTES = 256 * 1024
        private const val MAX_ICON_HTML = 128 * 1024
        private const val MAX_ICON_PX = 96
        private const val NEGATIVE_TTL_MS = 15L * 60L * 1000L
    }
}
