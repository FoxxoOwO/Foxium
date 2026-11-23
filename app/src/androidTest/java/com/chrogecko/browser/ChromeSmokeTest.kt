package com.chrogecko.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests against the real activity: the GeckoView runtime, the bundled extensions and Room are
 * all started by the production application class, so a launch here exercises the whole boot path.
 * Strings are resolved from resources so the suite also passes under a non-English locale.
 */
@RunWith(AndroidJUnit4::class)
class ChromeSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun newTabPageShowsTheOmniboxPlaceholder() {
        compose.onNodeWithText(compose.activity.getString(R.string.omnibox_placeholder)).assertIsDisplayed()
    }

    @Test
    fun typingIntoTheOmniboxKeepsTheTypedText() {
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement("example.com")
        compose.waitForIdle()
        // Target the editable field: a suggestion row can also contain the same text.
        compose.onNode(hasSetTextAction()).assertTextEquals("example.com")
    }

    @Test
    fun openingSettingsShowsPrivacyControls() {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.cd_menu)).performClick()
        compose.waitForIdle()

        val settingsTitle = activity.getString(R.string.settings_title)
        compose.onNodeWithText(settingsTitle).performScrollTo().performClick()
        compose.waitForIdle()

        val cookiesTitle = activity.getString(R.string.settings_cookies_title)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(cookiesTitle))
        compose.onNodeWithText(cookiesTitle).assertIsDisplayed().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(activity.getString(R.string.settings_cookies_policy_header)).assertIsDisplayed()
    }
}
