package com.semiovert.app

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.feature.records.RecordCalendar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionFlowTest {
    private val durationScale=AppMotionDurationScale(1f)
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @OptIn(ExperimentalTestApi::class)
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>(effectContext=durationScale)

    @Test fun touchRequestsSupportedHighRefreshAfterActivityRecreation() {
        fun assertRequestedRate()=app.runOnIdle {
            val now=SystemClock.uptimeMillis()
            val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,40f,40f,0)
            app.activity.window.callback.dispatchTouchEvent(down)
            down.recycle()
            @Suppress("DEPRECATION")
            val display=app.activity.windowManager.defaultDisplay
            val mode=display.mode
            val rates=display.supportedModes.filter {it.physicalWidth==mode.physicalWidth&&it.physicalHeight==mode.physicalHeight}.map {it.refreshRate}
            val expected=if(Build.VERSION.SDK_INT>=34)MotionFramePolicy.TARGET_FPS else MotionFramePolicy.legacyRefreshRate(rates)
            assertEquals(expected,app.activity.window.attributes.preferredRefreshRate,.001f)
            val cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,40f,40f,0)
            app.activity.window.callback.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        assertRequestedRate()
        app.activityRule.scenario.recreate();app.waitForIdle()
        assertRequestedRate()
    }

    @Test fun systemBackFromPrimaryPagesRestoresHomeAndItsMainCircle() {
        for(label in listOf("分类","搜索","我的")) {
            val action=app.onNodeWithTag("main-circle").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first {it.label=="前往"+label}
            app.runOnIdle {assertTrue(action.action())}
            app.waitForIdle()
            app.onNodeWithTag("main-circle").assertContentDescriptionEquals(label+"主圆")
            Espresso.pressBack()
            app.waitForIdle()
            app.onNodeWithTag("feed-home").assertIsDisplayed()
            app.onNodeWithTag("main-circle").assertContentDescriptionEquals("首页主圆")
            app.onNodeWithTag("main-circle").assertHasClickAction().performTouchInput {click()}
            app.waitForIdle()
            app.onNodeWithText("去哪里看看").assertDoesNotExist()
            app.onNodeWithTag("create-draft").assertIsDisplayed()
            app.onNodeWithTag("draft-box").assertIsDisplayed()
            app.onNodeWithTag("main-circle").performClick()
            app.waitForIdle()
            app.onNodeWithTag("create-draft").assertDoesNotExist()
            app.onNodeWithTag("feed-home").assertIsDisplayed()
        }
    }

    @Test fun radialMenuReturnsSoftlyBeforeNavigation() {
        val density=app.activity.resources.displayMetrics.density
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("main-circle").performTouchInput {down(center);advanceEventTime(800);moveTo(center+Offset(-94f,-100f)*density,200)}
            app.mainClock.advanceTimeBy(350)
            app.onNodeWithTag("radial-search").assertExists()
            app.onNodeWithTag("main-circle").performTouchInput {up()}
            app.mainClock.advanceTimeBy(240)
            app.onNodeWithTag("feed-home").assertExists()
            app.onNodeWithTag("radial-category").assertExists()
            app.onNodeWithTag("radial-search").assertExists()
            app.mainClock.advanceTimeBy(700)
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("search-input").assertIsDisplayed()
    }

    @Test fun homeDraftCirclesStackAboveMainAndKeepLongPressNavigation() {
        app.onNodeWithTag("main-circle").performTouchInput {click()}
        app.waitForIdle()
        val main=app.onNodeWithTag("main-circle").fetchSemanticsNode().boundsInRoot
        val create=app.onNodeWithTag("create-draft").assertIsDisplayed()
            .assertContentDescriptionEquals("创建草稿").fetchSemanticsNode().boundsInRoot
        val drafts=app.onNodeWithTag("draft-box").assertIsDisplayed()
            .assertContentDescriptionEquals("草稿箱").fetchSemanticsNode().boundsInRoot
        assertEquals(main.center.x,create.center.x,2f)
        assertEquals(main.center.x,drafts.center.x,2f)
        assertEquals(64f,(drafts.center.y-create.center.y)/app.activity.resources.displayMetrics.density,1f)
        assertTrue(drafts.bottom<main.top)
        app.onNodeWithText("去哪里看看").assertDoesNotExist()

        app.onNodeWithTag("create-draft").performTouchInput {click()}
        app.waitForIdle()
        app.onNodeWithTag("create-draft").assertDoesNotExist()
        app.onNodeWithTag("feed-home").assertIsDisplayed()

        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        app.onNodeWithTag("home-draft-dismiss").performTouchInput {click(Offset(12f,12f))}
        app.waitForIdle()
        app.onNodeWithTag("draft-box").assertDoesNotExist()

        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        Espresso.pressBack()
        app.waitForIdle()
        app.onNodeWithTag("draft-box").assertDoesNotExist()
        app.onNodeWithTag("feed-home").assertIsDisplayed()

        app.onNodeWithTag("main-circle").performClick()
        app.waitForIdle()
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick) {it()}
        app.waitForIdle()
        app.onNodeWithTag("create-draft").assertDoesNotExist()
        app.onNodeWithText("去哪里看看").assertIsDisplayed()
    }

    private fun releaseAttachedSearchPull(returnToRest: Boolean) {
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {
            val rest=center+Offset(-94f,-100f)*density
            val pulled=center+Offset(-94f,-100f)*1.35f*density
            down(center);advanceEventTime(800)
            moveTo(rest,200)
            moveTo(pulled,160)
            if(returnToRest)moveTo(rest,160)
            up()
        }
        app.onNodeWithTag("search-input").assertIsDisplayed()
        app.onNodeWithTag("shortcut-search").assertDoesNotExist()
    }

    @Test fun pullingWithoutDetachingStillNavigatesOnRelease() {
        releaseAttachedSearchPull(returnToRest=false)
    }

    @Test fun pullingAndReturningToTheSelectedCircleStillNavigatesOnRelease() {
        releaseAttachedSearchPull(returnToRest=true)
    }

    @Test fun pullingBeyondTheMenuPinsAnEntryThatSurvivesRecreationAndReconnects() {
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {
            down(center);advanceEventTime(800)
            moveTo(center+Offset(-94f,-100f)*density,200)
            moveTo(center+Offset(-175f,-186f)*density,200);up()
        }
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        app.onNodeWithTag("shortcut-search").assertIsDisplayed()
        assertTrue(app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getString("nav_shortcuts","")!!.contains("search"))
        app.activityRule.scenario.recreate();app.waitForIdle()
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        app.onNodeWithTag("shortcut-search").performClick()
        app.onNodeWithTag("search-input").assertIsDisplayed()
        val main=app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot()
        val shortcut=app.onNodeWithTag("shortcut-search").getUnclippedBoundsInRoot()
        val drop=Offset(((main.left+main.right)/2-shortcut.left).value*density,((main.top+main.bottom)/2-shortcut.top).value*density)
        app.onNodeWithTag("shortcut-search").performTouchInput {down(center);advanceEventTime(800);moveTo(drop,300);up()}
        app.onNodeWithTag("shortcut-search").assertDoesNotExist()
    }

    @Test fun openingAndInitialFlowDoNotReplayWhenReturningOrRecreating() {
        app.onNodeWithTag("identity-intro").assertDoesNotExist()
        app.onNodeWithTag("article-forest").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.waitForIdle()
        app.onNodeWithTag("article-forest").performClick()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.activityRule.scenario.recreate();app.waitForIdle()
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
            app.onNodeWithTag("article-forest").assertIsDisplayed().assertIsEnabled()
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun calendarKeepsDetailOpenWhileChangingDateAndClosesSoftly() {
        app.activity.runOnUiThread {app.activity.setContent {MotionEnvironment {SoTheme {Column {RecordCalendar("lin",true,false,false,initialExpanded=true)}}}}}
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("record-day-25").performClick()
            app.mainClock.advanceTimeBy(180)
            val partial=app.onNodeWithTag("record-detail").fetchSemanticsNode().boundsInRoot.height
            app.mainClock.advanceTimeBy(400)
            val full=app.onNodeWithTag("record-detail").fetchSemanticsNode().boundsInRoot.height
            assertTrue("The detail container must grow continuously: partial=$partial, full=$full",partial>0f&&partial<full)
            app.onNodeWithTag("record-day-26").performClick()
            app.mainClock.advanceTimeBy(160)
            assertEquals(full,app.onNodeWithTag("record-detail").fetchSemanticsNode().boundsInRoot.height,2f)
            assertTrue(app.onNodeWithTag("record-content-25").getUnclippedBoundsInRoot().left.value<0f)
            assertTrue(app.onNodeWithTag("record-content-26").getUnclippedBoundsInRoot().left.value>0f)
            app.mainClock.advanceTimeBy(500)
            app.onNodeWithTag("record-day-26").assertIsSelected()
            app.onNodeWithContentDescription("关闭日期详情").performClick()
            app.mainClock.advanceTimeBy(180)
            app.onNodeWithTag("record-detail").assertExists()
            app.mainClock.advanceTimeBy(300)
            app.onNodeWithTag("record-detail").assertDoesNotExist()
            app.onNodeWithTag("record-day-25").assertIsDisplayed()
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun disabledSystemAnimationsSkipTheIntroAndMovingPageTransitions() {
        val old=Settings.Global.getString(app.activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE)?:"1"
        fun shell(command: String) {ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use {it.readBytes()}}
        try {
            shell("settings put global animator_duration_scale 0")
            // The test rule owns its recomposer, so mirror the app's disabled clock.
            app.runOnIdle {durationScale.updateSystemScale(0f)}
            app.activityRule.scenario.recreate();app.waitForIdle()
            app.mainClock.autoAdvance=false
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
            app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
            app.mainClock.advanceTimeBy(64)
            app.onNodeWithContentDescription("切换到分类").performClick()
            app.mainClock.advanceTimeBy(64)
            app.waitForIdle()
            app.onNodeWithTag("feed-category").assertIsDisplayed()
            app.onNodeWithTag("category-wave").assertDoesNotExist()
            app.onNodeWithTag("article-forest").performClick()
            app.mainClock.advanceTimeBy(64)
            app.waitForIdle()
            assertEquals(0f,app.onNodeWithTag("reader-screen").getUnclippedBoundsInRoot().left.value,.5f)
        } finally {
            app.mainClock.autoAdvance=true
            durationScale.updateSystemScale(old.toFloat())
            shell("settings put global animator_duration_scale $old")
        }
    }
}
