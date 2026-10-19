package com.semiovert.app

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.semiovert.app.core.designsystem.cuboid.*
import org.junit.Assert.*
import org.junit.Test

class CuboidMotionTest {
    @Test fun inputFilterConsumesTouchSlopAndAccumulatesSlowMovementWithoutJitterDrift() {
        val filter=CuboidDragFilter(CubePoint(0f,0f),8f,1f)
        repeat(20) {
            assertEquals(CubePoint(0f,0f),filter.moveTo(CubePoint(if(it%2==0).4f else -.4f,.3f)))
        }
        assertFalse(filter.dragging)
        assertEquals(CubePoint(0f,0f),filter.moveTo(CubePoint(8f,0f)))
        assertEquals(1f,filter.moveTo(CubePoint(9f,0f)).x,.0001f)
        assertTrue(filter.dragging)
        assertEquals(CubePoint(0f,0f),filter.moveTo(CubePoint(9.4f,0f)))
        assertEquals(CubePoint(0f,0f),filter.moveTo(CubePoint(9.8f,0f)))
        assertEquals(1.1f,filter.moveTo(CubePoint(10.1f,0f)).x,.0001f)
        repeat(20) {
            assertEquals(CubePoint(0f,0f),filter.moveTo(CubePoint(if(it%2==0)10.5f else 9.7f,.2f)))
        }
    }

    @Test fun boundaryJitterDoesNotToggleThePickedFaceButDeliberateReversalStillWorks() {
        val turn=CuboidTurn(11,0)
        turn.drag(.78f,0f)
        repeat(20) {
            turn.drag(.014f,0f);assertEquals(0,turn.focusedFace)
            turn.drag(-.014f,0f);assertEquals(0,turn.focusedFace)
        }
        turn.drag(.10f,0f);assertEquals(3,turn.focusedFace)
        turn.drag(-.10f,0f)
        repeat(20) {
            turn.drag(-.008f,0f);assertEquals(3,turn.focusedFace)
            turn.drag(.008f,0f);assertEquals(3,turn.focusedFace)
        }
        turn.drag(-.10f,0f);assertEquals(0,turn.focusedFace)
        assertEquals(0,turn.settle().face)

        val boundary=CuboidTurn(11,0)
        boundary.drag(.88f,0f);boundary.drag(-.10f,0f)
        val before=boundary.frame()
        val plan=boundary.settle()
        assertEquals(3,plan.face)
        assertEquals(before.articles[3],plan.article)
        assertEquals(before.articles,boundary.frame().articles)
    }

    @Test fun weightedSettleStartsAtRestAndApproachesTheTargetWithoutOvershoot() {
        val animation=TargetBasedAnimation(CuboidSettleMotion.spec(1f),Float.VectorConverter,0f,1f)
        val duration=animation.durationNanos
        assertTrue("A deliberate settling beat, not a long input lock",duration in 420_000_001L..1_200_000_000L)
        assertEquals(0f,animation.getVelocityVectorFromNanos(0L).value,.0001f)
        var previous=0f
        for(step in 0..60) {
            val value=animation.getValueFromNanos(duration*step/60)
            assertTrue("No recoil or bounce",value>=previous-.0001f && value in 0f..1f)
            previous=value
        }
        assertEquals(1f,previous,.0001f)
        val small=TargetBasedAnimation(CuboidSettleMotion.spec(.03f),Float.VectorConverter,0f,1f)
        assertTrue("A tiny correction does not wait for the full large-turn tail",small.durationNanos<duration)
    }
}
