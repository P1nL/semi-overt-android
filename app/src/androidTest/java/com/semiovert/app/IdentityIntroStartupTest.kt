package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityIntroStartupTest {
    @get:Rule val app=createComposeRule()

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
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
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
}
