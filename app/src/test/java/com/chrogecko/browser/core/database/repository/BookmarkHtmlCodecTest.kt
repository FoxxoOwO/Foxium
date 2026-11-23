package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlCodecTest {

    @Test
    fun exportAndParsePreservesNestedFoldersAndEscapedText() {
        val folders = listOf(
            BookmarkFolderEntity(id = 1L, name = "Work & Tools", parentId = null),
            BookmarkFolderEntity(id = 2L, name = "Docs <2026>", parentId = 1L),
        )
        val bookmarks = listOf(
            BookmarkEntity(id = 10L, url = "https://example.com/?a=1&b=2", title = "Home \"Page\" & 'More'", createdAt = 1L, folderId = null),
            BookmarkEntity(id = 11L, url = "https://work.example/app", title = "Portal", createdAt = 2L, folderId = 1L),
            BookmarkEntity(id = 12L, url = "https://docs.example/spec", title = "Spec <v2>", createdAt = 3L, folderId = 2L),
        )

        val html = BookmarkHtmlCodec.exportHtml(bookmarks, folders)
        assertTrue(html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("Work &amp; Tools"))
        assertTrue(html.contains("Docs &lt;2026&gt;"))

        val parsed = BookmarkHtmlCodec.parseHtml(html)
        assertEquals(3, parsed.size)

        val rootBookmark = parsed.first { it.url == "https://example.com/?a=1&b=2" }
        assertEquals("Home \"Page\" & 'More'", rootBookmark.title)
        assertEquals(emptyList<String>(), rootBookmark.folderPath)

        val workBookmark = parsed.first { it.url == "https://work.example/app" }
        assertEquals("Portal", workBookmark.title)
        assertEquals(listOf("Work & Tools"), workBookmark.folderPath)

        val nestedBookmark = parsed.first { it.url == "https://docs.example/spec" }
        assertEquals("Spec <v2>", nestedBookmark.title)
        assertEquals(listOf("Work & Tools", "Docs <2026>"), nestedBookmark.folderPath)
    }

    @Test
    fun parseRejectsNonHttpAndMalformedUrls() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
                <DT><A HREF="javascript:alert(1)">XSS</A>
                <DT><A HREF="data:text/html,&lt;script&gt;1&lt;/script&gt;">Data</A>
                <DT><A HREF="file:///etc/passwd">Local</A>
                <DT><A HREF="http://legacy.example/page">HTTP OK</A>
                <DT><A HREF="https://valid.example/path">HTTPS OK</A>
            </DL><p>
        """.trimIndent()

        val parsed = BookmarkHtmlCodec.parseHtml(html)
        assertEquals(2, parsed.size)
        assertEquals("http://legacy.example/page", parsed[0].url)
        assertEquals("https://valid.example/path", parsed[1].url)
    }
}
