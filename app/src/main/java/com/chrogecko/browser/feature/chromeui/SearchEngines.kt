package com.chrogecko.browser.feature.chromeui

import java.net.URI

/** A search provider whose [template] holds a single `%s` for the encoded query. */
data class SearchEngine(val id: String, val name: String, val template: String)

object SearchEngines {
    const val DEFAULT_ID = "duckduckgo"
    const val CUSTOM_ID = "custom"

    val all: List<SearchEngine> = listOf(
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
        SearchEngine(DEFAULT_ID, "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s"),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
    )

    /** Unknown or blank ids fall back to the default instead of breaking navigation. */
    fun byId(id: String?): SearchEngine = all.firstOrNull { it.id == id }
        ?: all.first { it.id == DEFAULT_ID }

    /**
     * Validates a custom HTTPS search URL template containing `%s`. Returns the trimmed template
     * when valid, or `null` when malformed or insecure.
     */
    fun normalizeCustomTemplate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            return null
        }
        if ("%s" !in trimmed) return null
        val sample = trimmed.replace("%s", "test")
        val uri = runCatching { URI(sample) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return trimmed
    }

    /** Resolves either a built-in provider or the saved custom template (falling back safely). */
    fun resolve(
        id: String?,
        customName: String = "",
        customTemplate: String = "",
    ): SearchEngine {
        if (id != CUSTOM_ID) return byId(id)
        val normalized = normalizeCustomTemplate(customTemplate) ?: return byId(DEFAULT_ID)
        val trimmedName = customName.trim()
        val displayName = trimmedName.ifBlank {
            runCatching { URI(normalized.replace("%s", "test")).host?.removePrefix("www.") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "Custom"
        }
        return SearchEngine(id = CUSTOM_ID, name = displayName, template = normalized)
    }
}
