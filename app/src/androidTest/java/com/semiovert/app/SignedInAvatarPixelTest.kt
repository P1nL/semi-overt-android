package com.semiovert.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SignedInAvatarPixelTest {
    @get:Rule val app=createComposeRule()

    @Test fun waitsForTheAvatarThenRevealsTheWholeImageWithoutTileHoles() {
        val source=mutableStateOf<ImageBitmap?>(null)
        var composed=false
        app.mainClock.autoAdvance=false
        app.setContent {
            Box(Modifier.size(200.dp)) {
                SoTheme(ThemeMode.LIGHT) {
                    IdentityIntroHost(true,0,avatarSource={
                        val anchor=LocalIdentity.current
                        val image=source.value
                        SideEffect {anchor.image=image}
                    }) {
                        SideEffect {composed=true}
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        // More than the full intro, but the missing source must not consume the pixel phase.
        app.mainClock.advanceTimeBy(4_000)
        app.onNodeWithTag("identity-intro").assertExists()
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.RED)}
        app.runOnIdle {source.value=bitmap.asImageBitmap()}
        app.waitUntil(5_000) {
            app.mainClock.advanceTimeByFrame()
            val pixels=app.onNodeWithTag("identity-intro").captureToImage().toPixelMap()
            pixels[pixels.width/2,(pixels.height*.42f).toInt()].green<.9f
        }
        app.mainClock.advanceTimeBy(100)
        assertFalse("Avatar pixels must not wait for navigation construction",composed)
        val pixels=app.onNodeWithTag("identity-intro").captureToImage().toPixelMap()
        val radius=58f*app.density.density
        for(dx in listOf(-.4f,0f,.4f))for(dy in listOf(-.4f,0f,.4f)) {
            val color=pixels[(pixels.width*.5f+radius*dx).toInt(),(pixels.height*.42f+radius*dy).toInt()]
            assertTrue("The full avatar is present, not a tiled mask",color.red>.9f&&color.green<.1f&&color.blue<.1f)
        }
        app.mainClock.advanceTimeBy(500)
        app.onNodeWithTag("identity-intro").assertExists()
        app.mainClock.advanceTimeBy(1_000)
        // Let Android draw the finished avatar and mount the route before the final reveal.
        app.waitUntil(5_000) {
            app.mainClock.advanceTimeBy(100)
            app.onAllNodesWithTag("identity-intro").fetchSemanticsNodes().isEmpty()
        }
    }
}
