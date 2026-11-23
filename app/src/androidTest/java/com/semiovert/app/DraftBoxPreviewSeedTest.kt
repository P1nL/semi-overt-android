package com.semiovert.app

import android.webkit.WebView
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.feature.editor.ArticleEditorScreen
import com.semiovert.app.feature.editor.ArticleEditorSession
import com.semiovert.app.feature.editor.EditorPalette
import com.semiovert.app.feature.editor.rememberArticleEditorSession
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit preview preparation for an emulator: existing drafts keep their storage keys and content. */
@RunWith(AndroidJUnit4::class)
class DraftBoxPreviewSeedTest {
    @get:Rule val app = createComposeRule()

    @Test fun addTwelveExampleDrafts() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seedDraftPreview") == "true")
        val ready = AtomicBoolean()
        val seeded = AtomicBoolean()
        lateinit var session: ArticleEditorSession
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    session = rememberArticleEditorSession()
                    ArticleEditorScreen({}, onReady = { ready.set(true) }, session = session)
                }
            }
        }
        app.waitUntil(20_000) { ready.get() }
        onView(isAssignableFrom(WebView::class.java)).check { view, error ->
            if (error != null) throw error
            (view as WebView).evaluateJavascript("""
                (() => {
                  const drafts = [
                    ['把清晨留给一段散步', '巷口的早餐店刚开门，蒸汽和晨光一起落在街边。'],
                    ['窗边的一点光', '午后的光慢慢落在纸上，还有几句想写下的话。'],
                    ['今天读到的一句话', '把一句喜欢的话抄下来，隔几天再看看它带来了什么。'],
                    ['雨停以后', '雨停了，树叶上的水还在滴，路面映着一点天空。'],
                    ['关于一杯热茶', '水声停下来的时候，房间里安静得刚刚好。'],
                    ['旧书里夹着的车票', '翻到那一页，想起很久以前经过的一座小城。'],
                    ['回家路上的树影', '每天经过的街道，在不同时间有着不同的光。'],
                    ['一个还没写完的故事', '故事的开头是推开一扇门，后面的情节再慢慢想。'],
                    ['留给周末的小计划', '去公园坐一会儿，带一本薄书和一小瓶水。'],
                    ['夜里的最后一盏灯', '楼下的灯还亮着，有人正在结束忙碌的一天。'],
                    ['整理房间时想到的事', '留下常用的东西，也给还没发生的事情空出位置。'],
                    ['写在最后的一点想法', '有些想法暂时没有答案，先把它们轻轻记下来。']
                  ];
                  drafts.forEach(([title, summary], index) => {
                    const key = draftKey + ':preview-' + String(index + 1).padStart(2, '0');
                    if (!localStorage.getItem(key)) localStorage.setItem(key, JSON.stringify({
                      title, summary, body: summary + String.fromCharCode(10, 10) + '这是一篇用来体验草稿列表滚动与滑动删除的示例草稿，可以继续编辑。',
                      coverUrl: '', updatedAt: new Date(Date.parse('2026-11-16T06:00:00Z') - index * 3600000).toISOString()
                    }));
                  });
                  return true;
                })()
            """.trimIndent()) { result -> seeded.set(result == "true") }
        }
        app.waitUntil(10_000) { seeded.get() }
        app.runOnIdle { session.loadDrafts(EditorPalette.forTheme(false)) }
        app.waitUntil(10_000) { !session.draftsLoading && session.drafts.count { it.key.contains(":preview-") } == 12 }
        // Chromium batches localStorage disk commits after five seconds.
        // Keep this host alive before instrumentation stops the renderer.
        SystemClock.sleep(6_000)
    }
}
