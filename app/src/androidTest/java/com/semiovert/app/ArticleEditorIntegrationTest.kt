package com.semiovert.app

import android.app.Application
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.WebView
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.InkRevealLayout
import com.semiovert.app.feature.editor.EditorPalette
import com.semiovert.app.navigation.AppNavigation
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ArticleEditorIntegrationTest {
    @get:Rule val app = createComposeRule()

    private fun eval(script: String): String {
        val result = AtomicReference<String?>()
        onView(withContentDescription("文章编辑器")).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript(script) { result.set(it) }
        }
        app.waitUntil(15_000) { result.get() != null }
        return checkNotNull(result.get())
    }

    private fun navigation(shortcuts: List<NavigationShortcut> = emptyList()) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = PreviewViewModel(application)
        app.setContent {
            SoTheme {
                AppNavigation(PreviewState(shortcuts = shortcuts), model, {})
            }
        }
        app.waitForIdle()
    }

    private fun waitForEditor() {
        app.onNodeWithTag("article-editor").assertExists()
        app.waitUntil(20_000) { eval("Boolean(window.SemiNativeEditor)").equals("true") }
        app.waitForIdle()
        assertEquals("true", eval("document.querySelector('.status-bar').offsetHeight === 0"))
    }

    private fun isolatedEditor() {
        val ready = AtomicBoolean()
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    ArticleEditorScreen({}, storageKey = "editor-test-" + UUID.randomUUID(),
                        onReady = { ready.set(true) })
                }
            }
        }
        app.waitUntil(20_000) { ready.get() }
        app.waitForIdle()
    }

    private fun webTarget(selector: String) = JSONObject(JSONTokener(eval("""
            JSON.stringify((() => {
              const r = document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();
              return { x: r.left + r.width / 2, y: r.top + r.height / 2, width: innerWidth };
            })())
        """.trimIndent())).nextValue().toString())

    private fun clickWeb(selector: String, tap: Tap = Tap.SINGLE) {
        val target = webTarget(selector)
        onView(withContentDescription("文章编辑器")).perform(GeneralClickAction(tap, { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val scale = view.width / target.getDouble("width")
            floatArrayOf((location[0] + target.getDouble("x") * scale).toFloat(),
                (location[1] + target.getDouble("y") * scale).toFloat())
        }, Press.FINGER))
    }

    private fun swipeWeb(from: String, to: String) {
        val start = webTarget(from)
        val end = webTarget(to)
        fun coordinates(view: android.view.View, point: JSONObject): FloatArray {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val scale = view.width / point.getDouble("width")
            return floatArrayOf((location[0] + point.getDouble("x") * scale).toFloat(),
                (location[1] + point.getDouble("y") * scale).toFloat())
        }
        onView(withContentDescription("文章编辑器")).perform(GeneralSwipeAction(Swipe.SLOW,
            { view -> coordinates(view, start) }, { view -> coordinates(view, end) }, Press.FINGER))
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            visible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun assertNoAutomaticKeyboard() {
        // Allow a queued focus/IME request one frame and a keyboard transition to become observable.
        eval("window.keyboardCheckDone = false; setTimeout(() => window.keyboardCheckDone = true, 350)")
        app.waitUntil(5_000) { eval("window.keyboardCheckDone") == "true" }
        assertFalse("Toolbar operations must not summon the IME", keyboardVisible())
    }

    private fun assertBodySelectionPreserved() {
        assertEquals("The body must retain real DOM focus", "true",
            eval("editor.isFocused && document.activeElement === editor.view.dom"))
        assertEquals("The DOM selection must follow the editor selection", "true", eval("""
            (() => {
              const selection = window.getSelection(), expected = editor.state.selection;
              return selection.rangeCount === 1 &&
                editor.view.posAtDOM(selection.anchorNode, selection.anchorOffset) === expected.anchor &&
                editor.view.posAtDOM(selection.focusNode, selection.focusOffset) === expected.head &&
                !editor.view.dom.classList.contains('ProseMirror-hideselection') &&
                getComputedStyle(editor.view.dom).caretColor !== 'rgba(0, 0, 0, 0)';
            })()
        """.trimIndent()))
    }

    private fun captureEditor(name: String) {
        val image = app.onRoot().captureToImage()
        ApplicationProvider.getApplicationContext<Application>().cacheDir.resolve("$name.png").outputStream().use {
            assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun toolbarCommandsDoNotSummonTheKeyboardButDirectTextTapsDo() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>Toolbar keyboard regression</p>'); editor.commands.setTextSelection(2)")
        clickWeb("#quick-tools [data-tool=bold]")
        assertNoAutomaticKeyboard()
        assertEquals("true", eval("editor.isActive('bold')"))

        clickWeb("#topic-title")
        app.waitUntil(5_000) { keyboardVisible() }
        closeSoftKeyboard()
        clickWeb("#expand-button")
        clickWeb("#common-tools [data-tool=italic]")
        assertNoAutomaticKeyboard()
        clickWeb("#expand-button")
        assertNoAutomaticKeyboard()

        clickWeb("#cursor-button")
        clickWeb("#cursor-controls [data-move=right]")
        assertEquals("3", eval("editor.state.selection.head"))
        assertNoAutomaticKeyboard()
        clickWeb("#quick-tools [data-tool=link]")
        assertEquals("true", eval("$('#link-popover').dataset.open === 'true' && !dialog.open"))
        assertNoAutomaticKeyboard()
        eval("SemiNativeEditor.back()")
        assertNoAutomaticKeyboard()
        clickWeb(".send-button")
        assertNoAutomaticKeyboard()

        clickWeb(".tiptap")
        app.waitUntil(5_000) { keyboardVisible() }
        clickWeb("#expand-button")
        app.waitUntil(5_000) { !keyboardVisible() }
        clickWeb("#expand-button")
        assertNoAutomaticKeyboard()
    }

    @Test fun keyboardRaisesTheToolbarWithoutResizingTheWritingAreaAndBodyTapClosesDrawer() {
        isolatedEditor()
        // Match MainActivity instead of the generic Compose host's default window behavior.
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().first()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        app.waitForIdle()
        val height = eval("$('#writing-area').getBoundingClientRect().height")
        val phoneHeight = eval("phone.getBoundingClientRect().height")
        val top = eval("island.getBoundingClientRect().top").toDouble()
        clickWeb("#expand-button")
        clickWeb("#topic-title")
        app.waitUntil(5_000) { keyboardVisible() }
        app.waitUntil(5_000) { eval("parseFloat(getComputedStyle(phone).getPropertyValue('--keyboard-inset')) > 100") == "true" }
        eval("window.keyboardSettled = false; setTimeout(() => window.keyboardSettled = true, 350)")
        app.waitUntil(5_000) { eval("window.keyboardSettled") == "true" }
        captureEditor("editor-keyboard")
        assertEquals("false", eval("state.expanded"))
        assertEquals(height, eval("$('#writing-area').getBoundingClientRect().height"))
        assertEquals(phoneHeight, eval("phone.getBoundingClientRect().height"))
        assertTrue(eval("island.getBoundingClientRect().top").toDouble() < top - 100)
        val toolbarTop = eval("island.getBoundingClientRect().top")
        swipeWeb("#quick-tools [data-tool=bold]", "#grabber")
        assertEquals(toolbarTop, eval("island.getBoundingClientRect().top"))
        assertEquals("false", eval("state.expanded"))
        assertEquals("0", eval("window.scrollY"))
        onView(withContentDescription("文章编辑器")).check { view, _ -> assertEquals(0, view.scrollY) }
        clickWeb("#expand-button")
        app.waitUntil(5_000) { !keyboardVisible() }
        assertEquals("true", eval("state.expanded"))
        clickWeb(".tiptap p")
        app.waitUntil(5_000) { keyboardVisible() }
        assertEquals("false", eval("state.expanded"))
        eval("editor.commands.insertContent('<p>keyboard line</p>'.repeat(30)); true")
        app.waitUntil(5_000) {
            eval("editor.view.coordsAtPos(editor.state.selection.head).bottom < island.getBoundingClientRect().top") == "true"
        }
        assertEquals(height, eval("$('#writing-area').getBoundingClientRect().height"))
        swipeWeb("#quick-tools [data-tool=bold]", "#grabber")
        assertEquals("0", eval("window.scrollY"))
        onView(withContentDescription("文章编辑器")).check { view, _ -> assertEquals(0, view.scrollY) }
    }

    @Test fun imageToolDirectlyInvokesTheLocalPicker() {
        isolatedEditor()
        assertEquals("true", eval("""
            (() => {
              const picker = $('#image-picker'), original = picker.click;
              let requested = false;
              picker.click = () => { requested = true; };
              try { runTool('image'); return requested && !dialog.open; }
              finally { picker.click = original; }
            })()
        """.trimIndent()))
        assertNoAutomaticKeyboard()
    }

    @Test fun upperToolbarDoesNotReopenTheDismissedKeyboard() {
        isolatedEditor()
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().first()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        eval("editor.commands.setContent('<p>控制工具栏时保持键盘关闭</p>'); editor.commands.insertContent('正文'); true")
        clickWeb(".tiptap p")
        app.waitUntil(5_000) { keyboardVisible() }
        closeSoftKeyboard()
        app.waitUntil(5_000) { !keyboardVisible() && eval("!phone.classList.contains('keyboard-open')") == "true" }
        assertBodySelectionPreserved()
        val selection = eval("JSON.stringify(editor.state.selection.toJSON())")
        clickWeb("#content-button")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        assertEquals(selection, eval("JSON.stringify(editor.state.selection.toJSON())"))
        assertEquals("true", eval("$('#editing-actions').offsetWidth <= 210 && $('#editing-actions').offsetHeight <= 136"))
        captureEditor("editor-content-compact")
        clickWeb("[data-content-action=selectAll]")
        assertNoAutomaticKeyboard()
        assertEquals("false", eval("editor.state.selection.empty"))
        assertBodySelectionPreserved()
        eval("editor.commands.setTextSelection(2)")
        clickWeb("#cursor-button")
        assertEquals("true", eval("$('#cursor-controls').offsetWidth <= 210 && $('#cursor-controls').offsetHeight <= 138"))
        captureEditor("editor-cursor-compact")
        clickWeb("#cursor-controls [data-move=right]", Tap.LONG)
        assertNoAutomaticKeyboard()
        assertTrue(eval("editor.state.selection.head").toInt() > 2)
        assertBodySelectionPreserved()
        clickWeb("#select-button")
        clickWeb("#cursor-controls [data-move=left]")
        assertNoAutomaticKeyboard()
        assertEquals("false", eval("editor.state.selection.empty"))
        assertBodySelectionPreserved()
        clickWeb(".history-shortcuts [data-tool=undo]")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        clickWeb(".history-shortcuts [data-tool=redo]")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        clickWeb("[data-drawer-page='1']")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        clickWeb("[data-drawer-page='0']")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        clickWeb(".tiptap p")
        app.waitUntil(5_000) { keyboardVisible() && eval("phone.classList.contains('keyboard-open')") == "true" }
        clickWeb("#content-button")
        assertTrue("An already-open keyboard stays open during toolbar use", keyboardVisible())
        assertBodySelectionPreserved()
        clickWeb("#expand-button")
        app.waitUntil(5_000) { !keyboardVisible() }
        assertBodySelectionPreserved()
        clickWeb("#expand-button")
        assertNoAutomaticKeyboard()
        assertBodySelectionPreserved()
        clickWeb(".tiptap p")
        app.waitUntil(5_000) { keyboardVisible() }
        assertBodySelectionPreserved()
    }

    @Test fun imageFollowingParagraphKeepsToolbarDockedToTheKeyboard() {
        isolatedEditor()
        var windowHeight = 0
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>().first()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            windowHeight = view.rootView.height
        }
        val bitmap = Bitmap.createBitmap(400, 460, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        val image = ByteArrayOutputStream().use { bytes ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        bitmap.recycle()
        eval("""
            editor.commands.setContent('<img src="data:image/png;base64,$image"><p>图片下一行</p>');
            editor.commands.setTextSelection(1);
            true;
        """.trimIndent())
        app.waitUntil(5_000) { eval("$('.tiptap img').complete && $('.tiptap img').naturalWidth > 0") == "true" }
        assertTrue("Use a full-width image that needs caret scrolling", eval("$('.tiptap img').getBoundingClientRect().height").toDouble() > 300)
        eval("""
            (() => {
              const scroller = $('#rich-editor'), paragraph = $('.tiptap > p:last-child');
              scroller.scrollTop += Math.max(0, paragraph.getBoundingClientRect().bottom - scroller.getBoundingClientRect().bottom + 16);
            })()
        """.trimIndent())
        val height = eval("phone.getBoundingClientRect().height")
        clickWeb(".tiptap > p:last-child")
        app.waitUntil(5_000) { keyboardVisible() }
        eval("window.imageKeyboardSettled = false; setTimeout(() => window.imageKeyboardSettled = true, 500)")
        app.waitUntil(5_000) { eval("window.imageKeyboardSettled") == "true" }
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            // Exercise native caret panning independently of DOM scroller movement.
            view.scrollTo(0, 96)
            assertEquals("The WebView root must stay fixed after an IME scroll request", 0, view.scrollY)
            val frame = view.parent as InkRevealLayout
            frame.translationY = -80f
            ViewCompat.dispatchApplyWindowInsets(frame, checkNotNull(ViewCompat.getRootWindowInsets(view)))
        }
        app.waitForIdle()
        captureEditor("editor-image-keyboard")
        val layout = JSONObject(JSONTokener(eval("""
            JSON.stringify({
              bottom: island.getBoundingClientRect().bottom,
              viewportTop: visualViewport.offsetTop,
              width: innerWidth,
              scroll: scrollY,
              caret: editor.view.coordsAtPos(editor.state.selection.head).bottom,
              toolbar: island.getBoundingClientRect().top
            })
        """.trimIndent())).nextValue().toString())
        onView(withContentDescription("文章编辑器")).check { view, _ ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val scale = view.width / layout.getDouble("width")
            val ime = checkNotNull(ViewCompat.getRootWindowInsets(view)).getInsets(WindowInsetsCompat.Type.ime()).bottom
            val toolbarBottom = location[1] + (layout.getDouble("bottom") - layout.getDouble("viewportTop")) * scale
            val gap = (windowHeight - ime - toolbarBottom) / scale
            assertEquals("Toolbar/keyboard gap in CSS pixels; layout=$layout, nativeScroll=${view.scrollY}", 11.0, gap, 3.0)
            assertEquals(0, view.scrollY)
        }
        assertEquals(height, eval("phone.getBoundingClientRect().height"))
        assertEquals(0, layout.getInt("scroll"))
        assertTrue(layout.getDouble("caret") < layout.getDouble("toolbar"))
        swipeWeb("#quick-tools [data-tool=bold]", "#grabber")
        assertEquals(layout.getDouble("bottom"), eval("island.getBoundingClientRect().bottom").toDouble(), 1.0)
    }

    @Test fun columnChangesAnimateCellsEvenWhenTheTableWidthDoesNotChange() {
        isolatedEditor()
        eval("runTool('table')")
        clickWeb("[data-context-action=table-confirm]")
        clickWeb(".tiptap td", Tap.LONG)
        clickWeb("[data-context-action=scope-column]")
        assertEquals("true", eval("""
            (() => {
              const width = $('.tiptap table').getBoundingClientRect().width;
              $('[data-context-action=table-add]').click();
              const cells = [...document.querySelectorAll('.tiptap td')];
              return SemiEditor.tableInfo(editor).cols === 4 &&
                Math.abs($('.tiptap table').getBoundingClientRect().width - width) < 2 &&
                (reducedMotion.matches || cells.some(c => c.getAnimations().some(a =>
                  a.effect.getKeyframes().some(f => f.transform && f.transform !== 'none'))));
            })()
        """.trimIndent()))
        app.waitUntil(5_000) { eval("[...document.querySelectorAll('.tiptap td')].every(c => !c.getAnimations().length)") == "true" }
        assertEquals("true", eval("""
            $('[data-context-action=table-delete]').click();
            SemiEditor.tableInfo(editor).cols === 3 &&
              (reducedMotion.matches || (
                $('.table-cell-ghost') &&
                [...document.querySelectorAll('.tiptap td')].some(c => c.getAnimations().length > 0)))
        """.trimIndent()))
        app.waitUntil(5_000) { eval("!$('.table-cell-ghost') && [...document.querySelectorAll('.tiptap td')].every(c => !c.getAnimations().length)") == "true" }
        assertEquals("true", eval("!editor.getHTML().includes('table-cell-ghost') && !SemiEditor.serializeEditorToMarkdown(editor).includes('transform')"))
        assertNoAutomaticKeyboard()
    }

    @Test fun tableSetupLongPressScopesAndDragHandlesOperateOnTheDocument() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>before after</p>'); editor.commands.setTextSelection(8); runTool('table')")
        assertEquals("true", eval("(() => { const t = SemiEditor.tableInfo(editor); return t.rows === 3 && t.cols === 3 && !t.header && $('#table-setup').dataset.open === 'true'; })()"))
        assertNoAutomaticKeyboard()
        clickWeb("[data-context-action=row-more]")
        clickWeb("[data-context-action=col-less]")
        clickWeb("[data-context-action=header]")
        assertEquals("true", eval("(() => { const t = SemiEditor.tableInfo(editor); return t.rows === 4 && t.cols === 2 && t.header; })()"))
        assertEquals("true", eval("editor.getText().includes('before') && editor.getText().includes('after')"))
        assertEquals("true", eval("(() => { const a = $('.tiptap table').getBoundingClientRect(), b = $('#rich-editor').getBoundingClientRect(); return Math.abs(a.left + a.right - b.left - b.right) < 2; })()"))
        captureEditor("editor-table-setup")
        clickWeb("[data-context-action=table-confirm]")
        clickWeb(".tiptap tr:nth-child(2) td:first-child", Tap.LONG)
        assertEquals(eval("JSON.stringify({type:editor.state.selection.constructor.name,cells:document.querySelectorAll('.selectedCell').length,phase:$('#table-actions').dataset.open})"),
            "true", eval("SemiEditor.tableInfo(editor).cellSelection && document.querySelectorAll('.selectedCell').length === 8"))
        clickWeb("[data-context-action=scope-row]")
        clickWeb("[data-context-action=table-add]")
        assertEquals("5", eval("SemiEditor.tableInfo(editor).rows"))
        clickWeb("[data-context-action=table-delete]")
        assertEquals("4", eval("SemiEditor.tableInfo(editor).rows"))
        clickWeb("[data-context-action=scope-column]")
        clickWeb("[data-context-action=table-add]")
        assertEquals("3", eval("SemiEditor.tableInfo(editor).cols"))
        clickWeb("[data-context-action=table-delete]")
        assertEquals("2", eval("SemiEditor.tableInfo(editor).cols"))
        clickWeb("[data-context-action=scope-cell]")
        swipeWeb("[data-table-handle=head]", ".tiptap tr:last-child td:last-child")
        assertEquals("true", eval("document.querySelectorAll('.selectedCell').length > 1"))
        app.waitUntil(5_000) {
            eval("""
                (() => {
                  const cells = [...document.querySelectorAll('.selectedCell')].map(c => c.getBoundingClientRect());
                  const ink = $('#table-selection-ink').getBoundingClientRect();
                  return Math.abs(ink.left - Math.min(...cells.map(r => r.left))) < 2 &&
                    Math.abs(ink.right - Math.max(...cells.map(r => r.right))) < 2 &&
                    Math.abs(ink.bottom - Math.max(...cells.map(r => r.bottom))) < 2;
                })()
            """.trimIndent()) == "true"
        }
        captureEditor("editor-table-selection")
        assertEquals("true", eval("(() => { const p = $('#table-actions').getBoundingClientRect(), b = phone.getBoundingClientRect(); return p.left >= b.left && p.right <= b.right && p.top >= b.top; })()"))
        assertNoAutomaticKeyboard()
    }

    @Test fun linkCaretAndFloatingUrlApplyToSelectedOrNewlyTypedText() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>链接文字与后文</p>'); editor.commands.setTextSelection({from:1,to:5}); runTool('link')")
        assertEquals("true", eval("phone.classList.contains('link-editing') && !$('#link-caret').hidden && !dialog.open"))
        captureEditor("editor-link")
        assertNoAutomaticKeyboard()
        eval("$('#link-url').value = 'https://example.com'")
        clickWeb("[data-context-action=link-confirm]")
        assertEquals("true", eval("$('.tiptap a').textContent === '链接文字' && $('.tiptap a').href === 'https://example.com/'"))
        eval("editor.commands.setTextSelection(editor.state.doc.content.size - 1); runTool('link'); editor.commands.insertContent('追加链接'); $('#link-url').value = 'example.net'")
        assertEquals("true", eval("$('.pending-link-text').textContent === '追加链接'"))
        clickWeb("[data-context-action=link-confirm]")
        assertEquals("true", eval("[...document.querySelectorAll('.tiptap a')].some(a => a.textContent === '追加链接' && a.href === 'https://example.net/')"))
        assertEquals("true", eval("!editor.getHTML().includes('pending-link') && SemiEditor.getLinkDraft(editor) === null"))
        assertNoAutomaticKeyboard()
    }

    @Test fun lastSelectedRowAndColumnCanDeleteTheWholeTable() {
        isolatedEditor()
        for (scope in listOf("row", "column")) {
            eval("editor.commands.setContent('<p></p>'); runTool('table')")
            assertEquals("true", eval("reducedMotion.matches || $('.tableWrapper').getAnimations().length > 0"))
            clickWeb("[data-context-action=table-confirm]")
            clickWeb(".tiptap td", Tap.LONG)
            clickWeb("[data-context-action=scope-$scope]")
            repeat(3) { clickWeb("[data-context-action=table-delete]") }
            assertEquals("true", eval("!$('.tiptap table') && $('#table-actions').dataset.open === 'false'"))
        }
        assertNoAutomaticKeyboard()
    }

    @Test fun headingMenuAnchorsAboveItsButtonAndSupportsAllFiveLevels() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>标题内容</p><p></p>'); editor.commands.setTextSelection(2); window.paragraphMarkdown = SemiEditor.serializeEditorToMarkdown(editor)")
        clickWeb("#quick-tools [data-tool=heading]")
        assertEquals("true", eval("!dialog.open && $('#heading-popover').dataset.open === 'true' && document.querySelectorAll('[data-heading-level]').length === 6"))
        app.waitUntil(5_000) { eval("$('#heading-popover').getAnimations().length") == "0" }
        assertEquals("true", eval("$('#heading-popover').getBoundingClientRect().bottom < $('#quick-tools [data-tool=heading]').getBoundingClientRect().top"))
        assertEquals("true", eval("$('#heading-popover').offsetWidth <= 150 && $('[data-heading-level]').offsetHeight <= 38"))
        captureEditor("editor-heading")
        for (level in 1..5) {
            if (level > 1) clickWeb("#quick-tools [data-tool=heading]")
            clickWeb("[data-heading-level='$level']")
            assertEquals("true", eval("editor.isActive('heading', {level:$level})"))
            assertEquals("true", eval("SemiEditor.serializeEditorToMarkdown(editor).startsWith('${"#".repeat(level)} ')"))
        }
        clickWeb("#quick-tools [data-tool=heading]")
        clickWeb("[data-heading-level='0']")
        assertEquals(eval("JSON.stringify(editor.getJSON())"), "true",
            eval("editor.isActive('paragraph') && !editor.isActive('heading') && editor.state.doc.textContent === '标题内容'"))
        assertEquals("true", eval("SemiEditor.serializeEditorToMarkdown(editor) === paragraphMarkdown"))
        assertNoAutomaticKeyboard()
    }

    @Test fun holdingCursorArrowsRepeatsAndSelectionLabelsTransition() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>' + 'abcdef'.repeat(30) + '</p>'); editor.commands.setTextSelection(2)")
        clickWeb("#cursor-button")
        clickWeb("#cursor-controls [data-move=right]", Tap.LONG)
        assertTrue(eval("editor.state.selection.head").toInt() > 6)
        val head = eval("editor.state.selection.head")
        eval("window.repeatStopped = false; setTimeout(() => window.repeatStopped = true, 160)")
        app.waitUntil(5_000) { eval("window.repeatStopped") == "true" }
        assertEquals(head, eval("editor.state.selection.head"))
        eval("""
            window.scrambleProbe = null;
            $('#select-button').click();
            requestAnimationFrame(() => requestAnimationFrame(() =>
              window.scrambleProbe = $('#select-button .select-label').textContent));
        """.trimIndent())
        app.waitUntil(5_000) { eval("scrambleProbe !== null") == "true" }
        assertEquals("true", eval("state.selecting && $('#select-button').getAttribute('aria-label') === '退出选择'"))
        assertEquals("true", eval("reducedMotion.matches || [...scrambleProbe].some(c => '<>/\\\\|{}[]#%01'.includes(c))"))
        app.waitUntil(5_000) { eval("$('#select-button .select-label').textContent === '退出选择'") == "true" }
        clickWeb("#select-button")
        app.waitUntil(5_000) { eval("$('#select-button .select-label').textContent === '选择文字'") == "true" }
        assertEquals("false", eval("state.selecting"))
        eval("$('#select-button').click(); $('#select-button').click(); true")
        app.waitUntil(5_000) { eval("$('#select-button .select-label').textContent === '选择文字' && selectLabelFrame === 0") == "true" }
        assertEquals("true", eval("$('#select-button .select-label').getAttribute('aria-hidden') === 'true' && $('#select-button').getAttribute('aria-label') === '选择文字'"))
        assertNoAutomaticKeyboard()
    }

    @Test fun linkToolShowsItsDraftStateAndSecondTapCancelsIt() {
        isolatedEditor()
        eval("editor.commands.setContent('<p>普通文字</p>'); editor.commands.setTextSelection({from:1,to:3})")
        clickWeb("#quick-tools [data-tool=link]")
        assertEquals("true", eval("phone.classList.contains('link-editing') && $('#quick-tools [data-tool=link]').getAttribute('aria-pressed') === 'true'"))
        clickWeb("#quick-tools [data-tool=link]")
        assertEquals("true", eval("!phone.classList.contains('link-editing') && SemiEditor.getLinkDraft(editor) === null && !$('.tiptap a') && $('#quick-tools [data-tool=link]').getAttribute('aria-pressed') === 'false'"))
        assertNoAutomaticKeyboard()
    }

    @Test fun editorTextUsesNativeRenderingWithoutGlyphAnimationLayers() {
        isolatedEditor()
        eval("""
            titleInput.value = '字符';
            titleInput.dispatchEvent(new InputEvent('input', {bubbles:true,inputType:'insertText',data:'字符'}));
            editor.view.dom.dispatchEvent(new InputEvent('beforeinput', {bubbles:true,inputType:'insertText',data:'新文字'}));
            editor.commands.insertContent('新文字');
            editor.view.dom.dispatchEvent(new InputEvent('input', {bubbles:true,inputType:'insertText',data:'新文字'}));
            true;
        """.trimIndent())
        eval("""
            titleInput.dispatchEvent(new CompositionEvent('compositionstart', {bubbles:true}));
            titleInput.value = '字符中';
            titleInput.dispatchEvent(new InputEvent('input', {bubbles:true,inputType:'insertCompositionText',data:'中',isComposing:true}));
            titleInput.dispatchEvent(new CompositionEvent('compositionend', {bubbles:true,data:'中'}));
            editor.commands.insertContent('续');
            summaryInput.value = '摘要';
            summaryInput.dispatchEvent(new Event('input', {bubbles:true}));
            runTool('link');
            $('#link-url').value = 'example.com';
            $('#link-url').dispatchEvent(new InputEvent('input', {bubbles:true,inputType:'insertText',data:'example.com'}));
            true;
        """.trimIndent())
        assertEquals("true", eval("titleInput.value === '字符中' && summaryInput.value === '摘要' && editor.getText() === '新文字续'"))
        assertEquals("true", eval("!document.querySelector('.stitched-input-wrap, .editor-glyph-layer, .falling-glyph, .glyph-arriving') && !SemiEditor.glyphArrivals"))
        assertEquals("true", eval("[titleInput, summaryInput, $('#link-url'), editor.view.dom].every(e => getComputedStyle(e).color !== 'rgba(0, 0, 0, 0)')"))
        assertEquals("true", eval("!SemiEditor.serializeEditorToMarkdown(editor).includes('glyph')"))
        assertNoAutomaticKeyboard()
    }

    @Test fun moreMenuAnimatesBothDirectionsAndCanReverseWhileClosing() {
        isolatedEditor()
        val reduced = eval("reducedMotion.matches") == "true"
        assertEquals("true", eval("""
            actions.menu();
            $('#overflow-menu').dataset.open === 'true' && !$('#overflow-menu').inert &&
              ($reduced || $('#overflow-menu').getAnimations().length === 1)
        """.trimIndent()))
        assertEquals("true", eval("""
            closeMenu();
            $('#overflow-menu').inert && $('#menu-dismiss').hidden &&
              $('#more-button').getAttribute('aria-expanded') === 'false' &&
              ($reduced || (!$('#overflow-menu').hidden && $('#overflow-menu').getAnimations().length === 1))
        """.trimIndent()))
        assertEquals("true", eval("""
            actions.menu();
            $('#overflow-menu').dataset.open === 'true' && !$('#overflow-menu').hidden && !$('#overflow-menu').inert
        """.trimIndent()))
        eval("closeMenu()")
        app.waitUntil(5_000) { eval("$('#overflow-menu').hidden") == "true" }
        assertEquals("0", eval("$('#overflow-menu').getAnimations().length"))
    }

    @Test fun homeCreateButtonOpensThePackagedEditorAndReturnsToHome() {
        navigation()
        app.onNodeWithTag("main-circle").performClick()
        assertEditorWaitsForDraftMenu("create-draft")
        eval("SemiNativeEditor.back()")
        app.waitUntil(10_000) { app.onAllNodesWithTag("article-editor").fetchSemanticsNodes().isEmpty() }
        app.onNodeWithTag("main-circle").assertIsDisplayed()
    }

    @Test fun independentCreateShortcutOpensTheSameEditor() {
        navigation(listOf(NavigationShortcut("create-draft", .2f, .4f)))
        app.onNodeWithTag("shortcut-create-draft").performClick()
        waitForEditor()
        assertEquals("true", eval("Boolean(document.querySelector('.tiptap[contenteditable=true]'))"))
    }

    @Test fun independentCreateWaitsForTheRemainingDraftCircleToCollapse() {
        navigation(listOf(NavigationShortcut("create-draft", .2f, .4f)))
        app.onNodeWithTag("main-circle").performClick()
        assertEditorWaitsForDraftMenu("shortcut-create-draft")
    }

    private fun assertEditorWaitsForDraftMenu(createTag: String) {
        app.waitForIdle()
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithTag(createTag).performClick()
            app.onNodeWithTag("article-editor").assertDoesNotExist()
            app.mainClock.advanceTimeBy(100)
            app.onNodeWithTag("home-draft-dismiss").assertExists()
            app.onNodeWithTag("article-editor").assertDoesNotExist()
        } finally {
            app.mainClock.autoAdvance = true
        }
        waitForEditor()
        app.onNodeWithTag("home-draft-dismiss").assertDoesNotExist()
    }

    @Test fun formattingSummaryCoverAndLocalDraftSurviveReload() {
        val ready = AtomicInteger()
        val closed = AtomicBoolean()
        val key = "editor-test-" + UUID.randomUUID()
        app.setContent {
            SoTheme {
                ArticleEditorScreen({ closed.set(true) }, storageKey = key, onReady = { ready.incrementAndGet() })
            }
        }
        app.waitUntil(20_000) { ready.get() > 0 }
        app.waitForIdle()
        val bitmap = Bitmap.createBitmap(32, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
        val image = ByteArrayOutputStream().use { bytes ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        }
        bitmap.recycle()
        eval("""
            titleInput.value = '移动端编辑检查';
            titleInput.dispatchEvent(new Event('input', {bubbles:true}));
            editor.commands.setContent('<p>这是一段超过二十个字的正文，用于确认编辑工具和草稿格式在应用中可以正常保留。</p>');
            editor.commands.selectAll();
            document.querySelector('#quick-tools [data-tool="bold"]').click();
            summaryInput.value = '摘要内容';
            summaryInput.dispatchEvent(new Event('input', {bubbles:true}));
            (() => {
              const bytes = Uint8Array.from(atob('$image'), c => c.charCodeAt(0));
              const files = new DataTransfer();
              files.items.add(new File([bytes], 'cover.png', {type:'image/png'}));
              const input = document.querySelector('#cover-picker');
              input.files = files.files;
              input.dispatchEvent(new Event('change', {bubbles:true}));
            })();
        """.trimIndent())
        app.waitUntil(10_000) { eval("state.coverUrl.startsWith('data:image/jpeg')") == "true" }
        eval("SemiNativeEditor.flush()")
        val saved = JSONObject(JSONTokener(eval("localStorage.getItem(${JSONObject.quote(key)})")).nextValue().toString())
        assertEquals("移动端编辑检查", saved.getString("title"))
        assertEquals("摘要内容", saved.getString("summary"))
        assertTrue("Bold must retain the web editor's canonical strong markup", saved.getString("body").contains("<strong>"))
        assertTrue(saved.getString("coverUrl").startsWith("data:image/jpeg"))
        val beforeReload = ready.get()
        eval("location.reload()")
        app.waitUntil(20_000) { ready.get() > beforeReload }
        assertEquals("true", eval("titleInput.value === '移动端编辑检查' && summaryInput.value === '摘要内容' && editor.getHTML().includes('<strong>')"))
        eval("document.querySelector('.send-button').click()")
        assertEquals("true", eval("state.preview && !document.querySelector('#preview-cover').hidden"))
        eval("SemiNativeEditor.back()")
        assertEquals("false", eval("state.preview"))
        assertFalse(closed.get())
        eval("SemiNativeEditor.back()")
        app.waitUntil(10_000) { closed.get() }
    }

    @Test fun reducedMotionShowsTheReadyEditorWithoutExpansion() {
        val ready = AtomicBoolean()
        val closed = AtomicBoolean()
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    ArticleEditorScreen({ closed.set(true) }, storageKey = "editor-test-" + UUID.randomUUID(), onReady = { ready.set(true) })
                }
            }
        }
        app.waitUntil(20_000) { ready.get() }
        app.waitForIdle()
        onView(isAssignableFrom(InkRevealLayout::class.java)).check { view, _ ->
            assertEquals(1f, (view as InkRevealLayout).revealProgress, 0f)
        }
        eval("SemiNativeEditor.back()")
        app.waitUntil(10_000) { closed.get() }
    }

    @Test fun closeReversesTheExistingMaskBeforeDismissing() {
        val ready = AtomicBoolean()
        val closed = AtomicInteger()
        app.setContent {
            SoTheme {
                ArticleEditorScreen({ closed.incrementAndGet() }, storageKey = "editor-test-" + UUID.randomUUID(),
                    onReady = { ready.set(true) })
            }
        }
        app.waitUntil(20_000) { ready.get() }
        app.waitForIdle()
        lateinit var frame: InkRevealLayout
        onView(isAssignableFrom(InkRevealLayout::class.java)).check { view, _ -> frame = view as InkRevealLayout }
        assertEquals(1f, frame.revealProgress, 0f)
        app.mainClock.autoAdvance = false
        // Repeated native close requests must not dismiss early or restart the reverse.
        eval("SemiNative.close(); SemiNative.close()")
        app.mainClock.advanceTimeBy(400)
        app.runOnIdle {
            assertSame(frame, frame.getChildAt(0).parent)
            assertTrue("The existing mask must move downward", frame.revealProgress in .01f.. .99f)
            assertFalse(frame.documentReady)
            assertEquals(0, closed.get())
        }
        app.mainClock.advanceTimeBy(400)
        app.runOnIdle { assertEquals(0, closed.get()) }
        app.mainClock.advanceTimeBy(200)
        app.runOnIdle {
            assertEquals(0f, frame.revealProgress, 0f)
            assertEquals(1, closed.get())
        }
        app.mainClock.autoAdvance = true
    }

    @Test fun lightAndDarkThemesReachAllEditorSurfacesWithoutReloadingTheDraft() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        val ready = AtomicInteger()
        val key = "editor-test-" + UUID.randomUUID()
        app.setContent {
            SoTheme(mode.value) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    ArticleEditorScreen({}, storageKey = key, onReady = { ready.incrementAndGet() })
                }
            }
        }
        app.waitUntil(20_000) { ready.get() == 1 }
        eval("""
            titleInput.value = '主题适配';
            titleInput.dispatchEvent(new Event('input', {bubbles:true}));
            editor.commands.setContent('<h2>浅暗色编辑</h2><p>正文、摘要和封面设置随主题共同切换，切换时保留正在编辑的草稿。</p>');
            summaryInput.value = '摘要内容';
            summaryInput.dispatchEvent(new Event('input', {bubbles:true}));
            setExpanded(true);
        """.trimIndent())
        for (palette in listOf(EditorPalette.Light, EditorPalette.Dark)) {
            app.runOnIdle { mode.value = if (palette == EditorPalette.Dark) ThemeMode.DARK else ThemeMode.LIGHT }
            app.waitForIdle()
            assertEquals(JSONObject.quote(palette.name), eval("document.documentElement.dataset.theme"))
            app.waitUntil(5_000) { eval("document.querySelector('.tool-surface .press-surface').getAnimations().length") == "0" }
            val computed = JSONObject(JSONTokener(eval("""
                JSON.stringify((() => {
                  const css = selector => getComputedStyle(document.querySelector(selector));
                  const root = getComputedStyle(document.documentElement);
                  return {
                    background: css('body').backgroundColor,
                    text: css('.tiptap').color,
                    island: css('#island').backgroundColor,
                    summary: css('#topic-summary').backgroundColor,
                    cover: css('#cover-upload').backgroundColor,
                    popover: css('#editing-actions').backgroundColor,
                    tile: css('.tool-surface .press-surface').backgroundColor,
                    blur: css('#island').backdropFilter,
                    footer: css('.island-footer').backgroundColor,
                    dialog: css('#action-dialog').backgroundColor,
                    preview: css('#preview-title').color,
                    accent: root.getPropertyValue('--accent').trim(),
                    scheme: root.colorScheme
                  };
                })())
            """.trimIndent())).nextValue().toString())
            val dark = palette == EditorPalette.Dark
            assertEquals("rgba(0, 0, 0, 0)", computed.getString("background"))
            assertEquals(if (dark) "rgb(232, 230, 222)" else "rgb(27, 31, 44)", computed.getString("text"))
            assertEquals(if (dark) "rgba(37, 39, 43, 0.62)" else "rgba(255, 255, 255, 0.62)", computed.getString("island"))
            for (surface in listOf("summary", "cover"))
                assertEquals(if (dark) "rgba(255, 255, 255, 0.04)" else "rgba(255, 255, 255, 0.45)", computed.getString(surface))
            assertEquals(if (dark) "rgba(38, 40, 44, 0.86)" else "rgba(255, 255, 255, 0.82)", computed.getString("popover"))
            assertEquals(if (dark) "rgba(255, 255, 255, 0.055)" else "rgba(255, 255, 255, 0.38)", computed.getString("tile"))
            assertEquals("blur(22px)", computed.getString("blur"))
            assertEquals("rgba(0, 0, 0, 0)", computed.getString("footer"))
            assertEquals(if (dark) "rgb(24, 42, 43)" else "rgb(255, 254, 249)", computed.getString("dialog"))
            assertEquals(computed.getString("text"), computed.getString("preview"))
            assertEquals(if (dark) "#2cb38b" else "#158624", computed.getString("accent"))
            assertEquals(palette.name, computed.getString("scheme"))
            assertEquals("true", eval("titleInput.value === '主题适配' && summaryInput.value === '摘要内容' && editor.getHTML().includes('切换时保留')"))
            assertEquals("Theme changes must not reload the document", 1, ready.get())
            onView(isAssignableFrom(InkRevealLayout::class.java)).check { view, _ ->
                assertEquals(palette, (view as InkRevealLayout).palette)
            }
            val image = app.onRoot().captureToImage()
            val context = ApplicationProvider.getApplicationContext<Application>()
            context.cacheDir.resolve("editor-${palette.name}.png").outputStream().use {
                assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test fun hardwareWavesRiseFromBottomAndFinallyCoverTheScreen() = checkHardwareWaves(EditorPalette.Dark)
    @Test fun lightWavesReverseAlongTheSamePath() = checkHardwareWaves(EditorPalette.Light)

    private fun checkHardwareWaves(palette: EditorPalette) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val progress = mutableFloatStateOf(0f)
        val underlying = Color(0xFFAF41FF)
        app.setContent {
            Box(Modifier.fillMaxSize().background(underlying)) {
                AndroidView(factory = {
                    InkRevealLayout(it, arrayOf(
                        floatArrayOf(0f, 80f, 220f, 130f, 300f, 40f, 270f, 180f, 100f, 250f),
                        floatArrayOf(250f, 100f, 180f, 270f, 40f, 300f, 130f, 220f, 80f, 0f),
                    )).apply { this.palette = palette }
                }, modifier = Modifier.fillMaxSize(),
                    update = { it.revealProgress = progress.floatValue })
            }
        }
        app.waitForIdle()
        var pixels = app.onRoot().captureToImage().toPixelMap()
        fun matches(color: Color, expected: Color) =
            kotlin.math.abs(color.red - expected.red) < .01f &&
                kotlin.math.abs(color.green - expected.green) < .01f &&
                kotlin.math.abs(color.blue - expected.blue) < .01f
        fun editor(x: Int, y: Int) = matches(pixels[x, y], Color(palette.background))
        fun revealed(x: Int, y: Int) = !matches(pixels[x, y], underlying)
        val cx = pixels.width / 2
        val cy = pixels.height / 2
        assertFalse(editor(cx, cy))
        assertFalse(editor(8, 8))
        assertFalse(revealed(cx, pixels.height - 9))
        app.runOnIdle { progress.floatValue = .5f }
        app.waitForIdle()
        val middleImage = app.onRoot().captureToImage()
        pixels = middleImage.toPixelMap()
        assertFalse(revealed(cx, 8))
        assertTrue(editor(cx, pixels.height - 9))
        val fronts = (8 until pixels.width - 8 step maxOf(1, pixels.width / 40)).map { x ->
            (8 until pixels.height - 8).first { y -> revealed(x, y) }
        }
        assertTrue("The reveal must retain pronounced peaks and valleys", fronts.max() - fronts.min() > pixels.width * .15f)
        assertTrue("Portrait waves must not stretch into long strands", fronts.max() - fronts.min() <= pixels.width * .50f + 4f)
        assertTrue("The leading gradient must remain visible above the editor", (8 until pixels.height - 8).any {
            val color = pixels[cx, it]
            if (palette == EditorPalette.Light) color.red > .8f && color.blue < .2f
            else color.blue > .8f && color.red < .2f
        })
        context.cacheDir.resolve("liquid-reveal-${palette.name}.png").outputStream().use {
            assertTrue(middleImage.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val earlierPixels = pixels
        app.runOnIdle { progress.floatValue = .7f }
        app.waitForIdle()
        pixels = app.onRoot().captureToImage().toPixelMap()
        for (x in 8 until pixels.width - 8 step 16) for (y in 8 until pixels.height - 8 step 16) {
            if (matches(earlierPixels[x, y], Color(palette.background))) {
                assertTrue("Already revealed content must remain visible while the waves advance", editor(x, y))
            }
        }
        app.runOnIdle { progress.floatValue = .999f }
        app.waitForIdle()
        pixels = app.onRoot().captureToImage().toPixelMap()
        assertTrue(editor(8, 8))
        assertTrue(editor(pixels.width - 9, 8))
        assertTrue(editor(8, pixels.height - 9))
        assertTrue(editor(pixels.width - 9, pixels.height - 9))
        app.runOnIdle { progress.floatValue = 1f }
        app.waitForIdle()
        pixels = app.onRoot().captureToImage().toPixelMap()
        assertTrue(editor(cx, cy))
        app.runOnIdle { progress.floatValue = .5f }
        app.waitForIdle()
        pixels = app.onRoot().captureToImage().toPixelMap()
        for (x in 8 until pixels.width - 8 step 16) for (y in 8 until pixels.height - 8 step 16) {
            assertEquals("Reverse playback must reuse the entrance contour", earlierPixels[x, y], pixels[x, y])
        }
        app.runOnIdle { progress.floatValue = 0f }
        app.waitForIdle()
        pixels = app.onRoot().captureToImage().toPixelMap()
        assertFalse(revealed(cx, cy))
        assertFalse(revealed(cx, pixels.height - 9))
    }
}
