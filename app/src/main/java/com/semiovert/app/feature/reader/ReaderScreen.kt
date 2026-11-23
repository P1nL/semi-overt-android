package com.semiovert.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.rememberRequest
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.Article
import com.semiovert.app.navigation.BrowseScene
import com.semiovert.app.navigation.Destination
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReaderViewModel(private val savedState: SavedStateHandle): ViewModel() {
    val articleId=savedState.get<String>("articleId").orEmpty()
    val article get() = SampleLibrary.article(articleId)
    val contentsOpen=savedState.getStateFlow("contentsOpen",false)
    fun contents(open: Boolean){savedState["contentsOpen"]=open}
}

private suspend fun LazyListState.rewindToTop() {
    scroll(MutatePriority.PreventUserInput) {
        val layout=LazyLayoutScrollScope(this@rewindToTop,this)
        var previous=0f
        animate(0f,1f,animationSpec=tween(240,easing=SoSceneEase)) {progress,_->
            if(previous<1f) {
                val remaining=layout.calculateDistanceTo(0).coerceAtMost(0)
                scrollBy(remaining*(progress-previous)/(1f-previous))
            }
            previous=progress
        }
    }
    if(canScrollBackward)animateScrollToItem(0)
}

/** The live reader and transition preview measure the exact same typography and layout. */
@Composable
internal fun ReaderArticleLayout(article: Article,list: LazyListState,onAuthor: (String)->Unit,
    anchors: ReaderAnchors?=null,bodyProgress: Float=1f,avatarProgress: Float=1f,headingProgress: Float=1f,
    tag: String="reader-screen",interactive: Boolean=anchors==null,rich: List<RichSection>?=null,
    remoteBody: (@Composable () -> Unit)?=null) {
    val hidden=if(anchors!=null)Modifier.graphicsLayer {alpha=0f}.clearAndSetSemantics {} else Modifier
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+40.dp
    val bodyTravel=LocalConfiguration.current.screenHeightDp*LocalDensity.current.density
    Box(Modifier.fillMaxSize().testTag(tag)) {
        LazyColumn(state=list,userScrollEnabled=interactive,modifier=Modifier.fillMaxSize().testTag(if(anchors==null)"reader-content" else "reader-preview-content"),
            contentPadding=PaddingValues(start=24.dp,end=24.dp,top=top,bottom=136.dp)) {
            item("title") {
                Text(article.title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.onGloballyPositioned {anchors?.title=it.boundsInRoot()}
                    .then(hidden).testTag(if(anchors==null)"reader-title" else "reader-title-target").semantics {heading()})
            }
            item("author") {
                Row(Modifier.sparkClickable(enabled=interactive&&article.author.id.isNotBlank(),onClickLabel="查看作者主页"){onAuthor(article.author.id)}.padding(top=18.dp,bottom=22.dp),verticalAlignment=Alignment.CenterVertically) {
                    AuthorAvatar(article.author.id,size=24,shared=anchors==null,modifier=Modifier.graphicsLayer {alpha=avatarProgress})
                    Column(Modifier.padding(start=10.dp)) {
                        Text(article.author.name,style=MaterialTheme.typography.labelLarge,modifier=Modifier.onGloballyPositioned {anchors?.author=it.boundsInRoot()}.then(hidden))
                        Row {
                            Text((article.date+" · ").withInterfaceDigits(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.onGloballyPositioned {anchors?.date=it.boundsInRoot()}.then(hidden))
                            Text(("约 "+article.minutes+" 分钟").withInterfaceDigits(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.onGloballyPositioned {anchors?.duration=it.boundsInRoot()}.then(hidden))
                        }
                    }
                }
                HorizontalDivider(Modifier.graphicsLayer {alpha=bodyProgress},color=MaterialTheme.colorScheme.outlineVariant)
            }
            if (rich != null) {
                items(rich.size, key = { "rich-$it" }) { index ->
                    val section = rich[index]
                    Column(Modifier.fillMaxWidth().padding(top=24.dp).testTag("section-rich-$index"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        section.heading?.let { Text(it, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }) }
                        section.blocks.forEach { RichContent(it) }
                    }
                }
            } else if (remoteBody != null) {
                item("remote-body") { remoteBody() }
            } else items(article.sections,key={it.id}) {section ->
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
    val expansion=LocalArticleExpansion.current
    val business = LocalBusiness.current
    val remote = business?.let {
        rememberRequest(it, model.articleId) {
            val article = it.articleDetail(model.articleId)
            article to withContext(Dispatchers.Default) {
                RichArticle.parse(article.markdown.orEmpty(), it.mediaUrl("/") ?: "https://api.semi-overt.com/")
            }
        }
    }
    val article = if (business == null) model.article else remote?.first?.value?.first
        ?: expansion?.source?.article?.takeIf { it.id == model.articleId } ?: business.knownArticle(model.articleId)
    val rich = remote?.first?.value?.second
    if(article==null) {
        PageFrame("阅读", onBack, "reader-unavailable") {
            val error = remote?.first?.error
            RemoteState(error ?: if (remote?.first?.loading == true) "正在加载文章…" else "未找到文章",
                action = error?.let { "重新加载" }, onAction = remote?.second ?: {})
        }
        return
    }
    val motion=LocalMotionEnabled.current
    var docked by rememberSaveable(article.id) {mutableStateOf(expansion?.isOpeningReader(article.id)==true||!motion)}
    val circleDock=remember(article.id) {Animatable(if(docked)1f else 0f)}
    LaunchedEffect(circleDock,motion) {
        if(!docked) {
            circleDock.animateTo(1f,if(motion)tween(SceneMotion.READER_CIRCLE_DOCK_MS,easing=SoSceneEase) else snap())
            docked=true
        }
    }
    SideEffect {expansion?.readerReady(article.id)}
    val list=rememberLazyListState()
    val coroutine=rememberCoroutineScope()
    var rewinding by remember {mutableStateOf(false)}
    val contentsOpen by model.contentsOpen.collectAsStateWithLifecycle()
    val close={if(expansion==null)onBack() else expansion.close(article.id,onBack)}
    BackHandler(!contentsOpen&&expansion?.active!=true) {
        if(!rewinding) {
            if(motion&&list.canScrollBackward) {
                rewinding=true
                coroutine.launch {
                    try {
                        list.rewindToTop()
                        close()
                    } finally {rewinding=false}
                }
            } else close()
        }
    }
    BrowseScene(origin,"",onNavigate,onPrimary={if(!rewinding)model.contents(true)},showHeader=false,
        readerDock=circleDock.asState(),navigationEnabled=!rewinding) {
        ReaderArticleLayout(article,list,onAuthor,interactive=!rewinding, rich=rich,
            remoteBody = if (remote != null) {{
                val error = remote.first.error
                RemoteState(error ?: "正在加载正文…", action = error?.let { "重新加载" }, onAction = remote.second)
            }} else null)
    }
    if(contentsOpen)SoBottomSheet(onDismissRequest={model.contents(false)}) {close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=24.dp,end=24.dp,bottom=24.dp).testTag("reader-contents")) {
            Text("目录",style=MaterialTheme.typography.headlineSmall.withInterfaceTypeface(),modifier=Modifier.padding(bottom=16.dp))
            val headings = if (business != null) rich.orEmpty().mapIndexedNotNull { index, section ->
                section.heading?.let { index to it }
            } else article.sections.mapIndexed { index, section -> index to section.title }
            if (headings.isEmpty()) Text("这篇文章没有目录", Modifier.padding(vertical = 12.dp))
            headings.forEach {(index,title)->TextButton(onClick={close {coroutine.launch {list.scrollToItem(index+2)}}},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
            }}
        }
    }
}
