package com.chrogecko.browser.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverRepositoryTest {

    @Test
    fun sourceMapsToHackerNewsEndpoint() {
        assertEquals(DiscoverRepository.TOP_STORIES_URL, DiscoverRepository.endpointFor("top"))
        assertEquals(DiscoverRepository.NEW_STORIES_URL, DiscoverRepository.endpointFor("new"))
        assertEquals(DiscoverRepository.BEST_STORIES_URL, DiscoverRepository.endpointFor("best"))
        assertEquals(DiscoverRepository.SHOW_STORIES_URL, DiscoverRepository.endpointFor("show"))
        assertEquals(DiscoverRepository.TOP_STORIES_URL, DiscoverRepository.endpointFor("unknown"))
    }
}
