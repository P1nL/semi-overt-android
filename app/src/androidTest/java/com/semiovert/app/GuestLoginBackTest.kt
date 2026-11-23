package com.semiovert.app

import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SceneMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestLoginBackTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()

    private val avatar get()=app.onNodeWithContentDescription("打开个人页")
    private val form get()=app.onNodeWithTag("login-screen",useUnmergedTree=true)

    @Test fun edgeBackOnTheSecondVisitWaitsForEntryThenPlaysTheLoginExit() {
        avatar.performClick()
        app.waitForIdle()
        val resting=form.getUnclippedBoundsInRoot()
        val home=app.onNodeWithTag("main-circle").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first {it.label=="前往首页"}
        app.runOnIdle {assertTrue(home.action())}
        app.waitForIdle()

        app.mainClock.autoAdvance=false
        try {
            avatar.performClick()
            val timeToForm=SceneMotion.PROFILE_GUEST_ENTER_MS+160L
            app.mainClock.advanceTimeBy(timeToForm)
            assertEquals(resting,form.getUnclippedBoundsInRoot())

            startBackGesture()
            app.mainClock.advanceTimeBy(96)
            assertEquals(resting,form.getUnclippedBoundsInRoot())
            app.runOnIdle {app.activity.onBackPressedDispatcher.dispatchOnBackCancelled()}
            app.mainClock.advanceTimeBy(64)
            assertEquals(resting,form.getUnclippedBoundsInRoot())

            startBackGesture()
            app.mainClock.advanceTimeBy(32)
            confirmBackGesture()
            app.mainClock.advanceTimeBy(SceneMotion.HOME_EXIT_MS-timeToForm-96-64-32+144)
            val exiting=form.getUnclippedBoundsInRoot()
            assertTrue("The login form is still mounted and sliding right after commit",exiting.left.value>resting.left.value+1f)
            assertEquals(resting.top.value,exiting.top.value,.5f)
        } finally {
            app.mainClock.autoAdvance=true
            app.waitForIdle()
        }

        form.assertDoesNotExist()
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        app.onNodeWithTag("main-circle").assertContentDescriptionEquals("首页主圆")
    }

    @Test fun settledEdgeBackClosesRecoveryBeforeLeavingLogin() {
        avatar.performClick()
        app.onNodeWithTag("account-recovery-toggle").performScrollTo().performClick()
        app.onNodeWithTag("recovery-code").assertExists()

        startBackGesture()
        confirmBackGesture()
        app.onNodeWithTag("recovery-code").assertDoesNotExist()
        form.assertIsDisplayed()
        app.onNodeWithTag("main-circle").assertContentDescriptionEquals("我的主圆")

        startBackGesture()
        confirmBackGesture()
        form.assertDoesNotExist()
        app.onNodeWithTag("main-circle").assertContentDescriptionEquals("首页主圆")
    }

    private fun startBackGesture()=app.runOnIdle {
        val dispatcher=app.activity.onBackPressedDispatcher
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f,300f,0f,BackEventCompat.EDGE_LEFT))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(240f,300f,.65f,BackEventCompat.EDGE_LEFT))
    }

    private fun confirmBackGesture()=app.runOnIdle {app.activity.onBackPressedDispatcher.onBackPressed()}
}
