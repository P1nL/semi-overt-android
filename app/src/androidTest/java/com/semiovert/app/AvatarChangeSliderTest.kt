package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.profile.AvatarChangeSlider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AvatarChangeSliderTest {
    @get:Rule val app=createComposeRule()
    private val replacing=mutableStateOf(false)
    private var pickerOpens=0
    private val track get()=app.onNodeWithTag("avatar-change-track")
    private val thumb get()=app.onNodeWithTag("edit-avatar-preview",useUnmergedTree=true)

    private fun show(motion: Boolean=true,theme: ThemeMode=ThemeMode.LIGHT) {
        app.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides motion) {
                SoTheme(theme) {
                    Surface {
                        Box(Modifier.width(320.dp)) {
                            AvatarChangeSlider(0,null,true,replacing.value,
                                onSlide={replacing.value=true},onCollapsed={pickerOpens++})
                        }
                    }
                }
            }
        }
    }

    @Test fun pullingPastTheStartSquashesTheThumbAndReleasingRestoresIt() {
        show()
        val rest=thumb.fetchSemanticsNode().boundsInRoot
        app.mainClock.autoAdvance=false
        try {
            thumb.performTouchInput {down(center);moveBy(Offset(-width*.90f,0f),delayMillis=96)}
            app.mainClock.advanceTimeBy(160)
            val squashed=thumb.fetchSemanticsNode().boundsInRoot
            assertTrue(squashed.width<rest.width*.85f)
            assertTrue(squashed.height>rest.height)
            thumb.performTouchInput {up()}
            app.mainClock.advanceTimeBy(800)
            assertEquals(rest,thumb.fetchSemanticsNode().boundsInRoot)
            app.runOnIdle {assertEquals(0,pickerOpens)}
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun aFastFlingPassesTheReleasePointBeforeOpeningThePicker() {
        show()
        val startingTrack=track.fetchSemanticsNode().boundsInRoot
        val startingThumb=thumb.fetchSemanticsNode().boundsInRoot
        val distance=startingTrack.width-startingThumb.width-2*(startingThumb.left-startingTrack.left)
        app.mainClock.autoAdvance=false
        try {
            thumb.performTouchInput {swipe(center,center+Offset(distance*.44f,0f),durationMillis=72)}
            app.mainClock.advanceTimeBy(64)
            assertTrue(thumb.fetchSemanticsNode().boundsInRoot.left>startingThumb.left+distance*.48f)
            app.runOnIdle {assertEquals(0,pickerOpens)}
            app.mainClock.advanceTimeBy(880)
            app.runOnIdle {assertEquals(1,pickerOpens)}
            val ball=track.fetchSemanticsNode().boundsInRoot
            assertEquals(ball.height,ball.width,1f)
            thumb.assertDoesNotExist()
            app.runOnIdle {replacing.value=false}
            app.mainClock.advanceTimeBy(600)
            assertEquals(startingTrack,track.fetchSemanticsNode().boundsInRoot)
            assertEquals(startingThumb,thumb.fetchSemanticsNode().boundsInRoot)
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun recessedTrackFadesInADeepBlueTrailWithoutAVisibleLabel() {
        show(motion=false,theme=ThemeMode.DARK)
        track.assertContentDescriptionEquals("更换头像")
        app.onNodeWithText("更换头像",useUnmergedTree=true).assertDoesNotExist()
        assertTrue(thumb.getUnclippedBoundsInRoot().let {it.right-it.left}>=65.dp)
        val image=track.captureToImage().toPixelMap()
        val sampleX=image.width*5/6
        assertEquals("The top outer edge matches the page background",image[0,0],image[sampleX,0])
        assertTrue(image[sampleX,image.height*4/5].luminance()>image[sampleX,image.height/5].luminance())
        val travel=track.fetchSemanticsNode().boundsInRoot.width-thumb.fetchSemanticsNode().boundsInRoot.height
        val trailStartX=image.width/16
        val trailY=image.height/2
        thumb.performTouchInput {down(center);moveBy(Offset(travel*.15f,0f),delayMillis=120)}
        val shortTrail=track.captureToImage().toPixelMap()[trailStartX,trailY]
        thumb.performTouchInput {moveBy(Offset(travel*.43f,0f),delayMillis=240)}
        val dragged=track.captureToImage().toPixelMap()
        assertTrue(dragged[trailStartX,trailY].blue>shortTrail.blue+.10f)
        val trailColor=dragged[image.width/3,trailY]
        assertTrue(trailColor.blue>.61f&&trailColor.blue-trailColor.green>.32f)
        thumb.performTouchInput {cancel()}
        track.performClick()
        app.runOnIdle {assertEquals(0,pickerOpens)}
        track.performSemanticsAction(SemanticsActions.OnClick) {assertTrue(it())}
        app.runOnIdle {assertEquals(1,pickerOpens)}
        val ball=track.captureToImage().toPixelMap()
        val ballX=ball.width*2/5
        assertEquals(ball[ballX,ball.height/4].luminance(),ball[ballX,ball.height*3/4].luminance(),.02f)
    }
}
