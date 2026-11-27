package com.chrogecko.browser.feature.darkmode

import com.chrogecko.browser.core.database.entity.DarkModeMode
import com.chrogecko.browser.core.database.entity.SiteDarkRuleEntity
import com.chrogecko.browser.core.database.repository.DarkModeRepository
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeEvent
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import com.chrogecko.browser.core.preferences.AppPreferences
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class DarkModeEngine(val id: String) {
    INVERT("invert"),
    DYNAMIC("dynamic"),
    ;

    companion object {
        fun fromId(id: String): DarkModeEngine =
            entries.firstOrNull { it.id == id } ?: INVERT
    }
}

data class DarkPageStatus(
    val url: String,
    val host: String,
    val mode: String,
    val pageWasDark: Boolean,
)

/**
 * Tier 1 (Gecko `prefers-color-scheme`) is driven by [AppPreferences]; tier 2 rules and engine
 * are pushed to `sys_darkmode`, which applies them live to every open tab.
 */
@Singleton
class DarkModeFeature @Inject constructor(
    private val preferences: AppPreferences,
    private val repository: DarkModeRepository,
    private val bridge: NativeMessagingBridge,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _status = MutableStateFlow<Map<Long, DarkPageStatus>>(emptyMap())
    val status: StateFlow<Map<Long, DarkPageStatus>> = _status.asStateFlow()

    val enabled = preferences.forceDark
    val engine = preferences.darkEngine

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                preferences.forceDark,
                preferences.darkEngine,
                repository.rules,
            ) { forceDark, engineId, rules -> Triple(forceDark, engineId, rules) }
                .collect { (forceDark, engineId, rules) -> pushConfig(forceDark, engineId, rules) }
        }
        scope.launch {
            bridge.events.collect { event -> handleEvent(event) }
        }
        scope.launch {
            bridge.connectedExtensions
                .map { SystemExtensionIds.DARKMODE in it }
                .distinctUntilChanged()
                .filter { it }
                .collect { pushCurrentConfig() }
        }
    }

    /** Re-send the current policy, e.g. after a navigation when a content script may have missed it. */
    fun refresh() {
        scope.launch { pushCurrentConfig() }
    }

    suspend fun setGlobalEnabled(value: Boolean) {
        preferences.setForceDark(value)
    }

    suspend fun setEngine(value: DarkModeEngine) {
        preferences.setDarkEngine(value.id)
    }

    suspend fun modeFor(host: String): DarkModeMode = repository.modeFor(host)

    suspend fun explicitMode(host: String): DarkModeMode? = repository.explicitMode(host)

    suspend fun setMode(host: String, mode: DarkModeMode) {
        repository.setMode(host, mode)
    }

    suspend fun toggle(
        host: String,
        globallyEnabled: Boolean,
        engineId: String = DarkModeEngine.INVERT.id,
    ): DarkModeMode {
        val preferred = if (engineId == DarkModeEngine.DYNAMIC.id) {
            DarkModeMode.DYNAMIC
        } else {
            DarkModeMode.INVERT
        }
        return repository.toggle(host, globallyEnabled, preferred)
    }

    suspend fun reset(host: String) = repository.reset(host)

    fun clearStatus(tabId: Long) {
        _status.update { it - tabId }
    }

    private fun handleEvent(event: NativeEvent) {
        if (event.extensionId != SystemExtensionIds.DARKMODE) return
        when (event.type) {
            "dark/ready" -> scope.launch { pushCurrentConfig() }
            "dark/status" -> {
                val tabId = event.payload.optLong("tabId", -1L)
                if (tabId < 0) return
                val entry = DarkPageStatus(
                    url = event.payload.optString("url"),
                    host = event.payload.optString("host"),
                    mode = event.payload.optString("mode"),
                    pageWasDark = event.payload.optBoolean("pageWasDark"),
                )
                _status.update { current ->
                    val next = current.toMutableMap()
                    next[tabId] = entry
                    while (next.size > MAX_TRACKED_TABS) {
                        val oldest = next.keys.firstOrNull() ?: break
                        next.remove(oldest)
                    }
                    next
                }
            }
        }
    }

    private suspend fun pushCurrentConfig() {
        val forceDark = preferences.forceDark.first()
        val engineId = preferences.darkEngine.first()
        val rules = repository.rules.first()
        pushConfig(forceDark, engineId, rules)
    }

    private suspend fun pushConfig(
        forceDark: Boolean,
        engineId: String,
        rules: List<SiteDarkRuleEntity>,
    ) {
        val rulesJson = JSONArray()
        rules.forEach { rule ->
            rulesJson.put(
                JSONObject()
                    .put("domain", rule.domain)
                    .put("mode", rule.mode.name.lowercase()),
            )
        }
        val payload = JSONObject()
            .put("type", "dark/config")
            .put("enabled", forceDark)
            .put("engine", if (engineId == DarkModeEngine.DYNAMIC.id) "dynamic" else "invert")
            .put("rules", rulesJson)
        withContext(Dispatchers.Main.immediate) {
            bridge.post(SystemExtensionIds.DARKMODE, payload)
        }
    }

    companion object {
        private const val MAX_TRACKED_TABS = 64

        /** Extracts a lowercase host for per-site rules. */
        fun hostOf(url: String): String? = runCatching {
            URI(url).host?.lowercase()?.trimEnd('.')
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
