package com.semiovert.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.GuestAvatar
import com.semiovert.app.core.designsystem.BrandIntroMotion
import com.semiovert.app.core.designsystem.GuestGhostPlayback
import com.semiovert.app.core.designsystem.HOME_GHOST_DRAW_FRACTION
import com.semiovert.app.core.designsystem.IdentityAvatar
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.LocalGuestGhost
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.navigation.BrowseScene
import com.semiovert.app.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GuestIdentityRenderingTest {
    @get:Rule val app=createComposeRule()

    @Test fun disabledMotionShowsTheRinglessGhostInBothThemes() {
        var dark by mutableStateOf(false)
        app.setContent {
            val background=if(dark)Color.Black else Color.White
            val colors=if(dark)darkColorScheme(background=background) else lightColorScheme(background=background)
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                MaterialTheme(colorScheme=colors) {
                    IdentityIntroHost(false,0) {
                        Column(Modifier.background(background).padding(8.dp)) {
                            IdentityAvatar(Modifier.testTag("guest-header"),size=56)
                            GuestAvatar(80,Modifier.testTag("guest-login"))
                        }
                    }
                }
            }
        }
        for(isDark in listOf(false,true)) {
            app.runOnIdle {dark=isDark}
            for(tag in listOf("guest-header","guest-login")) {
                val pixels=app.onNodeWithTag(tag).captureToImage().toPixelMap()
                val background=if(isDark)0f else 1f
                val eyesY=(pixels.height*.42f).toInt()
                for(eyeX in listOf(.42f,.58f))
                    assertEquals("$tag draws both Ghost eyes",1f-background,pixels[(pixels.width*eyeX).toInt(),eyesY].red,.03f)
                assertEquals("$tag has an unfilled face",background,pixels[pixels.width/2,eyesY].red,.01f)
                var softEdges=0
                for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
                    val coverage=if(isDark)pixels[x,y].red else 1f-pixels[x,y].red
                    val outerBand=x<pixels.width*.17f||x>=pixels.width*.83f||y<pixels.height*.17f||y>=pixels.height*.83f
                    if(outerBand)assertEquals("$tag has no ink outside the Ghost",0f,coverage,.01f)
                    if(coverage in .05f.. .95f)softEdges++
                }
                assertTrue("$tag retains antialiased edges",softEdges>pixels.width/2)
            }
        }
    }

    @Test fun homeEnlargesTheGhostWithinTheSameAvatarButton() {
        var destination by mutableStateOf(Destination.CATEGORY)
        app.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides false) {
                MaterialTheme(colorScheme=lightColorScheme(background=Color.White)) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        BrowseScene(destination,"",{}) {}
                    }
                }
            }
        }
        fun ghostWidth(): Int {
            val pixels=app.onNodeWithContentDescription("打开个人页")
                .assertHasClickAction()
                .assertWidthIsEqualTo(56.dp)
                .assertHeightIsEqualTo(56.dp)
                .captureToImage().toPixelMap()
            val columns=(0 until pixels.width).filter {x -> (0 until pixels.height).any {y -> pixels[x,y].red<.5f}}
            assertTrue("The guest button contains Ghost ink",columns.isNotEmpty())
            return columns.last()-columns.first()+1
        }
        val categoryWidth=ghostWidth()
        app.runOnIdle {destination=Destination.HOME}
        assertEquals("Home draws the Ghost 25% wider",categoryWidth*1.25f,ghostWidth().toFloat(),2f)
    }

    @Test fun supersamplingSmoothsTheSameGhostFrameAtTheSameSize() {
        val ghost=GuestGhostPlayback()
        var supersample by mutableStateOf(false)
        app.setContent {
            val context=LocalContext.current.applicationContext
            LaunchedEffect(context) {ghost.load(context,Color.Black)}
            CompositionLocalProvider(LocalGuestGhost provides ghost,LocalMotionEnabled provides true) {
                MaterialTheme(colorScheme=lightColorScheme(background=Color.White)) {
                    Box(Modifier.background(Color.White)) {
                        IdentityAvatar(Modifier.testTag("ghost-frame"),size=56,
                            guestArtworkFraction=HOME_GHOST_DRAW_FRACTION,supersampleGuestArtwork=supersample)
                    }
                }
            }
        }
        app.waitUntil(5_000) {ghost.loaded}
        val avatar=app.onNodeWithTag("ghost-frame").assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        val direct=avatar.captureToImage().toPixelMap()
        app.runOnIdle {supersample=true}
        val sampled=avatar.captureToImage().toPixelMap()
        fun PixelMap.inkColumns()=(0 until width).filter {column ->
            (0 until height).any {row -> this[column,row].red<.5f}
        }
        fun PixelMap.edgePixels()=(0 until height).sumOf {row ->
            (0 until width).count {column -> this[column,row].red in .05f.. .95f}
        }
        assertEquals(direct.width,sampled.width)
        assertEquals(direct.height,sampled.height)
        assertTrue("The sampled Ghost keeps the same outline width",
            kotlin.math.abs(direct.inkColumns().size-sampled.inkColumns().size)<=1)
        val directEdges=direct.edgePixels()
        val sampledEdges=sampled.edgePixels()
        assertTrue("Edge coverage at the same size: direct=$directEdges, sampled=$sampledEdges",
            directEdges>0&&sampledEdges>directEdges)
    }

    @Test fun guestGhostKeepsLoopingAfterIntroAndAcrossAvatarChanges() {
        var login by mutableStateOf(false)
        app.mainClock.autoAdvance=false
        try {
            app.setContent {
                MaterialTheme(colorScheme=lightColorScheme(background=Color.White)) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        IdentityIntroHost(false,0) {
                            if(login)GuestAvatar(80,Modifier.testTag("guest-login"))
                            else IdentityAvatar(Modifier.testTag("guest-header"),size=56,
                                guestArtworkFraction=HOME_GHOST_DRAW_FRACTION,supersampleGuestArtwork=true)
                        }
                    }
                }
            }
            app.mainClock.advanceTimeBy(BrandIntroMotion.TOTAL_MILLIS+128L)
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
            SystemClock.sleep(1_600)
            assertAnimatedGhost("guest-header",margin=.1f)

            app.runOnIdle {login=true}
            app.mainClock.advanceTimeByFrame()
            assertAnimatedGhost("guest-login")
        } finally {app.mainClock.autoAdvance=true}
    }

    private fun assertAnimatedGhost(tag: String,margin: Float=.17f) {
        fun ink(): BooleanArray {
            val pixels=app.onNodeWithTag(tag).captureToImage().toPixelMap()
            return BooleanArray(pixels.width*pixels.height) {index ->
                val x=index%pixels.width
                val y=index/pixels.width
                val red=pixels[x,y].red
                val outerBand=x<pixels.width*margin||x>=pixels.width*(1f-margin)||
                    y<pixels.height*margin||y>=pixels.height*(1f-margin)
                if(outerBand&&red<.99f)assertEquals("$tag has no outer circle",1f,red,.01f)
                red<.5f
            }
        }
        val first=ink()
        assertTrue("$tag has visible Ghost ink",first.any {it})
        app.waitUntil(3_000) {
            val later=ink()
            later.any {it}&&!later.contentEquals(first)
        }
    }
}
