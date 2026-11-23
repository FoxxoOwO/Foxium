package com.chrogecko.browser.feature.cast

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Latest screen frame captured by [MirrorCaptureService], handed to the Ktor MJPEG endpoint.
 * One frame at a time is enough: every viewer pulls whatever is current.
 */
object MirrorFrames {

    private val current = AtomicReference<ByteArray?>(null)
    private val viewerCount = AtomicInteger(0)

    @Volatile
    var updatedAt: Long = 0L
        private set

    private val _running = MutableStateFlow(false)

    /** True while [MirrorCaptureService] holds a live MediaProjection. */
    val running: StateFlow<Boolean> = _running

    fun viewerJoined(): Int = viewerCount.incrementAndGet()

    fun viewerLeft(): Int = viewerCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }

    fun hasViewers(): Boolean = viewerCount.get() > 0

    fun markRunning() {
        _running.value = true
    }

    fun publish(jpeg: ByteArray) {
        current.set(jpeg)
        updatedAt = System.currentTimeMillis()
    }

    fun latest(): ByteArray? = current.get()

    fun clear() {
        current.set(null)
        updatedAt = 0L
        viewerCount.set(0)
        _running.value = false
    }
}
