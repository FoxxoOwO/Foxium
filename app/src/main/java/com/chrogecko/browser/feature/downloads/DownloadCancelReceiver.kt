package com.chrogecko.browser.feature.downloads

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Backs the "Cancel" action of the download progress notification. Not exported: only the
 * notification's own PendingIntent targets it.
 */
class DownloadCancelReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadEntryPoint {
        fun downloadManager(): DownloadManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = EntryPointAccessors
            .fromApplication(context, DownloadEntryPoint::class.java)
            .downloadManager()
        when (intent.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                if (id > 0) manager.cancel(id)
            }
            ACTION_OPEN -> intent.getStringExtra(EXTRA_PATH)?.let(manager::openFile)
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.chrogecko.browser.action.CANCEL_DOWNLOAD"
        const val ACTION_OPEN = "com.chrogecko.browser.action.OPEN_DOWNLOAD"
        const val EXTRA_ID = "download_id"
        const val EXTRA_PATH = "download_path"
    }
}
