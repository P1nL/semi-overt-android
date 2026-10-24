package com.semiovert.app.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.readingStackOverlay
import kotlin.math.roundToInt

@Composable
fun ArticleExpansionHost(content: @Composable ()->Unit) {
    val scope=rememberCoroutineScope()
    val expansion=remember {ArticleExpansionState(scope)}
    val motion=LocalMotionEnabled.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    SideEffect {expansion.motionEnabled=motion;expansion.onStart={focus.clearFocus();keyboard?.hide()}}
    var root by remember {mutableStateOf(Offset.Zero)}
    BackHandler(expansion.active){expansion.cancel()}
    CompositionLocalProvider(LocalArticleExpansion provides expansion) {
        Box(Modifier.fillMaxSize().clipToBounds().onGloballyPositioned {root=it.positionInRoot()}) {
            Box(Modifier.fillMaxSize().then(if(expansion.active)Modifier.clearAndSetSemantics {} else Modifier)){content()}
            expansion.source?.let {source->ExpansionOverlay(source,expansion,root)}
        }
    }
}

@Composable
private fun ExpansionOverlay(source: ArticleSource,state: ArticleExpansionState,root: Offset) {
    val anchors=state.anchors
    val density=LocalDensity.current
    val p=state.progress
    val geometry=if(anchors.title==Rect.Zero)0f else SceneMotion.readingText(p)
    val titlePosition=if(source.origin==ArticleOrigin.STACK&&anchors.title!=Rect.Zero)SceneMotion.readingTitlePosition(p) else geometry
    val color=lerp(source.ink,MaterialTheme.colorScheme.onSurface,geometry)
    val metaColor=lerp(source.ink,MaterialTheme.colorScheme.onSurfaceVariant,geometry)
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(20f)
        .readingStackOverlay(source.stackLayer) {true}.testTag("reading-expansion").semantics {
        stateDescription=if(state.settling)"阅读转场收束中" else "上提展开中"
        progressBarRangeInfo=ProgressBarRangeInfo(p.coerceIn(0f,1f),0f..1f)
    }.pointerInput(Unit){awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
        val widthPx=with(density){maxWidth.toPx()};val heightPx=with(density){maxHeight.toPx()}
        val b=source.card
        val lift=(p/SceneMotion.READING_DRAG_STAGE).coerceIn(-.08f,SceneMotion.READING_CONTAINER_START/SceneMotion.READING_DRAG_STAGE)
        val liftedScale=1f+.035f*lift
        val lifted=Rect(Offset(b.left-b.width*(liftedScale-1f)/2f,
            b.top-SceneMotion.LIFT_DISTANCE_DP*density.density*lift),Size(b.width*liftedScale,b.height*liftedScale))
        val container=SceneMotion.readingContainer(p)
        val reader=Rect(root,Size(widthPx,heightPx))
        val card=lerp(lifted,reader,container)
        // Only the opening home background starts early; text anchors and return keep their old path.
        val background=if(source.origin==ArticleOrigin.HOME&&!state.returning)SceneMotion.homeReadingContainer(p) else container
        val backgroundCard=lerp(lifted,reader,background)
        val sourceScale=card.width/b.width
        // Keep the original text layout: the source anchors travel with the whole card first.
        fun carried(anchor: Rect)=Rect(Offset(card.left+(anchor.left-b.left)*sourceScale,
            card.top+(anchor.top-b.top)*sourceScale),anchor.size)
        Surface(Modifier.offset {IntOffset((backgroundCard.left-root.x).roundToInt(),(backgroundCard.top-root.y).roundToInt())}
            .wrapContentSize(Alignment.TopStart,unbounded=true)
            .requiredSize(with(density){backgroundCard.width.toDp()},with(density){backgroundCard.height.toDp()}),
            shape=RoundedCornerShape(topStart=(18f*(1f-background)).dp,topEnd=(18f*(1f-background)).dp,
                bottomStart=(if(source.origin==ArticleOrigin.HOME)18f*(1f-background) else 0f).dp,
                bottomEnd=(if(source.origin==ArticleOrigin.HOME)18f*(1f-background) else 0f).dp),
            shadowElevation=((if(source.origin==ArticleOrigin.HOME)10f else 2f)*(1f-background)).dp,
            color=MaterialTheme.colorScheme.background.copy(alpha=if(source.origin==ArticleOrigin.HOME)SceneMotion.homeReadingFade(p) else SceneMotion.readingBody(p))) {
            Image(articleCoverPainter(articleCoverResource(source.article,source.origin==ArticleOrigin.STACK)),null,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().graphicsLayer {alpha=1f-SceneMotion.segment(p,.70f,.90f)})
        }
        ReaderArticleLayout(source.article,rememberLazyListState(),{},anchors,
            bodyProgress=SceneMotion.readingBody(p),avatarProgress=SceneMotion.readingAvatar(p),headingProgress=geometry,tag="reader-preview")
        val cardTitle=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
        val cardMeta=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp)
        StableReadingText(source.article.title,carried(source.title),anchors.title,cardTitle,MaterialTheme.typography.headlineLarge,
            color,geometry,root,sourceLines=3,targetLines=Int.MAX_VALUE,sourceScale=sourceScale,positionProgress=titlePosition)
        StableReadingText(source.article.author.name,carried(source.author),anchors.author,cardMeta,MaterialTheme.typography.labelLarge,
            color,geometry,root,sourceScale=sourceScale)
        val separator=Rect(source.author.right,source.author.top,source.duration.left,source.author.bottom)
        if(separator.width>0f)StableReadingText(" · ",carried(separator),Rect.Zero,cardMeta,cardMeta,
            color,geometry,root,targetText="",sourceScale=sourceScale)
        StableReadingText(source.article.date,carried(source.date),anchors.date,cardMeta,MaterialTheme.typography.labelMedium,
            metaColor,geometry,root,targetText=source.article.date+" · ",sourceScale=sourceScale)
        StableReadingText("约 "+source.article.minutes+" 分钟",carried(source.duration),anchors.duration,cardMeta,MaterialTheme.typography.labelMedium,
            metaColor,geometry,root,sourceScale=sourceScale)
        if(source.origin==ArticleOrigin.STACK)Box(Modifier.offset {IntOffset((card.center.x-root.x-16.dp.toPx()*sourceScale).roundToInt(),
            (card.top+12.dp.toPx()*sourceScale-root.y).roundToInt())}
            .size((32f*sourceScale).dp,(3f*sourceScale).dp).graphicsLayer {alpha=1f-SceneMotion.segment(p,.62f,.78f)}
            .background(source.ink.copy(alpha=.55f),RoundedCornerShape(2.dp)))
    }
}

/** Measure each endpoint once. Only layers scale/move; width and font never reflow in flight. */
@Composable
private fun StableReadingText(text: String,from: Rect,to: Rect,fromStyle: TextStyle,toStyle: TextStyle,
    color: Color,progress: Float,root: Offset,sourceLines: Int=1,targetLines: Int=1,targetText: String=text,sourceScale: Float=1f,
    positionProgress: Float=progress) {
    val density=LocalDensity.current
    val target=if(to==Rect.Zero)from else to
    val ratio=toStyle.fontSize.value/fromStyle.fontSize.value
    val zoom=sourceScale+(ratio-sourceScale)*progress
    val blend=SceneMotion.segment(progress,.2f,.8f)
    fun placement(width: Float,scale: Float,opacity: Float)=Modifier
        .offset {IntOffset((from.left+(target.left-from.left)*positionProgress-root.x).roundToInt(),
            (from.top+(target.top-from.top)*positionProgress-root.y).roundToInt())}
        .requiredWidth(with(density){width.toDp()})
        .graphicsLayer {transformOrigin=TransformOrigin(0f,0f);scaleX=scale;scaleY=scale;alpha=opacity}
        .clearAndSetSemantics {}
    Text(text,modifier=placement(from.width,zoom,1f-blend),style=fromStyle,color=color,
        maxLines=sourceLines,softWrap=sourceLines>1,overflow=TextOverflow.Ellipsis)
    Text(targetText,modifier=placement(target.width,zoom/ratio,blend),style=toStyle,color=color,
        maxLines=targetLines,softWrap=targetLines>1,overflow=TextOverflow.Ellipsis)
}
