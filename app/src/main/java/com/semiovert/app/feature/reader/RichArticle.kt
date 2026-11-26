package com.semiovert.app.feature.reader

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.semiovert.app.core.designsystem.rememberRemoteImage
import com.semiovert.app.core.designsystem.StatusText
import com.semiovert.app.core.designsystem.accentInk
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

internal data class RichSection(val heading: String?, val blocks: List<RichBlock>)

internal sealed interface RichBlock {
    data class TextHtml(val html: String) : RichBlock
    data class Code(val value: String) : RichBlock
    data class Picture(val url: String, val alt: String) : RichBlock
    data class Table(val rows: List<List<String>>) : RichBlock
    data object Rule : RichBlock
}

internal object RichArticle {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).sanitizeUrls(true).build()
    private val safe = Cleaner(Safelist.relaxed()
        .addTags("del", "s", "strike", "hr", "sup", "sub")
        .removeProtocols("a", "href", "http", "ftp", "mailto")
        .removeProtocols("img", "src", "http")
        .preserveRelativeLinks(true))

    fun parse(source: String, baseUrl: String): List<RichSection> {
        val body = safe.clean(Jsoup.parseBodyFragment(renderer.render(parser.parse(source)), baseUrl)).body()
        val sections = mutableListOf<RichSection>()
        var heading: String? = null
        val blocks = mutableListOf<RichBlock>()
        fun finish() {
            if (heading != null || blocks.isNotEmpty()) sections += RichSection(heading, blocks.toList())
            blocks.clear()
        }
        fun block(element: Element) {
            when (element.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    finish()
                    heading = element.text()
                }
                "pre" -> blocks += RichBlock.Code(element.wholeText().trimEnd())
                "table" -> blocks += RichBlock.Table(element.select("tr").map { row -> row.children().map(Element::text) })
                "hr" -> blocks += RichBlock.Rule
                "div" -> element.childNodes().forEach { child ->
                    if (child is Element) block(child)
                    else if (child is TextNode && !child.isBlank) blocks += RichBlock.TextHtml(child.outerHtml())
                }
                else -> {
                    val images = if (element.tagName() == "img") listOf(element) else element.select("img").toList()
                    val pictures = images.mapNotNull { image -> image.attr("src").takeIf { it.isNotBlank() }
                        ?.let { RichBlock.Picture(it, image.attr("alt")) } }
                    if (element.tagName() != "img") {
                        images.forEach { it.remove() }
                        element.select("a[href]").forEach { link ->
                            val target = link.absUrl("href")
                            if (target.startsWith("https://")) link.attr("href", target) else link.removeAttr("href")
                        }
                        if (element.hasText()) blocks += RichBlock.TextHtml(element.outerHtml())
                    }
                    blocks += pictures
                }
            }
        }
        body.childNodes().forEach { child ->
            if (child is Element) block(child)
            else if (child is TextNode && !child.isBlank) blocks += RichBlock.TextHtml(child.outerHtml())
        }
        finish()
        return sections
    }
}

@Composable
internal fun RichContent(block: RichBlock) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    when (block) {
        is RichBlock.TextHtml -> {
            val text = remember(block.html) { HtmlCompat.fromHtml(block.html, HtmlCompat.FROM_HTML_MODE_COMPACT).trimEnd() }
            AndroidView(factory = { viewContext -> TextView(viewContext).apply {
                movementMethod = LinkMovementMethod.getInstance()
            } }, modifier = Modifier.fillMaxWidth(), update = {
                it.text = text
                it.textSize = textStyle.fontSize.value
                it.setTextColor(colors.onSurface.toArgb())
                it.setLinkTextColor(colors.accentInk.toArgb())
                it.setLineSpacing(0f, 1.16f)
            })
        }
        is RichBlock.Picture -> {
            val photo = rememberRemoteImage(block.url)
            val image = photo.bitmap
            if (image != null) Image(image, block.alt.ifBlank { "正文图片" }, Modifier.fillMaxWidth()
                .aspectRatio((image.width.toFloat() / image.height).coerceIn(.4f, 4f)), contentScale = ContentScale.Fit)
            else StatusText(if (photo.loaded) block.alt.ifBlank { "图片不可用" } else "图片加载中",
                style = MaterialTheme.typography.bodySmall)
        }
        is RichBlock.Code -> Box(Modifier.fillMaxWidth().background(colors.surfaceVariant)) {
            Text(block.value, Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
        is RichBlock.Table -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            block.rows.forEachIndexed { index, cells ->
                Row(Modifier.background(if (index == 0) colors.secondaryContainer else colors.surfaceVariant)) {
                    cells.forEach { cell ->
                        Text(cell, Modifier.width(132.dp).padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        RichBlock.Rule -> androidx.compose.material3.HorizontalDivider()
    }
}
