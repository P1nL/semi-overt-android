package com.semiovert.app

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.feature.editor.*
import com.semiovert.app.navigation.DraftBoxBubble
import com.semiovert.app.navigation.DraftBoxState
import androidx.compose.ui.geometry.Offset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftBoxEditorIntegrationTest {
    @get:Rule val app = createComposeRule()

    private fun eval(script: String): String {
        val result = AtomicReference<String?>()
        onView(isAssignableFrom(WebView::class.java)).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript(script) { result.set(it) }
        }
        app.waitUntil(10_000) { result.get() != null }
        return checkNotNull(result.get())
    }

    @Test fun coldDraftListReadsExistingStorageAndContinuesTheSameDocument() {
        val storageKey = "draft-box-test-" + UUID.randomUUID()
        val ready = AtomicInteger()
        val open = mutableStateOf(true)
        val generation = mutableIntStateOf(0)
        lateinit var session: ArticleEditorSession
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    val box = remember { DraftBoxState() }
                    key(generation.intValue) {
                        session = rememberArticleEditorSession(storageKey)
                        Box(Modifier.fillMaxSize()) {
                            Button(onClick = { box.open(Offset(160f, 800f)) }) { Text("打开草稿箱") }
                            DraftBoxBubble(box, session.drafts, session.draftsLoading, session.draftsFailed,
                                { session.loadDrafts(EditorPalette.forTheme(false)) },
                                { draft -> session.selectDraft(draft); open.value = true })
                            if (open.value) ArticleEditorScreen(
                                onBack = { open.value = false }, session = session, onReady = { ready.incrementAndGet() })
                        }
                    }
                }
            }
        }
        app.waitUntil(20_000) { ready.get() == 1 }
        eval("""
            editor.commands.setContent('<p>保留下来的草稿正文</p>');
            titleInput.value = '从草稿箱继续';
            summaryInput.value = '草稿列表里的摘要';
            saveDraft();
            actions.back();
        """.trimIndent())
        app.waitUntil(5_000) { !open.value }
        app.waitForIdle()
        app.runOnIdle { generation.intValue++ }
        app.waitForIdle()
        app.onNodeWithText("打开草稿箱").performClick()
        app.waitUntil(20_000) { !session.draftsLoading && session.drafts.isNotEmpty() }
        app.onNodeWithText("从草稿箱继续").assertIsDisplayed()
        app.onNodeWithText("草稿列表里的摘要").assertIsDisplayed()
        app.onNodeWithTag("draft-box-item-0").performClick()
        app.waitUntil(20_000) { ready.get() == 2 }
        assertEquals("true", eval("titleInput.value === '从草稿箱继续' && editor.getText().includes('保留下来的草稿正文')"))
        eval("localStorage.removeItem(draftKey)")
    }

    @Test fun multipleDraftsOpenTheirOwnContentAndDeletionSurvivesReload() {
        val storageKey = "draft-box-test-" + UUID.randomUUID()
        val ready = AtomicInteger()
        val open = mutableStateOf(true)
        lateinit var session: ArticleEditorSession
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    session = rememberArticleEditorSession(storageKey)
                    val box = remember { DraftBoxState() }
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { box.open(Offset(180f, 900f)) }) { Text("打开草稿箱") }
                        DraftBoxBubble(box, session.drafts, session.draftsLoading, session.draftsFailed,
                            { session.loadDrafts(EditorPalette.forTheme(false)) },
                            { draft -> session.selectDraft(draft); open.value = true },
                            session.deletingDraftKey, session::deleteDraft)
                        if (open.value) ArticleEditorScreen(
                            onBack = { open.value = false }, session = session, onReady = { ready.incrementAndGet() })
                    }
                }
            }
        }
        app.waitUntil(20_000) { ready.get() == 1 }
        eval("""
            localStorage.setItem(draftKey + ':first', JSON.stringify({
              title: '第一篇独立草稿', body: '第一篇独立正文', summary: '第一篇摘要', coverUrl: '', updatedAt: '2026-11-17T08:00:00Z'
            }));
            localStorage.setItem(draftKey + ':second', JSON.stringify({
              title: '第二篇独立草稿', body: '第二篇独立正文', summary: '第二篇摘要', coverUrl: '', updatedAt: '2026-11-17T07:00:00Z'
            }));
            actions.back();
        """.trimIndent())
        app.waitUntil(5_000) { !open.value }
        app.onNodeWithText("打开草稿箱").performClick()
        app.waitUntil(15_000) { !session.draftsLoading && session.drafts.size >= 2 }
        app.onNodeWithText("第二篇独立草稿").performClick()
        app.waitUntil(20_000) { ready.get() == 2 }
        assertEquals("true", eval("titleInput.value === '第二篇独立草稿' && editor.getText().includes('第二篇独立正文')"))
        eval("actions.back()")
        app.waitUntil(5_000) { !open.value }
        app.onNodeWithText("打开草稿箱").performClick()
        app.waitUntil(15_000) { !session.draftsLoading }
        app.runOnIdle { session.deleteDraft(session.drafts.single { it.title == "第二篇独立草稿" }) }
        app.waitUntil(10_000) { session.deletingDraftKey == null && session.drafts.none { it.title == "第二篇独立草稿" } }
        app.runOnIdle { session.loadDrafts(EditorPalette.forTheme(false)) }
        app.waitUntil(15_000) { !session.draftsLoading }
        app.runOnIdle {
            assertTrue(session.drafts.none { it.title == "第二篇独立草稿" })
            assertTrue(session.drafts.any { it.title == "第一篇独立草稿" })
        }
        app.onNodeWithText("第一篇独立草稿").performClick()
        app.waitUntil(20_000) { ready.get() == 3 }
        assertEquals("true", eval("titleInput.value === '第一篇独立草稿' && editor.getText().includes('第一篇独立正文')"))
        eval("localStorage.removeItem(draftKey)")
    }
}
