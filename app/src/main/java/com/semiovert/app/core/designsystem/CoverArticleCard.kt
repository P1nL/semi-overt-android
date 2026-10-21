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

fun articleCoverResource(article: Article,category: Boolean): Int {
    val cover=article.cover?:if(article.tone==CardTone.MINT)Cover.FOREST else Cover.ARCHITECTURE
    return when {
        category&&cover==Cover.FOREST->R.drawable.category_cover_forest
        category->R.drawable.category_cover_architecture
        cover==Cover.FOREST->R.drawable.home_cover_forest
        else->R.drawable.home_cover_architecture
    }
}

/** The category skin owns its handle and reading behavior, wherever it is used. */
@Composable
fun CoverArticleCard(article: Article,selected: Boolean,modifier: Modifier,coverTag: String,topAligned: Boolean=false,squareBottom: Boolean=false,shortBottomBlur: Boolean=false,onClick: (()->Unit)?=null,interactable: Boolean=true,exitRank: Int?=null,
    readingOrigin: ArticleOrigin?=if(topAligned&&squareBottom)ArticleOrigin.STACK else null,
    allowLift: Boolean=selected,onRead: (()->Unit)?=onClick,shadowElevation: Dp=2.dp,stackLayer: Float?=null) {
    val shared=readingOrigin!=null
    val handle=topAligned&&squareBottom
    val expansion=LocalArticleExpansion.current
    val colors=MaterialTheme.colorScheme
    val cover=article.cover?:if(article.tone==CardTone.MINT)Cover.FOREST else Cover.ARCHITECTURE
    val lightCopy=topAligned&&cover==Cover.FOREST
    val ink=if(lightCopy)if(colors.surface.luminance()<colors.onSurface.luminance())colors.surface else colors.onSurface else Color.White
    var bounds by remember(article.id){mutableStateOf<LayoutCoordinates?>(null)}
    var title by remember(article.id){mutableStateOf<LayoutCoordinates?>(null)}
    var author by remember(article.id){mutableStateOf<LayoutCoordinates?>(null)}
    var date by remember(article.id){mutableStateOf<LayoutCoordinates?>(null)}
    var duration by remember(article.id){mutableStateOf<LayoutCoordinates?>(null)}
    var restingTop by remember(article.id){mutableFloatStateOf(0f)}
    fun measured(coordinates: LayoutCoordinates?)=coordinates?.takeIf {it.isAttached}?.articleBoundsInRoot()?:Rect.Zero
    val density=LocalDensity.current.density
    val screenHeight=LocalConfiguration.current.screenHeightDp*density
    val currentClick by rememberUpdatedState(onClick)
    val currentRead by rememberUpdatedState(onRead)
    val enabled by rememberUpdatedState(interactable&&allowLift)
    val source by rememberUpdatedState({ArticleSource(article,measured(bounds),measured(title),measured(author),measured(date),measured(duration),ink,readingOrigin?:ArticleOrigin.STACK,stackLayer)})
    val openReading: ()->Unit={currentRead?.let {read->if(shared&&expansion!=null)expansion.open(source(),read) else read()}}
    val open: ()->Unit={if(shared&&selected)openReading() else currentClick?.invoke()}
    val click=if(onClick!=null)Modifier.clickable(enabled=interactable&&expansion?.active!=true,onClickLabel=if(selected)"阅读文章" else "选择文章",onClick=open) else Modifier
    val readingTarget=if(shared&&exitRank!=null&&expansion?.active==true&&expansion.source?.article?.id!=article.id)
        SceneMotion.readingOffset(expansion.progress,exitRank,restingTop,screenHeight) else 0f
    val readingFollow by animateFloatAsState(readingTarget,SceneMotion.readingFollowerSpring(exitRank?:0),label="reading-list-"+article.id)
    Surface(modifier=modifier.then(click).onGloballyPositioned {
        bounds=it;restingTop=it.articleBoundsInRoot().top-readingFollow;expansion?.sourceReady(source())
    }.graphicsLayer {
        alpha=if(shared&&expansion?.source?.article?.id==article.id)0f else 1f
        translationY=readingFollow
    },color=colors.surfaceVariant,contentColor=ink,
        shape=RoundedCornerShape(18.dp,18.dp,if(squareBottom)0.dp else 18.dp,if(squareBottom)0.dp else 18.dp),
        border=BorderStroke(if(selected)1.dp else .5.dp,Color.White.copy(alpha=if(selected).28f else .12f)),shadowElevation=shadowElevation) {
        Box(Modifier.fillMaxSize()) {
            Image(painterResource(articleCoverResource(article,shortBottomBlur)),null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().testTag(coverTag))
            Column(Modifier.align(if(topAligned)Alignment.TopStart else Alignment.BottomStart).fillMaxWidth().padding(start=18.dp,end=18.dp,top=if(handle)34.dp else 18.dp,bottom=18.dp)) {
                Text(article.title,style=MaterialTheme.typography.titleLarge.copy(fontFamily=FontFamily.SansSerif,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,
                    shadow=if(lightCopy)Shadow.None else Shadow(Color.Black.copy(alpha=.22f),Offset(0f,1f),4f)),maxLines=3,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.onGloballyPositioned {title=it;expansion?.sourceReady(source())}.testTag("card-title-"+article.id))
                Row(Modifier.padding(top=5.dp)) {
                    Text(article.author.name,style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {author=it;expansion?.sourceReady(source())})
                    Text(" · ",style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp))
                    Text("约 "+article.minutes+" 分钟",style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {duration=it;expansion?.sourceReady(source())})
                }
                Text(article.date,style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1,modifier=Modifier.onGloballyPositioned {date=it;expansion?.sourceReady(source())})
            }
            if(handle) {
                val gesture=if(expansion==null)Modifier else Modifier.pointerInput(article.id,expansion) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        if(!enabled||expansion.active)return@awaitEachGesture
                        val velocity=VelocityTracker();velocity.addPosition(down.uptimeMillis,down.position)
                        var acquired=false;var released=false
                        try {
                            while(true) {
                                val change=awaitPointerEvent().changes.firstOrNull {it.id==down.id}?:break
                                velocity.addPosition(change.uptimeMillis,change.position)
                                if(!change.pressed) {
                                    if(acquired){released=true;expansion.release(-velocity.calculateVelocity().y/density)}
                                    break
                                }
                                val up=down.position.y-change.position.y
                                val side=abs(down.position.x-change.position.x)
                                if(!acquired&&change.isConsumed)break
                                if(!acquired&&up>8f*density&&up>side*1.2f) {
                                    acquired=expansion.begin(source()){currentRead?.invoke()}
                                    if(!acquired){change.consume();break}
                                }
                                if(acquired){change.consume();expansion.drag(up/(SceneMotion.LIFT_DISTANCE_DP*density))}
                            }
                        } finally {if(acquired&&!released)expansion.cancel()}
                    }
                }
                Box(Modifier.align(Alignment.TopCenter).size(96.dp,48.dp).then(gesture)
                    .then(if(allowLift)Modifier.testTag("article-handle-"+article.id).semantics {contentDescription="上提展开阅读"}
                        .clickable(enabled=interactable&&expansion?.active!=true,onClickLabel="阅读文章",onClick=openReading) else Modifier.clearAndSetSemantics {})) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top=12.dp).size(32.dp,3.dp).background(ink.copy(alpha=.55f),RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
