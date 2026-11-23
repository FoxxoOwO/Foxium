package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextSnippetDao {

    @Query("SELECT * FROM text_snippets ORDER BY trigger ASC")
    fun observeAll(): Flow<List<TextSnippetEntity>>

    @Query("SELECT * FROM text_snippets WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TextSnippetEntity?

    @Query("SELECT * FROM text_snippets WHERE trigger = :trigger LIMIT 1")
    suspend fun findByTrigger(trigger: String): TextSnippetEntity?

    @Query("UPDATE text_snippets SET expansion = :expansion, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateExpansion(id: Long, expansion: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: TextSnippetEntity): Long

    @Query("UPDATE text_snippets SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM text_snippets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM text_snippets")
    suspend fun clear()
}
