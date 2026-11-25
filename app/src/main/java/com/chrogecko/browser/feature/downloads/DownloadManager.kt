package com.chrogecko.browser.feature.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chrogecko.browser.MainActivity
import com.chrogecko.browser.R
import com.chrogecko.browser.core.database.entity.DownloadEntity
import com.chrogecko.browser.core.database.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.geckoview.WebResponse

/**
 * Streams Gecko's external responses to the app's Downloads directory and records them in Room.
 */
@Singleton
class DownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val preferences: com.chrogecko.browser.core.preferences.AppPreferences,
    baseHttpClient: OkHttpClient,
) {
    val downloads: Flow<List<DownloadEntity>> = repository.downloads

    private val _notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when a download needs the notification permission the app has not been granted yet. */
    val notificationPermissionRequests: SharedFlow<Unit> = _notificationPermissionRequests

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadLocation = com.chrogecko.browser.feature.downloads.DownloadStorage.PUBLIC_DOWNLOADS

    init {
        scope.launch { preferences.downloadLocation.collect { downloadLocation = it } }
    }

    private val jobs = ConcurrentHashMap<Long, Job>()

    private val fileLock = Any()

    private val http: OkHttpClient = baseHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Returns immediately; the response body is consumed on IO and always closed. */
    fun enqueue(response: WebResponse) {
        val mime = header(response, "content-type")?.substringBefore(';')?.trim()
            .orEmpty().ifBlank { "application/octet-stream" }
        val fileName = fileName(response)
        val declaredSize = (header(response, "content-length")?.toLongOrNull() ?: 0L).coerceAtLeast(0L)
        val body = response.body
        if (body == null) {
            // Keep a visible failed row: silently returning makes "Download link" look broken.
            val uri = response.uri
            scope.launch {
                val id = repository.start(uri, fileName, mime)
                repository.failed(id, context.getString(R.string.msg_download_empty_response))
            }
            return
        }
        scope.launch {
            streamToFile(
                uri = response.uri,
                fileName = fileName,
                mime = mime,
                declaredSize = declaredSize,
                body = body,
                onCancel = { runCatching { body.close() } },
            )
        }
    }

    /**
     * Downloads a link or image the user picked in the long-press menu. Those URLs never pass
     * through Gecko's response handler, so they are fetched here with the page as Referer.
     */
    fun enqueueUrl(url: String, referer: String?) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            // Mirror other failures: keep a visible failed row instead of doing nothing.
            scope.launch {
                val id = repository.start(trimmed, fileNameFor(trimmed, null), "application/octet-stream")
                repository.failed(id, context.getString(R.string.msg_invalid_url))
            }
            return
        }
        scope.launch {
            val request = Request.Builder().url(trimmed).apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
            }.build()
            val call = http.newCall(request)
            val response = runCatching { call.execute() }.getOrNull()
            if (response == null || !response.isSuccessful) {
                response?.close()
                val id = repository.start(url, fileNameFor(url, null), "application/octet-stream")
                repository.failed(id, "Could not reach the server")
                return@launch
            }
            val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
                .orEmpty().ifBlank { "application/octet-stream" }
            val body = response.body
            if (body == null) {
                val id = repository.start(url, fileNameFor(url, null), mime)
                repository.failed(id, context.getString(R.string.msg_download_empty_response))
                response.close()
                return@launch
            }
            val finalUrl = response.request.url.toString()
            val name = fileNameFor(finalUrl, response.header("Content-Disposition"))
            val total = body.contentLength().coerceAtLeast(0L)
            try {
                streamToFile(
                    uri = finalUrl,
                    fileName = name,
                    mime = mime,
                    declaredSize = total,
                    body = body.byteStream(),
                    onCancel = { call.cancel() },
                )
            } finally {
                response.close()
            }
        }
    }

    private suspend fun CoroutineScope.streamToFile(
        uri: String,
        fileName: String,
        mime: String,
        declaredSize: Long,
        body: InputStream,
        onCancel: (() -> Unit)? = null,
    ) {
        val safeDeclaredSize = declaredSize.coerceAtLeast(0L)
        val id = repository.start(uri, fileName, mime)
        // Asked here instead of at launch: a permission dialog on the first screen would cover the
        // new tab page, and Chrome only asks when there is something to notify about.
        ensureNotificationPermission()
        val currentJob = coroutineContext[Job] ?: return
        jobs[id] = currentJob
        val cancelHook = onCancel?.let { hook ->
            currentJob.invokeOnCompletion { cause -> if (cause is CancellationException) hook() }
        }
        var target: DownloadStorage.Target? = null
        try {
            val destination = DownloadStorage.create(context, fileName, mime, downloadLocation)
            target = destination
            var written = 0L
            var reported = 0L
            var notified = 0L
            notifyProgress(id, fileName, 0, safeDeclaredSize)
            body.use { input ->
                destination.output.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (written - reported >= 256 * 1024) {
                            reported = written
                            repository.progress(id, written, safeDeclaredSize)
                        }
                        if (written - notified >= 512 * 1024) {
                            notified = written
                            notifyProgress(id, fileName, written, safeDeclaredSize)
                        }
                    }
                }
            }
            coroutineContext.ensureActive()
            destination.finish()
            repository.completed(id, if (written > 0) written else safeDeclaredSize, destination.path)
            android.util.Log.i("ChroGeckoDownloads", "completed id=$id size=$written")
            notifyCompleted(id, fileName, destination.path)
        } catch (error: CancellationException) {
            notificationManager?.cancel(id.toInt())
            target?.let { DownloadStorage.delete(context, it.path) }
            withContext(NonCancellable) {
                repository.cancelled(id)
            }
            throw error
        } catch (error: Exception) {
            notificationManager?.cancel(id.toInt())
            target?.let { DownloadStorage.delete(context, it.path) }
            repository.failed(id, error.message ?: "Download failed")
        } finally {
            cancelHook?.dispose()
            jobs.remove(id)
        }
    }

    fun cancel(id: Long) {
        notificationManager?.cancel(id.toInt())
        notificationManager?.cancel(id.toInt() + COMPLETED_NOTIFICATION_OFFSET)
        val job = jobs[id]
        if (job != null && job.isActive) {
            job.cancel()
            return
        }
        // A job that had not started yet would never run the cancellation catch block. Never
        // overwrite an already finished download - that would hide a file that exists on disk.
        scope.launch {
            if (repository.find(id)?.status == DownloadEntity.STATUS_RUNNING) {
                repository.cancelled(id)
            }
        }
    }

    fun openFile(path: String) {
        val uri = DownloadStorage.uriFor(context, path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = android.content.ClipData.newRawUri(DownloadStorage.displayName(path), uri)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Progress notification with Chrome's cancel action; the completion one replaces it. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        _notificationPermissionRequests.tryEmit(Unit)
    }

    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun notifyProgress(id: Long, fileName: String, written: Long, total: Long) {
        if (!notificationsGranted()) return
        val manager = notificationManager ?: return
        val percent = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
        val cancel = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, DownloadCancelReceiver::class.java)
                .setAction(DownloadCancelReceiver.ACTION_CANCEL)
                .putExtra(DownloadCancelReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(
                if (total > 0) {
                    "${percent}% · ${formatBytes(written)} / ${formatBytes(total)}"
                } else {
                    formatBytes(written)
                },
            )
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.cancel), cancel)
            .build()
        runCatching { manager.notify(id.toInt(), notification) }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val notificationManager: NotificationManager? by lazy {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.downloads_title),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager
    }

    private fun notifyCompleted(id: Long, fileName: String, path: String) {
        val manager = notificationManager ?: return
        val completed = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getBroadcast(
            context,
            id.toInt() + OPEN_REQUEST_OFFSET,
            Intent(context, DownloadCancelReceiver::class.java)
                .setAction(DownloadCancelReceiver.ACTION_OPEN)
                .putExtra(DownloadCancelReceiver.EXTRA_PATH, path),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(fileName)
            .setContentIntent(completed)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.open), open)
        // A separate id keeps the finished file from ever colliding with a progress update
        // that the system is still holding for the same download.
        manager.cancel(id.toInt())
        val completedId = id.toInt() + COMPLETED_NOTIFICATION_OFFSET
        runCatching { manager.notify(completedId, builder.build()) }
    }

    fun delete(id: Long) {
        scope.launch {
            val download = repository.find(id)
            download?.localPath?.let { path -> DownloadStorage.delete(context, path) }
            repository.delete(id)
        }
    }

    fun clear() {
        scope.launch {
            repository.all().forEach { download ->
                download.localPath?.let { path -> DownloadStorage.delete(context, path) }
            }
            repository.clear()
        }
    }

    private fun fileName(response: WebResponse): String =
        fileNameFor(response.uri, header(response, "content-disposition"))

    private fun header(response: WebResponse, name: String): String? =
        response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val OPEN_REQUEST_OFFSET = 1_000_000
        private const val COMPLETED_NOTIFICATION_OFFSET = 500_000

        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = listOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024
            var index = 0
            while (value >= 1024 && index < units.lastIndex) {
                value /= 1024
                index++
            }
            return String.format(java.util.Locale.US, "%.1f %s", value, units[index])
        }

        private val ILLEGAL_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        private val ENCODED_FILENAME_REGEX =
            Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)?''([^;]+)""")
        private val PLAIN_FILENAME_REGEX = Regex("""filename\s*=\s*"?([^";]+)"?""")

        internal fun fileNameFor(uri: String, disposition: String?): String {
            val fromHeader = disposition?.let(::dispositionFileName)
            val raw = fromHeader
                ?: uri.substringBefore('#').substringBefore('?').substringAfterLast('/')
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            val safe = buildString(decoded.length) {
                decoded.forEach { char ->
                    append(if (char.code < 32 || char in ILLEGAL_NAME_CHARS) '_' else char)
                }
            }.trim()
            val cleaned = if (safe.isBlank() || safe.all { it == '.' }) "download.bin" else safe
            return cleaned.take(120)
        }

        private fun dispositionFileName(header: String): String? {
            val encoded = ENCODED_FILENAME_REGEX.find(header)
            if (encoded != null) return encoded.groupValues[1].trim().trim('"')
            val plain = PLAIN_FILENAME_REGEX.find(header)
            return plain?.groupValues?.get(1)?.trim()
        }
    }

}
