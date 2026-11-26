package com.semiovert.app.feature.category

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.rememberPaged
import com.semiovert.app.core.model.Article
import com.semiovert.app.core.designsystem.ArticleFeed
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.designsystem.wave.DeckCounter
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.navigation.*

class CategoryViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val duration=savedState.getStateFlow("duration",ReadingDuration.QUICK.name)
    fun cycle() { savedState["duration"]=ReadingDuration.valueOf(duration.value).next().name }
}

@Composable
fun CategoryScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,model: CategoryViewModel=viewModel()) {
    val durationName by model.duration.collectAsStateWithLifecycle()
    val duration=ReadingDuration.valueOf(durationName)
    val business = LocalBusiness.current
    val remote = business?.let { rememberPaged(it, duration, id = Article::id) {
        page -> it.category(duration, page)
    } }
    val request=remote?.first
    val articles=request?.items ?: remember(duration) {SampleLibrary.inDuration(duration)}
    val loading=request?.loading==true&&articles.isEmpty()
    val error=request?.error
    val labelFrame=rememberScrambleText(duration.label)
    val lists=rememberSaveableStateHolder()
    val scope=rememberCoroutineScope()
    val roll=remember {Animatable(0f)}
    val inheritedRoll=LocalFlowRoll.current
    val inheritedPhase=LocalFlowPhase.current
    val motion=LocalMotionEnabled.current
    val returning=LocalArticleExpansion.current?.returning==true
    var changing by remember {mutableStateOf(false)}
    var phase by remember {mutableStateOf(FlowPhase.IDLE)}
    var focused by remember(durationName) {mutableIntStateOf(0)}
    val swapping=changing&&inheritedPhase==FlowPhase.IDLE
    val requestRoll by rememberResultsEntrance(request,request!=null&&articles.isNotEmpty(),SceneMotion.CATEGORY_SWAP_ENTER_MS)
    val swapMillis=if(phase==FlowPhase.ENTER)SceneMotion.CATEGORY_SWAP_ENTER_MS else SceneMotion.CATEGORY_SWAP_MS
    val cycle: ()->Unit={if(!changing&&inheritedPhase==FlowPhase.IDLE&&(!motion||articles.isEmpty()||requestRoll<.001f))scope.launch {changing=true;try {
        phase=FlowPhase.EXIT
        roll.snapTo(0f)
        roll.animateTo(1f,tween(if(motion&&articles.isNotEmpty())SceneMotion.CATEGORY_SWAP_MS else 0,easing=LinearEasing))
        model.cycle()
        if(request==null) {
            phase=FlowPhase.ENTER;roll.animateTo(0f,tween(if(motion)SceneMotion.CATEGORY_SWAP_ENTER_MS else 0,easing=LinearEasing))
        }
    }finally {phase=FlowPhase.IDLE;changing=false}};Unit}
    BrowseScene(Destination.CATEGORY,"",onNavigate,onPrimary=cycle) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density=LocalDensity.current
            val useWave=motion&&density.fontScale<=1.2f&&maxHeight>=440.dp&&maxHeight>maxWidth
            val frontTop=WaveDeckMath.restingFrontTop(maxWidth.value,maxHeight.value,maxHeight.value)
            val occlusionTop=if(useWave&&articles.isNotEmpty()) {
                val geometry=WaveDeckMath.embeddedGeometry(maxWidth.value,maxHeight.value,articles.size,maxHeight.value,
                    frontTop,WaveDeckMath.CATEGORY_VISIBLE,density.fontScale)
                WaveDeckMath.stackTop((minOf(articles.size,WaveDeckMath.CATEGORY_VISIBLE)-1).toFloat(),geometry).dp
            } else null
            val counter: @Composable ()->Unit = {
                DeckCounter(if(articles.isEmpty())0 else focused.coerceAtMost(articles.lastIndex)+1,
                    request?.total ?: articles.size,Destination.CATEGORY.route,categoryLabel=duration.label,compact=!useWave,
                    categoryLabelFrame=labelFrame,counterOcclusionTop=occlusionTop,loadingMessage="正在加载分类".takeIf {loading})
            }
            val results: @Composable ()->Unit = {
                val remoteEntry=motion&&!returning&&request!=null&&articles.isNotEmpty()&&requestRoll>0f
                val visiblePhase=when {
                    swapping->phase
                    inheritedPhase==FlowPhase.EXIT->inheritedPhase
                    remoteEntry->FlowPhase.ENTER
                    else->inheritedPhase
                }
                val visibleRoll=when {
                    swapping->roll.value
                    inheritedPhase==FlowPhase.EXIT->inheritedRoll
                    remoteEntry->maxOf(inheritedRoll,requestRoll)
                    else->inheritedRoll
                }
                CompositionLocalProvider(LocalFlowRoll provides visibleRoll,LocalFlowPhase provides visiblePhase) {
                    lists.SaveableStateProvider(durationName) {
                        var selected by rememberSaveable {mutableIntStateOf(0)}
                        Box(Modifier.fillMaxSize()) {
                            when {
                                loading->Unit
                                articles.isEmpty()&&error!=null->
                                    RemoteState(error,Modifier.align(Alignment.Center),"重试",remote.second)
                                useWave&&articles.isNotEmpty()->WaveArticleDeck(articles,selected.coerceIn(0,articles.lastIndex),
                                    {selected=it},onArticle,visibleLimit=WaveDeckMath.CATEGORY_VISIBLE,showCounter=false,
                                    sceneDurationMillis=if(swapping)swapMillis else if(remoteEntry)SceneMotion.CATEGORY_SWAP_ENTER_MS else null,
                                    sceneStaggerMillis=if(swapping||remoteEntry)SceneMotion.CATEGORY_SWAP_STAGGER_MS else SceneMotion.DECK_STAGGER_MS,
                                    entranceEase=if(swapping||remoteEntry)SceneMotion.categorySwapEntranceEase else SceneMotion.categoryRouteEntranceEase,
                                    openWithLift=true,frontTop=frontTop.dp,onFocused={focused=it},usePaletteCover=true)
                                else->{
                                    val list=rememberLazyListState(initialFirstVisibleItemIndex=selected)
                                    SideEffect {focused=list.firstVisibleItemIndex}
                                    CompositionLocalProvider(LocalMotionEnabled provides false) {
                                        ArticleFeed(articles,list,"feed-category",onArticle,
                                            modifier=Modifier.graphicsLayer {
                                                if(request!=null&&articles.isNotEmpty()) {
                                                    alpha=1f-visibleRoll;translationY=32.dp.toPx()*visibleRoll
                                                }
                                            },categoryCards=true,openWithLift=true,displayEmptyCopy=true,usePaletteCover=true)
                                    }
                                }
                            }
                            if (remote!=null&&articles.isNotEmpty()&&request?.hasNext==true) {
                                MoreResults(request.loading,error,remote.second,
                                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=88.dp))
                            }
                        }
                    }
                }
            }
            if(useWave) {counter();results()} else Column {counter();Box(Modifier.weight(1f)) {results()}}
        }
    }
}
