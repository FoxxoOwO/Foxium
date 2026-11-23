package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chrogecko.browser.core.database.entity.TabEntity
import com.chrogecko.browser.core.database.entity.TabGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {

    @Query("SELECT * FROM tabs ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isPrivate = :isPrivate ORDER BY sortOrder ASC")
    fun observeByProfile(isPrivate: Boolean): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY lastActiveAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE groupId = :groupId ORDER BY sortOrder ASC")
    fun observeGroup(groupId: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun findById(id: String): TabEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tabs WHERE isPrivate = :isPrivate")
    suspend fun maxSortOrder(isPrivate: Boolean): Int

    @Query("SELECT COUNT(*) FROM tabs WHERE isPrivate = :isPrivate")
    suspend fun count(isPrivate: Boolean): Int

    @Query("SELECT * FROM tabs WHERE isPrivate = 0 ORDER BY sortOrder ASC")
    suspend fun normalTabs(): List<TabEntity>

    @Query("UPDATE tabs SET url = :url, title = :title WHERE id = :id")
    suspend fun updateContent(id: String, url: String?, title: String)

    @Query("UPDATE tabs SET sessionState = :state WHERE id = :id")
    suspend fun updateSessionState(id: String, state: String)

    @Query("UPDATE tabs SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("UPDATE tabs SET lastActiveAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("DELETE FROM tabs WHERE isPrivate = 1")
    suspend fun clearPrivate()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("UPDATE tabs SET groupId = :groupId WHERE id = :tabId")
    suspend fun assignGroup(tabId: String, groupId: String?)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tabs WHERE isPrivate = :isPrivate")
    suspend fun deleteByProfile(isPrivate: Boolean)

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceNormalTabs(tabs: List<TabEntity>) {
        deleteByProfile(false)
        upsertAll(tabs)
    }

    @Transaction
    suspend fun reorderTabs(ids: List<String>) {
        ids.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }
}

@Dao
interface TabGroupDao {

    @Query("SELECT * FROM tab_groups ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TabGroupEntity>>

    @Query("SELECT * FROM tab_groups")
    suspend fun allGroups(): List<TabGroupEntity>

    @Query("SELECT * FROM tab_groups WHERE id = :id")
    suspend fun findById(id: String): TabGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: TabGroupEntity)

    @Query("UPDATE tab_groups SET title = :title, color = :color WHERE id = :id")
    suspend fun update(id: String, title: String, color: Int)

    @Query("DELETE FROM tab_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
