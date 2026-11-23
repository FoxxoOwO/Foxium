package com.chrogecko.browser.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tab_groups")
data class TabGroupEntity(
    @PrimaryKey val id: String,
    val title: String,
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tabs",
    foreignKeys = [
        ForeignKey(
            entity = TabGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId"), Index("isPrivate")],
)
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String? = null,
    val title: String = "",
    val faviconUrl: String? = null,
    val groupId: String? = null,
    val isPrivate: Boolean = false,
    val sortOrder: Int = 0,
    val lastActiveAt: Long = System.currentTimeMillis(),
    /** Serialized Gecko session history so a restart keeps back/forward and scroll position. */
    val sessionState: String? = null,
)
