package com.semiovert.app.core.designsystem.cuboid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlin.math.hypot

/** Spatial filtering avoids a debounce delay and does not amplify slow pointer noise. */
internal class CuboidDragFilter(start: CubePoint,private val touchSlop: Float,private val jitterRadius: Float) {
    private var last=start
    var dragging=false; private set

    fun moveTo(point: CubePoint): CubePoint {
        val dx=point.x-last.x;val dy=point.y-last.y
        val distance=hypot(dx,dy)
        if(distance<=if(dragging)jitterRadius else touchSlop)return CubePoint(0f,0f)
        // Consume the initial touch slop rather than jumping by its full distance.
        val gain=if(dragging)1f else (distance-touchSlop)/distance
        last=point;dragging=true
        return CubePoint(dx*gain,dy*gain)
    }
}

internal object CuboidSettleMotion {
    const val ANGULAR_THRESHOLD=.0015f

    // A low-stiffness critically damped spring: soft acceleration, a weighted
    // deceleration and no overshoot. Tiny corrections finish sooner than full turns.
    fun spec(angle: Float)=spring<Float>(
        dampingRatio=Spring.DampingRatioNoBouncy,
        stiffness=120f,
        visibilityThreshold=(ANGULAR_THRESHOLD/angle.coerceAtLeast(ANGULAR_THRESHOLD)).coerceIn(.002f,.03f),
    )
}
