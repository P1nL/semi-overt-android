package com.semiovert.app.feature.editor

import androidx.compose.ui.geometry.Offset
import kotlin.random.Random

internal object LiquidRevealMotion {
    const val POINT_COUNT = 10
    const val LAYER_COUNT = 2
    const val POINT_DURATION_MS = 900
    const val MAX_POINT_DELAY_MS = 300
    const val LAYER_DELAY_MS = 250
    const val DURATION_MS = 950
    // Keep the approved wave geometry independent of playback speed.
    private const val WAVE_DURATION_MS = POINT_DURATION_MS + MAX_POINT_DELAY_MS + (LAYER_COUNT - 1) * LAYER_DELAY_MS
    const val MAX_WAVE_HEIGHT_WIDTH_RATIO = .50f
    const val LAYER_GAP_WIDTH_RATIO = .22f

    data class WaveSegment(val start: Offset, val first: Offset, val second: Offset, val end: Offset)

    fun newPointDelays(): Array<FloatArray> =
        Array(LAYER_COUNT) { FloatArray(POINT_COUNT) { Random.nextFloat() * MAX_POINT_DELAY_MS } }

    private fun ease(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        // GSAP power2.inOut: cubic acceleration and deceleration without overshoot.
        return if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    fun pointY(index: Int, layer: Int, progress: Float, width: Float, height: Float, delays: FloatArray): Float {
        // Bound the wave by its horizontal span, so portrait screens cannot stretch it into strands.
        val pointDelayScale = minOf(1f, width * MAX_WAVE_HEIGHT_WIDTH_RATIO / height)
        val layerDelayScale = minOf(1f, width * LAYER_GAP_WIDTH_RATIO / height)
        val pointDuration = WAVE_DURATION_MS - MAX_POINT_DELAY_MS * pointDelayScale -
            (LAYER_COUNT - 1) * LAYER_DELAY_MS * layerDelayScale
        val elapsed = progress.coerceIn(0f, 1f) * WAVE_DURATION_MS -
            layer * LAYER_DELAY_MS * layerDelayScale - delays[index] * pointDelayScale
        return height * (1f - ease(elapsed / pointDuration))
    }

    fun segment(
        index: Int, layer: Int, progress: Float, width: Float, height: Float, delays: FloatArray,
    ): WaveSegment {
        val startX = width * index / (POINT_COUNT - 1)
        val endX = width * (index + 1) / (POINT_COUNT - 1)
        val startY = pointY(index, layer, progress, width, height, delays)
        val endY = pointY(index + 1, layer, progress, width, height, delays)
        val controlX = (startX + endX) / 2f
        // Dynamic Morphing's midpoint controls keep the tangent horizontal at every point.
        return WaveSegment(
            Offset(startX, startY), Offset(controlX, startY),
            Offset(controlX, endY), Offset(endX, endY),
        )
    }
}
