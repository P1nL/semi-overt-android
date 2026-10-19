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

    @Test fun spriteInterpolationKeepsItsDurationAndBlendsAcrossAtlasBoundaries() {
        assertEquals(1500,IdentityPlayback.DURATION_MILLIS)
        assertEquals(IdentityFrameBlend(0,1,0f),IdentityPlayback.sample(0f))
        assertEquals(IdentityFrameBlend(29,30,.5f),IdentityPlayback.sample(29.5f))
        assertEquals(IdentityFrameBlend(59,60,.5f),IdentityPlayback.sample(59.5f))
        assertEquals(IdentityFrameBlend(89,89,0f),IdentityPlayback.sample(90f))
        for(frame in 0..180) {
            val sample=IdentityPlayback.sample(IdentityPlayback.FRAME_COUNT*frame/180f)
            assertTrue(sample.first in 0..89&&sample.second in sample.first..89)
            assertTrue(sample.mix in 0f..1f)
        }
        assertEquals(.5f,IdentityPlayback.sample(.5f).mix,0f)
    }
}
