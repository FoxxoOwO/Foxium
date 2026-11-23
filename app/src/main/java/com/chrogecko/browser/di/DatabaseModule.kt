package com.chrogecko.browser.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chrogecko.browser.core.database.ChroGeckoDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS downloads (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, " +
                    "sizeBytes INTEGER NOT NULL, status INTEGER NOT NULL, localPath TEXT, " +
                    "error TEXT, createdAt INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE downloads ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favicons (" +
                    "host TEXT NOT NULL PRIMARY KEY, iconUrl TEXT NOT NULL, " +
                    "bytes BLOB NOT NULL, fetchedAt INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS logins (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "host TEXT NOT NULL, username TEXT NOT NULL, secret BLOB NOT NULL, " +
                    "updatedAt INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_logins_host ON logins (host)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_logins_host_username " +
                    "ON logins (host, username)",
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS addresses (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "label TEXT NOT NULL, name TEXT NOT NULL, street TEXT NOT NULL, " +
                    "city TEXT NOT NULL, postal TEXT NOT NULL, country TEXT NOT NULL, " +
                    "phone TEXT NOT NULL, email TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS cards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "label TEXT NOT NULL, nameOnCard TEXT NOT NULL, number BLOB NOT NULL, " +
                    "expiry TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tabs ADD COLUMN sessionState TEXT")
        }
    }

    /** Exposed for the migration smoke test; production uses [provideDatabase]. */
    fun migrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12,
    )

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChroGeckoDatabase =
        Room.databaseBuilder(context, ChroGeckoDatabase::class.java, ChroGeckoDatabase.NAME)
            .addMigrations(*migrations())
            .build()

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN folderId INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmark_folders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, parentId INTEGER, createdAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_bookmark_folders_parentId " +
                    "ON bookmark_folders (parentId)",
            )
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_bookmarks_folderId ON bookmarks (folderId)",
            )
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE site_rules ADD COLUMN readerMode INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS site_rules (" +
                    "host TEXT NOT NULL PRIMARY KEY, " +
                    "blockJavascript INTEGER NOT NULL, desktopSite INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL)",
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS text_snippets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "trigger TEXT NOT NULL, expansion TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_text_snippets_trigger " +
                    "ON text_snippets (trigger)",
            )
        }
    }

    @Provides
    fun provideTextSnippetDao(database: ChroGeckoDatabase): TextSnippetDao =
        database.textSnippetDao()

    @Provides
    fun provideBookmarkFolderDao(database: ChroGeckoDatabase): BookmarkFolderDao =
        database.bookmarkFolderDao()

    @Provides
    fun provideSiteRuleDao(database: ChroGeckoDatabase): SiteRuleDao = database.siteRuleDao()

    @Provides
    fun provideTabDao(database: ChroGeckoDatabase): TabDao = database.tabDao()

    @Provides
    fun provideTabGroupDao(database: ChroGeckoDatabase): TabGroupDao = database.tabGroupDao()

    @Provides
    fun provideBookmarkDao(database: ChroGeckoDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHistoryDao(database: ChroGeckoDatabase): HistoryDao = database.historyDao()

    @Provides
    fun provideSiteDarkRuleDao(database: ChroGeckoDatabase): SiteDarkRuleDao = database.siteDarkRuleDao()

    @Provides
    fun provideUserscriptDao(database: ChroGeckoDatabase): UserscriptDao = database.userscriptDao()

    @Provides
    fun provideDownloadDao(database: ChroGeckoDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideAutofillDao(database: ChroGeckoDatabase): AutofillDao = database.autofillDao()

    @Provides
    fun provideFaviconDao(database: ChroGeckoDatabase): FaviconDao = database.faviconDao()

    @Provides
    fun provideLoginDao(database: ChroGeckoDatabase): LoginDao = database.loginDao()
}
