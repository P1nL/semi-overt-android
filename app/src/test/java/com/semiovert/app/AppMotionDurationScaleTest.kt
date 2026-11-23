package com.semiovert.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.semiovert.app.core.designsystem.AppMotionDurationScale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppMotionDurationScaleTest {
    @Test fun everyEnabledSystemMultiplierUsesAuthoredTiming() {
        for(systemScale in listOf(.5f,1f,1.5f,2f,5f,10f)) {
            val scale=AppMotionDurationScale(systemScale)
            assertEquals("System multiplier $systemScale",1f,scale.scaleFactor,0f)
            assertSame(scale,scale[MotionDurationScale])
        }
    }

    @Test fun disabledAnimationsStayDisabledAtStartup() {
        assertEquals(0f,AppMotionDurationScale(0f).scaleFactor,0f)
    }

    @Test fun changesToTheSystemSettingOnlyToggleMotion() {
        val scale=AppMotionDurationScale(2f)
        for(systemScale in listOf(0f,.5f,10f,0f,1f)) {
            scale.updateSystemScale(systemScale)
            assertEquals(if(systemScale>0f)1f else 0f,scale.scaleFactor,0f)
        }
    }

    @Test fun tweensKeepTheirDurationInsteadOfMultiplyingItByTheSystemSetting() {
        for(systemScale in listOf(.5f,1f,2f,10f)) {
            assertEquals(816L,animationMillis(systemScale,tween(800,easing=LinearEasing)))
        }
    }

    @Test fun springsKeepTheirTimingAndDisabledMotionFinishesOnTheFirstFrame() {
        val spec=spring<Float>()
        val baseline=animationMillis(1f,spec)
        for(systemScale in listOf(.5f,2f,10f))assertEquals(baseline,animationMillis(systemScale,spec))
        assertEquals(16L,animationMillis(0f,spec))
        assertEquals(16L,animationMillis(0f,tween(800)))
    }

    private fun animationMillis(systemScale: Float,spec: AnimationSpec<Float>): Long {
        var frameTime=0L
        val clock=object: MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long)->R): R {
                frameTime+=16_000_000L
                return onFrame(frameTime)
            }
        }
        runBlocking(clock+AppMotionDurationScale(systemScale)) {
            val animation=Animatable(0f)
            animation.animateTo(1f,spec)
            assertEquals(1f,animation.value,0f)
        }
        return frameTime/1_000_000L
    }
}
