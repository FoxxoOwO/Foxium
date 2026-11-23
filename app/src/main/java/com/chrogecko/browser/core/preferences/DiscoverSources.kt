package com.chrogecko.browser.core.preferences

/** Pure helpers for the multi-select Discover source setting. */
object DiscoverSources {

    fun normalize(selected: Set<String>): Set<String> =
        selected.filter { it in DISCOVER_SOURCES }.toSet().ifEmpty { setOf("top") }

    fun toggle(selected: Set<String>, source: String, enabled: Boolean): Set<String> =
        normalize(if (enabled) selected + source else selected - source)
}
