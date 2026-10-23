package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SceneMotion
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedCardMotionTest {
    @get:Rule(order=0)val preferences=PreviewPreferencesRule()
    @get:Rule(order=1)val app=createAndroidComposeRule<MainActivity>()
    private fun menu(){app.waitForIdle();app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()};app.waitForIdle()}
    private fun navigate(label: String){menu();app.onNodeWithContentDescription("切换到"+label).performClick();app.waitForIdle()}
    private fun lift(id: String,distanceDp: Float) {
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("article-handle-"+id,useUnmergedTree=true).performTouchInput {
            val start=Offset(center.x,12f*density)
            down(start);moveTo(start+Offset(0f,-distanceDp*density),600);advanceEventTime(180);up()
        }
        app.waitForIdle()
    }
    @Test fun searchStackLiftsToReadingAndRestoresQueryWhileAuthorsStayRows() {
        navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("林间");app.onNodeWithTag("search-input").performImeAction();Espresso.closeSoftKeyboard();app.waitForIdle()
        app.onNodeWithTag("search-wave").assertIsDisplayed()
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
        Espresso.pressBack();app.waitForIdle()
        app.waitUntil(3000){app.onAllNodesWithTag("search-input").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithTag("search-input").assertTextContains("林间")
        app.onNodeWithTag("article-handle-forest",useUnmergedTree=true).assertIsDisplayed()
        app.onNodeWithText("作者",useUnmergedTree=true).performClick();app.waitForIdle()
        app.onNodeWithTag("author-lin").assertIsDisplayed()
        app.onAllNodesWithTag("article-handle-forest",useUnmergedTree=true).assertCountEquals(0)
    }
    @Test fun categoryShortLiftReturnsWholeStackAndFullLiftOpensSameArticle() {
        navigate("分类")
        val before=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        lift("forest",24f)
        app.onAllNodesWithTag("reader-screen").assertCountEquals(0)
        val after=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top,after.top,1f);assertEquals(before.bottom,after.bottom,1f)
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("article-forest").assertIsDisplayed()
    }
    @Test fun mainCircleStaysOpaqueDuringReadingEnterAndReturn() {
        navigate("分类")
        val bounds=app.onNodeWithTag("main-circle").fetchSemanticsNode().boundsInRoot
        // Sample the circle's solid backing away from its icon, edge and shadow.
        val x=(bounds.left+bounds.width*.76f).toInt()
        val y=(bounds.top+bounds.height*.66f).toInt()
        fun pixel()=app.onRoot().captureToImage().toPixelMap()[x,y]
        val resting=pixel()
        app.mainClock.autoAdvance=false
        try {
            lift("forest",135f)
            app.mainClock.advanceTimeBy(100)
            assertEquals("The primary circle must not fade or move with the opening article",resting,pixel())
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        assertEquals(resting,pixel())
        app.mainClock.autoAdvance=false
        try {
            Espresso.pressBack();app.mainClock.advanceTimeBy(500)
            assertEquals("The primary circle must stay visible during the reverse card transition",resting,pixel())
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        assertEquals(resting,pixel())
    }
    @Test fun rearCardCanLiftDirectlyAndReturnsAtTheFront() {
        navigate("分类")
        lift("shade",135f)
        app.onNodeWithTag("reader-title").assertTextEquals("午后的树影，替我们留了一会儿白")
        app.mainClock.autoAdvance=false
        try {
            Espresso.pressBack()
            app.mainClock.advanceTimeBy(250)
            val progress=app.onNodeWithTag("reading-expansion").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue("Return should still be in the body exit stage, not already gone",progress>.92f)
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("article-shade").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"选中"))
        app.onNodeWithTag("feed-category").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"第 3 篇，共 3 篇，已停稳"))
    }
    @Test fun readerHasFourRadialDestinationsAndKeepsShortPressContents() {
        navigate("分类")
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        // These actions use the same choices as the radial circles, including the reader's origin.
        val destinations=app.onNodeWithTag("main-circle").fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .map {it.label}.filter {it.startsWith("前往")}
        assertEquals(listOf("前往首页","前往分类","前往搜索","前往我的"),destinations)
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("reader-contents").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle()
        menu()
        app.onNodeWithText("当前").assertDoesNotExist()
        val resting=app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("切换到分类").performClick()
            // The menu first takes its 400ms exit, then the reader starts its own 400ms slide.
            app.mainClock.advanceTimeBy(SceneMotion.DRAWER_MS+200L)
            val moving=app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot()
            assertEquals(resting.left.value,moving.left.value,.1f)
            assertTrue("Reader must travel down across the screen",moving.top.value>resting.top.value+(resting.bottom.value-resting.top.value)*.15f)
            app.onNodeWithTag("feed-category").assertExists()
            app.mainClock.advanceTimeBy(300)
            assertTrue("Reader must be completely below the screen after its 400ms exit",
                app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot().top.value>=resting.bottom.value-.1f)
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("feed-category").assertIsDisplayed()
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        // Cover the forward-navigation case as well as popping to the source category.
        lift("forest",135f)
        app.mainClock.autoAdvance=false
        try {
            val search=app.onNodeWithTag("main-circle").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first {it.label=="前往搜索"}
            app.runOnIdle {search.action()}
            app.mainClock.advanceTimeBy(200)
            val moving=app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot()
            assertEquals(resting.left.value,moving.left.value,.1f)
            assertTrue(moving.top.value>resting.top.value+(resting.bottom.value-resting.top.value)*.15f)
            app.mainClock.advanceTimeBy(300)
            assertTrue(app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot().top.value>=resting.bottom.value-.1f)
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("search-input").assertIsDisplayed()
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
    }
    @Test fun ownPublicArticlesShareLiftAndReturnWithoutReplayingProfileEntrance() {
        navigate("我的")
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick();app.waitForIdle()
        app.onNodeWithTag("profile-identity").performClick()
        app.onNodeWithText("林间",useUnmergedTree=true).performScrollTo().performClick()
        app.onNodeWithText("保存资料").performScrollTo().performClick();app.waitForIdle()
        app.onNodeWithTag("profile-shared-avatar",useUnmergedTree=true).assertIsDisplayed()
        app.onNodeWithTag("profile-pixel-cover",useUnmergedTree=true).assertIsDisplayed()
        app.onNodeWithText("我的",useUnmergedTree=true).assertDoesNotExist()
        app.onNodeWithTag("profile-articles").assertIsDisplayed()
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("profile-wave").assertIsDisplayed()
        app.onNodeWithTag("article-handle-forest",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun homeDoesNotDispatchTheNextPageUntilItsNineHundredMillisecondExit() {
        menu();app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("切换到分类").performClick()
            app.mainClock.advanceTimeBy(800)
            app.onNodeWithTag("feed-home").assertExists();app.onNodeWithTag("feed-category").assertDoesNotExist()
            app.mainClock.advanceTimeBy(160)
            app.onNodeWithTag("feed-category").assertExists()
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
    }
}
