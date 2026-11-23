package com.semiovert.app

import com.semiovert.app.core.data.RefreshCookie
import com.semiovert.app.core.data.changedExcerpt
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.feature.reader.RichArticle
import com.semiovert.app.feature.reader.RichBlock
import org.junit.Assert.*
import org.junit.Test

class BusinessContractTest {
    @Test fun matchesTheServerCategoryCodes() {
        assertEquals(listOf("QUICK", "SHORT", "DEEP"), ReadingDuration.entries.map { it.apiCode })
    }

    @Test fun keepsOnlyUsableDeviceCookies() {
        val secure = "device=rotated_token; Path=/api; HttpOnly; Secure; SameSite=Lax"
        assertEquals(RefreshCookie("device=rotated_token", 11_000), RefreshCookie.from(listOf("$secure; Max-Age=10"), 1000))
        assertEquals(RefreshCookie("device=rotated_token", null), RefreshCookie.from(listOf(secure), 1000))
        assertNull(RefreshCookie.from(listOf("$secure; Max-Age=0"), 1000))
        assertNull(RefreshCookie.from(listOf("other=x; Path=/; Secure"), 1000))
    }

    @Test fun preservesCodePointBoundariesInLocalDiffs() {
        assertEquals("😀" to "😁", changedExcerpt("头😀尾", "头😁尾"))
        assertEquals("" to "新增", changedExcerpt("首尾", "首新增尾"))
        assertEquals("删除" to "", changedExcerpt("首删除尾", "首尾"))
    }

    @Test fun rendersMarkdownAndLegacyHtmlWithoutActiveContent() {
        val sections = RichArticle.parse("""
            ## 第一节
            **粗体** [文档](https://example.com/doc) [链接](javascript:alert%281%29)

            | 日期 | 内容 |
            | --- | --- |
            | 周一 | 发布 |

            ![封面](/static/uploads/photo.png)

            ```kotlin
            println("hello")
            ```

            <h2>历史正文</h2><p>正文 <script>hidden()</script></p>
        """.trimIndent(), "https://api.semi-overt.com/")
        assertEquals(listOf("第一节", "历史正文"), sections.map { it.heading })
        val blocks = sections.flatMap { it.blocks }
        assertEquals("发布", blocks.filterIsInstance<RichBlock.Table>().single().rows[1][1])
        assertEquals("/static/uploads/photo.png", blocks.filterIsInstance<RichBlock.Picture>().single().url)
        assertEquals("println(\"hello\")", blocks.filterIsInstance<RichBlock.Code>().single().value)
        val html = blocks.filterIsInstance<RichBlock.TextHtml>().joinToString { it.html }
        assertTrue(html.contains("<strong>粗体</strong>"))
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("hidden()"))
    }
}
