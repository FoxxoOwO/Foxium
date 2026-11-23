package com.chrogecko.browser.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chrogecko.browser.core.database.dao.AutofillDao
import com.chrogecko.browser.core.database.dao.BookmarkDao
import com.chrogecko.browser.core.database.dao.BookmarkFolderDao
import com.chrogecko.browser.core.database.dao.DownloadDao
import com.chrogecko.browser.core.database.dao.FaviconDao
import com.chrogecko.browser.core.database.dao.HistoryDao
import com.chrogecko.browser.core.database.dao.LoginDao
import com.chrogecko.browser.core.database.dao.SiteDarkRuleDao
import com.chrogecko.browser.core.database.dao.SiteRuleDao
import com.chrogecko.browser.core.database.dao.TextSnippetDao
import com.chrogecko.browser.core.database.dao.TabDao
import com.chrogecko.browser.core.database.dao.TabGroupDao
import com.chrogecko.browser.core.database.dao.UserscriptDao
import com.chrogecko.browser.core.database.entity.AddressEntity
import com.chrogecko.browser.core.database.entity.BookmarkEntity
import com.chrogecko.browser.core.database.entity.BookmarkFolderEntity
import com.chrogecko.browser.core.database.entity.CardEntity
import com.chrogecko.browser.core.database.entity.DownloadEntity
import com.chrogecko.browser.core.database.entity.FaviconEntity
import com.chrogecko.browser.core.database.entity.HistoryEntity
import com.chrogecko.browser.core.database.entity.LoginEntity
import com.chrogecko.browser.core.database.entity.SiteDarkRuleEntity
import com.chrogecko.browser.core.database.entity.SiteRuleEntity
import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import com.chrogecko.browser.core.database.entity.TabEntity
import com.chrogecko.browser.core.database.entity.TabGroupEntity
import com.chrogecko.browser.core.database.entity.UserscriptEntity

@Database(
    entities = [
        TabEntity::class,
        TabGroupEntity::class,
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        HistoryEntity::class,
        SiteDarkRuleEntity::class,
        SiteRuleEntity::class,
        TextSnippetEntity::class,
        UserscriptEntity::class,
        DownloadEntity::class,
        FaviconEntity::class,
        LoginEntity::class,
        AddressEntity::class,
        CardEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChroGeckoDatabase : RoomDatabase() {

    abstract fun tabDao(): TabDao

    abstract fun tabGroupDao(): TabGroupDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun bookmarkFolderDao(): BookmarkFolderDao

    abstract fun historyDao(): HistoryDao

    abstract fun siteDarkRuleDao(): SiteDarkRuleDao

    abstract fun siteRuleDao(): SiteRuleDao

    abstract fun textSnippetDao(): TextSnippetDao

    abstract fun userscriptDao(): UserscriptDao

    abstract fun downloadDao(): DownloadDao

    abstract fun faviconDao(): FaviconDao

    abstract fun loginDao(): LoginDao

    abstract fun autofillDao(): AutofillDao

    companion object {
        const val NAME = "chrogecko.db"
    }
}
