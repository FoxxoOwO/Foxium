package com.chrogecko.browser.feature.addons

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.chrogecko.browser.core.engine.ChroGeckoRuntime
import com.chrogecko.browser.core.engine.SystemExtensionIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoDisplay
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

data class InstalledAddon(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val enabled: Boolean,
    val builtIn: Boolean,
    val hasAction: Boolean = false,
    val actionTitle: String? = null,
    val badgeText: String? = null,
)

data class ExtensionPopup(
    val extensionId: String,
    val title: String,
    val session: GeckoSession,
)

/** An install waiting for the user to accept the permissions the add-on asks for. */
data class PendingAddonInstall(
    val name: String,
    val version: String,
    val permissions: List<String>,
    val origins: List<String>,
    val dataCollection: List<String>,
)

/**
 * Unrestricted WebExtension manager: installs from `.xpi` URIs, direct URLs (including
 * addons.mozilla.org) and hosts browser-action popups in a GeckoSession the Compose layer can
 * render in a bottom sheet.
 */
@Singleton
class AddonManagerController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: ChroGeckoRuntime,
) {

    private val controller: WebExtensionController
        get() = runtime.runtime.webExtensionController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val attached = ConcurrentHashMap.newKeySet<String>()
    private val actionStates = ConcurrentHashMap<String, WebExtension.Action>()
    private val extensions = ConcurrentHashMap<String, WebExtension>()

    private val _addons = MutableStateFlow<List<InstalledAddon>>(emptyList())
    val addons: StateFlow<List<InstalledAddon>> = _addons.asStateFlow()

    private val _popup = MutableStateFlow<ExtensionPopup?>(null)
    val popup: StateFlow<ExtensionPopup?> = _popup.asStateFlow()

    private val _pendingInstall = MutableStateFlow<PendingAddonInstall?>(null)
    val pendingInstall: StateFlow<PendingAddonInstall?> = _pendingInstall.asStateFlow()

    // GeckoResult whose completion is deferred until the user answers the permission prompt.
    private var pendingPrompt: GeckoResult<WebExtension.PermissionPromptResponse>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollection: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                requestPermission(extension, permissions, origins, dataCollection)

            // Only reached when an update wants new privileges. Bundled extensions may take them;
            // a third-party update is refused so nothing escalates without the user seeing it.
            override fun onUpdatePrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollection: Array<String>,
            ): GeckoResult<AllowOrDeny> {
                if (isSystemExtension(extension)) return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                _messages.tryEmit(
                    "Update of ${extension.metaData.name ?: extension.id} needs new permissions and was refused",
                )
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollection: Array<String>,
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.DENY)
        })
        refresh()
    }

    private fun isSystemExtension(extension: WebExtension): Boolean =
        extension.isBuiltIn || extension.id in SystemExtensionIds.ALL

    /**
     * Holds the install open until the Compose layer answers, instead of granting everything.
     * A superseded prompt is denied so no GeckoResult is left dangling.
     */
    private fun requestPermission(
        extension: WebExtension,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollection: Array<String>,
    ): GeckoResult<WebExtension.PermissionPromptResponse> {
        if (isSystemExtension(extension)) {
            return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
        }
        pendingPrompt?.complete(WebExtension.PermissionPromptResponse(false, false, false))
        val result = GeckoResult<WebExtension.PermissionPromptResponse>()
        pendingPrompt = result
        _pendingInstall.value = PendingAddonInstall(
            name = extension.metaData.name ?: extension.id,
            version = extension.metaData.version.orEmpty(),
            permissions = permissions.toList(),
            origins = origins.toList(),
            dataCollection = dataCollection.toList(),
        )
        return result
    }

    /** Answers the open install prompt; without an answer GeckoView keeps the install pending. */
    fun respondToPendingInstall(allow: Boolean) {
        val result = pendingPrompt ?: return
        pendingPrompt = null
        _pendingInstall.value = null
        mainHandler.post {
            // The dialog never asks for incognito access: never grant private mode implicitly.
            // The third field covers the data-collection permission that the dialog does list.
            result.complete(WebExtension.PermissionPromptResponse(allow, false, allow))
        }
    }

    private val refreshGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    fun refresh() {
        val generation = refreshGeneration.incrementAndGet()
        controller.list().accept(
            { list ->
                // A slow earlier list() callback must not resurrect a removed extension or hide
                // a freshly installed one.
                if (refreshGeneration.get() != generation) return@accept
                val rawList = list.orEmpty()
                val activeIds = rawList.mapTo(HashSet()) { it.id }
                extensions.keys.retainAll(activeIds)
                attached.retainAll(activeIds)
                actionStates.keys.retainAll(activeIds)
                val installed = rawList.map(::toAddon)
                _addons.value = installed
                rawList.forEach(::attachDelegate)
            },
            { error ->
                if (refreshGeneration.get() == generation) {
                    _messages.tryEmit("Could not list extensions: ${error?.message}")
                }
            },
        )
    }

    /** Installs a picked `.xpi`/`.zip` through the Storage Access Framework. */
    fun installFromUri(uri: Uri) {
        scope.launch {
            val target = runCatching { copyToCache(uri) }.getOrElse { error ->
                _messages.tryEmit("Cannot read extension file: ${error.message}")
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                installFile(target)
            }
        }
    }

    /** Installs directly from an https URL, e.g. an addons.mozilla.org or GitHub `.xpi` link. */
    fun installFromUrl(url: String) {
        val scheme = runCatching { url.toUri().scheme?.lowercase() }.getOrNull()
        if (scheme != "https") {
            _messages.tryEmit("Only https extension URLs can be installed")
            return
        }
        controller.install(url).accept(
            { extension ->
                if (extension != null) {
                    attachDelegate(extension)
                    _messages.tryEmit("Installed ${extension.metaData.name ?: extension.id}")
                    refresh()
                }
            },
            { error -> _messages.tryEmit("Install failed: ${errorText(error)}") },
        )
    }

    /** Installs the add-on shown on the current AMO detail page. */
    fun installFromAmoPage(pageUrl: String): Boolean {
        val slug = AmoInstallUrls.addonSlug(pageUrl) ?: return false
        installFromUrl(AmoInstallUrls.xpiUrl(slug))
        return true
    }

    fun uninstall(id: String) {
        val extension = extensions[id] ?: return
        controller.uninstall(extension).accept(
            {
                extensions.remove(id)
                attached.remove(id)
                actionStates.remove(id)
                if (_popup.value?.extensionId == id) {
                    closePopup()
                }
                refresh()
            },
            { error -> _messages.tryEmit("Uninstall failed: ${error?.message}") },
        )
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val extension = extensions[id] ?: return
        val operation = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        if (!enabled) {
            // A disabled extension must not keep its action badge or popup alive.
            actionStates.remove(id)
            if (_popup.value?.extensionId == id) closePopup()
            _addons.update { list -> list.map { if (it.id == id) it.copy(hasAction = false, badgeText = null) else it } }
        }
        operation.accept(
            { refresh() },
            { error -> _messages.tryEmit("Could not change extension state: ${error?.message}") },
        )
    }

    /** Forwards an action click to the extension; popups inherit the calling tab's profile. */
    @Volatile
    private var popupPrivate = false
    private var activeSession: GeckoSession? = null

    /** Notifies WebExtensions which native tab is active so action clicks have a tab context. */
    fun setActiveSession(session: GeckoSession?) {
        if (activeSession === session) return
        activeSession?.let { runCatching { controller.setTabActive(it, false) } }
        activeSession = session
        session?.let { runCatching { controller.setTabActive(it, true) } }
    }

    fun clickAction(id: String, isPrivate: Boolean = false) {
        popupPrivate = isPrivate
        val action = actionStates[id]
        if (action == null) {
            _messages.tryEmit("Extension action is not ready yet")
            return
        }
        action.click()
    }

    fun closePopup() {
        val session = _popup.value?.session
        // Same pattern as EngineSession.releaseDisplay(): a stranded display makes the next
        // setSession() fail with "Display already acquired".
        runCatching {
            val getter = GeckoSession::class.java.getDeclaredMethod("getDisplay")
            getter.isAccessible = true
            (getter.invoke(session) as? GeckoDisplay)?.let { session?.releaseDisplay(it) }
        }
        session?.close()
        _popup.value = null
    }

    private fun installFile(file: File) {
        val fileUri = Uri.fromFile(file).toString()
        controller.install(fileUri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { extension ->
                runCatching { file.delete() }
                if (extension != null) {
                    attachDelegate(extension)
                    _messages.tryEmit("Installed ${extension.metaData.name ?: extension.id}")
                    refresh()
                }
            },
            { error ->
                runCatching { file.delete() }
                _messages.tryEmit("Install failed: ${errorText(error)}")
            },
        )
    }

    private fun errorText(error: Throwable?): String {
        val code = (error as? WebExtension.InstallException)?.code
        return listOfNotNull(error?.message, code?.let { "code=$it" }).joinToString(" · ")
            .ifBlank { "unknown error" }
    }

    private fun copyToCache(uri: Uri): File {
        val directory = File(context.cacheDir, "addons").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.xpi")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Selected file cannot be opened")
        return target
    }

    /** Creates the GeckoSession that hosts an extension popup and publishes it to Compose. */
    private fun openPopupInternal(extension: WebExtension, action: WebExtension.Action): GeckoSession {
        if (_popup.value != null) closePopup()
        val session = GeckoSession(
            GeckoSessionSettings.Builder().usePrivateMode(popupPrivate).build(),
        )
        session.open(runtime.runtime)
        _popup.value = ExtensionPopup(
            extensionId = extension.id,
            title = action.title ?: extension.metaData.name.orEmpty(),
            session = session,
        )
        return session
    }

    private fun attachDelegate(extension: WebExtension) {
        extensions[extension.id] = extension
        attached.add(extension.id)
        // Always (re-)attach: Gecko may hand back a fresh WebExtension instance after an update
        // or disable/enable cycle, and setActionDelegate is idempotent on the same instance.
        extension.setActionDelegate(ActionDelegate(extension.id))
    }

    private fun toAddon(extension: WebExtension): InstalledAddon {
        val action = actionStates[extension.id]
        return InstalledAddon(
            id = extension.id,
            name = extension.metaData.name ?: extension.id,
            version = extension.metaData.version.orEmpty(),
            description = extension.metaData.description.orEmpty(),
            enabled = extension.metaData.enabled,
            builtIn = extension.isBuiltIn || extension.id in SystemExtensionIds.ALL,
            hasAction = action != null,
            actionTitle = action?.title,
            badgeText = action?.badgeText,
        )
    }

    private fun updateAddon(extensionId: String) {
        val current = _addons.value
        if (current.none { it.id == extensionId }) return
        _addons.value = current.map { addon ->
            if (addon.id != extensionId) return@map addon
            val action = actionStates[extensionId]
            addon.copy(
                hasAction = action != null,
                actionTitle = action?.title,
                badgeText = action?.badgeText,
            )
        }
    }

    private inner class ActionDelegate(
        private val extensionId: String,
    ) : WebExtension.ActionDelegate {

        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) {
            actionStates[extensionId] = action
            updateAddon(extensionId)
        }

        override fun onPageAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) {
            actionStates[extensionId] = action
            updateAddon(extensionId)
        }

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> {
            if (_popup.value?.extensionId == extension.id) {
                closePopup()
                return GeckoResult<GeckoSession>().apply { complete(null) }
            }
            return openPopup(extension, action)
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = openPopup(extension, action)

        private fun openPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = GeckoResult.fromValue(openPopupInternal(extension, action))
    }
}
