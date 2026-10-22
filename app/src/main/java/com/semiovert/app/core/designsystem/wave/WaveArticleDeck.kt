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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun WaveArticleDeck(articles: List<Article>,selected: Int,onSelected: (Int)->Unit,onArticle: (String)->Unit,modifier: Modifier=Modifier,listTag: String="feed-category",deckTag: String="category-wave",frontTop: androidx.compose.ui.unit.Dp?=null,referenceHeight: androidx.compose.ui.unit.Dp?=null,
    visibleLimit: Int=WaveDeckMath.CATEGORY_VISIBLE,showCounter: Boolean=true,consumePageDrag: (Float)->Float={0f},
    sceneDurationMillis: Int?=null,sceneStaggerMillis: Int=SceneMotion.DECK_STAGGER_MS,elasticEntrance: Boolean=false) {
    val ids=articles.map {it.id}
    var position by remember(ids) {mutableFloatStateOf(selected.toFloat())}
    var focusedPage by remember(ids) {mutableIntStateOf(selected)}
    var dragging by remember {mutableStateOf(false)}
    var settling by remember {mutableStateOf(false)}
    var elastic by remember {mutableFloatStateOf(0f)}
    var dragDistance by remember {mutableFloatStateOf(0f)}
    var pullOrigin by remember {mutableFloatStateOf(0f)}
    var settleJob by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val select by rememberUpdatedState(onSelected)
    val read by rememberUpdatedState(onArticle)
    val roll=LocalFlowRoll.current
    val phase=LocalFlowPhase.current
    val expansion=LocalArticleExpansion.current
    val interactive=roll<.001f&&phase==FlowPhase.IDLE&&expansion?.active!=true
    val density=LocalDensity.current.density
    fun move(value: Float) {position=value;focusedPage=WaveDeckMath.focus(value,focusedPage)}
    fun show(index: Int): Boolean {
        if(!interactive||index !in articles.indices)return false
        settleJob?.cancel();dragging=false;settling=false;elastic=0f
        position=index.toFloat();focusedPage=index;select(index)
        return true
    }
    LaunchedEffect(selected) {
        if(!dragging&&!settling&&WaveDeckMath.index(focusedPage,articles.size)!=selected) {position=selected.toFloat();focusedPage=selected}
    }
    LaunchedEffect(interactive) {
        if(!interactive) {
            settleJob?.cancel();dragging=false;settling=false;elastic=0f
            position=selected.toFloat();focusedPage=selected
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag(listTag).semantics {
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
        val g=WaveDeckMath.embeddedGeometry(maxWidth.value,maxHeight.value,articles.size,
            (referenceHeight?:maxHeight).value,frontTop?.value,visibleLimit,LocalDensity.current.fontScale)
        val stepPx=g.scrollStep*density
        val focused=WaveDeckMath.index(focusedPage,articles.size)
        fun settleTo(target: Int,velocity: Float=0f) {
            if(!interactive)return
            settleJob?.cancel();settling=true
            settleJob=scope.launch {
                try {
                    coroutineScope {
                        launch {animate(elastic,0f,animationSpec=WaveDeckMotion.spec()) {value,_->elastic=value}}
                        animate(position,target.toFloat(),initialVelocity=velocity,animationSpec=WaveDeckMotion.spec()) {value,_->move(value)}
                    }
                    val index=WaveDeckMath.index(target,articles.size)
                    position=index.toFloat();focusedPage=index;select(index)
                } finally {if(isActive)settling=false}
            }
        }
        val poses=WaveDeckMath.frames(articles.size,position,g,visibleLimit)
        val ordered=poses.sortedByDescending {it.layer}
        val readingId=expansion?.source?.article?.id
        val departing=ordered.filter {articles[it.article].id!=readingId}
        if(showCounter)DeckCounter(focused+1,articles.size,page=listTag.removePrefix("feed-"))
        Box(Modifier.fillMaxSize().testTag(deckTag)
            .waveDeckOverlay {interactive&&(dragging||settling)}
            .draggable(
                state=rememberDraggableState {delta->
                    val remaining=delta-consumePageDrag(delta)
                    if(remaining!=0f) {
                        dragDistance+=remaining/density
                        elastic=(pullOrigin+WaveDeckMath.elasticPull(dragDistance,maxHeight.value)).coerceIn(-WaveDeckMath.MAX_ELASTIC_PULL,0f)
                        move(position+WaveDeckMath.dragDelta(remaining,stepPx))
                    }
                },
                orientation=Orientation.Vertical,enabled=interactive,startDragImmediately=settling,
                onDragStarted={settleJob?.cancel();settling=false;dragging=true;dragDistance=0f;pullOrigin=elastic},
                onDragStopped={velocity->
                    dragging=false
                    val deckVelocity=if(dragDistance==0f)0f else velocity
                    settleTo(WaveDeckMath.target(position,deckVelocity,stepPx),WaveDeckMotion.releaseVelocity(deckVelocity,stepPx))
                },
            )) {
            for(pose in poses) {
                val article=articles[pose.article]
                val active=pose.article==focused
                val rank=ordered.indexOf(pose)
                key(article.id) {
                // Each neighbour owns a retargetable spring, like the web rail.
                // Removing it during the hidden transfer clears stale motion
                // before that same article reappears at the back of the queue.
                val follower=if(interactive&&!pose.returning)animateFloatAsState(
                    targetValue=pose.waveOffset,animationSpec=WaveDeckMotion.followSpec(),label="wave-follow-"+article.id,
                ) else null
                val top=pose.centerY-pose.height/2f
                val readingRank=departing.indexOf(pose).coerceAtLeast(0)
                val readingTarget=if(expansion?.active==true&&article.id!=readingId)
                    SceneMotion.readingOffset(expansion.progress,readingRank,top,g.height) else 0f
                // Keep this spring mounted when a lift is cancelled, so its return tail is not cut off.
                val readingFollow by animateFloatAsState(readingTarget,SceneMotion.readingFollowerSpring(readingRank),
                    label="reading-follow-"+article.id)
                Box(Modifier.zIndex(pose.layer).wrapContentSize(Alignment.TopStart,unbounded=true)
                    .requiredSize(g.cardSize.dp,(pose.height+WaveDeckMath.BOTTOM_OVERFLOW).dp)
                    .readingStackOverlay(pose.layer) {expansion?.source?.stackLayer!=null}
                    .graphicsLayer {
                    translationX=(pose.centerX-g.cardSize/2f)*density
                    val sceneOffset=SceneMotion.waveOffset(roll,phase,rank,poses.size,top,g.height,
                        sceneDurationMillis?:if(phase==FlowPhase.EXIT)SceneMotion.CATEGORY_EXIT_MS else SceneMotion.CATEGORY_ENTER_MS,
                        sceneStaggerMillis,elasticEntrance)
                    translationY=(top+(follower?.value?:0f)+sceneOffset+readingFollow+elastic)*density
                    scaleX=pose.scale;scaleY=pose.scale;alpha=pose.alpha
                }.testTag("wave-card-"+article.id)) {
                    val semantics=Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button;stateDescription=if(active)"选中" else "可上提展开"
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                    }
                    CoverArticleCard(article,active,Modifier.fillMaxSize().then(semantics),"wave-cover-"+article.id,
                        topAligned=true,squareBottom=true,shortBottomBlur=true,interactable=interactive&&!dragging&&!settling,
                        onClick={if(active)read(article.id) else settleTo(WaveDeckMath.nearestPage(pose.article,position,articles.size))},
                        allowLift=true,stackLayer=pose.layer,onRead={
                            // Commit the source as the front card while the reader overlay covers the queue.
                            position=pose.article.toFloat();focusedPage=pose.article;select(pose.article)
                            read(article.id)
                        })
                }
                }
            }
        }
    }
}
