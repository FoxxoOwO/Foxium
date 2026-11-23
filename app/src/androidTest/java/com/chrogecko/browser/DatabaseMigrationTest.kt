package com.chrogecko.browser

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrogecko.browser.core.database.ChroGeckoDatabase
import com.chrogecko.browser.di.DatabaseModule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Creates a database with the version-1 schema by hand, inserts one row in every original table,
 * then runs the complete migration chain and checks that data and the new schema survive.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private fun createV1Database(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE tab_groups (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, " +
                    "color INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE tabs (id TEXT PRIMARY KEY NOT NULL, url TEXT, title TEXT NOT NULL, " +
                    "faviconUrl TEXT, groupId TEXT, isPrivate INTEGER NOT NULL, " +
                    "sortOrder INTEGER NOT NULL, lastActiveAt INTEGER NOT NULL, " +
                    "FOREIGN KEY(groupId) REFERENCES tab_groups(id) ON DELETE SET NULL)",
            )
            db.execSQL("CREATE INDEX index_tabs_groupId ON tabs (groupId)")
            db.execSQL("CREATE INDEX index_tabs_isPrivate ON tabs (isPrivate)")
            db.execSQL(
                "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_bookmarks_url ON bookmarks (url)")
            db.execSQL(
                "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "url TEXT NOT NULL, title TEXT NOT NULL, visitedAt INTEGER NOT NULL, " +
                    "visitCount INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_history_url ON history (url)")
            db.execSQL("CREATE INDEX index_history_visitedAt ON history (visitedAt)")
            db.execSQL(
                "CREATE TABLE site_dark_rules (domain TEXT PRIMARY KEY NOT NULL, " +
                    "mode TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE userscripts (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                    "namespace TEXT NOT NULL, version TEXT NOT NULL, description TEXT NOT NULL, " +
                    "matchPatterns TEXT NOT NULL, includePatterns TEXT NOT NULL, " +
                    "excludePatterns TEXT NOT NULL, runAt TEXT NOT NULL, grants TEXT NOT NULL, " +
                    "requires TEXT NOT NULL, resources TEXT NOT NULL, source TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL, sourceUrl TEXT, updateUrl TEXT, " +
                    "installedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX index_userscripts_namespace_name ON userscripts (namespace, name)")
            db.execSQL("INSERT INTO tab_groups VALUES ('g1', 'Group', -1, 1)")
            db.execSQL(
                "INSERT INTO tabs VALUES ('t1', 'https://example.com', 'Example', NULL, 'g1', 0, 0, 1)",
            )
            db.execSQL("INSERT INTO bookmarks VALUES (1, 'https://example.com', 'Example', 1)")
            db.execSQL("INSERT INTO history VALUES (1, 'https://example.com', 'Example', 1, 1)")
            db.execSQL("INSERT INTO site_dark_rules VALUES ('example.com', 'INVERT', 1)")
            db.execSQL(
                "INSERT INTO userscripts VALUES ('s1', 'Test', '', '', '', " +
                    "'[\"https://example.com/*\"]', '[]', '[]', 'document-idle', '[]', '[]', " +
                    "'{}', 'console.log(1);', 1, NULL, NULL, 1, 1)",
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    @Test
    fun fullMigrationChainPreservesDataAndCreatesEveryTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration_test.db"
        context.deleteDatabase(name)
        val file = File(context.getDatabasePath(name).path)
        file.parentFile?.mkdirs()
        createV1Database(file)

        val database = Room.databaseBuilder(context, ChroGeckoDatabase::class.java, name)
            .addMigrations(*DatabaseModule.migrations())
            .build()
        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(12, migrated.version)

            val tables = mutableSetOf<String>()
            migrated.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            listOf(
                "tabs", "tab_groups", "bookmarks", "bookmark_folders", "history",
                "site_dark_rules", "site_rules", "text_snippets", "userscripts",
                "downloads", "favicons", "logins", "addresses", "cards",
            ).forEach { assertTrue("missing table: $it", tables.contains(it)) }

            fun columnExists(table: String, column: String): Boolean {
                migrated.query("PRAGMA table_info($table)").use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) return true
                    }
                }
                return false
            }
            assertTrue(columnExists("tabs", "sessionState"))
            assertTrue(columnExists("bookmarks", "folderId"))
            assertTrue(columnExists("site_rules", "readerMode"))
            assertTrue(columnExists("downloads", "downloadedBytes"))

            fun count(table: String): Int = migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, count("tabs"))
            assertEquals(1, count("tab_groups"))
            assertEquals(1, count("bookmarks"))
            assertEquals(1, count("history"))
            assertEquals(1, count("site_dark_rules"))
            assertEquals(1, count("userscripts"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
