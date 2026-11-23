package com.semiovert.app

import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryWaveDeckTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private fun enter() {
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
        app.onNodeWithContentDescription("切换到分类").performClick()
        app.waitForIdle()
        app.onNodeWithTag("category-wave").assertIsDisplayed()
    }
    private fun rest(index: Int,duration: ReadingDuration=ReadingDuration.QUICK) {
        val count=SampleLibrary.inDuration(duration).size
        app.onNodeWithTag("feed-category").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,"第 ${index+1} 篇，共 $count 篇，已停稳"))
    }
    private fun swipe(down: Boolean) {
        app.onNodeWithTag("category-wave").performTouchInput {
            val start=Offset(width*.96f,height*if(down).14f else .78f)
            swipe(start,start+Offset(0f,height*if(down).61f else -.61f),600)
        }
        app.waitForIdle()
    }
    private fun bounds(id: String)=app.onNodeWithTag("wave-card-"+id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
    private fun labelLayout(label: SemanticsNodeInteraction): TextLayoutResult {
        val results=mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {assertTrue(it(results))}
        return results.single()
    }

    @Test fun categoryLabelIsCenteredAboveTheLargeCounter() {
        enter()
        fun assertCenter(duration: ReadingDuration) {
            val labelNode=app.onNodeWithTag("category-label").assertTextEquals(duration.label)
            val style=labelLayout(labelNode).layoutInput.style
            assertEquals(18.sp,style.fontSize)
            assertEquals(FontWeight.Bold,style.fontWeight)
            val label=labelNode.fetchSemanticsNode().boundsInRoot
            val counter=app.onNodeWithTag("deck-counter").fetchSemanticsNode().boundsInRoot
            val firstCardTop=SampleLibrary.inDuration(duration).minOf {bounds(it.id).top}
            val numberBand=counter.copy(bottom=minOf(counter.bottom,firstCardTop))
            val nightMode=app.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val ink=if(nightMode==Configuration.UI_MODE_NIGHT_YES)Color.White else Color.Black
            val pixels=app.onRoot().captureToImage().toPixelMap()
            fun inkCenter(band: Rect): Float {
                var left=pixels.width
                var right=-1
                for(row in band.top.roundToInt() until band.bottom.roundToInt()) {
                    for(column in 0 until pixels.width) {
                        if(pixels[column,row]==ink) {
                            left=minOf(left,column)
                            right=maxOf(right,column)
                        }
                    }
                }
                assertTrue("Expected rendered text in $band",right>left)
                return (left+right)/2f
            }
            assertTrue(label.bottom<=counter.top)
            assertEquals(inkCenter(numberBand),inkCenter(label),app.density.density*1.5f)
        }
        assertCenter(ReadingDuration.QUICK)
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        assertCenter(ReadingDuration.MEDIUM)
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        assertCenter(ReadingDuration.LONG)
    }

    @Test fun categoryLabelScramblesAcrossTheDurationSwap() {
        enter()
        val label=app.onNodeWithTag("category-label")
        val previous=ReadingDuration.QUICK.label
        val target=ReadingDuration.MEDIUM.label
        assertEquals(previous,labelLayout(label).layoutInput.text.text)
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("main-circle").performClick()
            app.mainClock.advanceTimeBy(SceneMotion.CATEGORY_SWAP_MS+128L)
            label.assertTextEquals(target)
            val middle=labelLayout(label).layoutInput.text.text
            assertNotEquals(previous,middle)
            assertNotEquals(target,middle)
            app.mainClock.advanceTimeBy(SceneMotion.CATEGORY_SWAP_ENTER_MS.toLong())
            assertEquals(target,labelLayout(label).layoutInput.text.text)
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun frontIsCompleteAndCardsBehindItAreHigherWithoutAnOldGlScene() {
        enter();rest(0)
        val root=app.onNodeWithTag("feed-category").fetchSemanticsNode().boundsInRoot
        val front=bounds("forest");val middle=bounds("tea");val back=bounds("shade")
        assertTrue(front.left>=root.left&&front.right<=root.right&&front.top>=root.top&&front.bottom<=root.bottom)
        val density=app.activity.resources.displayMetrics.density
        val g=WaveDeckMath.geometry(root.width/density,root.height/density,3)
        assertEquals("The top edge stays at the original height",root.top+g.frontTop*density,front.top,1f)
        assertEquals("The front card extends to the viewport bottom",root.bottom,front.bottom,1f)
        assertEquals(root.bottom,middle.bottom,1f)
        assertEquals(root.bottom,back.bottom,1f)
        assertEquals("All cards keep equal widths",front.width,middle.width,1f)
        assertEquals(front.width,back.width,1f)
        val title=app.onNodeWithText(SampleLibrary.inDuration(ReadingDuration.QUICK).first().title,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals("Copy clears the shared reading handle",front.top+34f*density,title.top,1f)
        assertEquals(front.left+18f*density,title.left,1f)
        assertTrue(front.height>front.width)
        assertTrue(back.center.y<middle.center.y&&middle.center.y<front.center.y)
        app.runOnIdle {assertNull(app.activity.window.decorView.solidScene())}
    }

    @Test fun downwardAndUpwardGesturesRunBothDirectionsAndLoopAtBothEnds() {
        enter()
        swipe(true);rest(1);app.onNodeWithTag("article-tea").assertIsDisplayed()
        swipe(false);rest(0)
        swipe(false);rest(2);app.onNodeWithTag("article-shade").assertIsDisplayed()
        swipe(true);rest(0)
    }

    @Test fun readingRecreationAndEachDurationKeepTheirOwnSelection() {
        enter()
        app.onNodeWithTag("feed-category").performScrollToIndex(1)
        app.onNodeWithTag("article-tea").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle();rest(1)
        app.activityRule.scenario.recreate();app.waitForIdle();rest(1)
        app.onNodeWithTag("main-circle").performClick();app.waitForIdle();rest(0,ReadingDuration.MEDIUM)
        val mediumBottom=app.onNodeWithTag("feed-category").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals("The tallest rear card also extends to the bottom",mediumBottom,bounds("letter").bottom,1f)
        for(article in SampleLibrary.inDuration(ReadingDuration.MEDIUM))assertEquals(bounds("street").width,bounds(article.id).width,1f)
        val title=app.onNodeWithText(SampleLibrary.inDuration(ReadingDuration.MEDIUM).first().title,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val circle=app.onNodeWithTag("main-circle").fetchSemanticsNode().boundsInRoot
        assertTrue("Top-aligned text clears the bottom-right circle",!title.overlaps(circle))
        app.onNodeWithTag("feed-category").performScrollToIndex(2)
        app.onNodeWithTag("main-circle").performClick();app.waitForIdle();rest(0,ReadingDuration.LONG)
        app.onNodeWithTag("main-circle").performClick();app.waitForIdle();rest(1)
        app.onNodeWithTag("main-circle").performClick();app.waitForIdle();rest(2,ReadingDuration.MEDIUM)
    }

    @Test fun allRearCardsRespondAsSoonAsTheDragStarts() {
        enter()
        val density=app.activity.resources.displayMetrics.density
        val root=app.onNodeWithTag("category-wave").fetchSemanticsNode().boundsInRoot
        val g=WaveDeckMath.geometry(root.width/density,root.height/density,3)
        val front=bounds("forest");val following=bounds("tea");val rear=bounds("shade")
        val start=Offset(root.width*.96f,root.height*.12f)
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("category-wave").performTouchInput {down(start);moveTo(start+Offset(0f,g.scrollStep*density*.08f),400)}
            app.mainClock.advanceTimeByFrame()
            assertTrue(bounds("forest").top>front.top+24f)
            assertTrue("The next card responds at drag start",bounds("tea").top>following.top+1f)
            assertTrue("The rear card responds in the same frame",bounds("shade").top>rear.top+1f)
            app.onNodeWithTag("category-wave").performTouchInput {moveTo(start+Offset(0f,g.scrollStep*density*.24f),400)}
            app.mainClock.advanceTimeBy(320)
            assertTrue("Web-style neighbour springs make the first crest clearly stronger",
                bounds("tea").top-following.top>(bounds("shade").top-rear.top)*1.5f)
        } finally {
            app.onNodeWithTag("category-wave").performTouchInput {up()}
            app.mainClock.advanceTimeBy(1600);app.mainClock.autoAdvance=true
        }
    }

    @Test fun aNewDragCanReverseTheSettleWithoutQueuedPlayback() {
        enter();app.mainClock.autoAdvance=false
        try {
            swipe(true);app.mainClock.advanceTimeBy(80)
            swipe(false);app.mainClock.advanceTimeBy(1600)
        } finally {app.mainClock.autoAdvance=true}
        app.waitForIdle();rest(0)
    }

    @Test fun aShortDownwardDragCanAdvanceWithoutNeedingAFling() {
        enter()
        val density=app.activity.resources.displayMetrics.density
        val root=app.onNodeWithTag("category-wave").fetchSemanticsNode().boundsInRoot
        val g=WaveDeckMath.geometry(root.width/density,root.height/density,3)
        app.onNodeWithTag("category-wave").performTouchInput {
            val start=Offset(width*.96f,height*.12f)
            down(start)
            moveTo(start+Offset(0f,g.scrollStep*density*.45f),600)
            advanceEventTime(250)
            up()
        }
        app.waitForIdle();rest(1)
    }

    @Test fun downwardDragContinuesAfterTheFocusedCardChangesAtHalfway() {
        enter()
        val density=app.activity.resources.displayMetrics.density
        val root=app.onNodeWithTag("category-wave").fetchSemanticsNode().boundsInRoot
        val g=WaveDeckMath.embeddedGeometry(root.width/density,root.height/density,3,root.height/density,
            WaveDeckMath.restingFrontTop(root.width/density,root.height/density,root.height/density))
        val start=Offset(root.width*.5f,bounds("forest").top-root.top+8f*density)
        val deck=app.onNodeWithTag("category-wave",useUnmergedTree=true)
        app.mainClock.autoAdvance=false
        try {
            deck.performTouchInput {down(start);moveTo(start+Offset(0f,g.scrollStep*density*.48f/WaveDeckMath.DOWNWARD_GAIN),400)}
            app.mainClock.advanceTimeBy(48)
            app.onNodeWithTag("feed-category").assert(SemanticsMatcher("a live deck drag") {it.config[SemanticsProperties.StateDescription].endsWith("滑动中")})
            val before=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot().top.value
            deck.performTouchInput {moveTo(start+Offset(0f,g.scrollStep*density*.62f/WaveDeckMath.DOWNWARD_GAIN),160)}
            app.mainClock.advanceTimeBy(48)
            val after=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot().top.value
            assertTrue("The first card must keep following after focus changes: $before -> $after",after>before+20f)
            deck.performTouchInput {moveTo(start+Offset(0f,g.scrollStep*density*.68f/WaveDeckMath.DOWNWARD_GAIN),160)}
            app.mainClock.advanceTimeBy(48)
            assertTrue(app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot().top.value>after+20f)
        }finally {
            app.onRoot().performTouchInput {up()}
            app.mainClock.autoAdvance=true
        }
        app.waitForIdle();rest(1)
    }

    @Test fun tinyAndHorizontalGesturesDoNotChangeOrOpenTheFrontCard() {
        enter()
        app.onNodeWithTag("category-wave").performTouchInput {
            val start=Offset(width*.96f,height*.3f)
            swipe(start,start+Offset(0f,height*.005f),320)
        }
        rest(0)
        app.onNodeWithTag("category-wave").performTouchInput {
            val start=Offset(width*.96f,height*.3f)
            swipe(start,start+Offset(-width*.18f,0f),320)
        }
        rest(0);app.onNodeWithTag("reader-screen").assertDoesNotExist()
    }
}
