package com.chrogecko.browser.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssFeedParserTest {

    @Test
    fun normalizeFeedUrlRequiresPublicHttps() {
        assertEquals(
            "https://feeds.example.org/rss.xml",
            RssFeedParser.normalizeFeedUrl("  feeds.example.org/rss.xml  "),
        )
        assertEquals(
            "https://news.ycombinator.com/rss",
            RssFeedParser.normalizeFeedUrl("https://news.ycombinator.com/rss"),
        )
        assertNull(RssFeedParser.normalizeFeedUrl(""))
        assertNull(RssFeedParser.normalizeFeedUrl("http://feeds.example.org/rss.xml"))
        assertNull(RssFeedParser.normalizeFeedUrl("https://localhost/feed.xml"))
        assertNull(RssFeedParser.normalizeFeedUrl("https://127.0.0.1/feed.xml"))
        assertNull(RssFeedParser.normalizeFeedUrl("https://192.168.1.50/feed.xml"))
        assertNull(RssFeedParser.normalizeFeedUrl("https://[::1]/feed.xml"))
        assertNull(RssFeedParser.normalizeFeedUrl("https://intranet/feed.xml"))
    }

    @Test
    fun parsesRss2FeedWithCdataEntitiesAndGuidFallback() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Example News</title>
                <item>
                  <title><![CDATA[Hello <b>Foxium</b> &amp; World]]></title>
                  <link>https://example.org/articles/one</link>
                </item>
                <item>
                  <title>Second &quot;Quoted&quot; Story&#39;s Headline</title>
                  <guid>https://sub.example.org/articles/two</guid>
                </item>
                <item>
                  <title>Non-permalink guid</title>
                  <guid isPermaLink="false">https://example.org/not-a-link</guid>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val items = RssFeedParser.parse(xml, "https://example.org/feed.xml")
        assertEquals(2, items.size)
        assertEquals("Hello Foxium & World", items[0].title)
        assertEquals("https://example.org/articles/one", items[0].url)
        assertEquals("example.org", items[0].host)
        assertEquals("Second \"Quoted\" Story's Headline", items[1].title)
        assertEquals("https://sub.example.org/articles/two", items[1].url)
        assertEquals("sub.example.org", items[1].host)
    }

    @Test
    fun parsesAtomFeedResolvesRelativeLinksAndSkipsUnsafeSchemes() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>Relative Post</title>
                <link rel="self" href="https://example.com/atom.xml"/>
                <link rel="alternate" href="/posts/relative-post"/>
              </entry>
              <entry>
                <title>Unsafe Javascript</title>
                <link href="javascript:alert(1)"/>
              </entry>
              <entry>
                <title>Unsafe File</title>
                <link href="file:///etc/passwd"/>
              </entry>
            </feed>
        """.trimIndent()

        val items = RssFeedParser.parse(xml, "https://example.com/feeds/atom.xml")
        assertEquals(1, items.size)
        assertEquals("Relative Post", items[0].title)
        assertEquals("https://example.com/posts/relative-post", items[0].url)
        assertEquals("example.com", items[0].host)
    }

    @Test
    fun malformedXmlReturnsEmptyList() {
        assertEquals(emptyList<DiscoverItem>(), RssFeedParser.parse("<rss><broken", "https://example.com/feed.xml"))
    }

    @Test
    fun doesNotDoubleDecodeAmpersandEntities() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title><![CDATA[Literal &amp;quot;Token&amp;#39;s End]]></title>
                  <link>https://example.org/articles/entities</link>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val items = RssFeedParser.parse(xml, "https://example.org/feed.xml")
        assertEquals(1, items.size)
        assertEquals("Literal &quot;Token&#39;s End", items[0].title)
    }
}
