package com.chrogecko.browser.feature.chromeui

/** Pure helpers for "back closes a popup tab and returns to the tab that opened it". */
internal object TabOpenerPolicy {

    /** Opener of [tabId], but only when that tab still exists. */
    fun resolve(openerIds: Map<String, String>, tabId: String, existingIds: Set<String>): String? =
        openerIds[tabId]?.takeIf { it in existingIds }

    /** Drops entries for a closed tab, both as child and as parent. */
    fun prune(openerIds: MutableMap<String, String>, closedId: String) {
        openerIds.remove(closedId)
        openerIds.entries.removeAll { it.value == closedId }
    }
}
