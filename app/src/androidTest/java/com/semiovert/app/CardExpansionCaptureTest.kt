package com.semiovert.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.semiovert.app.core.designsystem.ArticleOrigin
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Produces seekable native frames for the standalone card-motion viewer.
 * The Compose test clock advances by one 16ms frame; encoding time never enters playback.
 */
@RunWith(AndroidJUnit4::class)
class CardExpansionCaptureTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private fun readyHome() {
        app.onNodeWithTag("home-arc").assertIsDisplayed()
        app.waitUntil(15_000) {
            !app.onNodeWithTag("article-forest").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)
        }
        app.waitForIdle()
    }

    private fun openForest() {
        // Dispatch the selected card's action without tapping through an overlapping neighbour.
        app.onNodeWithTag("article-forest").performSemanticsAction(SemanticsActions.OnClick) {it()}
    }

    private fun readyCategory() {
        readyHome()
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick) {it()}
        app.onNodeWithContentDescription("切换到分类").performClick()
        app.waitForIdle()
    }

    private fun capture(name: String,durationMs: Int,trigger: ()->Unit) {
        val folder=File(app.activity.getExternalFilesDir(null),"card-expansion-viewer/$name").apply {mkdirs()}
        val frames=JSONArray()
        var coverSize: DpRect?=null
        var stoppedCover: DpRect?=null
        val titleSamples=mutableListOf<Pair<Long,Float>>()
        app.mainClock.autoAdvance=false
        try {
            trigger()
            app.mainClock.advanceTimeByFrame()
            val start=app.mainClock.currentTime
            for(frame in 0..durationMs/16) {
                if(frame>0)app.mainClock.advanceTimeByFrame()
                val progress=app.onAllNodesWithTag("reading-expansion").fetchSemanticsNodes().firstOrNull()
                    ?.config?.get(SemanticsProperties.ProgressBarRangeInfo)?.current
                if(progress!=null) {
                    val background=app.onNodeWithTag("reading-card-background",useUnmergedTree=true).getUnclippedBoundsInRoot()
                    coverSize?.let {initial ->
                        assertEquals("The cover must keep its width in both directions",(initial.right-initial.left).value,(background.right-background.left).value,.6f)
                        assertEquals("The cover must keep its height in both directions",(initial.bottom-initial.top).value,(background.bottom-background.top).value,.6f)
                    }?:run {coverSize=background}
                    if(name.startsWith("home-")||progress>=SceneMotion.READING_DRAG_STAGE) {
                        stoppedCover?.let {stopped ->
                            assertEquals("The faded cover must stay in place",stopped.top.value,background.top.value,.6f)
                            assertEquals(stopped.left.value,background.left.value,.6f)
                        }?:run {stoppedCover=background}
                    }
                    if(frame%4==0&&(name=="home-reading"||name=="home-closing")&&
                        SceneMotion.readingOpeningText(progress,ArticleOrigin.HOME,returning=name=="home-closing") in .08f.. .90f) {
                        val top=app.onNodeWithTag("reading-copy-title-source",useUnmergedTree=true).getUnclippedBoundsInRoot().top.value
                        titleSamples.add(app.mainClock.currentTime to top)
                    }
                }
                if(name=="home-lift"&&frame==58) {
                    val root=app.onNodeWithTag("feed-home").getUnclippedBoundsInRoot()
                    val g=ArcDeckMath.geometry((root.right-root.left).value,(root.bottom-root.top).value)
                    val card=app.onNodeWithTag("article-forest").getUnclippedBoundsInRoot()
                    assertEquals("The 900ms lift reaches its horizontal anchor within two frames",
                        root.left.value+ArcDeckMath.pulledLeft(g),card.left.value,.6f)
                    assertEquals("The 900ms lift reaches its vertical anchor within two frames",
                        root.top.value+g.focusY-ArcDeckMath.pullLift(0f,g), (card.top.value+card.bottom.value)/2f,.6f)
                }
                if(name=="home-lowering"&&frame==58) {
                    val root=app.onNodeWithTag("feed-home").getUnclippedBoundsInRoot()
                    val g=ArcDeckMath.geometry((root.right-root.left).value,(root.bottom-root.top).value)
                    val card=app.onNodeWithTag("article-forest").getUnclippedBoundsInRoot()
                    assertEquals("The 900ms return reaches its original horizontal anchor",
                        root.left.value+g.focusX,(card.left.value+card.right.value)/2f,.6f)
                    assertEquals("The 900ms return reaches its original vertical anchor",
                        root.top.value+g.focusY,(card.top.value+card.bottom.value)/2f,.6f)
                }
                if(name=="home-closing"&&frame==durationMs/16) {
                    assertNull("The ${SceneMotion.HOME_READING_CLOSE_MS}ms return must finish within the measured-source allowance",progress)
                }
                val original=app.onRoot().captureToImage().asAndroidBitmap()
                val bitmap=Bitmap.createScaledBitmap(original,540,(original.height*540f/original.width).toInt(),true)
                val filename=frame.toString().padStart(4,'0')+".webp"
                File(folder,filename).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY,92,it)
                }
                if(bitmap!==original)bitmap.recycle()
                frames.put(JSONObject().put("file",filename).put("t",app.mainClock.currentTime-start)
                    .put("p",progress?:if(name=="home-lift"||name=="home-lowering")JSONObject.NULL else if(name.endsWith("-closing"))0f else 1f))
            }
            File(folder,"frames.json").writeText(JSONObject().put("name",name).put("step",16)
                .put("width",540).put("frames",frames).toString())
            if(name=="home-reading"||name=="home-closing") {
                assertTrue("Sample at least three points on the real title path",titleSamples.size>=3)
                val speeds=titleSamples.zipWithNext {a,b ->(b.second-a.second)/(b.first-a.first)}
                assertTrue("The real home title must move in the requested direction",
                    speeds.all {if(name=="home-closing")it>0f else it<0f})
                assertTrue("The real home title must move at constant speed",speeds.max()-speeds.min()<.012f)
            }
        } finally {
            app.mainClock.autoAdvance=true
            app.waitForIdle()
        }
    }

    @Test fun captureHomeLift() {
        readyHome()
        capture("home-lift",1280) {openForest()}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
    }

    @Test fun captureHomeReading() {
        readyHome()
        openForest()
        app.waitForIdle()
        val duration=(SceneMotion.HOME_READING_OPEN_MS+64+15)/16*16
        capture("home-reading",duration) {openForest()}
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
    }
    @Test fun captureHomeLowering() {
        readyHome()
        openForest()
        app.waitForIdle()
        capture("home-lowering",1008) {app.onNodeWithTag("feed-home").performScrollToIndex(0)}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
    }

    @Test fun captureCategoryReading() {
        readyCategory()
        capture("category-reading",SceneMotion.READING_OPEN_MS+64) {openForest()}
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
    }

    @Test fun captureHomeClosing() {
        readyHome()
        openForest()
        app.waitForIdle()
        openForest()
        app.waitForIdle()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
        capture("home-closing",SceneMotion.HOME_READING_CLOSE_MS+80) {pressBack()}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("home-arc").assertIsDisplayed()
    }

    @Test fun captureCategoryClosing() {
        readyCategory()
        openForest()
        app.waitForIdle()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
        capture("category-closing",1280) {pressBack()}
        app.onNodeWithTag("reader-screen").assertDoesNotExist()
        app.onNodeWithTag("article-forest").assertIsDisplayed()
    }

    @Test fun captureCategoryDragHandoffWhileFingerIsStillDown() {
        readyCategory()
        val density=app.activity.resources.displayMetrics.density
        try {
            capture("category-drag",608) {
                app.onNodeWithTag("article-handle-forest",useUnmergedTree=true).performTouchInput {
                    val start=Offset(center.x,12f*density)
                    down(start)
                    moveTo(start-Offset(0f,SceneMotion.LIFT_DISTANCE_DP*density),600)
                }
            }
            app.onNodeWithTag("reader-screen").assertIsDisplayed()
            app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
        } finally {app.onRoot().performTouchInput {up()}}
    }

    @Test fun categoryHandleTapUsesTheSameReadingTransition() {
        readyCategory()
        app.onNodeWithTag("article-handle-forest",useUnmergedTree=true)
            .performSemanticsAction(SemanticsActions.OnClick) {it()}
        app.waitForIdle()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
    }

    @Test fun categoryFastReleaseBelowTheBoundaryStillCompletesReading() {
        readyCategory()
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("article-handle-forest",useUnmergedTree=true).performTouchInput {
            val start=Offset(center.x,12f*density)
            swipe(start,start-Offset(0f,48f*density),32)
        }
        app.waitForIdle()
        app.onNodeWithTag("reader-title").assertTextEquals("林间雾起的时候")
    }
}
