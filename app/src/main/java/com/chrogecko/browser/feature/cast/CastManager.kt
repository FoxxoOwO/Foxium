package com.chrogecko.browser.feature.cast

import android.content.Context
import androidx.core.net.toUri
import com.chrogecko.browser.core.engine.SystemExtensionIds
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

data class CastPlaybackState(
    val connected: Boolean = false,
    val deviceName: String? = null,
    val mediaTitle: String? = null,
    val mediaUrl: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class CastException(message: String) : IllegalStateException(message)

/**
 * Receives sniffed media from `sys_mediasniffer`, publishes it through the LAN proxy and drives
 * playback on the connected Chromecast.
 */
@Singleton
class CastManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bridge: NativeMessagingBridge,
    private val proxy: CastProxyServer,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var remote: RemoteMediaClient? = null

    private val _media = MutableStateFlow<Map<Long, List<CastMedia>>>(emptyMap())
    val media: StateFlow<Map<Long, List<CastMedia>>> = _media.asStateFlow()

    private val _playback = MutableStateFlow(CastPlaybackState())
    val playback: StateFlow<CastPlaybackState> = _playback.asStateFlow()

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { bridge.events.collect(::handleEvent) }
        scope.launch { initializeCast() }
    }

    fun clearMedia(tabId: Long) {
        _media.update { it - tabId }
    }

    suspend fun cast(media: CastMedia): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        val castSession = session ?: castContext?.sessionManager?.currentCastSession
            ?: return@withContext Result.failure(CastException("Connect to a Cast device first"))
        val remoteClient = castSession.remoteMediaClient
            ?: return@withContext Result.failure(CastException("Cast session is not ready"))

        val proxiedUrl = withContext(Dispatchers.IO) { proxy.publish(media) }
        val contentType = when {
            media.isHls -> "application/x-mpegURL"
            media.isDash -> "application/dash+xml"
            media.mime.isNotBlank() -> media.mime
            else -> "video/mp4"
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, media.title.ifBlank { media.pageTitle })
            media.poster?.let { poster ->
                runCatching { addImage(WebImage(poster.toUri())) }
            }
        }
        val info = MediaInfo.Builder(proxiedUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .apply { if (media.durationMs > 0) setStreamDuration(media.durationMs) }
            .build()

        _playback.update { current ->
            current.copy(
                connected = true,
                deviceName = castSession.castDevice?.friendlyName,
                mediaTitle = media.title,
                mediaUrl = proxiedUrl,
                durationMs = media.durationMs,
                positionMs = 0L,
            )
        }

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .build()
        val loaded = awaitLoad(remoteClient.load(request))
        if (loaded) {
            updatePlayback()
            Result.success(Unit)
        } else {
            val message = "Chromecast rejected ${contentType}"
            _errors.tryEmit(message)
            updatePlayback() // roll back the optimistic "connected" state
            Result.failure(CastException(message))
        }
    }

    /** Screen mirroring is published on the same LAN server as the media proxy. */
    suspend fun publishMirror(): String = proxy.publishMirror()

    fun stopMirror() {
        MirrorFrames.clear()
        proxy.clearMirror()
    }

    fun play() {
        remote?.play()
    }

    fun pause() {
        remote?.pause()
    }

    fun seek(positionMs: Long) {
        remote?.seek(
            com.google.android.gms.cast.MediaSeekOptions.Builder()
                .setPosition(positionMs)
                .build(),
        )
    }

    fun stopPlayback() {
        remote?.stop()
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    fun release() {
        remote?.unregisterCallback(remoteCallback)
        castContext?.sessionManager?.removeSessionManagerListener(
            sessionListener,
            CastSession::class.java,
        )
        proxy.stop()
    }

    private suspend fun initializeCast() {
        // CastContext initialization must run on the main thread; the executor runs module loading off-thread.
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                val task = CastContext.getSharedInstance(context, Dispatchers.IO.asExecutor())
                suspendCancellableCoroutine { continuation ->
                    task.addOnSuccessListener { cast ->
                        if (continuation.isActive) continuation.resume(cast)
                    }
                    task.addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }.onSuccess { cast ->
                castContext = cast
                cast.sessionManager.addSessionManagerListener(
                    sessionListener,
                    CastSession::class.java,
                )
                bindSession(cast.sessionManager.currentCastSession)
            }.onFailure { error ->
                _errors.tryEmit("Google Cast unavailable: ${error.message}")
            }
        }
    }

    private fun bindSession(castSession: CastSession?) {
        remote?.unregisterCallback(remoteCallback)
        session = castSession
        remote = runCatching { castSession?.remoteMediaClient }.getOrNull()
        remote?.registerCallback(remoteCallback)
        updatePlayback()
    }

    private fun updatePlayback() {
        val currentSession = session
        if (currentSession == null) {
            _playback.update {
                CastPlaybackState(connected = false)
            }
            return
        }
        val client = remote
        val status = client?.mediaStatus
        val info = status?.mediaInfo
        _playback.update { current ->
            current.copy(
                connected = true,
                deviceName = runCatching { currentSession.castDevice?.friendlyName }.getOrNull(),
                mediaTitle = info?.metadata?.getString(MediaMetadata.KEY_TITLE)
                    ?: current.mediaTitle,
                mediaUrl = info?.contentUrl ?: current.mediaUrl,
                playing = status?.playerState ==
                    com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING,
                positionMs = client?.approximateStreamPosition ?: current.positionMs,
                durationMs = info?.streamDuration ?: current.durationMs,
            )
        }
    }

    private fun handleEvent(event: com.chrogecko.browser.core.messaging.NativeEvent) {
        if (event.extensionId != SystemExtensionIds.MEDIASNIFFER) return
        when (event.type) {
            "media/snapshot" -> {
                val tabId = event.payload.optLong("tabId", -1L)
                if (tabId < 0) return
                val pageUrl = event.payload.optString("pageUrl")
                val pageTitle = event.payload.optString("pageTitle")
                val items = event.payload.optJSONArray("items") ?: JSONArray()
                val detected = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        CastMedia.fromJson(item, pageUrl, pageTitle)?.let(::add)
                    }
                }
                _media.update { it + (tabId to detected) }
            }
            "media/clear" -> {
                val tabId = event.payload.optLong("tabId", -1L)
                if (tabId >= 0) _media.update { it - tabId }
            }
        }
    }

    private suspend fun awaitLoad(
        result: PendingResult<RemoteMediaClient.MediaChannelResult>,
    ): Boolean = withTimeoutOrNull(CAST_LOAD_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { result.cancel() } }
            result.setResultCallback { mediaResult ->
                if (continuation.isActive) continuation.resume(mediaResult.status.isSuccess)
            }
        }
    } ?: false

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = updatePlayback()

        override fun onMetadataUpdated() = updatePlayback()

        override fun onMediaError(mediaError: com.google.android.gms.cast.MediaError) {
            _errors.tryEmit("Cast media error: ${mediaError}")
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(castSession: CastSession) = Unit

        override fun onSessionStarted(castSession: CastSession, sessionId: String) {
            bindSession(castSession)
        }

        override fun onSessionStartFailed(castSession: CastSession, error: Int) {
            _errors.tryEmit("Cast session failed: $error")
            bindSession(null)
            proxy.stop()
        }

        override fun onSessionEnding(castSession: CastSession) = Unit

        override fun onSessionEnded(castSession: CastSession, error: Int) {
            bindSession(null)
            // Nothing can be streaming once the session is gone, so the LAN server must not stay
            // listening on a port for the rest of the process; the next cast re-binds it in publish().
            proxy.stop()
        }

        override fun onSessionResuming(castSession: CastSession, sessionId: String) = Unit

        override fun onSessionResumed(castSession: CastSession, wasSuspended: Boolean) {
            bindSession(castSession)
        }

        override fun onSessionResumeFailed(castSession: CastSession, error: Int) {
            _errors.tryEmit("Cast resume failed: $error")
        }

        override fun onSessionSuspended(castSession: CastSession, reason: Int) = Unit
    }

    private companion object {
        const val CAST_LOAD_TIMEOUT_MS = 15_000L
    }
}
