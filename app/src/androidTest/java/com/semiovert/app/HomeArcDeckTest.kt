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
    @Test fun restingCardsStayOnTheRingWithoutAutomaticLiftOrTilt() {
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
        val density=app.activity.resources.displayMetrics.density
        val g=ArcDeckMath.geometry(root.width/density,root.height/density)
        val poses=ArcDeckMath.frames(SampleLibrary.articles.size,0f,g)
        for(index in listOf(0,1,10)) {
            val id=SampleLibrary.articles[index].id
            val bounds=app.onNodeWithTag("arc-card-"+id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val pose=poses.single {it.article==index}
            assertEquals(root.left+pose.centerX*density,bounds.center.x,2f)
            assertEquals(root.top+pose.centerY*density,bounds.center.y,2f)
            assertEquals(g.cardHeight*density,bounds.height,2f)
            app.onNodeWithTag("article-"+id).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"未展开"))
        }
        app.onNodeWithTag("arc-tab-forest",useUnmergedTree=true).assertDoesNotExist()
    }
    @Test fun scrollingDoesNotLiftAndClickingPullsInPlaceBeforeReading() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        val root=app.onNodeWithTag("feed-home").fetchSemanticsNode().boundsInRoot
        val density=app.activity.resources.displayMetrics.density
        val g=ArcDeckMath.geometry(root.width/density,root.height/density)
        for((index,id) in listOf(0 to "forest",1 to "street",0 to "forest")) {
            app.onNodeWithTag("feed-home").performScrollToIndex(index)
            app.waitForIdle()
            val selected=app.onNodeWithTag("article-"+id).fetchSemanticsNode().boundsInRoot
            assertEquals(root.left+g.focusX*density,selected.center.x,2f)
            assertEquals(root.top+g.focusY*density,selected.center.y,2f)
            assertEquals(g.cardWidth*density,selected.width,2f)
            assertEquals(g.cardHeight*density,selected.height,2f)
        }
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        val belowBefore=app.onNodeWithTag("arc-card-street",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        // Tap the exposed top strip, not a semantics-only click through a foreground card.
        app.onNodeWithTag("article-forest").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("feed-home").assertHomeResting(0)
        val pulled=app.onNodeWithTag("article-forest").fetchSemanticsNode().boundsInRoot
        assertEquals(root.left+ArcDeckMath.pulledLeft(g)*density,pulled.left,2f)
        assertEquals(root.top+(g.focusY-ArcDeckMath.pullLift(0f,g))*density,pulled.center.y,2f)
        val belowAfter=app.onNodeWithTag("arc-card-street",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals("Tilting does not move the neighbour horizontally",belowBefore.center.x,belowAfter.center.x,2f)
        assertTrue("Keep the front card full-sized, with only the requested tilt",belowAfter.height>belowBefore.height*.9f)
        assertTrue("The front card retains its place and the pulled card clears it",pulled.bottom<belowAfter.top)
        app.onNodeWithTag("article-forest").performTouchInput {click(Offset(width*.5f,height*.9f))}
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
    }
    @Test fun clickingAnUpperCardMakesMinimalRoomAndDraggingCollapsesIt() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        val root=app.onNodeWithTag("feed-home").fetchSemanticsNode().boundsInRoot
        val density=app.activity.resources.displayMetrics.density
        val g=ArcDeckMath.geometry(root.width/density,root.height/density)
        val id=SampleLibrary.articles[8].id
        val before=app.onNodeWithTag("article-"+id).fetchSemanticsNode().boundsInRoot
        app.onNodeWithTag("article-"+id).performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
        val pulled=app.onNodeWithTag("article-"+id).fetchSemanticsNode().boundsInRoot
        val header=app.onNodeWithContentDescription("打开个人页").fetchSemanticsNode().boundsInRoot
        assertTrue(pulled.top>=header.bottom)
        assertEquals(g.cardHeight*density,pulled.height,2f)
        assertEquals(root.right-16f*density,pulled.right,2f)
        assertTrue("Only enough repositioning to clear the header",pulled.top<root.top+g.focusY*density-g.cardHeight*density/2f)
        assertTrue(pulled.center.y>before.center.y-g.cardHeight*density)
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        swipePage(up=true)
        app.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已展开，再次点击阅读")).assertCountEquals(0)
    }
    @Test fun transparentHeaderRemainsTappableAboveOverflowingCards() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        app.onNodeWithContentDescription("打开页面导航").performTouchInput {click()}
        app.onNodeWithText("去哪里看看").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.onNodeWithContentDescription("打开个人页").performTouchInput {click()}
        app.waitForIdle()
        app.onNodeWithTag("profile-scene").assertIsDisplayed()
    }
    @Test fun upAndDownSelectCardsAndReadingAndRecreationKeepTheSelection() {
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        swipePage(up=true)
        app.onNodeWithTag("feed-home").assertHomeResting(1)
        app.onNodeWithTag("article-street").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
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
        app.onNodeWithTag("article-letter").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
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
