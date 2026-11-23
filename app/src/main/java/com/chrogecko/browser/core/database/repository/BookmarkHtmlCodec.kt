package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import java.net.URI

/** A parsed bookmark entry together with the nested folder names enclosing it in HTML. */
data class ImportedBookmark(
    val url: String,
    val title: String,
    val folderPath: List<String>,
)

/**
 * Encodes and parses the standard Netscape Bookmark HTML format (`<!DOCTYPE NETSCAPE-Bookmark-file-1>`)
 * used by Chrome, Firefox, Edge, and Safari for portable bookmark backup/import.
 */
object BookmarkHtmlCodec {

    private val TOKEN_REGEX = Regex(
        """<h3\b[^>]*>(.*?)</h3>|<dl\b[^>]*>|</dl\s*>|<a\b([^>]*)>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HREF_REGEX = Regex(
        """\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        RegexOption.IGNORE_CASE,
    )
    private val STRIP_TAGS_REGEX = Regex("""<[^>]+>""")

    fun exportHtml(
        bookmarks: List<BookmarkEntity>,
        folders: List<BookmarkFolderEntity>,
    ): String {
        val foldersByParent = folders.groupBy { it.parentId }
        val bookmarksByFolder = bookmarks.groupBy { it.folderId }
        val visited = HashSet<Long>()
        val out = StringBuilder(2048)

        out.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        out.appendLine("<!-- This is an automatically generated file.")
        out.appendLine("     It will be read and overwritten.")
        out.appendLine("     DO NOT EDIT! -->")
        out.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        out.appendLine("<TITLE>Bookmarks</TITLE>")
        out.appendLine("<H1>Bookmarks</H1>")
        out.appendLine("<DL><p>")

        fun writeNode(parentId: Long?, indent: String) {
            val childFolders = foldersByParent[parentId]
                .orEmpty()
                .sortedBy { it.name.lowercase() }
            for (folder in childFolders) {
                if (!visited.add(folder.id)) continue
                out.append(indent)
                    .append("<DT><H3>")
                    .append(escapeHtml(folder.name))
                    .appendLine("</H3>")
                out.append(indent).appendLine("<DL><p>")
                writeNode(folder.id, "$indent    ")
                out.append(indent).appendLine("</DL><p>")
            }

            val childBookmarks = bookmarksByFolder[parentId]
                .orEmpty()
                .sortedBy { it.createdAt }
            for (bookmark in childBookmarks) {
                val safeTitle = bookmark.title.ifBlank { bookmark.url }
                out.append(indent)
                    .append("<DT><A HREF=\"")
                    .append(escapeHtml(bookmark.url))
                    .append("\">")
                    .append(escapeHtml(safeTitle))
                    .appendLine("</A>")
            }
        }

        writeNode(parentId = null, indent = "    ")
        out.appendLine("</DL><p>")
        return out.toString()
    }

    fun parseHtml(html: String): List<ImportedBookmark> {
        val results = mutableListOf<ImportedBookmark>()
        val dlStack = ArrayList<String?>()
        var pendingFolder: String? = null

        for (match in TOKEN_REGEX.findAll(html)) {
            val token = match.value
            when {
                token.startsWith("<h3", ignoreCase = true) -> {
                    val rawName = match.groupValues[1]
                    val cleaned = unescapeHtml(rawName.replace(STRIP_TAGS_REGEX, "")).trim()
                    pendingFolder = cleaned.takeIf { it.isNotEmpty() }
                }
                token.startsWith("<dl", ignoreCase = true) -> {
                    dlStack.add(pendingFolder)
                    pendingFolder = null
                }
                token.startsWith("</dl", ignoreCase = true) -> {
                    if (dlStack.isNotEmpty()) dlStack.removeAt(dlStack.lastIndex)
                    pendingFolder = null
                }
                token.startsWith("<a", ignoreCase = true) -> {
                    pendingFolder = null
                    val attrs = match.groupValues[2]
                    val rawTitle = match.groupValues[3]
                    val hrefMatch = HREF_REGEX.find(attrs) ?: continue
                    val rawUrl = hrefMatch.groupValues[1].ifEmpty {
                        hrefMatch.groupValues[2].ifEmpty { hrefMatch.groupValues[3] }
                    }
                    val url = unescapeHtml(rawUrl).trim()
                    val uri = runCatching { URI(url) }.getOrNull() ?: continue
                    val scheme = uri.scheme?.lowercase()
                    if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) continue
                    val title = unescapeHtml(rawTitle.replace(STRIP_TAGS_REGEX, "")).trim()
                        .ifBlank { url }
                    results += ImportedBookmark(
                        url = url,
                        title = title,
                        folderPath = dlStack.filterNotNull(),
                    )
                }
            }
        }
        return results
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun unescapeHtml(text: String): String {
        if ('&' !in text) return text
        return text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }
}
