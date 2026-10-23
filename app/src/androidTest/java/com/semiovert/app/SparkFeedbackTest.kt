package com.semiovert.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import org.junit.Rule
import org.junit.Test

class SparkFeedbackTest {
    @get:Rule val app=createComposeRule()

    @Test fun sparksOutliveTheClickedRouteForBothAuthorEntriesAndMaterialControls() {
        var page by mutableIntStateOf(0)
        app.setContent {
            SoTheme {
                SparkFeedbackHost {
                    if(page==0)Box(Modifier.size(100.dp).sparkClickable {page=1}.testTag("author-entry"))
                    else if(page==1)SparkFeedbackScope {
                        val spark=rememberSparkControl()
                        Button(onClick={page=2},interactionSource=spark.source,modifier=spark.modifier.testTag("settings-button")) {Text("Next")}
                    }
                    else Text("Destination")
                }
            }
        }
        app.waitForIdle();app.mainClock.autoAdvance=false
        try {
            for(tag in listOf("author-entry","settings-button")) {
                app.onNodeWithTag(tag).performTouchInput {click()}
                app.mainClock.advanceTimeBy(100)
                app.onNodeWithTag(tag).assertDoesNotExist()
                app.onNodeWithTag("spark-feedback").assertExists()
                app.mainClock.advanceTimeBy(450)
                app.onNodeWithTag("spark-feedback").assertDoesNotExist()
            }
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun disabledControlsCancelledDragsAndReducedMotionDoNotEmitSparks() {
        var motion by mutableStateOf(true)
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides motion) {
                    SparkFeedbackHost {SparkFeedbackScope {
                        Column {
                            val disabled=rememberSparkControl();val enabled=rememberSparkControl()
                            Button(onClick={},enabled=false,interactionSource=disabled.source,modifier=disabled.modifier.testTag("disabled")) {Text("Disabled")}
                            Button(onClick={},interactionSource=enabled.source,modifier=enabled.modifier.testTag("enabled")) {Text("Enabled")}
                        }
                    }}
                }
            }
        }
        app.waitForIdle();app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("disabled").performTouchInput {click()}
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("spark-feedback").assertDoesNotExist()
            app.onNodeWithTag("enabled").performTouchInput {down(center);moveTo(center.copy(y=center.y+200f));up()}
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("spark-feedback").assertDoesNotExist()
            app.runOnIdle {motion=false}
            app.onNodeWithTag("enabled").performTouchInput {click()}
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("spark-feedback").assertDoesNotExist()
        } finally {app.mainClock.autoAdvance=true}
    }
}
