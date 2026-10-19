package com.semiovert.app.core.designsystem.cuboid

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.LocalFlowRoll
import com.semiovert.app.core.designsystem.solid.ArticleTexture
import com.semiovert.app.core.designsystem.solid.SolidTexture
import com.semiovert.app.core.model.Article
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CuboidTurnSaver=listSaver<CuboidTurn,Any>(
    save={turn ->
        val state=turn.snapshot()
        listOf<Any>(turn.count,state.selected,state.cursor,state.rotation.w,state.rotation.x,state.rotation.y,state.rotation.z)+state.articles
    },
    restore={values ->
        CuboidTurn(values[0] as Int,values[1] as Int,CuboidSnapshot(
            CubeRotation(values[3] as Float,values[4] as Float,values[5] as Float,values[6] as Float),
            values.drop(7).map {it as Int},values[2] as Int,values[1] as Int))
    },
)

/** Six opaque, perspective-projected planes; no per-frame bitmap readback or EGL surface. */
@Composable
fun CuboidArticleDeck(
    articles: List<Article>, selected: Int, onSelected: (Int)->Unit, onArticle: (String)->Unit,
    modifier: Modifier=Modifier,
) {
    val ids=articles.map {it.id}
    val pageColor=MaterialTheme.colorScheme.background.toArgb()
    val textures=remember(ids,pageColor) {mutableStateMapOf<String,SolidTexture>()}
    var turn by rememberSaveable(ids,stateSaver=CuboidTurnSaver) {mutableStateOf(CuboidTurn(articles.size,selected))}
    var frame by remember(turn) {mutableStateOf(turn.frame())}
    var dragging by remember(turn) {mutableStateOf(false)}
    var settling by remember(turn) {mutableStateOf(false)}
    LaunchedEffect(selected) {if(turn.selected!=selected)turn=CuboidTurn(articles.size,selected)}
    val ready=articles.all {textures[it.id]!=null}
    val roll=LocalFlowRoll.current
    val interactive=ready&&roll<.001f
    val scope=rememberCoroutineScope()
    val select by rememberUpdatedState(onSelected)
    val read by rememberUpdatedState(onArticle)
    val bitmapPaint=remember {Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)}
    val shadePaint=remember {Paint(Paint.ANTI_ALIAS_FLAG)}
    val bevelPaint=remember {Paint(Paint.ANTI_ALIAS_FLAG).apply {style=Paint.Style.FILL_AND_STROKE;strokeWidth=.7f}}
    val matrix=remember {Matrix()}
    val clip=remember {Path()}

    fun settle(): Boolean {
        if(!interactive||settling) return false
        val plan=turn.settle()
        settling=true
        scope.launch {
            try {
                val angle=plan.from.angleTo(plan.to)
                if(angle>CuboidSettleMotion.ANGULAR_THRESHOLD) {
                    animate(0f,1f,animationSpec=CuboidSettleMotion.spec(angle)) {progress,_ ->
                        frame=frame.copy(rotation=plan.from.interpolate(plan.to,progress))
                    }
                }
                frame=turn.finish()
                select(plan.article)
            } finally {settling=false}
        }
        return true
    }

    // Linear navigation remains available to assistive technology, independently
    // of the spatial gesture. It does not relabel an already visible rotating face.
    fun showArticle(index: Int): Boolean {
        if(!interactive||settling||dragging||index !in articles.indices) return false
        turn=CuboidTurn(articles.size,index)
        select(index)
        return true
    }

    BoxWithConstraints(modifier.fillMaxSize().testTag("feed-home").semantics {
        isTraversalGroup=true
        stateDescription="第 ${selected+1} 篇，共 ${articles.size} 篇，"+when {
            !ready -> "准备中"; settling -> "收束中"; dragging -> "旋转中"; else -> "已停稳"
        }
        horizontalScrollAxisRange=ScrollAxisRange({selected.toFloat()},{(articles.size-1).toFloat()},reverseScrolling=false)
        verticalScrollAxisRange=ScrollAxisRange({selected.toFloat()},{(articles.size-1).toFloat()},reverseScrolling=false)
        scrollBy {x,y ->
            val delta=if(abs(x)>abs(y))x else y
            if(delta==0f)false else showArticle((selected+(if(delta>0f)1 else -1)+articles.size)%articles.size)
        }
        scrollToIndex {index ->showArticle(index)}
        customActions=listOf(
            CustomAccessibilityAction("下一篇文章") {showArticle((selected+1)%articles.size)},
            CustomAccessibilityAction("上一篇文章") {showArticle((selected+articles.size-1)%articles.size)},
        )
    }) {
        val frontSize=maxWidth*CuboidMath.REST_WIDTH
        Box(Modifier.fillMaxWidth().fillMaxHeight(.80f).align(Alignment.Center)
            .graphicsLayer {alpha=1f-roll;translationY=32.dp.toPx()*roll;scaleX=1f-.04f*roll;scaleY=scaleX}
            .clipToBounds().testTag("home-cuboid")
            .pointerInput(turn,interactive) {
                if(!interactive) return@pointerInput
                awaitEachGesture {
                    val down=awaitFirstDown()
                    if(settling) return@awaitEachGesture
                    var moved=false
                    var ended=false
                    val filter=CuboidDragFilter(CubePoint(down.position.x,down.position.y),viewConfiguration.touchSlop,.75.dp.toPx())
                    try {
                        while(true) {
                            val event=awaitPointerEvent()
                            val change=event.changes.firstOrNull {it.id==down.id} ?: break
                            if(change.isConsumed||event.changes.count {it.pressed}>1) break
                            val delta=filter.moveTo(CubePoint(change.position.x,change.position.y))
                            if(filter.dragging) {
                                moved=true;dragging=true
                                if(delta.x!=0f||delta.y!=0f) {
                                    turn.dragScreen(delta.x,delta.y,size.width.toFloat())
                                    frame=turn.frame()
                                }
                                change.consume()
                            }
                            if(!change.pressed) {
                                ended=true;dragging=false
                                if(moved) settle()
                                else {
                                    val p=change.position-Offset(size.width/2f,size.height/2f)
                                    if(abs(p.x)<size.width*CuboidMath.REST_WIDTH/2f&&abs(p.y)<size.width*CuboidMath.REST_WIDTH/2f) {
                                        change.consume();read(articles[turn.selected].id)
                                    }
                                }
                                break
                            }
                        }
                    } finally {
                        if(!ended&&moved) {turn.cancel();frame=turn.frame();dragging=false}
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val canvas=drawContext.canvas.nativeCanvas
                val weights=CuboidMath.faces.map {face ->(frame.rotation.rotate(face.normal).z*CuboidMath.CAMERA-.5f).coerceAtLeast(0f)}
                val colors=frame.articles.map {index ->textures[articles[index].id]?.backColor ?: pageColor}
                for(bevel in CuboidMath.projectBevels(frame.rotation,size.width,size.height)) {
                    // Fillets inherit the colors of the actual adjacent articles.
                    val total=bevel.faces.sumOf {weights[it].toDouble()}.toFloat()
                    fun channel(shift: Int): Int {
                        val value=if(total>0f)bevel.faces.sumOf {(((colors[it] shr shift) and 255)*weights[it]).toDouble()}.toFloat()/total
                            else ((colors[0] shr shift) and 255).toFloat()
                        return (value*bevel.light).toInt().coerceIn(0,255)
                    }
                    bevelPaint.color=android.graphics.Color.rgb(channel(16),channel(8),channel(0))
                    clip.reset();clip.moveTo(bevel.corners[0].x,bevel.corners[0].y)
                    for(point in bevel.corners.drop(1))clip.lineTo(point.x,point.y)
                    clip.close();canvas.drawPath(clip,bevelPaint)
                }
                for(face in CuboidMath.project(frame.rotation,size.width,size.height)) {
                    val texture=textures[articles[frame.articles[face.face]].id] ?: continue
                    val bitmap=texture.bitmap
                    val source=floatArrayOf(0f,0f,bitmap.width.toFloat(),0f,bitmap.width.toFloat(),bitmap.height.toFloat(),0f,bitmap.height.toFloat())
                    val target=face.corners.flatMap {listOf(it.x,it.y)}.toFloatArray()
                    clip.reset();clip.moveTo(face.outline[0].x,face.outline[0].y)
                    for(point in face.outline.drop(1))clip.lineTo(point.x,point.y)
                    clip.close()
                    val saved=canvas.save()
                    canvas.clipPath(clip)
                    canvas.drawColor(colors[face.face])
                    if(matrix.setPolyToPoly(source,0,target,0,4))canvas.drawBitmap(bitmap,matrix,bitmapPaint)
                    shadePaint.color=android.graphics.Color.argb((face.shade*255).toInt(),0,0,0)
                    canvas.drawPath(clip,shadePaint)
                    canvas.restoreToCount(saved)
                }
            }
            if(interactive&&!dragging&&!settling) {
                val article=articles[selected]
                Box(Modifier.align(Alignment.Center).size(frontSize).testTag("article-"+article.id).semantics {
                    role=Role.Button
                    contentDescription=article.title+"，"+article.author.name+"，约"+article.minutes+"分钟"
                    onClick("阅读文章") {read(article.id);true}
                })
            }
        }
        Text("向任意方向轻划 · ${selected+1} / ${articles.size}",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=96.dp).clearAndSetSemantics {})
        // Cache the article pool once; only six physical planes are drawn per frame.
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            articles.forEach {article ->key(article.id) {ArticleTexture(article,height=372.dp) {textures[article.id]=it}}}
        }
    }
}
