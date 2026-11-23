package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A text expander entry: typing [trigger] followed by space, Enter or Tab inserts [expansion]. */
@Entity(tableName = "text_snippets", indices = [Index(value = ["trigger"], unique = true)])
data class TextSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val expansion: String,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)
