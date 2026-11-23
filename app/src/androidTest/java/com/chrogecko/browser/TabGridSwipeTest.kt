package com.chrogecko.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the tab switcher's swipe-to-close gesture end to end, in any locale. */
@RunWith(AndroidJUnit4::class)
class TabGridSwipeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun swipingTabCardClosesItAndOffersUndo() {
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.tab_grid_tabs_label),
        ).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithTag("tab_card")[0].performTouchInput { swipeLeft() }

        compose.onNodeWithText(compose.activity.getString(R.string.undo)).assertIsDisplayed()
    }

    @Test
    fun tabClosedSnackbarCanBeSwipedAway() {
        compose.onNodeWithContentDescription(
            compose.activity.getString(R.string.tab_grid_tabs_label),
        ).performClick()
        compose.waitForIdle()

        compose.onAllNodesWithTag("tab_card")[0].performTouchInput { swipeLeft() }
        val message = compose.activity.getString(R.string.msg_tab_closed)
        compose.onNodeWithText(message).assertIsDisplayed()

        compose.onNodeWithText(message).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(
            0,
            compose.onAllNodesWithText(compose.activity.getString(R.string.undo)).fetchSemanticsNodes().size,
        )
    }
}
