package com.semiovert.app

import com.semiovert.app.core.designsystem.FlowPhase
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.designsystem.SoSceneEase
import com.semiovert.app.core.designsystem.arc.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ArcDeckMathTest {
    @Test fun liftedCardsRemainVisibleAfterTheirBaseSlotLeavesTheScreen() {
        val g=ArcDeckMath.geometry(411f,780f)
        val base=ArcDeckMath.frames(11,-.4f,g,includeOffscreen=true).single {it.article==5}
        assertFalse(ArcDeckMath.frames(11,-.4f,g).any {it.article==5})
        assertFalse(ArcDeckMath.isVisible(base,g))
        val pull=.8f
        val rightAligned=ArcDeckMath.pulledLeft(g)+g.cardWidth/2f
        val drawn=base.copy(
            centerX=base.centerX+(rightAligned-base.centerX)*pull,
            centerY=base.centerY-ArcDeckMath.pullLift(base.distance,g)*pull,
        )
        assertTrue("Visibility must use the still-returning card, not its hidden base slot",
            ArcDeckMath.isVisible(drawn,g))
        assertEquals(base.article,drawn.article)
    }

    @Test fun liftedCopyOwnsTheOriginalSlotUntilItWraps() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(direction in listOf(-1f,1f)) {
            val base=ArcDeckMath.frames(11,-direction*5.2f,g,includeOffscreen=true).single {it.article==0}
            val copies=ArcDeckMath.copies(base,direction*5.1f,11,g,hasLiftedCopy=true)
            assertSame(base,copies.queue)
            assertSame(base,copies.lifted)
            assertFalse("The original slot must not paint through the lifted card",copies.queueVisible)
        }
    }

    @Test fun wrappedQueueSlotDoesNotWaitForTheLiftedCopyToReturn() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(direction in listOf(-1f,1f)) {
            val base=ArcDeckMath.frames(11,-direction*6.2f,g,includeOffscreen=true).single {it.article==0}
            val copies=ArcDeckMath.copies(base,direction*5.49f,11,g,hasLiftedCopy=true)
            assertTrue("The incoming queue card must paint while the old copy is still returning",copies.queueVisible)
            assertSame(base,copies.queue)
            assertTrue(ArcDeckMath.isVisible(copies.queue,g))
            assertEquals(direction*6.2f,copies.lifted!!.distance,.0001f)
            assertEquals(base.article,copies.lifted.article)
            assertEquals(11f,abs(copies.queue.distance-copies.lifted.distance),.0001f)
            val settled=ArcDeckMath.copies(base,copies.lifted.distance,11,g,hasLiftedCopy=false)
            assertNull(settled.lifted)
            assertTrue(settled.queueVisible)
            assertSame("Finishing the spring cannot replace or move the queue card",copies.queue,settled.queue)
        }
    }

    @Test fun reversingAcrossTheBoundaryRestoresSingleCopyOwnership() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(direction in listOf(-1f,1f)) {
            var previous=direction*4.9f
            for(distance in listOf(5.2f,5.49f,5.8f,6.2f,5.8f,5.49f,5.2f)) {
                val base=ArcDeckMath.frames(11,-direction*distance,g,includeOffscreen=true).single {it.article==0}
                val copies=ArcDeckMath.copies(base,previous,11,g,hasLiftedCopy=true)
                assertEquals(direction*distance,copies.lifted!!.distance,.0001f)
                assertEquals(distance>5.5f,copies.queueVisible)
                previous=copies.lifted.distance
            }
        }
    }

    @Test fun queueKeepsLoopingIndependentlyAcrossMultipleReturningLaps() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(direction in listOf(-1f,1f)) {
            var previous=direction*5f
            for(step in 1..92) {
                val distance=direction*(5f+step*.25f)
                val base=ArcDeckMath.frames(11,-distance,g,includeOffscreen=true).single {it.article==0}
                val copies=ArcDeckMath.copies(base,previous,11,g,hasLiftedCopy=true)
                assertEquals(distance,copies.lifted!!.distance,.0001f)
                assertSame(base,copies.queue)
                if(abs(distance)>5.5f)assertTrue(copies.queueVisible)
                previous=copies.lifted.distance
            }
        }
    }

    @Test fun returningCardsContinueAcrossLoopBoundariesWithoutJumpingToTheOtherEnd() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(direction in listOf(-1f,1f)) {
            val before=ArcDeckMath.frames(11,-direction*5.49f,g,includeOffscreen=true).single {it.article==0}
            val wrapped=ArcDeckMath.frames(11,-direction*5.51f,g,includeOffscreen=true).single {it.article==0}
            assertTrue(before.distance*wrapped.distance<0f)
            val continued=ArcDeckMath.continuePose(wrapped,before.distance,11,g)
            assertEquals(direction*5.51f,continued.distance,.0001f)
            assertTrue(abs(continued.centerY-before.centerY)<g.scrollStep*.1f)
            assertTrue(abs(continued.centerX-before.centerX)<g.cardWidth*.01f)
            assertEquals(before.layer+direction*.02f,continued.layer,.0001f)
            assertEquals(before,ArcDeckMath.continuePose(before,continued.distance,11,g))
        }
    }

    @Test fun scrollingFeedsUpperCardsFromTheUpperLeftWhileKeepingThemStacked() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            val join=ArcDeckMath.frames(11,ArcDeckMath.UPPER_ENTER_START_DISTANCE,g).single {it.article==0}
            assertFalse(ArcDeckMath.frames(11,ArcDeckMath.EDGE_CLEAR_DISTANCE,g).any {it.article==0})
            val positions=(0..290).map {ArcDeckMath.EDGE_CLEAR_DISTANCE-it*.01f}
            val visible=positions.mapNotNull {position ->
                ArcDeckMath.frames(11,position,g).firstOrNull {it.article==0}?.let {position to it}
            }
            assertTrue(visible.isNotEmpty())
            var previous=visible.first().second
            assertTrue(previous.centerX<0f)
            assertTrue(previous.centerX+g.cardWidth/2f>=0f)
            assertTrue("Entry stays near the upper queue",previous.centerY>0f)
            for((position,incoming) in visible) {
                val frames=ArcDeckMath.frames(11,position,g)
                val leading=frames.single {it.article==1}
                assertTrue(incoming.centerX>=previous.centerX)
                assertTrue(incoming.centerY>=previous.centerY)
                assertTrue(abs(incoming.centerX-leading.centerX)<g.cardWidth*.80f)
                assertTrue("Incoming cards remain vertically overlapped",
                    g.cardHeight-(leading.centerY-incoming.centerY)>g.cardHeight*.50f)
                assertTrue(incoming.layer<leading.layer)
                assertEquals(1f,incoming.scale,0f)
                assertEquals(1f,incoming.alpha,0f)
                previous=incoming
            }
            assertEquals(join.centerX,previous.centerX,.001f)
            assertEquals(join.centerY,previous.centerY,.001f)
            for((position,pose) in visible.asReversed()) {
                assertEquals(pose,ArcDeckMath.frames(11,position,g).single {it.article==0})
            }
        }
    }

    @Test fun scrollingUpperEntryMeetsTheRestingArcWithoutChangingVelocity() {
        val g=ArcDeckMath.geometry(411f,780f)
        fun at(position: Float)=ArcDeckMath.frames(11,position,g).single {it.article==0}
        val start=ArcDeckMath.UPPER_ENTER_START_DISTANCE
        val before=at(start+.001f)
        val join=at(start)
        val after=at(start-.001f)
        assertEquals(join.centerX-before.centerX,after.centerX-join.centerX,.01f)
        assertEquals(join.centerY-before.centerY,after.centerY-join.centerY,.01f)
    }

    @Test fun upperLeftEntryMirrorsLowerLeftExitAroundTheQueue() {
        val g=ArcDeckMath.geometry(411f,780f)
        for(step in 0..95) {
            val distance=ArcDeckMath.UPPER_ENTER_START_DISTANCE+step*.02f
            val upper=ArcDeckMath.frames(11,distance,g).single {it.article==0}
            val lower=ArcDeckMath.frames(11,-distance,g).single {it.article==0}
            assertEquals(upper.centerX,lower.centerX,.001f)
            assertEquals(g.focusY*2f,upper.centerY+lower.centerY,.001f)
        }
    }

    @Test fun scrollingLowerCardsStayStackedWhileLeavingThroughTheLowerLeft() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            fun at(distance: Float)=ArcDeckMath.frames(11,-distance,g).firstOrNull {it.article==0}
            val outward=(0..145).mapNotNull {step ->
                val distance=ArcDeckMath.LOWER_EXIT_START_DISTANCE+step*.02f
                at(distance)?.let {distance to it}
            }
            assertTrue(outward.isNotEmpty())
            val last=outward.last().second
            assertTrue(last.centerX<0f&&last.centerX+g.cardWidth/2f>=0f)
            assertTrue("Exit stays near the lower queue",last.centerY<g.height)
            for((before,after) in outward.map {it.second}.zipWithNext()) {
                assertTrue(after.centerX<before.centerX)
                assertTrue(after.centerY>before.centerY)
                assertTrue("No abrupt single-card fling",before.centerX-after.centerX<g.cardWidth*.03f)
                assertEquals(1f,after.scale,0f)
                assertEquals(1f,after.alpha,0f)
            }
            for((distance,pose) in outward.asReversed())assertEquals(pose,at(distance))
            assertFalse(ArcDeckMath.frames(11,-ArcDeckMath.EDGE_CLEAR_DISTANCE,g).any {it.article==0})
        }
    }

    @Test fun lowerEdgeKeepsBothHorizontalAndVerticalOverlapUntilItIsClipped() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            for(step in 0..290) {
                val frames=ArcDeckMath.frames(11,-(ArcDeckMath.LOWER_EXIT_START_DISTANCE+step*.01f),g)
                val front=frames.firstOrNull {it.article==0} ?: continue
                val behind=frames.single {it.article==10}
                assertTrue(front.layer>behind.layer)
                assertTrue("The front card keeps horizontal overlap with the queue",
                    abs(front.centerX-behind.centerX)<g.cardWidth*.80f)
                val verticalOverlap=g.cardHeight-(front.centerY-behind.centerY)
                assertTrue("The last card must not accelerate away from the stack",
                    verticalOverlap>g.cardHeight*.50f)
                assertTrue("A departing card remains drawn until it clears the screen edge",
                    front.centerY-g.cardHeight/2f<=g.height&&front.centerX+g.cardWidth/2f>0f)
            }
        }
    }

    @Test fun scrollingLowerExitKeepsTheRingsTangentAtTheJoin() {
        val g=ArcDeckMath.geometry(411f,780f)
        fun at(distance: Float)=ArcDeckMath.frames(11,-distance,g).single {it.article==0}
        val distance=ArcDeckMath.LOWER_EXIT_START_DISTANCE
        val before=at(distance-.001f)
        val join=at(distance)
        val after=at(distance+.001f)
        assertEquals(join.centerX-before.centerX,after.centerX-join.centerX,.01f)
        assertEquals(join.centerY-before.centerY,after.centerY-join.centerY,.01f)
    }

    @Test fun entranceArrivesFromTheLeftWithoutChangingCardSizeLayersOrRestingPositions() {
        for((w,h) in listOf(320f to 620f,411f to 780f,480f to 860f)) {
            val g=ArcDeckMath.geometry(w,h)
            for(position in listOf(0f,.25f,.5f)) {
                for(pose in ArcDeckMath.frames(11,position,g)) {
                    val start=ArcDeckMath.scenePose(pose,g,1f,FlowPhase.ENTER)
                    assertTrue(start.centerX+g.cardWidth/2f<0f)
                    assertTrue(start.centerY<pose.centerY)
                    for(frame in 0..120) {
                        val moving=ArcDeckMath.scenePose(pose,g,1f-frame/120f,FlowPhase.ENTER)
                        assertEquals(pose.layer,moving.layer,0f)
                        assertEquals(pose.scale,moving.scale,0f)
                        assertEquals(pose.alpha,moving.alpha,0f)
                    }
                    val nearEnd=ArcDeckMath.scenePose(pose,g,.00001f,FlowPhase.ENTER)
                    assertEquals(pose.centerX,nearEnd.centerX,.01f)
                    assertEquals(pose.centerY,nearEnd.centerY,.01f)
                    assertEquals(pose,ArcDeckMath.scenePose(pose,g,0f,FlowPhase.ENTER))
                }
            }
        }
    }

    @Test fun incomingCardsStayBehindTheLeadingQueueAndKeepMovingDown() {
        val g=ArcDeckMath.geometry(411f,780f)
        val poses=ArcDeckMath.frames(11,0f,g).sortedBy {it.distance}
        var previous=poses.map {ArcDeckMath.scenePose(it,g,1f,FlowPhase.ENTER)}
        for(frame in 1..120) {
            val moving=poses.map {ArcDeckMath.scenePose(it,g,1f-frame/120f,FlowPhase.ENTER)}
            for(index in moving.indices) {
                assertTrue(moving[index].centerY>=previous[index].centerY-.001f)
                if(index>0) {
                    assertTrue(moving[index].centerY>=moving[index-1].centerY-.001f)
                    assertTrue(moving[index].layer>moving[index-1].layer)
                }
            }
            previous=moving
        }
    }

    @Test fun diagonalApproachMeetsTheArcWithoutAnAbruptTurn() {
        val g=ArcDeckMath.geometry(411f,780f)
        val pose=ArcDeckMath.frames(11,0f,g).single {it.distance==0f}
        fun atPathProgress(progress: Float): ArcCardPose {
            var low=0f;var high=1f
            repeat(24) {
                val mid=(low+high)/2f
                if(SoSceneEase.transform(mid)<progress)low=mid else high=mid
            }
            val cardMillis=SceneMotion.HOME_ENTER_MS-SceneMotion.DECK_STAGGER_MS
            val elapsed=(low+high)/2f*cardMillis+SceneMotion.DECK_STAGGER_MS/2f
            return ArcDeckMath.scenePose(pose,g,1f-elapsed/SceneMotion.HOME_ENTER_MS,FlowPhase.ENTER)
        }
        val joinX=ArcDeckMath.frames(11,ArcDeckMath.EDGE_START_DISTANCE,g).single {it.article==0}.centerX
        var low=0f;var high=1f
        repeat(24) {
            val mid=(low+high)/2f
            if(atPathProgress(mid).centerX<joinX)low=mid else high=mid
        }
        val join=(low+high)/2f
        val before=atPathProgress(join-.0001f)
        val at=atPathProgress(join)
        val after=atPathProgress(join+.0001f)
        val incomingX=at.centerX-before.centerX;val incomingY=at.centerY-before.centerY
        val outgoingX=after.centerX-at.centerX;val outgoingY=after.centerY-at.centerY
        assertTrue(incomingX>0f&&incomingY>0f&&outgoingX>0f&&outgoingY>0f)
        assertEquals(incomingX,outgoingX,.01f)
        assertEquals(incomingY,outgoingY,.01f)
    }

    @Test fun bothEdgesStartGentlyAndShareClearanceAcrossSeveralSlots() {
        val g=ArcDeckMath.geometry(411f,780f)
        assertEquals(2.9f,ArcDeckMath.EDGE_CLEAR_DISTANCE-ArcDeckMath.UPPER_ENTER_START_DISTANCE,.0001f)
        assertEquals(2.9f,ArcDeckMath.EDGE_CLEAR_DISTANCE-ArcDeckMath.LOWER_EXIT_START_DISTANCE,.0001f)
        for(direction in listOf(-1f,1f)) {
            val start=if(direction<0f)ArcDeckMath.UPPER_ENTER_START_DISTANCE else ArcDeckMath.LOWER_EXIT_START_DISTANCE
            val edge=ArcDeckMath.frames(11,-direction*start,g).single {it.article==0}
            val beginning=ArcDeckMath.frames(11,-direction*(start+.02f),g).single {it.article==0}
            assertTrue(abs(beginning.centerY-edge.centerY)<g.cardHeight*.01f)
            var previous=edge
            for(step in 1..145) {
                val next=ArcDeckMath.frames(11,-direction*(start+step*.02f),g).firstOrNull {it.article==0} ?: break
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
    @Test fun bothHalvesKeepTheRingWidthAndLayersWhileExtendingToTheirScreenEdges() {
        val g=ArcDeckMath.geometry(411f,780f)
        val frames=ArcDeckMath.frames(11,0f,g)
        var lastLeft=g.focusX
        var lastRight=g.focusX
        val selected=frames.single {it.article==0}
        for(step in 1..5) {
            val below=frames.single {it.article==step}
            val above=frames.single {it.article==11-step}
            assertTrue(above.centerX<lastLeft)
            assertTrue(below.centerX<lastRight)
            assertEquals(above.centerX,below.centerX,.001f)
            if(step<=ArcDeckMath.LOWER_EXIT_START_DISTANCE) {
                assertEquals(g.focusY*2f,above.centerY+below.centerY,.001f)
            } else {
                val ringOffset=g.radiusY*sin(min(step.toFloat(),ArcDeckMath.VISIBLE_DISTANCE)*ArcDeckMath.ANGLE_STEP)*ArcDeckMath.STACK_VERTICAL_SPACING
                assertTrue(above.centerY<g.focusY-ringOffset)
                assertTrue(below.centerY>g.focusY+ringOffset)
            }
            assertEquals(above.scale,below.scale,.0001f)
            assertTrue("Earlier cards remain behind the selected article",above.layer<selected.layer)
            assertTrue("Later cards remain above it in sequence",below.layer>selected.layer)
            val x=(below.centerX-(g.focusX-g.radiusX))/g.radiusX
            val y=(above.centerY-g.focusY)/(g.radiusY*ArcDeckMath.STACK_VERTICAL_SPACING)
            if(step<=ArcDeckMath.UPPER_ENTER_START_DISTANCE)assertEquals(1f,x*x+y*y,.001f)
            lastLeft=above.centerX
            lastRight=below.centerX
        }
    }
    @Test fun verticalStackCompressionKeepsFocusAndDragDistance() {
        val g=ArcDeckMath.geometry(411f,780f)
        val frames=ArcDeckMath.frames(11,0f,g).sortedBy {it.distance}
        val focus=frames.single {it.distance==0f}
        assertEquals(g.focusX,focus.centerX,0f)
        assertEquals(g.focusY,focus.centerY,0f)
        assertEquals(780f*.34f*.30f,g.scrollStep,.001f)
        fun uncompressedY(distance: Float): Float {
            val t=((abs(distance)-2.5f)/2.9f).coerceIn(0f,1f)
            val edge=min(48f,g.cardHeight*.15f)*t*t
            val angle=distance.coerceIn(-ArcDeckMath.VISIBLE_DISTANCE,ArcDeckMath.VISIBLE_DISTANCE)*.30f
            return g.focusY+g.radiusY*sin(angle)+sign(distance)*edge
        }
        for((before,after) in frames.zipWithNext()) {
            val uncompressedGap=uncompressedY(after.distance)-uncompressedY(before.distance)
            assertEquals(uncompressedGap*.80f,after.centerY-before.centerY,.001f)
            assertTrue(after.centerY>before.centerY)
        }
    }
    @Test fun bothEndsShowFiveNeighboursAndBecomeDenserAwayFromTheFocus() {
        for((w,h) in listOf(320f to 620f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            val frames=ArcDeckMath.frames(11,0f,g)
            assertEquals(11,frames.size)
            fun at(distance: Float)=frames.single {it.distance==distance}
            val centralGap=at(1f).centerY-at(0f).centerY
            for(direction in listOf(-1f,1f)) {
                var previousGap=centralGap
                for(slot in 2..5) {
                    val inner=at(direction*(slot-1))
                    val outer=at(direction*slot)
                    val gap=abs(outer.centerY-inner.centerY)
                    assertTrue("Outer vertical gaps must tighten rather than spread apart",gap<previousGap)
                    assertTrue(outer.centerX+g.cardWidth/2f>0f)
                    previousGap=gap
                }
            }
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
            assertTrue(frames.size in 10..11)
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
            for(d in listOf(3f,3.4f,3.8f,4.2f)) {
                val bottom=ArcDeckMath.frames(11,-d,g).single {it.article==0}
                assertTrue(bottom.centerY>lastY)
                assertEquals(1f,bottom.alpha,0f)
                lastY=bottom.centerY
            }
            assertTrue(ArcDeckMath.frames(11,-5f,g).any {it.article==0})
            assertFalse(ArcDeckMath.frames(11,-ArcDeckMath.EDGE_CLEAR_DISTANCE,g).any {it.article==0})
            assertFalse(ArcDeckMath.frames(11,-5.49f,g).any {it.article==0})
            assertFalse(ArcDeckMath.frames(11,-5.51f,g).any {it.article==0})
        }
    }
    @Test fun centralRingCoordinatesStayUnchangedWhileRestingSlotsIncreaseToEleven() {
        val w=411f;val h=780f
        val g=ArcDeckMath.geometry(w,h)
        val resting=ArcDeckMath.frames(11,0f,g)
        assertEquals(11,resting.size)
        assertTrue(resting.all {abs(it.distance)<=5f})
        for(position in listOf(0f,.25f,.5f,.75f)) {
            for(pose in ArcDeckMath.frames(11,position,g).filter {
                it.distance>=-ArcDeckMath.UPPER_ENTER_START_DISTANCE&&it.distance<=ArcDeckMath.LOWER_EXIT_START_DISTANCE
            }) {
                val angle=pose.distance*.30f
                assertEquals(w*.48f-w*.30f*(1f-cos(angle)),pose.centerX,.001f)
                assertEquals(h*.53f+h*.34f*sin(angle)*ArcDeckMath.STACK_VERTICAL_SPACING,pose.centerY,.001f)
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
        for(article in listOf(0,1,2,4)) {
            assertEquals(0f,ArcDeckMath.pullPosition(article,0f,11,g,100f,802f),0f)
        }
    }
    @Test fun edgeCardsMoveOnlyAsFarAsTheirNearestSafeBoundary() {
        for((w,h) in listOf(360f to 720f,411f to 914f)) {
            val g=ArcDeckMath.geometry(w,h)
            val top=100f;val bottom=h-112f
            assertTrue(ArcDeckMath.canPull(g,top,bottom))
            for(article in listOf(7,8)) {
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
