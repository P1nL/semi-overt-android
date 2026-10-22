package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import com.semiovert.app.core.designsystem.LiquidCircleGeometry
import org.junit.Assert.*
import org.junit.Test

class LiquidCircleGeometryTest {
    @Test fun neckExistsOnlyBetweenContainmentAndCompleteSeparation() {
        val main=Offset.Zero
        assertNull(LiquidCircleGeometry.bridge(main,28f,main,20f))
        assertNull(LiquidCircleGeometry.bridge(main,28f,Offset(8f,0f),20f))
        assertNotNull(LiquidCircleGeometry.bridge(main,28f,Offset(56f,0f),24f))
        val detach=LiquidCircleGeometry.detachDistance(28f,24f)
        assertNull(LiquidCircleGeometry.bridge(main,28f,Offset(detach,0f),24f))
        assertNull(LiquidCircleGeometry.bridge(main,28f,Offset(116f,-8f),24f))
    }

    @Test fun neckMeetsTheRealCircleEdgesAndNarrowsToZeroAtTheBreak() {
        val radius=24f
        val detach=LiquidCircleGeometry.detachDistance(28f,radius)
        var previousWidth=Float.POSITIVE_INFINITY
        for(distance in listOf(53f,65f,75f,detach-.1f)) {
            val small=Offset(distance,0f)
            val neck=LiquidCircleGeometry.bridge(Offset.Zero,28f,small,radius)!!
            assertEquals(28f,neck.mainA.getDistance(),.001f)
            assertEquals(28f,neck.mainB.getDistance(),.001f)
            assertEquals(radius,(neck.smallA-small).getDistance(),.001f)
            assertEquals(radius,(neck.smallB-small).getDistance(),.001f)
            val width=(neck.mainA-neck.mainB).getDistance()
            assertTrue(width<previousWidth)
            previousWidth=width
        }
        assertTrue("The liquid neck must pinch rather than vanish at full width",previousWidth<.01f)
    }

    @Test fun allRadialDirectionsKeepFiniteHandlesAndColorChangesOnlyAfterDetachment() {
        for(offset in listOf(Offset(-116f,-8f),Offset(94f,-100f),Offset(0f,-142f),Offset(96f,-56f))) {
            val small=offset*.45f
            val neck=LiquidCircleGeometry.bridge(Offset.Zero,28f,small,22f)!!
            for(point in listOf(neck.mainA,neck.mainHandleA,neck.smallHandleA,neck.smallA,neck.smallB,neck.smallHandleB,neck.mainHandleB,neck.mainB)) {
                assertTrue(point.x.isFinite()&&point.y.isFinite())
            }
        }
        val detach=LiquidCircleGeometry.detachDistance(28f,24f)
        assertEquals(0f,LiquidCircleGeometry.materialProgress(detach,28f,24f),0f)
        assertTrue(LiquidCircleGeometry.materialProgress(detach+8f,28f,24f) in .1f.. .9f)
        assertEquals(1f,LiquidCircleGeometry.materialProgress(116f,28f,24f),0f)
    }
}
