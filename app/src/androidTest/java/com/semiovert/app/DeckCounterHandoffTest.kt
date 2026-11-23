package com.semiovert.app

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.LocalIntroReady
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.navigation.AppNavigation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeckCounterHandoffTest {
    @get:Rule val app=createComposeRule()

    @Test fun bothDirectionsRollToTheDestinationWithoutFadingTheRow() {
        val model=PreviewViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        val shortcuts=listOf(NavigationShortcut("category",.14f,.72f),NavigationShortcut("search",.36f,.72f))
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalIntroReady provides false) {
                    AppNavigation(PreviewState(shortcuts=shortcuts),model) {}
                }
            }
        }
        val counter=app.onNodeWithTag("deck-counter")
        fun PixelMap.ink(columns: IntRange=0 until width,maxRed: Float=0f): BooleanArray {
            val rows=minOf(height,(32f*app.density.density).toInt())
            val span=columns.last-columns.first+1
            return BooleanArray(rows*span) {index->this[columns.first+index%span,index/span].red<=maxRed}
        }
        fun assertRollAfterSwitch(route: String,sourceCount: String,targetCount: String) {
            counter.assertContentDescriptionEquals(sourceCount)
            app.waitForIdle()
            val bounds=counter.getUnclippedBoundsInRoot()
            val source=counter.captureToImage().toPixelMap()
            val center=source.width/2
            val slashColumns=(center-source.width/18) until (center+source.width/18)
            val slash=source.ink(slashColumns)
            assertTrue("The center slash must be visible",slash.any {it})
            fun frame(): PixelMap {
                app.onAllNodesWithTag("deck-counter").assertCountEquals(1)
                assertEquals(bounds,counter.getUnclippedBoundsInRoot())
                return counter.captureToImage().toPixelMap().also {
                    assertArrayEquals("The slash must stay unchanged entering $route",slash,it.ink(slashColumns))
                }
            }
            app.mainClock.autoAdvance=false
            try {
                app.onNodeWithTag("shortcut-"+route).performClick()
                val destination=app.onAllNodes(hasTestTag("deck-counter") and hasContentDescription(targetCount))
                app.waitUntil(5_000) {
                    app.mainClock.advanceTimeByFrame()
                    destination.fetchSemanticsNodes().isNotEmpty()
                }
                val rolling=listOf(80L,112L).map {elapsed ->
                    app.mainClock.advanceTimeBy(elapsed)
                    frame().ink(maxRed=.65f)
                }
                app.mainClock.advanceTimeBy(512L)
                frame()
                app.mainClock.advanceTimeBy(1000L)
                val target=frame().ink(maxRed=.65f)
                counter.assertContentDescriptionEquals(targetCount)
                val before=source.ink(maxRed=.65f)
                assertTrue("Digits must roll through intermediate positions entering $route",
                    rolling.any {sample->sample.indices.any {sample[it]&&!before[it]&&!target[it]}})
            } finally {app.mainClock.autoAdvance=true}
        }
        app.onNodeWithTag("shortcut-category").performClick()
        app.onNodeWithTag("feed-category").performScrollToIndex(1)
        val categorySize=SampleLibrary.inDuration(ReadingDuration.QUICK).size
        val categoryCount="第 2 篇，共 $categorySize 篇"
        assertRollAfterSwitch("search",categoryCount,"第 0 篇，共 0 篇")

        val keyword="林间"
        val search=app.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("search-input")),useUnmergedTree=true)
        search.performTextInput(keyword)
        search.performImeAction()
        app.onNodeWithTag("search-wave").assertExists()
        val searchSize=SampleLibrary.searchArticles(keyword).size
        assertRollAfterSwitch("category","第 1 篇，共 $searchSize 篇",categoryCount)

        app.onNodeWithTag("feed-category").performScrollToIndex(2)
        counter.assertContentDescriptionEquals("第 3 篇，共 $categorySize 篇")
    }
}
