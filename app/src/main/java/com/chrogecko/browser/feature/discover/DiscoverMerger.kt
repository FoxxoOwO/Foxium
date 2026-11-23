package com.chrogecko.browser.feature.discover

/** Pure merge of several Discover feeds, kept testable without network calls. */
internal object DiscoverMerger {

    fun merge(
        feeds: List<List<DiscoverItem>>,
        hidden: Set<String>,
        limit: Int,
        maxPerHost: Int = MAX_PER_HOST,
        interleave: Boolean = false,
    ): List<DiscoverItem> {
        val ordered = if (interleave) interleave(feeds) else feeds.flatten()
        val perHost = mutableMapOf<String, Int>()
        return ordered
            .filterNot { it.url in hidden }
            .distinctBy { it.url }
            .filter { item ->
                val used = perHost[item.host] ?: 0
                if (used < maxPerHost) {
                    perHost[item.host] = used + 1
                    true
                } else {
                    false
                }
            }
            .take(limit)
    }

    private fun interleave(feeds: List<List<DiscoverItem>>): List<DiscoverItem> {
        val maxSize = feeds.maxOfOrNull { it.size } ?: 0
        if (maxSize == 0) return emptyList()
        val out = ArrayList<DiscoverItem>(feeds.sumOf { it.size })
        for (i in 0 until maxSize) {
            for (feed in feeds) {
                if (i < feed.size) out.add(feed[i])
            }
        }
        return out
    }

    private const val MAX_PER_HOST = 2
}
