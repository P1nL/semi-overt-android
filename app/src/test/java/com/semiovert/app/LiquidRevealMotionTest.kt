package com.semiovert.app

import com.semiovert.app.feature.editor.LiquidRevealMotion
import org.junit.Assert.*
import org.junit.Test

class LiquidRevealMotionTest {
    private val delays = floatArrayOf(0f, 80f, 220f, 130f, 300f, 40f, 270f, 180f, 100f, 250f)

    @Test fun startsWithBothWavesBelowTheScreenRatherThanACenterDot() {
        for (layer in 0 until LiquidRevealMotion.LAYER_COUNT) {
            for (point in delays.indices) {
                assertEquals(2400f, LiquidRevealMotion.pointY(point, layer, 0f, 1080f, 2400f, delays), 0f)
            }
        }
    }

    @Test fun playbackIs950msWhilePreservingTheReferenceWaveTimeline() {
        assertEquals(10, LiquidRevealMotion.POINT_COUNT)
        assertEquals(2, LiquidRevealMotion.LAYER_COUNT)
        assertEquals(900, LiquidRevealMotion.POINT_DURATION_MS)
        assertEquals(300, LiquidRevealMotion.MAX_POINT_DELAY_MS)
        assertEquals(250, LiquidRevealMotion.LAYER_DELAY_MS)
        assertEquals(950, LiquidRevealMotion.DURATION_MS)
        val progress = 450f / 1450f
        assertEquals(1200f, LiquidRevealMotion.pointY(0, 0, progress, 12000f, 2400f, delays), .001f)
        assertEquals(2400f, LiquidRevealMotion.pointY(4, 1, 550f / 1450f, 12000f, 2400f, delays), .001f)
        assertEquals(0f, LiquidRevealMotion.pointY(4, 1, 1f, 1080f, 2400f, delays), 0f)
    }

    @Test fun delayedPointsProduceAnUnevenWaveFront() {
        val ys = delays.indices.map { LiquidRevealMotion.pointY(it, 0, .5f, 1080f, 2400f, delays) }
        assertTrue("The edge must have pronounced peaks and valleys", ys.max() - ys.min() > 1080f * .15f)
        assertTrue("The wave must not stretch with the screen height",
            ys.max() - ys.min() <= 1080f * LiquidRevealMotion.MAX_WAVE_HEIGHT_WIDTH_RATIO)
        assertTrue("An earlier point must rise before a delayed point", ys[0] < ys[4])
    }

    @Test fun secondWaveFollowsWithAShortViewportScaledDelay() {
        val layerDelay = 250f * (1080f * LiquidRevealMotion.LAYER_GAP_WIDTH_RATIO / 2400f)
        for (point in delays.indices) for (step in 0..80) {
            val progress = step / 100f
            val leading = LiquidRevealMotion.pointY(point, 0, progress, 1080f, 2400f, delays)
            val following = LiquidRevealMotion.pointY(point, 1, progress + layerDelay / 1450f, 1080f, 2400f, delays)
            assertEquals(leading, following, .002f)
        }
    }

    @Test fun cubicSegmentsHaveMatchingHorizontalTangents() {
        for (index in 0 until LiquidRevealMotion.POINT_COUNT - 1) {
            val segment = LiquidRevealMotion.segment(index, 0, .5f, 1080f, 2400f, delays)
            assertEquals(segment.start.y, segment.first.y, 0f)
            assertEquals(segment.end.y, segment.second.y, 0f)
            assertEquals((segment.start.x + segment.end.x) / 2f, segment.first.x, .001f)
            assertEquals(segment.first.x, segment.second.x, 0f)
            if (index < LiquidRevealMotion.POINT_COUNT - 2) {
                val next = LiquidRevealMotion.segment(index + 1, 0, .5f, 1080f, 2400f, delays)
                assertEquals(segment.end, next.start)
                assertTrue(((segment.end - segment.second) - (next.first - next.start)).getDistance() < .001f)
            }
        }
    }

    @Test fun wavesMoveOnlyUpwardAndCoverAllScreenSizesBeforeTheMaskIsRemoved() {
        for ((width, height) in listOf(1080f to 2400f, 2400f to 1080f, 1600f to 2560f, 2400f to 600f)) {
            for (layer in 0 until LiquidRevealMotion.LAYER_COUNT) for (point in delays.indices) {
                var previous = height
                for (step in 0..100) {
                    val y = LiquidRevealMotion.pointY(point, layer, step / 100f, width, height, delays)
                    assertTrue(y.isFinite() && y in 0f..height && y <= previous)
                    previous = y
                }
                assertTrue(LiquidRevealMotion.pointY(point, layer, .999f, width, height, delays) < .01f)
                assertEquals(0f, previous, 0f)
            }
        }
    }

    @Test fun waveHeightAndLayerGapStayBoundedOnTallAndWideScreens() {
        for ((width, height) in listOf(1080f to 2400f, 1080f to 3200f, 2400f to 1080f, 1600f to 2560f)) {
            for (step in 0..100) {
                val progress = step / 100f
                val allPoints = (0 until LiquidRevealMotion.LAYER_COUNT).flatMap { layer ->
                    val points = delays.indices.map {
                        LiquidRevealMotion.pointY(it, layer, progress, width, height, delays)
                    }
                    assertTrue(points.max() - points.min() <= width * LiquidRevealMotion.MAX_WAVE_HEIGHT_WIDTH_RATIO + .001f)
                    points
                }
                assertTrue("The two layers must not form a full-height ribbon",
                    allPoints.max() - allPoints.min() <= width * .69f + .001f)
            }
        }
    }

    @Test fun randomDelaysAreCreatedOncePerEntranceWithinTheReferenceRange() {
        val generated = LiquidRevealMotion.newPointDelays()
        assertEquals(LiquidRevealMotion.LAYER_COUNT, generated.size)
        for (layer in generated) {
            assertEquals(LiquidRevealMotion.POINT_COUNT, layer.size)
            assertTrue(layer.all { it >= 0f && it < LiquidRevealMotion.MAX_POINT_DELAY_MS })
        }
        assertNotSame(generated, LiquidRevealMotion.newPointDelays())
    }
}
