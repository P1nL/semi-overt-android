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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.semiovert.app.core.designsystem.*
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
    val anchors=remember(source.article.id){ReaderAnchors()}
    val density=LocalDensity.current
    val p=state.progress
    val geometry=if(anchors.title==Rect.Zero)0f else p
    val color=lerp(source.ink,MaterialTheme.colorScheme.onSurface,geometry)
    val metaColor=lerp(source.ink,MaterialTheme.colorScheme.onSurfaceVariant,geometry)
    fun mix(a: Float,b: Float)=a+(b-a)*geometry
    fun place(from: Rect,to: Rect): Modifier {
        val target=if(to==Rect.Zero)from else to
        return Modifier.offset {IntOffset((mix(from.left,target.left)-root.x).roundToInt(),(mix(from.top,target.top)-root.y).roundToInt())}
            .width(with(density){mix(from.width,target.width).toDp()})
    }
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(20f).testTag("reading-expansion").semantics {
        stateDescription=if(state.settling)"阅读转场收束中" else "上提展开中"
        progressBarRangeInfo=ProgressBarRangeInfo(p,0f..1f)
    }.pointerInput(Unit){awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
        val widthPx=with(density){maxWidth.toPx()};val heightPx=with(density){maxHeight.toPx()}
        val b=source.card
        Surface(Modifier.offset {IntOffset((mix(b.left,root.x)-root.x).roundToInt(),(mix(b.top,root.y)-root.y).roundToInt())}
            .wrapContentSize(Alignment.TopStart,unbounded=true)
            .requiredSize(with(density){mix(b.width,widthPx).toDp()},with(density){mix(b.height,heightPx).toDp()}),
            shape=RoundedCornerShape(topStart=(18f*(1f-geometry)).dp,topEnd=(18f*(1f-geometry)).dp),color=MaterialTheme.colorScheme.background) {
            Image(painterResource(articleCoverResource(source.article,true)),null,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().graphicsLayer {alpha=1f-SceneMotion.segment(p,.08f,.68f)})
        }
        ReaderArticleLayout(source.article,rememberLazyListState(),{state.cancel()},{},{},anchors,
            bodyProgress=SceneMotion.segment(p,.63f,1f),tag="reader-preview")
        val cardTitle=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold)
        val cardMeta=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp)
        Text(source.article.title,modifier=place(source.title,anchors.title).clearAndSetSemantics {},color=color,
            style=lerp(cardTitle,MaterialTheme.typography.headlineLarge,geometry),maxLines=if(p==0f)3 else Int.MAX_VALUE)
        Text(source.article.author.name,modifier=place(source.author,anchors.author).clearAndSetSemantics {},color=color,
            style=lerp(cardMeta,MaterialTheme.typography.labelLarge,geometry),maxLines=1)
        Text(source.article.date+(if(p>.8f)" · " else ""),modifier=place(source.date,anchors.date).clearAndSetSemantics {},color=metaColor,
            style=lerp(cardMeta,MaterialTheme.typography.labelMedium,geometry),maxLines=1)
        Text("约 "+source.article.minutes+" 分钟",modifier=place(source.duration,anchors.duration).clearAndSetSemantics {},color=metaColor,
            style=lerp(cardMeta,MaterialTheme.typography.labelMedium,geometry),maxLines=1)
        Box(Modifier.offset {IntOffset((mix(b.center.x,root.x+widthPx/2f)-root.x-16.dp.toPx()).roundToInt(),(mix(b.top+12.dp.toPx(),root.y)-root.y).roundToInt())}
            .size(32.dp,3.dp).graphicsLayer {alpha=1f-SceneMotion.segment(p,0f,.24f)}.background(source.ink.copy(alpha=.55f),RoundedCornerShape(2.dp)))
    }
}
