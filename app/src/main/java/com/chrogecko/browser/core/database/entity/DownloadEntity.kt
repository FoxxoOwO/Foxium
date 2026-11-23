package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: Int = STATUS_RUNNING,
    val localPath: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_RUNNING = 0
        const val STATUS_COMPLETED = 1
        const val STATUS_FAILED = 2
        const val STATUS_CANCELLED = 3
    }
}
