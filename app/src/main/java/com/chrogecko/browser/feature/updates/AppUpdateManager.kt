package com.chrogecko.browser.feature.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.chrogecko.browser.BuildConfig
import com.chrogecko.browser.MainActivity
import com.chrogecko.browser.R
import com.chrogecko.browser.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(
        val update: AvailableUpdate,
        val bannerDismissed: Boolean = false,
    ) : UpdateState

    data class Downloading(
        val update: AvailableUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val backgrounded: Boolean = false,
    ) : UpdateState

    data class ReadyToInstall(
        val update: AvailableUpdate,
        val apkFile: File,
        val needsInstallPermission: Boolean = false,
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}

/**
 * Checks GitHub Releases for newer Foxium builds, downloads the ABI-matched APK in a
 * singleton background scope into `cacheDir/updates` with status-bar notifications, and hands
 * it off to Android's package installer via FileProvider.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val preferences: AppPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _notificationPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val notificationPermissionRequests: SharedFlow<Unit> = _notificationPermissionRequests

    private val downloading = AtomicBoolean(false)

    @Volatile
    private var downloadJob: Job? = null

    @Volatile
    private var activeCall: Call? = null

    private val notificationManager: NotificationManager? by lazy {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.settings_updates_header),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager
    }

    suspend fun checkForUpdates(force: Boolean) {
        if (downloading.get() || _state.value is UpdateState.Downloading) return
        if (!force && _state.value is UpdateState.ReadyToInstall) return
        if (!force) {
            if (!preferences.autoUpdateEnabled.first()) return
            val lastCheck = preferences.lastUpdateCheckMs.first()
            if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_INTERVAL_MS) return
        } else {
            _state.value = UpdateState.Checking
        }

        val includePrereleases = preferences.autoUpdatePrereleases.first()
        val result = try {
            val body = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Foxium/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty response")
                }
            }
            preferences.setLastUpdateCheckMs(System.currentTimeMillis())
            val candidate = AppUpdateParser.parseLatestUpdate(
                jsonBody = body,
                currentVersion = BuildConfig.VERSION_NAME,
                includePrereleases = includePrereleases,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            )
            Result.success(candidate)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }

        result.fold(
            onSuccess = { update ->
                if (update == null) {
                    _state.value = if (force) UpdateState.UpToDate else UpdateState.Idle
                } else {
                    val dismissedTag = preferences.dismissedUpdateTag.first()
                    _state.value = UpdateState.Available(
                        update = update,
                        bannerDismissed = !force && dismissedTag == update.tagName,
                    )
                }
            },
            onFailure = { error ->
                if (force) {
                    _state.value = UpdateState.Error(error.message ?: "Update check failed")
                }
            },
        )
    }

    fun startDownload(update: AvailableUpdate) {
        if (!AppUpdateParser.isTrustedHttpsUrl(update.apkUrl)) {
            _state.value = UpdateState.Error("Untrusted download URL")
            return
        }
        if (!downloading.compareAndSet(false, true)) return
        ensureNotificationPermission()
        downloadJob = scope.launch {
            runDownload(update)
        }
    }

    fun hideDownloadToBackground() {
        val current = _state.value
        if (current is UpdateState.Downloading && !current.backgrounded) {
            _state.value = current.copy(backgrounded = true)
        }
    }

    fun cancelDownload() {
        activeCall?.cancel()
        downloadJob?.cancel()
    }

    private suspend fun runDownload(update: AvailableUpdate) {
        var partFile: File? = null
        try {
            _state.value = UpdateState.Downloading(
                update = update,
                downloadedBytes = 0L,
                totalBytes = update.apkSizeBytes,
                backgrounded = false,
            )
            notifyProgress(update, 0L, update.apkSizeBytes)

            val apkFile = withContext(Dispatchers.IO) {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updatesDir.listFiles()?.forEach { oldFile ->
                    if (oldFile.isFile) oldFile.delete()
                }

                val safeName = update.apkName
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "foxium-update.apk" }
                val tempFile = File(updatesDir, "$safeName.part")
                partFile = tempFile
                val targetFile = File(updatesDir, safeName)

                val request = Request.Builder()
                    .url(update.apkUrl)
                    .header("User-Agent", "Foxium/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                val call = httpClient.newCall(request)
                activeCall = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val finalUrl = response.request.url.toString()
                    if (!AppUpdateParser.isTrustedHttpsUrl(finalUrl)) {
                        throw IOException("Untrusted redirect URL")
                    }
                    val body = response.body ?: throw IOException("Empty download body")
                    val totalBytes = body.contentLength().takeIf { it > 0L } ?: update.apkSizeBytes
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var lastStateReportMs = 0L
                    var lastNotifyReportMs = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastStateReportMs >= 150L) {
                                    lastStateReportMs = now
                                    val bg = (_state.value as? UpdateState.Downloading)?.backgrounded == true
                                    _state.value = UpdateState.Downloading(
                                        update = update,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        backgrounded = bg,
                                    )
                                }
                                if (now - lastNotifyReportMs >= 500L) {
                                    lastNotifyReportMs = now
                                    notifyProgress(update, downloaded, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }

                    val bg = (_state.value as? UpdateState.Downloading)?.backgrounded == true
                    _state.value = UpdateState.Downloading(
                        update = update,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes.coerceAtLeast(downloaded),
                        backgrounded = bg,
                    )
                }

                coroutineContext.ensureActive()
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Could not finalize update file")
                }
                targetFile
            }

            val wasBackgrounded = (_state.value as? UpdateState.Downloading)?.backgrounded == true
            val canInstall = context.packageManager.canRequestPackageInstalls()
            _state.value = UpdateState.ReadyToInstall(
                update = update,
                apkFile = apkFile,
                needsInstallPermission = !canInstall,
            )
            notifyReadyToInstall(update)
            if (canInstall && !wasBackgrounded) {
                launchInstaller(apkFile)
            }
        } catch (ce: CancellationException) {
            runCatching { partFile?.delete() }
            cancelProgressNotification()
            _state.value = UpdateState.Available(update = update, bannerDismissed = false)
            throw ce
        } catch (t: Throwable) {
            runCatching { partFile?.delete() }
            cancelProgressNotification()
            _state.value = UpdateState.Error(t.message ?: "Download failed")
        } finally {
            activeCall = null
            downloadJob = null
            downloading.set(false)
        }
    }

    fun launchInstaller(file: File): Boolean {
        if (!file.exists()) {
            _state.value = UpdateState.Error("Update package missing")
            return false
        }
        notificationManager?.cancel(READY_NOTIFICATION_ID)
        val canInstall = context.packageManager.canRequestPackageInstalls()
        val current = _state.value
        if (!canInstall) {
            if (current is UpdateState.ReadyToInstall) {
                _state.value = current.copy(needsInstallPermission = true)
            }
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(permissionIntent)
                false
            } catch (error: ActivityNotFoundException) {
                _state.value = UpdateState.Error(error.message ?: "Cannot open install settings")
                false
            } catch (error: SecurityException) {
                _state.value = UpdateState.Error(error.message ?: "Cannot open install settings")
                false
            }
        }

        if (current is UpdateState.ReadyToInstall && current.needsInstallPermission) {
            _state.value = current.copy(needsInstallPermission = false)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(installIntent)
            true
        } catch (error: ActivityNotFoundException) {
            _state.value = UpdateState.Error(error.message ?: "No package installer found")
            false
        } catch (error: SecurityException) {
            _state.value = UpdateState.Error(error.message ?: "Cannot launch package installer")
            false
        }
    }

    suspend fun dismissUpdateBanner(tagName: String) {
        preferences.setDismissedUpdateTag(tagName)
        notificationManager?.cancel(READY_NOTIFICATION_ID)
        when (val current = _state.value) {
            is UpdateState.Available -> if (current.update.tagName == tagName) {
                _state.value = current.copy(bannerDismissed = true)
            }
            is UpdateState.ReadyToInstall -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _notificationPermissionRequests.tryEmit(Unit)
        }
    }

    private fun notifyProgress(update: AvailableUpdate, downloaded: Long, total: Long) {
        val manager = notificationManager ?: return
        val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        val text = if (total > 0L) {
            "$percent% · ${Formatter.formatShortFileSize(context, downloaded)} / ${Formatter.formatShortFileSize(context, total)}"
        } else {
            Formatter.formatShortFileSize(context, downloaded)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_notification_downloading, update.versionName))
            .setContentText(text)
            .setProgress(100, percent, total <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { manager.notify(PROGRESS_NOTIFICATION_ID, notification) }
    }

    private fun notifyReadyToInstall(update: AvailableUpdate) {
        val manager = notificationManager ?: return
        manager.cancel(PROGRESS_NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notification_ready_title, update.versionName))
            .setContentText(context.getString(R.string.update_notification_ready_text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { manager.notify(READY_NOTIFICATION_ID, notification) }
    }

    private fun cancelProgressNotification() {
        notificationManager?.cancel(PROGRESS_NOTIFICATION_ID)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        CONTENT_INTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val RELEASES_API_URL =
            "https://api.github.com/repos/FoxxoOwO/Foxium/releases?per_page=10"
        private const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val CHANNEL_ID = "foxium_updates"
        private const val PROGRESS_NOTIFICATION_ID = 70001
        private const val READY_NOTIFICATION_ID = 70002
        private const val CONTENT_INTENT_REQUEST_CODE = 70003
    }
}
