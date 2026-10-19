package com.semiovert.app

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.feature.records.RecordCalendar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionFlowTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    @Test fun highRefreshRateRequestSurvivesActivityRecreation() {
        fun assertRequestedRate()=app.runOnIdle {
            @Suppress("DEPRECATION")
            val display=app.activity.windowManager.defaultDisplay
            val mode=display.mode
            val rates=display.supportedModes.filter {it.physicalWidth==mode.physicalWidth&&it.physicalHeight==mode.physicalHeight}.map {it.refreshRate}
            val expected=if(Build.VERSION.SDK_INT>=34)MotionFramePolicy.TARGET_FPS else MotionFramePolicy.legacyRefreshRate(rates)
            assertEquals(expected,app.activity.window.attributes.preferredRefreshRate,.001f)
        }
        assertRequestedRate()
        app.activityRule.scenario.recreate();app.waitForIdle()
        assertRequestedRate()
    }

    @Test fun selectedCircleMergesAfterTheOtherCirclesBeforeNavigation() {
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
            app.onNodeWithTag("radial-category").assertDoesNotExist()
            app.onNodeWithTag("radial-search").assertExists()
            app.mainClock.advanceTimeBy(700)
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("search-input").assertIsDisplayed()
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
        app.onNodeWithTag("article-forest").performClick()
        androidx.test.espresso.Espresso.pressBack();app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.activityRule.scenario.recreate();app.waitForIdle()
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
            app.onNodeWithTag("article-forest").assertIsDisplayed().assertIsEnabled()
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun calendarDetailExpandsAndReversesBackIntoAnEdgeDate() {
        app.activity.runOnUiThread {app.activity.setContent {MotionEnvironment {SoTheme {Column {RecordCalendar("lin",true,false,false,initialExpanded=true)}}}}}
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("record-day-25").performClick()
            app.mainClock.advanceTimeBy(100)
            val partial=app.onNodeWithTag("record-detail").getUnclippedBoundsInRoot().height.value
            app.mainClock.advanceTimeBy(240)
            val full=app.onNodeWithTag("record-detail").getUnclippedBoundsInRoot().height.value
            assertTrue("The date bubble must grow continuously: partial=$partial, full=$full",partial>0f&&partial<full)
            app.onNodeWithContentDescription("关闭日期详情").performClick()
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
            app.activityRule.scenario.recreate();app.waitForIdle()
            app.mainClock.autoAdvance=false
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
            app.onNodeWithContentDescription("打开页面导航").performClick()
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
        } finally {app.mainClock.autoAdvance=true;shell("settings put global animator_duration_scale $old")}
    }
}
