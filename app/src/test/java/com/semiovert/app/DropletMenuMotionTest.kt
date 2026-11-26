package com.semiovert.app

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.semiovert.app.navigation.DropletMenuMotion
import org.junit.Assert.*
import org.junit.Test

class DropletMenuMotionTest {
    @Test fun openingOvershootsAndClosingAcceleratesIntoTheCore() {
        val open=TargetBasedAnimation(DropletMenuMotion.travel(true),Float.VectorConverter,0f,1f)
        val close=TargetBasedAnimation(DropletMenuMotion.travel(false),Float.VectorConverter,1f,0f)
        assertEquals(950_000_000L,open.durationNanos)
        assertEquals(500_000_000L,close.durationNanos)
        assertTrue((0..95).maxOf {open.getValueFromNanos(it*10_000_000L)} in 1.12f..1.24f)
        assertTrue(close.getValueFromNanos(250_000_000L)>.9f)
        assertTrue(close.getValueFromNanos(450_000_000L)<.4f)
        assertEquals(1f,open.getValueFromNanos(open.durationNanos),0f)
        assertEquals(0f,close.getValueFromNanos(close.durationNanos),0f)
        assertEquals(.55f,DropletMenuMotion.scale(0f),0f)
        assertEquals(1f,DropletMenuMotion.scale(1f),0f)
    }

    @Test fun iconAppearsAfterEjectionStartsAndDisappearsBeforeRejoining() {
        val reveal=TargetBasedAnimation(DropletMenuMotion.icon(true),Float.VectorConverter,0f,1f)
        val hide=TargetBasedAnimation(DropletMenuMotion.icon(false),Float.VectorConverter,1f,0f)
        assertEquals(50L,DropletMenuMotion.stagger(1,open=true))
        assertEquals(35L,DropletMenuMotion.stagger(1,open=false))
        assertEquals(0f,reveal.getValueFromNanos(130_000_000L),0f)
        assertTrue(reveal.getValueFromNanos(230_000_000L) in .01f.. .99f)
        assertEquals(1f,reveal.getValueFromNanos(440_000_000L),0f)
        assertEquals(0f,hide.getValueFromNanos(180_000_000L),0f)
    }

    @Test fun neckUsesTheRenderedRadiusAndIsGoneAtTheNearestDraftSlot() {
        val density=2f
        val color=Color.Cyan
        assertNull(DropletMenuMotion.join(Offset.Zero,.55f,density,color))
        for(offset in listOf(Offset(0f,-60f),Offset(-48f,-36f))) {
            val join=DropletMenuMotion.join(offset,.9f,density,color)!!
            val silhouette=join.silhouette
            assertEquals(DropletMenuMotion.coreCenter(density)+offset*density,silhouette.center)
            assertEquals(26f*.9f*density,silhouette.radius,.001f)
            assertEquals(color,join.color)
            assertEquals(silhouette.radius,(silhouette.bridge!!.smallA-silhouette.center).getDistance(),.001f)
        }
        assertNull(DropletMenuMotion.join(Offset(0f,-76f),1f,density,color))
    }
}
