package com.semiovert.app.feature.category

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.ArticleFeed
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
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
    val lists=rememberSaveableStateHolder()
    val scope=rememberCoroutineScope()
    val roll=remember {Animatable(0f)}
    val inheritedRoll=LocalFlowRoll.current
    val motion=LocalMotionEnabled.current
    var changing by remember {mutableStateOf(false)}
    val cycle: ()->Unit={if(!changing)scope.launch {changing=true;try {roll.animateTo(1f,tween(if(motion)1000 else 0,easing=FastOutLinearInEasing));model.cycle();roll.animateTo(0f,tween(if(motion)1000 else 0,easing=SoEase))}finally {changing=false}};Unit}
    BrowseScene(Destination.CATEGORY,duration.label,onNavigate,onPrimary=cycle) {
        CompositionLocalProvider(LocalFlowRoll provides maxOf(inheritedRoll,roll.value)) {
        lists.SaveableStateProvider(durationName) {
            val articles=SampleLibrary.inDuration(duration)
            var selected by rememberSaveable {mutableIntStateOf(0)}
            BoxWithConstraints {
                if(motion&&LocalDensity.current.fontScale<=1.2f&&maxHeight>=440.dp&&maxHeight>maxWidth&&articles.isNotEmpty()) {
                    WaveArticleDeck(articles,selected,{selected=it},onArticle)
                } else {
                    CompositionLocalProvider(LocalMotionEnabled provides false) {
                        ArticleFeed(articles,rememberLazyListState(initialFirstVisibleItemIndex=selected),"feed-category",onArticle,topPadding=64,
                            emptyMessage="这个时长还没有文章",emptyHint="点主圆，换一个阅读时长。")
                    }
                }
            }
        }
        }
    }
}
