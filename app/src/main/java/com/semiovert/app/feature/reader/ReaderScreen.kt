package com.semiovert.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.Article
import com.semiovert.app.navigation.BrowseScene
import com.semiovert.app.navigation.Destination
import kotlinx.coroutines.launch

class ReaderViewModel(private val savedState: SavedStateHandle): ViewModel() {
    val article=SampleLibrary.article(savedState.get<String>("articleId").orEmpty())
    val contentsOpen=savedState.getStateFlow("contentsOpen",false)
    fun contents(open: Boolean){savedState["contentsOpen"]=open}
}

/** The live reader and transition preview measure the exact same typography and layout. */
@Composable
fun ReaderArticleLayout(article: Article,list: LazyListState,onAuthor: (String)->Unit,
    anchors: ReaderAnchors?=null,bodyProgress: Float=1f,avatarProgress: Float=1f,headingProgress: Float=1f,tag: String="reader-screen") {
    val hidden=if(anchors!=null)Modifier.graphicsLayer {alpha=0f}.clearAndSetSemantics {} else Modifier
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+40.dp
    val bodyTravel=LocalConfiguration.current.screenHeightDp*LocalDensity.current.density
    Box(Modifier.fillMaxSize().testTag(tag)) {
        LazyColumn(state=list,userScrollEnabled=anchors==null,modifier=Modifier.fillMaxSize().testTag(if(anchors==null)"reader-content" else "reader-preview-content"),
            contentPadding=PaddingValues(start=24.dp,end=24.dp,top=top,bottom=136.dp)) {
            item("title") {
                Text("本地示例",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.padding(bottom=12.dp).graphicsLayer {alpha=headingProgress}.semantics {heading()})
                Text(article.title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.onGloballyPositioned {anchors?.title=it.boundsInRoot()}
                    .then(hidden).testTag(if(anchors==null)"reader-title" else "reader-title-target").semantics {heading()})
            }
            item("author") {
                Row(Modifier.sparkClickable(enabled=anchors==null,onClickLabel="查看作者主页"){onAuthor(article.author.id)}.padding(top=18.dp,bottom=22.dp),verticalAlignment=Alignment.CenterVertically) {
                    AuthorAvatar(article.author.id,size=24,shared=anchors==null,modifier=Modifier.graphicsLayer {alpha=avatarProgress})
                    Column(Modifier.padding(start=10.dp)) {
                        Text(article.author.name,style=MaterialTheme.typography.labelLarge,modifier=Modifier.onGloballyPositioned {anchors?.author=it.boundsInRoot()}.then(hidden))
                        Row {
                            Text(article.date+" · ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.onGloballyPositioned {anchors?.date=it.boundsInRoot()}.then(hidden))
                            Text("约 "+article.minutes+" 分钟",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.onGloballyPositioned {anchors?.duration=it.boundsInRoot()}.then(hidden))
                        }
                    }
                }
                HorizontalDivider(Modifier.graphicsLayer {alpha=bodyProgress},color=MaterialTheme.colorScheme.outlineVariant)
            }
            items(article.sections,key={it.id}) {section ->
                Column(Modifier.fillMaxWidth().padding(top=28.dp).graphicsLayer {
                    alpha=bodyProgress;translationY=bodyTravel*(1f-bodyProgress)
                }.testTag("section-"+section.id)) {
                    Text(section.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=12.dp))
                    section.paragraphs.forEach {Text(it,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(bottom=16.dp))}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: ()->Unit,onAuthor: (String)->Unit,onNavigate: (Destination)->Unit,origin: Destination,model: ReaderViewModel=viewModel()) {
    val article=model.article
    if(article==null){Column(Modifier.fillMaxSize().padding(24.dp)){Text("找不到这篇示例文章");TextButton(onClick=onBack){Text("返回文章列表")}};return}
    val expansion=LocalArticleExpansion.current
    SideEffect {expansion?.readerReady(article.id)}
    val list=rememberLazyListState()
    val coroutine=rememberCoroutineScope()
    val contentsOpen by model.contentsOpen.collectAsStateWithLifecycle()
    BackHandler(!contentsOpen&&expansion?.active!=true) {
        if(expansion==null)onBack() else expansion.close(article.id,onBack)
    }
    BrowseScene(origin,"",onNavigate,onPrimary={model.contents(true)},showHeader=false,readerMode=true) {
        ReaderArticleLayout(article,list,onAuthor)
    }
    if(contentsOpen)SoBottomSheet(onDismissRequest={model.contents(false)}) {close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=24.dp,end=24.dp,bottom=24.dp).testTag("reader-contents")) {
            Text("目录",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=16.dp))
            article.sections.forEachIndexed {index,section->TextButton(onClick={close {coroutine.launch {list.scrollToItem(index+2)}}},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Text(section.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
            }}
        }
    }
}
