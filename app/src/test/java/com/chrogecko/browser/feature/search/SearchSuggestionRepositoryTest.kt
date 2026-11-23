package com.chrogecko.browser.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSuggestionRepositoryTest {

    @Test
    fun endpointsOnlyForSupportedEngines() {
        assertEquals(
            "https://suggestqueries.google.com/complete/search?client=firefox&q=gecko",
            SearchSuggestionRepository.endpointFor("google", "gecko"),
        )
        assertEquals(
            "https://duckduckgo.com/ac/?q=gecko&type=list",
            SearchSuggestionRepository.endpointFor("duckduckgo", "gecko"),
        )
        assertNull(SearchSuggestionRepository.endpointFor("bing", "gecko"))
    }
}
