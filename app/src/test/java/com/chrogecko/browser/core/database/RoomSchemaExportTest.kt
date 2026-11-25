package com.chrogecko.browser.core.database

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Room schema export is the precondition for verifying future migrations. This guards against
 * accidentally turning exportSchema back off or moving the KSP schema location.
 */
class RoomSchemaExportTest {

    @Test
    fun currentSchemaIsExported() {
        // @Database has CLASS retention, so the version cannot be read by reflection. Read it from
        // the source instead: this keeps the guard tied to the actual current database version.
        val sourceCandidates = listOf(
            File("src/main/java/com/chrogecko/browser/core/database/ChroGeckoDatabase.kt"),
            File("app/src/main/java/com/chrogecko/browser/core/database/ChroGeckoDatabase.kt"),
            File("../app/src/main/java/com/chrogecko/browser/core/database/ChroGeckoDatabase.kt"),
        )
        val source = sourceCandidates.firstOrNull { it.isFile }
            ?: error("ChroGeckoDatabase source not found; checked ${sourceCandidates.joinToString { it.path }}")
        val version = Regex("""version\s*=\s*(\d+)""")
            .find(source.readText())?.groupValues?.get(1)
            ?: error("Database version not found in ${source.path}")
        val relative = "schemas/com.chrogecko.browser.core.database.ChroGeckoDatabase/$version.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        val schema = candidates.firstOrNull { it.isFile }
        assertTrue(
            "Room schema $version.json not found; checked ${candidates.joinToString { it.path }}",
            schema != null,
        )
        val text = schema!!.readText()
        assertTrue("schema must declare version $version", text.contains("\"version\": $version"))
        assertTrue("schema must contain the tabs table", text.contains("\"tableName\": \"tabs\""))
        assertTrue("schema must contain the logins table", text.contains("\"tableName\": \"logins\""))
    }
}
