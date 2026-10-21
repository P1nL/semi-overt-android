package com.semiovert.app

import com.semiovert.app.core.designsystem.arc.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ArcDeckMathTest {
    @Test fun bothEdgesStartGentlyAndSpreadTravelAcrossAWholeSlot() {
        val g=ArcDeckMath.geometry(411f,780f)
        assertEquals(1f,ArcDeckMath.EDGE_CLEAR_DISTANCE-ArcDeckMath.EDGE_START_DISTANCE,0f)
        for(direction in listOf(-1f,1f)) {
            val edge=ArcDeckMath.frames(11,-direction*4f,g).single {it.article==0}
            val beginning=ArcDeckMath.frames(11,-direction*4.02f,g).single {it.article==0}
            assertTrue(abs(beginning.centerY-edge.centerY)<g.cardHeight*.01f)
            var previous=edge
            for(step in 1..40) {
                val next=ArcDeckMath.frames(11,-direction*(4f+step*.02f),g).firstOrNull {it.article==0} ?: break
                assertTrue("The edge must not jump a large distance in a small drag",abs(next.centerY-previous.centerY)<g.cardHeight*.06f)
                assertEquals(1f,next.alpha,0f)
                previous=next
            }
        }
    }
    @Test fun centrePoseAndBothLeftReturningHalvesFitPortraitPhoneSizes() {
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
            assertTrue("All cards keep the same size",frames.all {it.scale==1f})
            assertTrue(frames.filter {it.distance<0f}.all {it.centerX<selected.centerX})
            assertTrue(frames.filter {it.distance>0f}.all {it.centerX<selected.centerX})
        }
    }
    @Test fun bothHalvesFollowTheSameEllipseWithoutChangingTheSequenceLayers() {
        val g=ArcDeckMath.geometry(411f,780f)
        val frames=ArcDeckMath.frames(11,0f,g)
        var lastLeft=g.focusX
        var lastRight=g.focusX
        val selected=frames.single {it.article==0}
        for(step in 1..4) {
            val below=frames.single {it.article==step}
            val above=frames.single {it.article==11-step}
            assertTrue(above.centerX<lastLeft)
            assertTrue(below.centerX<lastRight)
            assertEquals(above.centerX,below.centerX,.001f)
            assertEquals(g.focusY*2f,above.centerY+below.centerY,.001f)
            assertEquals(above.scale,below.scale,.0001f)
            assertTrue("Earlier cards remain behind the selected article",above.layer<selected.layer)
            assertTrue("Later cards remain above it in sequence",below.layer>selected.layer)
            val x=(below.centerX-(g.focusX-g.radiusX))/g.radiusX
            val y=(below.centerY-g.focusY)/g.radiusY
            assertEquals(1f,x*x+y*y,.001f)
            lastLeft=above.centerX
            lastRight=below.centerX
        }
    }
    @Test fun neighbouringCardsOverlapWithoutReservingACentreWindow() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(step in 0..40) {
            val frames=ArcDeckMath.frames(11,step/40f,g)
            val earlier=frames.single {it.article==0}
            val later=frames.single {it.article==1}
            assertTrue(earlier.layer<later.layer)
            val earlierBottom=earlier.centerY+ArcDeckMath.waveY(earlier.distance,earlier.waveOffset)+g.cardHeight*earlier.scale/2f
            val laterTop=later.centerY+ArcDeckMath.waveY(later.distance,later.waveOffset)-g.cardHeight*later.scale/2f
            assertTrue("The ring remains continuous through the centre",earlierBottom>laterTop)
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
        assertTrue(ArcDeckMath.waveX(below.distance,below.waveOffset)<0f)
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
                assertTrue(abs(pose.distance)<=5.5f)
                assertEquals("Cards leave by motion, never fading",1f,pose.alpha,0f)
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
    @Test fun recyclingHappensOnlyAfterTheWholeCardHasClearedTheScreen() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            var lastY=Float.NEGATIVE_INFINITY
            for(d in listOf(4.1f,4.3f,4.5f,4.7f)) {
                val bottom=ArcDeckMath.frames(11,-d,g).single {it.article==0}
                assertTrue(bottom.centerY>lastY)
                assertEquals(1f,bottom.alpha,0f)
                lastY=bottom.centerY
            }
            assertFalse(ArcDeckMath.frames(11,-5f,g).any {it.article==0})
            assertFalse(ArcDeckMath.frames(11,-5.49f,g).any {it.article==0})
            assertFalse(ArcDeckMath.frames(11,-5.51f,g).any {it.article==0})
        }
    }
    @Test fun approvedCentralRingCoordinatesAndRestingVisibleSlotsStayUnchanged() {
        val w=411f;val h=780f
        val g=ArcDeckMath.geometry(w,h)
        val resting=ArcDeckMath.frames(11,0f,g)
        assertEquals(9,resting.size)
        assertTrue(resting.all {abs(it.distance)<=4f})
        for(position in listOf(0f,.25f,.5f,.75f)) {
            for(pose in ArcDeckMath.frames(11,position,g).filter {abs(it.distance)<=ArcDeckMath.EDGE_START_DISTANCE}) {
                val angle=pose.distance*.30f
                assertEquals(w*.48f-w*.30f*(1f-cos(angle)),pose.centerX,.001f)
                assertEquals(h*.53f+h*.34f*sin(angle),pose.centerY,.001f)
                assertEquals(1f,pose.scale,0f)
                assertEquals(pose.distance,pose.layer,0f)
            }
        }
    }
    @Test fun tiltTravelsInOppositeDirectionsAndDecaysThroughNeighbours() {
        fun tilt(page: Int)=ArcDeckMath.neighbourTilt(page.toFloat())
        assertEquals(0f,tilt(0),0f)
        assertEquals(-20f,tilt(1),0f)
        assertEquals(-6f,tilt(2),0f)
        assertEquals(0f,tilt(3),0f)
        assertTrue("Back cards lean away; front cards lean toward the viewer",tilt(-1)>0f&&tilt(1)<0f)
        for(page in 2..5) {
            assertTrue(abs(tilt(page))<=abs(tilt(page-1)))
            assertEquals(-tilt(page),tilt(-page),.001f)
        }
        assertTrue(ArcDeckMath.FOCUS_ELEVATION>0f)
    }
    @Test fun cardsThatFitPullInPlaceWithoutRecenteringTheQueue() {
        val g=ArcDeckMath.geometry(411f,914f)
        for(article in listOf(0,1,2)) {
            assertEquals(0f,ArcDeckMath.pullPosition(article,0f,11,g,100f,802f),0f)
        }
    }
    @Test fun edgeCardsMoveOnlyAsFarAsTheirNearestSafeBoundary() {
        for((w,h) in listOf(360f to 720f,411f to 914f)) {
            val g=ArcDeckMath.geometry(w,h)
            val top=100f;val bottom=h-112f
            assertTrue(ArcDeckMath.canPull(g,top,bottom))
            for(article in listOf(7,8,4)) {
                val target=ArcDeckMath.pullPosition(article,0f,11,g,top,bottom)
                assertTrue(abs(target)>.001f)
                val pose=ArcDeckMath.frames(11,target,g).single {it.article==article}
                assertTrue("Stop at the boundary, not at an integer centre slot",abs(pose.distance)>.01f)
                val centerY=pose.centerY-ArcDeckMath.pullLift(pose.distance,g)
                assertTrue(centerY-g.cardHeight/2f>=top-.002f)
                assertTrue(centerY+g.cardHeight/2f<=bottom+.002f)
                assertEquals(target,ArcDeckMath.pullPosition(article,target,11,g,top,bottom),.0001f)
                val less=target-sign(target)*.01f
                val short=ArcDeckMath.frames(11,less,g).single {it.article==article}
                val shortY=short.centerY-ArcDeckMath.pullLift(short.distance,g)
                assertTrue("Any shorter travel still clips the card",shortY-g.cardHeight/2f<top||shortY+g.cardHeight/2f>bottom)
            }
        }
    }
    @Test fun pullClearsTheFrontCardWithoutChangingItsLayerOrBaseRing() {
        val g=ArcDeckMath.geometry(411f,914f)
        val original=ArcDeckMath.frames(11,0f,g)
        for(pose in original.filter {abs(it.distance)<=3f}) {
            val next=original.single {it.distance==pose.distance+1f}
            val bottom=pose.centerY-ArcDeckMath.pullLift(pose.distance,g)+g.cardHeight/2f
            assertTrue("Lift, not z-order, exposes the card",bottom<=next.centerY-g.cardHeight/2f-15.99f)
            assertTrue(pose.layer<next.layer)
        }
        assertEquals(g.width-16f,ArcDeckMath.pulledLeft(g)+g.cardWidth,.001f)
        assertEquals(original,ArcDeckMath.frames(11,0f,g))
    }
    @Test fun shortViewportUsesTheReadableFallbackWhenAWholeCardCannotFit() {
        val g=ArcDeckMath.geometry(360f,440f)
        assertFalse(ArcDeckMath.canPull(g,100f,220f))
    }
}
