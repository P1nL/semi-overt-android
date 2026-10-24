package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.designsystem.wave.secondaryPageOverlay
import com.semiovert.app.core.designsystem.wave.LocalShortcutFusionOverlay
import com.semiovert.app.core.designsystem.wave.ShortcutFusionOverlay
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.navigation.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic host only; does not open MainActivity or modify persisted preview data. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ShortcutPointerFollowTest {
    @get:Rule val app=createComposeRule()

    @Test fun heldMouseTracksTheNextFrameAndDoesNotCatchUpAfterStopping()=assertFollows(mouse=true)
    @Test fun emulatorTouchTracksTheNextFrameAndDoesNotCatchUpAfterStopping()=assertFollows(mouse=false)
    @Test fun sameHeldTouchKeepsTrackingAfterRadialDetachment()=assertRadialDetachmentFollows(mouse=false)
    @Test fun sameHeldMouseKeepsTrackingAfterRadialDetachment()=assertRadialDetachmentFollows(mouse=true)

    private fun assertRadialDetachmentFollows(mouse: Boolean) {
        val shortcuts=mutableStateOf(emptyList<NavigationShortcut>())
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalShortcuts provides ShortcutActions(shortcuts.value,
                    {saved->shortcuts.value=listOf(saved)},{route->shortcuts.value=shortcuts.value.filterNot {it.route==route}})) {
                    NavigationShortcutHost(Destination.HOME,{},true) {
                        WaveDeckOverlayHost {
                            BrowseScene(Destination.HOME,"首页",{},showHeader=false) {Box(Modifier.fillMaxSize())}
                        }
                    }
                }
            }
        }
        app.waitForIdle()
        val density=app.density.density
        fun center(tag: String): Offset {
            val bounds=app.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val y=if(tag.startsWith("radial-"))bounds.top.value+26f else (bounds.top.value+bounds.bottom.value)/2f
            return Offset((bounds.left.value+bounds.right.value)*density/2f,y*density)
        }
        val main=center("main-circle")
        val root=app.onRoot()
        fun move(point: Offset) {
            if(mouse)root.performMouseInput {moveTo(point,delayMillis=16)}
            else root.performTouchInput {moveTo(point,delayMillis=16)}
            app.mainClock.advanceTimeByFrame()
        }
        app.mainClock.autoAdvance=false
        try {
            if(mouse)root.performMouseInput {moveTo(main);press();advanceEventTime(800)}
            else root.performTouchInput {down(main);advanceEventTime(800)}
            app.mainClock.advanceTimeBy(1800)
            move(center("radial-search"))
            move(main+Offset(-160f,-200f)*density)
            app.mainClock.advanceTimeBy(RadialNavigationGesture.DETACH_MILLIS+32L)
            // Keep the very same pointer down: do not start a second shortcut gesture.
            val targets=listOf(Offset(-150f,-240f),Offset(-190f,-160f),Offset(-70f,-130f),Offset(-66f,-133f))
            for(delta in targets) {
                val point=main+delta*density
                move(point)
                val actual=center("radial-search")
                assertEquals("detached horizontal movement must not restart the break animation",point.x,actual.x,1.5f)
                assertEquals("detached vertical movement must not re-enter pull resistance",point.y,actual.y,1.5f)
            }
            val stopped=center("radial-search")
            app.mainClock.advanceTimeBy(160)
            assertEquals(stopped.x,center("radial-search").x,.5f)
            assertEquals(stopped.y,center("radial-search").y,.5f)
        } finally {
            if(mouse)root.performMouseInput {release()} else root.performTouchInput {up()}
            app.mainClock.autoAdvance=true
        }
        app.waitForIdle()
        val dropped=center("shortcut-search")
        val expected=main+Offset(-66f,-133f)*density
        assertEquals(expected.x,dropped.x,1.5f)
        assertEquals(expected.y,dropped.y,1.5f)
    }

    @Test fun fusionKeepsMainCircleInFrontButDoesNotLiftItOverSecondaryPagesAfterwards() {
        val shortcuts=mutableStateOf(listOf(NavigationShortcut(Destination.SEARCH.route,.35f,.4f)))
        val covered=mutableStateOf(false)
        var fusionLayer: ShortcutFusionOverlay?=null
        val green=Destination.HOME.circleColors.background
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalShortcuts provides ShortcutActions(shortcuts.value,{saved->shortcuts.value=listOf(saved)},
                    {route->shortcuts.value=shortcuts.value.filterNot {it.route==route}})) {
                    NavigationShortcutHost(Destination.HOME,{},true) {
                        WaveDeckOverlayHost {
                            val fusion=LocalShortcutFusionOverlay.current!!
                            SideEffect {fusionLayer=fusion}
                            BrowseScene(Destination.HOME,"首页",{},showHeader=false) {
                                Box(Modifier.fillMaxSize().background(Color.White))
                            }
                            if(covered.value)Box(Modifier.fillMaxSize().secondaryPageOverlay(3f).background(Color.Red))
                        }
                    }
                }
            }
        }
        app.waitForIdle()
        val density=app.density.density
        fun center(tag: String): Offset {
            val bounds=app.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return Offset((bounds.left.value+bounds.right.value)*density/2f,(bounds.top.value+bounds.bottom.value)*density/2f)
        }
        val start=center("shortcut-search")
        val main=center("main-circle")
        app.onRoot().performTouchInput {down(start);advanceEventTime(800)}
        app.mainClock.advanceTimeBy(800)
        app.onRoot().performTouchInput {moveTo(main+Offset(0f,-42f)*density,16)}
        app.waitForIdle()
        app.runOnIdle {assertTrue("the drag must activate the outer fusion plane",fusionLayer!!.active)}
        app.runOnIdle {assertTrue("connected circles must share one exterior shadow",fusionLayer!!.joinedShadow)}
        app.onNodeWithTag("shortcut-fusion-shadow").assertExists()
        val sample=main+Offset(0f,-18f)*density
        val overlapping=app.onRoot().captureToImage().toPixelMap()
        assertEquals("the independent circle must remain under the main body",green,overlapping[sample.x.toInt(),sample.y.toInt()])
        val joint=main+Offset(0f,-30f)*density
        assertEquals("no main-circle rim shadow may darken the shortcut at the joint",
            Destination.SEARCH.circleColors.background,overlapping[joint.x.toInt(),joint.y.toInt()])
        val exterior=main+Offset(0f,31f)*density
        assertTrue("the combined silhouette must keep its exterior projection",
            overlapping[exterior.x.toInt(),exterior.y.toInt()].red<.995f)
        app.onRoot().performTouchInput {up()}
        app.waitForIdle()
        app.onNodeWithTag("shortcut-search").assertDoesNotExist()
        app.onNodeWithTag("shortcut-fusion-shadow").assertDoesNotExist()
        app.runOnIdle {assertFalse("standalone main-circle shadow must be restored",fusionLayer!!.joinedShadow)}
        app.runOnIdle {covered.value=true}
        val secondary=app.onRoot().captureToImage().toPixelMap()
        assertEquals("normal secondary-page coverage must be preserved",Color.Red,secondary[main.x.toInt(),main.y.toInt()])
    }

    private fun assertFollows(mouse: Boolean) {
        val shortcuts=mutableStateOf(listOf(NavigationShortcut(Destination.SEARCH.route,.35f,.4f)))
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalShortcuts provides ShortcutActions(shortcuts.value,{saved->shortcuts.value=listOf(saved)},{})) {
                    NavigationShortcutHost(Destination.HOME,{},true) {
                        Box(Modifier.fillMaxSize().testTag("shortcut-pointer-fixture"))
                    }
                }
            }
        }
        app.waitForIdle()
        val density=app.density.density
        fun center(): Offset {
            val bounds=app.onNodeWithTag("shortcut-search").getUnclippedBoundsInRoot()
            return Offset((bounds.left.value+bounds.right.value)*density/2f,(bounds.top.value+bounds.bottom.value)*density/2f)
        }
        val start=center()
        val root=app.onRoot()
        app.mainClock.autoAdvance=false
        try {
            if(mouse)root.performMouseInput {moveTo(start);press();advanceEventTime(800)}
            else root.performTouchInput {down(start);advanceEventTime(800)}
            app.mainClock.advanceTimeBy(800)
            val targets=listOf(start+Offset(60f,-40f)*density,start+Offset(-35f,30f)*density,start+Offset(40f,65f)*density)
            for(point in targets) {
                if(mouse)root.performMouseInput {moveTo(point,delayMillis=16)}
                else root.performTouchInput {moveTo(point,delayMillis=16)}
                app.mainClock.advanceTimeByFrame()
                val actual=center()
                assertEquals("horizontal tracking must not await pointer idle",point.x,actual.x,1.5f)
                assertEquals("vertical tracking must not await pointer idle",point.y,actual.y,1.5f)
            }
            val stopped=center()
            app.mainClock.advanceTimeBy(160)
            assertEquals(stopped.x,center().x,.5f)
            assertEquals(stopped.y,center().y,.5f)
        } finally {
            if(mouse)root.performMouseInput {release()} else root.performTouchInput {up()}
            app.mainClock.autoAdvance=true
        }
    }
}
