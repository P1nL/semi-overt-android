package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.spring

internal object ArcDeckMotion {
    const val MAX_RELEASE_SPEED=2.2f

    // Low stiffness supplies weight; high-but-not-critical damping adds one restrained recoil.
    fun spec()=spring<Float>(dampingRatio=.82f,stiffness=105f,visibilityThreshold=.0015f)
    // Match the independent neighbour springs already used by the category deck.
    fun followSpec()=spring<Float>(dampingRatio=1.14f,stiffness=120f,visibilityThreshold=.05f)

    fun releaseVelocity(screenVelocity: Float,scrollStepPx: Float)=
        (-screenVelocity/scrollStepPx).coerceIn(-MAX_RELEASE_SPEED,MAX_RELEASE_SPEED)
}
