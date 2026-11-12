package com.roadar.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoadarNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardActionsAndNavigationOpenAllScreens() {
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
    fun automaticModeIsClearlyExperimentalAndCanBePaused() {
        composeRule.onNodeWithText("Start detection").performClick()
        composeRule.onNodeWithTag("reporting_mode_automatic")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Automatic reporting · Experimental")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Automatic persistence is paused", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("automatic_tracking_switch").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("automatic_tracking_switch").performClick()
        composeRule.onNodeWithTag("automatic_tracking_switch").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Automatically saved this session")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
