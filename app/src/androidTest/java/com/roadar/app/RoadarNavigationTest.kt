package com.roadar.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoadarNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardActionsAndNavigationOpenAllScreens() {
        composeRule.onNodeWithText("ROADAR").assertIsDisplayed()
        composeRule.onNodeWithText("Smart pothole detection").assertIsDisplayed()
        composeRule.onNodeWithText("Start detection").performClick()
        composeRule.onNodeWithText("Camera preview").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_reports").performClick()
        composeRule.onNodeWithText("Pothole reports").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_map").performClick()
        if (BuildConfig.MAPTILER_API_KEY.isBlank()) {
            composeRule.onNodeWithText("Online basemap is not configured").assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag("roadar_interactive_map").assertIsDisplayed()
        }
        composeRule.onNodeWithText("Road map").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_settings").performClick()
        composeRule.onNodeWithText("Detection model").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_home").performClick()
        composeRule.onNodeWithText("Start detection").assertIsDisplayed()
        composeRule.onNodeWithText("View reports").performClick()
        composeRule.onNodeWithText("Pothole reports").assertIsDisplayed()
    }

    @Test
    fun detectionOffersOnlyManualReporting() {
        composeRule.onNodeWithText("Start detection").performClick()
        composeRule.onNodeWithText("Manual reporting")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("GPS")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Reporting mode")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("AUTOMATIC · EXPERIMENTAL")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Test
    fun firebaseSyncStartsOffAndEnableRequiresConfirmation() {
        composeRule.onNodeWithTag("navigation_settings").performClick()
        val syncSwitch = composeRule.onNodeWithTag("firebase_sync_switch")
        syncSwitch.assertIsOff()

        if (BuildConfig.FIREBASE_CONFIGURED) {
            syncSwitch.performClick()
            composeRule.onNodeWithText("Enable Firebase Sync?").assertIsDisplayed()
            composeRule.onNodeWithText(
                "Only reports created after you enable sync may be uploaded. " +
                    "Existing reports will remain local. New eligible report details " +
                    "and later deletions will sync to the configured Firebase project."
            ).assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            syncSwitch.assertIsOff()
        }
    }
}
