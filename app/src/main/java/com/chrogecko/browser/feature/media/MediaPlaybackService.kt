package com.chrogecko.browser.feature.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.chrogecko.browser.MainActivity
import com.chrogecko.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the playback notification and lock-screen controls alive
 * while a page plays media. Gecko remains the player; this service only presents the
 * [MediaSession] and forwards transport buttons back through [MediaPlayback].
 */
class MediaPlaybackService : Service() {

    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current: NowPlaying? = null
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        MediaPlayback.serviceRunning = true
        createChannel()
        session = MediaSession(this, "Foxium").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() = MediaPlayback.play?.invoke() ?: Unit
                    override fun onPause() = MediaPlayback.pause?.invoke() ?: Unit
                    override fun onStop() = MediaPlayback.stop?.invoke() ?: Unit
                    override fun onSkipToNext() = MediaPlayback.next?.invoke() ?: Unit
                    override fun onSkipToPrevious() = MediaPlayback.previous?.invoke() ?: Unit
                    override fun onSeekTo(pos: Long) = MediaPlayback.seekTo?.invoke(pos) ?: Unit
                },
            )
            isActive = true
            setSessionActivity(openAppIntent())
        }
        scope.launch {
            var sawState = false
            MediaPlayback.state.collect { state ->
                if (state == null && !sawState) return@collect
                sawState = true
                current = state
                if (state == null) {
                    shutdown()
                } else {
                    present(state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (current?.playing == true) {
                MediaPlayback.pause?.invoke()
            } else {
                MediaPlayback.play?.invoke()
            }
            ACTION_NEXT -> MediaPlayback.next?.invoke()
            ACTION_PREVIOUS -> MediaPlayback.previous?.invoke()
            ACTION_STOP_SELF -> {
                if (!foreground) {
                    startMinimalForeground()
                }
                shutdown()
            }
            else -> {
                val state = current ?: MediaPlayback.state.value
                if (state != null) {
                    current = state
                    present(state)
                } else {
                    if (!foreground) {
                        startMinimalForeground()
                    }
                    shutdown()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        MediaPlayback.serviceRunning = false
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun startMinimalForeground() {
        val notification = buildNotification(
            NowPlaying(
                title = getString(R.string.media_playing),
                artist = "",
                album = "",
                playing = false,
                features = 0L,
                positionMs = 0L,
                durationMs = 0L,
            ),
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundType(),
        )
        foreground = true
    }

    private fun present(state: NowPlaying) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.title.ifBlank { getString(R.string.media_playing) })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, state.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actionsFor(state.features, state.durationMs))
                .setState(
                    if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.positionMs,
                    if (state.playing) 1f else 0f,
                )
                .build(),
        )
        val notification = buildNotification(state)
        if (foreground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } else {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType(),
            )
            foreground = true
        }
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

    private fun shutdown() {
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf()
    }

    private fun actionsFor(features: Long, durationMs: Long): Long {
        var actions = 0L
        val any = features == 0L
        if (any || features and FEATURE_PLAY != 0L) {
            actions = actions or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE
        }
        if (any || features and FEATURE_STOP != 0L) actions = actions or PlaybackState.ACTION_STOP
        if (any || features and FEATURE_NEXT != 0L) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        if (any || features and FEATURE_PREVIOUS != 0L) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (durationMs > 0 && (any || features and FEATURE_SEEK != 0L)) {
            actions = actions or PlaybackState.ACTION_SEEK_TO
        }
        return actions
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(state: NowPlaying): Notification {
        val toggle = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val prev = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(state.title.ifBlank { getString(R.string.media_playing) })
            .setContentText(state.artist.ifBlank { state.album })
            .setContentIntent(openAppIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(state.playing)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    getString(R.string.media_previous),
                    prev,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (state.playing) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play,
                    getString(if (state.playing) R.string.pause else R.string.play),
                    toggle,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(android.R.drawable.ic_media_next, getString(R.string.media_next), next).build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.media_playback_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "chrogecko-playback"
        private const val NOTIFICATION_ID = 0x4d50
        const val ACTION_TOGGLE = "com.chrogecko.browser.media.TOGGLE"
        const val ACTION_NEXT = "com.chrogecko.browser.media.NEXT"
        const val ACTION_PREVIOUS = "com.chrogecko.browser.media.PREVIOUS"
        const val ACTION_STOP_SELF = "com.chrogecko.browser.media.STOP"

        // Mirrors org.mozilla.geckoview.MediaSession.Feature
        private const val FEATURE_PLAY = 1L shl 0
        private const val FEATURE_STOP = 1L shl 2
        private const val FEATURE_SEEK = 1L shl 3
        private const val FEATURE_NEXT = 1L shl 7
        private const val FEATURE_PREVIOUS = 1L shl 8

        @Suppress("unused")
        fun start(context: android.content.Context) {
            context.startForegroundService(Intent(context, MediaPlaybackService::class.java))
        }
    }
}
