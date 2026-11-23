package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import kotlin.math.abs

internal object ArcDeckMotion {
    const val MAX_RELEASE_SPEED=2.2f
    const val MIN_FLING_SPEED=1.2f
    const val MAX_FLING_SPEED=12f
    const val LIFT_MS=900

    // Decay carries a quick release across several cards before the final snap.
    fun flingSpec()=exponentialDecay<Float>(frictionMultiplier=1f,absVelocityThreshold=.5f)
    fun shouldFling(velocity: Float)=abs(velocity)>=MIN_FLING_SPEED
    fun flingVelocity(screenVelocity: Float,scrollStepPx: Float)=
        (-screenVelocity/scrollStepPx).coerceIn(-MAX_FLING_SPEED,MAX_FLING_SPEED)

    // Low stiffness supplies weight; high-but-not-critical damping adds one restrained recoil.
    fun spec()=spring<Float>(dampingRatio=.82f,stiffness=105f,visibilityThreshold=.0015f)
    // Shared 0..1 pull progress keeps both axes in step, without a final pixel-sized snap.
    fun followSpec()=spring<Float>(dampingRatio=1.14f,stiffness=120f,visibilityThreshold=.001f)
    private val liftReference=TargetBasedAnimation(followSpec(),Float.VectorConverter,0f,1f)
    private val liftEase=Easing {fraction ->
        liftReference.getValueFromNanos((fraction*liftReference.durationNanos).toLong())
    }
    // Selection and cancellation share the weighted spring path and a 900ms clock.
    fun liftSpec()=tween<Float>(LIFT_MS,easing=liftEase)
    // Distant neighbours respond more softly, carrying the tilt out through the ring.
    fun tiltSpec(distance: Float)=spring<Float>(dampingRatio=1f,stiffness=180f/(1f+.3f*abs(distance)),visibilityThreshold=.05f)

    fun releaseVelocity(screenVelocity: Float,scrollStepPx: Float)=
        (-screenVelocity/scrollStepPx).coerceIn(-MAX_RELEASE_SPEED,MAX_RELEASE_SPEED)
}
