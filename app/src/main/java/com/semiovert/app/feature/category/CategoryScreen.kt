package com.semiovert.app.feature.category

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val remote = business?.let { rememberPaged(it, duration, id = Article::id) { page -> it.category(duration, page) } }
    val labelFrame=rememberScrambleText(duration.label)
    val lists=rememberSaveableStateHolder()
    val scope=rememberCoroutineScope()
    val roll=remember {Animatable(0f)}
    val inheritedRoll=LocalFlowRoll.current
    val inheritedPhase=LocalFlowPhase.current
    val motion=LocalMotionEnabled.current
    var changing by remember {mutableStateOf(false)}
    var phase by remember {mutableStateOf(FlowPhase.IDLE)}
    val swapping=changing&&inheritedPhase==FlowPhase.IDLE
    val swapMillis=if(phase==FlowPhase.ENTER)SceneMotion.CATEGORY_SWAP_ENTER_MS else SceneMotion.CATEGORY_SWAP_MS
    val cycle: ()->Unit={if(!changing&&inheritedPhase==FlowPhase.IDLE)scope.launch {changing=true;try {
        phase=FlowPhase.EXIT;roll.animateTo(1f,tween(if(motion)SceneMotion.CATEGORY_SWAP_MS else 0,easing=LinearEasing))
        model.cycle();phase=FlowPhase.ENTER;roll.animateTo(0f,tween(if(motion)SceneMotion.CATEGORY_SWAP_ENTER_MS else 0,easing=LinearEasing))
    }finally {phase=FlowPhase.IDLE;changing=false}};Unit}
    BrowseScene(Destination.CATEGORY,"",onNavigate,onPrimary=cycle) {
        CompositionLocalProvider(LocalFlowRoll provides if(swapping)roll.value else inheritedRoll,
            LocalFlowPhase provides if(swapping)phase else inheritedPhase) {
        lists.SaveableStateProvider(durationName) {
            val articles=remote?.first?.items ?: SampleLibrary.inDuration(duration)
            var selected by rememberSaveable {mutableIntStateOf(0)}
            BoxWithConstraints {
                if (articles.isEmpty() && remote != null && (remote.first.loading || remote.first.error != null)) {
                    val error = remote.first.error
                    RemoteState(error ?: "正在加载分类…", Modifier.align(Alignment.Center), error?.let { "重新加载" }, remote.second)
                } else if(motion&&LocalDensity.current.fontScale<=1.2f&&maxHeight>=440.dp&&maxHeight>maxWidth&&articles.isNotEmpty()) {
                    WaveArticleDeck(articles,selected.coerceIn(0,articles.lastIndex),{selected=it},onArticle,visibleLimit=WaveDeckMath.CATEGORY_VISIBLE,
                        sceneDurationMillis=if(swapping)swapMillis else null,
                        sceneStaggerMillis=if(swapping)SceneMotion.CATEGORY_SWAP_STAGGER_MS else SceneMotion.DECK_STAGGER_MS,
                        entranceEase=if(swapping)SceneMotion.categorySwapEntranceEase else SceneMotion.categoryRouteEntranceEase,
                        categoryLabel=duration.label,categoryLabelFrame=labelFrame,openWithLift=true,
                        frontTop=WaveDeckMath.restingFrontTop(maxWidth.value,maxHeight.value,maxHeight.value).dp)
                } else {
                    val list=rememberLazyListState(initialFirstVisibleItemIndex=selected)
                    CompositionLocalProvider(LocalMotionEnabled provides false) {
                        Column {
                            DeckCounter(if(articles.isEmpty())0 else list.firstVisibleItemIndex+1,articles.size,Destination.CATEGORY.route,
                                categoryLabel=duration.label,compact=true,categoryLabelFrame=labelFrame)
                            ArticleFeed(articles,list,"feed-category",onArticle,
                                emptyMessage="这个时长还没有文章",emptyHint="点主圆，换一个阅读时长。",categoryCards=true,
                                openWithLift=true,displayEmptyCopy=true)
                        }
                    }
                }
                if (remote != null && articles.isNotEmpty() && remote.first.hasNext) {
                    MoreResults(remote.first.loading, remote.first.error, remote.second,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
                }
            }
        }
        }
    }
}
