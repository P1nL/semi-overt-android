package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
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
import kotlin.math.abs

/** Browsing never lifts a card. Only an explicit card selection opens the stack. */
@Composable
fun ArcArticleDeck(articles: List<Article>,selected: Int,onSelected: (Int)->Unit,onArticle: (String)->Unit,safeTop: Float,safeBottom: Float,modifier: Modifier=Modifier) {
    val ids=articles.map {it.id}
    var position by rememberSaveable(ids) {mutableFloatStateOf(selected.toFloat())}
    var focusedPage by rememberSaveable(ids) {mutableIntStateOf(selected)}
    var expandedArticle by rememberSaveable(ids) {mutableStateOf<Int?>(null)}
    var preparingExpansion by remember {mutableStateOf(false)}
    var dragging by remember {mutableStateOf(false)}
    var settling by remember {mutableStateOf(false)}
    var settleJob by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val select by rememberUpdatedState(onSelected)
    val read by rememberUpdatedState(onArticle)
    val roll=LocalFlowRoll.current
    val phase=LocalFlowPhase.current
    val expansion=LocalArticleExpansion.current
    val interactive=roll<.001f&&phase==FlowPhase.IDLE&&expansion?.active!=true
    val lastFrames=remember {mutableMapOf<Int,ArcCardPose>()}
    val exitFrames=remember(phase){if(phase==FlowPhase.EXIT)lastFrames.toMap() else emptyMap()}
    val density=LocalDensity.current.density
    val placement=LocalNavigationPlacement.current
    val cardShape=remember {RoundedCornerShape(18.dp)}
    fun move(value: Float) {position=value;focusedPage=ArcDeckMath.focus(value,focusedPage)}
    fun show(index: Int): Boolean {
        if(!interactive||index !in articles.indices)return false
        settleJob?.cancel();dragging=false;settling=false
        expandedArticle=null;preparingExpansion=false
        position=index.toFloat();focusedPage=index;select(index)
        return true
    }
    LaunchedEffect(selected) {
        if(!dragging&&!settling&&ArcDeckMath.index(focusedPage,articles.size)!=selected) {
            if(phase!=FlowPhase.EXIT){expandedArticle=null;preparingExpansion=false;position=selected.toFloat();focusedPage=selected}
        }
    }
    LaunchedEffect(interactive) {
        if(!interactive) {
            settleJob?.cancel();dragging=false;settling=false
            preparingExpansion=false
            if(expansion?.active!=true)expandedArticle=null
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
        fun expand(article: Int) {
            if(!interactive||dragging)return
            settleJob?.cancel()
            expandedArticle=null;preparingExpansion=true
            val target=ArcDeckMath.pullPosition(article,position,articles.size,g,safeTop,safeBottom)
            if(abs(target-position)<.0001f) {
                settling=false
                expandedArticle=article
                preparingExpansion=false
                return
            }
            settling=true
            settleJob=scope.launch {
                try {
                    // Monotone placement: do not overshoot the newly cleared screen edge.
                    animate(position,target,animationSpec=tween(320,easing=FastOutSlowInEasing)) {value,_ ->move(value)}
                    move(target)
                    select(ArcDeckMath.index(focusedPage,articles.size))
                    expandedArticle=article
                } finally {if(isActive){settling=false;preparingExpansion=false}}
            }
        }
        Box(Modifier.fillMaxSize().testTag("home-arc")
            .draggable(
                state=rememberDraggableState {delta ->move(position-delta/stepPx)},
                orientation=Orientation.Vertical,enabled=interactive,startDragImmediately=settling,
                onDragStarted={settleJob?.cancel();expandedArticle=null;preparingExpansion=false;settling=false;dragging=true},
                onDragStopped={velocity ->
                    dragging=false
                    settleTo(ArcDeckMath.target(position,velocity,stepPx),ArcDeckMotion.releaseVelocity(velocity,stepPx))
                },
            )) {
            ArcAuthorAvatar(articles[focused].author,articles[focused].id,position,dragging,settling,
                enabled=interactive&&expandedArticle==null&&!preparingExpansion,geometry=g,
                modifier=Modifier.zIndex(-ArcDeckMath.VISIBLE_DISTANCE-2f))
            for(pose in ArcDeckMath.frames(articles.size,position,g)) {
                val path=ArcDeckMath.scenePose(pose,g,roll,phase)
                val start=exitFrames[pose.article]
                val moving=if(phase==FlowPhase.EXIT&&start!=null)path.copy(
                    centerX=path.centerX+(start.centerX-pose.centerX)*(1f-roll),
                    centerY=path.centerY+(start.centerY-pose.centerY)*(1f-roll),
                    scale=path.scale+(start.scale-pose.scale)*(1f-roll)) else path
                val article=articles[pose.article]
                val active=pose.article==expandedArticle
                key(article.id) {
                val relativePage=expandedArticle?.let {ArcDeckMath.distance(pose.article,it.toFloat(),articles.size)}?:0f
                val pull=animateFloatAsState(targetValue=if(active&&(interactive||expansion?.active==true))1f else 0f,
                    animationSpec=ArcDeckMotion.followSpec(),label="arc-pull-"+article.id)
                val amount=if(phase==FlowPhase.EXIT)0f else pull.value
                // Resolve once when lifting starts, not every frame of the lift or return.
                val avatarPlacement=remember(active,g,safeTop,safeBottom) {
                    if(active)ArcAuthorAvatarMotion.expandedPlacement(g,
                        pose.centerY-ArcDeckMath.pullLift(pose.distance,g)-g.cardHeight/2f,safeTop,safeBottom) else null
                }
                ArcExpandedAuthorAvatar(article.author,avatarPlacement,
                    visible=active&&interactive&&amount>=ArcAuthorAvatarMotion.EXPANDED_REVEAL_PROGRESS,
                    tag="home-expanded-author-"+article.id,
                    modifier=Modifier.zIndex(-ArcDeckMath.VISIBLE_DISTANCE-2f))
                val lift=ArcDeckMath.pullLift(pose.distance,g)*amount
                val shiftX=(ArcDeckMath.pulledLeft(g)+g.cardWidth/2f-moving.centerX)*amount
                val tilt=animateFloatAsState(targetValue=if(interactive||expansion?.active==true)ArcDeckMath.neighbourTilt(relativePage) else 0f,
                    animationSpec=ArcDeckMotion.tiltSpec(relativePage),label="arc-tilt-"+article.id)
                SideEffect {if(phase!=FlowPhase.EXIT)lastFrames[pose.article]=moving.copy(centerX=moving.centerX+shiftX,centerY=moving.centerY-lift)}
                val action: ()->Unit={
                    if(active&&pull.value>=.995f&&!settling)read(article.id)
                    else if(!active)expand(pose.article)
                }
                val label=if(active)"阅读文章" else "展开文章"
                val enabled=interactive&&!dragging&&!settling
                Box(Modifier.requiredSize(g.cardWidth.dp,g.cardHeight.dp)
                    .zIndex(pose.layer)
                    .graphicsLayer {
                        translationX=(moving.centerX-g.cardWidth/2f+shiftX)*density
                        translationY=(moving.centerY-g.cardHeight/2f-lift)*density
                        rotationZ=0f
                        rotationX=tilt.value
                        transformOrigin=TransformOrigin(.5f,1f)
                        cameraDistance=g.cardHeight*density*8f
                        shape=cardShape
                        shadowElevation=if(expansion?.source?.article?.id==article.id)0f else ArcDeckMath.FOCUS_ELEVATION*amount*density
                        ambientShadowColor=Color.Black.copy(alpha=amount.coerceIn(0f,1f))
                        spotShadowColor=Color.Black.copy(alpha=amount.coerceIn(0f,1f))
                        scaleX=moving.scale;scaleY=moving.scale;alpha=moving.alpha
                    }.testTag("arc-card-"+article.id)) {
                    val semantics=Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                        stateDescription=if(active)"已展开，再次点击阅读" else "未展开"
                    }
                    CoverArticleCard(article,active,Modifier.fillMaxSize().then(semantics),"arc-cover-"+article.id,
                        onClick=action,interactable=enabled,shadowElevation=0.dp,
                        readingOrigin=if(active&&pull.value>=.995f)ArticleOrigin.HOME else null)
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
                .width(56.dp).readingChromeFade().testTag("home-position").clearAndSetSemantics {})
    }
}
