package com.chrogecko.browser.feature.discover

import com.chrogecko.browser.core.network.NetworkTargetPolicy
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_HACKER_NEWS
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_RSS
import com.chrogecko.browser.core.preferences.RssFeedSource
import com.chrogecko.browser.feature.favicons.faviconHost
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** One suggestion shown in the new tab page Discover list. */
data class DiscoverItem(val title: String, val url: String, val host: String)

/**
 * Supplies the new tab page Discover feed from either the public Hacker News API or
 * user-configured RSS/Atom feeds.
 */
@Singleton
class DiscoverRepository @Inject constructor(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheMutex = Mutex()

    /** Redirects are followed manually and every hop is re-validated; DNS is pinned at connect. */
    private val safeClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val resolved = Dns.SYSTEM.lookup(hostname)
                if (resolved.any(NetworkTargetPolicy::isNonPublic)) {
                    throw UnknownHostException("Blocked non-public address for $hostname")
                }
                return resolved
            }
        })
        .build()

    /** Separate cache per feed so switching Top/New never shows the other list's stories. */
    private val cache = ConcurrentHashMap<String, Pair<Long, List<DiscoverItem>>>()

    suspend fun suggestions(
        limit: Int = 12,
        force: Boolean = false,
        sources: Set<String> = setOf("top"),
        hidden: Set<String> = emptySet(),
        mode: String = DISCOVER_MODE_HACKER_NEWS,
        rssFeeds: List<RssFeedSource> = emptyList(),
    ): List<DiscoverItem> = withContext(Dispatchers.IO) {
        if (mode == DISCOVER_MODE_RSS) {
            return@withContext rssSuggestions(limit, force, rssFeeds, hidden)
        }
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            // Fetch every enabled source, then merge. Each feed keeps its own cache so toggling
            // sources never blocks on a stale combined entry.
            val merged = sources.ifEmpty { setOf("top") }.map { source ->
                val cached = cache[source]
                val fresh = cached != null && now - cached.first < CACHE_TTL_MS
                when {
                    force || !fresh -> runCatching { fetchSource(source) }
                        .getOrDefault(emptyList())
                        .also { if (it.isNotEmpty()) cache[source] = now to it }
                    else -> cached.second
                }
            }
            DiscoverMerger.merge(merged, hidden, limit)
        }
    }

    private suspend fun rssSuggestions(
        limit: Int,
        force: Boolean,
        rssFeeds: List<RssFeedSource>,
        hidden: Set<String>,
    ): List<DiscoverItem> = cacheMutex.withLock {
        if (rssFeeds.isEmpty()) return@withLock emptyList()
        val now = System.currentTimeMillis()
        val gate = Semaphore(4)
        val merged = coroutineScope {
            rssFeeds.map { feed ->
                async {
                    val key = "rss:${feed.url}"
                    val cached = cache[key]
                    val fresh = cached != null && now - cached.first < CACHE_TTL_MS
                    when {
                        force || !fresh -> gate.withPermit {
                            runCatching { fetchRss(feed.url) }
                                .getOrDefault(emptyList())
                        }.also { if (it.isNotEmpty()) cache[key] = now to it }
                        else -> cached.second
                    }
                }
            }.awaitAll()
        }
        val maxPerHost = if (rssFeeds.size <= 1) limit else maxOf(2, (limit + rssFeeds.size - 1) / rssFeeds.size)
        DiscoverMerger.merge(merged, hidden, limit, maxPerHost = maxPerHost, interleave = true)
    }

    private fun fetchRss(url: String): List<DiscoverItem> {
        val xml = fetchValidated(
            url,
            "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9",
        )
        // Store unfiltered items; DiscoverMerger applies hidden/limit at read time so un-hiding
        // an article or raising the limit does not wait for the cache TTL.
        return RssFeedParser.parse(xml, url, MAX_CACHED_FEED_ITEMS)
    }

    private fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw IOException("RSS feed exceeds $maxBytes bytes")
            }
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    }

    private suspend fun fetchSource(source: String): List<DiscoverItem> {
        val ids = json.parseToJsonElement(fetch(endpointFor(source))).jsonArray
            .mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
            .take(MAX_CACHED_FEED_ITEMS)
        val gate = Semaphore(6)
        val items = coroutineScope {
            ids.map { id -> async { gate.withPermit { item(id) } } }.awaitAll()
        }
        return items.filterNotNull()
            .distinctBy { it.host }
    }

    private fun fetch(url: String): String = fetchValidated(url, "application/json")

    /** Validates every URL before connecting and after each redirect (no automatic redirects). */
    private fun fetchValidated(url: String, accept: String): String {
        var current = url
        for (attempt in 0 until MAX_REDIRECTS) {
            val safe = RssFeedParser.normalizeFeedUrl(current)
                ?: throw IOException("Blocked URL: $current")
            val request = Request.Builder().url(safe).header("Accept", accept).build()
            safeClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (response.code in 301..308 && !location.isNullOrBlank()) {
                    current = URI(safe).resolve(location)?.toString().orEmpty()
                    if (current.isEmpty()) throw IOException("Invalid redirect target")
                    return@use
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $safe")
                val stream = response.body?.byteStream()
                if (stream != null) return readBoundedUtf8(stream, MAX_RSS_BYTES)
                return ""
            }
        }
        throw IOException("Too many redirects")
    }

    private fun item(id: Int): DiscoverItem? = runCatching {
        val obj = json.parseToJsonElement(fetch("$ITEM_URL$id.json")).jsonObject
        val title = obj["title"]?.jsonPrimitive?.content.orEmpty()
        val url = obj["url"]?.jsonPrimitive?.content.orEmpty()
        val host = faviconHost(url)
        if (title.isBlank() || !url.startsWith("http") || host == null) {
            null
        } else {
            DiscoverItem(title = title, url = url, host = host)
        }
    }.getOrNull()

    internal companion object {
        const val TOP_STORIES_URL = "https://hacker-news.firebaseio.com/v0/topstories.json"
        const val NEW_STORIES_URL = "https://hacker-news.firebaseio.com/v0/newstories.json"
        const val BEST_STORIES_URL = "https://hacker-news.firebaseio.com/v0/beststories.json"
        const val SHOW_STORIES_URL = "https://hacker-news.firebaseio.com/v0/showstories.json"
        const val ITEM_URL = "https://hacker-news.firebaseio.com/v0/item/"
        const val CACHE_TTL_MS = 10L * 60L * 1000L
        private const val MAX_RSS_BYTES = 2 * 1024 * 1024
        private const val MAX_CACHED_FEED_ITEMS = 200
        private const val MAX_REDIRECTS = 5

        /** Pure mapping so source selection can be unit-tested without a network call. */
        fun endpointFor(source: String): String = when (source) {
            "new" -> NEW_STORIES_URL
            "best" -> BEST_STORIES_URL
            "show" -> SHOW_STORIES_URL
            else -> TOP_STORIES_URL
        }
    }
}
