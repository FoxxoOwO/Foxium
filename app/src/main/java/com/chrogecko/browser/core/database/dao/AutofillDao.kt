package com.chrogecko.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutofillDao {

    @Query("SELECT * FROM addresses ORDER BY updatedAt DESC")
    fun observeAddresses(): Flow<List<AddressEntity>>

    @Query("SELECT * FROM cards ORDER BY updatedAt DESC")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM addresses WHERE id = :id")
    suspend fun address(id: Long): AddressEntity?

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: Long): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddress(address: AddressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity): Long

    @Query("DELETE FROM addresses WHERE id = :id")
    suspend fun deleteAddress(id: Long)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long)

    @Query("DELETE FROM addresses")
    suspend fun clearAddresses()

    @Query("DELETE FROM cards")
    suspend fun clearCards()
}
