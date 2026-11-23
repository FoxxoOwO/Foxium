package com.chrogecko.browser.feature.reader

import com.chrogecko.browser.core.database.repository.SiteRuleRepository
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Drives reading mode through the bundled `sys_reader` extension: readiness and the active flag
 * come back per URL, and a site rule marked for reader mode turns the view on automatically.
 */
@Singleton
class ReaderModeFeature @Inject constructor(
    private val bridge: NativeMessagingBridge,
    private val siteRules: SiteRuleRepository,
) {

    data class ReaderState(
        val url: String = "",
        val available: Boolean = false,
        val active: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _states = MutableStateFlow<Map<String, ReaderState>>(emptyMap())
    val states: StateFlow<Map<String, ReaderState>> = _states.asStateFlow()

    @Volatile
    private var autoHosts: Set<String> = emptySet()

    // Gecko assigns the tab ids, so they are learned from the state reports of each page.
    private val tabIds = ConcurrentHashMap<String, Int>()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            siteRules.rules.collect { rules ->
                autoHosts = rules
                    .filter { it.readerMode }
                    .map { it.host.trim().lowercase().removePrefix("www.") }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        }
        scope.launch {
            bridge.events.collect { event ->
                if (event.extensionId != SystemExtensionIds.READER) return@collect
                if (event.type != "reader/state") return@collect
                val payload = event.payload
                val url = payload.optString("url")
                if (url.isBlank()) return@collect
                payload.optInt("tabId", -1).takeIf { it >= 0 }?.let { tabId ->
                    if (tabIds.size >= MAX_TRACKED_URLS && !tabIds.containsKey(url)) {
                        tabIds.keys.firstOrNull()?.let(tabIds::remove)
                    }
                    tabIds[url] = tabId
                }
                val available = payload.optBoolean("available", false)
                val active = payload.optBoolean("active", false)
                _states.update { current ->
                    val next = LinkedHashMap(current)
                    next.remove(url)
                    next[url] = ReaderState(url, available, active)
                    while (next.size > MAX_TRACKED_URLS) {
                        val oldest = next.keys.firstOrNull() ?: break
                        next.remove(oldest)
                    }
                    next
                }
                val host = payload.optString("host").trim().lowercase().removePrefix("www.")
                if (available && !active && host.isNotBlank() && host in autoHosts) {
                    post("reader/activate", payload.optInt("tabId", -1))
                }
            }
        }
    }

    fun toggle(url: String) = post("reader/toggle", tabIds[url] ?: -1)

    fun stateFor(url: String): ReaderState = _states.value[url] ?: ReaderState(url)

    private fun post(type: String, tabId: Int = -1) {
        val payload = JSONObject().put("type", type)
        if (tabId >= 0) payload.put("tabId", tabId)
        bridge.post(SystemExtensionIds.READER, payload)
    }

    private companion object {
        const val MAX_TRACKED_URLS = 100
    }
}
