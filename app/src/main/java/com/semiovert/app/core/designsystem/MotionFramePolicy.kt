package com.semiovert.app.core.designsystem

/** One display-rate request for Compose, sheets and the native texture surface.
 * This is not a synthetic clock: animation time still comes from system VSync. */
object MotionFramePolicy {
    const val TARGET_FPS=120f

    // Before API 34, preferredRefreshRate must name a supported rate at this resolution.
    fun legacyRefreshRate(supported: List<Float>): Float =
        supported.filter {it<=TARGET_FPS+.5f}.maxOrNull() ?: supported.minOrNull() ?: 0f
}
