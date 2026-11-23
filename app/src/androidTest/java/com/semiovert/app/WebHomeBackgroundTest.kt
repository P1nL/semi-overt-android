package com.semiovert.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.core.app.ApplicationProvider
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.HomeBackgroundWebView
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.IntroPreparation
import com.semiovert.app.core.designsystem.BrandIntroMotion
import com.semiovert.app.core.designsystem.LocalIntroPreparation
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.ThemeRevealHost
import com.semiovert.app.core.designsystem.WebHomeBackground
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.navigation.AppNavigation
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.circleColors
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise the packaged web renderer without changing the account or draft storage. */
class WebHomeBackgroundTest {
    @get:Rule val app=createComposeRule()

    private fun withRenderer(action: (HomeBackgroundWebView)->Unit) {
        onView(isAssignableFrom(HomeBackgroundWebView::class.java)).check {view,error->
            if(error!=null)throw error
            action(view as HomeBackgroundWebView)
        }
    }

    private fun eval(script: String): String {
        val result=AtomicReference<String?>()
        withRenderer {it.evaluateJavascript(script) {value->result.set(value)}}
        app.waitUntil(15_000) {result.get()!=null}
        return checkNotNull(result.get())
    }

    private fun state()=JSONObject(JSONTokener(eval(
        "JSON.stringify(window.SemiWebBackground?.getState() ?? {})"
    )).nextValue().toString())

    private fun waitForPlayback() {
        app.waitUntil(15_000) {state().optBoolean("ready")&&state().optBoolean("playing")}
    }

    @Test fun coldStartPreparesOnePausedWebViewBeforeHomeReveal() {
        val model=PreviewViewModel(ApplicationProvider.getApplicationContext<Application>())
        val preview=PreviewState(theme=ThemeMode.LIGHT)
        val background=app.onNodeWithTag("home-web-background",useUnmergedTree=true)
        var preparation: IntroPreparation?=null
        lateinit var preparedView: HomeBackgroundWebView
        app.mainClock.autoAdvance=false
        try {
            app.setContent {
                SoTheme(preview.theme) {
                    ThemeRevealHost {
                        IdentityIntroHost(false,0) {
                            val intro=LocalIntroPreparation.current
                            SideEffect {preparation=intro}
                            AppNavigation(preview,model) {}
                        }
                    }
                }
            }
            app.waitUntil(15_000) {
                app.mainClock.advanceTimeByFrame()
                app.onAllNodesWithTag("home-web-background",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
            withRenderer {preparedView=it}
            app.waitUntil(15_000) {
                app.mainClock.advanceTimeByFrame()
                app.runOnIdle {preparation?.backgroundReady==true}
            }
            app.onNodeWithTag("identity-intro").assertExists()
            state().also {
                assertTrue(it.getBoolean("ready"))
                assertFalse(it.getBoolean("active"))
                assertFalse(it.getBoolean("playing"))
            }
            app.mainClock.advanceTimeBy(BrandIntroMotion.TOTAL_MILLIS+128L)
            app.onNodeWithTag("identity-intro").assertDoesNotExist()
        } finally {app.mainClock.autoAdvance=true}
        app.waitForIdle()
        background.assertExists()
        waitForPlayback()
        withRenderer {assertSame(preparedView,it)}
    }

    @Test fun orangeMeshAnimatesInBothThemesAndPassesThroughTouches() {
        var taps=0
        var theme by mutableStateOf(ThemeMode.LIGHT)
        app.setContent {
            SoTheme(theme) {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .clickable {taps++}.testTag("web-background-host")) {
                        WebHomeBackground()
                    }
                }
            }
        }
        waitForPlayback()
        val initial=state()
        val viewport=initial.getJSONArray("viewport")
        val artboard=initial.getJSONArray("artboard")
        assertEquals(viewport.getDouble(0)*.90,artboard.getDouble(2),.01)
        assertEquals(viewport.getDouble(1)*.80,artboard.getDouble(3),.01)
        assertEquals(viewport.getDouble(0)/2,artboard.getDouble(0)+artboard.getDouble(2)/2,.01)
        assertEquals(viewport.getDouble(1)/2,artboard.getDouble(1)+artboard.getDouble(3)/2,.01)
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            app.runOnIdle {theme=mode}
            val background=app.onNodeWithTag("home-web-background",useUnmergedTree=true)
            val first=background.captureToImage()
            val firstTime=state().getDouble("time")
            app.waitUntil(10_000) {state().getDouble("time")>firstTime+.35}
            val second=background.captureToImage()
            assertTrue("Orange lines must animate in $mode",changedInkPixels(first,second)>100)
        }
        eval("window.dispatchEvent(new PointerEvent('pointermove',{clientX:180,clientY:320,pointerType:'mouse'}));true")
        assertEquals(0.0,state().getDouble("pointerStrength"),0.0)
        app.onNodeWithTag("web-background-host").performTouchInput {click()}
        app.runOnIdle {assertEquals("The decoration must not intercept native input",1,taps)}
    }

    @Test fun pausedTimeIsNotAddedToTheMotionAndReducedMotionIsStatic() {
        var active by mutableStateOf(true)
        var motion by mutableStateOf(true)
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides motion) {
                    WebHomeBackground(active=active)
                }
            }
        }
        waitForPlayback()
        app.waitUntil(10_000) {state().getDouble("time")>.15}
        app.runOnIdle {active=false}
        app.waitUntil(10_000) {!state().optBoolean("active")&&!state().optBoolean("playing")}
        val pausedTime=state().getDouble("time")
        SystemClock.sleep(750)
        assertEquals(pausedTime,state().getDouble("time"),0.0)
        val resumeRequested=eval("performance.now()").toDouble()
        app.runOnIdle {active=true}
        var resumedTime=pausedTime
        app.waitUntil(10_000) {
            resumedTime=state().getDouble("time")
            resumedTime>pausedTime
        }
        val sinceResume=(eval("performance.now()").toDouble()-resumeRequested)*.001
        assertTrue("The hidden interval must not advance shader time",resumedTime-pausedTime<=sinceResume+.05)
        app.runOnIdle {motion=false}
        app.waitUntil(10_000) {!state().optBoolean("motionEnabled")&&!state().optBoolean("playing")}
        assertEquals("Reduced motion renders a static frame",0.0,state().getDouble("time"),0.0)
    }

    @Test fun profileCalendarDoesNotBlurTheBackgroundWhileOpeningOrAtRest() {
        startNavigation(mutableStateOf(PreviewState(signedIn=true,theme=ThemeMode.LIGHT)))
        navigate("我的")
        fun blurHeight()=eval("document.getElementById('records-backdrop')?.getBoundingClientRect().height ?? 0").toDouble()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("records-expand").performClick()
            app.mainClock.advanceTimeBy(180)
            app.onNodeWithTag("record-calendar-grid").assertIsDisplayed()
            assertEquals(0.0,blurHeight(),0.0)
            app.mainClock.advanceTimeBy(SceneMotion.DRAWER_MS.toLong())
            app.onNodeWithTag("record-day-16").assertIsDisplayed()
            assertEquals(0.0,blurHeight(),0.0)
        } finally {app.mainClock.autoAdvance=true}
    }

    private fun startNavigation(preview: State<PreviewState>) {
        val model=PreviewViewModel(ApplicationProvider.getApplicationContext<Application>())
        app.setContent {
            SoTheme(preview.value.theme) {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    ThemeRevealHost {AppNavigation(preview.value,model) {}}
                }
            }
        }
        waitForPlayback()
    }

    private fun navigate(label: String) {
        val action=app.onNodeWithTag("main-circle").fetchSemanticsNode().config[SemanticsActions.CustomActions].first {it.label=="前往$label"}
        app.runOnUiThread {action.action()}
        app.waitForIdle()
    }

    @Test fun browsePagesShareTheRendererAndSettingsAndLoginPauseIt() {
        val preview=mutableStateOf(PreviewState(signedIn=true,theme=ThemeMode.LIGHT))
        startNavigation(preview)
        lateinit var original: HomeBackgroundWebView
        withRenderer {original=it}
        for((label,tag) in listOf("分类" to "feed-category","搜索" to "search-input","我的" to "my-profile-feed")) {
            navigate(label)
            app.onNodeWithTag(tag).assertIsDisplayed()
            assertTrue("$label keeps playback active",state().getBoolean("playing"))
            withRenderer {assertSame(original,it)}
        }
        val circle=app.onNodeWithTag("main-circle").fetchSemanticsNode().boundsInRoot
        val circleColor=Destination.PROFILE.circleColors.background
        val sampleX=(circle.center.x-circle.width*.36f).toInt()
        val sampleY=circle.center.y.toInt()
        assertEquals(circleColor.toArgb(),app.onRoot().captureToImage().toPixelMap()[sampleX,sampleY].toArgb())
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithContentDescription("设置").performClick()
            app.mainClock.advanceTimeBy(240)
            val sheet=app.onNodeWithTag("settings-screen").fetchSemanticsNode().boundsInRoot
            assertTrue(sheet.left>1f&&sheet.left<circle.left)
            val color=app.onRoot().captureToImage().toPixelMap()[sampleX,sampleY]
            assertNotEquals("The sliding page covers the previous main circle",circleColor.toArgb(),color.toArgb())
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("settings-screen").assertIsDisplayed()
        val settingsTime=assertHiddenPausedFrame(original)
        pressBack()
        app.waitUntil(10_000) {state().getDouble("time")>settingsTime}
        app.runOnIdle {preview.value=preview.value.copy(signedIn=false)}
        app.onNodeWithTag("login-screen").assertIsDisplayed()
        val pausedTime=assertHiddenPausedFrame(original)
        navigate("首页")
        app.waitUntil(10_000) {state().getDouble("time")>pausedTime}
        withRenderer {assertSame(original,it)}
    }

    @Test fun homeReadingHidesTheStoppedFrameAndResumesAfterReturning() {
        startNavigation(mutableStateOf(PreviewState(theme=ThemeMode.LIGHT)))
        lateinit var original: HomeBackgroundWebView
        withRenderer {original=it}
        fun openCard() {app.onNodeWithTag("article-forest").performSemanticsAction(SemanticsActions.OnClick) {it()}}
        openCard()
        app.waitForIdle()
        openCard()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        val pausedTime=assertHiddenPausedFrame(original)
        app.mainClock.autoAdvance=false
        try {
            pressBack()
            app.mainClock.advanceTimeBy(240)
            val progress=app.onNodeWithTag("reading-expansion").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue(progress<1f&&progress>0f)
            assertEquals(pausedTime,state().getDouble("time"),0.0)
        } finally {app.mainClock.autoAdvance=true}
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        app.waitUntil(10_000) {state().getDouble("time")>pausedTime}
        withRenderer {assertSame(original,it)}
    }

    private fun assertHiddenPausedFrame(renderer: HomeBackgroundWebView): Double {
        app.waitUntil(10_000) {!state().optBoolean("playing")}
        withRenderer {assertSame(renderer,it)}
        val time=state().getDouble("time")
        val pixels=app.onRoot().captureToImage().toPixelMap()
        val orange=SoPalette.AutumnLeaf.toArgb()
        var inkCount=0
        for(y in pixels.height/4 until pixels.height*3/4 step 2) {
            for(x in 0 until pixels.width step 2)if(pixels[x,y].toArgb()==orange)inkCount++
        }
        assertEquals("The scene is retained in the renderer, not drawn as the page background",0,inkCount)
        SystemClock.sleep(180)
        assertEquals(time,state().getDouble("time"),0.0)
        return time
    }

    private fun changedInkPixels(first: ImageBitmap,second: ImageBitmap): Int {
        val before=first.toPixelMap()
        val after=second.toPixelMap()
        val orange=SoPalette.AutumnLeaf.toArgb()
        var changed=0
        for(y in first.height/5 until first.height*4/5)for(x in 0 until first.width) {
            val oldInk=before[x,y].toArgb()==orange
            val newInk=after[x,y].toArgb()==orange
            if(oldInk!=newInk)changed++
        }
        return changed
    }
}
