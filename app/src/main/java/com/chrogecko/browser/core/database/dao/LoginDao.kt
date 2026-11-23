package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.LoginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoginDao {

    @Query("SELECT * FROM logins WHERE host = :host ORDER BY updatedAt DESC")
    suspend fun forHost(host: String): List<LoginEntity>

    @Query("SELECT * FROM logins ORDER BY host ASC, updatedAt DESC")
    fun observeAll(): Flow<List<LoginEntity>>

    @Query("SELECT * FROM logins WHERE host = :host AND username = :username LIMIT 1")
    suspend fun find(host: String, username: String): LoginEntity?

    @Query("SELECT * FROM logins WHERE id = :id")
    suspend fun byId(id: Long): LoginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(login: LoginEntity): Long

    @Query("DELETE FROM logins WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM logins")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM logins")
    suspend fun count(): Int
}
