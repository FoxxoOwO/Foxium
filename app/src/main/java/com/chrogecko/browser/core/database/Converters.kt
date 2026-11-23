package com.chrogecko.browser.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room converters for the list/map fields stored inside userscript metadata. */
class Converters {

    @TypeConverter
    fun listToJson(value: List<String>?): String = json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun jsonToList(value: String?): List<String> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
        }

    @TypeConverter
    fun mapToJson(value: Map<String, String>?): String = json.encodeToString(value ?: emptyMap())

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString<Map<String, String>>(value) }
                .getOrDefault(emptyMap())
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
