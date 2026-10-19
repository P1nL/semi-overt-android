package com.semiovert.app

import com.semiovert.app.core.designsystem.wave.*
import org.junit.Assert.*
import org.junit.Test

class WaveDeckMathTest {
    @Test fun restingStackIsBackHighFrontLowAndTheFrontCardIsComplete() {
        for(count in listOf(1,2,3,6,11)) {
            val g=WaveDeckMath.geometry(411f,780f,count)
            val poses=WaveDeckMath.frames(count,0f,g)
            val front=poses.first()
            assertEquals(g.frontY,front.centerY,.001f)
            assertEquals("Widening must preserve the original top edge",299.49f,front.centerY-front.height/2f,.001f)
            assertEquals("Cards use ninety percent of this viewport width",369.9f,g.cardSize,.001f)
            assertTrue(front.centerY-front.height/2f>=0f)
            for(pose in poses)assertEquals("Every resting card extends to the bottom",g.height,pose.centerY+pose.height*pose.scale/2f,.001f)
            for(i in 1 until count) {
                assertTrue(poses[i].centerY<poses[i-1].centerY)
                assertTrue(poses[i].layer<poses[i-1].layer)
                assertEquals("Rear cards keep the same width and type size",1f,poses[i].scale,0f)
            }
        }
    }

    @Test fun everyFollowerRespondsImmediatelyAndDepthFormsAContinuousWave() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val start=WaveDeckMath.frames(6,0f,g)
        val beginning=WaveDeckMath.frames(6,.02f,g)
        assertEquals((g.outsideY-g.frontY)*.02f/WaveDeckMath.EXIT_FRACTION,beginning[0].centerY-start[0].centerY,.001f)
        for(i in 1..5)assertTrue("No rear card waits for a travel threshold",beginning[i].centerY>start[i].centerY+.1f)
        val wave=WaveDeckMath.frames(6,.25f,g)
        for(i in 1..4)assertTrue("Different depth responses form a wave, not a rigid translation",
            wave[i].centerY-start[i].centerY>wave[i+1].centerY-start[i+1].centerY)
        val reverse=WaveDeckMath.frames(6,-.02f,g)
        for(i in 0..4)assertTrue("Reversing the gesture responds immediately too",reverse[i].centerY<start[i].centerY)
        assertTrue(reverse.last().centerY>start.last().centerY)
        val crest=WaveDeckMath.frames(6,.5f,g)
        assertTrue("The first neighbour has a visible web-style crest",crest[1].waveOffset>40f)
        assertEquals("Web neighbour amplitudes use 100 / 40 falloff",2.5f,crest[1].waveOffset/crest[2].waveOffset,.001f)
        assertEquals(2.5f,crest[2].waveOffset/crest[3].waveOffset,.001f)
        for(pose in start)assertEquals("No idle wave or replay at rest",0f,pose.waveOffset,.001f)
    }

    @Test fun recyclingChangesDepthOnlyAfterTheWholeForegroundCardLeavesTheBottom() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val before=WaveDeckMath.frames(6,WaveDeckMath.EXIT_FRACTION-.002f,g)[0]
        assertTrue(before.outgoing)
        assertTrue("The complete card must be outside before recycling",before.centerY-before.height*before.scale/2f>g.height)
        val after=WaveDeckMath.frames(6,WaveDeckMath.EXIT_FRACTION+.0001f,g)[0]
        assertTrue(after.returning)
        assertTrue("The hidden transfer must not flash a new card",after.alpha<.001f)
        assertTrue(after.layer<before.layer)
    }

    @Test fun reverseTravelRaisesTheFrontWhileTheBackFallsAndTheSamePathIsReversible() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        val rest=WaveDeckMath.frames(6,0f,g)
        val up=WaveDeckMath.frames(6,-.08f,g)
        assertTrue(up[0].centerY<rest[0].centerY)
        assertTrue(up.last().centerY>rest.last().centerY)
        assertTrue(up.last().returning)
    }

    @Test fun everyArticleKeepsItsIdentityAcrossBothLoopSeamsAndRebasing() {
        for(count in listOf(1,2,3,6,11)) {
            val g=WaveDeckMath.geometry(411f,780f,count)
            for(step in -200..200) {
                val poses=WaveDeckMath.frames(count,step*.071f,g)
                assertEquals(count,poses.map {it.article}.distinct().size)
                assertTrue(poses.all {it.centerY.isFinite()&&it.scale==1f&&it.alpha in 0f..1f})
            }
            for(page in -count*3..count*3) {
                assertEquals(WaveDeckMath.frames(count,page.toFloat(),g),WaveDeckMath.frames(count,WaveDeckMath.index(page,count).toFloat(),g))
            }
        }
        assertTrue(WaveDeckMath.frames(0,0f,WaveDeckMath.geometry(411f,780f,0)).isEmpty())
    }

    @Test fun gestureDirectionAndNearestCardSelectionFollowTheQueueWithoutSkippingOnNoise() {
        val step=WaveDeckMath.geometry(411f,780f,6).scrollStep
        assertEquals(1,WaveDeckMath.target(1f,300f,step))
        assertEquals(-1,WaveDeckMath.target(-1f,-300f,step))
        assertEquals(0,WaveDeckMath.target(.08f,0f,step))
        assertEquals(0,WaveDeckMath.focus(.54f,0))
        assertEquals(1,WaveDeckMath.focus(.58f,0))
        assertEquals(-1,WaveDeckMath.nearestPage(2,0f,3))
        assertEquals(4,WaveDeckMath.nearestPage(1,3f,3))
    }

    @Test fun downwardGainShortensTheGestureByTwentyPercentWithoutChangingUpwardTravel() {
        val g=WaveDeckMath.geometry(411f,780f,6)
        assertEquals(1f,WaveDeckMath.dragDelta(g.scrollStep*.8f,g.scrollStep),.0001f)
        assertEquals(-.8f,WaveDeckMath.dragDelta(-g.scrollStep*.8f,g.scrollStep),.0001f)
        assertEquals(0f,WaveDeckMath.dragDelta(0f,g.scrollStep),0f)
    }
}
