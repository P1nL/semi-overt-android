package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal fun SemanticsNodeInteraction.assertHomeResting(index: Int,count: Int=SampleLibrary.articles.size) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"第 ${index+1} 篇，共 ${count} 篇，已停稳"))

@RunWith(AndroidJUnit4::class)
class HomeArcDeckTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private fun swipePage(up: Boolean) {
        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.96f,height*.46f)
            swipe(start,start+Offset(0f,height*(if(up)-.12f else .12f)),600)
        }
        app.waitForIdle()
    }
    @Test fun selectionIsExposedBetweenTheSequentialUpperAndLowerStacks() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        val root=app.onNodeWithTag("feed-home").fetchSemanticsNode().boundsInRoot
        val front=app.onNodeWithTag("article-forest").fetchSemanticsNode().boundsInRoot
        assertEquals("The new card is square",front.width,front.height,1f)
        val cover=app.onNodeWithTag("arc-cover-forest",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals("Cover fills the complete card",front,cover)
        assertTrue(front.left>=root.left&&front.right<=root.right)
        assertTrue(front.top>=root.top&&front.bottom<=root.bottom)
        val above=app.onNodeWithTag("arc-card-letter",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val below=app.onNodeWithTag("arc-card-street",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Upper stack must stay left: ${above} / ${front}",above.center.x<front.center.x)
        assertTrue("Lower stack must stay right: ${below} / ${front}",below.center.x>front.center.x)
        assertTrue("Upper stack leaves the whole selected card visible",above.bottom<=front.top+1f)
        assertTrue("Lower stack leaves the whole selected card visible",below.top>=front.bottom-1f)
    }
    @Test fun bothArcNeighboursHaveIndependentSpringsWithoutCoveringTheSelectedCard() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        val root=app.onNodeWithTag("feed-home").fetchSemanticsNode().boundsInRoot
        val density=app.activity.resources.displayMetrics.density
        val step=ArcDeckMath.geometry(root.width/density,root.height/density).scrollStep*density
        fun bounds(id: String)=app.onNodeWithTag("arc-card-"+id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("home-arc").performTouchInput {
                val start=Offset(width*.96f,height*.46f)
                down(start);moveTo(start+Offset(0f,-step*.25f),400)
            }
            app.mainClock.advanceTimeByFrame()
            val selected=bounds("forest");val upper=bounds("letter");val lower=bounds("street");val far=bounds("lamplight")
            app.mainClock.advanceTimeBy(240)
            val upperAfter=bounds("letter");val lowerAfter=bounds("street");val farAfter=bounds("lamplight")
            assertEquals("The selected card stays directly under gesture control",selected,bounds("forest"))
            assertTrue("Upper cards have their own outward spring",upperAfter.center.y<upper.center.y-1f)
            assertTrue("Lower cards have their own outward spring",lowerAfter.center.y>lower.center.y+1f)
            assertTrue("The neighbouring crest is stronger than the farther card",
                upper.center.y-upperAfter.center.y>(far.center.y-farAfter.center.y)*1.5f)
            assertTrue(upperAfter.bottom<=selected.top+1f)
            assertTrue(lowerAfter.top>=selected.bottom-1f)
        } finally {
            app.onNodeWithTag("home-arc").performTouchInput {advanceEventTime(250);up()}
            app.mainClock.autoAdvance=true;app.waitForIdle()
        }
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        app.onNodeWithTag("article-forest").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
    }
    @Test fun transparentHeaderRemainsTappableAboveOverflowingCards() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        app.onNodeWithContentDescription("打开页面导航").performTouchInput {click()}
        app.onNodeWithText("去哪里看看").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.onNodeWithContentDescription("打开搜索").performTouchInput {click()}
        app.waitForIdle()
        app.onNodeWithTag("search-input").assertIsDisplayed()
    }
    @Test fun upAndDownSelectCardsAndReadingAndRecreationKeepTheSelection() {
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        swipePage(up=true)
        app.onNodeWithTag("feed-home").assertHomeResting(1)
        app.onNodeWithTag("article-street").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("feed-home").assertHomeResting(1)
        app.activityRule.scenario.recreate();app.waitForIdle()
        app.onNodeWithTag("feed-home").assertHomeResting(1)
        swipePage(up=false)
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        swipePage(up=false)
        app.onNodeWithTag("feed-home").assertHomeResting(10)
        app.onNodeWithTag("article-letter").assertIsDisplayed()
    }
    @Test fun tinyAndHorizontalGesturesDoNotSelectAnotherCardOrOpenTheReader() {
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.98f,height*.46f)
            swipe(start,start+Offset(0f,-height*.005f),320)
        }
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.98f,height*.46f)
            swipe(start,start+Offset(-width*.18f,0f),320)
        }
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
    }
    @Test fun allElevenArticlesCanBeSelectedAndOpened() {
        assertEquals(11,SampleLibrary.articles.size)
        for((index,article) in SampleLibrary.articles.withIndex()) {
            app.onNodeWithTag("feed-home").performScrollToIndex(index)
            app.waitForIdle()
            app.onNodeWithTag("feed-home").assertHomeResting(index)
            app.onNodeWithTag("article-"+article.id).assertIsDisplayed()
            app.onNodeWithTag("arc-cover-"+article.id,useUnmergedTree=true).assertIsDisplayed()
        }
        app.onNodeWithTag("article-letter").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
    }
    @Test fun aNewDragInterruptsSettlingInsteadOfQueuingASecondMotion() {
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        app.mainClock.autoAdvance=false
        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.96f,height*.46f)
            swipe(start,start+Offset(0f,-height*.12f),600)
        }
        app.mainClock.advanceTimeBy(80)
        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.96f,height*.46f)
            swipe(start,start+Offset(0f,height*.12f),600)
        }
        app.mainClock.advanceTimeBy(1400)
        app.mainClock.autoAdvance=true;app.waitForIdle()
        app.onNodeWithTag("feed-home").assertHomeResting(0)
    }
}
