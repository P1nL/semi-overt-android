package com.semiovert.app

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
class AuthorReaderNavigationTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()
    private fun navigate(label: String) {
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
        app.onNodeWithContentDescription("切换到"+label).performClick()
    }
    private fun openAuthor() {
        navigate("搜索")
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("author-zhou").performClick()
    }
    private fun read() {
        app.onNodeWithTag("author-profile-feed").performScrollToNode(hasTestTag("author-articles"))
        app.onNodeWithTag("article-street").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
    }
    private fun visitAuthor() {
        app.onNodeWithText("街角观察员").performClick()
        app.onNodeWithTag("author-screen").assertIsDisplayed()
    }

    @Test fun returningToTheExistingAuthorReusesTheFourHundredMillisecondReaderExit() {
        openAuthor();read()
        val resting=app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithText("街角观察员").performClick()
            app.mainClock.advanceTimeBy(200)
            val moving=app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot()
            assertEquals("Do not replace the reading exit with a horizontal route slide",resting.left.value,moving.left.value,.1f)
            assertTrue("The reader remains mounted and travels downward",moving.top.value>resting.top.value+(resting.bottom.value-resting.top.value)*.15f)
            app.onNodeWithTag("author-screen").assertExists()
            app.mainClock.advanceTimeBy(300)
            assertTrue("The entire reader is below the screen after the 400ms exit",
                app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot().top.value>=resting.bottom.value-.1f)
        } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("author-screen").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("search-input").assertIsDisplayed()
    }

    @Test fun repeatedReaderAuthorVisitsReturnToOneExistingAuthorPage() {
        openAuthor()
        repeat(2) {read();visitAuthor()}
        Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("search-input").assertIsDisplayed()
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("author-screen").assertDoesNotExist()
    }

    @Test fun mainCircleNeverRestoresTheAuthorReaderTailAsAPrimaryPage() {
        openAuthor();read();visitAuthor()
        navigate("分类")
        app.onNodeWithTag("feed-category").assertIsDisplayed()
        navigate("搜索")
        app.onNodeWithTag("search-input").assertIsDisplayed()
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("author-screen").assertDoesNotExist()
        app.onNodeWithTag("author-zhou").performClick();read()
        navigate("首页")
        app.onNodeWithTag("home-theme-toggle").assertIsDisplayed()
        navigate("搜索")
        app.onNodeWithTag("search-input").assertIsDisplayed()
    }
}
