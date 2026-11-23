package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.FaviconEntity

@Dao
interface FaviconDao {

    @Query("SELECT * FROM favicons")
    suspend fun all(): List<FaviconEntity>

    @Query("SELECT * FROM favicons ORDER BY fetchedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<FaviconEntity>

    @Query("SELECT * FROM favicons WHERE host = :host LIMIT 1")
    suspend fun findByHost(host: String): FaviconEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: FaviconEntity)

    @Query("DELETE FROM favicons WHERE host NOT IN (SELECT host FROM favicons ORDER BY fetchedAt DESC LIMIT 300)")
    suspend fun trim()
}
