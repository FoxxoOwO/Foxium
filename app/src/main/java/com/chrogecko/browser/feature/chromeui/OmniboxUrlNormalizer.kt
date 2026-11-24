package com.chrogecko.browser.feature.chromeui

import java.net.URLEncoder

// Schemes the address bar may load directly. Chrome refuses data:/view-source:/blob:/file: typed or
// pasted here, and so do we: a pasted `data:` page renders with chrome-less content that is easy to
// pass off as browser UI, and `file://` could expose internal app files.
private val urlSchemes = listOf("http://", "https://", "about:")
private val hostPattern = Regex("^[A-Za-z0-9][A-Za-z0-9.-]*(?::\\d+)?(?:/.*)?$")

/** Turns omnibox input into a loadable URL, searching [searchTemplate] (a `%s` template) for free text. */
fun normalizeOmniboxInput(input: String, searchTemplate: String = DEFAULT_SEARCH_TEMPLATE): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "about:blank"
    if (urlSchemes.any { trimmed.startsWith(it, ignoreCase = true) }) return trimmed

    val looksLikeHost = !trimmed.contains(' ') &&
        hostPattern.matches(trimmed) &&
        (trimmed.contains('.') || trimmed.startsWith("localhost"))
    if (looksLikeHost) return "https://$trimmed"

    return searchTemplate.replace("%s", URLEncoder.encode(trimmed, Charsets.UTF_8.name()))
}

/**
 * Always searches, even when the text happens to look like a host or a URL. Chrome's
 * "Search the web" context-menu action searches the selected text verbatim, while the omnibox
 * is allowed to reinterpret host-like input as navigation.
 */
fun buildSearchUrl(query: String, searchTemplate: String = DEFAULT_SEARCH_TEMPLATE): String {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return "about:blank"
    return searchTemplate.replace("%s", URLEncoder.encode(trimmed, Charsets.UTF_8.name()))
}

const val DEFAULT_SEARCH_TEMPLATE = "https://duckduckgo.com/?q=%s"
