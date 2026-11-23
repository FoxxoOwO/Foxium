package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.SiteDarkRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDarkRuleDao {

    @Query("SELECT * FROM site_dark_rules ORDER BY domain ASC")
    fun observeAll(): Flow<List<SiteDarkRuleEntity>>

    @Query("SELECT * FROM site_dark_rules WHERE domain = :domain")
    suspend fun findByDomain(domain: String): SiteDarkRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SiteDarkRuleEntity)

    @Query("DELETE FROM site_dark_rules WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM site_dark_rules")
    suspend fun clear()
}
