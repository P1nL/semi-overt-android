package com.semiovert.app

import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.*
import org.junit.Assert.*
import org.junit.Test

class DeckSceneMotionTest {
    @Test fun homeExitIsExactlyNineHundredMilliseconds(){assertEquals(900,SceneMotion.HOME_EXIT_MS)}
    @Test fun primaryElementsGetOneSecondWithOverlappingSceneWindows() {
        assertEquals(1000,SceneMotion.PRIMARY_ELEMENT_MS)
        assertEquals(1180,SceneMotion.HOME_ENTER_MS)
        assertEquals(1500,SceneMotion.PROFILE_ENTER_MS);assertEquals(1500,SceneMotion.PROFILE_EXIT_MS)
        assertEquals(1180,SceneMotion.SEARCH_ENTER_MS);assertEquals(1180,SceneMotion.SEARCH_EXIT_MS)
    }
    @Test fun profileWindowsUseRealMillisecondsInsteadOfCompressedProportions() {
        for((start,end) in listOf(0f to 1000f,120f to 900f,220f to 820f,280f to 880f,340f to 940f,400f to 1000f,320f to 1500f,1000f to 1500f)) {
            assertEquals(0f,SceneMotion.profileSegment(0f,start,end),0f)
            assertEquals(.5f,SceneMotion.profileSegment((start+end)/(2f*SceneMotion.PROFILE_ENTER_MS),start,end),.001f)
            assertEquals(1f,SceneMotion.profileSegment(1f,start,end),0f)
        }
    }
    @Test fun sceneTravelDoesNotFinishMostOfItsDistanceInTheFirstThird() {
        assertTrue(SoSceneEase.transform(.3f)<.3f)
        assertEquals(.5f,SoSceneEase.transform(.5f),.001f)
        assertTrue(SoSceneEase.transform(.8f)<.95f)
        assertEquals(.5f,SceneMotion.searchHeader(500f/SceneMotion.SEARCH_ENTER_MS),.001f)
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
