package com.semiovert.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.circleColors
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurfaceRefinementTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()
    private fun navigate(label: String) {
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
        app.onNodeWithContentDescription("切换到"+label).performClick()
    }
    private fun back() {Espresso.pressBack();app.waitForIdle()}
    private fun waitForTheme() {
        app.waitUntil(5000) {app.onNodeWithTag("theme-reveal-host").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]=="主题已就位"}
    }
    private fun save(name: String) {
        val file=File(app.activity.getExternalFilesDir(null),"ui-refinement/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {app.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun themeRevealExpandsFromTheButtonInBothDirections() {
        val button=app.onNodeWithTag("home-theme-toggle").fetchSemanticsNode().boundsInRoot
        val scale=app.activity.resources.displayMetrics.density
        for(toDark in listOf(true,false)) {
            app.mainClock.autoAdvance=false
            try {
                app.onNodeWithTag("home-theme-toggle").performClick()
                app.waitUntil(5000) {
                    app.mainClock.advanceTimeByFrame()
                    app.onAllNodesWithContentDescription(if(toDark)"切换到浅色模式" else "切换到深色模式")
                        .fetchSemanticsNodes().isNotEmpty()
                }
                app.mainClock.advanceTimeBy(240)
                val image=app.onRoot().captureToImage().toPixelMap()
                val near=image[image.width-12,(button.center.y+80f*scale).toInt()].luminance()
                val far=image[image.width-12,image.height-(80f*scale).toInt()].luminance()
                assertTrue("Inside the expanding circle uses the new palette",if(toDark)near<.2f else near>.7f)
                assertTrue("Outside the circle still holds the old palette",if(toDark)far>.7f else far<.2f)
                save(if(toDark)"theme-reveal-to-dark" else "theme-reveal-to-light")
                app.mainClock.advanceTimeBy(900)
            } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
        }
    }

    @Test fun secondarySurfacesCoverTheCircleBeforeTheirSlideFinishes() {
        navigate("我的")
        val opens=listOf<Pair<String,()->Unit>>(
            "settings-screen" to {app.onNodeWithContentDescription("设置").performClick()},
            "register-screen" to {app.onNodeWithText("创建账号",useUnmergedTree=true).performClick()},
            "forgot-screen" to {app.onNodeWithText("忘记密码",useUnmergedTree=true).performClick()})
        for((tag,open) in opens) {
            val circle=app.onNodeWithTag("main-circle").fetchSemanticsNode().boundsInRoot
            val main=Destination.PROFILE.circleColors.background
            app.mainClock.autoAdvance=false
            try {
                open();app.mainClock.advanceTimeBy(240)
                val page=app.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("The page is still sliding",page.left>1f)
                assertTrue("The moving page already overlaps the circle",page.left<circle.left)
                val pixel=app.onRoot().captureToImage().toPixelMap()[circle.center.x.toInt(),circle.center.y.toInt()]
                assertTrue("The secondary surface, not the old circle, must be drawn here",
                    kotlin.math.abs(pixel.red-main.red)+kotlin.math.abs(pixel.green-main.green)+kotlin.math.abs(pixel.blue-main.blue)>.3f)
                save("$tag-mid-slide")
                app.mainClock.advanceTimeBy(700)
            } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
            app.onNodeWithTag("main-circle").assertDoesNotExist()
            back();app.onNodeWithTag("main-circle").assertIsDisplayed()
        }
    }

    @Test fun themePreferenceAndSnapshotPositionUseTheActualHomeAndCircle() {
        save("home-light")
        app.onNodeWithTag("home-theme-toggle").performClick()
        app.onNodeWithTag("home-theme-toggle").assertContentDescriptionEquals("切换到浅色模式")
        waitForTheme()
        save("home-dark")
        app.onNodeWithTag("home-theme-toggle").performClick()
        app.onNodeWithTag("home-theme-toggle").assertContentDescriptionEquals("切换到深色模式")
        waitForTheme()
        navigate("我的")
        val initial=app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot()
        app.onNodeWithContentDescription("设置").performClick()
        app.onNodeWithTag("follow-system-theme").assertIsOff().performClick().assertIsOn()
        app.onNodeWithTag("follow-system-theme").performClick().assertIsOff()
        save("settings")
        app.onNodeWithText("调整导航位置").performClick()
        app.onNodeWithTag("position-home-snapshot").assertIsDisplayed()
        val snapshot=app.onRoot().captureToImage().toPixelMap()
        assertTrue("The snapshot refreshes after returning to light theme",snapshot[snapshot.width-12,100].luminance()>.7f)
        app.onNodeWithText("左下").assertDoesNotExist()
        app.onNodeWithText("重置为右下角").assertDoesNotExist()
        app.onNodeWithText("取消").assertDoesNotExist()
        val preview=app.onNodeWithTag("position-preview")
        val before=preview.getUnclippedBoundsInRoot()
        assertEquals(initial.left.value,before.left.value,.5f)
        assertEquals(initial.top.value,before.top.value,.5f)
        val scale=app.activity.resources.displayMetrics.density
        preview.performTouchInput {down(center);moveBy(Offset(-160f*scale,-70f*scale),delayMillis=400);up()}
        val moved=preview.getUnclippedBoundsInRoot()
        assertTrue(moved.left<before.left&&moved.top<before.top)
        save("navigation-position")
        app.onNodeWithText("保存位置").performClick();back()
        val saved=app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot()
        assertEquals(moved.left.value,saved.left.value,.5f)
        assertEquals(moved.top.value,saved.top.value,.5f)
        assertTrue(app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getFloat("nav_x",1f)<1f)
    }

    @Test fun searchHintsFollowTheScopeAndAuthorKeepsTheStackAndNavigation() {
        navigate("搜索")
        app.onNodeWithText("搜索文章标题或作者").assertIsDisplayed()
        save("search-articles")
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithText("搜索作者昵称或简介").assertIsDisplayed()
        app.onNodeWithTag("author-zhou").performClick()
        app.onNodeWithText("作者主页").assertDoesNotExist()
        app.onNodeWithTag("main-circle").assertIsDisplayed()
        save("author-profile")
        app.onNodeWithTag("author-profile-feed").performScrollToNode(hasTestTag("author-articles"))
        app.onNodeWithTag("author-wave").assertIsDisplayed()
        save("author-stack")
        navigate("首页")
        app.onNodeWithTag("home-theme-toggle").assertIsDisplayed()
    }
}
