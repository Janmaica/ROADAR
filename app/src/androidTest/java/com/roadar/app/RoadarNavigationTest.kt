package com.roadar.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("No local reports yet").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_map").performClick()
        composeRule.onNodeWithText("Map is not implemented").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_settings").performClick()
        composeRule.onNodeWithText("Detection model").assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_home").performClick()
        composeRule.onNodeWithText("Start detection").assertIsDisplayed()
        composeRule.onNodeWithText("View reports").performClick()
        composeRule.onNodeWithText("No local reports yet").assertIsDisplayed()
    }
}
