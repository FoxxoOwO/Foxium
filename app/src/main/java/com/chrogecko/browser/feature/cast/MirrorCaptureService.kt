package com.chrogecko.browser.feature.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.createBitmap
import com.chrogecko.browser.MainActivity
import com.chrogecko.browser.R
import java.io.ByteArrayOutputStream

/**
 * Mirrors the screen into [MirrorFrames] so [CastProxyServer] can serve it as MJPEG.
 * Frames are half resolution and capped at [MAX_LATENCY_MS] apart, which keeps the
 * stream around a few Mbit/s on a phone screen.
 */
class MirrorCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var reusablePadded: Bitmap? = null
    private val reusableOut = ByteArrayOutputStream()
    private var lastFrameAt = 0L
    private var firstFrameLogged = false

    private fun ensureHandler(): Handler {
        val currentThread = thread
        val currentHandler = workerHandler
        if (currentThread != null && currentThread.isAlive && currentHandler != null) {
            return currentHandler
        }
        val worker = HandlerThread("chrogecko-mirror").also { it.start() }
        val nextHandler = Handler(worker.looper)
        thread = worker
        workerHandler = nextHandler
        return nextHandler
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            release()
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        val startedForeground = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                serviceType,
            )
        }.onFailure { error ->
            Log.w(TAG, "cannot start foreground: ${error.message}")
        }.isSuccess
        if (!startedForeground) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val capture = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (capture == null) {
            Log.w(TAG, "media projection denied")
            stopSelf()
            return START_NOT_STICKY
        }
        val mirrorHandler = ensureHandler()
        capture.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // The system (or the user via the status bar) revoked the projection: drop the
                    // service and the foreground notification instead of leaving a dead stream.
                    release()
                    stopSelf()
                }
            },
            mirrorHandler,
        )
        projection = capture
        MirrorFrames.markRunning()
        startCapture(capture, mirrorHandler)
        return START_NOT_STICKY
    }

    private fun startCapture(capture: MediaProjection, mirrorHandler: Handler) {
        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels / 2).coerceAtLeast(2) and 1.inv()
        val height = (metrics.heightPixels / 2).coerceAtLeast(2)
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull()
            if (image == null) {
                Log.w(TAG, "image available but null")
                return@setOnImageAvailableListener
            }
            try {
                if (!MirrorFrames.hasViewers() && MirrorFrames.latest() != null) {
                    return@setOnImageAvailableListener
                }
                val now = System.currentTimeMillis()
                if (now - lastFrameAt >= MAX_LATENCY_MS) {
                    lastFrameAt = now
                    val jpeg = image.toJpeg(width, height)
                    if (jpeg == null) {
                        Log.w(TAG, "jpeg encode returned null")
                    } else {
                        if (firstFrameLogged.not()) {
                            firstFrameLogged = true
                            Log.i(TAG, "first frame ${jpeg.size} bytes")
                        }
                        MirrorFrames.publish(jpeg)
                    }
                }
            } finally {
                image.close()
            }
        }, mirrorHandler)
        virtualDisplay = capture.createVirtualDisplay(
            "chrogecko-mirror",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            mirrorHandler,
        )
        Log.i(TAG, "virtual display=${virtualDisplay?.display?.displayId} ${width}x$height")
    }

    private fun Image.toJpeg(width: Int, height: Int): ByteArray? = runCatching {
        val plane = planes.first()
        val rowPixels = plane.rowStride / plane.pixelStride
        val padded = reusablePadded?.takeIf {
            !it.isRecycled && it.width == rowPixels && it.height == height
        } ?: createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888).also {
            reusablePadded?.recycle()
            reusablePadded = it
        }
        padded.copyPixelsFromBuffer(plane.buffer)
        val frame = if (rowPixels == width) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, width, height)
        }
        reusableOut.reset()
        frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, reusableOut)
        if (frame !== padded) frame.recycle()
        reusableOut.toByteArray()
    }.onFailure { Log.w(TAG, "frame encode failed: ${it.message}") }.getOrNull()

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    /** Teardown must run on the same worker that encodes frames, otherwise a recycle/close can
     * happen in the middle of toJpeg() and crash the process. */
    @Synchronized
    private fun release() {
        MirrorFrames.clear()
        val handler = workerHandler
        val workerThread = thread
        val vd = virtualDisplay
        val rdr = reader
        val proj = projection
        virtualDisplay = null
        reader = null
        projection = null
        workerHandler = null
        thread = null
        lastFrameAt = 0L
        firstFrameLogged = false
        if (handler != null && workerThread != null && workerThread.isAlive) {
            if (Looper.myLooper() === handler.looper) {
                releaseResources(vd, rdr, proj)
                handler.post { workerThread.quitSafely() }
            } else {
                handler.post {
                    releaseResources(vd, rdr, proj)
                    // Let already queued callbacks finish before quitting the looper.
                    handler.post { workerThread.quitSafely() }
                }
            }
        } else {
            releaseResources(vd, rdr, proj)
            workerThread?.quitSafely()
        }
    }

    private fun releaseResources(
        vd: VirtualDisplay?,
        rdr: ImageReader?,
        proj: MediaProjection?,
    ) {
        runCatching { vd?.release() }
        runCatching { rdr?.close() }
        runCatching { proj?.stop() }
        if (thread == null) {
            reusablePadded?.recycle()
            reusablePadded = null
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "chrogecko-mirror"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Screen mirroring", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, MirrorCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.mirror_notification_title))
            .setContentText(getString(R.string.mirror_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_launcher, getString(R.string.stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "ChroGeckoMirror"
        private const val NOTIFICATION_ID = 0x4d52
        private const val JPEG_QUALITY = 55
        private const val MAX_LATENCY_MS = 140L
        private const val ACTION_STOP = "com.chrogecko.browser.mirror.STOP"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_DATA = "resultData"

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MirrorCaptureService::class.java)) }
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MirrorCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            // The consent activity may still be finishing, so a foreground start can be
            // rejected for a few hundred ms; retry instead of dropping the projection.
            attemptStart(context, intent, ATTEMPTS)
        }

        private const val ATTEMPTS = 5

        private fun attemptStart(context: Context, intent: Intent, remaining: Int) {
            runCatching { context.startForegroundService(intent) }
                .onSuccess { return }
                .onFailure { error ->
                    Log.w(TAG, "cannot start service: ${error.message}")
                    if (remaining > 1) {
                        Handler(Looper.getMainLooper()).postDelayed(
                            { attemptStart(context, intent, remaining - 1) },
                            400,
                        )
                    }
                }
        }
    }
}
