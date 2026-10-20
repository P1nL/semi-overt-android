package com.semiovert.app.core.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.model.*
import com.semiovert.app.core.designsystem.solid.SolidArticleFeed
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ArticleFeed(
    articles: List<Article>, listState: LazyListState, listTag: String,
    onArticle: (String) -> Unit, topPadding: Int = 16, emptyMessage: String = "暂时没有文章",
    emptyHint: String = "试试其他阅读时长。", modifier: Modifier = Modifier, categoryCards: Boolean=false,
) {
    val expansion=LocalArticleExpansion.current
    var unavailable by remember {mutableStateOf(false)}
    if(!categoryCards&&articles.isNotEmpty()&&(listTag=="feed-home"||listTag=="feed-category")&&LocalMotionEnabled.current&&LocalDensity.current.fontScale<=1.2f&&!unavailable) {
        SolidArticleFeed(articles,listTag,onArticle,{unavailable=true},modifier)
        return
    }
    if (articles.isEmpty()) {
        Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
            SoIcon(SoSymbol.BOOK, modifier=Modifier.size(36.dp), tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(emptyMessage,style=MaterialTheme.typography.titleMedium)
            Text(emptyHint,Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(state=listState,userScrollEnabled=expansion?.active!=true,modifier=modifier.fillMaxSize().testTag(listTag),
            contentPadding=PaddingValues(start=20.dp,end=20.dp,top=topPadding.dp,bottom=128.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            itemsIndexed(articles,key={_,it->it.id}) { index,article ->
                if(categoryCards)CoverArticleCard(article,true,Modifier.fillMaxWidth().aspectRatio(.8f).testTag("article-"+article.id),
                    "wave-cover-"+article.id,topAligned=true,squareBottom=true,shortBottomBlur=true,onClick={onArticle(article.id)},
                    exitRank=(index-if(index>articles.indexOfFirst {it.id==expansion?.source?.article?.id})1 else 0).coerceAtLeast(0))
                else ArticleCard(article,onArticle)
            }
            item("offline-note") { Text("本地原创示例 · 插画与内容仅用于交互预览",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp)) }
        }
    }
}

@Composable
fun ArticleCard(article: Article, onArticle: (String) -> Unit) {
    val mint = article.tone == CardTone.MINT
    val background = if (mint) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
    val foreground = if (mint) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer
    val enabled=LocalFlowRoll.current<.001f
    Surface(color=background,contentColor=foreground,shape=RoundedCornerShape(26.dp),
        border=BorderStroke(0.5.dp,MaterialTheme.colorScheme.outlineVariant),
        modifier=Modifier.fillMaxWidth().testTag("article-"+article.id).clickable(enabled=enabled,onClickLabel="阅读文章") { onArticle(article.id) }) {
        Column {
            article.cover?.let { SampleCover(it,Modifier.fillMaxWidth().aspectRatio(372f/176f)) }
            Column(Modifier.fillMaxWidth().heightIn(min=168.dp).padding(17.dp)) {
                Text(article.title,style=MaterialTheme.typography.titleLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
                if (article.summary != null) Text(article.summary,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    SoIcon(SoSymbol.CLOCK,modifier=Modifier.size(15.dp),tint=foreground)
                    Text("约 "+article.minutes+" 分钟",Modifier.padding(start=5.dp),style=MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth().padding(top=7.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),contentAlignment=Alignment.Center) { SoIcon(SoSymbol.PERSON,modifier=Modifier.size(15.dp),tint=MaterialTheme.colorScheme.onSecondary) }
                    Text(article.author.name,Modifier.weight(1f).padding(start=7.dp),style=MaterialTheme.typography.bodySmall)
                    SoIcon(SoSymbol.ARROW,modifier=Modifier.size(18.dp),tint=foreground)
                }
            }
        }
    }
}
