package com.semiovert.app.feature.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.data.RequestState
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.ArcArticleDeck
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import com.semiovert.app.core.model.Article
import com.semiovert.app.navigation.*

private const val SHOW_HOME_CARDS = true

class HomeViewModel : ViewModel() { val articles get() = SampleLibrary.articles }

@Composable
internal fun HomeScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,model: HomeViewModel=viewModel(),
    onToggleTheme: (() -> Unit)?=null,remote: Pair<RequestState<List<Article>>, ()->Unit>?=null,
    onEntranceComplete: ()->Unit={}) {
    val articles = if (remote == null) model.articles else remote.first.value.orEmpty()
    var articleIndex by rememberSaveable {mutableIntStateOf(0)}
    var firstEntrancePlayed by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val ready=LocalIntroReady.current
    val preparation=LocalIntroPreparation.current
    var deckLaidOut by remember(preparation) {mutableStateOf(false)}
    SideEffect {preparation?.ready=deckLaidOut}
    val entrance=remember {Animatable(if(firstEntrancePlayed||!motion)0f else 1f)}
    val inherited=LocalFlowRoll.current
    val inheritedPhase=LocalFlowPhase.current
    val entry=LocalHomeEntry.current
    var handledEntry by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(entry){if(entry!=handledEntry){articleIndex=0;handledEntry=entry}}
    LaunchedEffect(ready,motion) {
        if(ready) {
            entrance.animateTo(0f,tween(if(motion)SceneMotion.HOME_ENTER_MS else 0,easing=LinearEasing))
            firstEntrancePlayed=true
            onEntranceComplete()
        }
    }
    val snapshot=LocalHomeSnapshot.current
    Box(Modifier.fillMaxSize().then(
        if(snapshot==null)Modifier else Modifier.drawWithContent {snapshot.drawHome(this)}
    )) {
    BrowseScene(Destination.HOME,"首页",onNavigate,onToggleTheme=onToggleTheme) {
        CompositionLocalProvider(LocalFlowRoll provides if(inheritedPhase==FlowPhase.EXIT)inherited else maxOf(inherited,entrance.value),
            LocalFlowPhase provides if(inheritedPhase==FlowPhase.EXIT)FlowPhase.EXIT else if(!firstEntrancePlayed)FlowPhase.ENTER else inheritedPhase) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
            if(preparation!=null&&!deckLaidOut)deckLaidOut=true
        }) {
            if(SHOW_HOME_CARDS) {
                val density=LocalDensity.current
                val safeTop=WindowInsets.safeDrawing.getTop(density)/density.density+76f
                val safeBottom=maxHeight.value-WindowInsets.safeDrawing.getBottom(density)/density.density-86f-140f*LocalNavigationPlacement.current.y
                val geometry=ArcDeckMath.geometry(maxWidth.value,maxHeight.value)
                if (remote?.first?.loading == true) {
                    RemoteState("首页加载中", Modifier.align(Alignment.Center))
                } else if (remote?.first?.error != null) {
                    RemoteState(remote.first.error!!, Modifier.align(Alignment.Center), "重试", remote.second)
                } else if(motion&&density.fontScale<=1.2f&&maxHeight>maxWidth&&ArcDeckMath.canPull(geometry,safeTop,safeBottom)) {
                    // The 48dp theme button starts at safeTop; reserve a further 14dp avatar gap.
                    ArcArticleDeck(articles,articleIndex,{articleIndex=it},onArticle,safeTop,safeBottom,
                        expandedAvatarSafeTop=safeTop+48f+14f)
                } else {
                    val listState=rememberLazyListState(initialFirstVisibleItemIndex=articleIndex.coerceIn(0,articles.lastIndex.coerceAtLeast(0)))
                    CompositionLocalProvider(LocalMotionEnabled provides false) {
                        ArticleFeed(articles,listState,"feed-home",onArticle,topPadding=24)
                    }
                }
            }
        }
        }
    }
    }
}
