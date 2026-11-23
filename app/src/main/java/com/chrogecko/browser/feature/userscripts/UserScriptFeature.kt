package com.chrogecko.browser.feature.userscripts

import android.content.Context
import android.net.Uri
import com.chrogecko.browser.core.database.entity.UserscriptEntity
import com.chrogecko.browser.core.database.repository.UserscriptRepository
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeEvent
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class InjectedScriptStatus(
    val scriptId: String,
    val status: String,
    val error: String?,
)

data class UserScriptMenuCommand(
    val id: String,
    val label: String,
    val scriptId: String,
    val tabId: Long = -1,
)

sealed interface UpdateCheck {
    data object NoUpdateUrl : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val version: String) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * Owns the userscript lifecycle: parsing, persistence, pushing scripts to the privileged
 * `sys_userscripts` extension and routing GM_/menu events back to the UI.
 */
@Singleton
class UserScriptFeature @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: UserscriptRepository,
    private val bridge: NativeMessagingBridge,
    private val client: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private val _status = MutableStateFlow<Map<Long, List<InjectedScriptStatus>>>(emptyMap())
    val status: StateFlow<Map<Long, List<InjectedScriptStatus>>> = _status.asStateFlow()

    private val _menuCommands = MutableStateFlow<Map<String, List<UserScriptMenuCommand>>>(emptyMap())
    val menuCommands: StateFlow<Map<String, List<UserScriptMenuCommand>>> = _menuCommands.asStateFlow()

    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()

    val scripts = repository.scripts

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            repository.enabledScripts.collectLatest { pushScripts(it) }
        }
        scope.launch {
            bridge.events.collect(::handleEvent)
        }
        scope.launch {
            bridge.connectedExtensions
                .map { SystemExtensionIds.USERSCRIPTS in it }
                .distinctUntilChanged()
                .filter { it }
                .collect { pushScripts(repository.forInjection()) }
        }
    }

    suspend fun install(source: String, sourceUrl: String? = null): UserscriptEntity {
        val parsed = UserScriptParser.parse(source)
        val now = System.currentTimeMillis()
        val entity = parsed.toEntity(
            id = UUID.randomUUID().toString(),
            sourceUrl = sourceUrl,
            installedAt = now,
            updatedAt = now,
        )
        return withContext(Dispatchers.IO) { repository.install(entity) }
    }

    suspend fun installFromUri(uri: Uri): UserscriptEntity =
        install(sourceFromUri(uri), sourceUrl = uri.toString())

    suspend fun installFromUrl(url: String): UserscriptEntity =
        install(sourceFromUrl(url), sourceUrl = url)

    suspend fun sourceFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { it.readText() }
        } ?: throw UserScriptParseException("Cannot read the selected file")
    }

    suspend fun sourceFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val parsedUrl = runCatching { java.net.URI(url) }.getOrNull()
            ?: throw UserScriptParseException("Invalid URL")
        if (parsedUrl.scheme != "https") {
            throw UserScriptParseException("Only https userscript URLs are supported")
        }
        fetch(url)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        repository.setEnabled(id, enabled)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        repository.delete(id)
    }

    suspend fun saveSource(id: String, source: String): UserscriptEntity = withContext(Dispatchers.IO) {
        val existing = repository.find(id)
            ?: throw UserScriptParseException("Unknown script")
        val parsed = UserScriptParser.parse(source)
        val updated = parsed.toEntity(
            id = existing.id,
            sourceUrl = existing.sourceUrl,
            enabled = existing.enabled,
            installedAt = existing.installedAt,
            updatedAt = System.currentTimeMillis(),
        )
        repository.save(updated)
        updated
    }

    suspend fun checkForUpdates(id: String): UpdateCheck = withContext(Dispatchers.IO) {
        val existing = repository.find(id) ?: return@withContext UpdateCheck.Failed("Unknown script")
        val updateUrl = existing.updateUrl ?: existing.sourceUrl
            ?: return@withContext UpdateCheck.NoUpdateUrl
        val result = runCatching { fetch(updateUrl) }
            .getOrElse { return@withContext UpdateCheck.Failed(it.message ?: "Download failed") }
        val parsed = runCatching { UserScriptParser.parse(result) }
            .getOrElse { return@withContext UpdateCheck.Failed(it.message ?: "Invalid script") }
        if (compareVersions(parsed.version, existing.version) <= 0) {
            UpdateCheck.UpToDate
        } else {
            UpdateCheck.Available(parsed.version)
        }
    }

    suspend fun update(id: String): UserscriptEntity = withContext(Dispatchers.IO) {
        val existing = repository.find(id)
            ?: throw UserScriptParseException("Unknown script")
        val updateUrl = existing.updateUrl ?: existing.sourceUrl
            ?: throw UserScriptParseException("Script has no @updateURL/@downloadURL")
        val firstParsed = UserScriptParser.parse(fetch(updateUrl))
        val isMetaStub = updateUrl.substringBefore('?').endsWith(".meta.js", ignoreCase = true)
        val bodyUrl = if (isMetaStub || (updateUrl != existing.sourceUrl && firstParsed.downloadUrl != null)) {
            firstParsed.downloadUrl ?: existing.sourceUrl ?: updateUrl
        } else {
            updateUrl
        }
        val parsed = if (bodyUrl != updateUrl) UserScriptParser.parse(fetch(bodyUrl)) else firstParsed
        val updated = parsed.toEntity(
            id = existing.id,
            sourceUrl = bodyUrl,
            enabled = existing.enabled,
            installedAt = existing.installedAt,
            updatedAt = System.currentTimeMillis(),
        )
        repository.save(updated)
        updated
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        pushScripts(repository.forInjection())
    }

    fun runMenuCommand(tabId: Long, commandId: String) {
        val payload = JSONObject()
            .put("type", "userscripts/menu-command")
            .put("tabId", tabId)
            .put("commandId", commandId)
        bridge.post(SystemExtensionIds.USERSCRIPTS, payload)
    }

    fun clearError() {
        _errors.value = null
    }

    private fun handleEvent(event: NativeEvent) {
        if (event.extensionId != SystemExtensionIds.USERSCRIPTS) return
        when (event.type) {
            "userscripts/ready" -> scope.launch { pushScripts(repository.forInjection()) }
            "userscripts/injected" -> {
                val tabId = event.payload.optLong("tabId", -1L)
                if (tabId < 0) return
                val array = event.payload.optJSONArray("scripts") ?: return
                val statuses = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    InjectedScriptStatus(
                        scriptId = item.optString("id"),
                        status = item.optString("status", "unknown"),
                        error = item.optString("error").ifBlank { null },
                    )
                }
                _status.update { it + (tabId to statuses) }
            }
            "userscripts/menu" -> {
                val tabId = event.payload.optLong("tabId", -1L)
                if (tabId < 0) return
                val url = event.payload.optString("url")
                if (url.isBlank()) return
                val array = event.payload.optJSONArray("commands") ?: JSONArray()
                val commands = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val label = item.optString("label").ifBlank { item.optString("title") }
                    UserScriptMenuCommand(
                        id = item.optString("id"),
                        label = label,
                        scriptId = item.optString("scriptId"),
                        tabId = tabId,
                    )
                }
                _menuCommands.update { current ->
                    val updated = LinkedHashMap(current)
                    updated.remove(url)
                    updated[url] = commands
                    while (updated.size > MAX_MENU_URLS) {
                        val oldestKey = updated.keys.firstOrNull() ?: break
                        updated.remove(oldestKey)
                    }
                    updated
                }
            }
            "userscripts/status" -> {
                val error = event.payload.optString("error")
                if (error.isNotBlank()) _errors.value = error
            }
        }
    }

    private suspend fun pushScripts(scripts: List<UserscriptEntity>) {
        val array = JSONArray()
        scripts.filter { it.enabled }.forEach { script ->
            array.put(
                JSONObject()
                    .put("id", script.id)
                    .put("name", script.name)
                    .put("namespace", script.namespace)
                    .put("version", script.version)
                    .put("description", script.description)
                    .put("runAt", script.runAt)
                    .put("match", JSONArray(script.matchPatterns))
                    .put("include", JSONArray(script.includePatterns))
                    .put("exclude", JSONArray(script.excludePatterns))
                    .put("grants", JSONArray(script.grants))
                    .put("requires", JSONArray(script.requires))
                    .put("resources", JSONObject(script.resources as Map<*, *>))
                    .put("source", script.source),
            )
        }
        val payload = JSONObject()
            .put("type", "userscripts/sync")
            .put("scripts", array)
        withContext(Dispatchers.Main.immediate) {
            if (!bridge.post(SystemExtensionIds.USERSCRIPTS, payload) && scripts.isNotEmpty()) {
                // The extension re-syncs on its own ready event after the port connects.
            }
        }
    }

    private fun fetch(url: String): String {
        // A userscript is executable code inside every matching page, so plaintext is never accepted
        // (an update check over http would be a remote code injection on any hostile Wi-Fi).
        val scheme = runCatching { java.net.URI(url).scheme }.getOrNull()?.lowercase()
        if (scheme != "https") {
            throw UserScriptParseException("Only https userscript URLs are supported")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Foxium/${android.os.Build.VERSION.SDK_INT}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserScriptParseException("HTTP ${response.code} while downloading script")
            }
            val body = response.body ?: throw UserScriptParseException("Empty response")
            if (body.contentLength() > MAX_SCRIPT_BYTES) {
                throw UserScriptParseException("Script is larger than 4 MB")
            }
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SCRIPT_BYTES) {
                        throw UserScriptParseException("Script is larger than 4 MB")
                    }
                    out.write(buffer, 0, read)
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun ParsedUserScript.toEntity(
        id: String,
        sourceUrl: String?,
        enabled: Boolean = true,
        installedAt: Long,
        updatedAt: Long,
    ): UserscriptEntity = UserscriptEntity(
        id = id,
        name = name,
        namespace = namespace,
        version = version,
        description = description,
        matchPatterns = matchPatterns,
        includePatterns = includePatterns,
        excludePatterns = excludePatterns,
        runAt = runAt,
        grants = grants,
        requires = requires,
        resources = resources,
        source = source,
        enabled = enabled,
        sourceUrl = sourceUrl,
        updateUrl = updateUrl,
        installedAt = installedAt,
        updatedAt = updatedAt,
    )

    companion object {
        private const val MAX_SCRIPT_BYTES = 4L * 1024L * 1024L
        private const val MAX_MENU_URLS = 50
        private val VERSION_SPLIT = Regex("[^A-Za-z0-9]+")

        /** Compares dotted versions; numeric segments beat alphanumeric prerelease tags. */
        fun compareVersions(left: String, right: String): Int {
            val a = left.split(VERSION_SPLIT).filter { it.isNotEmpty() }
            val b = right.split(VERSION_SPLIT).filter { it.isNotEmpty() }
            val size = maxOf(a.size, b.size)
            for (index in 0 until size) {
                val first = a.getOrNull(index) ?: "0"
                val second = b.getOrNull(index) ?: "0"
                val firstNumber = first.toIntOrNull()
                val secondNumber = second.toIntOrNull()
                val comparison = when {
                    firstNumber != null && secondNumber != null -> firstNumber.compareTo(secondNumber)
                    firstNumber != null -> 1
                    secondNumber != null -> -1
                    else -> first.compareTo(second, ignoreCase = true)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}
