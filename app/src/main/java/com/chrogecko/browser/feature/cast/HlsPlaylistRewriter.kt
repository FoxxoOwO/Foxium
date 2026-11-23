package com.chrogecko.browser.feature.cast

import java.net.URI

/** Rewrites HLS playlists so every child playlist, segment and key is fetched through the LAN proxy. */
object HlsPlaylistRewriter {

    private val URI_ATTRIBUTE = Regex("""URI="([^"]*)"""")

    fun rewrite(playlist: String, baseUrl: String, proxy: (String) -> String): String {
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return playlist
        return playlist.lineSequence().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> line
                line.startsWith("#") -> URI_ATTRIBUTE.replace(line) { match ->
                    val resolved = resolve(base, match.groupValues[1]) ?: return@replace match.value
                    "URI=\"${proxy(resolved)}\""
                }
                else -> resolve(base, line.trim())?.let(proxy) ?: line
            }
        }
    }

    private fun resolve(base: URI, reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:") || trimmed.startsWith("blob:")) return null
        return runCatching { base.resolve(trimmed).toString() }
            .getOrNull()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
