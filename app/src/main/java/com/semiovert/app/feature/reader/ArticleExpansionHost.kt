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
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.readingStackOverlay
import kotlin.math.roundToInt

@Composable
internal fun rememberArticleExpansionState(): ArticleExpansionState {
    val scope=rememberCoroutineScope()
    return remember(scope) {ArticleExpansionState(scope)}
}

@Composable
fun ArticleExpansionHost(expansion: ArticleExpansionState=rememberArticleExpansionState(),content: @Composable ()->Unit) {
    val motion=LocalMotionEnabled.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val business=LocalBusiness.current
    val readCache=remember(business) {business?.let {it::cachedArticle}}
    SideEffect {
        expansion.motionEnabled=motion
        expansion.onStart={focus.clearFocus();keyboard?.hide()}
        expansion.readCachedArticle=readCache
    }
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
    val contentProgress=state.contentProgress
    val body=SceneMotion.readingBody(contentProgress,state.returning,source.origin)
    val avatar=SceneMotion.readingOpeningAvatar(contentProgress,source.origin,state.returning)
    val returningReader=state.returningReader
    val readerArticle=returningReader?.article ?: source.article
    val loader=state.openingLoad?.takeIf {it.cacheMissed}?.playback
    // Both directions share the copy path and fixed-size cover, with independent clocks.
    val textPosition=if(anchors.title==Rect.Zero)0f else SceneMotion.readingOpeningText(contentProgress,source.origin,state.returning)
    val geometry=SoEase.transform(textPosition)
    val color=lerp(source.ink,MaterialTheme.colorScheme.onSurface,geometry)
    val metaColor=lerp(source.ink,MaterialTheme.colorScheme.onSurfaceVariant,geometry)
    Box(Modifier.fillMaxSize().zIndex(20f)
        .readingStackOverlay(source.stackLayer) {true}.testTag("reading-expansion").semantics {
        stateDescription=if(state.settling)"阅读转场收束中" else "上提展开中"
        progressBarRangeInfo=ProgressBarRangeInfo(p.coerceIn(0f,1f),0f..1f)
    }.pointerInput(Unit){awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
        val b=source.card
        val lift=SceneMotion.readingOpeningLift(p,source.origin)
        val card=Rect(Offset(b.left,b.top-SceneMotion.LIFT_DISTANCE_DP*density.density*lift),b.size)
        val cardAlpha=SceneMotion.readingOpeningCardAlpha(contentProgress,source.origin,state.returning)
        fun carried(anchor: Rect)=Rect(Offset(card.left+anchor.left-b.left,
            card.top+anchor.top-b.top),anchor.size)
        Surface(Modifier.offset {IntOffset((card.left-root.x).roundToInt(),(card.top-root.y).roundToInt())}
            .wrapContentSize(Alignment.TopStart,unbounded=true)
            .requiredSize(with(density){card.width.toDp()},with(density){card.height.toDp()})
            .graphicsLayer {alpha=cardAlpha}
            .testTag("reading-card-background"),
            shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,
                bottomStart=(if(source.origin==ArticleOrigin.HOME)18f else 0f).dp,
                bottomEnd=(if(source.origin==ArticleOrigin.HOME)18f else 0f).dp),
            border=if(source.origin==ArticleOrigin.HOME)articleCardBorder(selected=true) else null,
            shadowElevation=(if(source.origin==ArticleOrigin.HOME)10f else 2f).dp,
            color=MaterialTheme.colorScheme.background) {
            ArticleCoverArtwork(source.article,source.origin==ArticleOrigin.STACK,Modifier.fillMaxSize(),source.coverColor)
        }
        if(returningReader!=null)returningReader.layout(anchors,body,avatar)
        else ReaderArticleLayout(source.article,rememberLazyListState(),{},anchors,
            bodyProgress=body,avatarProgress=avatar,
            headingProgress=geometry,tag="reader-preview") {
            if(loader!=null)ContentLoading(true,READER_BODY_LOADING,Modifier.align(Alignment.Center),playback=loader)
        }
        val cardTitle=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
        val cardMeta=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp)
        StableReadingText(source.article.title,carried(source.title),anchors.title,cardTitle,MaterialTheme.typography.headlineLarge,
            color,geometry,root,sourceLines=3,targetLines=Int.MAX_VALUE,targetText=readerArticle.title,positionProgress=textPosition,tag="reading-copy-title")
        StableReadingText(source.article.author.name,carried(source.author),anchors.author,cardMeta,MaterialTheme.typography.labelLarge,
            color,geometry,root,targetText=readerArticle.author.name,positionProgress=textPosition)
        val separator=Rect(source.author.right,source.author.top,source.duration.left,source.author.bottom)
        if(separator.width>0f)StableReadingText(" · ",carried(separator),Rect.Zero,cardMeta,cardMeta,
            color,geometry,root,targetText="",positionProgress=textPosition)
        StableReadingText(source.article.date,carried(source.date),anchors.date,cardMeta,MaterialTheme.typography.labelMedium,
            metaColor,geometry,root,targetText=readerArticle.date+" · ",positionProgress=textPosition,interfaceDigits=true)
        StableReadingText("约 "+source.article.minutes+" 分钟",carried(source.duration),anchors.duration,cardMeta,MaterialTheme.typography.labelMedium,
            metaColor,geometry,root,targetText="约 "+readerArticle.minutes+" 分钟",positionProgress=textPosition,interfaceDigits=true)
        if(source.origin==ArticleOrigin.STACK)Box(Modifier.offset {IntOffset((card.center.x-root.x-16.dp.toPx()).roundToInt(),
            (card.top+12.dp.toPx()-root.y).roundToInt())}
            .size(32.dp,3.dp).graphicsLayer {alpha=cardAlpha}
            .background(source.ink.copy(alpha=.55f),RoundedCornerShape(2.dp)))
    }
}

/** Measure each endpoint once. Only layers scale/move; width and font never reflow in flight. */
@Composable
private fun StableReadingText(text: String,from: Rect,to: Rect,fromStyle: TextStyle,toStyle: TextStyle,
    color: Color,progress: Float,root: Offset,sourceLines: Int=1,targetLines: Int=1,targetText: String=text,
    positionProgress: Float=progress,tag: String?=null,interfaceDigits: Boolean=false) {
    val density=LocalDensity.current
    val target=if(to==Rect.Zero)from else to
    val ratio=toStyle.fontSize.value/fromStyle.fontSize.value
    val zoom=1f+(ratio-1f)*progress
    val blend=SceneMotion.segment(progress,.2f,.8f)
    fun placement(width: Float,scale: Float,opacity: Float,layer: String)=Modifier
        .offset {IntOffset((from.left+(target.left-from.left)*positionProgress-root.x).roundToInt(),
            (from.top+(target.top-from.top)*positionProgress-root.y).roundToInt())}
        .requiredWidth(with(density){width.toDp()})
        .graphicsLayer {transformOrigin=TransformOrigin(0f,0f);scaleX=scale;scaleY=scale;alpha=opacity}
        .clearAndSetSemantics {if(tag!=null)testTag="$tag-$layer"}
    Text(if(interfaceDigits)text.withInterfaceDigits() else AnnotatedString(text),
        modifier=placement(from.width,zoom,1f-blend,"source"),style=fromStyle,color=color,
        maxLines=sourceLines,softWrap=sourceLines>1,overflow=TextOverflow.Ellipsis)
    Text(if(interfaceDigits)targetText.withInterfaceDigits() else AnnotatedString(targetText),
        modifier=placement(target.width,zoom/ratio,blend,"target"),style=toStyle,color=color,
        maxLines=targetLines,softWrap=targetLines>1,overflow=TextOverflow.Ellipsis)
}
