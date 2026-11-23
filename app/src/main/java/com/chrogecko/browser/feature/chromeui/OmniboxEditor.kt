package com.chrogecko.browser.feature.chromeui

/**
 * Chrome wipes the whole URL when you start typing after tapping the omnibox, even if the tap put a
 * cursor in the middle of it. Returns the typed text to keep in that case, or null when the edit
 * should be applied verbatim ([old] to [new] was a deletion or a replacement of a selection).
 */
internal fun firstEditReplacement(old: String, new: String): String? {
    if (new.length <= old.length) return null
    val prefix = old.commonPrefixWith(new).length
    val typedLength = new.length - old.length
    if (prefix + typedLength > new.length) return null
    return new.substring(prefix, prefix + typedLength)
}
