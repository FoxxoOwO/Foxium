package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.SiteRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteRuleDao {

    @Query("SELECT * FROM site_rules ORDER BY host ASC")
    fun observeAll(): Flow<List<SiteRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SiteRuleEntity)

    @Query("DELETE FROM site_rules WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM site_rules")
    suspend fun clear()
}
