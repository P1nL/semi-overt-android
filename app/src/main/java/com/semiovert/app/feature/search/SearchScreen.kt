package com.semiovert.app.feature.search

import android.os.SystemClock
import androidx.compose.ui.text.AnnotatedString
import com.semiovert.app.core.designsystem.wave.DeckCounter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.rememberPaged
import com.semiovert.app.core.model.Article
import com.semiovert.app.core.model.Author
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import com.semiovert.app.core.designsystem.wave.DeckCounterLayout
import com.semiovert.app.core.model.SearchScope
import com.semiovert.app.navigation.*
import kotlinx.coroutines.launch

class SearchViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val query=savedState.getStateFlow("query","")
    val committedQuery=savedState.getStateFlow("committed-query","")
    val scope=savedState.getStateFlow("scope",SearchScope.ARTICLES.name)
    private val gate=SearchSubmissionGate()
    private val busy=MutableStateFlow(false)
    val submitting=busy.asStateFlow()
    fun query(value: String) { savedState["query"]=value }
    fun beginSubmission(clear: Boolean=false): String? {
        if(clear&&!gate.busy&&committedQuery.value.isBlank()) {query("");return null}
        return gate.begin(if(clear)"" else query.value,committedQuery.value,SystemClock.elapsedRealtime(),explicitClear=clear)?.also {
            busy.value=true
            if(clear)query("")
        }
    }
    fun commit(value: String) {savedState["committed-query"]=value}
    fun finishSubmission() {gate.finish();busy.value=false}
    fun selectScope(value: SearchScope) {
        if(value.name!=scope.value&&gate.switchScope(SystemClock.elapsedRealtime()))savedState["scope"]=value.name
    }
    fun toggleScope()=selectScope(if(scope.value==SearchScope.ARTICLES.name)SearchScope.AUTHORS else SearchScope.ARTICLES)
}

@Composable
fun SearchScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,onAuthor: (String)->Unit,model: SearchViewModel=viewModel()) {
    val query by model.query.collectAsStateWithLifecycle()
    val committedQuery by model.committedQuery.collectAsStateWithLifecycle()
    val changing by model.submitting.collectAsStateWithLifecycle()
    val scopeName by model.scope.collectAsStateWithLifecycle()
    val searchScope=SearchScope.valueOf(scopeName)
    val scopeTransition=updateTransition(searchScope,label="search-scope")
    val outgoingScope=scopeTransition.currentState
    val switchingScope=outgoingScope!=searchScope
    val typography=MaterialTheme.typography
    val motion=LocalMotionEnabled.current
    val returning=LocalArticleExpansion.current?.returning==true
    val routeRoll=LocalFlowRoll.current
    val routePhase=LocalFlowPhase.current
    val headerProgress=if(motion)SceneMotion.searchHeader(1f-routeRoll) else 1f
    val identityProgress=if(LocalIdentityHandoffActive.current)LocalIdentityArrival.current else headerProgress
    val density=LocalDensity.current.density
    val articleList=key(committedQuery) {rememberLazyListState()}
    val authorList=key(committedQuery) {rememberLazyListState()}
    val coroutine=rememberCoroutineScope()
    val keyboard=LocalSoftwareKeyboardController.current
    val business = LocalBusiness.current
    val remoteArticles = if (business != null && committedQuery.isNotBlank() &&
        (searchScope==SearchScope.ARTICLES||outgoingScope==SearchScope.ARTICLES))
        rememberPaged(business, committedQuery, id = Article::id) { business.searchArticles(committedQuery, it) } else null
    val remoteAuthors = if (business != null && committedQuery.isNotBlank() &&
        (searchScope==SearchScope.AUTHORS||outgoingScope==SearchScope.AUTHORS))
        rememberPaged(business, committedQuery, id = Author::id) { business.searchAuthors(committedQuery, it) } else null
    val articles=if (business != null) remoteArticles?.first?.items.orEmpty() else
        remember(committedQuery) {if(committedQuery.isBlank())emptyList() else SampleLibrary.searchArticles(committedQuery)}
    var selectedArticle by rememberSaveable(committedQuery) {mutableIntStateOf(0)}
    val authors=if (business != null) remoteAuthors?.first?.items.orEmpty() else
        remember(committedQuery) {if(committedQuery.isBlank())emptyList() else SampleLibrary.searchAuthors(committedQuery)}
    val request=if(searchScope==SearchScope.ARTICLES)remoteArticles?.first else remoteAuthors?.first
    val ready=request?.items?.isNotEmpty()==true
    val animateArrival=remember(request,ready) {!switchingScope}
    val requestRoll by rememberResultsEntrance(request.takeIf {animateArrival},ready,SceneMotion.CATEGORY_SWAP_MS)
    val swapRoll=remember {Animatable(0f)}
    var swapPhase by remember {mutableStateOf(FlowPhase.IDLE)}
    var submission by remember {mutableStateOf<Job?>(null)}
    LaunchedEffect(routePhase) {if(routePhase!=FlowPhase.IDLE)submission?.cancel()}
    val submit: (Boolean)->Unit={clear ->
        keyboard?.hide()
        if(routePhase==FlowPhase.IDLE&&routeRoll<.001f) {
            val requested=model.beginSubmission(clear)
            if(requested!=null)submission=coroutine.launch {
                try {
                    swapRoll.snapTo(0f)
                    val hasOldResults=if(searchScope==SearchScope.ARTICLES)articles.isNotEmpty() else authors.isNotEmpty()
                    if(hasOldResults) {
                        swapPhase=FlowPhase.EXIT
                        swapRoll.animateTo(1f,tween(if(motion)SceneMotion.CATEGORY_SWAP_MS else 0,easing=LinearEasing))
                    } else swapRoll.snapTo(1f)
                    model.commit(requested)
                    if(business==null) {
                        swapPhase=FlowPhase.ENTER
                        withFrameNanos {}
                        val hasNewResults=requested.isNotBlank()&&if(searchScope==SearchScope.ARTICLES)
                            SampleLibrary.searchArticles(requested).isNotEmpty() else SampleLibrary.searchAuthors(requested).isNotEmpty()
                        swapRoll.animateTo(0f,tween(if(motion&&hasNewResults)SceneMotion.CATEGORY_SWAP_MS else 0,easing=LinearEasing))
                    }
                } finally {swapPhase=FlowPhase.IDLE;model.finishSubmission()}
            }
        }
    }
    BrowseScene(Destination.SEARCH,"搜索",onNavigate,onPrimary=model::toggleScope,header={openProfile ->
        val headerGutter=12.dp
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=headerGutter,vertical=8.dp)) {
            val sharedIdentity=LocalIdentityHandoffActive.current
            val travel=(maxWidth-56.dp-8.dp).value*density
            Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=openProfile,modifier=Modifier.size(56.dp).graphicsLayer {
                translationX=if(sharedIdentity)0f else travel*(1f-identityProgress)
            }.semantics {contentDescription="打开个人页"}) {
                IdentityAvatar(size=56)
            }
            Box(Modifier.weight(1f).padding(start=8.dp).identityCompanionOverlay()) {
            StitchedTextField(value=query,onValueChange=model::query,modifier=Modifier.fillMaxWidth().height(56.dp).graphicsLayer {
                translationX=(size.width+headerGutter.toPx()+2.dp.toPx())*(1f-identityProgress)
            }.testTag("search-input"),
                singleLine=true,placeholder={Text(if(searchScope==SearchScope.ARTICLES)"搜索文章标题或作者" else "搜索作者昵称或简介",
                    style=typography.bodyLarge.withInterfaceTypeface(),maxLines=1,overflow=TextOverflow.Ellipsis)},
                visualTransformation=InterfaceDigitTransformation,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(false)}),
                trailingIcon={if(query.isNotEmpty()||committedQuery.isNotBlank())
                    IconButton(onClick={submit(true)},enabled=!changing&&routePhase==FlowPhase.IDLE) {SoIcon(SoSymbol.CLOSE,"清空搜索")}})
            }
            }
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val referenceHeight=maxHeight
        val useWave=motion&&LocalDensity.current.fontScale<=1.2f&&referenceHeight>=440.dp&&referenceHeight>maxWidth
        val resultMotion=Modifier.graphicsLayer {alpha=headerProgress;translationY=32f*density*(1f-headerProgress)}
        Column(Modifier.fillMaxSize().padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+DeckCounterLayout.SEARCH_HEADER_TOP.dp)) {
            Row(Modifier.fillMaxWidth().height(DeckCounterLayout.SEARCH_SCOPE_HEIGHT.dp).clipToBounds()
                .padding(horizontal=20.dp).then(resultMotion).readingChromeFade(),verticalAlignment=Alignment.CenterVertically) {
                SearchScope.entries.forEach { item ->
                    TextButton(onClick={model.selectScope(item)},enabled=!changing,modifier=Modifier.semantics {selected=searchScope==item}) {
                        Text(item.label,style=typography.labelLarge.withInterfaceTypeface(),color=if(searchScope==item) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if(item==SearchScope.AUTHORS)RollingAuthorCount(remoteAuthors?.first?.total ?: authors.size)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(switchingScope) {
                if(switchingScope)awaitPointerEventScope {
                    while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
                }
            }) {
            scopeTransition.AnimatedContent(modifier=Modifier.fillMaxSize(),transitionSpec={
                val direction=if(targetState==SearchScope.AUTHORS)1 else -1
                (slideInHorizontally(tween(if(motion)300 else 0,easing=SoEase)) {it*direction} togetherWith slideOutHorizontally(tween(if(motion)300 else 0,easing=SoEase)) {-it*direction}).using(SizeTransform(clip=false))
            }) {visibleScope ->
            val localSwap=changing&&routePhase==FlowPhase.IDLE
            val remoteEntry=motion&&!returning&&visibleScope==searchScope&&request?.items?.isNotEmpty()==true&&requestRoll>0f&&routePhase!=FlowPhase.EXIT
            val roll=when {
                localSwap->swapRoll.value
                remoteEntry->maxOf(requestRoll,routeRoll)
                else->routeRoll
            }
            val resultSwap=Modifier.graphicsLayer {
                if(localSwap||remoteEntry) {alpha=1f-roll;translationY=32f*density*roll}
            }
            CompositionLocalProvider(LocalFlowRoll provides roll,
                LocalFlowPhase provides when {
                    localSwap->swapPhase
                    remoteEntry||switchingScope&&routePhase==FlowPhase.IDLE->FlowPhase.ENTER
                    else->routePhase
                }) {
            if(visibleScope==SearchScope.ARTICLES) BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val articleRequest = remoteArticles?.first
                val error = articleRequest?.error
                val index=if(useWave)selectedArticle else articleList.firstVisibleItemIndex
                DeckCounter(if(articles.isEmpty())0 else index.coerceAtMost(articles.lastIndex)+1,
                    articleRequest?.total ?: articles.size,"search",compact=!useWave,
                    loadingMessage="正在搜索文章".takeIf {articles.isEmpty()&&articleRequest?.loading==true})
                if (articles.isEmpty() && error!=null) {
                    RemoteState(error,Modifier.align(Alignment.Center),"重试",remoteArticles.second)
                } else if(articles.isNotEmpty()&&useWave)
                    WaveArticleDeck(articles,selectedArticle,{selectedArticle=it},onArticle,listTag="feed-search",deckTag="search-wave",referenceHeight=referenceHeight,
                        visibleLimit=WaveDeckMath.SEARCH_VISIBLE,showCounter=false,
                        entranceEase=if(localSwap||remoteEntry)SceneMotion.searchSwapEntranceEase else SoSceneEase,
                        sceneDurationMillis=if(localSwap||remoteEntry)SceneMotion.CATEGORY_SWAP_MS else SceneMotion.SEARCH_ENTER_MS,
                        sceneStaggerMillis=if(localSwap||remoteEntry)SceneMotion.CATEGORY_SWAP_STAGGER_MS else SceneMotion.SEARCH_STAGGER_MS,
                        frontTop=WaveDeckMath.restingFrontTop(maxWidth.value,maxHeight.value,referenceHeight.value).dp)
                else if(committedQuery.isNotBlank()&&articleRequest?.loading!=true)ArticleFeed(articles,articleList,"feed-search",onArticle,emptyMessage="未找到文章",
                    modifier=Modifier.fillMaxSize().then(resultMotion).then(resultSwap),
                    categoryCards=true,displayEmptyCopy=true)
                if (remoteArticles != null && articles.isNotEmpty() && remoteArticles.first.hasNext)
                    MoreResults(remoteArticles.first.loading, remoteArticles.first.error, remoteArticles.second,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
            }
            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                val authorRequest=remoteAuthors?.first
                val waiting=authors.isEmpty()&&authorRequest?.loading==true
                val error=authorRequest?.error
                val stateMotion=resultMotion.then(resultSwap)
                if(authors.isNotEmpty()) {
                    LazyColumn(state=authorList,modifier=Modifier.fillMaxSize().then(resultSwap).testTag("author-results"),
                        contentPadding=PaddingValues(20.dp,20.dp,20.dp,128.dp),
                        verticalArrangement=Arrangement.spacedBy(28.dp)) {
                        items(authors,key={it.id}) {author ->
                            Row(Modifier.fillMaxWidth().sparkClickable(enabled=!changing,onClickLabel="查看作者主页") {onAuthor(author.id)}.testTag("author-"+author.id),verticalAlignment=Alignment.Top) {
                                AuthorAvatar(author.id,modifier=if(authorAvatarInHandoff(author.id))Modifier else resultMotion)
                                Column(Modifier.weight(1f).padding(start=16.dp).then(resultMotion)) {
                                    Text(author.name,style=typography.titleMedium.withInterfaceTypeface())
                                    Text(author.bio,style=typography.bodyMedium.withInterfaceTypeface(),modifier=Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    val secondary = if (business == null) "${SampleLibrary.articles.count {it.author.id==author.id}} 篇文章"
                                        else author.handle.takeIf { it.isNotBlank() }?.let { "@$it" }
                                    if (secondary != null) Text(secondary.withInterfaceDigits(),style=typography.labelMedium,modifier=Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if(authorRequest?.hasNext==true) item("more-authors") {
                            MoreResults(authorRequest.loading,error,remoteAuthors.second,loadingMessage="正在加载更多作者")
                        }
                    }
                } else if(error!=null) {
                    RemoteState(error,stateMotion,"重试",remoteAuthors.second)
                } else if(!waiting&&committedQuery.isNotBlank()) {
                    EmptyContent("未找到作者",SoSymbol.PERSON,stateMotion,typography.titleMedium.withInterfaceTypeface())
                }
                ContentLoading(waiting,"正在搜索作者",resultMotion)
            }
            }
            }
            }
        }
        }
    }
}

@Composable
private fun RollingAuthorCount(count: Int) {
    val duration=motionMillis(360)
    AnimatedContent(count,modifier=Modifier.padding(start=4.dp).clipToBounds().testTag("search-author-count")
        .clearAndSetSemantics {text=AnnotatedString(count.toString())},label="search-author-count",transitionSpec={
        val direction=if(targetState>initialState)1 else -1
        (slideInVertically(tween(duration)){it*direction}+fadeIn(tween(duration))) togetherWith
            (slideOutVertically(tween(duration)){-it*direction}+fadeOut(tween(duration)))
    }) {value ->Text(value.toString().withInterfaceDigits(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
