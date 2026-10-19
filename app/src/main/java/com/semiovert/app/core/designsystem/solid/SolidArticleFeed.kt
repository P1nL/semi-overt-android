package com.semiovert.app.core.designsystem.solid

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.*
import kotlin.math.*

@Composable
fun SolidArticleFeed(articles: List<Article>,tag: String,onArticle: (String)->Unit,onUnavailable: ()->Unit,modifier: Modifier=Modifier,initialScroll: Float=0f) {
    var scroll by rememberSaveable {mutableFloatStateOf(initialScroll)}
    var viewportWidth by remember {mutableIntStateOf(1)}
    var viewportHeight by remember {mutableIntStateOf(1)}
    var top by remember {mutableFloatStateOf(0f)}
    var ready by remember {mutableStateOf(false)}
    val scale=viewportWidth/412f
    val maximum=max(0f,(articles.size-1)*SolidFlowMath.STRIDE)
    val roll=LocalFlowRoll.current
    val currentRoll by rememberUpdatedState(roll)
    val textures=remember(articles.map {it.id}) {mutableStateMapOf<String,SolidTexture>()}
    val frames=remember(scroll,articles.size) {articles.indices.map {SolidFlowMath.frame(it,scroll,slots=articles.size)}}
    val currentFrames by rememberUpdatedState(frames)
    val currentArticle by rememberUpdatedState(onArticle)
    val currentArticles by rememberUpdatedState(articles)
    val scrollable=rememberScrollableState {delta ->
        val old=scroll
        scroll=(scroll-delta/scale).coerceIn(0f,maximum)
        (old-scroll)*scale
    }
    val pageColor=MaterialTheme.colorScheme.background.toArgb()
    val spec=SolidRenderSpec(articles.map {textures[it.id]},scroll,roll,top,pageColor)
    Box(modifier.fillMaxSize().clipToBounds().testTag(tag).onGloballyPositioned {viewportWidth=it.size.width;viewportHeight=it.size.height;top=it.positionInRoot().y/(it.size.width/412f)}
        .semantics {
            isTraversalGroup=true
            stateDescription=if(ready)"实体卡片就绪" else "实体卡片准备中"
            verticalScrollAxisRange=ScrollAxisRange({scroll*scale},{maximum*scale})
            scrollToIndex {index ->if(index in articles.indices) {scroll=index*SolidFlowMath.STRIDE;true} else false}
            scrollBy {_,y ->scroll=(scroll+y/scale).coerceIn(0f,maximum);true}
        }.scrollable(scrollable,Orientation.Vertical,enabled=roll<.001f)
        .pointerInput(articles.map {it.id},scale,top) {
            detectTapGestures {position ->
                if(currentRoll<.001f) {
                    val index=SolidFlowMath.pick(currentFrames,position.x/scale-206f,position.y/scale+top)
                    index?.let {currentArticles.getOrNull(it)?.let {article ->currentArticle(article.id)}}
                }
            }
        }) {
        AndroidView(factory={context ->SolidSceneView(context).apply {onFrame={ready=it};onFailure={onUnavailable()}}},modifier=Modifier.fillMaxSize(),update={it.submit(spec)},onRelease={it.dispose()})
        // Native Compose content is recorded only for texture updates, never drawn twice.
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            articles.forEach {article ->key(article.id) {ArticleTexture(article) {textures[article.id]=it}}}
        }
        if(roll<.001f)articles.forEachIndexed {index,article ->
            val bounds=SolidFlowMath.frontBounds(frames[index])?:return@forEachIndexed
            val left=(viewportWidth/2+bounds.left*scale).coerceIn(0f,viewportWidth.toFloat())
            val right=(viewportWidth/2+bounds.right*scale).coerceIn(0f,viewportWidth.toFloat())
            val y=((bounds.top-top)*scale).coerceIn(0f,viewportHeight.toFloat())
            val bottom=((bounds.bottom-top)*scale).coerceIn(0f,viewportHeight.toFloat())
            if(right>left&&bottom>y)Box(Modifier.offset {IntOffset(left.roundToInt(),y.roundToInt())}.requiredSize(with(LocalDensity.current){(right-left).toDp()},with(LocalDensity.current){(bottom-y).toDp()})
                .testTag("article-"+article.id).semantics {
                    role=Role.Button
                    contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                    onClick("阅读文章") {onArticle(article.id);true}
                })
        }
    }
}

@Composable
internal fun ArticleTexture(article: Article,height: Dp=352.dp,onReady: (SolidTexture)->Unit) {
    val mint=article.tone==CardTone.MINT
    val background=if(mint)MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
    val foreground=if(mint)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer
    val back=if(MaterialTheme.colorScheme.background.luminance()>.5f)if(mint)Color(.74f,.86f,.77f) else Color(.88f,.76f,.65f) else background
    val layer=rememberGraphicsLayer()
    var recorded by remember(article,background,foreground) {mutableStateOf(false)}
    val currentReady by rememberUpdatedState(onReady)
    LaunchedEffect(recorded,article,background,foreground) {
        if(recorded) {
            withFrameNanos { }
            val image=layer.toImageBitmap().asAndroidBitmap()
            val readable=if(image.config==Bitmap.Config.HARDWARE)image.copy(Bitmap.Config.ARGB_8888,false) else image
            currentReady(SolidTexture(article.id,readable,back.toArgb()))
        }
    }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Box(Modifier.requiredSize(372.dp,height).drawWithContent {layer.record {this@drawWithContent.drawContent()};if(!recorded)recorded=true}.background(background)) {
            article.cover?.let {SampleCover(it,Modifier.fillMaxWidth().height(176.dp))}
            val textTop=if(article.cover!=null)176.dp else 0.dp
            Text(article.title,style=MaterialTheme.typography.titleLarge.copy(lineHeight=29.sp),maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=18.dp,end=18.dp,top=textTop+14.dp))
            if(article.cover==null&&article.summary!=null)Text(article.summary,style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=18.dp,end=18.dp,top=118.dp))
            Row(Modifier.align(Alignment.BottomStart).padding(start=18.dp,bottom=56.dp),verticalAlignment=Alignment.CenterVertically) {
                SoIcon(SoSymbol.CLOCK,tint=foreground,modifier=Modifier.size(15.dp))
                Text("约 "+article.minutes+" 分钟",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(start=5.dp))
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                ProfileAvatar(size=26)
                Text(article.author.name,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f).padding(start=8.dp))
                SoIcon(SoSymbol.ARROW,tint=foreground,modifier=Modifier.size(18.dp))
            }
        }
    }
}
