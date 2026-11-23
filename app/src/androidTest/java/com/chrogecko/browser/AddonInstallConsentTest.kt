package com.chrogecko.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrogecko.browser.feature.addons.PendingAddonInstall
import com.chrogecko.browser.feature.chromeui.AddonInstallDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks in the audit fix that no extension is installed without an explicit confirmation: the dialog
 * has to show what the extension asks for, and only its own buttons may answer the prompt.
 */
@RunWith(AndroidJUnit4::class)
class AddonInstallConsentTest {

    @get:Rule
    val compose = createComposeRule()

    private val pending = PendingAddonInstall(
        name = "uBlock Origin",
        version = "1.60.0",
        permissions = listOf("Access your data for all websites"),
        origins = listOf("https://*/*"),
        dataCollection = emptyList(),
    )

    private fun show(onInstall: () -> Unit, onDismiss: () -> Unit) {
        compose.setContent {
            MaterialTheme {
                AddonInstallDialog(pending = pending, onInstall = onInstall, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun dialogShowsTheRequestedPermissionsAndOrigins() {
        show(onInstall = {}, onDismiss = {})

        compose.onNodeWithText("uBlock Origin 1.60.0").assertIsDisplayed()
        compose.onNodeWithText("Access your data for all websites").assertIsDisplayed()
        compose.onNodeWithText("https://*/*").assertIsDisplayed()
    }

    @Test
    fun installButtonAccepts() {
        var installed = false
        var dismissed = false
        show(onInstall = { installed = true }, onDismiss = { dismissed = true })

        val installLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.install)
        compose.onNodeWithText(installLabel).performClick()

        assertTrue(installed)
        assertFalse(dismissed)
    }

    @Test
    fun cancelButtonRefuses() {
        var installed = false
        var dismissed = false
        show(onInstall = { installed = true }, onDismiss = { dismissed = true })

        val cancelLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cancel)
        compose.onNodeWithText(cancelLabel).performClick()

        assertTrue(dismissed)
        assertFalse(installed)
    }
}
