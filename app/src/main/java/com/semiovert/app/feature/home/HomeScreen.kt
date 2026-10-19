package com.semiovert.app.feature.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.ArcArticleDeck
import com.semiovert.app.navigation.*

class HomeViewModel : ViewModel() { val articles = SampleLibrary.articles }

@Composable
fun HomeScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,model: HomeViewModel=viewModel()) {
    var articleIndex by rememberSaveable {mutableIntStateOf(0)}
    var firstEntrancePlayed by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val ready=LocalIntroReady.current
    val entrance=remember {Animatable(if(firstEntrancePlayed||!motion)0f else 1f)}
    val inherited=LocalFlowRoll.current
    LaunchedEffect(ready,motion) {if(ready) {entrance.animateTo(0f,tween(if(motion)1000 else 0,easing=SoEase));firstEntrancePlayed=true}}
    BrowseScene(Destination.HOME,"本地示例",onNavigate) {
        CompositionLocalProvider(LocalFlowRoll provides maxOf(inherited,entrance.value)) {
        BoxWithConstraints {
            if(motion&&LocalDensity.current.fontScale<=1.2f&&maxHeight>=440.dp&&maxHeight>maxWidth&&model.articles.isNotEmpty()) {
                ArcArticleDeck(model.articles,articleIndex,{articleIndex=it},onArticle)
            } else {
                val listState=rememberLazyListState(initialFirstVisibleItemIndex=articleIndex)
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    ArticleFeed(model.articles,listState,"feed-home",onArticle,topPadding=24)
                }
            }
        }
        }
    }
}
