package com.chrogecko.browser.feature.search

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** One row in the omnibox suggestion list. */
data class SearchSuggestion(val text: String, val url: String, val kind: Kind) {
    enum class Kind { QUERY, HISTORY, BOOKMARK }
}

/** Fetches completion suggestions from the selected engine's public suggest endpoint. */
@Singleton
class SearchSuggestionRepository @Inject constructor(client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private val suggestClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun engineSuggestions(query: String, engineId: String): List<String> =
        withContext(Dispatchers.IO) {
            val endpoint = endpointFor(engineId, query) ?: return@withContext emptyList()
            val body = try {
                fetchBody(endpoint)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                ""
            }
            if (body.isEmpty()) emptyList() else parse(body).take(MAX_SUGGESTIONS)
        }

    private suspend fun fetchBody(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = suggestClient.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume("")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.use { res ->
                        if (res.isSuccessful) res.body?.string().orEmpty() else ""
                    }
                    if (continuation.isActive) {
                        continuation.resume(body)
                    }
                }
            },
        )
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
