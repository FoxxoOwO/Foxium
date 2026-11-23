package com.chrogecko.browser.feature.chromeui

import java.net.URI

/**
 * Google Translate serves pages through a per-site proxy host, e.g.
 * `https://example-com.translate.goog/path?…&_x_tr_sl=auto&_x_tr_tl=cs&_x_tr_hl=cs`.
 * Returns null for URLs Chrome could not translate (non-HTTP, already proxied, no host).
 */
fun translateUrl(url: String, targetLanguage: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    val scheme = uri.scheme ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (host.endsWith(".translate.goog")) return null
    val language = targetLanguage.substringBefore('-').ifBlank { "en" }
    val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
    val query = listOfNotNull(
        uri.rawQuery,
        "_x_tr_sl=auto",
        "_x_tr_tl=$language",
        "_x_tr_hl=$language",
    ).joinToString("&")
    val translatedHost = host.replace("-", "--").replace('.', '-')
    return "https://$translatedHost.translate.goog$path?$query"
}
