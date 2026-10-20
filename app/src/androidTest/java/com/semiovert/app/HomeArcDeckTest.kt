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
    @Test fun focusRisesWhileNeighboursKeepTheirFullHeightAndRingPosition() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        app.waitUntil(10_000) {
            !app.onNodeWithTag("article-forest").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        app.waitForIdle()
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
        assertTrue("Lower stack also returns left: ${below} / ${front}",below.center.x<front.center.x)
        assertTrue("Previous neighbour must not be flattened",above.height>=front.height*.95f)
        assertTrue("Following neighbour must not be flattened",below.height>=front.height*.95f)
        assertTrue("Following neighbour makes room: $below / $front",below.top>=front.bottom-1f)
        val density=app.activity.resources.displayMetrics.density
        val g=ArcDeckMath.geometry(root.width/density,root.height/density)
        assertEquals(root.left+(g.focusX+ArcDeckMath.focusShiftX(g))*density,front.center.x,2f)
        assertEquals(root.top+(g.focusY-ArcDeckMath.focusLift(g))*density,front.center.y,2f)
        val poses=ArcDeckMath.frames(SampleLibrary.articles.size,0f,g)
        assertEquals(root.top+poses.single {it.article==1}.centerY*density,below.center.y,2f)
    }
    @Test fun switchingFocusKeepsTheSameRiseAndCardSize() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        val root=app.onNodeWithTag("feed-home").fetchSemanticsNode().boundsInRoot
        val density=app.activity.resources.displayMetrics.density
        val g=ArcDeckMath.geometry(root.width/density,root.height/density)
        for((index,id) in listOf(0 to "forest",1 to "street",0 to "forest")) {
            app.onNodeWithTag("feed-home").performScrollToIndex(index)
            app.waitForIdle()
            val selected=app.onNodeWithTag("article-"+id).fetchSemanticsNode().boundsInRoot
            assertEquals(root.left+(g.focusX+ArcDeckMath.focusShiftX(g))*density,selected.center.x,2f)
            assertEquals(root.top+(g.focusY-ArcDeckMath.focusLift(g))*density,selected.center.y,2f)
            assertEquals(g.cardWidth*density,selected.width,2f)
            assertEquals(g.cardHeight*density,selected.height,2f)
        }
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        // The main card rises above the following card instead of flattening its neighbours.
        app.onNodeWithTag("article-forest").performTouchInput {click(Offset(width*.5f,height*.9f))}
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
