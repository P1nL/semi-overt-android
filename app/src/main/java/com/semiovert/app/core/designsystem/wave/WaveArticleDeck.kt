package com.semiovert.app.core.designsystem.wave

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.Article
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun WaveArticleDeck(articles: List<Article>,selected: Int,onSelected: (Int)->Unit,onArticle: (String)->Unit,modifier: Modifier=Modifier) {
    val ids=articles.map {it.id}
    var position by remember(ids) {mutableFloatStateOf(selected.toFloat())}
    var focusedPage by remember(ids) {mutableIntStateOf(selected)}
    var dragging by remember {mutableStateOf(false)}
    var settling by remember {mutableStateOf(false)}
    var settleJob by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val select by rememberUpdatedState(onSelected)
    val read by rememberUpdatedState(onArticle)
    val roll=LocalFlowRoll.current
    val interactive=roll<.001f
    val density=LocalDensity.current.density
    fun move(value: Float) {position=value;focusedPage=WaveDeckMath.focus(value,focusedPage)}
    fun show(index: Int): Boolean {
        if(!interactive||index !in articles.indices)return false
        settleJob?.cancel();dragging=false;settling=false
        position=index.toFloat();focusedPage=index;select(index)
        return true
    }
    LaunchedEffect(selected) {
        if(!dragging&&!settling&&WaveDeckMath.index(focusedPage,articles.size)!=selected) {position=selected.toFloat();focusedPage=selected}
    }
    LaunchedEffect(interactive) {
        if(!interactive) {
            settleJob?.cancel();dragging=false;settling=false
            position=selected.toFloat();focusedPage=selected
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("feed-category").semantics {
        isTraversalGroup=true
        stateDescription="第 ${WaveDeckMath.index(focusedPage,articles.size)+1} 篇，共 ${articles.size} 篇，"+when {
            dragging->"滑动中";settling->"收束中";else->"已停稳"
        }
        verticalScrollAxisRange=ScrollAxisRange({WaveDeckMath.index(focusedPage,articles.size).toFloat()},{(articles.size-1).toFloat()})
        scrollToIndex {show(it)}
        scrollBy {_,y->if(y==0f)false else show(WaveDeckMath.index(focusedPage+if(y>0f)1 else -1,articles.size))}
        customActions=listOf(
            CustomAccessibilityAction("下一篇文章") {show(WaveDeckMath.index(focusedPage+1,articles.size))},
            CustomAccessibilityAction("上一篇文章") {show(WaveDeckMath.index(focusedPage-1,articles.size))},
        )
    }) {
        val g=WaveDeckMath.geometry(maxWidth.value,maxHeight.value,articles.size)
        val stepPx=g.scrollStep*density
        val focused=WaveDeckMath.index(focusedPage,articles.size)
        fun settleTo(target: Int,velocity: Float=0f) {
            if(!interactive)return
            settleJob?.cancel();settling=true
            settleJob=scope.launch {
                try {
                    animate(position,target.toFloat(),initialVelocity=velocity,animationSpec=WaveDeckMotion.spec()) {value,_->move(value)}
                    val index=WaveDeckMath.index(target,articles.size)
                    position=index.toFloat();focusedPage=index;select(index)
                } finally {if(isActive)settling=false}
            }
        }
        Box(Modifier.fillMaxSize().testTag("category-wave")
            .graphicsLayer {alpha=1f-roll;translationY=32.dp.toPx()*roll}
            .draggable(
                state=rememberDraggableState {delta->move(position+WaveDeckMath.dragDelta(delta,stepPx))},
                orientation=Orientation.Vertical,enabled=interactive,startDragImmediately=settling,
                onDragStarted={settleJob?.cancel();settling=false;dragging=true},
                onDragStopped={velocity->
                    dragging=false
                    settleTo(WaveDeckMath.target(position,velocity,stepPx),WaveDeckMotion.releaseVelocity(velocity,stepPx))
                },
            )) {
            for(pose in WaveDeckMath.frames(articles.size,position,g)) {
                val article=articles[pose.article]
                val active=pose.article==focused
                key(article.id) {
                // Each neighbour owns a retargetable spring, like the web rail.
                // Removing it during the hidden transfer clears stale motion
                // before that same article reappears at the back of the queue.
                val follower=if(interactive&&!pose.returning)animateFloatAsState(
                    targetValue=pose.waveOffset,animationSpec=WaveDeckMotion.followSpec(),label="wave-follow-"+article.id,
                ) else null
                Box(Modifier.zIndex(pose.layer).wrapContentSize(Alignment.TopStart,unbounded=true)
                    .requiredSize(g.cardSize.dp,pose.height.dp).graphicsLayer {
                    translationX=(pose.centerX-g.cardSize/2f)*density
                    translationY=(pose.centerY-pose.height/2f+(follower?.value?:0f))*density
                    scaleX=pose.scale;scaleY=pose.scale;alpha=pose.alpha
                }.testTag("wave-card-"+article.id)) {
                    val semantics=if(active)Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button;stateDescription="选中"
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                    } else Modifier.clearAndSetSemantics {}
                    CoverArticleCard(article,active,Modifier.fillMaxSize().then(semantics)
                        .clickable(enabled=interactive&&!dragging&&!settling,onClickLabel=if(active)"阅读文章" else "选择文章") {
                            if(active)read(article.id) else settleTo(WaveDeckMath.nearestPage(pose.article,position,articles.size))
                        },"wave-cover-"+article.id,topAligned=true,squareBottom=true,shortBottomBlur=true)
                }
                }
            }
            Text("${focused+1} / ${articles.size}",style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.align(Alignment.TopEnd).zIndex(articles.size+2f).padding(end=24.dp,top=16.dp).clearAndSetSemantics {})
        }
    }
}
