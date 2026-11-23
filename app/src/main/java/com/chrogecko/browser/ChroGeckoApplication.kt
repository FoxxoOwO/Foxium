package com.chrogecko.browser

import android.app.Application
import android.os.Build
import android.os.Process
import com.chrogecko.browser.core.engine.ChroGeckoRuntime
import com.chrogecko.browser.core.engine.SystemExtensionsInstaller
import com.chrogecko.browser.core.preferences.AppLanguage
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.core.preferences.AppPreferences
import com.chrogecko.browser.feature.addons.AddonManagerController
import com.chrogecko.browser.feature.cast.CastManager
import com.chrogecko.browser.feature.darkmode.DarkModeFeature
import com.chrogecko.browser.feature.expander.TextExpanderFeature
import com.chrogecko.browser.feature.reader.ReaderModeFeature
import com.chrogecko.browser.feature.userscripts.UserScriptFeature
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltAndroidApp
class ChroGeckoApplication : Application() {

    @Inject
    lateinit var runtime: Lazy<ChroGeckoRuntime>

    @Inject
    lateinit var preferences: Lazy<AppPreferences>

    @Inject
    lateinit var systemExtensions: Lazy<SystemExtensionsInstaller>

    @Inject
    lateinit var addons: Lazy<AddonManagerController>

    @Inject
    lateinit var userscripts: Lazy<UserScriptFeature>

    @Inject
    lateinit var darkMode: Lazy<DarkModeFeature>

    @Inject
    lateinit var textExpander: Lazy<TextExpanderFeature>

    @Inject
    lateinit var readerMode: Lazy<ReaderModeFeature>

    @Inject
    lateinit var cast: Lazy<CastManager>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Gecko spawns :tabN/:gpu/:crashhelper services that share this Application.
        // Creating the runtime, installing extensions or touching Cast there blocks
        // their startup until the system kills them, which also kills the browser.
        if (currentProcessName() != packageName) return
        val chroGeckoRuntime = runtime.get()
        chroGeckoRuntime.warmUp()
        systemExtensions.get().installAll()
        addons.get().start()
        userscripts.get().start()
        darkMode.get().start()
        textExpander.get().start()
        readerMode.get().start()
        applicationScope.launch(Dispatchers.Default) {
            cast.get().start()
        }
        applicationScope.launch {
            preferences.get().preferredColorScheme.collectLatest(chroGeckoRuntime::applyColorScheme)
        }
        applicationScope.launch {
            preferences.get().appLanguage.collectLatest { tag ->
                chroGeckoRuntime.applyLocales(
                    AppLocaleManager.geckoLocalesFor(AppLanguage.fromTag(tag)),
                )
            }
        }
    }

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching {
                java.io.File("/proc/${Process.myPid()}/cmdline").readText().trim('\u0000')
            }.getOrDefault(packageName)
        }
}
