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
        val relative = "schemas/com.chrogecko.browser.core.database.ChroGeckoDatabase/11.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        val schema = candidates.firstOrNull { it.isFile }
        assertTrue(
            "Room schema 11.json not found; checked ${candidates.joinToString { it.path }}",
            schema != null,
        )
        val text = schema!!.readText()
        assertTrue("schema must declare version 11", text.contains("\"version\": 11"))
        assertTrue("schema must contain the tabs table", text.contains("\"tableName\": \"tabs\""))
        assertTrue("schema must contain the logins table", text.contains("\"tableName\": \"logins\""))
    }
}
