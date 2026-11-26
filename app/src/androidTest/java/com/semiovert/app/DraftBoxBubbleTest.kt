package com.semiovert.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.wave.WaveDeckOverlayHost
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.editor.EditorDraft
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.home.HomeViewModel
import com.semiovert.app.feature.reader.ArticleExpansionHost
import com.semiovert.app.navigation.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftBoxBubbleTest {
    @get:Rule val app = createComposeRule()
    private val sample = EditorDraft("窗边的一点光", "午后的光慢慢落在纸上，还有几句想写下的话。", 28, "2026-11-16T08:32:00Z", "sample")

    private fun fixture(
        pinned: Boolean = false,
        motion: Boolean = true,
        dark: Boolean = false,
        drafts: List<EditorDraft> = listOf(sample),
        onOpen: () -> Unit = {},
        onDelete: (EditorDraft) -> Unit = {},
    ) {
        app.setContent {
            SoTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                val box = remember { DraftBoxState() }
                val items = remember { mutableStateOf(drafts) }
                CompositionLocalProvider(
                    LocalDraftBox provides box,
                    LocalMotionEnabled provides motion,
                    LocalShortcuts provides ShortcutActions(
                        if (pinned) listOf(NavigationShortcut(HomeDraftAction.BOX.route, .23f, .42f)) else emptyList(), {}, {}),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        NavigationShortcutHost(Destination.HOME, {}, !box.active) {
                            WaveDeckOverlayHost {
                                ArticleExpansionHost {
                                    HomeScreen({}, {}, model = remember { HomeViewModel() })
                                }
                            }
                        }
                        DraftBoxBubble(box, items.value, false, false, {}, { onOpen() },
                            onDeleteDraft = { item -> items.value = items.value - item; onDelete(item) })
                    }
                }
            }
        }
        app.waitForIdle()
    }

    private fun bounds(tag: String) = app.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun center(tag: String): Offset {
        val rect = bounds(tag)
        return Offset((rect.left.value + rect.right.value) / 2f,
            (rect.top.value + rect.bottom.value) / 2f) * app.density.density
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder = File(context.getExternalFilesDir(null), "draft-box-captures").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            app.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun dockedButtonBecomesTheBubbleAndReturnsToItsOriginalPosition() {
        fixture()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val source = center("draft-box")
        app.mainClock.autoAdvance = false
        try {
            app.onRoot().performTouchInput { click(source) }
            app.mainClock.advanceTimeByFrame()
            val start = bounds("draft-box-bubble")
            assertEquals(52f, (start.right - start.left).value, .5f)
            assertEquals(source.x, center("draft-box-bubble").x, 1.5f)
            assertEquals(source.y, center("draft-box-bubble").y, 1.5f)
            app.mainClock.advanceTimeBy(96)
            val middle = bounds("draft-box-bubble")
            assertTrue((middle.right - middle.left).value > 52f)
            assertTrue((middle.right - middle.left).value < 392f)
            capture("light-opening")
            app.mainClock.advanceTimeBy(600)
            app.onNodeWithTag("draft-box-item-0").assertIsDisplayed()
            app.onNodeWithText(sample.title).assertIsDisplayed()
            capture("light-list")
            app.onNodeWithTag("draft-box-close").performClick()
            app.mainClock.advanceTimeBy(500)
            app.onNodeWithTag("draft-box-overlay").assertDoesNotExist()
            assertEquals(source.x, center("draft-box").x, 1.5f)
            assertEquals(source.y, center("draft-box").y, 1.5f)
        } finally { app.mainClock.autoAdvance = true }
    }

    @Test fun pinnedButtonUsesItsOwnOriginAndOutsideTapClosesIt() {
        fixture(pinned = true, dark = true)
        val source = center("shortcut-draft-box")
        app.mainClock.autoAdvance = false
        try {
            app.onRoot().performTouchInput { click(source) }
            app.mainClock.advanceTimeByFrame()
            assertEquals(source.x, center("draft-box-bubble").x, 1.5f)
            assertEquals(source.y, center("draft-box-bubble").y, 1.5f)
            app.mainClock.advanceTimeBy(600)
            capture("dark-pinned-list")
            app.onNodeWithTag("draft-box-dismiss").performTouchInput { click(Offset(12f, 12f)) }
            app.mainClock.advanceTimeBy(500)
            app.onNodeWithTag("draft-box-overlay").assertDoesNotExist()
            app.onNodeWithTag("shortcut-draft-box").assertIsDisplayed()
        } finally { app.mainClock.autoAdvance = true }
    }

    @Test fun draftOpensOnlyAfterTheBubbleHasReturned() {
        var opened = 0
        fixture(onOpen = { opened++ })
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("draft-box").performClick()
        app.waitForIdle()
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithTag("draft-box-item-0").performClick()
            app.mainClock.advanceTimeBy(100)
            app.runOnIdle { assertEquals(0, opened) }
            app.onNodeWithTag("draft-box-overlay").assertExists()
            app.mainClock.advanceTimeBy(500)
            app.onNodeWithTag("draft-box-overlay").assertDoesNotExist()
            app.runOnIdle { assertEquals(1, opened) }
        } finally { app.mainClock.autoAdvance = true }
    }

    @Test fun emptyDraftBoxWorksWithMotionDisabled() {
        var opened = false
        fixture(motion = false, drafts = emptyList(), onOpen = { opened = true })
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("draft-box").performClick()
        app.onNodeWithText("暂无草稿").assertIsDisplayed()
        app.onNodeWithText("自动保存于本机").assertDoesNotExist()
        app.onNodeWithText("写下的内容会自动保存在这里").assertDoesNotExist()
        app.onNodeWithText("创建草稿").assertDoesNotExist()
        capture("empty")
        app.onNodeWithTag("draft-box-create").assertContentDescriptionEquals("创建草稿").performClick()
        app.waitForIdle()
        app.onNodeWithTag("draft-box-overlay").assertDoesNotExist()
        app.runOnIdle { assertTrue(opened) }
    }

    @Test fun systemBackReturnsToTheDraftButtons() {
        fixture(motion = false)
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("draft-box").performClick()
        app.waitForIdle()
        app.onNodeWithText(sample.title).assertIsDisplayed()
        pressBack()
        app.waitForIdle()
        app.onNodeWithTag("draft-box-overlay").assertDoesNotExist()
        app.onNodeWithTag("draft-box").assertIsDisplayed()
    }

    @Test fun manyDraftsScrollVerticallyWithoutOpeningAndKeepTheCompactHeader() {
        var opened = 0
        fixture(drafts = (0 until 12).map { sample.copy(title = "示例草稿${it + 1}", key = "sample-$it") },
            onOpen = { opened++ })
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("draft-box").performClick()
        app.waitForIdle()
        app.onNodeWithText("12篇草稿").assertIsDisplayed()
        capture("many-drafts-top")
        app.onNodeWithTag("draft-box-list").performTouchInput { swipeUp() }
        app.waitForIdle()
        app.runOnIdle { assertEquals(0, opened) }
        app.onNodeWithTag("draft-box-list").performScrollToIndex(11)
        app.onNodeWithText("示例草稿12").assertIsDisplayed()
        app.onNodeWithText("12篇草稿").assertIsDisplayed()
        capture("many-drafts-bottom")
    }

    @Test fun leftSwipeRevealsDeleteAndOnlyTheTrashButtonRemovesTheDraft() {
        var opened = 0
        val deleted = mutableListOf<EditorDraft>()
        fixture(drafts = listOf(sample, sample.copy(title = "下一篇草稿", key = "second")),
            onOpen = { opened++ }, onDelete = { deleted.add(it) })
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("draft-box").performClick()
        app.waitForIdle()
        app.onNodeWithTag("draft-box-item-0").performTouchInput { swipeLeft() }
        app.waitForIdle()
        app.onNodeWithTag("draft-box-delete-0").assertIsDisplayed()
        app.onNodeWithText("2篇草稿").assertIsDisplayed()
        app.runOnIdle { assertEquals(0, opened); assertTrue(deleted.isEmpty()) }
        capture("swipe-delete")
        app.onNodeWithTag("draft-box-delete-0").performClick()
        app.waitForIdle()
        app.onNodeWithText(sample.title).assertDoesNotExist()
        app.onNodeWithText("1篇草稿").assertIsDisplayed()
        app.onNodeWithText("下一篇草稿").assertIsDisplayed()
        app.runOnIdle { assertEquals(listOf(sample), deleted); assertEquals(0, opened) }
    }
}
