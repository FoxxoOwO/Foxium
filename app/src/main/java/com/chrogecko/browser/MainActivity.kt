package com.chrogecko.browser

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chrogecko.browser.core.engine.ChroGeckoRuntime
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.feature.chromeui.ChromeBrowserScreen
import com.chrogecko.browser.feature.updates.AppUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var chroGeckoRuntime: ChroGeckoRuntime

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    /** Links handed over by other apps (and by pinned shortcuts); cleared once navigated. */
    private val externalUrl = MutableStateFlow<String?>(null)
    private var pendingWebAuthnResult: GeckoResult<Intent>? = null

    private val webAuthnLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingWebAuthnResult ?: return@registerForActivityResult
            pendingWebAuthnResult = null
            val data = result.data
            // The platform/GMS failure is delivered as a serializable extra; surface it so the
            // page sees the real NotAllowedError/SecurityError instead of a generic cancellation.
            val error = data?.let {
                runCatching {
                    IntentCompat.getSerializableExtra(
                        it,
                        "androidx.activity.result.contract.extra.ACTIVITY_RESULT_ERROR",
                        Throwable::class.java,
                    )
                }.getOrNull()
            }
            when {
                error != null -> pending.completeExceptionally(error)
                result.resultCode == RESULT_OK -> pending.complete(data)
                else -> pending.completeExceptionally(
                    CancellationException("WebAuthn cancelled (code=${result.resultCode})"),
                )
            }
        }

    private val runtimeActivityDelegate = GeckoRuntime.ActivityDelegate { pendingIntent ->
        // Matches the official GeckoView example: launch first, then create and store the result.
        // The delegate is @UiThread already, so no extra UI posting (which could race onDestroy).
        if (pendingWebAuthnResult != null) {
            GeckoResult.fromException<Intent>(
                IllegalStateException("Another WebAuthn request is already in progress"),
            )
        } else {
            val geckoResult = GeckoResult<Intent>()
            try {
                webAuthnLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                )
                pendingWebAuthnResult = geckoResult
                geckoResult
            } catch (error: IllegalStateException) {
                GeckoResult.fromException<Intent>(error)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val localized = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppLocaleManager.wrapContext(newBase, AppLocaleManager.readSavedTag(newBase))
        } else {
            newBase
        }
        super.attachBaseContext(localized)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chroGeckoRuntime.runtime.activityDelegate = runtimeActivityDelegate
        if (intent?.action == AppUpdateManager.ACTION_INSTALL_UPDATE) {
            appUpdateManager.installFromNotification()
            // Avoid re-launching the installer after a configuration change.
            intent?.let { setIntent(Intent(it).setAction(null)) }
        }
        enableEdgeToEdge()
        // Read the intent even when saved state exists: after process death Android restores the
        // activity with a non-null Bundle but does not call onNewIntent, so an ACTION_VIEW link
        // would otherwise be lost.
        externalUrl.value = intent?.dataString
        setContent {
            val url by externalUrl.collectAsState()
            ChromeBrowserScreen(
                externalUrl = url,
                onExternalUrlConsumed = {
                    externalUrl.value = null
                    intent?.let { current ->
                        if (current.data != null) {
                            setIntent(Intent(current).setData(null))
                        }
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalUrl.value = intent.dataString
    }

    override fun onDestroy() {
        pendingWebAuthnResult?.completeExceptionally(
            CancellationException("Activity destroyed"),
        )
        pendingWebAuthnResult = null
        if (::chroGeckoRuntime.isInitialized &&
            chroGeckoRuntime.runtime.activityDelegate === runtimeActivityDelegate
        ) {
            chroGeckoRuntime.runtime.activityDelegate = null
        }
        super.onDestroy()
    }
}
