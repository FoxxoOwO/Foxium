package com.chrogecko.browser.core.database.repository

import androidx.room.withTransaction
import com.chrogecko.browser.core.database.ChroGeckoDatabase
import com.chrogecko.browser.core.database.dao.BookmarkDao
import com.chrogecko.browser.core.database.dao.BookmarkFolderDao
import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class BookmarkRepository @Inject constructor(
    private val database: ChroGeckoDatabase,
    private val dao: BookmarkDao,
    private val folderDao: BookmarkFolderDao,
) {

    val bookmarks: Flow<List<BookmarkEntity>> = dao.observeAll()
    val folders: Flow<List<BookmarkFolderEntity>> = folderDao.observeAll()
    val count: Flow<Int> = dao.observeCount()

    suspend fun isBookmarked(url: String): Boolean = dao.findByUrl(url) != null

    /** Returns the bookmark state after the toggle. */
    suspend fun toggle(url: String, title: String): Boolean = database.withTransaction {
        val existing = dao.findByUrl(url)
        if (existing != null) {
            dao.deleteById(existing.id)
            false
        } else {
            dao.insert(BookmarkEntity(url = url, title = title.ifBlank { url }))
            true
        }
    }

    suspend fun remove(url: String) {
        dao.findByUrl(url)?.let { dao.deleteById(it.id) }
    }

    suspend fun removeById(id: Long) = dao.deleteById(id)

    suspend fun createFolder(name: String, parentId: Long?): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return 0
        return folderDao.insert(BookmarkFolderEntity(name = trimmed, parentId = parentId))
    }

    /** Deletes the folder, keeping its bookmarks and subfolders by moving them one level up. */
    suspend fun deleteFolder(id: Long) {
        database.withTransaction {
            val folder = folderDao.findById(id) ?: return@withTransaction
            folderDao.reparent(from = id, parentId = folder.parentId)
            dao.moveFolderContents(from = id, folderId = folder.parentId)
            folderDao.deleteById(id)
        }
    }

    suspend fun moveBookmark(id: Long, folderId: Long?) = dao.setFolder(id, folderId)

    suspend fun moveFolder(id: Long, parentId: Long?) {
        if (id == parentId) return
        var ancestorId = parentId
        while (ancestorId != null) {
            if (ancestorId == id) return
            ancestorId = folderDao.findById(ancestorId)?.parentId
        }
        folderDao.move(id, parentId)
    }

    /** Exports all bookmarks and folders in Netscape Bookmark HTML format. */
    suspend fun exportHtml(): String = BookmarkHtmlCodec.exportHtml(
        bookmarks = bookmarks.first(),
        folders = folders.first(),
    )

    /**
     * Imports bookmarks and nested folders from Netscape Bookmark HTML, skipping existing URLs
     * and creating missing folders by `(parentId, name)` case-insensitively. Returns added count.
     */
    suspend fun importHtml(html: String): Int {
        val entries = BookmarkHtmlCodec.parseHtml(html)
        if (entries.isEmpty()) return 0
        return database.withTransaction {
            val existingFolders = folders.first().toMutableList()
            val folderIndex = HashMap<Pair<Long?, String>, Long>()
            existingFolders.forEach { folder ->
                folderIndex[folder.parentId to folder.name.lowercase()] = folder.id
            }

            suspend fun ensureFolder(path: List<String>): Long? {
                var parentId: Long? = null
                for (segment in path) {
                    val trimmed = segment.trim()
                    if (trimmed.isEmpty()) continue
                    val key = parentId to trimmed.lowercase()
                    val found = folderIndex[key]
                    if (found != null) {
                        parentId = found
                    } else {
                        val createdId = folderDao.insert(
                            BookmarkFolderEntity(name = trimmed, parentId = parentId),
                        )
                        folderIndex[key] = createdId
                        parentId = createdId
                    }
                }
                return parentId
            }

            var added = 0
            val seenUrls = HashSet<String>()
            for (entry in entries) {
                if (!seenUrls.add(entry.url)) continue
                if (dao.findByUrl(entry.url) != null) continue
                val targetFolderId = ensureFolder(entry.folderPath)
                dao.insert(
                    BookmarkEntity(
                        url = entry.url,
                        title = entry.title.ifBlank { entry.url },
                        folderId = targetFolderId,
                    ),
                )
                added++
            }
            added
        }
    }
}
