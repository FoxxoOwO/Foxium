package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "userscripts",
    indices = [Index(value = ["namespace", "name"], unique = true)],
)
data class UserscriptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val namespace: String = "",
    val version: String = "",
    val description: String = "",
    val matchPatterns: List<String> = emptyList(),
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
    val runAt: String = "document-idle",
    val grants: List<String> = emptyList(),
    val requires: List<String> = emptyList(),
    val resources: Map<String, String> = emptyMap(),
    val source: String,
    val enabled: Boolean = true,
    val sourceUrl: String? = null,
    val updateUrl: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
