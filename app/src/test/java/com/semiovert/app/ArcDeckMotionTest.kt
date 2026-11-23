package com.semiovert.app

import androidx.compose.animation.core.*
import com.semiovert.app.core.designsystem.arc.ArcDeckMotion
import org.junit.Assert.*
import org.junit.Test

class ArcDeckMotionTest {
    @Test fun flingVelocityPreservesDirectionAndUsesCardUnitsAcrossDensities() {
        assertEquals(6f,ArcDeckMotion.flingVelocity(-600f,100f),.0001f)
        assertEquals(-6f,ArcDeckMotion.flingVelocity(600f,100f),.0001f)
        assertEquals(ArcDeckMotion.flingVelocity(-600f,100f),ArcDeckMotion.flingVelocity(-1800f,300f),0f)
        assertEquals(ArcDeckMotion.MAX_FLING_SPEED,ArcDeckMotion.flingVelocity(-100000f,100f),0f)
        assertEquals(-ArcDeckMotion.MAX_FLING_SPEED,ArcDeckMotion.flingVelocity(100000f,100f),0f)
    }

    @Test fun slowReleaseSnapsDirectlyWhileQuickReleaseCoasts() {
        assertFalse(ArcDeckMotion.shouldFling(0f))
        assertFalse(ArcDeckMotion.shouldFling(1f))
        assertFalse(ArcDeckMotion.shouldFling(-1f))
        assertTrue(ArcDeckMotion.shouldFling(ArcDeckMotion.MIN_FLING_SPEED))
        assertTrue(ArcDeckMotion.shouldFling(-ArcDeckMotion.MIN_FLING_SPEED))
    }

    @Test fun fasterFlingTravelsFurtherWithoutSpinningThroughTheWholeQueue() {
        val decay=ArcDeckMotion.flingSpec()
        val medium=decay.calculateTargetValue(0f,6f)
        val fast=decay.calculateTargetValue(0f,ArcDeckMotion.MAX_FLING_SPEED)
        assertTrue("A medium flick continues beyond one card",medium>1f)
        assertTrue("A fast flick coasts across several cards",fast>medium&&fast in 2f..3.5f)
        assertEquals(-fast,decay.calculateTargetValue(0f,-ArcDeckMotion.MAX_FLING_SPEED),.0001f)
        assertEquals(5f+fast,decay.calculateTargetValue(5f,ArcDeckMotion.MAX_FLING_SPEED),.0001f)
    }

    @Test fun releaseVelocityFollowsTheGestureButDoesNotAmplifyFastFlicks() {
        assertEquals(.8f,ArcDeckMotion.releaseVelocity(-80f,100f),.0001f)
        assertEquals(-.8f,ArcDeckMotion.releaseVelocity(80f,100f),.0001f)
        assertEquals(ArcDeckMotion.MAX_RELEASE_SPEED,ArcDeckMotion.releaseVelocity(-100000f,100f),0f)
        assertEquals(-ArcDeckMotion.MAX_RELEASE_SPEED,ArcDeckMotion.releaseVelocity(100000f,100f),0f)
    }

    @Test fun settleHasWeightAndOneSmallRecoilInsteadOfAPlayfulBounce() {
        val animation=TargetBasedAnimation(ArcDeckMotion.spec(),Float.VectorConverter,0f,1f)
        val values=(0..180).map {animation.getValueFromNanos(it*1_000_000_000L/120)}
        val peak=values.maxOrNull()!!
        assertTrue("A small visible elastic finish: peak=$peak",peak in 1.005f..1.035f)
        assertTrue("The tail must not keep input locked",animation.durationNanos in 450_000_000L..1_200_000_000L)
        assertEquals(0f,animation.getVelocityVectorFromNanos(0L).value,0f)
        assertEquals(1f,values.last(),.0001f)
    }

    @Test fun neighbourSpringMatchesTheCategoryAndRespondsWithoutAQueuedDelay() {
        val spec=ArcDeckMotion.followSpec()
        assertEquals(120f,spec.stiffness,0f)
        assertEquals(1.14f,spec.dampingRatio,0f)
        val animation=TargetBasedAnimation(spec,Float.VectorConverter,0f,40f)
        val early=animation.getValueFromNanos(80_000_000L)
        val later=animation.getValueFromNanos(320_000_000L)
        assertTrue(early>0f&&later>early&&later<40f)
    }

    @Test fun selectionLiftAndReturnKeepTheirSpringPathAndFinishIn900Ms() {
        val lift=TargetBasedAnimation(ArcDeckMotion.liftSpec(),Float.VectorConverter,0f,1f)
        val returning=TargetBasedAnimation(ArcDeckMotion.liftSpec(),Float.VectorConverter,1f,0f)
        val spring=TargetBasedAnimation(ArcDeckMotion.followSpec(),Float.VectorConverter,0f,1f)
        assertEquals(900_000_000L,lift.durationNanos)
        assertEquals(900_000_000L,returning.durationNanos)
        for(frame in 0..30) {
            val fraction=frame/30f
            assertEquals("Only the clock changes; the weighted path stays the same",
                spring.getValueFromNanos((fraction*spring.durationNanos).toLong()),
                lift.getValueFromNanos((fraction*lift.durationNanos).toLong()),.001f)
            val time=(fraction*lift.durationNanos).toLong()
            assertEquals(1f-lift.getValueFromNanos(time),returning.getValueFromNanos(time),.001f)
        }
        assertEquals(1f,lift.getValueFromNanos(lift.durationNanos),0f)
        assertEquals(0f,returning.getValueFromNanos(returning.durationNanos),0f)
    }

    @Test fun releasingNearTheTargetRetainsBoundedMomentumAndStillSettles() {
        val animation=TargetBasedAnimation(ArcDeckMotion.spec(),Float.VectorConverter,1f,1f,AnimationVector1D(ArcDeckMotion.MAX_RELEASE_SPEED))
        val values=(0..180).map {animation.getValueFromNanos(it*1_000_000_000L/120)}
        val peak=values.maxOrNull()!!
        assertTrue("A little inertia, not another page: peak=$peak",peak>1.01f&&peak<1.12f)
        assertEquals(1f,values.last(),.0001f)
    }
}
