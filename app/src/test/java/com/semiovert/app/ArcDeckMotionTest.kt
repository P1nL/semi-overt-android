package com.semiovert.app

import androidx.compose.animation.core.*
import com.semiovert.app.core.designsystem.arc.ArcDeckMotion
import org.junit.Assert.*
import org.junit.Test

class ArcDeckMotionTest {
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

    @Test fun releasingNearTheTargetRetainsBoundedMomentumAndStillSettles() {
        val animation=TargetBasedAnimation(ArcDeckMotion.spec(),Float.VectorConverter,1f,1f,AnimationVector1D(ArcDeckMotion.MAX_RELEASE_SPEED))
        val values=(0..180).map {animation.getValueFromNanos(it*1_000_000_000L/120)}
        val peak=values.maxOrNull()!!
        assertTrue("A little inertia, not another page: peak=$peak",peak>1.01f&&peak<1.12f)
        assertEquals(1f,values.last(),.0001f)
    }
}
