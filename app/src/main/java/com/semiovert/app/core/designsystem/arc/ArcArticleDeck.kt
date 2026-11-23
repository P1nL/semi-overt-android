package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.core.*
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
import kotlin.math.floor
import kotlin.math.roundToInt

private data class ArcCardDrawKey(val article: Int,val lifted: Boolean)
private class ArcLiftHistory(var distance: Float)
private data class ArcCardRender(
    val pose: ArcCardPose,val drawn: ArcCardPose,val paints: Boolean,val primary: Boolean,val amount: Float,
)
private data class ArcCardStructure(val visible: Boolean,val layer: Float,val primary: Boolean)

/** Browsing never lifts a card. Only an explicit card selection opens the stack. */
@Composable
fun ArcArticleDeck(articles: List<Article>,selected: Int,onSelected: (Int)->Unit,onArticle: (String)->Unit,safeTop: Float,safeBottom: Float,modifier: Modifier=Modifier,
    expandedAvatarSafeTop: Float=safeTop) {
    val ids=remember(articles) {articles.map {it.id}}
    val positionState=rememberSaveable(ids) {mutableFloatStateOf(selected.toFloat())}
    var position by positionState
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
    val lastFrames=remember {mutableMapOf<ArcCardDrawKey,ArcCardPose>()}
    val exitFrames=remember(phase){if(phase==FlowPhase.EXIT)lastFrames.toMap() else emptyMap()}
    val density=LocalDensity.current.density
    val placement=LocalNavigationPlacement.current
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
        val g=remember(maxWidth,maxHeight) {ArcDeckMath.geometry(maxWidth.value,maxHeight.value)}
        val stepPx=g.scrollStep*density
        val focused=ArcDeckMath.index(focusedPage,articles.size)
        fun settleAfterRelease(screenVelocity: Float) {
            if(!interactive)return
            settleJob?.cancel();settling=true
            settleJob=scope.launch {
                try {
                    val velocity=ArcDeckMotion.flingVelocity(screenVelocity,stepPx)
                    val fling=ArcDeckMotion.shouldFling(velocity)
                    val motion=AnimationState(initialValue=position,initialVelocity=if(fling)velocity
                        else ArcDeckMotion.releaseVelocity(screenVelocity,stepPx))
                    if(fling)motion.animateDecay(ArcDeckMotion.flingSpec()) {move(value)}
                    val target=if(fling)motion.value.roundToInt()
                        else ArcDeckMath.target(motion.value,screenVelocity,stepPx)
                    // Continue from the decay's position and velocity, with no pause between stages.
                    motion.animateTo(target.toFloat(),animationSpec=ArcDeckMotion.spec(),sequentialAnimation=fling) {move(value)}
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
                    settleAfterRelease(velocity)
                },
            )) {
            ArcAuthorAvatar(articles[focused].author,articles[focused].id,{position},dragging,settling,
                enabled=interactive&&expandedArticle==null&&!preparingExpansion,geometry=g,
                modifier=Modifier.zIndex(-ArcDeckMath.VISIBLE_DISTANCE-2f))
            // Queue and lifted copies have separate lifetimes across the ring's wrap boundary.
            articles.forEachIndexed {index,article ->
                key(article.id) {
                    ArcDeckCard(article,index,articles.size,positionState,g,roll,phase,expandedArticle,
                        interactive,dragging,settling,expandedAvatarSafeTop,safeBottom,lastFrames,exitFrames,
                        onClick={if(index==expandedArticle&&!settling)read(article.id) else if(index!=expandedArticle)expand(index)})
                }
            }
        }
        Text("${focused+1} / ${articles.size}".withInterfaceDigits(),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign=TextAlign.Center,
            modifier=Modifier.align(Alignment.BottomEnd).zIndex(1f).imePadding().navigationBarsPadding()
                .padding(end=20.dp,bottom=86.dp)
                .offset(x=-(maxWidth-96.dp).coerceAtLeast(0.dp)*(1f-placement.x),y=-140.dp*placement.y)
                .width(56.dp).readingChromeFade().testTag("home-position").clearAndSetSemantics {})
    }
}

@Composable
private fun ArcDeckCard(article: Article,index: Int,count: Int,position: State<Float>,g: ArcDeckGeometry,
    roll: Float,phase: FlowPhase,expandedArticle: Int?,interactive: Boolean,dragging: Boolean,settling: Boolean,
    expandedAvatarSafeTop: Float,safeBottom: Float,lastFrames: MutableMap<ArcCardDrawKey,ArcCardPose>,
    exitFrames: Map<ArcCardDrawKey,ArcCardPose>,onClick: ()->Unit) {
    val expansion=LocalArticleExpansion.current
    val active=index==expandedArticle
    val density=LocalDensity.current.density
    val cardShape=remember {RoundedCornerShape(18.dp)}
    val basePose=remember(index,count,position,g) {derivedStateOf {ArcDeckMath.displayPose(index,count,position.value,g)}}
    val history=remember {ArcLiftHistory(basePose.value.distance)}
    val queueKey=remember(index) {ArcCardDrawKey(index,false)}
    val liftedKey=remember(index) {ArcCardDrawKey(index,true)}
    val lifting=active&&(interactive||expansion?.active==true)
    val pull=animateFloatAsState(if(lifting)1f else 0f,ArcDeckMotion.liftSpec(),label="arc-pull-"+article.id)
    val hasLiftedCopy by remember(pull,exitFrames,liftedKey) {
        derivedStateOf {pull.value!=0f||exitFrames.containsKey(liftedKey)}
    }
    val copies=if(hasLiftedCopy) {
        val current=ArcDeckMath.copies(basePose.value,history.distance,count,g,hasLiftedCopy=true)
        SideEffect {history.distance=(current.lifted?:current.queue).distance}
        rememberUpdatedState(current)
    } else {
        SideEffect {
            history.distance=basePose.value.distance
            if(phase!=FlowPhase.EXIT)lastFrames.remove(liftedKey)
        }
        remember(basePose) {derivedStateOf {ArcCardCopies(basePose.value,null,true)}}
    }
    val relativePage=expandedArticle?.let {ArcDeckMath.distance(index,it.toFloat(),count)}?:0f
    val tilt=animateFloatAsState(
        if(interactive||expansion?.active==true)ArcDeckMath.neighbourTilt(relativePage) else 0f,
        ArcDeckMotion.tiltSpec(relativePage),label="arc-tilt-"+article.id)
    val avatarPlacement=remember(active,g,expandedAvatarSafeTop,safeBottom) {
        if(active) {
            val pose=copies.value.lifted?:basePose.value
            ArcAuthorAvatarMotion.expandedPlacement(g,
                pose.centerY-ArcDeckMath.pullLift(pose.distance,g)-g.cardHeight/2f,expandedAvatarSafeTop,safeBottom)
        } else null
    }
    val avatarVisible by remember(active,interactive,phase,pull) {
        derivedStateOf {active&&interactive&&phase!=FlowPhase.EXIT&&pull.value>=ArcAuthorAvatarMotion.EXPANDED_REVEAL_PROGRESS}
    }
    ArcExpandedAuthorAvatar(article.author,avatarPlacement,avatarVisible,"home-expanded-author-"+article.id,
        Modifier.zIndex(-ArcDeckMath.VISIBLE_DISTANCE-2f))
    for(copy in 0..1)key(copy) {
        val isLifted=copy==1
        if(!isLifted||hasLiftedCopy) {
            val drawKey=if(isLifted)liftedKey else queueKey
            val render=remember(copies,g,roll,phase,exitFrames,drawKey,pull) {
                derivedStateOf {
                    val pair=copies.value
                    val pose=if(isLifted)pair.lifted?:pair.queue else pair.queue
                    val queuePaints=if(phase==FlowPhase.EXIT)exitFrames.containsKey(queueKey) else pair.queueVisible
                    val paints=if(phase==FlowPhase.EXIT)exitFrames.containsKey(drawKey)
                        else if(isLifted)pair.lifted!=null else queuePaints
                    val path=ArcDeckMath.scenePose(pose,g,roll,phase)
                    val start=exitFrames[drawKey]
                    val moving=if(phase==FlowPhase.EXIT&&start!=null)path.copy(
                        centerX=path.centerX+(start.centerX-pose.centerX)*(1f-roll),
                        centerY=path.centerY+(start.centerY-pose.centerY)*(1f-roll),
                        scale=path.scale+(start.scale-pose.scale)*(1f-roll)) else path
                    val amount=if(isLifted&&phase!=FlowPhase.EXIT)pull.value else 0f
                    val drawn=if(amount==0f)moving else moving.copy(
                        centerX=moving.centerX+(ArcDeckMath.pulledLeft(g)+g.cardWidth/2f-moving.centerX)*amount,
                        centerY=moving.centerY-ArcDeckMath.pullLift(pose.distance,g)*amount)
                    ArcCardRender(pose,drawn,paints,paints&&(!isLifted||!queuePaints),amount)
                }
            }
            // Visibility and discrete stacking order drive composition; pixels follow every frame.
            val structure by remember(render,g,phase) {
                derivedStateOf {
                    val frame=render.value
                    ArcCardStructure(ArcDeckMath.isVisible(frame.drawn,g)||phase!=FlowPhase.IDLE,
                        floor(frame.pose.layer),frame.primary)
                }
            }
            if(structure.visible) {
                DisposableEffect(lastFrames,drawKey) {onDispose {lastFrames.remove(drawKey)}}
                val primary=structure.primary
                Box(Modifier.requiredSize(g.cardWidth.dp,g.cardHeight.dp).zIndex(structure.layer)
                    .graphicsLayer {
                        val frame=render.value
                        val drawn=frame.drawn
                        if(phase!=FlowPhase.EXIT) {
                            if(frame.paints&&ArcDeckMath.isVisible(drawn,g))lastFrames[drawKey]=drawn
                            else lastFrames.remove(drawKey)
                        }
                        translationX=(drawn.centerX-g.cardWidth/2f)*density
                        translationY=(drawn.centerY-g.cardHeight/2f)*density
                        rotationZ=0f
                        rotationX=tilt.value
                        transformOrigin=TransformOrigin(.5f,1f)
                        cameraDistance=g.cardHeight*density*8f
                        shape=cardShape
                        shadowElevation=if(expansion?.source?.article?.id==article.id)0f else ArcDeckMath.FOCUS_ELEVATION*frame.amount*density
                        ambientShadowColor=Color.Black.copy(alpha=frame.amount.coerceIn(0f,1f))
                        spotShadowColor=Color.Black.copy(alpha=frame.amount.coerceIn(0f,1f))
                        scaleX=drawn.scale;scaleY=drawn.scale;alpha=if(frame.paints)drawn.alpha else 0f
                    }.then(if(primary)Modifier.testTag("arc-card-"+article.id) else Modifier.clearAndSetSemantics {
                        testTag=(if(isLifted)"arc-returning-card-" else "arc-parked-card-")+article.id
                    })) {
                    val semantics=if(primary)Modifier.testTag("article-"+article.id).semantics(mergeDescendants=true) {
                        role=Role.Button
                        contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                        stateDescription=if(active)"已展开，再次点击阅读" else "未展开"
                    } else Modifier
                    val coverTag=(if(primary)"arc-cover-" else if(isLifted)"arc-returning-cover-" else "arc-parked-cover-")+article.id
                    CoverArticleCard(article,active&&primary,Modifier.fillMaxSize().then(semantics),coverTag,
                        onClick=if(primary)onClick else null,interactable=primary&&interactive&&!dragging&&!settling,shadowElevation=0.dp,
                        readingOrigin=if(active&&primary)ArticleOrigin.HOME else null,ownsReadingSource=primary,
                        coverColor=homeArticleCoverColor(index))
                }
            }
        }
    }
}
