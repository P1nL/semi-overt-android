package com.semiovert.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.semiovert.app.core.designsystem.BrandIntroArtwork
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.min
import kotlin.math.roundToInt

class BrandIntroRenderingTest {
    @get:Rule val app=createComposeRule()

    @Test fun fourEdgesStartTogetherAndTheConnectedWordReturnsToTheStarInBothThemes() {
        var time by mutableFloatStateOf(350f)
        var dark by mutableStateOf(false)
        app.setContent {
            val artwork=remember {BrandIntroArtwork()}
            Canvas(Modifier.fillMaxSize()) {
                artwork.draw(this,time,if(dark)Color.Black else Color.White,if(dark)Color.White else Color.Black)
            }
        }
        val tips=listOf(206f to 58f,328f to 180f,206f to 302f,84f to 180f)
        for(mode in listOf(false,true)) {
            app.runOnIdle {dark=mode;time=0f}
            val first=frame()
            for((x,y) in tips)
                assertTrue("All four tips are visible in the first frame",first.inkAt(x,y,dark=mode))
            app.runOnIdle {dark=mode;time=350f}
            val drawing=frame()
            for((x,y) in tips)
                assertTrue("All four tips already have ink at 350ms",drawing.inkAt(x,y,dark=mode))
            assertFalse("The center has no Ghost or avatar",drawing.inkAt(206f,180f,dark=mode))
            app.runOnIdle {time=2_700f}
            assertTrue("The final t is visible after writing",frame().inkAt(892f,255f,shift=265f,dark=mode))
            app.runOnIdle {time=3_700f}
            val returned=frame()
            assertTrue("The star has returned to the original center",returned.inkAt(206f,58f,dark=mode))
            assertFalse("The t has retracted completely",returned.inkAt(892f,255f,shift=265f,dark=mode))
        }
    }

    @Test fun starApertureRevealsTheUnderlyingPageAndClearsEveryCorner() {
        var time by mutableFloatStateOf(4_400f)
        app.setContent {
            val artwork=remember {BrandIntroArtwork()}
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                Canvas(Modifier.fillMaxSize()) {artwork.draw(this,time,Color.White,Color.Black)}
            }
        }
        val passing=frame()
        assertRed(passing[passing.width/2,(passing.height*.42f).roundToInt()])
        assertEquals(Color.White,passing[5,5])
        app.runOnIdle {time=4_800f}
        val home=frame()
        for(x in listOf(5,home.width/2,home.width-6))
            for(y in listOf(5,home.height/2,home.height-6))assertRed(home[x,y])
    }

    private fun frame()=app.onRoot().captureToImage().toPixelMap()

    private fun assertRed(color: Color) {
        assertTrue("The page remains visible through the opening",color.red>.9f&&color.green<.1f&&color.blue<.1f)
    }

    private fun PixelMap.inkAt(x: Float,y: Float,shift: Float=0f,dark: Boolean=false): Boolean {
        val scale=min(width/1_100f,height/620f)
        val screenX=(width*.5f+(x-206f-shift)*scale).roundToInt()
        val screenY=(height*.42f+(y-180f)*scale).roundToInt()
        return (-2..2).any {dx -> (-2..2).any {dy ->
            val color=this[(screenX+dx).coerceIn(0,width-1),(screenY+dy).coerceIn(0,height-1)]
            if(dark)color.red>.8f else color.red<.2f
        }}
    }
}
