package com.semiovert.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.PageHandoff
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Navigation-only regression: preserve the user's offline identity and preferences. */
@RunWith(AndroidJUnit4::class)
class ProfileSceneMotionTest {
    @get:Rule val app=createAndroidComposeRule<MainActivity>()

    private fun menu() {
        app.waitForIdle()
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
        app.waitForIdle()
    }

    @Test fun profileOverlapsExitAndReplaysEntryAfterPrimaryNavigation() {
        menu()
        app.onNodeWithContentDescription("切换到我的").performClick()
        app.waitForIdle()
        app.onNodeWithTag("profile-scene").assertExists()
        val signedIn=app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getBoolean("demo_signed_in",false)
        val contentTag=if(signedIn)"profile-info" else "login-screen"
        val resting=app.onNodeWithTag(contentTag,useUnmergedTree=true).getUnclippedBoundsInRoot()

        menu()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("切换到首页").performClick()
            // Page navigation starts after the accessible menu's 400ms dismissal.
            app.mainClock.advanceTimeBy(SceneMotion.DRAWER_MS+64L)
            app.mainClock.advanceTimeBy(200)
            app.onNodeWithTag("profile-scene").assertExists()
            // Both routes are mounted; the incoming scene waits in its hidden pose.
            app.onNodeWithTag("feed-home").assertExists()
            app.mainClock.advanceTimeBy(SceneMotion.PROFILE_EXIT_MS+64L)
            app.onNodeWithTag("feed-home").assertExists()
        } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}

        menu()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("切换到我的").performClick()
            app.mainClock.advanceTimeBy(SceneMotion.DRAWER_MS+64L)
            val handoff=PageHandoff(Destination.HOME,Destination.PROFILE,profileGuest=!signedIn)
            val halfway=handoff.enterMillis/2L
            app.mainClock.advanceTimeBy(handoff.enterDelayMillis+halfway)
            app.onNodeWithTag("profile-scene").assertExists()
            val entering=app.onNodeWithTag(contentTag,useUnmergedTree=true).getUnclippedBoundsInRoot()
            if(signedIn)assertTrue("Restored profile text enters from the right",entering.left.value>resting.left.value+1f)
            else assertTrue("Restored login form enters from below",entering.top.value>resting.top.value+1f)
            app.mainClock.advanceTimeBy(halfway+32L)
            assertTrue("Profile content settles on its shortened entrance clock",
                app.onNodeWithTag(contentTag,useUnmergedTree=true).getUnclippedBoundsInRoot()==resting)
        } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
    }
}
