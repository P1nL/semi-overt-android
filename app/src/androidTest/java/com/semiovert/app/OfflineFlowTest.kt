package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private fun navigate(label: String) {
        app.onNodeWithTag("main-circle").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick){it()}
        app.onNodeWithContentDescription("切换到"+label).performClick()
        app.waitForIdle()
    }
    private fun back() {
        androidx.test.espresso.Espresso.pressBack()
        app.waitForIdle()
    }

    @Test fun homeReaderUsesSharedExpansionInsteadOfASlidingPage() {
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        app.onNodeWithTag("article-forest").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("article-forest").performClick()
            app.mainClock.advanceTimeBy(160)
            app.waitForIdle()
            app.onNodeWithTag("reading-expansion").assertIsDisplayed()
            app.onNodeWithTag("reader-screen").assertDoesNotExist()
            app.mainClock.advanceTimeBy(1000)
            app.waitForIdle()
            app.onNodeWithTag("reader-screen").assertIsDisplayed()
            app.onNodeWithTag("reader-bottom-title").assertDoesNotExist()
            app.onNodeWithContentDescription("打开目录").assertIsDisplayed()
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun holdingMainCircleDoesNotWashOutTheArticle() {
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        val density=app.activity.resources.displayMetrics.density
        val before=app.onRoot().captureToImage().toPixelMap()
        val x=before.width/2
        val article=app.onNodeWithTag("article-forest").getUnclippedBoundsInRoot()
        val y=((article.top.value+article.bottom.value)/2f*density).roundToInt()
        app.onNodeWithTag("main-circle").performTouchInput {down(center);advanceEventTime(800)}
        try {
            val during=app.onRoot().captureToImage().toPixelMap()
            assertEquals("Long press must leave the page pixels unchanged",before[x,y].toArgb(),during[x,y].toArgb())
        } finally {app.onNodeWithTag("main-circle").performTouchInput {up()}}
    }

    @Test fun darkPageAndNavigationSheetUseRequestedBackground() {
        navigate("我的")
        app.onNodeWithText("外观").performClick()
        app.onNodeWithText("外观").performClick()
        app.onNodeWithText("深色").assertIsDisplayed()
        val page=app.onRoot().captureToImage().toPixelMap()
        assertEquals(Color(0xFF191A1C).toArgb(),page[2,2].toArgb())
        app.onNodeWithContentDescription("打开页面导航").performClick()
        val sheet=app.onNodeWithText("去哪里看看").captureToImage().toPixelMap()
        assertEquals(Color(0xFF191A1C).toArgb(),sheet[0,0].toArgb())
    }

    @Test fun readerClosesContentsBeforeReturningToSelectedHomeArticle() {
        app.onNodeWithTag("feed-home").performScrollToIndex(3)
        app.onNodeWithTag("article-window").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        app.onNodeWithContentDescription("打开目录").performClick()
        app.onNodeWithTag("reader-contents").assertIsDisplayed()
        back()
        app.onNodeWithTag("reader-contents").assertDoesNotExist()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        back()
        app.onNodeWithTag("article-window").assertIsDisplayed()
    }

    @Test fun categoryKeepsDurationWhenReturningFromReading() {
        navigate("分类")
        app.onNodeWithText("<3 min").assertIsDisplayed()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithText("3~8 min").assertIsDisplayed()
        app.onNodeWithTag("article-street").performClick()
        back()
        app.onNodeWithText("3~8 min").assertIsDisplayed()
        app.onNodeWithTag("article-street").assertIsDisplayed()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithText(">8 min").assertIsDisplayed()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithText("<3 min").assertIsDisplayed()
    }

    @Test fun localSearchSupportsAuthorsAndBothEmptyStates() {
        navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("林间")
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("author-results").assertIsDisplayed()
        app.onNodeWithText("林间来信").assertIsDisplayed()
        app.onNodeWithTag("search-input").performTextReplacement("不会命中的词")
        app.onNodeWithText("没有找到相关作者").assertIsDisplayed()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithText("没有找到相关文章").assertIsDisplayed()
    }

    @Test fun longPressCancelsWithoutHitAndDragsToAnotherPage() {
        app.onNodeWithTag("main-circle").performTouchInput {
            down(center);advanceEventTime(800);up()
        }
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {
            down(center);advanceEventTime(800)
            moveTo(center+Offset(-116f*density,-8f*density),delayMillis=200)
            up()
        }
        app.onNodeWithText("<3 min").assertIsDisplayed()
    }

    @Test fun searchAndReaderSurviveActivityRecreation() {
        navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("林间")
        app.onNodeWithTag("feed-search").performScrollToIndex(2)
        app.onNodeWithTag("article-slow").performClick()
        app.activityRule.scenario.recreate()
        app.waitForIdle()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        back()
        app.onNodeWithTag("search-input").assertTextContains("林间")
        app.onNodeWithTag("article-slow").assertIsDisplayed()
    }
}
