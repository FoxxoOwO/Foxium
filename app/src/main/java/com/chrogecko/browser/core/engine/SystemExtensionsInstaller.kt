package com.chrogecko.browser.core.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chrogecko.browser.core.messaging.NativeMessagingBridge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the privileged WebExtensions that ship inside the APK. GeckoView grants built-in
 * extensions their requested permissions at install time, which is what lets the userscript,
 * dark-mode and media-sniffing engines run without AMO signing.
 */
@Singleton
class SystemExtensionsInstaller @Inject constructor(
    private val chroGeckoRuntime: ChroGeckoRuntime,
    private val bridge: NativeMessagingBridge,
) {

    fun installAll() {
        chroGeckoRuntime.warmUp()
        val controller = chroGeckoRuntime.runtime.webExtensionController
        for ((path, id) in BUILT_IN_EXTENSIONS) {
            // ensureBuiltIn is idempotent: a fresh install on every process start restarts the
            // background scripts and races with native port setup for no benefit.
            controller.ensureBuiltIn(path, id).accept(
                { extension ->
                    if (extension != null) {
                        bridge.register(extension)
                        Log.i(TAG, "Installed ${extension.id}@${extension.metaData.version}")
                    }
                },
                { error -> Log.e(TAG, "Failed to install $path", error) },
            )
        }
        // A built-in extension can boot its background script before Gecko processes the
        // message-delegate registration, so register again once its runtime is settled.
        Handler(Looper.getMainLooper()).postDelayed({
            controller.list().accept(
                { extensions ->
                    extensions?.forEach { ext ->
                        if (ext.id in SystemExtensionIds.ALL && !bridge.isConnected(ext.id)) {
                            bridge.register(ext, force = true)
                        }
                    }
                },
                { error -> Log.e(TAG, "Could not list extensions", error) },
            )
        }, 3000)
    }

    companion object {
        private const val TAG = "ChroGeckoExtensions"

        val BUILT_IN_EXTENSIONS = listOf(
            "resource://android/assets/extensions/sys_userscripts/" to SystemExtensionIds.USERSCRIPTS,
            "resource://android/assets/extensions/sys_darkmode/" to SystemExtensionIds.DARKMODE,
            "resource://android/assets/extensions/sys_mediasniffer/" to SystemExtensionIds.MEDIASNIFFER,
            "resource://android/assets/extensions/sys_gestures/" to SystemExtensionIds.GESTURES,
            "resource://android/assets/extensions/sys_autofill/" to SystemExtensionIds.AUTOFILL,
            "resource://android/assets/extensions/sys_expander/" to SystemExtensionIds.EXPANDER,
            "resource://android/assets/extensions/sys_reader/" to SystemExtensionIds.READER,
        )
    }
}
