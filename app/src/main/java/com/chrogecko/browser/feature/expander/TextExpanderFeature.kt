package com.chrogecko.browser.feature.expander

import com.chrogecko.browser.core.database.entity.TextSnippetEntity
import com.chrogecko.browser.core.database.repository.TextSnippetRepository
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import com.chrogecko.browser.core.preferences.AppPreferences
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns the snippet table and mirrors it into `sys_expander`, which expands triggers inside text
 * fields of any page.
 */
@Singleton
class TextExpanderFeature @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: TextSnippetRepository,
    private val bridge: NativeMessagingBridge,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    val enabled: Flow<Boolean> = preferences.textExpanderEnabled
    val snippets: Flow<List<TextSnippetEntity>> = repository.snippets

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(preferences.textExpanderEnabled, repository.snippets) { on, list -> on to list }
                .collect { (on, list) -> pushConfig(on, list) }
        }
        scope.launch {
            bridge.events.collect { event ->
                if (event.extensionId == SystemExtensionIds.EXPANDER && event.type == "expander/ready") {
                    pushCurrentConfig()
                }
            }
        }
        scope.launch {
            bridge.connectedExtensions
                .map { SystemExtensionIds.EXPANDER in it }
                .distinctUntilChanged()
                .filter { it }
                .collect { pushCurrentConfig() }
        }
    }

    suspend fun setEnabled(value: Boolean) = preferences.setTextExpanderEnabled(value)

    suspend fun save(id: Long, trigger: String, expansion: String): Long =
        repository.save(id, trigger, expansion)

    suspend fun setSnippetEnabled(id: Long, value: Boolean) = repository.setEnabled(id, value)

    suspend fun delete(id: Long) = repository.delete(id)

    private suspend fun pushCurrentConfig() {
        pushConfig(preferences.textExpanderEnabled.first(), repository.snippets.first())
    }

    private suspend fun pushConfig(enabled: Boolean, list: List<TextSnippetEntity>) {
        val array = JSONArray()
        list.filter { it.enabled }.forEach { snippet ->
            array.put(
                JSONObject()
                    .put("trigger", snippet.trigger)
                    .put("expansion", snippet.expansion),
            )
        }
        val payload = JSONObject()
            .put("type", "expander/config")
            .put("enabled", enabled)
            .put("snippets", array)
        withContext(Dispatchers.Main.immediate) {
            bridge.post(SystemExtensionIds.EXPANDER, payload)
        }
    }
}
