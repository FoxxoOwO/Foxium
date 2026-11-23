package com.chrogecko.browser.feature.chromeui

import com.chrogecko.browser.feature.search.SearchSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestionMergerTest {

    private fun query(text: String) = SearchSuggestion(text, "https://example.com/?q=$text", SearchSuggestion.Kind.QUERY)
    private fun history(text: String) = SearchSuggestion(text, "https://$text.example", SearchSuggestion.Kind.HISTORY)

    @Test
    fun localSuggestionsComeBeforeEngineSuggestions() {
        val merged = SuggestionMerger.merge(listOf(history("gecko")), listOf(query("gecko view")), limit = 5)
        assertEquals(listOf(SearchSuggestion.Kind.HISTORY, SearchSuggestion.Kind.QUERY), merged.map { it.kind })
    }

    @Test
    fun duplicateTextsAreRemovedCaseInsensitively() {
        val merged = SuggestionMerger.merge(listOf(history("Gecko")), listOf(query("gecko")), limit = 5)
        assertEquals(1, merged.size)
        assertEquals(SearchSuggestion.Kind.HISTORY, merged.first().kind)
    }

    @Test
    fun resultIsCapped() {
        val many = (1..20).map { query("term$it") }
        assertEquals(8, SuggestionMerger.merge(emptyList(), many).size)
    }

    @Test
    fun emptyInputsProduceEmptyList() {
        assertEquals(emptyList<SearchSuggestion>(), SuggestionMerger.merge(emptyList(), emptyList()))
    }
}
