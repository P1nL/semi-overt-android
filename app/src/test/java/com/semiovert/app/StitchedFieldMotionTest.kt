package com.semiovert.app

import com.semiovert.app.core.designsystem.StitchedFieldMotion
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class StitchedFieldMotionTest {
    @Test fun pressMatchesTheSuppliedPower2OutTimeline() {
        assertEquals(.992f,StitchedFieldMotion.PRESSED_SCALE,0f)
        assertEquals(80,StitchedFieldMotion.PRESS_MILLIS)
        assertEquals(600,StitchedFieldMotion.RELEASE_MILLIS)
        assertEquals(.875f,StitchedFieldMotion.pressEase.transform(.5f),.000001f)
    }

    @Test fun elasticReleaseHasExactEndpoints() {
        assertEquals(0f,StitchedFieldMotion.releaseEase.transform(0f),0f)
        assertEquals(1f,StitchedFieldMotion.releaseEase.transform(1f),0f)
    }

    @Test fun elasticReleaseMatchesAmplitudeOneAndPeriodPoint42() {
        val ease=StitchedFieldMotion.releaseEase
        assertEquals(1f,ease.transform(.105f),.000001f)
        assertEquals((1.0+2.0.pow(-2.1)).toFloat(),ease.transform(.21f),.000001f)
        assertEquals((1.0-2.0.pow(-4.2)).toFloat(),ease.transform(.42f),.000001f)
        assertEquals((1.0+2.0.pow(-6.3)).toFloat(),ease.transform(.63f),.000001f)
    }
}
