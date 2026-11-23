package com.semiovert.app

import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.rememberArticleEditorSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EditorEntryPerformanceTest {
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

    @Test fun repeatedEntryReportsReadinessAndPreservesTheDraft() {
        val open = mutableStateOf(false)
        val ready = AtomicInteger()
        val started = AtomicLong()
        val elapsed = AtomicLong()
        var browser: WebView? = null
        var sentinel = ""
        val key = "editor-entry-test-" + UUID.randomUUID()
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    val session = rememberArticleEditorSession(key)
                    if (open.value) ArticleEditorScreen(
                        onBack = { open.value = false },
                        storageKey = key,
                        session = session,
                        onReady = {
                            elapsed.set(SystemClock.elapsedRealtime() - started.get())
                            ready.incrementAndGet()
                        },
                    )
                }
            }
        }
        repeat(3) { attempt ->
            app.runOnIdle {
                started.set(SystemClock.elapsedRealtime())
                open.value = true
            }
            app.waitUntil(20_000) { ready.get() == attempt + 1 }
            Log.i("EditorEntryPerf", "entry=${attempt + 1} readyMs=${elapsed.get()}")
            onView(isAssignableFrom(WebView::class.java)).check { view, _ ->
                if (browser == null) browser = view as WebView
                else assertSame("Reentry must reuse the same renderer", browser, view)
            }
            if (attempt == 0) {
                sentinel = eval("window.entrySentinel = Math.random()")
                eval("""
                    editor.commands.setContent('<p>Repeated entry draft</p>');
                    editor.commands.insertContent(' undo');
                    titleInput.value = 'Retained title';
                    titleInput.dispatchEvent(new Event('input', {bubbles:true}));
                    summaryInput.value = 'Retained summary';
                    summaryInput.dispatchEvent(new Event('input', {bubbles:true}));
                    saveDraft();
                """.trimIndent())
            } else {
                assertEquals("Reentry must not reload scripts", sentinel, eval("window.entrySentinel"))
                assertEquals("true", eval("editor.getText().includes('Repeated entry draft')"))
                assertEquals("true", eval("titleInput.value === 'Retained title' && summaryInput.value === 'Retained summary'"))
                assertEquals("true", eval("!state.expanded && !state.preview && !dialog.open && !editor.isFocused"))
                assertEquals("true", eval("!phone.classList.contains('keyboard-open')"))
                assertEquals("true", eval("$('#overflow-menu').hidden || $('#overflow-menu').inert"))
                assertEquals("true", eval("editor.can().undo()"))
            }
            // Parking resets transient UI without clearing content or undo history.
            eval("actions.preview(); SemiNative.close()")
            app.waitUntil(5_000) { !open.value }
            app.waitForIdle()
            app.onNodeWithTag("article-editor").assertDoesNotExist()
        }
    }
}
