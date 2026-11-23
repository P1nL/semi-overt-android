package com.semiovert.app

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.designsystem.AppMotionDurationScale
import com.semiovert.app.core.designsystem.MotionEnvironment
import com.semiovert.app.core.designsystem.SoBottomSheet
import com.semiovert.app.core.designsystem.SoTheme
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMotionPolicyTest {
    // ComposeTestRule replaces the window factory with its test clock. Use the real
    // application factory here; deterministic duration checks live in the unit test.
    @Test fun activityAndSheetShareTheAppClockAfterRecreation() {
        val resolver=InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val systemScale=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            repeat(2) {round ->
                if(round>0)scenario.recreate()
                val finished=CountDownLatch(2)
                val samples=ConcurrentHashMap<String,Sample>()
                scenario.onActivity {activity ->
                    activity.setContent {
                        MotionEnvironment {
                            SoTheme {
                                ClockProbe("activity") {name,sample->samples[name]=sample;finished.countDown()}
                                SoBottomSheet(onDismissRequest={}) {
                                    ClockProbe("sheet") {name,sample->samples[name]=sample;finished.countDown()}
                                }
                            }
                        }
                    }
                }
                assertTrue("Both windows must inherit the app clock",finished.await(8,TimeUnit.SECONDS))
                for((name,sample) in samples) {
                    assertTrue("$name uses the app policy",sample.effectScale is AppMotionDurationScale)
                    assertSame("$name shares its effect and event clocks",sample.effectScale,sample.eventScale)
                    assertEquals(if(systemScale>0f)1f else 0f,sample.effectScale!!.scaleFactor,0f)
                }
                assertSame(samples.getValue("activity").effectScale,samples.getValue("sheet").effectScale)
            }
        }
        assertEquals("The app must not change the device setting",systemScale,
            Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f),0f)
    }

    private data class Sample(val effectScale: MotionDurationScale?,val eventScale: MotionDurationScale?)

    @Composable
    private fun ClockProbe(name: String,onFinished: (String,Sample)->Unit) {
        val eventScale=rememberCoroutineScope().coroutineContext[MotionDurationScale]
        LaunchedEffect(Unit) {
            val effectScale=coroutineContext[MotionDurationScale]
            onFinished(name,Sample(effectScale,eventScale))
        }
        Text(name)
    }
}
