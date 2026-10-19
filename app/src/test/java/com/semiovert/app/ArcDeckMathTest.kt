package com.semiovert.app

import com.semiovert.app.core.designsystem.arc.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ArcDeckMathTest {
    @Test fun selectedCardIsCompleteAndLargestAcrossPortraitPhoneSizes() {
        for((w,h) in listOf(320f to 620f,360f to 660f,411f to 780f,480f to 860f)) {
            val g=ArcDeckMath.geometry(w,h)
            assertEquals("Cover cards follow the square reference",g.cardWidth,g.cardHeight,.0001f)
            val frames=ArcDeckMath.frames(11,3f,g)
            val selected=frames.single {it.article==3}
            assertEquals(1f,selected.scale,.0001f)
            assertTrue(selected.centerX-g.cardWidth/2f>=0f)
            assertTrue(selected.centerX+g.cardWidth/2f<=w)
            assertTrue(selected.centerY-g.cardHeight/2f>=0f)
            assertTrue(selected.centerY+g.cardHeight/2f<=h)
            assertTrue(frames.filter {it.article!=3}.all {it.scale<selected.scale})
            assertTrue(frames.filter {it.distance<0f}.all {it.centerX<selected.centerX})
            assertTrue(frames.filter {it.distance>0f}.all {it.centerX>selected.centerX})
        }
    }
    @Test fun upperCardsRecedeLeftAndLowerCardsRecedeRightWithoutChangingTheSequenceLayers() {
        val g=ArcDeckMath.geometry(411f,780f)
        val frames=ArcDeckMath.frames(11,0f,g)
        var lastLeft=g.focusX
        var lastRight=g.focusX
        val selected=frames.single {it.article==0}
        val selectedTop=selected.centerY-g.cardHeight/2f
        val selectedBottom=selected.centerY+g.cardHeight/2f
        for(step in 1..4) {
            val below=frames.single {it.article==step}
            val above=frames.single {it.article==11-step}
            assertTrue(above.centerX<lastLeft)
            assertTrue(below.centerX>lastRight)
            assertEquals(g.focusX*2f,above.centerX+below.centerX,.001f)
            assertEquals(g.focusY*2f,above.centerY+below.centerY,.001f)
            assertEquals(above.scale,below.scale,.0001f)
            assertTrue("Earlier cards remain behind the selected article",above.layer<selected.layer)
            assertTrue("Later cards remain above it in sequence",below.layer>selected.layer)
            assertTrue(above.centerY+g.cardHeight*above.scale/2f<=selectedTop-ArcDeckMath.CARD_GAP+.001f)
            assertTrue(below.centerY-g.cardHeight*below.scale/2f>=selectedBottom+ArcDeckMath.CARD_GAP-.001f)
            lastLeft=above.centerX
            lastRight=below.centerX
        }
    }
    @Test fun theCentreWindowRemainsOpenWhileAdjacentCardsExchangePlaces() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(step in 0..40) {
            val frames=ArcDeckMath.frames(11,step/40f,g)
            val earlier=frames.single {it.article==0}
            val later=frames.single {it.article==1}
            assertTrue(earlier.layer<later.layer)
            val earlierBottom=earlier.centerY+ArcDeckMath.waveY(earlier.distance,earlier.waveOffset)+g.cardHeight*earlier.scale/2f
            val laterTop=later.centerY+ArcDeckMath.waveY(later.distance,later.waveOffset)-g.cardHeight*later.scale/2f
            assertTrue("No z-index promotion is needed to expose the moving cards",earlierBottom<=laterTop+.001f)
        }
    }
    @Test fun neighbourWaveStartsImmediatelyDecaysWithDistanceAndProtectsTheFocus() {
        val g=ArcDeckMath.geometry(411f,780f)
        assertTrue(ArcDeckMath.frames(11,0f,g).all {it.waveOffset==0f})
        val moving=ArcDeckMath.frames(11,.02f,g)
        val selected=moving.single {it.article==0}
        val above=moving.single {it.article==10}
        val aboveSecond=moving.single {it.article==9}
        val below=moving.single {it.article==1}
        assertEquals(0f,selected.waveOffset,0f)
        assertTrue("Both neighbours respond from the beginning",above.waveOffset>0f&&below.waveOffset>0f)
        assertEquals("The first and second neighbour use the same falloff as the web",2.5f,above.waveOffset/aboveSecond.waveOffset,.001f)
        assertTrue(ArcDeckMath.waveX(above.distance,above.waveOffset)<0f)
        assertTrue(ArcDeckMath.waveY(above.distance,above.waveOffset)<0f)
        assertTrue(ArcDeckMath.waveX(below.distance,below.waveOffset)>0f)
        assertTrue(ArcDeckMath.waveY(below.distance,below.waveOffset)>0f)
        for(distance in listOf(-.65f,-.56f,0f,.56f,.65f)) {
            assertEquals("A lingering spring never moves the focus",0f,ArcDeckMath.waveX(distance,1000f),0f)
            assertEquals(0f,ArcDeckMath.waveY(distance,1000f),0f)
        }
    }
    @Test fun wrappedFramesNeverDuplicateOrChangeTheArticleIdentity() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(step in -120..120) {
            val position=step*.19f
            val frames=ArcDeckMath.frames(11,position,g)
            assertEquals(frames.size,frames.map {it.article}.distinct().size)
            assertTrue(frames.size in 9..10)
            for(pose in frames) {
                assertTrue(pose.article in 0..10)
                assertTrue(abs(pose.distance)<=ArcDeckMath.VISIBLE_DISTANCE)
                assertTrue(pose.alpha in 0f..1f)
                assertTrue(pose.scale in .55f..1f)
                assertTrue(pose.centerX.isFinite()&&pose.centerY.isFinite())
            }
        }
    }
    @Test fun allElevenArticlesCanBeSelectedAndBothEndsLoopWithoutAnEmptyFrame() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(page in -22..33) {
            val focused=ArcDeckMath.index(page,11)
            val before=ArcDeckMath.frames(11,page.toFloat(),g).single {it.article==focused}
            val after=ArcDeckMath.frames(11,focused.toFloat(),g).single {it.article==focused}
            assertEquals(before,after)
        }
        assertEquals(10,ArcDeckMath.index(-1,11))
        assertEquals(0,ArcDeckMath.index(11,11))
        assertEquals(1,ArcDeckMath.frames(1,0f,g).size)
    }
    @Test fun upwardDraggingAndVelocityChooseLaterArticlesAndDownwardChooseEarlierOnes() {
        val step=ArcDeckMath.geometry(411f,780f).scrollStep
        assertEquals(1,ArcDeckMath.target(0f-(-step)/step,0f,step))
        assertEquals(-1,ArcDeckMath.target(0f-step/step,0f,step))
        assertTrue(ArcDeckMath.target(.3f,-800f,step)>0)
        assertTrue(ArcDeckMath.target(-.3f,800f,step)<0)
    }
    @Test fun aFastFlingIsBoundedAndShortDragsReturnToTheSameCard() {
        val step=ArcDeckMath.geometry(411f,780f).scrollStep
        assertEquals(0,ArcDeckMath.target(.12f,0f,step))
        assertEquals(0,ArcDeckMath.target(-.12f,0f,step))
        assertTrue(abs(ArcDeckMath.target(2.2f,-100000f,step)-2.2f)<=2f)
        assertTrue(abs(ArcDeckMath.target(2.2f,100000f,step)-2.2f)<=2f)
    }
    @Test fun focusedCardHasAHysteresisBandInsteadOfFlickeringAtHalfway() {
        assertEquals(0,ArcDeckMath.focus(.54f,0))
        assertEquals(1,ArcDeckMath.focus(.58f,0))
        assertEquals(1,ArcDeckMath.focus(.46f,1))
        assertEquals(0,ArcDeckMath.focus(.42f,1))
    }
}
