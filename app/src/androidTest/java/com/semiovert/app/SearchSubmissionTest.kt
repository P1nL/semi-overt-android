package com.semiovert.app

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.DeckCounterHost
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.search.SearchViewModel
import com.semiovert.app.feature.search.SearchScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchSubmissionTest {
    @get:Rule val app=createComposeRule()
    private fun start(model: SearchViewModel) {
        app.setContent {SoTheme(ThemeMode.LIGHT) {
            CompositionLocalProvider(LocalMotionEnabled provides true) {
                DeckCounterHost("search",SampleLibrary.articles.size) {SearchScreen({},{},{},model)}
            }
        }}
        app.waitForIdle()
    }

    @Test fun clearButtonExitsAndResetsWithoutImeAndDoesNotReplayWhenOnlyDraftRemains() {
        val model=SearchViewModel(SavedStateHandle())
        start(model)
        app.onNodeWithTag("search-input").performTextInput("林间")
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("search-input").performImeAction()
            app.mainClock.advanceTimeBy(160)
            app.onNodeWithContentDescription("清空搜索").assertIsNotEnabled()
            app.mainClock.advanceTimeBy(650)
            val resting=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot()
            app.onNodeWithContentDescription("清空搜索").performClick()
            assertEquals("",model.query.value)
            app.mainClock.advanceTimeBy(200)
            assertEquals("Old cards stay mounted until their exit finishes","林间",model.committedQuery.value)
            val leaving=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertTrue("Clear uses the downward exit",leaving.top.value>resting.top.value+10)
            app.onNodeWithContentDescription("清空搜索").assertIsNotEnabled()
            app.mainClock.advanceTimeBy(800)
            app.onNodeWithTag("search-wave").assertDoesNotExist()
            app.onNodeWithTag("deck-counter").assertContentDescriptionEquals("第 0 篇，共 0 篇")
            app.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("0")
            app.onNodeWithContentDescription("清空搜索").assertDoesNotExist()
            app.onNodeWithTag("search-input").performTextInput("草稿")
            app.mainClock.advanceTimeByFrame()
            app.onNodeWithContentDescription("清空搜索").performClick()
            assertEquals("",model.query.value)
            assertFalse("Clearing only a draft starts no result animation",model.submitting.value)
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun initialZeroCountsAndDraftDoNotCreateCardsUntilImeConfirmation() {
        val model=SearchViewModel(SavedStateHandle())
        start(model)
        app.onNodeWithTag("deck-counter").assertContentDescriptionEquals("第 0 篇，共 0 篇")
        app.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("0")
        app.onNodeWithTag("search-wave").assertDoesNotExist()
        app.onNodeWithTag("search-input").performTextInput("林间")
        app.onNodeWithTag("search-wave").assertDoesNotExist()
        app.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("0")
        assertEquals("",model.committedQuery.value)
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("search-input").performImeAction()
            app.mainClock.advanceTimeBy(160)
            val entering=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot()
            repeat(12) {app.onNodeWithTag("search-input").performImeAction()}
            assertEquals("林间",model.committedQuery.value)
            app.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("1")
            app.mainClock.advanceTimeBy(650)
            val settled=app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertTrue("New cards use the category's upward entrance",entering.top.value>settled.top.value+10)
            assertFalse(model.submitting.value)
            val count=SampleLibrary.searchArticles("林间").size
            app.onNodeWithTag("deck-counter").assertContentDescriptionEquals("第 1 篇，共 $count 篇")
            app.onNodeWithTag("search-input").performImeAction()
            app.mainClock.advanceTimeBy(160)
            assertEquals(settled,app.onNodeWithTag("wave-card-forest",useUnmergedTree=true).getUnclippedBoundsInRoot())
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun newConfirmationExitsOldQueueBeforeCommittingAndBlankConfirmationReturnsToZero() {
        val model=SearchViewModel(SavedStateHandle(mapOf("query" to "林间","committed-query" to "林间")))
        start(model)
        app.onNodeWithTag("search-input").performTextReplacement("街角")
        assertEquals("林间",model.committedQuery.value)
        app.onNodeWithTag("article-forest").assertExists()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("search-input").performImeAction()
            app.mainClock.advanceTimeBy(200)
            assertEquals("Old data stays mounted through exit", "林间",model.committedQuery.value)
            app.onNodeWithTag("article-street").assertDoesNotExist()
            app.onNodeWithTag("search-input").performTextReplacement("不应排队执行")
            repeat(12) {app.onNodeWithTag("search-input").performImeAction()}
            app.mainClock.advanceTimeBy(1100)
            assertEquals("Only the accepted query is committed","街角",model.committedQuery.value)
            assertFalse("The completed result swap releases the submission lock",model.submitting.value)
            app.onNodeWithTag("article-street").assertExists()
            app.onNodeWithTag("article-forest").assertDoesNotExist()
            app.onNodeWithTag("search-input").performTextReplacement("")
            app.onNodeWithTag("article-street").assertExists()
            assertEquals("",model.query.value)
            val clearAfter=SystemClock.elapsedRealtime()+1200
            app.waitUntil(2500) {SystemClock.elapsedRealtime()>=clearAfter}
            app.onNodeWithTag("search-input").performImeAction()
            assertTrue("A confirmed clear is accepted after cooldown",model.submitting.value)
            app.mainClock.advanceTimeByFrame()
            app.mainClock.advanceTimeBy(800)
            assertEquals("",model.committedQuery.value)
            app.onNodeWithTag("search-wave").assertDoesNotExist()
            app.onNodeWithTag("deck-counter").assertContentDescriptionEquals("第 0 篇，共 0 篇")
            app.onNodeWithTag("search-author-count",useUnmergedTree=true).assertTextEquals("0")
        } finally {app.mainClock.autoAdvance=true}
    }
}
