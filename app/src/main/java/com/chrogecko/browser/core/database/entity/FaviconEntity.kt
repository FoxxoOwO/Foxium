package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A site icon keyed by host. GeckoView has no favicon API, so the bytes come from the page's own
 * `<link rel=icon>` (or `/favicon.ico`) and are kept here to survive restarts.
 */
@Entity(tableName = "favicons")
data class FaviconEntity(
    @PrimaryKey val host: String,
    val iconUrl: String,
    val bytes: ByteArray,
    val fetchedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaviconEntity) return false
        return host == other.host &&
            iconUrl == other.iconUrl &&
            bytes.contentEquals(other.bytes) &&
            fetchedAt == other.fetchedAt
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + iconUrl.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + fetchedAt.hashCode()
        return result
    }
}
