package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import com.semiovert.app.navigation.RadialNavigationGesture
import com.semiovert.app.navigation.ShortcutViewport
import com.semiovert.app.navigation.ShortcutDragState
import com.semiovert.app.navigation.ShortcutLiquidContact
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.mainCirclePageAfter
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.core.designsystem.LiquidCircleGeometry
import org.junit.Assert.*
import org.junit.Test

class RadialNavigationGestureTest {
    private val offsets=listOf(Offset(-116f,-8f),Offset(-94f,-100f),Offset(0f,-142f))

    @Test fun wholeCircleIsSelectableIncludingTheInnerHalf() {
        offsets.forEachIndexed {index,center ->
            for(point in listOf(center,center*.8f,center+Offset(0f,25f)))
                assertEquals(index,RadialNavigationGesture.target(point,offsets,null))
        }
        assertNull(RadialNavigationGesture.target(Offset.Zero,offsets,null))
    }

    @Test fun movingAcrossStaticCirclesImmediatelyChangesTheSelection() {
        var selected: Int?=null
        for(index in listOf(0,1,2,1,0,2)) {
            selected=RadialNavigationGesture.target(offsets[index]*.85f,offsets,selected)
            assertEquals(index,selected)
        }
    }

    @Test fun outwardPullRetainsItsTargetInEmptySpaceAndMovingInwardOutOfTheHitRegionCancelsIt() {
        val selected=RadialNavigationGesture.target(offsets[0],offsets,null)
        assertEquals(0,selected)
        for(point in listOf(Offset(120f,140f),Offset(0f,230f)))
            assertEquals(0,RadialNavigationGesture.target(point,offsets,selected))
        assertNull(RadialNavigationGesture.target(offsets[0]*.5f,offsets,selected))
        assertNull(RadialNavigationGesture.target(Offset.Zero,offsets,selected))
        assertNull(RadialNavigationGesture.target(Offset(0f,250f),offsets,null))
    }

    @Test fun detachmentDependsOnlyOnStraightLineDistanceFromMain() {
        for(direction in listOf(Offset(1f,0f),Offset(0f,-1f),Offset(-.6f,.8f))) {
            assertFalse(RadialNavigationGesture.canPin(direction*215f))
            assertTrue(RadialNavigationGesture.canPin(direction*217f))
        }
        assertFalse(RadialNavigationGesture.canPin(Offset(216f,0f)))
    }

    @Test fun outwardPullHasAnEmptyTravelBeforeResistantMovementAndDeformation() {
        val rest=Offset(0f,-142f)
        for(distance in listOf(142f,150f,162f)) {
            val pointer=Offset(0f,-distance)
            assertEquals(0f,RadialNavigationGesture.pullProgress(pointer,rest),0f)
            assertEquals(rest,RadialNavigationGesture.attachedCenter(pointer,rest,rest))
        }
        val slight=Offset(0f,-166f)
        val far=Offset(0f,-200f)
        assertTrue(RadialNavigationGesture.pullProgress(slight,rest)>0f)
        assertTrue(RadialNavigationGesture.pullProgress(far,rest)>RadialNavigationGesture.pullProgress(slight,rest))
        assertTrue((RadialNavigationGesture.attachedCenter(slight,rest,rest)-rest).getDistance()<1f)
        assertTrue((RadialNavigationGesture.attachedCenter(far,rest,rest)-rest).getDistance()<(far-rest).getDistance()*.25f)
    }

    @Test fun deadZoneStartsAtTheActualAcquisitionPoint() {
        val rest=Offset(0f,-142f)
        val origin=Offset(0f,-160f)
        assertEquals(rest,RadialNavigationGesture.attachedCenter(Offset(0f,-179f),origin,rest))
        assertTrue(RadialNavigationGesture.pullProgress(Offset(0f,-190f),origin)>0f)
    }

    @Test fun enteringFromTheInnerHalfDoesNotStartPullingInsideTheStaticOrbit() {
        val rest=Offset(0f,-142f)
        val origin=rest*.8f
        for(point in listOf(rest,Offset(0f,-160f))) {
            assertEquals(0f,RadialNavigationGesture.pullProgress(point,origin,rest),0f)
            assertEquals(rest,RadialNavigationGesture.attachedCenter(point,origin,rest))
        }
    }

    @Test fun detachmentClockCompletesOnceWithin120MsThenEveryPointerPositionIsDirect() {
        val animation=TargetBasedAnimation(RadialNavigationGesture.detachMotion(),Float.VectorConverter,0f,1f)
        assertEquals(120_000_000L,animation.durationNanos)
        val progress=animation.getValueFromNanos(animation.durationNanos)
        assertEquals(1f,progress,0f)
        for(pointer in listOf(Offset(-120f,-200f),Offset(-70f,-130f),Offset(-66f,-133f)))
            assertEquals(pointer,androidx.compose.ui.geometry.lerp(Offset(0f,-150f),pointer,progress))
    }

    @Test fun mergeDeformsOnlyVeryNearMainAndDropsWithin75DpAreAbsorbed() {
        assertEquals(0f,RadialNavigationGesture.mergeStretch(100f),0f)
        assertEquals(0f,RadialNavigationGesture.mergeStretch(84f),0f)
        assertTrue(RadialNavigationGesture.mergeStretch(60f)>RadialNavigationGesture.mergeStretch(75f))
        assertTrue(RadialNavigationGesture.canMerge(Offset(45f,60f)))
        assertFalse(RadialNavigationGesture.canMerge(Offset(0f,75.1f)))
    }

    @Test fun detachedRoutesAreNotDuplicatedInTheExpandedMenu() {
        val shortcuts=listOf(NavigationShortcut(Destination.SEARCH.route,.2f,.4f),NavigationShortcut(Destination.PROFILE.route,.4f,.5f))
        assertEquals(listOf(Destination.CATEGORY),RadialNavigationGesture.menuChoices(Destination.HOME,false,shortcuts))
        assertEquals(listOf(Destination.HOME,Destination.CATEGORY),RadialNavigationGesture.menuChoices(Destination.HOME,true,shortcuts))
    }

    @Test fun breakRecoilAndSelectedScaleUseRealUnderdampedSprings() {
        val stretch=TargetBasedAnimation(RadialNavigationGesture.stretchSpring(),Float.VectorConverter,.8f,0f)
        val scale=TargetBasedAnimation(RadialNavigationGesture.scaleSpring(),Float.VectorConverter,1.14f,1f)
        val samples=(0..60).map {it*10_000_000L}
        assertTrue(samples.minOf {stretch.getValueFromNanos(it)}<-.01f)
        assertTrue(samples.minOf {scale.getValueFromNanos(it)}<1f)
        assertEquals(0f,stretch.getValueFromNanos(stretch.durationNanos),.001f)
        assertEquals(1f,scale.getValueFromNanos(scale.durationNanos),.001f)
    }

    @Test fun droppedCentreRoundTripsWithoutRebasingOrEdgeJump() {
        val viewport=ShortcutViewport(411f,780f,24f)
        for(point in listOf(Offset(123.4f,456.7f),Offset(34f,34f),Offset(377f,722f))) {
            val saved=viewport.shortcut("search",point)
            assertEquals(point.x,viewport.center(saved).x,.001f)
            assertEquals(point.y,viewport.center(saved).y,.001f)
        }
    }

    @Test fun dragAndSaveApplyTheSameSafeEdgeConstraint() {
        val viewport=ShortcutViewport(411f,780f,24f)
        val dragged=viewport.constrain(Offset(500f,900f))
        assertEquals(Offset(377f,722f),dragged)
        assertEquals(dragged,viewport.center(viewport.shortcut("search",dragged)))
    }

    @Test fun independentDragUsesTheAbsoluteRootPointerAndRetainsTheGrabOffset() {
        val viewport=ShortcutViewport(411f,780f,24f)
        val state=ShortcutDragState(Offset(140f,400f))
        state.start(Offset(148f,410f))
        state.moveTo(Offset(173f,370f),viewport)
        assertEquals(Offset(165f,360f),state.center)
        state.moveTo(Offset(113f,380f),viewport)
        assertEquals(Offset(105f,370f),state.center)
        state.release()
        assertEquals(state.center,viewport.center(viewport.shortcut("search",state.center)))
    }

    @Test fun draggingToAnEdgeAndReversingDoesNotAccumulatePointerLag() {
        val viewport=ShortcutViewport(411f,780f,24f)
        val state=ShortcutDragState(Offset(140f,400f))
        state.start()
        state.moveTo(Offset(500f,900f),viewport)
        assertEquals(Offset(377f,722f),state.center)
        state.moveTo(Offset(350f,680f),viewport)
        assertEquals(Offset(350f,680f),state.center)
    }

    @Test fun liquidNeckStartsOnlyAtTangencyAndStaysFor18DpAfterContact() {
        val contact=ShortcutLiquidContact()
        contact.update(65f,28f,26f,true)
        assertFalse(contact.connected)
        contact.update(54f,28f,26f,true)
        assertTrue(contact.connected)
        contact.update(68f,28f,26f,true)
        assertTrue(contact.connected)
        contact.update(72f,28f,26f,true)
        assertFalse(contact.connected)
        contact.update(65f,28f,26f,true)
        assertFalse(contact.connected)
        contact.update(53f,28f,26f,true)
        assertTrue(contact.connected)
        contact.update(53f,28f,26f,false)
        assertFalse(contact.connected)
    }

    @Test fun neckAppearanceIsA180MsMonotonicTransitionRatherThanInstantFullWidth() {
        val animation=TargetBasedAnimation(RadialNavigationGesture.neckAppearMotion(),Float.VectorConverter,0f,1f)
        assertEquals(180_000_000L,animation.durationNanos)
        assertEquals(0f,animation.getValueFromNanos(0L),0f)
        assertTrue(animation.getValueFromNanos(60_000_000L) in .01f.. .99f)
        var previous=0f
        for(time in 0..animation.durationNanos step 10_000_000L) {
            val progress=animation.getValueFromNanos(time)
            assertTrue(progress>=previous)
            previous=progress
        }
        assertEquals(1f,animation.getValueFromNanos(animation.durationNanos),0f)
    }

    @Test fun tangencyIncludesTheActualMainFacingDeformation() {
        val radius=LiquidCircleGeometry.facingRadius(26f,.25f)
        val tangent=28f+radius
        val contact=ShortcutLiquidContact()
        contact.update(tangent+1f,28f,radius,true)
        assertFalse(contact.connected)
        contact.update(tangent,28f,radius,true)
        assertTrue(contact.connected)
        assertNotNull(LiquidCircleGeometry.bridge(Offset.Zero,28f,Offset(tangent,0f),26f,
            tangent+ShortcutLiquidContact.HOLD_DISTANCE))
    }

    @Test fun gestureCancellationCannotResetAnAbsorbingCircleToItsOldSavedPosition() {
        val state=ShortcutDragState(Offset(140f,400f))
        state.start()
        state.beginMerge()
        state.absorb(Offset(140f,400f),Offset(180f,450f),.4f)
        val absorbingCenter=state.center
        state.cancel(Offset(70f,200f))
        assertEquals(absorbingCenter,state.center)
        assertEquals(.6f,state.absorption,.001f)
        assertTrue(state.merging)
    }

    @Test fun fusionShrinksAndMovesMonotonicallyWithoutOvershootOrReappearing() {
        val from=Offset(100f,200f)
        val to=Offset(130f,240f)
        val state=ShortcutDragState(from)
        state.beginMerge()
        val animation=TargetBasedAnimation(RadialNavigationGesture.mergeMotion(),Float.VectorConverter,0f,1f)
        var distance=(from-to).getDistance()
        var opacity=1f
        for(time in 0..animation.durationNanos step 10_000_000L) {
            state.absorb(from,to,animation.getValueFromNanos(time))
            val nextDistance=(state.center-to).getDistance()
            assertTrue(nextDistance<=distance+.001f)
            assertTrue(state.absorption<=opacity+.001f)
            distance=nextDistance;opacity=state.absorption
        }
        state.absorb(from,to,animation.getValueFromNanos(animation.durationNanos))
        assertEquals(to,state.center)
        assertEquals(0f,state.absorption,0f)
    }

    @Test fun shortcutNavigationKeepsTheLastMainCirclePage() {
        assertEquals(Destination.HOME,mainCirclePageAfter(Destination.HOME,Destination.SEARCH,true))
        val selected=mainCirclePageAfter(Destination.HOME,Destination.CATEGORY,false)
        assertEquals(Destination.CATEGORY,selected)
        assertEquals(selected,mainCirclePageAfter(selected,Destination.PROFILE,true))
    }
}
