package com.chrogecko.browser.feature.chromeui

import com.chrogecko.browser.feature.search.SearchSuggestion

/** Pure merge of local (history/bookmark) and engine suggestions, kept testable. */
internal object SuggestionMerger {

    fun merge(
        local: List<SearchSuggestion>,
        remote: List<SearchSuggestion>,
        limit: Int = 8,
    ): List<SearchSuggestion> = (local + remote)
        .distinctBy { it.text.lowercase() }
        .take(limit)
}
