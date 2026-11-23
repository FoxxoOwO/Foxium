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
        if (activeOwnerId == null || _state.value?.playing != true) {
            activeOwnerId = ownerId
        }
    }

    /** Called on every Gecko callback so the notification follows playback. */
    fun update(ownerId: String, item: NowPlaying) {
        if (activeOwnerId != null && activeOwnerId != ownerId && _state.value?.playing == true && !item.playing) {
            return
        }
        if (item.playing || activeOwnerId == null) {
            activeOwnerId = ownerId
        }
        if (activeOwnerId != ownerId) return
        _state.value = item
        if (!serviceRunning && item.playing) {
            startService()
        }
    }

    /** The page stopped having a media session at all; tear the notification down. */
    fun release(ownerId: String? = null) {
        if (ownerId != null) {
            callbacksByOwner.remove(ownerId)
            if (activeOwnerId != null && activeOwnerId != ownerId) return
        } else {
            callbacksByOwner.clear()
        }
        activeOwnerId = null
        _state.value = null
        val ctx = context ?: return
        runCatching { ctx.stopService(Intent(ctx, MediaPlaybackService::class.java)) }
    }

    private fun startService() {
        val ctx = context ?: return
        runCatching { ctx.startForegroundService(Intent(ctx, MediaPlaybackService::class.java)) }
    }
}
