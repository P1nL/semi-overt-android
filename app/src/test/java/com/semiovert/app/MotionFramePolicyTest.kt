package com.semiovert.app

import com.semiovert.app.core.designsystem.*
import org.junit.Assert.*
import org.junit.Test

class MotionFramePolicyTest {
    @Test fun targetIs120AndLegacyDevicesOnlyRequestSupportedRates() {
        assertEquals(120f,MotionFramePolicy.TARGET_FPS,0f)
        assertEquals(120f,MotionFramePolicy.legacyRefreshRate(listOf(60f,90f,120f,144f)),0f)
        assertEquals(90f,MotionFramePolicy.legacyRefreshRate(listOf(60f,90f,144f)),0f)
        assertEquals(60f,MotionFramePolicy.legacyRefreshRate(listOf(60f)),0f)
        assertEquals(144f,MotionFramePolicy.legacyRefreshRate(listOf(144f)),0f)
        assertEquals(0f,MotionFramePolicy.legacyRefreshRate(emptyList()),0f)
    }

    @Test fun guestHeadKeepsTheSuppliedBounceKeyframes() {
        assertEquals(600,GuestIdentityMotion.BOUNCE_MILLIS)
        for(p in listOf(0f,1f)) {
            assertEquals(0f,GuestIdentityMotion.headOffset(p),.0001f)
        }
        assertEquals(4f,GuestIdentityMotion.headOffset(.33f),.0001f)
        assertEquals(-2f,GuestIdentityMotion.headOffset(.66f),.0001f)
        assertEquals(2f,GuestIdentityMotion.headOffset(.165f),.001f)
    }

    @Test fun guestRisesFromBelowTheCircleBeforeBouncingAtItsRestingPosition() {
        assertEquals(560,GuestIdentityMotion.RISE_MILLIS)
        assertEquals(1f,GuestIdentityMotion.riseOffset(0f),0f)
        assertEquals(.5f,GuestIdentityMotion.riseOffset(.5f),0f)
        assertEquals(0f,GuestIdentityMotion.riseOffset(1f),0f)
        for(frame in 0..120) {
            val p=frame/120f
            assertTrue(GuestIdentityMotion.headOffset(p) in -2f..4f)
        }
    }
}
