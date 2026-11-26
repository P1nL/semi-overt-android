package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.semiovert.app.R
import com.semiovert.app.core.model.*
import kotlin.math.abs

private val HomeArticleCoverColors=with(SoPalette) {
    listOf(Teal,AutumnLeaf,FrenchBlue,Tomato,Olive,BrightAmber,Amethyst,CarrotOrange,MintLeaf,TomatoJam,Plum)
}

private class ArticleCardCoordinates {
    var bounds: LayoutCoordinates?=null
    var handle: LayoutCoordinates?=null
    var title: LayoutCoordinates?=null
    var author: LayoutCoordinates?=null
    var date: LayoutCoordinates?=null
    var duration: LayoutCoordinates?=null
}

internal fun homeArticleCoverColor(index: Int)=HomeArticleCoverColors[index%HomeArticleCoverColors.size]

internal fun categoryArticleCoverColor(article: Article,index: Int)=
    if(article.cover==null)homeArticleCoverColor(index) else null

internal fun articleCardBorder(selected: Boolean)=BorderStroke(
    if(selected)1.dp else .5.dp,Color.White.copy(alpha=if(selected).28f else .12f))

fun articleCoverResource(article: Article,category: Boolean): Int {
    val cover=article.cover?:if(article.tone==CardTone.MINT)Cover.FOREST else Cover.ARCHITECTURE
    return when {
        category&&cover==Cover.FOREST->R.drawable.category_cover_forest
        category->R.drawable.category_cover_architecture
        cover==Cover.FOREST->R.drawable.home_cover_forest
        else->R.drawable.home_cover_architecture
    }
}

@Composable
internal fun ArticleCoverArtwork(article: Article,category: Boolean,modifier: Modifier,coverColor: Color?,
    photo: ImageBitmap?=rememberRemoteImage(article.coverUrl,900).bitmap) {
    if (photo != null) Box(modifier) {
        Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .42f)))
    }
    else if(coverColor!=null)Box(modifier.background(coverColor))
    else Image(painterResource(articleCoverResource(article,category)),null,contentScale=ContentScale.Crop,modifier=modifier)
}

/** The category skin owns its handle and reading behavior, wherever it is used. */
@Composable
fun CoverArticleCard(article: Article,selected: Boolean,modifier: Modifier,coverTag: String,topAligned: Boolean=false,squareBottom: Boolean=false,shortBottomBlur: Boolean=false,onClick: (()->Unit)?=null,interactable: Boolean=true,exitRank: Int?=null,
    readingOrigin: ArticleOrigin?=if(topAligned&&squareBottom)ArticleOrigin.STACK else null,
    allowLift: Boolean=selected,onRead: (()->Unit)?=onClick,shadowElevation: Dp=2.dp,stackLayer: Float?=null,
    ownsReadingSource: Boolean=true,coverColor: Color?=null,openWithLift: Boolean=false) {
    val shared=readingOrigin!=null
    val handle=topAligned&&squareBottom
    val expansion=LocalArticleExpansion.current
    val colors=MaterialTheme.colorScheme
    val cover=article.cover?:if(article.tone==CardTone.MINT)Cover.FOREST else Cover.ARCHITECTURE
    val lightCopy=topAligned&&cover==Cover.FOREST
    val photo=rememberRemoteImage(article.coverUrl,900).bitmap
    val ink=when {
        photo!=null||(article.coverUrl!=null&&(coverColor==null||readingOrigin==ArticleOrigin.HOME))->Color.White
        coverColor!=null->SoPalette.inkOn(coverColor)
        lightCopy->if(colors.surface.luminance()<colors.onSurface.luminance())colors.surface else colors.onSurface
        else->Color.White
    }
    val coordinates=remember(article.id) {ArticleCardCoordinates()}
    var restingTop by remember(article.id){mutableFloatStateOf(0f)}
    fun measured(coordinates: LayoutCoordinates?)=coordinates?.takeIf {it.isAttached}?.articleBoundsInRoot()?:Rect.Zero
    val density=LocalDensity.current.density
    val screenHeight=LocalConfiguration.current.screenHeightDp*density
    val currentClick by rememberUpdatedState(onClick)
    val currentRead by rememberUpdatedState(onRead)
    val enabled by rememberUpdatedState(interactable&&allowLift)
    val source by rememberUpdatedState({ArticleSource(article,measured(coordinates.bounds),measured(coordinates.title),measured(coordinates.author),measured(coordinates.date),measured(coordinates.duration),
        ink,readingOrigin?:ArticleOrigin.STACK,stackLayer,coverColor)})
    fun sourceReady() {
        if(ownsReadingSource&&expansion?.needsSourceMeasurement(article.id)==true)expansion.sourceReady(source())
    }
    val openReading: ()->Unit={currentRead?.let {read->if(shared&&expansion!=null)expansion.open(source(),openWithLift,read) else read()}}
    val open: ()->Unit={if(shared&&selected)openReading() else currentClick?.invoke()}
    val click=if(onClick!=null)Modifier.clickable(enabled=interactable&&expansion?.active!=true,onClickLabel=if(selected)"阅读文章" else "选择文章",onClick=open) else Modifier
    val readingTarget=if(shared&&exitRank!=null&&expansion?.active==true&&expansion.source?.article?.id!=article.id)
        SceneMotion.readingOffset(expansion.progress,exitRank,restingTop,screenHeight) else 0f
    val readingFollow by animateFloatAsState(readingTarget,SceneMotion.readingFollowerSpring(exitRank?:0),label="reading-list-"+article.id)
    Surface(modifier=modifier.onGloballyPositioned {
        coordinates.bounds=it
        if(shared&&exitRank!=null)restingTop=it.articleBoundsInRoot().top-readingFollow
        sourceReady()
    }.graphicsLayer {
        alpha=if(shared&&expansion?.source?.article?.id==article.id)0f else 1f
        translationY=readingFollow
    }.then(click),color=coverColor?:colors.surfaceVariant,contentColor=ink,
        shape=RoundedCornerShape(18.dp,18.dp,if(squareBottom)0.dp else 18.dp,if(squareBottom)0.dp else 18.dp),
        border=articleCardBorder(selected),shadowElevation=shadowElevation) {
        Box(Modifier.fillMaxSize()) {
            val artwork=Modifier.fillMaxSize().testTag(coverTag)
            if(coverColor==null||article.coverUrl!=null)ArticleCoverArtwork(article,shortBottomBlur,artwork,coverColor,photo)
            else Box(artwork)
            Column(Modifier.align(if(topAligned)Alignment.TopStart else Alignment.BottomStart).fillMaxWidth().padding(start=18.dp,end=18.dp,top=if(handle)ArticleCardMetrics.STACK_TITLE_TOP_DP.dp else 18.dp,bottom=18.dp)) {
                Text(article.title,style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=ArticleCardMetrics.TITLE_FONT_SP.sp,lineHeight=ArticleCardMetrics.TITLE_LINE_SP.sp,fontWeight=FontWeight.SemiBold,
                    shadow=if(coverColor!=null||lightCopy)Shadow.None else Shadow(Color.Black.copy(alpha=.22f),Offset(0f,1f),4f)),maxLines=3,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.onGloballyPositioned {coordinates.title=it;sourceReady()}.testTag("card-title-"+article.id))
                Row(Modifier.padding(top=5.dp)) {
                    Text(article.author.name,style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {coordinates.author=it;sourceReady()})
                    Text(" · ",style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp))
                    Text(("约 "+article.minutes+" 分钟").withInterfaceDigits(),style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {coordinates.duration=it;sourceReady()})
                }
                Text(article.date.withInterfaceDigits(),style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {coordinates.date=it;sourceReady()})
            }
            if(handle) {
                val gesture=if(expansion==null)Modifier else Modifier.pointerInput(article.id,expansion) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        if(!enabled||expansion.active)return@awaitEachGesture
                        val handleCoordinates=coordinates.handle?.takeIf {it.isAttached}?:return@awaitEachGesture
                        // The card moves faster than the finger on a downward queue drag.
                        // Its moving local coordinates must never turn that drag into a lift.
                        val start=handleCoordinates.localToRoot(down.position)
                        val velocity=VelocityTracker();velocity.addPosition(down.uptimeMillis,start)
                        var acquired=false;var released=false
                        try {
                            while(true) {
                                val change=awaitPointerEvent().changes.firstOrNull {it.id==down.id}?:break
                                if(!handleCoordinates.isAttached)break
                                val point=handleCoordinates.localToRoot(change.position)
                                velocity.addPosition(change.uptimeMillis,point)
                                if(!change.pressed) {
                                    if(acquired){released=true;expansion.release(-velocity.calculateVelocity().y/density)}
                                    break
                                }
                                val up=start.y-point.y
                                val side=abs(start.x-point.x)
                                if(!acquired&&(!enabled||change.isConsumed))break
                                if(!acquired&&up>8f*density&&up>side*1.2f) {
                                    acquired=expansion.begin(source()){currentRead?.invoke()}
                                    if(!acquired){change.consume();break}
                                }
                                if(acquired){change.consume();expansion.drag(up/(SceneMotion.LIFT_DISTANCE_DP*density))}
                            }
                        } finally {if(acquired&&!released)expansion.cancelDrag()}
                    }
                }
                Box(Modifier.align(Alignment.TopCenter).size(96.dp,48.dp).onGloballyPositioned {coordinates.handle=it}.then(gesture)
                    .then(if(allowLift)Modifier.testTag("article-handle-"+article.id).semantics {contentDescription="上提展开阅读"}
                        .clickable(interactionSource=null,indication=null,enabled=interactable&&expansion?.active!=true,onClickLabel="阅读文章",onClick=openReading) else Modifier.clearAndSetSemantics {})) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top=12.dp).size(32.dp,3.dp).background(ink.copy(alpha=.55f),RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
