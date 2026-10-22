package com.semiovert.app.feature.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.arc.ArcArticleDeck
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import com.semiovert.app.navigation.*

class HomeViewModel : ViewModel() { val articles = SampleLibrary.articles }

@Composable
fun HomeScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,model: HomeViewModel=viewModel(),onToggleTheme: (() -> Unit)?=null) {
    var articleIndex by rememberSaveable {mutableIntStateOf(0)}
    var firstEntrancePlayed by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val ready=LocalIntroReady.current
    val entrance=remember {Animatable(if(firstEntrancePlayed||!motion)0f else 1f)}
    val inherited=LocalFlowRoll.current
    val inheritedPhase=LocalFlowPhase.current
    val entry=LocalHomeEntry.current
    var handledEntry by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(entry){if(entry!=handledEntry){articleIndex=0;handledEntry=entry}}
    LaunchedEffect(ready,motion) {if(ready) {entrance.animateTo(0f,tween(if(motion)SceneMotion.HOME_ENTER_MS else 0,easing=LinearEasing));firstEntrancePlayed=true}}
    val snapshot=LocalHomeSnapshot.current
    val layer=rememberGraphicsLayer()
    val colors=MaterialTheme.colorScheme
    LaunchedEffect(firstEntrancePlayed,inheritedPhase,articleIndex,colors.background,colors.onSurfaceVariant,colors.secondaryContainer) {
        if(firstEntrancePlayed&&inheritedPhase!=FlowPhase.ENTER&&snapshot!=null) {
            // Departure freezes the last resting draw; finish its readback while the source
            // is still mounted, even if a previous idle capture was interrupted by navigation.
            if(inheritedPhase==FlowPhase.IDLE) {withFrameNanos {};withFrameNanos {}}
            snapshot.image=layer.toImageBitmap()
        }
    }
    Box(Modifier.fillMaxSize().drawWithContent {
        if(inheritedPhase==FlowPhase.EXIT)drawContent()
        else {
            layer.record {this@drawWithContent.drawContent()}
            drawLayer(layer)
        }
    }.background(colors.background)) {
    BrowseScene(Destination.HOME,"本地示例",onNavigate,onToggleTheme=onToggleTheme) {
        CompositionLocalProvider(LocalFlowRoll provides if(inheritedPhase==FlowPhase.EXIT)inherited else maxOf(inherited,entrance.value),
            LocalFlowPhase provides if(inheritedPhase==FlowPhase.EXIT)FlowPhase.EXIT else if(!firstEntrancePlayed)FlowPhase.ENTER else inheritedPhase) {
        BoxWithConstraints {
            val density=LocalDensity.current
            val safeTop=WindowInsets.safeDrawing.getTop(density)/density.density+76f
            val safeBottom=maxHeight.value-WindowInsets.safeDrawing.getBottom(density)/density.density-86f-140f*LocalNavigationPlacement.current.y
            val geometry=ArcDeckMath.geometry(maxWidth.value,maxHeight.value)
            if(motion&&density.fontScale<=1.2f&&maxHeight>maxWidth&&ArcDeckMath.canPull(geometry,safeTop,safeBottom)&&model.articles.isNotEmpty()) {
                ArcArticleDeck(model.articles,articleIndex,{articleIndex=it},onArticle,safeTop,safeBottom)
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
}
