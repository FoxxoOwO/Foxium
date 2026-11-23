package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_rules")
data class SiteRuleEntity(
    @PrimaryKey val host: String,
    val blockJavascript: Boolean = false,
    val desktopSite: Boolean = false,
    val readerMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
