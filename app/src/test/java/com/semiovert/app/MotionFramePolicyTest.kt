package com.semiovert.app

import com.semiovert.app.core.designsystem.*
import org.junit.Assert.*
import org.junit.Test

class MotionFramePolicyTest {
    @Test fun quietRequestReleasesAfterFourHundredMilliseconds() {
        val activity=MotionFrameActivity()
        activity.activity(1_000)
        assertTrue(activity.highRequested)
        assertFalse(activity.releaseIfIdle(1_399))
        assertTrue(activity.releaseIfIdle(1_400))
        assertFalse(activity.highRequested)
    }

    @Test fun everyAnimationFrameExtendsTheQuietWindowWithoutChangingItsClock() {
        val activity=MotionFrameActivity()
        activity.activity(0)
        activity.activity(350)
        assertFalse(activity.releaseIfIdle(400))
        assertEquals(350L,activity.remainingIdleMillis(400))
        activity.activity(700)
        assertFalse(activity.releaseIfIdle(1_099))
        assertTrue(activity.releaseIfIdle(1_100))
    }

    @Test fun holdingEitherWindowKeepsHighRefreshAndReleaseStartsANewQuietWindow() {
        val activity=MotionFrameActivity()
        val main=Any();val sheet=Any()
        activity.touch(main,true,0)
        activity.touch(sheet,true,20)
        activity.touch(main,false,500)
        assertFalse(activity.releaseIfIdle(5_000))
        assertNull(activity.remainingIdleMillis(5_000))
        activity.touch(sheet,false,5_000)
        assertFalse(activity.releaseIfIdle(5_399))
        assertTrue(activity.releaseIfIdle(5_400))
    }

    @Test fun closingAWindowRemovesItsHeldInputAndStoppingClearsTheRequest() {
        val activity=MotionFrameActivity()
        val window=Any()
        activity.touch(window,true,0)
        activity.forget(window)
        assertTrue(activity.releaseIfIdle(400))
        activity.touch(window,true,500)
        activity.clear()
        assertFalse(activity.highRequested)
        activity.activity(1_000)
        assertTrue(activity.releaseIfIdle(1_400))
    }

    @Test fun targetIs120AndLegacyDevicesOnlyRequestSupportedRates() {
        assertEquals(120f,MotionFramePolicy.TARGET_FPS,0f)
        assertEquals(120f,MotionFramePolicy.legacyRefreshRate(listOf(60f,90f,120f,144f)),0f)
        assertEquals(90f,MotionFramePolicy.legacyRefreshRate(listOf(60f,90f,144f)),0f)
        assertEquals(60f,MotionFramePolicy.legacyRefreshRate(listOf(60f)),0f)
        assertEquals(144f,MotionFramePolicy.legacyRefreshRate(listOf(144f)),0f)
        assertEquals(0f,MotionFramePolicy.legacyRefreshRate(emptyList()),0f)
    }

}
