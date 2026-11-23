package com.chrogecko.browser.feature.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.OutputStream

/** Where finished downloads are stored. */
object DownloadStorage {

    const val PUBLIC_DOWNLOADS = "downloads"
    const val APP_PRIVATE = "app"

    data class Target(
        /** Stored in the database; either an absolute path or a content:// Uri. */
        val path: String,
        val output: OutputStream,
        val finish: () -> Unit,
    )

    fun create(context: Context, fileName: String, mime: String, location: String): Target {
        val safeName = sanitizeFileName(fileName)
        if (location == PUBLIC_DOWNLOADS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var inserted: android.net.Uri? = null
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: error("MediaStore insert returned null")
                inserted = uri
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: error("MediaStore output stream unavailable")
                return Target(
                    path = uri.toString(),
                    output = output,
                    finish = {
                        runCatching {
                            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                            context.contentResolver.update(uri, done, null, null)
                        }
                    },
                )
            }.onFailure {
                // Never leave an invisible IS_PENDING row behind when the stream cannot open.
                inserted?.let { uri ->
                    runCatching {
                        val pending = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                        context.contentResolver.update(uri, pending, null, null)
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            }
        }
        return fileTarget(context, safeName)
    }

    private fun fileTarget(context: Context, fileName: String): Target {
        val safeName = sanitizeFileName(fileName)
        val directory = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
        }
        directory.mkdirs()
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val file = synchronized(this) {
            var reserved: File? = null
            for (index in 0..999) {
                val candidate = File(directory, if (index == 0) safeName else "$base ($index)$extension")
                if (runCatching { candidate.createNewFile() }.getOrDefault(false)) {
                    reserved = candidate
                    break
                }
            }
            reserved ?: File(directory, safeName)
        }
        return Target(
            path = file.absolutePath,
            output = file.outputStream(),
            finish = {},
        )
    }

    private fun sanitizeFileName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val clean = leaf
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .trim()
            .trimStart('.')
        return if (clean.isEmpty() || clean == "." || clean == "..") "download" else clean
    }

    fun uriFor(context: Context, path: String): Uri =
        if (path.startsWith("content://")) {
            path.toUri()
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        }

    fun displayName(path: String): String =
        if (path.startsWith("content://")) {
            path.toUri().lastPathSegment ?: path
        } else {
            File(path).name
        }

    fun delete(context: Context, path: String) {
        runCatching {
            if (path.startsWith("content://")) {
                context.contentResolver.delete(path.toUri(), null, null)
            } else {
                File(path).delete()
            }
        }
    }
}
