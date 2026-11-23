package com.chrogecko.browser.feature.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.chrogecko.browser.BuildConfig
import com.chrogecko.browser.core.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    ) : UpdateState

    data class ReadyToInstall(
        val update: AvailableUpdate,
        val apkFile: File,
        val needsInstallPermission: Boolean = false,
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}

/**
 * Checks GitHub Releases for newer Foxium builds, downloads the ABI-matched APK into
 * app cache (`cacheDir/updates`), and hands it off to Android's package installer via FileProvider.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val preferences: AppPreferences,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val downloading = AtomicBoolean(false)

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

    suspend fun downloadUpdate(update: AvailableUpdate) {
        if (!AppUpdateParser.isTrustedHttpsUrl(update.apkUrl)) {
            _state.value = UpdateState.Error("Untrusted download URL")
            return
        }
        if (!downloading.compareAndSet(false, true)) return

        try {
            _state.value = UpdateState.Downloading(
                update = update,
                downloadedBytes = 0L,
                totalBytes = update.apkSizeBytes,
            )

            val downloadResult = try {
                val file = withContext(Dispatchers.IO) {
                    val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                    updatesDir.listFiles()?.forEach { oldFile ->
                        if (oldFile.isFile) oldFile.delete()
                    }

                    val safeName = update.apkName
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .ifBlank { "foxium-update.apk" }
                    val partFile = File(updatesDir, "$safeName.part")
                    val targetFile = File(updatesDir, safeName)

                    val request = Request.Builder()
                        .url(update.apkUrl)
                        .header("User-Agent", "Foxium/${BuildConfig.VERSION_NAME}")
                        .get()
                        .build()

                    httpClient.newCall(request).execute().use { response ->
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
                        var lastReportMs = 0L

                        body.byteStream().use { input ->
                            FileOutputStream(partFile).use { output ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastReportMs >= 150L) {
                                        lastReportMs = now
                                        _state.value = UpdateState.Downloading(
                                            update = update,
                                            downloadedBytes = downloaded,
                                            totalBytes = totalBytes,
                                        )
                                    }
                                }
                                output.flush()
                            }
                        }

                        _state.value = UpdateState.Downloading(
                            update = update,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes.coerceAtLeast(downloaded),
                        )
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        throw IOException("Could not finalize update file")
                    }
                    targetFile
                }
                Result.success(file)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            }

            downloadResult.fold(
                onSuccess = { apkFile ->
                    val canInstall = context.packageManager.canRequestPackageInstalls()
                    _state.value = UpdateState.ReadyToInstall(
                        update = update,
                        apkFile = apkFile,
                        needsInstallPermission = !canInstall,
                    )
                    if (canInstall) {
                        launchInstaller(apkFile)
                    }
                },
                onFailure = { error ->
                    _state.value = UpdateState.Error(error.message ?: "Download failed")
                },
            )
        } finally {
            downloading.set(false)
        }
    }

    fun launchInstaller(file: File): Boolean {
        if (!file.exists()) {
            _state.value = UpdateState.Error("Update package missing")
            return false
        }
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
        when (val current = _state.value) {
            is UpdateState.Available -> if (current.update.tagName == tagName) {
                _state.value = current.copy(bannerDismissed = true)
            }
            is UpdateState.ReadyToInstall -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    companion object {
        const val RELEASES_API_URL =
            "https://api.github.com/repos/FoxxoOwO/Foxium/releases?per_page=10"
        private const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    }
}
