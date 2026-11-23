package com.chrogecko.browser.feature.chromeui

/** What should become active after a tab is closed. */
internal sealed interface TabReplacement {
    data object None : TabReplacement
    data class Select(val id: String) : TabReplacement
    data class Create(val isPrivate: Boolean) : TabReplacement
}

/** Pure decision logic for tab close, kept separate from the ViewModel so it can be unit-tested. */
internal object TabClosePolicy {

    /**
     * @param remaining tabs that are still open, as (id, isPrivate) pairs.
     * @param allowReplacement when false, closing the last incognito tab leaves incognito mode.
     */
    fun decide(
        closingIsPrivate: Boolean,
        remaining: List<Pair<String, Boolean>>,
        allowReplacement: Boolean,
    ): TabReplacement {
        val sameProfile = remaining.firstOrNull { it.second == closingIsPrivate }
        if (sameProfile != null) return TabReplacement.Select(sameProfile.first)
        if (closingIsPrivate && !allowReplacement) {
            val normal = remaining.firstOrNull { !it.second }
            return if (normal != null) {
                TabReplacement.Select(normal.first)
            } else {
                TabReplacement.Create(false)
            }
        }
        return TabReplacement.Create(closingIsPrivate)
    }
}
