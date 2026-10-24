package com.semiovert.app

import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.*
import androidx.compose.animation.core.*
import org.junit.Assert.*
import org.junit.Test

class DeckSceneMotionTest {
    @Test fun browseLabelAndCounterEnterAndExitInSixHundredMilliseconds() {
        assertEquals(600,SceneMotion.BROWSE_ANNOTATION_MS)
        assertEquals(1f,SceneMotion.browseAnnotationAlpha(0f,FlowPhase.IDLE,0),0f)
        for(duration in listOf(SceneMotion.CATEGORY_ENTER_MS,SceneMotion.SEARCH_ENTER_MS)) {
            for(phase in listOf(FlowPhase.ENTER,FlowPhase.EXIT)) {
                fun alpha(elapsed: Int): Float {
                    val progress=elapsed.toFloat()/duration
                    return SceneMotion.browseAnnotationAlpha(if(phase==FlowPhase.ENTER)1f-progress else progress,phase,duration)
                }
                assertEquals(if(phase==FlowPhase.ENTER)0f else 1f,alpha(0),.001f)
                assertEquals(.5f,alpha(300),.001f)
                assertEquals(if(phase==FlowPhase.ENTER)1f else 0f,alpha(600),.001f)
                assertEquals(if(phase==FlowPhase.ENTER)1f else 0f,alpha(duration),.001f)
            }
        }
    }

    @Test fun categorySwapUsesFiveHundredAndFiftyMillisecondsPerPhase() {
        assertEquals(550,SceneMotion.CATEGORY_SWAP_MS)
    }

    @Test fun categoryEntranceKeepsASmallReboundInsideItsExistingWindow() {
        val travel=780f+24f-220f
        fun offset(p: Float,elastic: Boolean=true,phase: FlowPhase=FlowPhase.ENTER)=
            SceneMotion.waveOffset(if(phase==FlowPhase.ENTER)1f-p else p,phase,0,1,220f,780f,1000,0,elastic)
        assertEquals(travel,offset(0f),.001f)
        assertEquals(travel*.5f,offset(.5f),.01f)
        val lowest=(55..100).minOf {offset(it/100f)}
        assertTrue("Entrance gently passes its resting point",lowest< -1f)
        assertTrue("Softer rebound stays below one and a half percent of travel",lowest> -travel*.015f)
        assertEquals(0f,offset(1f),0f)
        for(frame in 0..20) {
            val p=frame/20f
            assertEquals(offset(p,false,FlowPhase.EXIT),offset(p,true,FlowPhase.EXIT),0f)
        }
    }

    @Test fun readingOpenAndReturnLeaveTimeForVisibleStages() {
        val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(),Float.VectorConverter,0f,1f)
        assertEquals(800_000_000L,opening.durationNanos)
        assertEquals(.5f,opening.getValueFromNanos(400_000_000L),.001f)
        assertEquals(1f,opening.getValueFromNanos(opening.durationNanos),0f)
        assertTrue(SceneMotion.readingContainer(.70f)<.2f)
        assertEquals(.5f,SceneMotion.readingContainer(.78f),.001f)
        assertTrue(SceneMotion.readingContainer(.81f)>.5f)
        assertEquals(1f,SceneMotion.readingContainer(.94f),0f)
        val closing=TargetBasedAnimation(SceneMotion.readingCloseSpec(),Float.VectorConverter,1f,0f)
        assertEquals(1_300_000_000L,closing.durationNanos)
        assertTrue(SceneMotion.readingBody(closing.getValueFromNanos(closing.durationNanos*5/32))>0f)
        assertEquals(.5f,closing.getValueFromNanos(650_000_000L),.001f)
        assertEquals(0f,closing.getValueFromNanos(closing.durationNanos),0f)
    }
    @Test fun fastLiftCannotShortenOpeningOrRushTheTitleStage() {
        val speed=SceneMotion.readingReleaseVelocity(12_000f)
        for(start in listOf(0f,SceneMotion.readingDrag(.1f),SceneMotion.readingDrag(20f))) {
            val opening=TargetBasedAnimation(SceneMotion.readingOpenSpec(),Float.VectorConverter,start,1f)
            val flung=TargetBasedAnimation(SceneMotion.readingOpenSpec(),Float.VectorConverter,start,1f,AnimationVector1D(speed))
            assertEquals(800_000_000L,flung.durationNanos)
            val times=(0..100).map {it*flung.durationNanos/100}
            for(time in times)assertEquals(opening.getValueFromNanos(time),flung.getValueFromNanos(time),0f)
            val titleStart=times.first {SceneMotion.readingTitlePosition(flung.getValueFromNanos(it))>0f}
            val titleEnd=times.first {SceneMotion.readingTitlePosition(flung.getValueFromNanos(it))>=1f}
            assertTrue("A fast lift preserves the title's share of the shorter opening",titleEnd-titleStart>=flung.durationNanos*3/16)
        }
    }
    @Test fun readingTextSettlesBeforeAvatarAndBodyBegin() {
        val held=SceneMotion.readingDrag(10f)
        assertTrue(held<SceneMotion.READING_CONTAINER_START)
        assertEquals(0f,SceneMotion.readingText(held),0f)
        assertEquals(0f,SceneMotion.readingTitlePosition(held),0f)
        assertEquals(0f,SceneMotion.readingAvatar(held),0f)
        assertEquals(0f,SceneMotion.readingBody(held),0f)
        assertTrue(SceneMotion.readingChrome(SceneMotion.readingDrag(.2f))>0f)
        assertEquals(1f,SceneMotion.readingChrome(SceneMotion.readingDrag(1f)),0f)
        assertEquals(1f,SceneMotion.readingText(.88f),.001f)
        assertTrue(SceneMotion.readingAvatar(.92f)>0f)
        assertTrue(SceneMotion.readingBody(.96f)>0f)
        assertEquals(1f,SceneMotion.readingBody(1f),0f)
        assertEquals(0f,SceneMotion.readingText(0f),0f)
    }
    @Test fun stackReadingTitleTravelsEarlierAndDeceleratesIntoItsAnchor() {
        val start=SceneMotion.READING_CONTAINER_START;val end=SceneMotion.READING_TITLE_END
        assertEquals(0f,SceneMotion.readingTitlePosition(0f),0f)
        assertEquals(0f,SceneMotion.readingTitlePosition(start),0f)
        assertTrue(SceneMotion.readingTitlePosition(.70f)>0f)
        var previous=0f;var previousStep=Float.POSITIVE_INFINITY
        for(frame in 1..8) {
            val next=SceneMotion.readingTitlePosition(start+(end-start)*frame/8f)
            val step=next-previous
            assertTrue("Title keeps moving toward its anchor",step>0f)
            assertTrue("Title displacement decreases toward arrival",step<previousStep)
            previous=next;previousStep=step
        }
        assertEquals(1f,previous,0f)
        assertEquals(1f,SceneMotion.readingTitlePosition(1f),0f)
        assertEquals(0f,SceneMotion.readingAvatar(end),0f)
    }
    @Test fun readingSpringCarriesReleaseVelocityAndFollowersReboundInBothDirections() {
        val speed=SceneMotion.readingReleaseVelocity(640f)
        val release=TargetBasedAnimation(SceneMotion.readingSpring(),Float.VectorConverter,.25f,1f,AnimationVector1D(speed))
        assertEquals(speed,release.getVelocityVectorFromNanos(0L).value,.001f)
        assertTrue(SceneMotion.readingReleaseVelocity(-640f)<0f)
        val down=TargetBasedAnimation(SceneMotion.readingFollowerSpring(0),Float.VectorConverter,0f,100f)
        val up=TargetBasedAnimation(SceneMotion.readingFollowerSpring(0),Float.VectorConverter,100f,0f,AnimationVector1D(300f))
        val samples=(0..180).map {it*1_000_000_000L/120}
        assertTrue(samples.maxOf {down.getValueFromNanos(it)}>100f)
        assertTrue(up.getValueFromNanos(8_000_000L)>100f)
        assertTrue(samples.minOf {up.getValueFromNanos(it)}<0f)
        assertEquals(0f,up.getValueFromNanos(samples.last()),.01f)
    }
    @Test fun readingStackExitsInOrderAndUsesTheSameReversibleProgress() {
        assertTrue(SceneMotion.readingOffset(.04f,0,300f,900f)>0f)
        assertEquals(0f,SceneMotion.readingOffset(.04f,1,300f,900f),0f)
        for(rank in 0..5) {
            assertEquals(0f,SceneMotion.readingOffset(0f,rank,300f,900f),0f)
            assertEquals(624f,SceneMotion.readingOffset(1f,rank,300f,900f),.001f)
        }
        assertTrue(SceneMotion.CATEGORY_SWAP_MS<SceneMotion.CATEGORY_EXIT_MS)
    }
    @Test fun homeExitIsExactlyNineHundredMilliseconds(){assertEquals(900,SceneMotion.HOME_EXIT_MS)}
    @Test fun primaryElementsGetOneSecondWithOverlappingSceneWindows() {
        assertEquals(1000,SceneMotion.PRIMARY_ELEMENT_MS)
        assertEquals(1180,SceneMotion.HOME_ENTER_MS)
        assertEquals(1100,SceneMotion.PROFILE_ENTER_MS);assertEquals(1000,SceneMotion.PROFILE_EXIT_MS)
        assertEquals(450,SceneMotion.PROFILE_GUEST_MS)
        assertEquals(900,SceneMotion.SEARCH_ENTER_MS);assertEquals(900,SceneMotion.SEARCH_EXIT_MS)
    }
    @Test fun profileWindowsUseRealMillisecondsInsteadOfCompressedProportions() {
        for((start,end) in listOf(0f to 1000f,120f to 620f,200f to 550f,240f to 600f,280f to 680f,300f to 650f,100f to 1100f,300f to 550f)) {
            assertEquals(0f,SceneMotion.profileSegment(0f,start,end),0f)
            assertEquals(.5f,SceneMotion.profileSegment((start+end)/(2f*SceneMotion.PROFILE_ENTER_MS),start,end),.001f)
            assertEquals(1f,SceneMotion.profileSegment(1f,start,end),0f)
        }
    }
    @Test fun profileDeckGetsItsFullSecondWithoutASecondRoundOfStagger() {
        val travel=780f+24f-220f
        for(rank in 0..4) {
            assertEquals(travel*.5f,SceneMotion.waveOffset(.5f,FlowPhase.ENTER,rank,5,220f,780f,1000,0),.01f)
            assertEquals(0f,SceneMotion.waveOffset(0f,FlowPhase.ENTER,rank,5,220f,780f,1000,0),0f)
        }
    }
    @Test fun sceneTravelDoesNotFinishMostOfItsDistanceInTheFirstThird() {
        assertTrue(SoSceneEase.transform(.3f)<.3f)
        assertEquals(.5f,SoSceneEase.transform(.5f),.001f)
        assertTrue(SoSceneEase.transform(.8f)<.95f)
        assertEquals(.5f,SceneMotion.searchHeader(375f/SceneMotion.SEARCH_ENTER_MS),.001f)
        assertEquals(.5f,SceneMotion.profileSegment(500f/SceneMotion.PROFILE_ENTER_MS,0f,1000f),.001f)
    }
    @Test fun eachWaveCardMovesForTheWholeSecondAfterItsOwnStagger() {
        val count=7;val top=220f;val height=780f;val travel=height+24f-top
        for(rank in 0 until count) {
            val delay=rank.toFloat()/(count-1)*SceneMotion.DECK_STAGGER_MS
            val halfway=(delay+500f)/SceneMotion.CATEGORY_EXIT_MS
            assertEquals(travel*.5f,SceneMotion.waveOffset(halfway,FlowPhase.EXIT,rank,count,top,height),.01f)
            val finished=(delay+1000f)/SceneMotion.CATEGORY_EXIT_MS
            assertEquals(travel,SceneMotion.waveOffset(finished,FlowPhase.EXIT,rank,count,top,height),.01f)
        }
    }
    @Test fun searchEntranceAndExitFitTheSameFasterHeaderAndCardWindows() {
        val duration=SceneMotion.SEARCH_ENTER_MS
        val stagger=SceneMotion.SEARCH_STAGGER_MS
        val primary=SceneMotion.SEARCH_PRIMARY_MS
        assertEquals(750,primary);assertEquals(150,stagger)
        assertEquals(1f,SceneMotion.searchHeader(primary.toFloat()/duration),.001f)
        assertEquals(duration,SceneMotion.SEARCH_EXIT_MS)
        assertEquals(.5f,SceneMotion.searchHeader(375f/SceneMotion.SEARCH_EXIT_MS),.001f)
        val count=7;val top=220f;val height=780f;val travel=height+24f-top
        for(rank in 0 until count) {
            val delay=(count-1-rank).toFloat()/(count-1)*stagger
            val halfway=1f-(delay+primary/2f)/duration
            assertEquals(travel*.5f,SceneMotion.waveOffset(halfway,FlowPhase.ENTER,rank,count,top,height,duration,stagger),.01f)
            val finished=1f-(delay+primary)/duration
            assertEquals(0f,SceneMotion.waveOffset(finished,FlowPhase.ENTER,rank,count,top,height,duration,stagger),.001f)
            val exitDelay=rank.toFloat()/(count-1)*stagger
            val exitHalfway=(exitDelay+primary/2f)/duration
            assertEquals(travel*.5f,SceneMotion.waveOffset(exitHalfway,FlowPhase.EXIT,rank,count,top,height,duration,stagger),.01f)
            val exitFinished=(exitDelay+primary)/duration
            assertEquals(travel,SceneMotion.waveOffset(exitFinished,FlowPhase.EXIT,rank,count,top,height,duration,stagger),.001f)
        }
    }
    @Test fun homeEntranceKeepsArticleOneAtTheRealFocusAndStartsAboveTheScreen() {
        val g=ArcDeckMath.geometry(411f,780f)
        val poses=ArcDeckMath.frames(11,0f,g)
        for(pose in poses){val entering=ArcDeckMath.scenePose(pose,g,1f,FlowPhase.ENTER);assertTrue(entering.centerY+g.cardHeight*entering.scale/2f<0f)}
        val focus=ArcDeckMath.scenePose(poses.single {it.article==0},g,0f,FlowPhase.ENTER)
        assertEquals(0,focus.article);assertEquals(g.focusX,focus.centerX,.001f);assertEquals(g.focusY,focus.centerY,.001f)
    }
    @Test fun homeExitContinuesDownAndAllVisibleCardsClearTheBottom() {
        for((w,h) in listOf(360f to 760f,411f to 780f,800f to 1200f)) {
            val g=ArcDeckMath.geometry(w,h)
            for(pose in ArcDeckMath.frames(11,2.25f,g)) {
                var previous=pose.centerY
                for(frame in 0..30){val next=ArcDeckMath.scenePose(pose,g,frame/30f,FlowPhase.EXIT);assertTrue(next.centerY>=previous-.001f);previous=next.centerY}
                val end=ArcDeckMath.scenePose(pose,g,1f,FlowPhase.EXIT)
                assertTrue("Article "+pose.article+" still inside "+w+"x"+h,end.centerY-g.cardHeight*end.scale/2f>=h)
                assertEquals(pose.article,end.article)
            }
        }
    }
    @Test fun categoryExitIsForegroundFirstAndEntranceIsBackFirst() {
        val count=6;val top=220f;val height=780f
        assertTrue(SceneMotion.waveOffset(.15f,FlowPhase.EXIT,0,count,top,height)>0f)
        assertEquals(0f,SceneMotion.waveOffset(.15f,FlowPhase.EXIT,count-1,count,top,height),.001f)
        val hidden=height+24f-top
        assertEquals(hidden,SceneMotion.waveOffset(.85f,FlowPhase.ENTER,0,count,top,height),.001f)
        assertTrue(SceneMotion.waveOffset(.85f,FlowPhase.ENTER,count-1,count,top,height)<hidden)
        for(rank in 0 until count){assertEquals(0f,SceneMotion.waveOffset(0f,FlowPhase.ENTER,rank,count,top,height),0f);assertEquals(hidden,SceneMotion.waveOffset(1f,FlowPhase.EXIT,rank,count,top,height),.001f)}
    }
    @Test fun sharedLiftCommitsOnlyAtTheDistanceOrEffectiveFlingThreshold() {
        assertFalse(SceneMotion.commits(.44f,0f));assertTrue(SceneMotion.commits(.45f,0f))
        assertFalse(SceneMotion.commits(.07f,1200f));assertTrue(SceneMotion.commits(.1f,900f));assertFalse(SceneMotion.commits(.1f,-900f))
    }
    @Test fun introAndProfileUseTheSameCompletePixelPermutation() {
        assertEquals(0,(0 until 64).count {PixelReveal.tileVisible(it,0f)})
        assertEquals(32,(0 until 64).count {PixelReveal.tileVisible(it,31f/64f)})
        assertEquals(64,(0 until 64).count {PixelReveal.tileVisible(it,1f)})
        assertEquals(0,PixelReveal.sample(0f,6));assertEquals(5,PixelReveal.sample(1f,6))
    }
}
