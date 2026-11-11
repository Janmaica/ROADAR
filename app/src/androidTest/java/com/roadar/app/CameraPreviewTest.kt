package com.roadar.app

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.roadar.app.ai.YOLO26_MODEL_ASSET
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rearCameraPreviewStreamsAfterPermissionIsGranted() {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(
                "pm grant com.roadar.app ${Manifest.permission.CAMERA}"
            )
            .close()

        composeRule.onNodeWithText("Start detection").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Preview active")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        verifyCameraInferenceMetricsWhenModelIsBundled()

        composeRule.onNodeWithTag("navigation_reports").performClick()
        composeRule.onNodeWithTag("navigation_detection").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Preview active")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun verifyCameraInferenceMetricsWhenModelIsBundled() {
        val hasModel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .assets
            .list("")
            ?.contains(YOLO26_MODEL_ASSET) == true
        if (!hasModel) return

        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag("detection_metric_inference")
                .fetchSemanticsNodes()
                .any { node ->
                    node.config.contains(SemanticsProperties.Text) &&
                        node.config[SemanticsProperties.Text].any { it.text.endsWith(" ms") }
                }
        }
    }
}
