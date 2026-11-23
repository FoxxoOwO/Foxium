package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved login. [secret] holds the AES/GCM payload of the password, never the plaintext,
 * so a database dump alone does not leak credentials.
 */
@Entity(tableName = "logins", indices = [Index("host"), Index(value = ["host", "username"], unique = true)])
data class LoginEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val username: String,
    val secret: ByteArray,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoginEntity) return false
        return id == other.id &&
            host == other.host &&
            username == other.username &&
            secret.contentEquals(other.secret) &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + secret.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}
