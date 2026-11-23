package com.semiovert.app

import com.semiovert.app.core.designsystem.BrandIntroMotion
import org.junit.Assert.*
import org.junit.Test

class BrandIntroMotionTest {
    @Test fun starAndWritingUseTheAcceptedDurations() {
        assertEquals(700,BrandIntroMotion.STAR_MILLIS)
        assertEquals(2_000,BrandIntroMotion.WRITING_MILLIS)
        assertEquals(4_800,BrandIntroMotion.TOTAL_MILLIS)
        assertEquals(.5f,BrandIntroMotion.starProgress(350f),.0001f)
        assertEquals(1f,BrandIntroMotion.starProgress(700f),0f)
        assertEquals(0f,BrandIntroMotion.wordProgress(700f),0f)
        assertEquals(1f,BrandIntroMotion.wordProgress(2_700f),0f)
    }

    @Test fun handwritingRetainsTheReferenceCurveAndNeverRunsBackwards() {
        for((x,y) in listOf(
            0f to 0f,.116f to .064f,.188f to .278f,.424f to .38f,
            .512f to .574f,.696f to .632f,.788f to .826f,1f to 1f,
        ))assertEquals(y,BrandIntroMotion.handwriting(x),.0001f)
        val samples=(0..1_000).map {BrandIntroMotion.handwriting(it/1_000f)}
        assertTrue(samples.zipWithNext().all {(before,after)->after>=before})
        assertTrue(samples.all {it in 0f..1f})
    }

    @Test fun theHorizontalStrokeFinishesBeforeTheLogoMovesLeft() {
        val wordLength=2_000f
        val start=BrandIntroMotion.vStartMillis(wordLength)
        assertTrue(start in 700f..2_700f)
        assertEquals(312f,BrandIntroMotion.wordProgress(start)*wordLength,.01f)
        assertEquals(0f,BrandIntroMotion.leftShift(start-1f,start),0f)
        assertEquals(0f,BrandIntroMotion.leftShift(start,start),.0001f)
        assertTrue(BrandIntroMotion.leftShift(start+100f,start)>0f)
        assertEquals(265f,BrandIntroMotion.leftShift(2_700f,start),0f)
    }

    @Test fun holdThenReverseFinishBeforeThePortalOpens() {
        val start=BrandIntroMotion.vStartMillis(2_000f)
        assertEquals(1f,BrandIntroMotion.wordProgress(2_899f),0f)
        assertEquals(.5f,BrandIntroMotion.wordProgress(3_300f),.0001f)
        assertEquals(132.5f,BrandIntroMotion.leftShift(3_300f,start),.0001f)
        assertEquals(0f,BrandIntroMotion.wordProgress(3_700f),0f)
        assertEquals(0f,BrandIntroMotion.leftShift(3_700f,start),0f)
        assertEquals(0f,BrandIntroMotion.homeOpacity(3_700f),0f)
        assertEquals(1f,BrandIntroMotion.portalZoom(3_700f,40f),0f)
    }

    @Test fun passingThroughTheStarLandsOnTheUnscaledPage() {
        assertTrue(BrandIntroMotion.homeOpacity(3_800f) in 0f..1f)
        assertEquals(1f,BrandIntroMotion.homeOpacity(4_000f),0f)
        assertTrue(BrandIntroMotion.portalZoom(4_400f,40f)>BrandIntroMotion.portalZoom(4_000f,40f))
        assertEquals(40f,BrandIntroMotion.portalZoom(4_800f,40f),.0001f)
        assertEquals(1f,BrandIntroMotion.homeScale(4_800f),.0001f)
    }
}
