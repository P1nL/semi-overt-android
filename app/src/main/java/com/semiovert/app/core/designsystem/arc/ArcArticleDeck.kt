package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.*
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
import androidx.compose.ui.unit.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Sequentially layered cards; spacing exposes the focus, not a selected z-index boost. */
@Composable
fun ArcArticleDeck(articles: List<Article>,selected: Int,onSelected: (Int)->Unit,onArticle: (String)->Unit,modifier: Modifier=Modifier) {
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
    fun move(value: Float) {position=value;focusedPage=ArcDeckMath.focus(value,focusedPage)}
    fun show(index: Int): Boolean {
        if(!interactive||index !in articles.indices)return false
        settleJob?.cancel();dragging=false;settling=false
        position=index.toFloat();focusedPage=index;select(index)
        return true
    }
    LaunchedEffect(selected) {
        if(!dragging&&!settling&&ArcDeckMath.index(focusedPage,articles.size)!=selected) {
            position=selected.toFloat();focusedPage=selected
        }
    }
    LaunchedEffect(interactive) {
        if(!interactive) {
            settleJob?.cancel();dragging=false;settling=false
            position=selected.toFloat();focusedPage=selected
        }
    }

    // The route clips at the screen edges; let the deck continue behind the transparent header.
    BoxWithConstraints(modifier.fillMaxSize().testTag("feed-home").semantics {
        isTraversalGroup=true
        stateDescription="第 ${ArcDeckMath.index(focusedPage,articles.size)+1} 篇，共 ${articles.size} 篇，"+when {
            dragging -> "滑动中";settling -> "收束中";else -> "已停稳"
        }
        verticalScrollAxisRange=ScrollAxisRange({ArcDeckMath.index(focusedPage,articles.size).toFloat()},{(articles.size-1).toFloat()})
        scrollToIndex {show(it)}
        scrollBy {_,y ->if(y==0f)false else show(ArcDeckMath.index(focusedPage+if(y>0f)1 else -1,articles.size))}
        customActions=listOf(
            CustomAccessibilityAction("下一篇文章") {show(ArcDeckMath.index(focusedPage+1,articles.size))},
            CustomAccessibilityAction("上一篇文章") {show(ArcDeckMath.index(focusedPage-1,articles.size))},
        )
    }) {
        val g=ArcDeckMath.geometry(maxWidth.value,maxHeight.value)
        val stepPx=g.scrollStep*density
        val focused=ArcDeckMath.index(focusedPage,articles.size)
        fun settleTo(target: Int,velocity: Float=0f) {
            if(!interactive)return
            settleJob?.cancel();settling=true
            settleJob=scope.launch {
                try {
                    animate(position,target.toFloat(),initialVelocity=velocity,animationSpec=ArcDeckMotion.spec()) {value,_ ->move(value)}
                    val index=ArcDeckMath.index(target,articles.size)
                    position=index.toFloat();focusedPage=index;select(index)
                } finally {if(isActive)settling=false}
            }
        }
        Box(Modifier.fillMaxSize().testTag("home-arc")
            .graphicsLayer {alpha=1f-roll;translationY=24.dp.toPx()*roll}
            .draggable(
                state=rememberDraggableState {delta ->move(position-delta/stepPx)},
                orientation=Orientation.Vertical,enabled=interactive,startDragImmediately=settling,
                onDragStarted={settleJob?.cancel();settling=false;dragging=true},
                onDragStopped={velocity ->
                    dragging=false
                    settleTo(ArcDeckMath.target(position,velocity,stepPx),ArcDeckMotion.releaseVelocity(velocity,stepPx))
                },
            )) {
            for(pose in ArcDeckMath.frames(articles.size,position,g)) {
                val article=articles[pose.article]
                val active=pose.article==focused
                key(article.id) {
                val follower=if(interactive)animateFloatAsState(
                    targetValue=pose.waveOffset,animationSpec=ArcDeckMotion.followSpec(),label="arc-follow-"+article.id,
                ) else null
                Box(Modifier.requiredSize(g.cardWidth.dp,g.cardHeight.dp)
                    .zIndex(pose.layer)
                    .graphicsLayer {
                        val amount=follower?.value?:0f
                        translationX=(pose.centerX-g.cardWidth/2f+ArcDeckMath.waveX(pose.distance,amount))*density
                        translationY=(pose.centerY-g.cardHeight/2f+ArcDeckMath.waveY(pose.distance,amount))*density
                        scaleX=pose.scale;scaleY=pose.scale;alpha=pose.alpha
                    }.testTag("arc-card-"+article.id)) {
                    val semantics=if(active)Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                        stateDescription="选中"
                    } else Modifier.clearAndSetSemantics {}
                    CoverArticleCard(article,active,Modifier.fillMaxSize().then(semantics)
                        .clickable(enabled=interactive&&!dragging&&!settling,onClickLabel=if(active)"阅读文章" else "选择文章") {
                            if(active)read(article.id) else settleTo((position+pose.distance).toInt())
                        },"arc-cover-"+article.id)
                }
                }
            }
        }
        Text("${focused+1} / ${articles.size}",style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.align(Alignment.BottomStart).zIndex(1f)
                .padding(start=24.dp,bottom=30.dp).clearAndSetSemantics {})
    }
}
