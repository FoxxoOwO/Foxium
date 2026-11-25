package com.chrogecko.browser.core.messaging

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

data class NativeEvent(
    val extensionId: String,
    val type: String,
    val payload: JSONObject,
)

/**
 * Native Messaging bridge between privileged bundled extensions and Kotlin.
 *
 * Extensions connect with `browser.runtime.connectNative("chrogecko")`; every message must be a
 * JSON object with a `type` field. Kotlin answers through [post].
 */
@Singleton
class NativeMessagingBridge @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ports = ConcurrentHashMap<String, WebExtension.Port>()
    /** One delegate per extension *instance*: GeckoView can return a new object for the same id. */
    private val registrations = ConcurrentHashMap<String, Registration>()

    private class Registration(
        val extension: WebExtension,
        val delegate: WebExtension.MessageDelegate,
    )

    private val _events = MutableSharedFlow<NativeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<NativeEvent> = _events.asSharedFlow()

    private val _connectedExtensions = MutableStateFlow<Set<String>>(emptySet())
    val connectedExtensions: StateFlow<Set<String>> = _connectedExtensions.asStateFlow()

    fun register(extension: WebExtension, force: Boolean = false) {
        // setMessageDelegate is @UiThread but GeckoResult callbacks land elsewhere.
        mainHandler.post {
            val previous = registrations[extension.id]
            if (!force && previous?.extension === extension) return@post
            if (previous != null) {
                // Replacing a delegate does not disconnect ports created by the old one. Always
                // clear the delegate on the *old instance*, not on the new one.
                ports.remove(extension.id)?.let { old ->
                    runCatching { old.setDelegate(null) }
                    runCatching { old.disconnect() }
                }
                runCatching { previous.extension.setMessageDelegate(null, APPLICATION_NAME) }
                _connectedExtensions.update { it - extension.id }
            }
            val delegate = PortMessageDelegate(extension.id)
            registrations[extension.id] = Registration(extension, delegate)
            extension.setMessageDelegate(delegate, APPLICATION_NAME)
        }
    }

    fun post(extensionId: String, payload: JSONObject): Boolean {
        Log.d(TAG, "app -> $extensionId ${payload.optString("type")}")
        fun deliver(): Boolean {
            // Resolve the port on the main thread: a captured reference may already have been
            // displaced by a reconnect, and Gecko silently drops posts to a disconnected port.
            val port = ports[extensionId] ?: return false
            val ok = runCatching { port.postMessage(payload); true }.getOrDefault(false)
            if (!ok) {
                ports.remove(extensionId, port)
                if (!ports.containsKey(extensionId)) {
                    _connectedExtensions.update { it - extensionId }
                }
            }
            return ok
        }
        // A background caller cannot wait for the main-thread delivery, so "true" only means a
        // live port existed when the message was queued.
        return if (Looper.myLooper() == Looper.getMainLooper()) deliver() else {
            val queued = ports.containsKey(extensionId)
            if (queued) mainHandler.post { deliver() }
            queued
        }
    }

    fun isConnected(extensionId: String): Boolean = ports.containsKey(extensionId)

    private inner class PortMessageDelegate(
        private val extensionId: String,
    ) : WebExtension.MessageDelegate {

        override fun onConnect(port: WebExtension.Port) {
            val previous = ports.put(extensionId, port)
            if (previous != null && previous !== port) {
                // A background-script restart opens a new port. Keep one live channel per
                // extension or both ports keep emitting the same events into the bridge.
                runCatching { previous.setDelegate(null) }
                runCatching { previous.disconnect() }
            }
            Log.i(TAG, "port connected: $extensionId")
            _connectedExtensions.update { it + extensionId }
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val payload = message as? JSONObject ?: return
                    val type = payload.optString("type")
                    if (type.isEmpty()) return
                    Log.d(TAG, "${extensionId.removeSuffix("@chrogecko.browser")} -> $type")
                    _events.tryEmit(NativeEvent(extensionId, type, payload))
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    ports.remove(extensionId, port)
                    // A fast reconnect may already have stored a replacement port; keep the
                    // connected state in that case.
                    if (!ports.containsKey(extensionId)) {
                        _connectedExtensions.update { it - extensionId }
                    }
                }
            })
        }

        override fun onMessage(
            message: String,
            data: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? = null
    }

    companion object {
        const val APPLICATION_NAME = "chrogecko"
        private const val TAG = "ChroGeckoMessaging"
    }
}
