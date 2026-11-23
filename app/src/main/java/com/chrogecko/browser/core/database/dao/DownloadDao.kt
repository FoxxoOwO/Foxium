package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun findById(id: Long): DownloadEntity?

    @Insert
    suspend fun insert(download: DownloadEntity): Long

    @Query("UPDATE downloads SET status = 1, sizeBytes = :size, localPath = :path WHERE id = :id")
    suspend fun markCompleted(id: Long, size: Long, path: String)

    @Query("UPDATE downloads SET downloadedBytes = :written, sizeBytes = :total WHERE id = :id")
    suspend fun markProgress(id: Long, written: Long, total: Long)

    @Query("UPDATE downloads SET status = 3 WHERE id = :id")
    suspend fun markCancelled(id: Long)

    @Query("UPDATE downloads SET status = 2, error = :message WHERE id = :id")
    suspend fun markFailed(id: Long, message: String)

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
