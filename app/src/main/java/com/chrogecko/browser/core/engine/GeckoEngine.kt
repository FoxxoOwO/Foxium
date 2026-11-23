package com.chrogecko.browser.core.engine

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every live GeckoSession. Each tab in the UI maps 1:1 to an [EngineSession].
 * Context ids separate normal and private browsing storage.
 */
@Singleton
class GeckoEngine @Inject constructor(
    private val runtime: ChroGeckoRuntime,
    private val autofill: AutofillController,
) {

    private val sessions = ConcurrentHashMap<String, EngineSession>()

    /** Mirrors the user's privacy preference; new sessions start with it and existing ones adopt it. */
    @Volatile
    private var trackingProtection = true

    fun createSession(isPrivate: Boolean = false, openNow: Boolean = true): EngineSession {
        val contextId = if (isPrivate) PRIVATE_CONTEXT_ID else DEFAULT_CONTEXT_ID
        val session = EngineSession(
            contextId = contextId,
            isPrivate = isPrivate,
            runtime = runtime.runtime,
            appContext = runtime.appContext,
            autofill = autofill,
            openNow = openNow,
            trackingProtection = trackingProtection,
        )
        sessions[session.id] = session
        return session
    }

    fun session(id: String): EngineSession? = sessions[id]

    fun applyTrackingProtection(enabled: Boolean) {
        trackingProtection = enabled
        sessions.values.forEach { it.setTrackingProtection(enabled) }
    }

    fun closeSession(id: String) {
        sessions.remove(id)?.close()
    }

    fun closeAll() {
        sessions.values.forEach(EngineSession::close)
        sessions.clear()
    }

    companion object {
        const val DEFAULT_CONTEXT_ID = "chrogecko-default"
        const val PRIVATE_CONTEXT_ID = "chrogecko-private"
    }
}
