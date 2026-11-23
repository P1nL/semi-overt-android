package com.semiovert.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.LocalFlowPhase
import com.semiovert.app.core.designsystem.FlowPhase
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.homeArticleCoverColor
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.home.HomeScreen
import com.semiovert.app.feature.home.HomeSnapshotState
import com.semiovert.app.feature.home.HomeViewModel
import com.semiovert.app.feature.home.LocalHomeSnapshot
import com.semiovert.app.navigation.Destination
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated scenes preserve the installed preview account and draft storage. */
class HomeScreenTest {
    @get:Rule val app=createComposeRule()

    @Test fun homeControlsRemainAvailable() {
        val model=HomeViewModel()
        var destination: Destination?=null
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    HomeScreen({destination=it},{},model=model)
                }
            }
        }
        app.onNodeWithContentDescription("打开个人页").performClick()
        app.runOnIdle {assertEquals(Destination.PROFILE,destination)}
    }

    @Test fun homeDeckAndReducedMotionListUseTheSameSolidCovers() {
        val motion=mutableStateOf(true)
        val model=HomeViewModel()
        val colors=model.articles.indices.map(::homeArticleCoverColor)
        val swatches=setOf(
            Color(0xFFC33538),Color(0xFFEB641B),Color(0xFFF8961E),Color(0xFFE96149),Color(0xFFFFCF10),
            Color(0xFF158624),Color(0xFF2CB38B),Color(0xFF118480),Color(0xFF9946A9),Color(0xFF1F50D5),
        )
        assertEquals(swatches,colors.toSet())
        assertTrue(colors.indices.all {index->colors[index]!=colors[(index+1)%colors.size]})
        colors.forEach {color->
            val ink=SoPalette.inkOn(color).luminance()
            val fill=color.luminance()
            assertTrue((maxOf(ink,fill)+.05f)/(minOf(ink,fill)+.05f)>=4.5f)
        }
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides motion.value) {
                    HomeScreen({},{},model=model)
                }
            }
        }
        model.articles.take(2).forEachIndexed {index,article ->
            assertCoverBand("arc-cover-"+article.id,colors[index])
        }
        app.runOnIdle {motion.value=false}
        assertCoverBand("home-list-cover-"+model.articles.first().id,colors.first())
    }

    private fun assertCoverBand(tag: String,color: Color) {
        val pixels=app.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
        listOf(pixels.width/3,pixels.width*2/3).forEach {x ->
            assertEquals(tag,color.toArgb(),pixels[x,pixels.height/12].toArgb())
        }
    }

    @Test fun snapshotSurvivesAnImmediateDepartureWithMotionDisabled() {
        val snapshot=HomeSnapshotState()
        val model=HomeViewModel()
        val departed=mutableStateOf(false)
        app.setContent {
            val graphics=LocalGraphicsContext.current
            val scope=rememberCoroutineScope()
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false,LocalHomeSnapshot provides snapshot) {
                    if(!departed.value)HomeScreen({
                        scope.launch {snapshot.captureDeparture(graphics) {departed.value=true}}
                    },{},model=model)
                }
            }
        }
        app.runOnIdle {assertNull(snapshot.image)}
        app.onNodeWithContentDescription("打开个人页").performClick()
        app.waitUntil(timeoutMillis=5_000) {snapshot.image!=null}
        app.onNodeWithTag("feed-home").assertDoesNotExist()
        app.runOnIdle {
            assertTrue(snapshot.image!!.width>0)
            assertTrue(snapshot.image!!.height>0)
        }
    }

    @Test fun browsingAndThemeChangesKeepTheLastDepartureSnapshot() {
        val snapshot=HomeSnapshotState()
        val phase=mutableStateOf(FlowPhase.IDLE)
        val theme=mutableStateOf(ThemeMode.LIGHT)
        val model=HomeViewModel()
        var leave: ()->Unit={}
        app.setContent {
            val graphics=LocalGraphicsContext.current
            val scope=rememberCoroutineScope()
            SideEffect {leave={scope.launch {snapshot.captureDeparture(graphics) {phase.value=FlowPhase.EXIT}}}}
            SoTheme(theme.value) {
                CompositionLocalProvider(LocalMotionEnabled provides true,LocalHomeSnapshot provides snapshot,
                    LocalFlowPhase provides phase.value) {
                    HomeScreen({},{},model=model)
                }
            }
        }
        app.onNodeWithTag("feed-home").performScrollToIndex(4)
        app.runOnIdle {
            assertNull("Browsing must not capture a frame",snapshot.image)
            theme.value=ThemeMode.DARK
        }
        app.runOnIdle {
            assertNull("Changing the theme must not record the navigation preview",snapshot.image)
            leave()
        }
        app.waitUntil(5_000) {snapshot.image!=null}
        val first=snapshot.image!!
        app.runOnIdle {
            phase.value=FlowPhase.IDLE
            theme.value=ThemeMode.LIGHT
        }
        app.onNodeWithTag("feed-home").performScrollToIndex(7)
        app.runOnIdle {
            assertSame("Keep the previous snapshot until the next departure",first,snapshot.image)
            leave()
        }
        app.waitUntil(5_000) {snapshot.image!==first}
        app.runOnIdle {
            assertEquals(first.width,snapshot.image!!.width)
            assertEquals(first.height,snapshot.image!!.height)
        }
    }

}
