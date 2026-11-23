package com.chrogecko.browser.feature.cast

import org.json.JSONObject

/** A media stream detected by the bundled `sys_mediasniffer` extension. */
data class CastMedia(
    val id: String,
    val url: String,
    val mime: String,
    val kind: String,
    val title: String,
    val poster: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val headers: Map<String, String>,
    val pageUrl: String,
    val pageTitle: String,
    val requiresAuth: Boolean,
    val detectedAt: Long,
) {
    val isHls: Boolean
        get() = kind == "hls" || mime.contains("mpegurl", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    val isDash: Boolean
        get() = kind == "dash" || mime.contains("dash", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true)

    companion object {
        fun fromJson(item: JSONObject, pageUrl: String, pageTitle: String): CastMedia? {
            val url = item.optString("url")
            if (url.isBlank()) return null
            val headersJson = item.optJSONObject("headers")
            val headers = buildMap {
                if (headersJson != null) {
                    headersJson.keys().forEach { key ->
                        val value = headersJson.optString(key)
                        if (value.isNotBlank()) put(key, value)
                    }
                }
            }
            return CastMedia(
                id = item.optString("id").ifBlank { url },
                url = url,
                mime = item.optString("mime"),
                kind = item.optString("kind", "file"),
                title = item.optString("title").ifBlank { pageTitle },
                poster = item.optString("poster").ifBlank { null },
                durationMs = item.optLong("duration", 0L),
                width = item.optInt("width"),
                height = item.optInt("height"),
                headers = headers,
                pageUrl = pageUrl,
                pageTitle = pageTitle,
                requiresAuth = item.optBoolean("requiresAuth"),
                detectedAt = item.optLong("detectedAt", System.currentTimeMillis()),
            )
        }
    }
}
