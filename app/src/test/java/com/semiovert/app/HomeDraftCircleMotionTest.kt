package com.semiovert.app

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.navigation.HomeDraftAction
import com.semiovert.app.navigation.HomeDraftCircleMotion
import com.semiovert.app.navigation.ShortcutViewport
import com.semiovert.app.navigation.menuOffset
import com.semiovert.app.navigation.RadialNavigationGesture
import com.semiovert.app.navigation.homeDraftOrder
import org.junit.Assert.*
import org.junit.Test

class HomeDraftCircleMotionTest {
    @Test fun expansionIsFasterAndKeepsItsSmallSpringOvershoot() {
        val previous = TargetBasedAnimation(SceneMotion.navigationSpring(), Float.VectorConverter, 0f, 1f)
        val current = TargetBasedAnimation(HomeDraftCircleMotion.expansion(), Float.VectorConverter, 0f, 1f)
        assertTrue(current.durationNanos < previous.durationNanos * .8f)
        assertEquals(20L, HomeDraftCircleMotion.EXPAND_STAGGER_MS)
        val peak = (0..80).maxOf { current.getValueFromNanos(it * 10_000_000L) }
        assertTrue(peak > 1f)
        assertTrue(peak < 1.04f)
        assertEquals(1f, current.getValueFromNanos(current.durationNanos), .001f)
    }

    @Test fun bothDraftRoutesRoundTripWithTheExistingShortcutCoordinates() {
        val viewport = ShortcutViewport(411f, 780f, 24f)
        val position = Offset(140f, 360f)
        assertEquals(2, HomeDraftAction.entries.map { it.route }.toSet().size)
        HomeDraftAction.entries.forEach { action ->
            val shortcut = viewport.shortcut(action.route, position)
            assertEquals(action.route, shortcut.route)
            assertEquals(position.x, viewport.center(shortcut).x, .001f)
            assertEquals(position.y, viewport.center(shortcut).y, .001f)
        }
    }

    @Test fun circlesHaveASixDpGapAndTheLowerOneStaysOutsideTheMergeZone() {
        val create = HomeDraftAction.CREATE.menuOffset
        val box = HomeDraftAction.BOX.menuOffset
        assertEquals(6f, box.y - create.y - 52f, 0f)
        assertEquals(create.x, box.x, 0f)
        assertFalse(RadialNavigationGesture.canMerge(box))
    }

    @Test fun returningCircleTakesTheLowerSlotAndMovesTheExistingCircleUp() {
        for (returning in HomeDraftAction.entries) {
            val existing = HomeDraftAction.entries.single { it != returning }
            val previous = listOf(existing)
            assertEquals(-76f, HomeDraftCircleMotion.slotOffset(0, previous.size).y, 0f)
            val next = homeDraftOrder(previous, HomeDraftAction.entries)
            assertEquals(listOf(existing, returning), next)
            assertEquals(-134f, HomeDraftCircleMotion.slotOffset(next.indexOf(existing), next.size).y, 0f)
            assertEquals(-76f, HomeDraftCircleMotion.slotOffset(next.indexOf(returning), next.size).y, 0f)
        }
    }
}
