package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import com.chrogecko.browser.core.database.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    /** Bulk lookup for import: avoids one SELECT per imported URL. */
    @Query("SELECT url FROM bookmarks")
    suspend fun allUrls(): List<String>

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE bookmarks SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE bookmarks SET folderId = :folderId WHERE folderId = :from")
    suspend fun moveFolderContents(from: Long, folderId: Long?)
}

@Dao
interface BookmarkFolderDao {

    @Query("SELECT * FROM bookmark_folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookmarkFolderEntity>>

    @Query("SELECT * FROM bookmark_folders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BookmarkFolderEntity?

    @Query("SELECT * FROM bookmark_folders WHERE (:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId ORDER BY name COLLATE NOCASE ASC")
    suspend fun childrenOf(parentId: Long?): List<BookmarkFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: BookmarkFolderEntity): Long

    @Query("UPDATE bookmark_folders SET parentId = :parentId WHERE id = :id")
    suspend fun move(id: Long, parentId: Long?)

    @Query("UPDATE bookmark_folders SET parentId = :parentId WHERE parentId = :from")
    suspend fun reparent(from: Long, parentId: Long?)

    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 100",
    )
    fun search(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Query(
        "INSERT INTO history(url, title, visitedAt, visitCount) " +
            "VALUES(:url, :title, :visitedAt, 1) " +
            "ON CONFLICT(url) DO UPDATE SET " +
            "title = CASE WHEN :title != '' THEN :title ELSE title END, " +
            "visitedAt = :visitedAt, " +
            "visitCount = visitCount + 1",
    )
    suspend fun recordVisit(url: String, title: String, visitedAt: Long)

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Update
    suspend fun update(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE visitedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
