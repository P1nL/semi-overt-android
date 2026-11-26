package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.navigation.*
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.home.HomeViewModel
import com.semiovert.app.feature.reader.ArticleExpansionHost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeDraftShortcutTest {
    @get:Rule val app = createComposeRule()

    private fun fixture(
        initial: List<NavigationShortcut> = emptyList(),
        realHome: Boolean = false,
        onDraftAction: (HomeDraftAction) -> Unit = {},
    ): androidx.compose.runtime.MutableState<List<NavigationShortcut>> {
        val shortcuts = mutableStateOf(initial)
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalHomeDraftAction provides onDraftAction,
                    LocalShortcuts provides ShortcutActions(shortcuts.value,
                    { saved -> shortcuts.value = shortcuts.value.filterNot { it.route == saved.route } + saved },
                    { route -> shortcuts.value = shortcuts.value.filterNot { it.route == route } })) {
                    NavigationShortcutHost(Destination.HOME, {}, true) {
                        WaveDeckOverlayHost {
                            if (realHome) ArticleExpansionHost {
                                HomeScreen({}, {}, model = remember { HomeViewModel() })
                            } else BrowseScene(Destination.HOME, "首页", {}, showHeader = false) {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        app.waitForIdle()
        return shortcuts
    }

    private fun center(tag: String): Offset {
        val bounds = app.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return Offset((bounds.left.value + bounds.right.value) / 2f,
            (bounds.top.value + bounds.bottom.value) / 2f) * app.density.density
    }

    @Test fun createDraftFollowsTheHeldPointerAndMergesBack() = detachAndReturn(HomeDraftAction.CREATE)
    @Test fun draftBoxFollowsTheHeldPointerAndMergesBack() = detachAndReturn(HomeDraftAction.BOX)
    @Test fun createDraftMouseHoldDragsAndMergesBack() = detachAndReturn(HomeDraftAction.CREATE, mouse = true)
    @Test fun draftBoxMouseHoldDragsAndMergesBack() = detachAndReturn(HomeDraftAction.BOX, mouse = true)
    @Test fun mouseDragWorksAboveActualHomeCards() = detachAndReturn(HomeDraftAction.CREATE, mouse = true, realHome = true)
    @Test fun holdingCreateDoesNotScaleDraftBox() = holdWithoutScalingSibling(HomeDraftAction.CREATE)
    @Test fun holdingDraftBoxDoesNotScaleCreate() = holdWithoutScalingSibling(HomeDraftAction.BOX)
    @Test fun mouseHoldWithoutDraggingDoesNotPinEitherCircle() = holdWithoutDragging(mouse = true)
    @Test fun touchHoldWithoutDraggingDoesNotPinEitherCircle() = holdWithoutDragging(mouse = false)
    @Test fun returnedCreateEmergesBelowTheExistingDraftBox() = reclaimWhileExpanded(HomeDraftAction.CREATE, mouse = true)
    @Test fun returnedDraftBoxEmergesBelowTheExistingCreate() = reclaimWhileExpanded(HomeDraftAction.BOX, mouse = false)
    @Test fun mousePullUsesResistanceBeforeDetaching() = pullWithoutDetaching(mouse = true)
    @Test fun touchPullUsesResistanceBeforeDetaching() = pullWithoutDetaching(mouse = false)

    @Test fun draftDropsPinchOffAndRejoinOnlyWhileMoving() {
        fixture()
        val joinedShadow = app.onNodeWithTag("menu-droplet-shadow")
        fun reachesJoinedFrame(): Boolean {
            repeat(12) {
                app.mainClock.advanceTimeByFrame()
                if (app.onAllNodesWithTag("menu-droplet-shadow").fetchSemanticsNodes().isNotEmpty()) return true
            }
            return false
        }
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithTag("main-circle").performClick()
            assertTrue("The drops should emerge from one liquid silhouette", reachesJoinedFrame())
            app.mainClock.advanceTimeBy(1_100)
            joinedShadow.assertDoesNotExist()
            app.onNodeWithTag("draft-box").assertIsDisplayed()
            app.onNodeWithTag("main-circle").performClick()
            app.mainClock.advanceTimeBy(370)
            assertTrue("The drops should reconnect just before being absorbed", reachesJoinedFrame())
            app.mainClock.advanceTimeBy(300)
            joinedShadow.assertDoesNotExist()
            app.onNodeWithTag("draft-box").assertDoesNotExist()
        } finally {
            app.mainClock.autoAdvance = true
        }
    }

    @Test fun touchCreateDispatchesOnlyAfterBothCirclesCollapse() {
        val actions = mutableListOf<HomeDraftAction>()
        fixture(onDraftAction = { actions.add(it) })
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val start = center("create-draft")
        app.mainClock.autoAdvance = false
        try {
            app.onRoot().performTouchInput { click(start) }
            app.mainClock.advanceTimeBy(100)
            app.runOnIdle { assertTrue(actions.isEmpty()) }
            app.onNodeWithTag("home-draft-dismiss").assertExists()
            app.mainClock.advanceTimeBy(2_000)
            app.onNodeWithTag("home-draft-dismiss").assertDoesNotExist()
            app.runOnIdle { assertEquals(listOf(HomeDraftAction.CREATE), actions) }
        } finally {
            app.mainClock.autoAdvance = true
        }
    }

    @Test fun reopeningTheMenuCancelsThePendingCreate() {
        val actions = mutableListOf<HomeDraftAction>()
        fixture(onDraftAction = { actions.add(it) })
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithTag("create-draft").performClick()
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("main-circle").performClick()
            app.mainClock.advanceTimeBy(2_000)
            app.onNodeWithTag("create-draft").assertExists()
            app.onNodeWithTag("main-circle").performClick()
            app.mainClock.advanceTimeBy(2_000)
            app.onNodeWithTag("home-draft-dismiss").assertDoesNotExist()
            app.runOnIdle { assertTrue(actions.isEmpty()) }
        } finally {
            app.mainClock.autoAdvance = true
        }
    }

    private fun pullWithoutDetaching(mouse: Boolean) {
        val shortcuts = fixture()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val root = app.onRoot()
        for (action in HomeDraftAction.entries) {
            val start = center(action.route)
            val target = start + Offset(0f, -60f) * app.density.density
            app.mainClock.autoAdvance = false
            try {
                if (mouse) root.performMouseInput { moveTo(start); press(); advanceEventTime(800) }
                else root.performTouchInput { down(start); advanceEventTime(800) }
                app.mainClock.advanceTimeBy(800)
                if (mouse) root.performMouseInput { moveTo(target, delayMillis = 16) }
                else root.performTouchInput { moveTo(target, delayMillis = 16) }
                app.mainClock.advanceTimeByFrame()
                val attached = center(action.route)
                assertTrue("The circle should yield to the pull", attached.y < start.y)
                assertTrue("The attached circle should resist the pointer", attached.y > target.y)
                app.onNodeWithTag("shortcut-" + action.route).assertDoesNotExist()
            } finally {
                if (mouse) root.performMouseInput { release() } else root.performTouchInput { up() }
                app.mainClock.autoAdvance = true
            }
            app.waitForIdle()
            assertEquals(start.y, center(action.route).y, 1.5f)
            app.runOnIdle { assertTrue(shortcuts.value.isEmpty()) }
        }
    }

    private fun reclaimWhileExpanded(returning: HomeDraftAction, mouse: Boolean) {
        fixture(listOf(NavigationShortcut(returning.route, .2f, .4f)))
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val existing = HomeDraftAction.entries.single { it != returning }
        val main = center("main-circle")
        val existingStart = center(existing.route)
        val density = app.density.density
        assertEquals(main.y - 76f * density, existingStart.y, 1.5f)
        val root = app.onRoot()
        val tag = "shortcut-" + returning.route
        val start = center(tag)
        app.mainClock.autoAdvance = false
        try {
            if (mouse) root.performMouseInput { moveTo(start); press(); advanceEventTime(800) }
            else root.performTouchInput { down(start); advanceEventTime(800) }
            app.mainClock.advanceTimeBy(800)
            val merge = main + Offset(0f, -42f) * density
            if (mouse) root.performMouseInput { moveTo(merge, delayMillis = 16); release() }
            else root.performTouchInput { moveTo(merge, delayMillis = 16); up() }
            app.mainClock.advanceTimeByFrame()
            app.onNodeWithTag(returning.route).assertDoesNotExist()
            var appeared = false
            for (frame in 0 until 160) {
                app.mainClock.advanceTimeByFrame()
                if (app.onAllNodesWithTag(returning.route).fetchSemanticsNodes().isNotEmpty()) {
                    appeared = true
                    break
                }
            }
            assertTrue("The reclaimed circle did not return", appeared)
            app.onNodeWithTag(tag).assertDoesNotExist()
            val emerging = center(returning.route)
            assertTrue("The circle should start at the main circle, not flash in its final slot",
                kotlin.math.abs(emerging.y - main.y) < 30f * density)
            app.mainClock.advanceTimeBy(80)
            assertTrue(center(returning.route).y < emerging.y)
            assertTrue(center(existing.route).y < existingStart.y)
        } finally {
            app.mainClock.autoAdvance = true
        }
        app.waitForIdle()
        assertEquals(main.y - 134f * density, center(existing.route).y, 1.5f)
        assertEquals(main.y - 76f * density, center(returning.route).y, 1.5f)
    }

    private fun holdWithoutDragging(mouse: Boolean) {
        val shortcuts = fixture()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val root = app.onRoot()
        for (action in HomeDraftAction.entries) {
            val start = center(action.route)
            app.mainClock.autoAdvance = false
            try {
                if (mouse) root.performMouseInput { moveTo(start); press(); advanceEventTime(800) }
                else root.performTouchInput { down(start); advanceEventTime(800) }
                app.mainClock.advanceTimeBy(800)
                val jitter = start + Offset(1f, 1f) * app.density.density
                if (mouse) root.performMouseInput { moveTo(jitter, delayMillis = 16) }
                else root.performTouchInput { moveTo(jitter, delayMillis = 16) }
                app.mainClock.advanceTimeByFrame()
                app.onNodeWithTag(action.route).assertIsDisplayed()
                app.onNodeWithTag("shortcut-" + action.route).assertDoesNotExist()
            } finally {
                if (mouse) root.performMouseInput { release() } else root.performTouchInput { up() }
                app.mainClock.autoAdvance = true
            }
            app.waitForIdle()
            app.onNodeWithTag(action.route).assertIsDisplayed()
            app.onNodeWithTag("shortcut-" + action.route).assertDoesNotExist()
            app.runOnIdle { assertTrue(shortcuts.value.isEmpty()) }
        }
    }

    private fun holdWithoutScalingSibling(action: HomeDraftAction) {
        fixture()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val sibling = HomeDraftAction.entries.single { it != action }
        fun iconSize(item: HomeDraftAction): DpSize {
            val bounds = app.onNodeWithTag("draft-icon-" + item.route, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            return DpSize((bounds.width / app.density.density).dp, (bounds.height / app.density.density).dp)
        }
        val resting = iconSize(sibling)
        assertEquals(NavigationCircleIconSize.value, resting.width.value, .05f)
        assertEquals(NavigationCircleIconSize.value, resting.height.value, .05f)
        val root = app.onRoot()
        val start = center(action.route)
        app.mainClock.autoAdvance = false
        try {
            root.performMouseInput { moveTo(start); press(); advanceEventTime(800) }
            fun assertSiblingSize(frame: Int) {
                val bounds = iconSize(sibling)
                assertEquals("Sibling width changed on frame $frame", resting.width.value, bounds.width.value, .05f)
                assertEquals("Sibling height changed on frame $frame", resting.height.value, bounds.height.value, .05f)
            }
            repeat(60) {
                app.mainClock.advanceTimeByFrame()
                assertSiblingSize(it)
            }
            app.onNodeWithTag(action.route).assertIsDisplayed()
            app.onNodeWithTag("shortcut-" + action.route).assertDoesNotExist()
            root.performMouseInput { moveTo(start + Offset(-120f, -180f) * app.density.density, delayMillis = 16) }
            // Check every frame across the docked-to-independent composition handoff.
            repeat(20) {
                app.mainClock.advanceTimeByFrame()
                assertSiblingSize(it + 60)
            }
            app.onNodeWithTag(action.route).assertDoesNotExist()
            val held = iconSize(action)
            assertEquals(resting.width.value * RadialNavigationGesture.SELECTED_SCALE, held.width.value, .05f)
            assertEquals(resting.height.value * RadialNavigationGesture.SELECTED_SCALE, held.height.value, .05f)
        } finally {
            root.performMouseInput { release() }
            app.mainClock.autoAdvance = true
        }
        app.waitForIdle()
        val pinned = iconSize(action)
        assertEquals(resting.width.value, pinned.width.value, .05f)
        assertEquals(resting.height.value, pinned.height.value, .05f)
    }

    private fun detachAndReturn(action: HomeDraftAction, mouse: Boolean = false, realHome: Boolean = false) {
        val shortcuts = fixture(realHome = realHome)
        val root = app.onRoot()
        val density = app.density.density
        val main = center("main-circle")
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val start = center(action.route)
        val tag = "shortcut-" + action.route
        var expected = start
        app.mainClock.autoAdvance = false
        try {
            if (mouse) root.performMouseInput { moveTo(start); press(); advanceEventTime(800) }
            else root.performTouchInput { down(start); advanceEventTime(800) }
            app.mainClock.advanceTimeBy(800)
            app.onNodeWithTag(action.route).assertIsDisplayed()
            app.onNodeWithTag(tag).assertDoesNotExist()
            for ((index, delta) in listOf(Offset(-120f, -200f), Offset(-150f, -180f), Offset(-90f, -210f)).withIndex()) {
                expected = main + delta * density
                if (mouse) root.performMouseInput { moveTo(expected, delayMillis = 16) }
                else root.performTouchInput { moveTo(expected, delayMillis = 16) }
                app.mainClock.advanceTimeByFrame()
                if (index == 0) {
                    val handingOff = center(tag)
                    assertTrue("Detachment should start at the docked circle instead of snapping to the pointer",
                        (handingOff - expected).getDistance() > 1.5f)
                    app.mainClock.advanceTimeBy(RadialNavigationGesture.DETACH_MILLIS.toLong() + 16L)
                }
                val actual = center(tag)
                assertEquals(expected.x, actual.x, 1.5f)
                assertEquals(expected.y, actual.y, 1.5f)
            }
        } finally {
            if (mouse) root.performMouseInput { release() } else root.performTouchInput { up() }
            app.mainClock.autoAdvance = true
        }
        app.waitForIdle()
        app.onAllNodesWithTag(tag).assertCountEquals(1)
        assertEquals(expected.x, center(tag).x, 1.5f)
        assertEquals(expected.y, center(tag).y, 1.5f)
        app.runOnIdle { assertEquals(listOf(action.route), shortcuts.value.map { it.route }) }

        val pinned = center(tag)
        if (mouse) root.performMouseInput { moveTo(pinned); press(); advanceEventTime(800) }
        else root.performTouchInput { down(pinned); advanceEventTime(800) }
        app.mainClock.advanceTimeBy(800)
        val merge = main + Offset(0f, -42f) * density
        if (mouse) root.performMouseInput { moveTo(merge, delayMillis = 16); release() }
        else root.performTouchInput { moveTo(merge, delayMillis = 16); up() }
        app.waitForIdle()
        app.onNodeWithTag(tag).assertDoesNotExist()
        app.runOnIdle { assertTrue(shortcuts.value.isEmpty()) }
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        app.onNodeWithTag(action.route).assertIsDisplayed()
    }

    @Test fun cancellationLeavesTheCircleDockedWithoutSaving() {
        val shortcuts = fixture()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val start = center(HomeDraftAction.CREATE.route)
        app.onRoot().performTouchInput { down(start); advanceEventTime(800) }
        app.mainClock.advanceTimeBy(800)
        app.onRoot().performTouchInput { moveTo(start + Offset(-100f, -100f) * app.density.density, delayMillis = 16) }
        app.mainClock.advanceTimeByFrame()
        app.onNodeWithTag("shortcut-create-draft").assertExists()
        app.onRoot().performTouchInput { cancel() }
        app.waitForIdle()
        app.onNodeWithTag("shortcut-create-draft").assertDoesNotExist()
        app.onNodeWithTag("create-draft").assertIsDisplayed()
        app.runOnIdle { assertTrue(shortcuts.value.isEmpty()) }
    }

    @Test fun storedDraftCirclesRestoreAndReturnToTheMenuWhenRemoved() {
        fixture(HomeDraftAction.entries.mapIndexed { index, action ->
            NavigationShortcut(action.route, .25f + index * .4f, .35f)
        })
        app.onNodeWithTag("shortcut-create-draft").assertIsDisplayed()
        app.onNodeWithTag("shortcut-draft-box").assertIsDisplayed()
        app.onNodeWithTag("main-circle").assertHasNoClickAction()
        val remove = app.onNodeWithTag("shortcut-create-draft").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == "收回快捷入口" }
        app.runOnIdle { assertTrue(remove.action()) }
        app.waitForIdle()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        app.onNodeWithTag("create-draft").assertIsDisplayed()
        app.onNodeWithTag("draft-box").assertDoesNotExist()
    }
}
