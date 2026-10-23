package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.LocalIntroPreparation
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityIntroStartupTest {
    @get:Rule val app=createComposeRule()

    @Test fun guestIconAppearsBeforeRoutePreparation() {
        val canStart=mutableStateOf(false)
        val covers=CompletableDeferred<Map<Int,ImageBitmap>>()
        var composed=false
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {IdentityIntroHost(false,0,canStart=canStart.value,homeCovers=covers) {
                assertSame("The route reuses the early preparation task",covers,LocalIntroPreparation.current?.homeCovers)
                SideEffect {composed=true}
                Box(Modifier.fillMaxSize())
            }}
        }
        app.mainClock.advanceTimeBy(1_000)
        app.waitForIdle()
        assertFalse("Do not compose navigation behind the system startup window",composed)
        app.runOnIdle {canStart.value=true}
        app.mainClock.advanceTimeBy(200)
        assertTrue("A visible ring precedes route preparation",ringPixelCount()>0)
        assertFalse("Route construction must not interrupt the 500ms ring animation",composed)
        app.mainClock.advanceTimeBy(950)
        assertTrue("The user icon must appear without waiting for the route",iconPixelCount()>0)
        assertFalse("Route construction must not consume the icon animation",composed)
        app.runOnIdle {covers.complete(emptyMap())}
        app.waitUntil(5_000) {app.mainClock.advanceTimeByFrame();composed}
    }

    @Test fun scenePreparationMustFinishBeforeTheBackgroundReveal() {
        val ready=mutableStateOf(false)
        var composed=false
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {IdentityIntroHost(false,0) {
                val preparation=LocalIntroPreparation.current
                val done=ready.value
                SideEffect {composed=true;preparation?.ready=done}
                Box(Modifier.fillMaxSize())
            }}
        }
        app.waitUntil(5_000) {
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("identity-intro").captureToImage()
            composed
        }
        app.mainClock.advanceTimeBy(3_000)
        app.onNodeWithTag("identity-intro").assertExists()
        app.runOnIdle {ready.value=true}
        app.mainClock.advanceTimeBy(700)
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
    }

    @Test fun disabledMotionDoesNotWaitForTheSystemSplashGate() {
        app.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                SoTheme {IdentityIntroHost(false,0,canStart=false) {Box(Modifier.fillMaxSize())}}
            }
        }
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
    }

    @Test fun waitingForSystemSplashDoesNotConsumeTheIntro() {
        val canStart=mutableStateOf(false)
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                IdentityIntroHost(false,0,canStart=canStart.value) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        // Longer than the entire intro: it must still be on its untouched first frame.
        app.mainClock.advanceTimeBy(5_000)
        app.onNodeWithTag("identity-intro").assertExists()
        assertTrue("The ring must not draw behind the splash",ringPixelCount()==0)

        app.runOnIdle {canStart.value=true}
        app.mainClock.advanceTimeBy(250)
        assertTrue("The ring must start after the splash is removed",ringPixelCount()>0)
        app.mainClock.advanceTimeBy(1_000)
        app.onNodeWithTag("identity-intro").assertExists()
        app.mainClock.advanceTimeBy(2_500)
        // Route mounting follows a real draw of the completed icon, not only test-clock ticks.
        app.waitUntil(5_000) {
            app.mainClock.advanceTimeBy(100)
            app.onAllNodesWithTag("identity-intro").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun ringPixelCount(): Int {
        val pixels=app.onNodeWithTag("identity-intro").captureToImage().toPixelMap()
        var count=0
        for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
            val color=pixels[x,y]
            if(color.red<.12f&&color.green<.12f&&color.blue<.12f)count++
        }
        return count
    }

    private fun iconPixelCount(): Int {
        val pixels=app.onNodeWithTag("identity-intro").captureToImage().toPixelMap()
        val centerX=pixels.width/2
        val centerY=(pixels.height*.42f).toInt()
        val radius=(30f*app.density.density).toInt()
        var count=0
        for(y in centerY-radius..centerY+radius)for(x in centerX-radius..centerX+radius) {
            val color=pixels[x,y]
            if(color.red<.12f&&color.green<.12f&&color.blue<.12f)count++
        }
        return count
    }
}
