package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved postal address offered on address forms. */
@Entity(tableName = "addresses")
data class AddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val name: String,
    val street: String,
    val city: String,
    val postal: String,
    val country: String,
    val phone: String,
    val email: String,
    val updatedAt: Long,
)

/** A saved payment card; the number is encrypted by [com.chrogecko.browser.core.security.SecretBox]. */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val nameOnCard: String,
    val number: ByteArray,
    val expiry: String,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardEntity) return false
        return id == other.id &&
            label == other.label &&
            nameOnCard == other.nameOnCard &&
            number.contentEquals(other.number) &&
            expiry == other.expiry &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + nameOnCard.hashCode()
        result = 31 * result + number.contentHashCode()
        result = 31 * result + expiry.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
