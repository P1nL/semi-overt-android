package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        app.onNodeWithTag("search-input").performTextInput("林间");Espresso.closeSoftKeyboard();app.waitForIdle()
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
    @Test fun ownPublicArticlesShareLiftAndReturnWithoutReplayingProfileEntrance() {
        navigate("我的")
        app.onNodeWithText("登录",useUnmergedTree=true).performClick()
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
