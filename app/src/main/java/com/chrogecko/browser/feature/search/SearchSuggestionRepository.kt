package com.chrogecko.browser.feature.search

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** One row in the omnibox suggestion list. */
data class SearchSuggestion(val text: String, val url: String, val kind: Kind) {
    enum class Kind { QUERY, HISTORY, BOOKMARK }
}

/** Fetches completion suggestions from the selected engine's public suggest endpoint. */
@Singleton
class SearchSuggestionRepository @Inject constructor(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun engineSuggestions(query: String, engineId: String): List<String> =
        withContext(Dispatchers.IO) {
            val endpoint = endpointFor(engineId, query) ?: return@withContext emptyList()
            runCatching {
                val body = client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    response.body?.string().orEmpty()
                }
                parse(body)
            }.getOrDefault(emptyList()).take(MAX_SUGGESTIONS)
        }

    private fun parse(body: String): List<String> = runCatching {
        val array = json.parseToJsonElement(body).jsonArray
        if (array.size < 2) return@runCatching emptyList()
        array[1].jsonArray.mapNotNull { element ->
            runCatching { element.jsonPrimitive.content }.getOrNull()
        }
    }.getOrDefault(emptyList())

    internal companion object {
        const val MAX_SUGGESTIONS = 5

        fun endpointFor(engineId: String, query: String): String? {
            val encoded = URLEncoder.encode(query, "UTF-8")
            return when (engineId) {
                "google" -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
                "duckduckgo" -> "https://duckduckgo.com/ac/?q=$encoded&type=list"
                else -> null
            }
        }
    }
}
