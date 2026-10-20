package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.style.TextAlign
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import com.semiovert.app.navigation.LocalNavigationPlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The focus rises; neighbours pitch gently around fixed bottom edges without changing the base ring. */
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
    val phase=LocalFlowPhase.current
    val interactive=roll<.001f&&phase==FlowPhase.IDLE
    val lastFrames=remember {mutableMapOf<Int,ArcCardPose>()}
    val exitFrames=remember(phase){if(phase==FlowPhase.EXIT)lastFrames.toMap() else emptyMap()}
    val density=LocalDensity.current.density
    val placement=LocalNavigationPlacement.current
    val cardShape=remember {RoundedCornerShape(18.dp)}
    fun move(value: Float) {position=value;focusedPage=ArcDeckMath.focus(value,focusedPage)}
    fun show(index: Int): Boolean {
        if(!interactive||index !in articles.indices)return false
        settleJob?.cancel();dragging=false;settling=false
        position=index.toFloat();focusedPage=index;select(index)
        return true
    }
    LaunchedEffect(selected) {
        if(!dragging&&!settling&&ArcDeckMath.index(focusedPage,articles.size)!=selected) {
            if(phase!=FlowPhase.EXIT){position=selected.toFloat();focusedPage=selected}
        }
    }
    LaunchedEffect(interactive) {
        if(!interactive) {
            settleJob?.cancel();dragging=false;settling=false
            if(phase!=FlowPhase.EXIT){position=selected.toFloat();focusedPage=selected}
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
                val path=ArcDeckMath.scenePose(pose,g,roll,phase)
                val start=exitFrames[pose.article]
                val moving=if(phase==FlowPhase.EXIT&&start!=null)path.copy(
                    centerX=path.centerX+(start.centerX-pose.centerX)*(1f-roll),
                    centerY=path.centerY+(start.centerY-pose.centerY)*(1f-roll),
                    scale=path.scale+(start.scale-pose.scale)*(1f-roll)) else path
                SideEffect {lastFrames[pose.article]=moving}
                val article=articles[pose.article]
                val active=pose.article==focused
                key(article.id) {
                val relativePage=pose.distance+position-focusedPage
                val lift=animateFloatAsState(targetValue=if(interactive&&active)ArcDeckMath.focusLift(g) else 0f,
                    animationSpec=ArcDeckMotion.followSpec(),label="arc-lift-"+article.id)
                val shiftX=animateFloatAsState(targetValue=if(interactive&&active)ArcDeckMath.focusShiftX(g) else 0f,
                    animationSpec=ArcDeckMotion.followSpec(),label="arc-shift-x-"+article.id)
                val elevation=animateFloatAsState(targetValue=if(interactive&&active&&!dragging&&!settling)ArcDeckMath.FOCUS_ELEVATION else 0f,
                    animationSpec=tween(durationMillis=220,delayMillis=if(active&&!dragging&&!settling)180 else 0),label="arc-depth-"+article.id)
                val tilt=animateFloatAsState(targetValue=if(interactive)ArcDeckMath.neighbourTilt(relativePage) else 0f,
                    animationSpec=ArcDeckMotion.tiltSpec(relativePage),label="arc-tilt-"+article.id)
                Box(Modifier.requiredSize(g.cardWidth.dp,g.cardHeight.dp)
                    .zIndex(pose.layer)
                    .graphicsLayer {
                        translationX=(moving.centerX-g.cardWidth/2f+shiftX.value)*density
                        translationY=(moving.centerY-g.cardHeight/2f-lift.value)*density
                        rotationZ=0f
                        rotationX=tilt.value
                        transformOrigin=TransformOrigin(.5f,1f)
                        cameraDistance=g.cardHeight*density*8f
                        shape=cardShape
                        shadowElevation=elevation.value*density
                        scaleX=moving.scale;scaleY=moving.scale;alpha=moving.alpha
                    }.testTag("arc-card-"+article.id)) {
                    val semantics=if(active)Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                        stateDescription="选中"
                    } else Modifier.clearAndSetSemantics {}
                    CoverArticleCard(article,false,Modifier.fillMaxSize().then(semantics)
                        .clickable(enabled=interactive&&!dragging&&!settling,onClickLabel=if(active)"阅读文章" else "选择文章") {
                            if(active)read(article.id) else settleTo((position+pose.distance).toInt())
                        },"arc-cover-"+article.id)
                }
                }
            }
        }
        Text("${focused+1} / ${articles.size}",style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign=TextAlign.Center,
            modifier=Modifier.align(Alignment.BottomEnd).zIndex(1f).imePadding().navigationBarsPadding()
                .padding(end=20.dp,bottom=86.dp)
                .offset(x=-(maxWidth-96.dp).coerceAtLeast(0.dp)*(1f-placement.x),y=-140.dp*placement.y)
                .width(56.dp).testTag("home-position").clearAndSetSemantics {})
    }
}
