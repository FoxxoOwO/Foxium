package com.chrogecko.browser.core.engine

import android.util.SparseArray
import com.chrogecko.browser.core.database.repository.LoginRepository
import com.chrogecko.browser.core.database.repository.SavedLogin
import com.chrogecko.browser.core.preferences.AppPreferences
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autofill
import org.mozilla.geckoview.GeckoSession

/**
 * Bridges GeckoView's autofill sessions to both the Android Autofill Framework (third-party
 * password managers via [GeckoView]'s platform delegate) and ChroGecko's built-in login store.
 *
 * Gecko registers its autofill listeners in [GeckoSession.open], and [GeckoView.setSession] assigns
 * the Android platform delegate. Chaining both inside [CompositeAutofillDelegate] keeps Android's
 * native autofill popup/service and ChroGecko's store working side by side.
 */
@Singleton
class AutofillController @Inject constructor(
    private val logins: LoginRepository,
    preferences: AppPreferences,
) {

    /** Logins matching the focused login form. */
    data class Suggestion(
        /** EngineSession id this suggestion belongs to; the UI only shows it on that tab. */
        val sessionId: String,
        val host: String,
        val entries: List<SavedLogin>,
        val usernameId: Int?,
        val passwordId: Int?,
    )

    /** Credentials the user just typed into a login form. */
    data class SaveRequest(val host: String, val username: String, val password: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _suggestion = MutableStateFlow<Suggestion?>(null)
    val suggestion: StateFlow<Suggestion?> = _suggestion.asStateFlow()
    private val _saveRequest = MutableStateFlow<SaveRequest?>(null)
    val saveRequest: StateFlow<SaveRequest?> = _saveRequest.asStateFlow()

    @Volatile
    private var androidAutofillEnabled = true

    @Volatile
    private var builtInPasswordSaveEnabled = true

    // Autofill focus is inherently per-tab. A singleton's single job/session pair lets a focus
    // event in tab B steal tab A's in-flight lookup and even fill tab B's node ids into tab A.
    private val focusJobs = ConcurrentHashMap<String, Job>()
    private val suggestionsBySession = ConcurrentHashMap<String, Suggestion>()
    private val sessions = ConcurrentHashMap<String, WeakReference<GeckoSession>>()
    @Volatile
    private var latestSessionId: String? = null

    init {
        scope.launch {
            preferences.androidAutofillEnabled.collectLatest { androidAutofillEnabled = it }
        }
        scope.launch {
            preferences.builtInPasswordSaveEnabled.collectLatest { builtInPasswordSaveEnabled = it }
        }
    }

    fun isOwnedDelegate(delegate: Autofill.Delegate?): Boolean = delegate is CompositeAutofillDelegate

    fun attach(
        geckoSession: GeckoSession,
        isPrivate: Boolean = false,
        platformDelegate: Autofill.Delegate? = null,
        sessionId: String = "",
    ) {
        sessions[sessionId] = WeakReference(geckoSession)
        val current = geckoSession.autofillDelegate
        val resolvedPlatform = platformDelegate
            ?: (current as? CompositeAutofillDelegate)?.platform
            ?: current.takeUnless { it is CompositeAutofillDelegate }
        if (current is CompositeAutofillDelegate &&
            current.platform === resolvedPlatform &&
            current.isPrivate == isPrivate
        ) {
            return
        }
        val internal = (current as? CompositeAutofillDelegate)?.internal
            ?: InternalAutofillDelegate(sessionId, isPrivate)
        geckoSession.autofillDelegate = CompositeAutofillDelegate(
            platform = resolvedPlatform,
            internal = internal,
            isPrivate = isPrivate,
            isPlatformEnabled = { androidAutofillEnabled },
        )
    }

    fun detach(geckoSession: GeckoSession) {
        val sessionId = sessions.entries.firstOrNull { it.value.get() === geckoSession }?.key
        if (sessionId != null) {
            clearSuggestion(sessionId)
            sessions.remove(sessionId)
        }
        val delegate = geckoSession.autofillDelegate
        if (delegate is CompositeAutofillDelegate) {
            // detach() must undo only what attach() installed. Nulling the delegate would also
            // discard Android's platform autofill for a session that is merely being detached.
            geckoSession.autofillDelegate = delegate.platform
        }
    }

    /** Fills the picked login into the focused form of [sessionId] and no other tab. */
    fun fill(sessionId: String?, entry: SavedLogin) {
        if (sessionId.isNullOrBlank()) return
        val geckoSession = sessions[sessionId]?.get() ?: return
        val fields = suggestionsBySession[sessionId] ?: return
        if (fields.usernameId == null && fields.passwordId == null) return
        scope.launch {
            try {
                val password = logins.password(entry.id) ?: return@launch
                // Re-check under the single-threaded scope: another tab may have taken focus
                // between the tap and the database read.
                val current = suggestionsBySession[sessionId] ?: return@launch
                if (latestSessionId != sessionId) return@launch
                val values = SparseArray<CharSequence>()
                current.usernameId?.let { values.put(it, entry.username) }
                current.passwordId?.let { values.put(it, password) }
                runCatching { geckoSession.autofillSession.autofill(values) }
            } finally {
                clearSuggestion(sessionId)
            }
        }
    }

    fun save() {
        val request = _saveRequest.value ?: return
        _saveRequest.value = null
        scope.launch { logins.save(request.host, request.username, request.password) }
    }

    fun dismissSuggestion(sessionId: String?) {
        if (!sessionId.isNullOrBlank()) clearSuggestion(sessionId)
    }

    private fun clearSuggestion(sessionId: String) {
        focusJobs.remove(sessionId)?.cancel()
        suggestionsBySession.remove(sessionId)
        if (latestSessionId == sessionId) {
            latestSessionId = null
            _suggestion.value = null
        }
    }

    fun dismissSave() {
        _saveRequest.value = null
    }

    /** Node ids of the username and password fields of the focused form. */
    private fun loginFields(session: GeckoSession): Pair<Int?, Int?>? {
        val autofillSession = session.autofillSession
        val nodes = walk(autofillSession.root)
        val usernameNode = nodes.firstOrNull { it.isUsernameField() }
        val passwordNode = nodes.firstOrNull { it.hint == Autofill.Hint.PASSWORD }
        if (usernameNode == null && passwordNode == null) return null
        return Pair(
            usernameNode?.let { autofillSession.dataFor(it).id },
            passwordNode?.let { autofillSession.dataFor(it).id },
        )
    }

    private fun walk(root: Autofill.Node?): List<Autofill.Node> {
        if (root == null) return emptyList()
        val result = ArrayList<Autofill.Node>()
        val stack = ArrayDeque<Autofill.Node>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            result.add(node)
            val children = ArrayList(node.children)
            for (i in children.size - 1 downTo 0) {
                stack.addLast(children[i])
            }
        }
        return result
    }

    private fun Autofill.Node.isUsernameField() =
        hint == Autofill.Hint.USERNAME || hint == Autofill.Hint.EMAIL_ADDRESS

    private fun Autofill.Node.isLoginField() = isUsernameField() || hint == Autofill.Hint.PASSWORD

    private class CompositeAutofillDelegate(
        val platform: Autofill.Delegate?,
        val internal: Autofill.Delegate,
        val isPrivate: Boolean,
        private val isPlatformEnabled: () -> Boolean,
    ) : Autofill.Delegate {

        override fun onSessionStart(session: GeckoSession) {
            if (isPlatformEnabled()) runCatching { platform?.onSessionStart(session) }
            internal.onSessionStart(session)
        }

        override fun onSessionCommit(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled() && !isPrivate) {
                runCatching { platform?.onSessionCommit(session, node, data) }
            }
            internal.onSessionCommit(session, node, data)
        }

        override fun onSessionCancel(session: GeckoSession) {
            if (isPlatformEnabled()) runCatching { platform?.onSessionCancel(session) }
            internal.onSessionCancel(session)
        }

        override fun onNodeAdd(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled()) runCatching { platform?.onNodeAdd(session, node, data) }
            internal.onNodeAdd(session, node, data)
        }

        override fun onNodeRemove(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled()) runCatching { platform?.onNodeRemove(session, node, data) }
            internal.onNodeRemove(session, node, data)
        }

        override fun onNodeUpdate(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled()) runCatching { platform?.onNodeUpdate(session, node, data) }
            internal.onNodeUpdate(session, node, data)
        }

        override fun onNodeFocus(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled()) runCatching { platform?.onNodeFocus(session, node, data) }
            internal.onNodeFocus(session, node, data)
        }

        override fun onNodeBlur(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            if (isPlatformEnabled()) runCatching { platform?.onNodeBlur(session, node, data) }
            internal.onNodeBlur(session, node, data)
        }
    }

    private inner class InternalAutofillDelegate(
        private val sessionId: String,
        private val isPrivate: Boolean,
    ) : Autofill.Delegate {
        private var focusedUsername: String = ""

        override fun onNodeFocus(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) {
            if (!node.isLoginField()) return
            val host = hostOf(node.domain) ?: return
            focusJobs[sessionId]?.cancel()
            val job = scope.launch {
                val entries = logins.forHost(host)
                val fields = if (entries.isEmpty()) {
                    null
                } else {
                    runCatching { loginFields(session) }.getOrNull()
                }
                if (entries.isEmpty() || fields == null) {
                    clearSuggestion(sessionId)
                } else {
                    val suggestion = Suggestion(
                        sessionId = sessionId,
                        host = host,
                        entries = entries,
                        usernameId = fields.first,
                        passwordId = fields.second,
                    )
                    suggestionsBySession[sessionId] = suggestion
                    latestSessionId = sessionId
                    _suggestion.value = suggestion
                }
            }
            focusJobs[sessionId] = job
        }

        override fun onNodeBlur(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) {
            if (node.hint == Autofill.Hint.USERNAME || node.hint == Autofill.Hint.EMAIL_ADDRESS) {
                focusedUsername = data.value.orEmpty()
            }
        }

        override fun onSessionCommit(
            session: GeckoSession,
            node: Autofill.Node,
            data: Autofill.NodeData,
        ) {
            val capturedUsername = focusedUsername
            focusedUsername = ""
            if (isPrivate || !builtInPasswordSaveEnabled) return
            val autofillSession = session.autofillSession
            val nodes = walk(autofillSession.root)
            val passwordNode = nodes.firstOrNull { it.hint == Autofill.Hint.PASSWORD }
            val usernameNode = nodes.firstOrNull { it.isUsernameField() }
            val password = passwordNode?.let {
                runCatching { autofillSession.dataFor(it).value }.getOrNull()
            }.orEmpty()
            val username = capturedUsername.ifBlank {
                usernameNode?.let {
                    runCatching { autofillSession.dataFor(it).value }.getOrNull()
                }.orEmpty()
            }
            val host = hostOf(passwordNode?.domain ?: node.domain) ?: return
            if (password.isEmpty() || username.isBlank()) return
            scope.launch {
                if (logins.password(host, username) == password) return@launch
                _saveRequest.value = SaveRequest(host, username, password)
            }
        }

        override fun onSessionCancel(session: GeckoSession) {
            focusedUsername = ""
            clearSuggestion(sessionId)
        }
    }

    companion object {
        /** `http://www.example.com:80/login` becomes `example.com`. */
        fun hostOf(domain: String?): String? {
            val raw = domain?.trim()?.lowercase().orEmpty()
            if (raw.isEmpty()) return null
            return raw.substringAfter("://")
                .substringBefore('/')
                .substringBefore(':')
                .removePrefix("www.")
                .ifBlank { null }
        }
    }
}
