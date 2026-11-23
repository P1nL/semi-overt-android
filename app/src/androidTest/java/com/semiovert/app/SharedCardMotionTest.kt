package com.semiovert.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.model.NavigationPlacement
import java.io.File
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
    private fun field(tag: String)=app.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)),useUnmergedTree=true)
    private fun lift(id: String,distanceDp: Float) {
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("article-handle-"+id,useUnmergedTree=true).performTouchInput {
            val start=Offset(center.x,12f*density)
            down(start);moveTo(start+Offset(0f,-distanceDp*density),600);advanceEventTime(180);up()
        }
        app.waitForIdle()
    }
    @Test fun returningBodyKeepsMovingAfterTheOldExitWindow() {
        navigate("分类")
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        val sectionTag="section-"+SampleLibrary.article("forest")!!.sections.first().id
        val resting=app.onNodeWithTag(sectionTag).getUnclippedBoundsInRoot()
        app.mainClock.autoAdvance=false
        try {
            Espresso.pressBack()
            var progress=1f
            app.waitUntil(5000) {
                app.mainClock.advanceTimeByFrame()
                progress=app.onNodeWithTag("reading-expansion").fetchSemanticsNode().config[
                    SemanticsProperties.ProgressBarRangeInfo].current
                progress<.86f
            }
            assertTrue("Sample the extended body exit before it completes",progress>.83f)
            val body=SceneMotion.readingBody(progress,returning=true)
            assertTrue(body>0f&&body<1f)
            val section=app.onNode(hasTestTag(sectionTag) and hasAnyAncestor(hasTestTag("reader-preview")))
            section.assertIsDisplayed()
            val moving=section.getUnclippedBoundsInRoot()
            val travel=app.activity.resources.configuration.screenHeightDp
            assertEquals("The rendered body uses the slower return progress",
                resting.top.value+travel*(1f-body),moving.top.value,2f)
            val captures=File(app.activity.getExternalFilesDir(null),"motion-captures").apply {mkdirs()}
            File(captures,"reader-return-body.png").outputStream().use {
                app.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("reading-expansion").assertDoesNotExist()
        app.onNodeWithTag("feed-category").assertIsDisplayed()
    }

    @Test fun searchStackLiftsToReadingAndRestoresQueryWhileAuthorsStayRows() {
        navigate("搜索")
        field("search-input").performTextInput("林间");field("search-input").performImeAction();Espresso.closeSoftKeyboard();app.waitForIdle()
        app.onNodeWithTag("search-wave").assertIsDisplayed()
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
        Espresso.pressBack();app.waitForIdle()
        app.waitUntil(3000){app.onAllNodesWithTag("search-input").fetchSemanticsNodes().isNotEmpty()}
        field("search-input").assertTextContains("林间")
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
    @Test fun categoryTapDocksTheMainCircleAndReturnsItToItsOriginalPosition() {
        app.runOnIdle { ViewModelProvider(app.activity)[PreviewViewModel::class.java].placement(NavigationPlacement(.25f,.1f)) }
        navigate("分类")
        fun circle()=app.onNodeWithTag("main-circle",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        fun pixel(bounds: androidx.compose.ui.geometry.Rect)=app.onRoot().captureToImage().toPixelMap()[
            (bounds.left+bounds.width*.76f).toInt(),(bounds.top+bounds.height*.66f).toInt()]
        val resting=circle()
        val ink=pixel(resting)
        val screenRight=app.onRoot().fetchSemanticsNode().boundsInRoot.right
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("article-forest").performClick()
            app.mainClock.advanceTimeBy(320)
            val moving=circle()
            assertTrue("The circle moves right and up while the card is opening",moving.right>resting.right&&moving.top<resting.top)
            assertTrue("It has not jumped straight to the dock",moving.right<screenRight-20f)
            assertEquals(ink,pixel(moving))
            var previous=moving
            var mounted=false
            for(elapsed in 336..960 step 16) {
                app.mainClock.advanceTimeByFrame()
                val circles=app.onAllNodesWithTag("main-circle",useUnmergedTree=true).fetchSemanticsNodes()
                val reader=circles.firstOrNull {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("打开目录")==true
                }
                val current=(reader?:circles.single()).boundsInRoot
                assertTrue("The circle cannot reverse on handoff at ${elapsed}ms: $previous -> $current",
                    current.right>=previous.right-1f&&current.top<=previous.top+1f)
                previous=current
                if(reader!=null){mounted=true;break}
            }
            assertTrue("The live reader takes over during the sampled frames",mounted)
            assertEquals("The live reader starts at the dock on its first frame",20f,screenRight-previous.right,1f)
            app.mainClock.advanceTimeBy(SceneMotion.READER_CIRCLE_DOCK_MS.toLong())
            assertEquals(previous,circle())
            app.onNodeWithTag("reader-screen").assertIsDisplayed()
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        val docked=circle()
        assertEquals(20f,screenRight-docked.right,1f)
        assertEquals(ink,pixel(docked))
        app.mainClock.autoAdvance=false
        try {
            Espresso.pressBack();app.mainClock.advanceTimeBy(250)
            val moving=circle()
            assertTrue(moving.right in resting.right..docked.right)
            assertEquals(ink,pixel(moving))
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("article-forest").assertIsDisplayed()
        assertEquals(resting,circle())
    }
    @Test fun readerBackScrollsTheLiveArticleToTheTopBeforeStartingTheCardReturn() {
        navigate("分类")
        app.onNodeWithTag("article-forest").performClick()
        val section=app.onNodeWithTag("section-opening")
        val reader=app.onNodeWithTag("reader-content")
        val initialTop=section.getUnclippedBoundsInRoot().top.value
        reader.performScrollToIndex(2)
        val scrolledTop=section.getUnclippedBoundsInRoot().top.value
        assertTrue(scrolledTop<initialTop-8f)
        app.mainClock.autoAdvance=false
        try {
            app.runOnIdle { app.activity.onBackPressedDispatcher.onBackPressed() }
            val samples=mutableListOf<Float>()
            for(frame in 0 until 8) {
                app.mainClock.advanceTimeByFrame()
                if(app.onAllNodesWithTag("reading-expansion").fetchSemanticsNodes().isNotEmpty())break
                reader.assertIsDisplayed()
                samples+=section.getUnclippedBoundsInRoot().top.value
                if(samples.last()>scrolledTop&&samples.last()<initialTop)break
            }
            assertTrue("The section must pass through an intermediate position: $scrolledTop -> $samples -> $initialTop",
                samples.any {it>scrolledTop&&it<initialTop})
            app.mainClock.advanceTimeBy(500)
            app.onNodeWithTag("reading-expansion").assertExists()
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("article-forest").assertIsDisplayed()
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
        app.onNodeWithTag("drawer-panel").performSemanticsAction(SemanticsActions.Dismiss) {it()};app.waitForIdle()
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
        field("account-email").performScrollTo().performTextInput("reader@example.invalid")
        app.onNodeWithTag("account-password").performScrollTo().performTextInput("reader-pass-123")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("account-submit").performScrollTo().performClick();app.waitForIdle()
        app.onNodeWithTag("profile-identity").performClick()
        app.onNodeWithText("林间",useUnmergedTree=true).performScrollTo().performClick()
        app.onNodeWithText("保存资料").performScrollTo().performClick();app.waitForIdle()
        app.onNodeWithTag("profile-shared-avatar",useUnmergedTree=true).assertIsDisplayed()
        app.onNodeWithTag("profile-cover-image",useUnmergedTree=true).assertIsDisplayed()
        app.onNodeWithText("我的",useUnmergedTree=true).assertDoesNotExist()
        app.onNodeWithTag("profile-articles").assertIsDisplayed()
        lift("forest",135f)
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        Espresso.pressBack();app.waitForIdle()
        app.onNodeWithTag("profile-wave").assertIsDisplayed()
        app.onNodeWithTag("article-handle-forest",useUnmergedTree=true).assertIsDisplayed()
    }
    @Test fun homeAndCategoryOverlapDuringTheSecondHalfOfHomeDeparture() {
        val card=app.onNodeWithTag("arc-card-forest")
        val restingTop=card.getUnclippedBoundsInRoot().top.value
        app.onNodeWithTag("feed-category").assertDoesNotExist()
        menu();app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("切换到分类").performClick()
            // Navigation now waits for a real draw of the pre-departure snapshot.
            // Start timing when the outgoing card is moving on the virtual clock.
            var started=false
            for(frame in 0 until 60) {
                app.mainClock.advanceTimeByFrame()
                app.waitForIdle()
                if(app.onAllNodesWithTag("arc-card-forest").fetchSemanticsNodes().isNotEmpty()&&
                    card.getUnclippedBoundsInRoot().top.value>restingTop+.2f) {
                    started=true
                    break
                }
            }
            assertTrue("Home starts its departure after the snapshot is recorded",started)
            app.mainClock.advanceTimeBy(560)
            app.onNodeWithTag("feed-home").assertExists()
            app.onNodeWithTag("feed-category").assertExists()
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
        app.onNodeWithTag("feed-home").assertDoesNotExist()
        app.onNodeWithTag("feed-category").assertIsDisplayed()
    }
}
