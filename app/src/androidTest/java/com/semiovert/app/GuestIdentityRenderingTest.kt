package com.semiovert.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.GuestIdentityPainter
import com.semiovert.app.core.designsystem.GuestIdentityMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class GuestIdentityRenderingTest {
    @get:Rule val app=createComposeRule()

    @Test fun shouldersStayAtRestWhileTheHeadBounces() {
        var bounce by mutableFloatStateOf(0f)
        app.setContent {
            val painter=remember {GuestIdentityPainter()}
            Canvas(Modifier.size(116.dp).background(Color.White).testTag("guest")) {
                painter.draw(this,Rect(Offset.Zero,size),Color.Black,rise=1f,bounce=bounce)
            }
        }
        val resting=app.onNodeWithTag("guest").captureToImage().toPixelMap()
        for(progress in listOf(.33f,.66f,1f)) {
            app.runOnIdle {bounce=progress}
            val current=app.onNodeWithTag("guest").captureToImage().toPixelMap()
            // Below SVG y=18 the head never overlaps the shoulders, even at its lowest point.
            for(y in (current.height*.64f).toInt() until current.height)for(x in 0 until current.width) {
                assertEquals("Shoulders moved at bounce=$progress ($x,$y)",resting[x,y],current[x,y])
            }
            if(progress!=1f) {
                var headChanges=0
                for(y in (current.height*.2f).toInt() until (current.height*.5f).toInt())for(x in 0 until current.width) {
                    if(resting[x,y]!=current[x,y])headChanges++
                }
                assertTrue("The head still bounces",headChanges>20)
            }
        }
    }

    @Test fun openingAndRestingAvatarKeepSmoothEdgesAndAnUnfilledCenterInBothThemes() {
        var dark by mutableStateOf(false)
        app.setContent {
            val background=if(dark)Color.Black else Color.White
            val ink=if(dark)Color.White else Color.Black
            Column(Modifier.background(background).padding(8.dp)) {
                for((tag,diameter) in listOf("opening" to 116.25f,"avatar" to 40.25f)) {
                    val painter=remember {GuestIdentityPainter()}
                    Canvas(Modifier.size(diameter.dp).testTag(tag)) {
                        // Exercise subpixel placement, as used by the shrinking intro.
                        painter.draw(this,Rect(.25f,.25f,size.width-.25f,size.height-.25f),ink)
                    }
                }
            }
        }
        for(isDark in listOf(false,true)) {
            app.runOnIdle {dark=isDark}
            for(tag in listOf("opening","avatar")) {
                val pixels=app.onNodeWithTag(tag).captureToImage().toPixelMap()
                val background=if(isDark)0f else 1f
                // SVG y=13 is the gap between head and shoulders; y=12 touches the head stroke.
                val gapY=(pixels.height*(.5f+GuestIdentityMotion.ICON_FRACTION/24f)).toInt()
                assertEquals("No filled backing",background,pixels[pixels.width/2,gapY].red,.01f)
                assertEquals("Transparent corners",background,pixels[1,1].red,.01f)
                var softEdges=0;var solidInk=0;var softRingEdges=0
                val radius=(pixels.width-.5f)/2f
                val inner=radius-(pixels.width-.5f)*GuestIdentityMotion.RING_STROKE_FRACTION
                for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
                    val coverage=if(isDark)pixels[x,y].red else 1f-pixels[x,y].red
                    if(coverage in .05f.. .95f) {
                        softEdges++
                        val distance=hypot(x+.5f-pixels.width/2f,y+.5f-pixels.height/2f)
                        if(abs(distance-radius)<2f||abs(distance-inner)<2f)softRingEdges++
                    }
                    if(coverage>.98f)solidInk++
                }
                assertTrue("$tag retains filtered coverage along its curved edges: $softEdges",softEdges>pixels.width*5)
                assertTrue("$tag circle must not lose coverage at fractional scale: $softRingEdges",softRingEdges>pixels.width*4.5f)
                assertTrue("$tag remains solid, not blurred or untinted",solidInk>100)
            }
        }
    }
}
