package com.semiovert.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.navigation.AppNavigation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.*

/** Fixed in-memory guest state: no preference reset, login, or mutation of the user's settings. */
class AuthorAvatarFeedbackTest {
    @get:Rule val app=createComposeRule()
    private fun start() {
        val model=PreviewViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        app.setContent {SoTheme(ThemeMode.LIGHT) {
            CompositionLocalProvider(LocalIdentity provides remember {IdentityAnchor(false,0)},LocalMotionEnabled provides true) {
                AppNavigation(PreviewState(signedIn=false),model) {}
            }
        }}
        app.waitForIdle()
    }
    private fun navigate(label: String) {
        val action=app.onNodeWithTag("main-circle").fetchSemanticsNode().config[SemanticsActions.CustomActions].first {it.label=="前往$label"}
        app.runOnUiThread {action.action()}
    }
    private fun authorInk(): Rect {
        val pixels=app.onRoot().captureToImage().toPixelMap()
        val width=(pixels.width*.45f).toInt();val height=(pixels.height*.48f).toInt()
        val ink=BooleanArray(width*height)
        for(y in 0 until height)for(x in 0 until width) {
            val c=pixels[x,y]
            ink[y*width+x]=abs(c.red-105f/255f)<.025f&&abs(c.green-80f/255f)<.025f&&abs(c.blue-216f/255f)<.025f
        }
        // The selected scope label uses the same purple. Measure the solid avatar component, not that text.
        val queue=IntArray(ink.size)
        var largest=0;var bounds=Rect.Zero
        for(seed in ink.indices)if(ink[seed]) {
            var head=0;var tail=1;queue[0]=seed;ink[seed]=false
            var left=width;var top=height;var right=0;var bottom=0
            while(head<tail) {
                val index=queue[head++];val x=index%width;val y=index/width
                left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
                fun visit(next: Int) {if(ink[next]) {ink[next]=false;queue[tail++]=next}}
                if(x>0)visit(index-1);if(x+1<width)visit(index+1)
                if(y>0)visit(index-width);if(y+1<height)visit(index+width)
            }
            if(tail>largest) {largest=tail;bounds=Rect(left.toFloat(),top.toFloat(),right+1f,bottom+1f)}
        }
        assertTrue("The selected author remains visible above the route layers",largest>100)
        return bounds
    }

    @Test fun selectedSearchAndReaderAvatarsMoveToTheirAuthorAndMorphIntoTheGuestIdentity() {
        start();navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("街角");app.onNodeWithTag("search-input").performImeAction()
        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        val search=authorInk()
        val searchLayout=app.onNodeWithTag("author-avatar-zhou",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("author-zhou").performTouchInput {click()}
            app.mainClock.advanceTimeBy(200)
            app.onNodeWithTag("author-screen").assertExists()
            val moving=authorInk()
            assertTrue("Selected search avatar scales continuously: source=$search, layout=$searchLayout, moving=$moving",moving.width>search.width+1f)
            assertTrue("No separated old and new avatar copies",moving.height<moving.width+4f)
            app.onNodeWithTag("spark-feedback").assertExists()
            app.mainClock.advanceTimeBy(1600)
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("author-profile-feed").performScrollToNode(hasTestTag("author-articles"))
        app.onNodeWithTag("article-street").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed();app.waitForIdle()
        val reading=authorInk()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithText("街角观察员").performTouchInput {click()}
            app.mainClock.advanceTimeBy(200)
            val moving=authorInk()
            assertTrue("The reading author icon grows instead of disappearing with the opaque reader",moving.width>reading.width+1f)
            assertTrue("The old reader avatar does not leave a second copy",moving.height<moving.width+4f)
            app.mainClock.advanceTimeBy(1400)
            app.onNodeWithTag("author-avatar-zhou",useUnmergedTree=true).assertIsDisplayed()
            val departure=authorInk()
            navigate("首页")
            app.mainClock.advanceTimeBy(160)
            val morphing=authorInk()
            assertTrue("Pixelation travels with the avatar instead of swapping artwork at the destination",morphing.center.x>departure.center.x+1f)
            app.mainClock.advanceTimeBy(1600)
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("home-theme-toggle").assertIsDisplayed()
        app.onNodeWithTag("author-screen").assertDoesNotExist()
    }

    @Test fun guestSettingsHaveNoLoginShortcutAndChildButtonsUseSparkFeedback() {
        start()
        app.onNodeWithContentDescription("打开个人页").performClick()
        app.onNodeWithContentDescription("设置").performClick()
        app.onNodeWithText("进入登录页").assertDoesNotExist()
        app.onNodeWithText("页面状态预览").performScrollTo()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithText("页面状态预览").performTouchInput {click()}
            app.mainClock.advanceTimeBy(120)
            app.onNodeWithTag("spark-feedback").assertExists()
            app.mainClock.advanceTimeBy(600)
            app.onNodeWithText("空内容").performTouchInput {click()}
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("spark-feedback").assertExists()
            app.mainClock.advanceTimeBy(500)
        } finally {app.mainClock.autoAdvance=true}
    }
}
