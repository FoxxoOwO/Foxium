package com.chrogecko.browser.core.engine

import android.util.SparseArray
import com.chrogecko.browser.core.database.repository.LoginRepository
import com.chrogecko.browser.core.database.repository.SavedLogin
import com.chrogecko.browser.core.preferences.AppPreferences
import java.lang.ref.WeakReference
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

    private var suggestionSession: WeakReference<GeckoSession>? = null
    private var focusJob: Job? = null

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
    ) {
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
            ?: InternalAutofillDelegate(isPrivate)
        geckoSession.autofillDelegate = CompositeAutofillDelegate(
            platform = resolvedPlatform,
            internal = internal,
            isPrivate = isPrivate,
            isPlatformEnabled = { androidAutofillEnabled },
        )
    }

    fun detach(geckoSession: GeckoSession) {
        if (suggestionSession?.get() === geckoSession) {
            suggestionSession = null
            _suggestion.value = null
        }
        if (geckoSession.autofillDelegate is CompositeAutofillDelegate) {
            geckoSession.autofillDelegate = null
        }
    }

    /** Fills the picked login into the focused form. */
    fun fill(entry: SavedLogin) {
        val geckoSession = suggestionSession?.get() ?: return
        val fields = _suggestion.value ?: return
        if (fields.usernameId == null && fields.passwordId == null) return
        scope.launch {
            val password = logins.password(entry.id) ?: return@launch
            val values = SparseArray<CharSequence>()
            fields.usernameId?.let { values.put(it, entry.username) }
            fields.passwordId?.let { values.put(it, password) }
            geckoSession.autofillSession.autofill(values)
            suggestionSession = null
            _suggestion.value = null
        }
    }

    fun save() {
        val request = _saveRequest.value ?: return
        _saveRequest.value = null
        scope.launch { logins.save(request.host, request.username, request.password) }
    }

    fun dismissSuggestion() {
        suggestionSession = null
        _suggestion.value = null
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
        private val isPrivate: Boolean,
    ) : Autofill.Delegate {
        private var focusedUsername: String = ""

        override fun onNodeFocus(session: GeckoSession, node: Autofill.Node, data: Autofill.NodeData) {
            if (!node.isLoginField()) return
            val host = hostOf(node.domain) ?: return
            focusJob?.cancel()
            focusJob = scope.launch {
                val entries = logins.forHost(host)
                if (entries.isEmpty()) {
                    suggestionSession = null
                    _suggestion.value = null
                } else {
                    val fields = loginFields(session)
                    suggestionSession = WeakReference(session)
                    _suggestion.value = Suggestion(host, entries, fields?.first, fields?.second)
                }
            }
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
            val password = passwordNode?.let { autofillSession.dataFor(it).value }.orEmpty()
            val username = capturedUsername.ifBlank {
                usernameNode?.let { autofillSession.dataFor(it).value }.orEmpty()
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
            if (suggestionSession?.get() === session) {
                suggestionSession = null
                _suggestion.value = null
            }
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
