package com.semiovert.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.semiovert.app.core.designsystem.BrandIntroMotion
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.LocalIdentity
import com.semiovert.app.core.designsystem.SoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SignedInIntroTest {
    @get:Rule val app=createComposeRule()

    @Test fun signedInUsesTheSameStarAndCompletesWithoutAnAvatarSnapshot() {
        app.mainClock.autoAdvance=false
        app.setContent {
            SoTheme {IdentityIntroHost(true,0) {
                val anchor=LocalIdentity.current
                SideEffect {anchor.image=null}
                Box(Modifier.fillMaxSize().background(Color.Red))
            }}
        }
        app.mainClock.advanceTimeBy(600)
        val opening=app.onRoot().captureToImage().toPixelMap()
        val center=opening[opening.width/2,(opening.height*.42f).toInt()]
        assertTrue("The star center is empty, with no avatar",center.green>.8f)
        app.mainClock.advanceTimeBy(BrandIntroMotion.TOTAL_MILLIS.toLong())
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        val home=app.onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Red,home[home.width/2,home.height/2])
    }
}
