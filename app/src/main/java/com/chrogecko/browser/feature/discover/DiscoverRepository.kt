package com.chrogecko.browser.feature.discover

import com.chrogecko.browser.core.preferences.DISCOVER_MODE_HACKER_NEWS
import com.chrogecko.browser.core.preferences.DISCOVER_MODE_RSS
import com.chrogecko.browser.core.preferences.RssFeedSource
import com.chrogecko.browser.feature.favicons.faviconHost
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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
                    force || !fresh -> runCatching { fetchSource(source, limit, hidden) }
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
                            runCatching { fetchRss(feed.url, limit) }
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

    private fun fetchRss(url: String, limit: Int): List<DiscoverItem> {
        val safeUrl = RssFeedParser.normalizeFeedUrl(url) ?: return emptyList()
        val request = Request.Builder()
            .url(safeUrl)
            .header(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9",
            )
            .build()
        val xml = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $safeUrl" }
            val body = response.body ?: return emptyList()
            readBoundedUtf8(body.byteStream(), MAX_RSS_BYTES)
        }
        return RssFeedParser.parse(xml, safeUrl, limit * 2)
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

    private suspend fun fetchSource(source: String, limit: Int, hidden: Set<String>): List<DiscoverItem> {
        val ids = json.parseToJsonElement(fetch(endpointFor(source))).jsonArray
            .mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
            .take(limit * 4)
        val gate = Semaphore(6)
        val items = coroutineScope {
            ids.map { id -> async { gate.withPermit { item(id) } } }.awaitAll()
        }
        return items.filterNotNull()
            .distinctBy { it.host }
            .filterNot { it.url in hidden }
            .take(limit * 2)
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            response.body?.string().orEmpty()
        }
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

        /** Pure mapping so source selection can be unit-tested without a network call. */
        fun endpointFor(source: String): String = when (source) {
            "new" -> NEW_STORIES_URL
            "best" -> BEST_STORIES_URL
            "show" -> SHOW_STORIES_URL
            else -> TOP_STORIES_URL
        }
    }
}
