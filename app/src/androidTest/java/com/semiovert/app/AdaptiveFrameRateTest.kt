package com.semiovert.app

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.feature.editor.ArticleEditorScreen
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real window frame clocks: ComposeTestRule replaces the application factory with a test clock. */
@RunWith(AndroidJUnit4::class)
class AdaptiveFrameRateTest {
    @get:Rule val preferences=PreviewPreferencesRule()

    @Test fun homeReleasesHighRefreshWhileItsWebBackgroundKeepsPlaying() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitForRate(scenario,0f,timeout=15_000)
            Thread.sleep(650)
            assertRate(scenario,0f)
            val ready=CountDownLatch(1)
            val playing=AtomicReference<String>()
            scenario.onActivity {activity ->
                val background=findBackground(activity.window.decorView)
                assertNotNull("The real home background must be mounted",background)
                background!!.evaluateJavascript("window.SemiWebBackground.getState().playing") {
                    playing.set(it);ready.countDown()
                }
            }
            assertTrue(ready.await(3,TimeUnit.SECONDS))
            assertEquals("The 30fps canvas must keep playing at rest","true",playing.get())
            assertRate(scenario,0f)
        }
    }

    @Test fun heldTouchAnimationAndRecreationPreserveTheAdaptiveRequest() {
        val animate=mutableStateOf(false)
        val finished=CountDownLatch(1)
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                activity.setContent {MotionProbe(animate.value) {finished.countDown()}}
            }
            waitForRate(scenario,0f)
            dispatchTouch(scenario,MotionEvent.ACTION_DOWN)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            Thread.sleep(550)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            dispatchTouch(scenario,MotionEvent.ACTION_CANCEL)
            Thread.sleep(150)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            waitForRate(scenario,0f)

            scenario.onActivity {animate.value=true}
            waitForRate(scenario,MotionFramePolicy.TARGET_FPS)
            Thread.sleep(600)
            assertEquals("The animation is still running",1L,finished.count)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            assertTrue(finished.await(4,TimeUnit.SECONDS))
            waitForRate(scenario,0f)

            scenario.recreate()
            dispatchTouch(scenario,MotionEvent.ACTION_DOWN)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            dispatchTouch(scenario,MotionEvent.ACTION_CANCEL)
            waitForRate(scenario,0f,timeout=15_000)
        }
    }

    @Test fun sheetCanSettleAndCloseWithoutLoweringAnActiveActivity() {
        val showSheet=mutableStateOf(false)
        val animate=mutableStateOf(false)
        val sheetWindow=AtomicReference<Window>()
        val finished=CountDownLatch(1)
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                activity.setContent {
                    SoTheme {
                        MotionProbe(animate.value) {finished.countDown()}
                        if(showSheet.value)SoBottomSheet(onDismissRequest={showSheet.value=false}) {
                            val window=(LocalView.current.parent as DialogWindowProvider).window
                            SideEffect {sheetWindow.set(window)}
                            Text("帧率检查")
                        }
                    }
                }
            }
            waitForRate(scenario,0f)
            scenario.onActivity {showSheet.value=true}
            waitUntil {
                var high=false
                scenario.onActivity {high=sheetWindow.get()?.attributes?.preferredRefreshRate==MotionFramePolicy.TARGET_FPS}
                high
            }
            waitForRate(scenario,0f)
            scenario.onActivity {assertEquals(0f,sheetWindow.get().attributes.preferredRefreshRate,0f)}

            scenario.onActivity {animate.value=true}
            waitForRate(scenario,MotionFramePolicy.TARGET_FPS)
            scenario.onActivity {
                assertEquals(MotionFramePolicy.TARGET_FPS,sheetWindow.get().attributes.preferredRefreshRate,0f)
                showSheet.value=false
            }
            Thread.sleep(600)
            assertEquals(1L,finished.count)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            assertTrue(finished.await(4,TimeUnit.SECONDS))
            waitForRate(scenario,0f)
        }
    }

    @Test fun editorDomAnimationsAndInnerScrollingKeepHighRefreshUntilTheyFinish() {
        val ready=CountDownLatch(1)
        val storageKey="adaptive-frame-test-"+UUID.randomUUID()
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                activity.setContent {
                    SoTheme {
                        ArticleEditorScreen({},storageKey=storageKey,onReady={ready.countDown()})
                    }
                }
            }
            assertTrue("Editor must be ready",ready.await(15,TimeUnit.SECONDS))
            waitForRate(scenario,0f)
            eval(scenario,"""
                (() => {
                  const element = document.querySelector('#rich-editor');
                  element.animate([{opacity: 1}, {opacity: .8}], {duration: 1200});
                  element.dispatchEvent(new Event('click', {bubbles: true}));
                  return true;
                })()
            """.trimIndent())
            waitForRate(scenario,MotionFramePolicy.TARGET_FPS)
            Thread.sleep(600)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            waitForRate(scenario,0f)

            eval(scenario,"editor.commands.setContent('<p>滚动帧率检查</p>'.repeat(100)); true")
            waitForRate(scenario,0f)
            eval(scenario,"""
                (() => {
                  const element = document.querySelector('#rich-editor');
                  let ticks = 0;
                  const timer = setInterval(() => {
                    element.scrollTop += 30;
                    if (++ticks === 15) clearInterval(timer);
                  }, 80);
                  return true;
                })()
            """.trimIndent())
            waitForRate(scenario,MotionFramePolicy.TARGET_FPS)
            Thread.sleep(600)
            assertRate(scenario,MotionFramePolicy.TARGET_FPS)
            waitForRate(scenario,0f)
            assertEquals("true",eval(scenario,"document.querySelector('#rich-editor').scrollTop > 0"))
        }
    }

    @Composable
    private fun MotionProbe(start: Boolean,onFinished: ()->Unit) {
        val progress=remember {Animatable(0f)}
        LaunchedEffect(start) {
            if(start) {
                progress.animateTo(1f,tween(1_200))
                onFinished()
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer {translationX=progress.value*20f}) {Text("前景动画")}
    }

    private fun dispatchTouch(scenario: ActivityScenario<MainActivity>,action: Int) {
        scenario.onActivity {activity ->
            val now=SystemClock.uptimeMillis()
            val event=MotionEvent.obtain(now,now,action,40f,40f,0)
            activity.window.callback.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun assertRate(scenario: ActivityScenario<MainActivity>,rate: Float) {
        scenario.onActivity {assertEquals(rate,it.window.attributes.preferredRefreshRate,0f)}
    }

    private fun waitForRate(scenario: ActivityScenario<MainActivity>,rate: Float,timeout: Long=6_000) =
        waitUntil(timeout) {
            var actual=-1f
            scenario.onActivity {actual=it.window.attributes.preferredRefreshRate}
            actual==rate
        }

    private fun waitUntil(timeout: Long=6_000,predicate: ()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) {
            if(predicate())return
            Thread.sleep(25)
        }
        assertTrue("The requested frame rate did not settle within ${timeout}ms",predicate())
    }

    private fun findBackground(view: View): HomeBackgroundWebView? {
        if(view is HomeBackgroundWebView)return view
        if(view is ViewGroup)for(index in 0 until view.childCount)findBackground(view.getChildAt(index))?.let {return it}
        return null
    }

    private fun eval(scenario: ActivityScenario<MainActivity>,script: String): String {
        val completed=CountDownLatch(1)
        val result=AtomicReference<String>()
        fun findWeb(view: View): WebView? {
            if(view is WebView)return view
            if(view is ViewGroup)for(index in 0 until view.childCount)findWeb(view.getChildAt(index))?.let {return it}
            return null
        }
        scenario.onActivity {activity ->
            checkNotNull(findWeb(activity.window.decorView)).evaluateJavascript(script) {
                result.set(it);completed.countDown()
            }
        }
        assertTrue(completed.await(3,TimeUnit.SECONDS))
        return result.get()
    }
}
