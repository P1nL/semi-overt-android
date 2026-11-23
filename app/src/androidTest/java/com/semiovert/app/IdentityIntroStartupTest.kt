package com.semiovert.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.semiovert.app.core.designsystem.BrandIntroMotion
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.LocalIntroPreparation
import com.semiovert.app.core.designsystem.LocalIntroReady
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IdentityIntroStartupTest {
    @get:Rule val app=createComposeRule()

    @Test fun theFirstFrameHasStarInkAndWebViewReadinessDoesNotDelayDrawing() {
        var drawn=false
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {IdentityIntroHost(false,0) {
                val preparation=LocalIntroPreparation.current
                SideEffect {preparation?.backgroundReady=false}
                Box(Modifier.fillMaxSize().drawWithContent {drawContent();drawn=true})
            }}
        }
        val firstInk=inkPixels()
        assertTrue("The first frame shows all four strokes starting",firstInk>0)
        app.runOnIdle {assertTrue("Home is drawn underneath the opening cover",drawn)}
        app.mainClock.advanceTimeBy(160)
        assertTrue("The star grows while the WebView is still preparing",inkPixels()>firstInk)
    }

    @Test fun completedWritingReturnsBeforeRealHomeIsRevealedAndTappable() {
        var revealed=false
        var taps=0
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme {IdentityIntroHost(false,0) {
                val ready=LocalIntroReady.current
                SideEffect {revealed=ready}
                Box(Modifier.fillMaxSize().background(Color.Red)
                    .clickable {taps++}.testTag("intro-home"))
            }}
        }
        app.mainClock.advanceTimeBy(2_800)
        app.runOnIdle {assertFalse(revealed)}
        app.onRoot().performTouchInput {click(center)}
        app.runOnIdle {assertEquals(0,taps)}
        app.mainClock.advanceTimeBy(1_350)
        app.runOnIdle {assertTrue(revealed)}
        val passing=app.onRoot().captureToImage().toPixelMap()
        val centerPixel=passing[passing.width/2,(passing.height*.42f).toInt()]
        assertTrue("The aperture exposes the real red home",centerPixel.red>.9f&&centerPixel.green<.1f)
        assertTrue("The corner is still covered while passing through",passing[5,5].green>.8f)
        app.onNodeWithTag("identity-intro").assertExists()
        app.mainClock.advanceTimeBy(800)
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        app.onNodeWithTag("intro-home").performTouchInput {click(center)}
        app.runOnIdle {assertEquals(1,taps)}
    }

    @Test fun unfinishedHomeHoldsTheClosedStarUntilItsLayoutIsReady() {
        val ready=mutableStateOf(false)
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme {IdentityIntroHost(false,0) {
                val preparation=LocalIntroPreparation.current
                val done=ready.value
                SideEffect {preparation?.ready=done}
                Box(Modifier.fillMaxSize().background(Color.Red))
            }}
        }
        app.mainClock.advanceTimeBy(6_000)
        app.onNodeWithTag("identity-intro").assertExists()
        val held=app.onRoot().captureToImage().toPixelMap()
        assertTrue("The closed star still covers home",held[held.width/2,(held.height*.42f).toInt()].green>.8f)
        app.runOnIdle {ready.value=true}
        app.mainClock.advanceTimeBy(BrandIntroMotion.PORTAL_MILLIS+64L)
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
    }

    @Test fun decorativeBackgroundCannotKeepTheOpeningOnScreenIndefinitely() {
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme {IdentityIntroHost(false,0) {
                val preparation=LocalIntroPreparation.current
                SideEffect {preparation?.backgroundReady=false}
                Box(Modifier.fillMaxSize())
            }}
        }
        app.mainClock.advanceTimeBy(3_800)
        app.onNodeWithTag("identity-intro").assertExists()
        app.waitUntil(5_000) {
            app.mainClock.advanceTimeBy(64)
            app.onAllNodesWithTag("identity-intro").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun disabledMotionShowsHomeWithoutAnOpeningCover() {
        app.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                SoTheme {IdentityIntroHost(false,0) {Box(Modifier.fillMaxSize().testTag("intro-home"))}}
            }
        }
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        app.onNodeWithTag("intro-home").assertIsDisplayed()
    }

    @Test fun restoredCompletedOpeningDoesNotReplay() {
        val restoration=StateRestorationTester(app)
        app.mainClock.autoAdvance=false
        restoration.setContent {
            SoTheme {IdentityIntroHost(false,0) {Box(Modifier.fillMaxSize())}}
        }
        app.mainClock.advanceTimeBy(BrandIntroMotion.TOTAL_MILLIS+128L)
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
    }

    private fun inkPixels(): Int {
        val pixels=app.onNodeWithTag("identity-intro").captureToImage().toPixelMap()
        return (0 until pixels.height step 3).sumOf {y ->
            (0 until pixels.width step 3).count {x -> pixels[x,y].red<.2f}
        }
    }
}
