package com.chrogecko.browser

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chrogecko.browser.core.preferences.AppLocaleManager
import com.chrogecko.browser.feature.chromeui.ChromeBrowserScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Links handed over by other apps (and by pinned shortcuts); cleared once navigated. */
    private val externalUrl = MutableStateFlow<String?>(null)

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
        enableEdgeToEdge()
        // Read the intent even when saved state exists: after process death Android restores the
        // activity with a non-null Bundle but does not call onNewIntent, so an ACTION_VIEW link
        // would otherwise be lost.
        externalUrl.value = intent?.dataString
        setContent {
            val url by externalUrl.collectAsState()
            ChromeBrowserScreen(
                externalUrl = url,
                onExternalUrlConsumed = { externalUrl.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalUrl.value = intent.dataString
    }
}
