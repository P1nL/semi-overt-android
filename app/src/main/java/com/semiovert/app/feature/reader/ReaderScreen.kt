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
import com.semiovert.app.core.data.ReadingPosition
import com.semiovert.app.core.data.rememberRequest
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.Article
import com.semiovert.app.navigation.BrowseScene
import com.semiovert.app.navigation.Destination
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal const val READER_BODY_LOADING = "正在加载正文"

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
    remoteBody: (@Composable () -> Unit)?=null,richReveal: ()->Float={1f},overlay: @Composable BoxScope.()->Unit={}) {
    val hidden=if(anchors!=null)Modifier.graphicsLayer {alpha=0f}.clearAndSetSemantics {} else Modifier
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+40.dp
    val bodyTravel=LocalConfiguration.current.screenHeightDp*LocalDensity.current.density
    Box(Modifier.fillMaxSize().testTag(tag)) {
        LazyColumn(state=list,userScrollEnabled=interactive,modifier=Modifier.fillMaxSize().testTag(if(anchors==null)"reader-content" else "reader-preview-content"),
            contentPadding=PaddingValues(start=24.dp,end=24.dp,top=top,bottom=136.dp)) {
            val bodyMotion=Modifier.graphicsLayer {alpha=bodyProgress;translationY=bodyTravel*(1f-bodyProgress)}
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
            if (remoteBody!=null||rich!=null) {
                item("remote-body") {
                    Box(Modifier.fillMaxWidth().then(bodyMotion)) {
                        rich?.firstOrNull()?.let {RichReaderSection(it,0,richReveal)}
                        remoteBody?.invoke()
                    }
                }
                if(rich!=null)items((rich.size-1).coerceAtLeast(0),key={"rich-${it+1}"}) {index ->
                    Box(bodyMotion) {RichReaderSection(rich[index+1],index+1,richReveal)}
                }
            } else items(article.sections,key={it.id}) {section ->
                Column(Modifier.fillMaxWidth().padding(top=28.dp).then(bodyMotion).testTag("section-"+section.id)) {
                    Text(section.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=12.dp))
                    section.paragraphs.forEach {Text(it,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(bottom=16.dp))}
                }
            }
        }
        overlay()
    }
}

@Composable
private fun RichReaderSection(section: RichSection,index: Int,reveal: ()->Float) {
    Column(Modifier.fillMaxWidth().padding(top=24.dp).graphicsLayer {
        val progress=reveal()
        alpha=progress;translationY=24.dp.toPx()*(1f-progress)
    }.testTag("section-rich-$index"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        section.heading?.let {Text(it,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics {heading()})}
        section.blocks.forEach {RichContent(it)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: ()->Unit,onAuthor: (String)->Unit,onNavigate: (Destination)->Unit,origin: Destination,model: ReaderViewModel=viewModel()) {
    val expansion=LocalArticleExpansion.current
    val business = LocalBusiness.current
    val owner = business?.session?.collectAsStateWithLifecycle()?.value?.account?.id
    val fromCardTransition=remember(model.articleId) {expansion?.isOpeningReader(model.articleId)==true}
    val openingLoad=remember(model.articleId) {expansion?.openingLoad.takeIf {fromCardTransition}}
    var currentLoad by remember(business,model.articleId) {mutableStateOf(openingLoad ?: ReaderLoad())}
    val load=currentLoad
    val remote = business?.let {
        suspend fun parse(article: Article) = article to withContext(Dispatchers.Default) {
            RichArticle.parse(article.markdown.orEmpty(), it.mediaUrl("/") ?: "https://api.semi-overt.com/")
        }
        rememberRequest(it, model.articleId, load, readLocal = {
            val cached=if(load===openingLoad)load.awaitCache() else load.checkCache {it.cachedArticle(model.articleId)}
            cached?.let {article->parse(article)}
        }) {
            parse(it.articleDetail(model.articleId))
        }
    }
    val article = if (business == null) model.article else remote?.first?.value?.first
        ?: expansion?.source?.article?.takeIf { it.id == model.articleId } ?: business.knownArticle(model.articleId)
    val motion=LocalMotionEnabled.current
    val playback=load.playback
    var bodyShown by rememberSaveable(model.articleId) {mutableStateOf(false)}
    val rich=remote?.first?.value?.second
    val loading=rich==null&&remote?.first?.loading==true
    val body=rich?.takeIf {!motion||bodyShown||!load.cacheMissed||playback.firstCycleFinished}
    val waitingForBody=load.cacheMissed&&body==null&&(loading||rich!=null)
    val error=remote?.first?.error?.takeIf {rich==null}
    val retry: ()->Unit={currentLoad=ReaderLoad()}
    if(article==null) {
        Box(Modifier.fillMaxSize()) {
            PageFrame("阅读",onBack,"reader-unavailable",scroll=false) {
                if(!loading)RemoteState(error ?: "未找到文章",Modifier.fillMaxSize(),action=error?.let {"重试"},onAction=retry)
            }
            ContentLoading(waitingForBody,"正在加载文章",Modifier.align(Alignment.Center),playback=playback)
        }
        return
    }
    val richReveal=remember(article.id) {Animatable(if(motion)0f else 1f)}
    LaunchedEffect(body!=null,motion) {
        if(body!=null) {
            bodyShown=true
            if(motion)richReveal.animateTo(1f,tween(SceneMotion.READING_BODY_MS,easing=SoSceneEase))
            else richReveal.snapTo(1f)
        }
    }
    var docked by rememberSaveable(article.id) {mutableStateOf(fromCardTransition||!motion)}
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
    LaunchedEffect(business, article.id, owner, body!=null) {
        if (business == null || body == null) return@LaunchedEffect
        var saved = business.readingPosition(article.id, owner)
        val count = snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 2 }
        if (saved != null && !rewinding && !list.isScrollInProgress &&
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) {
            list.scrollToItem(saved.index.coerceIn(0, count-1), saved.offset)
        }
        snapshotFlow {
            if (rewinding || list.isScrollInProgress || expansion?.active == true) null
            else ReadingPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        }.filterNotNull().collect { current ->
            if (current != saved) {
                business.saveReadingPosition(article.id, owner, current)
                saved = current
            }
        }
    }
    val contentsOpen by model.contentsOpen.collectAsStateWithLifecycle()
    val close by rememberUpdatedState<()->Unit> {
        if(expansion==null)onBack()
        else {
            val reveal=richReveal.value
            expansion.close(ReaderReturnContent(article) {anchors,bodyProgress,avatarProgress ->
                ReaderArticleLayout(article,rememberLazyListState(),{},anchors,bodyProgress=bodyProgress,
                    avatarProgress=avatarProgress,tag="reader-preview",rich=body,richReveal={reveal})
            },onBack)
        }
    }
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
        ReaderArticleLayout(article,list,onAuthor,interactive=!rewinding,rich=body,richReveal={richReveal.value},
            remoteBody = if (remote != null) {{
                if(error!=null)RemoteState(error,action="重试",onAction=retry)
            }} else null) {
            if(remote!=null)ContentLoading(waitingForBody,READER_BODY_LOADING,Modifier.align(Alignment.Center),
                playback=playback,initiallyVisible=waitingForBody&&openingLoad===load)
        }
    }
    if(contentsOpen)SoBottomSheet(onDismissRequest={model.contents(false)}) {close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=24.dp,end=24.dp,bottom=24.dp).testTag("reader-contents")) {
            Text("目录",style=MaterialTheme.typography.headlineSmall.withInterfaceTypeface(),modifier=Modifier.padding(bottom=16.dp))
            val headings = if (business != null) body.orEmpty().mapIndexedNotNull { index, section ->
                section.heading?.let { index to it }
            } else article.sections.mapIndexed { index, section -> index to section.title }
            if(waitingForBody)ContentLoading(true,"正在加载目录")
            else if(error!=null)RemoteState(error,action="重试",onAction=retry)
            else if(headings.isEmpty())RemoteState("无目录")
            headings.forEach {(index,title)->TextButton(onClick={close {coroutine.launch {list.scrollToItem(index+2)}}},
                colors=accentTextButtonColors(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
            }}
        }
    }
}
