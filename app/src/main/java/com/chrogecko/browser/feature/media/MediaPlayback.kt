package com.chrogecko.browser.feature.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a page is playing right now. [features] mirrors `MediaSession.Feature`. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val playing: Boolean,
    val features: Long,
    val positionMs: Long,
    val durationMs: Long,
)

/**
 * Bridge between the Gecko media session delegate and [MediaPlaybackService].
 * Gecko owns playback, the service only renders the notification and routes
 * transport buttons back into the callbacks stored here.
 */
@SuppressLint("StaticFieldLeak")
object MediaPlayback {

    private data class Callbacks(
        val play: () -> Unit,
        val pause: () -> Unit,
        val stop: () -> Unit,
        val next: () -> Unit,
        val previous: () -> Unit,
        val seekTo: (Long) -> Unit,
    )

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val callbacksByOwner = ConcurrentHashMap<String, Callbacks>()
    private val statesByOwner = ConcurrentHashMap<String, NowPlaying>()

    @Volatile
    private var activeOwnerId: String? = null

    @Volatile
    var serviceRunning: Boolean = false

    val play: (() -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.play }
    val pause: (() -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.pause }
    val stop: (() -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.stop }
    val next: (() -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.next }
    val previous: (() -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.previous }
    val seekTo: ((Long) -> Unit)? get() = activeOwnerId?.let { callbacksByOwner[it]?.seekTo }

    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun bind(
        ownerId: String,
        play: () -> Unit,
        pause: () -> Unit,
        stop: () -> Unit,
        next: () -> Unit,
        previous: () -> Unit,
        seekTo: (Long) -> Unit,
    ) {
        callbacksByOwner[ownerId] = Callbacks(play, pause, stop, next, previous, seekTo)
        // Binding a new session must never steal ownership from one that is genuinely playing.
        if (activeOwnerId == null) activeOwnerId = ownerId
    }

    /** Called on every Gecko callback so the notification follows the owner that started playing. */
    fun update(ownerId: String, item: NowPlaying) {
        val previous = statesByOwner[ownerId]
        statesByOwner[ownerId] = item
        val active = activeOwnerId
        when {
            item.playing && previous?.playing != true && active != ownerId -> {
                // Only a false→true transition may take over the notification.
                activeOwnerId = ownerId
                _state.value = item
            }
            active == ownerId -> {
                _state.value = item
                if (!item.playing) electNextPlayingOwner(ownerId)
            }
            else -> return // a background tab's state must not replace the active one
        }
        if (!serviceRunning && _state.value?.playing == true) startService()
    }

    private fun electNextPlayingOwner(excluding: String) {
        val replacement = statesByOwner.entries.firstOrNull {
            it.key != excluding && it.value.playing
        } ?: return
        activeOwnerId = replacement.key
        _state.value = replacement.value
    }

    /** A media element stopped, but its session can still resume: keep transport callbacks. */
    fun stopOwner(ownerId: String?) {
        if (ownerId == null || activeOwnerId != ownerId) return
        statesByOwner[ownerId]?.let { statesByOwner[ownerId] = it.copy(playing = false) }
        _state.value = _state.value?.copy(playing = false)
        if (statesByOwner.values.none { it.playing }) {
            serviceRunning = false
            context?.let { ctx ->
                runCatching { ctx.stopService(Intent(ctx, MediaPlaybackService::class.java)) }
            }
        }
    }

    /** The page stopped having a media session at all; tear the notification down. */
    fun release(ownerId: String? = null) {
        if (ownerId != null) {
            callbacksByOwner.remove(ownerId)
            statesByOwner.remove(ownerId)
            if (activeOwnerId != null && activeOwnerId != ownerId) return
            electNextPlayingOwner(ownerId)
            if (activeOwnerId != null) return
        } else {
            callbacksByOwner.clear()
            statesByOwner.clear()
        }
        activeOwnerId = null
        _state.value = null
        // Mark stopped before requesting the stop: otherwise a playing update in the gap sees
        // serviceRunning == true, skips startForegroundService, and loses its notification.
        serviceRunning = false
        val ctx = context ?: return
        runCatching { ctx.stopService(Intent(ctx, MediaPlaybackService::class.java)) }
    }

    private fun startService() {
        val ctx = context ?: return
        runCatching { ctx.startForegroundService(Intent(ctx, MediaPlaybackService::class.java)) }
    }
}
