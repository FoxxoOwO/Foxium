package com.chrogecko.browser.feature.addons

import java.net.URI

/** Resolves an addons.mozilla.org add-on page to its direct XPI download URL. */
object AmoInstallUrls {

    /** Returns the add-on slug when [url] is an AMO add-on detail page, otherwise null. */
    fun addonSlug(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "addons.mozilla.org") return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val index = segments.indexOf("addon")
        if (index < 0 || index + 1 >= segments.size) return null
        val slug = segments[index + 1]
        return slug.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
    }

    /** AMO's stable redirect endpoint for the latest signed XPI of a slug. */
    fun xpiUrl(slug: String): String =
        "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
}
