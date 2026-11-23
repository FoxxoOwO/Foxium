package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.UserscriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserscriptDao {

    @Query("SELECT * FROM userscripts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<UserscriptEntity>>

    @Query("SELECT * FROM userscripts WHERE enabled = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeEnabled(): Flow<List<UserscriptEntity>>

    @Query("SELECT * FROM userscripts WHERE enabled = 1")
    suspend fun enabledScripts(): List<UserscriptEntity>

    @Query("SELECT * FROM userscripts WHERE id = :id")
    suspend fun findById(id: String): UserscriptEntity?

    @Query("SELECT * FROM userscripts WHERE namespace = :namespace AND name = :name LIMIT 1")
    suspend fun findByNamespaceAndName(namespace: String, name: String): UserscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: UserscriptEntity)

    @Query("UPDATE userscripts SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM userscripts WHERE id = :id")
    suspend fun deleteById(id: String)
}
