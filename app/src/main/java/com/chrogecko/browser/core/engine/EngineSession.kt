package com.chrogecko.browser.core.engine

import java.lang.ref.WeakReference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.URI
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autofill
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoDisplay
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse
import com.chrogecko.browser.feature.media.MediaPlayback
import com.chrogecko.browser.feature.media.NowPlaying

/** What the user long-pressed; the Chrome context menu offers actions for these. */
data class ContextMenu(
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    val text: String? = null,
)

/** Text the user long-pressed, driving Chrome's floating selection toolbar. */
data class SelectionMenu(
    val text: String,
    val canCopy: Boolean,
    val canSelectAll: Boolean,
    /** Screen-space bounds of the selection, used to anchor the floating toolbar. */
    val screenRect: android.graphics.RectF? = null,
)

data class SessionState(
    val url: String = "about:blank",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
)

/**
 * One GeckoSession plus the state the Chrome-style UI needs to observe.
 * Sessions are owned by [GeckoEngine]; call [close] through it.
 */
class EngineSession(
    val contextId: String,
    val isPrivate: Boolean,
    runtime: GeckoRuntime,
    private val appContext: Context,
    private val autofill: AutofillController,
    openNow: Boolean = true,
    trackingProtection: Boolean = true,
) {
    val id: String = UUID.randomUUID().toString()

    val geckoSession: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .contextId(contextId)
            .usePrivateMode(isPrivate)
            .useTrackingProtection(trackingProtection)
            .suspendMediaWhenInactive(false)
            .build(),
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attachedView: WeakReference<GeckoView>? = null
    private var platformAutofillDelegate: Autofill.Delegate? = null
    private var autofillAttached = false
    private var printAttached = false
    @Volatile
    private var pendingPrintResult: GeckoResult<Boolean>? = null
    private val printFiles = java.util.Collections.synchronizedList(mutableListOf<File>())

    private val _historyState = MutableStateFlow<String?>(null)

    /** Serialized Gecko session history; changes on every committed navigation. */
    val historyStateFlow: StateFlow<String?> = _historyState.asStateFlow()

    private val _scrollY = MutableStateFlow(0)

    /** Vertical page scroll in CSS pixels, used for Chrome-style toolbar auto-hide. */
    val scrollY: StateFlow<Int> = _scrollY.asStateFlow()

    /** Consumes external responses (downloads). Return true when the body was taken over. */
    var externalResponseHandler: ((WebResponse) -> Boolean)? = null

    /** Return true to block the navigation (the URL was handled natively). */
    var navigationInterceptor: ((String) -> Boolean)? = null

    /** Called for `target="_blank"` / `window.open`; return an unopened child [GeckoSession] if handled. */
    var newSessionHandler: ((String) -> GeckoSession?)? = null

    /** Called when page script invokes `window.close()` on a script-opened tab. */
    var closeRequestHandler: (() -> Unit)? = null

    val desktopMode: Boolean
        get() = geckoSession.settings.userAgentMode == GeckoSessionSettings.USER_AGENT_MODE_DESKTOP

    private val _contextMenu = MutableStateFlow<ContextMenu?>(null)

    /** Non-null while the long-press menu is open; cleared by [dismissContextMenu]. */
    val contextMenu: StateFlow<ContextMenu?> = _contextMenu.asStateFlow()

    private data class PendingPrompt(
        val prompt: GeckoSession.PromptDelegate.BasePrompt,
        val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    )

    private data class MediaRequest(
        val callback: GeckoSession.PermissionDelegate.MediaCallback,
        val video: Array<out GeckoSession.PermissionDelegate.MediaSource>,
        val audio: Array<out GeckoSession.PermissionDelegate.MediaSource>,
    )

    private var promptSeq = 0L
    private val pendingPrompts = mutableMapOf<Long, PendingPrompt>()
    private val pendingPermissionResults = mutableMapOf<Long, GeckoResult<Int>>()
    private var pendingMediaRequest: Pair<Long, MediaRequest>? = null
    private var pendingAndroidCallback: GeckoSession.PermissionDelegate.Callback? = null

    private val _prompt = MutableStateFlow<EnginePrompt?>(null)

    /** Dialog Gecko is blocked on, e.g. `window.confirm()` or `<input type=file>`. */
    val prompt: StateFlow<EnginePrompt?> = _prompt.asStateFlow()

    private val _permission = MutableStateFlow<PermissionAsk?>(null)

    /** Site permission the page is waiting for; answer with [answerPermission]. */
    val permission: StateFlow<PermissionAsk?> = _permission.asStateFlow()

    private val _androidPermissions = MutableStateFlow<AndroidPermissionAsk?>(null)

    /** Android runtime permissions to request from an Activity, then answer with [answerAndroidPermissions]. */
    val androidPermissions: StateFlow<AndroidPermissionAsk?> = _androidPermissions.asStateFlow()

    /** Called for `window.open`; the host opens the URI in a new tab. */
    var popupHandler: ((String) -> Unit)? = null

    /** Receives the print-ready PDF Gecko renders for the current page. */
    var printHandler: ((File) -> Unit)? = null

    /**
     * Applies per-site rules. Called with the host right before a document starts loading, which
     * is the only point where [applySiteRules] still affects the document being loaded.
     */
    var siteRuleApplier: ((String) -> Unit)? = null

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    init {
        attachSelectionDelegate()
        attachPromptDelegates()
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                _state.update { it.copy(url = url ?: it.url) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                runCatching { URI(request.uri).host?.lowercase() }.getOrNull()
                    ?.let { host -> siteRuleApplier?.invoke(host) }
                val blocked = navigationInterceptor?.invoke(request.uri) == true
                return GeckoResult.fromValue(if (blocked) AllowOrDeny.DENY else AllowOrDeny.ALLOW)
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String,
            ): GeckoResult<GeckoSession>? =
                newSessionHandler?.invoke(uri)?.let { GeckoResult.fromValue(it) }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _state.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _state.update { it.copy(canGoForward = canGoForward) }
            }
        }

        geckoSession.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                historyList: GeckoSession.HistoryDelegate.HistoryList,
            ) {
                Log.d(TAG, "history: ${historyList.size} entries")
            }
        }

        geckoSession.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                _scrollY.value = scrollY
            }
        }

        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                // Gecko rebuilds its autofill listener when a session starts, so ensure our
                // composite delegate is installed while preserving GeckoView's platform delegate.
                val currentDelegate = session.autofillDelegate
                if (currentDelegate != null && !autofill.isOwnedDelegate(currentDelegate)) {
                    platformAutofillDelegate = currentDelegate
                }
                if (!autofillAttached || !autofill.isOwnedDelegate(currentDelegate)) {
                    autofillAttached = true
                    autofill.attach(session, isPrivate, platformAutofillDelegate)
                }
                if (!printAttached) {
                    printAttached = true
                    attachPrintDelegate()
                }
                _contextMenu.value = null
                _scrollY.value = 0
                _state.update { it.copy(isLoading = true, progress = 0, url = url) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                _state.update {
                    it.copy(isLoading = false, progress = if (success) 100 else it.progress)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _state.update { it.copy(progress = progress) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                _state.update { it.copy(isSecure = securityInfo.isSecure) }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState,
            ) {
                _historyState.value = sessionState.toString()
            }
        }

        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                if (externalResponseHandler?.invoke(response) != true) {
                    runCatching { response.body?.close() }
                }
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                Log.d("ChroGeckoContext", "context menu type=${element.type} link=${element.linkUri} src=${element.srcUri}")
                val link = element.linkUri?.takeIf { it.startsWith("http") }
                val image = element.srcUri?.takeIf {
                    it.startsWith("http") &&
                        element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                }
                if (link == null && image == null) return
                _contextMenu.value = ContextMenu(
                    linkUrl = link,
                    imageUrl = image,
                    text = element.altText?.ifBlank { null } ?: element.textContent,
                )
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                _state.update { it.copy(title = title ?: "") }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                _fullscreen.value = fullScreen
            }

            override fun onCloseRequest(session: GeckoSession) {
                closeRequestHandler?.invoke()
            }

        }
        attachMediaSessionDelegate()
        if (openNow) {
            // MediaSession support is wired during open(), so the delegate must exist first.
            geckoSession.open(runtime)
            // Print support is registered during open(); a delegate set earlier is never called.
            printAttached = true
            attachPrintDelegate()
        }
    }

    private fun attachPrintDelegate() {
        geckoSession.printDelegate = object : GeckoSession.PrintDelegate {
            // printPageContent() only signals the app; the PDF arrives via didPrintPageContent().
            override fun onPrint(session: GeckoSession) {
                session.didPrintPageContent()
            }

            override fun onPrintWithStatus(stream: InputStream): GeckoResult<Boolean> {
                pendingPrintResult?.complete(false)
                val result = GeckoResult<Boolean>()
                pendingPrintResult = result
                ioScope.launch {
                    val file = if (isActive) runCatching { writePrintPdf(stream) }.getOrNull() else null
                    if (file != null) printFiles.add(file)
                    mainHandler.post {
                        if (pendingPrintResult !== result) {
                            // close() or a newer print job already completed the result; do not touch it again.
                            if (file != null) {
                                printFiles.remove(file)
                                file.delete()
                            }
                            return@post
                        }
                        pendingPrintResult = null
                        if (file != null && printHandler != null) {
                            printFiles.remove(file)
                            printHandler?.invoke(file)
                            result.complete(true)
                        } else {
                            if (file != null) {
                                printFiles.remove(file)
                                file.delete()
                            }
                            result.complete(false)
                        }
                    }
                }
                return result
            }
        }
    }

    private fun writePrintPdf(stream: InputStream): File {
        val dir = File(appContext.cacheDir, "print").apply { mkdirs() }
        val file = File(dir, "page-${UUID.randomUUID()}.pdf")
        file.outputStream().use { out -> stream.use { it.copyTo(out) } }
        return file
    }

    // Gecko renders the page to PDF asynchronously; the PrintDelegate streams it back.
    fun printPage(): Boolean {
        if (printHandler == null) return false
        geckoSession.printPageContent()
        return true
    }

    private fun attachMediaSessionDelegate() {
        MediaPlayback.attach(appContext)
        geckoSession.mediaSessionDelegate = object : MediaSession.Delegate {
            private var features = 0L
            private var positionMs = 0L
            private var durationMs = 0L
            private var playing = false
            private var title = ""
            private var artist = ""
            private var album = ""
            private var active = false

            private fun publish() {
                val pageTitle = state.value.title.ifBlank { state.value.url }
                MediaPlayback.update(
                    id,
                    NowPlaying(
                        title = title.ifBlank { pageTitle },
                        artist = artist,
                        album = album,
                        playing = playing,
                        features = features,
                        positionMs = positionMs,
                        durationMs = durationMs,
                    ),
                )
            }

            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                active = true
                MediaPlayback.bind(
                    ownerId = id,
                    play = { mediaSession.play() },
                    pause = { mediaSession.pause() },
                    stop = { mediaSession.stop() },
                    next = { mediaSession.nextTrack() },
                    previous = { mediaSession.previousTrack() },
                    seekTo = { mediaSession.seekTo(it / 1000.0, true) },
                )
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                active = false
                // Gecko deactivates the session when the tab is hidden, but audio keeps playing
                // (suspendMediaWhenInactive is off), so the notification must survive.
                if (!playing) MediaPlayback.release(id)
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                meta: MediaSession.Metadata,
            ) {
                title = meta.title.orEmpty()
                artist = meta.artist.orEmpty()
                album = meta.album.orEmpty()
                publish()
            }

            override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, value: Long) {
                features = value
                publish()
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                playing = true
                publish()
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                playing = false
                publish()
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                playing = false
                MediaPlayback.release(id)
            }

            private fun secondsToMillis(seconds: Double): Long =
                if (seconds.isFinite() && seconds >= 0.0) (seconds * 1000.0).toLong() else 0L

            override fun onPositionState(
                session: GeckoSession,
                mediaSession: MediaSession,
                state: MediaSession.PositionState,
            ) {
                positionMs = secondsToMillis(state.position)
                durationMs = secondsToMillis(state.duration)
                publish()
            }

        }
    }

    /** True while a page element (usually a video) asked for the whole screen. */
    fun exitFullScreen() {
        geckoSession.exitFullScreen()
    }

    fun load(url: String) {
        if (url.isNotBlank()) {
            geckoSession.loadUri(url)
        }
    }

    fun restoreState(serialized: String): Boolean {
        val state = runCatching { GeckoSession.SessionState.fromString(serialized) }.getOrNull()
            ?: return false
        geckoSession.restoreState(state)
        return true
    }

    fun dismissContextMenu() {
        _contextMenu.value = null
    }

    private val _selection = MutableStateFlow<SelectionMenu?>(null)

    /** Long-pressed text; drives the selection action bar. */
    val selection: StateFlow<SelectionMenu?> = _selection.asStateFlow()

    fun copySelection() {
        runCatching { selectionCache?.copy() }
        _selection.value = null
    }

    fun selectAllSelection() {
        runCatching { selectionCache?.selectAll() }
        _selection.value = null
    }

    fun clearSelection() {
        runCatching { selectionCache?.unselect() }
        runCatching { selectionCache?.hide() }
        selectionCache = null
        _selection.value = null
    }

    private var selectionCache: GeckoSession.SelectionActionDelegate.Selection? = null

    // The delegate has to exist or Gecko never asks us to show the action bar.
    private fun attachSelectionDelegate() {
        geckoSession.selectionActionDelegate = object : GeckoSession.SelectionActionDelegate {
            override fun onShowActionRequest(
                session: GeckoSession,
                selection: GeckoSession.SelectionActionDelegate.Selection,
            ) {
                selectionCache = selection
                _selection.value = SelectionMenu(
                    text = selection.text.orEmpty(),
                    canCopy = selection.availableActions.contains(GeckoSession.SelectionActionDelegate.ACTION_COPY),
                    canSelectAll = selection.availableActions.contains(GeckoSession.SelectionActionDelegate.ACTION_SELECT_ALL),
                    screenRect = selection.screenRect?.let { android.graphics.RectF(it) },
                )
            }

            override fun onHideAction(session: GeckoSession, reason: Int) {
                selectionCache = null
                _selection.value = null
            }
        }
    }

    fun reload() = geckoSession.reload()

    private fun attachPromptDelegates() {
        geckoSession.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AlertPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) { EnginePrompt.Alert(it, prompt.message.orEmpty()) }

            override fun onButtonPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ButtonPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) { EnginePrompt.Confirm(it, prompt.message.orEmpty()) }

            override fun onTextPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.TextPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) {
                    EnginePrompt.TextInput(it, prompt.message.orEmpty(), prompt.defaultValue.orEmpty())
                }

            override fun onChoicePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.ChoicePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) {
                    EnginePrompt.Choice(
                        it,
                        prompt.message.orEmpty(),
                        prompt.choices.map { choice -> choice.label.orEmpty() },
                    )
                }

            override fun onAuthPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.AuthPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) {
                    EnginePrompt.Auth(it, prompt.message.orEmpty(), prompt.authOptions.username)
                }

            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
                register(prompt) {
                    EnginePrompt.FilePick(
                        id = it,
                        mimeTypes = prompt.mimeTypes?.toList().orEmpty(),
                        multiple = prompt.type != GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE,
                        capture = prompt.capture,
                    )
                }

            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                prompt.targetUri?.takeIf { it.isNotBlank() }?.let { popupHandler?.invoke(it) }
                return GeckoResult.fromValue(prompt.dismiss())
            }
        }

        geckoSession.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                Log.d(TAG, "content perm ${perm.permission} value=${perm.value}")
                val kind = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                        PermissionAsk.Kind.GEOLOCATION
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION ->
                        PermissionAsk.Kind.NOTIFICATIONS
                    // Autoplay, persistent storage and DRM need no user decision.
                    else -> return GeckoResult.fromValue(
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
                    )
                }
                rejectPendingPermission(_permission.value?.id)
                val id = ++promptSeq
                val result = GeckoResult<Int>()
                pendingPermissionResults[id] = result
                _permission.value = PermissionAsk(id, hostOf(perm.uri), kind)
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                val kind = when {
                    !video.isNullOrEmpty() && !audio.isNullOrEmpty() ->
                        PermissionAsk.Kind.CAMERA_AND_MICROPHONE
                    !video.isNullOrEmpty() -> PermissionAsk.Kind.CAMERA
                    else -> PermissionAsk.Kind.MICROPHONE
                }
                rejectPendingPermission(_permission.value?.id)
                val id = ++promptSeq
                pendingMediaRequest = id to MediaRequest(callback, video.orEmpty(), audio.orEmpty())
                _permission.value = PermissionAsk(id, hostOf(uri), kind)
            }

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                pendingAndroidCallback?.reject()
                pendingAndroidCallback = callback
                _androidPermissions.value = AndroidPermissionAsk(++promptSeq, permissions.orEmpty().toList())
            }
        }
    }

    private fun rejectPendingPermission(id: Long?) {
        if (id == null) return
        pendingPermissionResults.remove(id)?.complete(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
        )
        val media = pendingMediaRequest
        if (media != null && media.first == id) {
            pendingMediaRequest = null
            media.second.callback.reject()
        }
    }

    private fun register(
        prompt: GeckoSession.PromptDelegate.BasePrompt,
        build: (Long) -> EnginePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        Log.d(TAG, "prompt ${prompt.javaClass.simpleName}")
        _prompt.value?.id?.let { previousId ->
            pendingPrompts.remove(previousId)?.let { previous ->
                if (!previous.prompt.isComplete) {
                    previous.result.complete(previous.prompt.dismiss())
                }
            }
        }
        val id = ++promptSeq
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        pendingPrompts[id] = PendingPrompt(prompt, result)
        _prompt.value = build(id)
        return result
    }

    private fun hostOf(uri: String?): String =
        runCatching { java.net.URI(uri.orEmpty()).host.orEmpty() }.getOrDefault("")

    fun dismissPrompt() {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (!pending.prompt.isComplete) {
            pending.result.complete(pending.prompt.dismiss())
        }
    }

    fun answerConfirm(positive: Boolean) {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (pending.prompt.isComplete) return
        val response = when (val prompt = pending.prompt) {
            is GeckoSession.PromptDelegate.ButtonPrompt -> prompt.confirm(
                if (positive) {
                    GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE
                } else {
                    GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE
                },
            )
            else -> prompt.dismiss()
        }
        pending.result.complete(response)
    }

    fun answerText(value: String) {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (pending.prompt.isComplete) return
        val response = when (val prompt = pending.prompt) {
            is GeckoSession.PromptDelegate.TextPrompt -> prompt.confirm(value)
            else -> prompt.dismiss()
        }
        pending.result.complete(response)
    }

    fun answerChoice(index: Int) {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (pending.prompt.isComplete) return
        val response = when (val prompt = pending.prompt) {
            is GeckoSession.PromptDelegate.ChoicePrompt -> {
                val choice = prompt.choices.getOrNull(index)
                if (choice != null) prompt.confirm(choice) else prompt.dismiss()
            }
            else -> prompt.dismiss()
        }
        pending.result.complete(response)
    }

    fun answerAuth(user: String, password: String) {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (pending.prompt.isComplete) return
        val response = when (val prompt = pending.prompt) {
            is GeckoSession.PromptDelegate.AuthPrompt -> prompt.confirm(user, password)
            else -> prompt.dismiss()
        }
        pending.result.complete(response)
    }

    /** Called by the host once the SAF picker returns; an empty list cancels the upload. */
    fun answerFiles(uris: List<Uri>) {
        val id = _prompt.value?.id ?: return
        _prompt.value = null
        val pending = pendingPrompts.remove(id) ?: return
        if (pending.prompt.isComplete) return
        val prompt = pending.prompt as? GeckoSession.PromptDelegate.FilePrompt
        val response = when {
            prompt == null -> pending.prompt.dismiss()
            uris.isEmpty() -> prompt.dismiss()
            else -> runCatching {
                prompt.confirm(appContext, uris.toTypedArray())
            }.getOrElse { prompt.dismiss() }
        }
        pending.result.complete(response)
    }

    fun answerPermission(allow: Boolean) {
        val ask = _permission.value ?: return
        _permission.value = null
        pendingPermissionResults.remove(ask.id)?.let { result ->
            result.complete(
                if (allow) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                } else {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                },
            )
            return
        }
        val pair = pendingMediaRequest
        if (pair == null || pair.first != ask.id) return
        pendingMediaRequest = null
        val media = pair.second
        if (!allow) {
            media.callback.reject()
            return
        }
        // Gecko only has the two-argument grant; an absent side is null.
        media.callback.grant(
            media.video.firstOrNull(),
            media.audio.firstOrNull(),
        )
    }

    fun answerAndroidPermissions(granted: Boolean) {
        _androidPermissions.value = null
        val callback = pendingAndroidCallback ?: return
        pendingAndroidCallback = null
        if (granted) callback.grant() else callback.reject()
    }

    fun stop() = geckoSession.stop()

    fun goBack() = geckoSession.goBack()

    fun goForward() = geckoSession.goForward()

    fun setActive(active: Boolean) = geckoSession.setActive(active)

    fun attachView(view: GeckoView) {
        attachedView = WeakReference(view)
        view.autofillEnabled = true
        val currentDelegate = geckoSession.autofillDelegate
        if (currentDelegate != null && !autofill.isOwnedDelegate(currentDelegate)) {
            platformAutofillDelegate = currentDelegate
        }
        autofill.attach(geckoSession, isPrivate, platformAutofillDelegate)
    }

    fun detachView(view: GeckoView) {
        if (attachedView?.get() === view) {
            attachedView = null
            platformAutofillDelegate = null
            autofillAttached = false
            autofill.detach(geckoSession)
        }
    }

    // GeckoSession only exposes acquireDisplay(), so a display left behind by a detached
    // GeckoView can only be freed through its package-private getDisplay().
    fun releaseDisplay() {
        runCatching {
            val getter = GeckoSession::class.java.getDeclaredMethod("getDisplay")
            getter.isAccessible = true
            val display = getter.invoke(geckoSession) as? GeckoDisplay ?: return
            geckoSession.releaseDisplay(display)
        }
    }

    fun capturePixels(onBitmap: (Bitmap) -> Unit) {
        val view = attachedView?.get()?.takeIf { it.session === geckoSession } ?: return
        runCatching { view.capturePixels() }
            .getOrNull()
            ?.accept({ bitmap -> if (bitmap != null) onBitmap(bitmap) }, { })
    }

    // Site rules change how the next document loads, so the caller decides about reloading.
    fun applySiteRules(blockJavascript: Boolean, desktopSite: Boolean) {
        val jsEnabled = !blockJavascript
        if (geckoSession.settings.allowJavascript != jsEnabled) {
            geckoSession.settings.allowJavascript = jsEnabled
        }
        val mode = if (desktopSite) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        if (geckoSession.settings.userAgentMode != mode) geckoSession.settings.userAgentMode = mode
    }

    fun setDesktopMode(enabled: Boolean) {
        geckoSession.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        geckoSession.reload()
    }

    fun setTrackingProtection(enabled: Boolean) {
        if (geckoSession.settings.useTrackingProtection != enabled) {
            geckoSession.settings.useTrackingProtection = enabled
        }
    }

    fun find(query: String, onResult: (GeckoSession.FinderResult) -> Unit) {
        finder.find(query, 0).accept({ result -> if (result != null) onResult(result) }, { })
    }

    fun findAgain(forward: Boolean, onResult: (GeckoSession.FinderResult) -> Unit) {
        val flags = if (forward) {
            GeckoSession.FINDER_FIND_FORWARD
        } else {
            GeckoSession.FINDER_FIND_BACKWARDS
        }
        finder.find(null, flags).accept({ result -> if (result != null) onResult(result) }, { })
    }

    fun clearFind() = finder.clear()

    private val finder get() = geckoSession.finder

    fun close() {
        pendingPrompts.values.forEach { pending ->
            if (!pending.prompt.isComplete) {
                pending.result.complete(pending.prompt.dismiss())
            }
        }
        pendingPrompts.clear()
        _prompt.value = null
        pendingPermissionResults.values.forEach { result ->
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
        pendingPermissionResults.clear()
        pendingMediaRequest?.second?.callback?.reject()
        pendingMediaRequest = null
        _permission.value = null
        pendingAndroidCallback?.reject()
        pendingAndroidCallback = null
        _androidPermissions.value = null
        MediaPlayback.release(id)
        autofill.detach(geckoSession)
        attachedView = null
        // Never leave Gecko waiting on an uncompleted print result when the tab goes away.
        pendingPrintResult?.complete(false)
        pendingPrintResult = null
        synchronized(printFiles) {
            printFiles.forEach { runCatching { it.delete() } }
            printFiles.clear()
        }
        ioScope.cancel()
        geckoSession.close()
    }


    private companion object {
        const val TAG = "ChroGeckoSession"
    }
}
