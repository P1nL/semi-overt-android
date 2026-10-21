package com.semiovert.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PageTransitionTest {
    @get:Rule val app=createComposeRule()
    private lateinit var controller: NavHostController
    private var handoff by mutableStateOf<PageHandoff?>(null)
    private val samples=mutableMapOf<Destination,Pair<Float,FlowPhase>>()

    private fun content(motion: Boolean=true) {
        app.setContent {
            CompositionLocalProvider(LocalMotionEnabled provides motion) {
                controller=rememberNavController()
                NavHost(controller,Destination.HOME.route,
                    enterTransition={pageEnter(motion)},exitTransition={pageExit(motion)},
                    popEnterTransition={pageEnter(motion,true)},popExitTransition={pageExit(motion,true)}) {
                    Destination.entries.forEach {page ->
                        composable(page.route) {
                            SceneMotionContent(page,handoff) {
                                val hidden=LocalFlowRoll.current
                                val phase=LocalFlowPhase.current
                                SideEffect {samples[page]=hidden to phase}
                                DisposableEffect(page) {onDispose {samples.remove(page)}}
                                Box(Modifier.fillMaxSize().testTag(page.route)) {Text(page.label)}
                            }
                        }
                    }
                    composable("settings") {Box(Modifier.fillMaxSize().testTag("settings")) {Text("设置")}}
                    composable("reader/{articleId}") {Box(Modifier.fillMaxSize().testTag("reader")) {Text("阅读")}}
                }
            }
        }
        app.waitForIdle()
    }

    @Test fun scenesOverlapWithIndependentProgressAndStayAliveUntilFinished() {
        content()
        app.mainClock.autoAdvance=false
        try {
            app.runOnIdle {
                handoff=PageHandoff(Destination.HOME,Destination.CATEGORY)
                controller.navigate(Destination.CATEGORY.route)
            }
            app.mainClock.advanceTimeBy(400)
            app.runOnIdle {
                assertTrue(samples.getValue(Destination.HOME).first in .3f.. .6f)
                assertEquals(1f,samples.getValue(Destination.CATEGORY).first,.001f)
            }
            app.mainClock.advanceTimeBy(350)
            app.runOnIdle {
                val old=samples.getValue(Destination.HOME)
                val next=samples.getValue(Destination.CATEGORY)
                assertEquals(FlowPhase.EXIT,old.second)
                assertEquals(FlowPhase.ENTER,next.second)
                assertTrue(old.first in .7f.. .95f)
                assertTrue(next.first in .65f.. .85f)
                assertNotEquals(old.first,next.first)
                assertNotEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry!!.lifecycle.currentState)
            }
            app.mainClock.advanceTimeBy(1000)
            app.runOnIdle {
                assertFalse(samples.containsKey(Destination.HOME))
                assertEquals(0f,samples.getValue(Destination.CATEGORY).first,.001f)
                assertEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry!!.lifecycle.currentState)
            }
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun secondaryPushAndPopMoveBothPagesWithoutReplayingSceneEntrance() {
        content()
        app.mainClock.autoAdvance=false
        try {
            app.runOnIdle {controller.navigate("settings")}
            app.mainClock.advanceTimeBy(100)
            assertTrue(app.onNodeWithTag("home").getUnclippedBoundsInRoot().left.value<0)
            assertTrue(app.onNodeWithTag("settings").getUnclippedBoundsInRoot().left.value>0)
            app.runOnIdle {assertEquals(0f,samples.getValue(Destination.HOME).first,.001f)}
            app.mainClock.advanceTimeBy(300)
            // Both directions must still be moving at 400ms, rather than finishing at the old 320ms.
            assertTrue(app.onNodeWithTag("settings").getUnclippedBoundsInRoot().left.value>0f)
            app.runOnIdle {assertNotEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry!!.lifecycle.currentState)}
            app.mainClock.advanceTimeBy(200)
            app.runOnIdle {controller.popBackStack()}
            app.mainClock.advanceTimeBy(100)
            assertTrue(app.onNodeWithTag("home").getUnclippedBoundsInRoot().left.value<0)
            assertTrue(app.onNodeWithTag("settings").getUnclippedBoundsInRoot().left.value>0)
            app.mainClock.advanceTimeBy(300)
            app.onNodeWithTag("settings").assertExists()
            app.runOnIdle {assertNotEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry!!.lifecycle.currentState)}
            app.mainClock.advanceTimeBy(200)
            app.runOnIdle {assertEquals(0f,samples.getValue(Destination.HOME).first,.001f)}
            app.onNodeWithTag("home").assertIsDisplayed()
            // The shared reader stays still, but a secondary page opened above it still slides.
            app.runOnIdle {controller.navigate("reader/forest")}
            app.mainClock.advanceTimeBy(400)
            app.runOnIdle {controller.navigate("settings")}
            app.mainClock.advanceTimeBy(100)
            assertEquals(0f,app.onNodeWithTag("reader").getUnclippedBoundsInRoot().left.value,.01f)
            assertTrue(app.onNodeWithTag("settings").getUnclippedBoundsInRoot().left.value>0)
            app.mainClock.advanceTimeBy(SIDE_PAGE_MS.toLong())
            app.runOnIdle {controller.popBackStack()}
            app.mainClock.advanceTimeBy(100)
            assertEquals(0f,app.onNodeWithTag("reader").getUnclippedBoundsInRoot().left.value,.01f)
            assertTrue(app.onNodeWithTag("settings").getUnclippedBoundsInRoot().left.value>0)
            app.mainClock.advanceTimeBy(SIDE_PAGE_MS.toLong())
            app.onNodeWithTag("reader").assertIsDisplayed()
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun disabledMotionDoesNotWaitForTheHandoffOrLeaveHiddenContent() {
        content(motion=false)
        app.runOnIdle {
            handoff=PageHandoff(Destination.HOME,Destination.PROFILE)
            controller.navigate(Destination.PROFILE.route)
        }
        app.waitForIdle()
        app.runOnIdle {
            assertEquals(0f,samples.getValue(Destination.PROFILE).first,.001f)
            assertEquals(FlowPhase.IDLE,samples.getValue(Destination.PROFILE).second)
            assertEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry!!.lifecycle.currentState)
        }
        app.onNodeWithTag("home").assertDoesNotExist()
        app.onNodeWithTag("profile").assertIsDisplayed()
    }
}
