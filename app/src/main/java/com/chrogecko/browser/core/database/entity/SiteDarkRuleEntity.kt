package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DarkModeMode {
    OFF,
    INVERT,
    DYNAMIC,
}

@Entity(tableName = "site_dark_rules")
data class SiteDarkRuleEntity(
    @PrimaryKey val domain: String,
    val mode: DarkModeMode,
    val updatedAt: Long = System.currentTimeMillis(),
)
