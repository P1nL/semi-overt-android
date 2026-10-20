package com.semiovert.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.designsystem.wave.waveDeckOverlay
import com.semiovert.app.core.designsystem.wave.mainCircleOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A synthetic layout only: no MainActivity, preference reset or demo identity changes. */
@RunWith(AndroidJUnit4::class)
class WaveDeckOverlayTest {
    @get:Rule val app=createComposeRule()

    @Test fun movingDeckEscapesParentClipAndLaterSiblingsThenReturnsToItsCrop() {
        val moving=mutableStateOf(false)
        app.setContent {
            Box(Modifier.size(160.dp).background(Color.White).testTag("overlay-fixture")) {
                WaveDeckOverlayHost {
                    Box(Modifier.offset(y=40.dp).size(100.dp,80.dp).clipToBounds()) {
                        Box(Modifier.offset(y=(-40).dp).size(80.dp,120.dp)
                            .waveDeckOverlay {moving.value}.background(Color.Red))
                    }
                    // A later sibling would obscure the deck even without the parent's crop.
                    Box(Modifier.size(40.dp).background(Color.Blue))
                    Box(Modifier.size(16.dp).mainCircleOverlay().background(Color.Green))
                }
            }
        }
        fun checkColors(overlaid: Boolean) {
            app.waitForIdle()
            val image=app.onNodeWithTag("overlay-fixture").captureToImage().toPixelMap()
            val scale=image.width/160f
            assertEquals("Only the primary circle stays above the deck",Color.Green,image[(8*scale).toInt(),(8*scale).toInt()])
            assertEquals(if(overlaid)Color.Red else Color.Blue,image[(20*scale).toInt(),(20*scale).toInt()])
            assertEquals(if(overlaid)Color.Red else Color.White,image[(60*scale).toInt(),(20*scale).toInt()])
            assertEquals(Color.Red,image[(60*scale).toInt(),(60*scale).toInt()])
        }
        checkColors(false)
        app.runOnIdle {moving.value=true}
        checkColors(true)
        app.runOnIdle {moving.value=false}
        checkColors(false)
    }
}
