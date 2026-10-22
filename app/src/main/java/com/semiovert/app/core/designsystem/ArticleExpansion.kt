package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.semiovert.app.core.model.Article
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** All rectangles use boundsInRoot, including the measured reader destination. */
enum class ArticleOrigin { HOME, STACK }
data class ArticleSource(val article: Article,val card: Rect,val title: Rect,val author: Rect,val date: Rect,val duration: Rect,val ink: Color,
    val origin: ArticleOrigin=ArticleOrigin.STACK,val stackLayer: Float?=null)
/** Keep the full card crop even when a profile card extends below a LazyColumn viewport. */
fun LayoutCoordinates.articleBoundsInRoot()=Rect(localToRoot(Offset.Zero),Size(size.width.toFloat(),size.height.toFloat()))
@Stable class ReaderAnchors {
    var title by mutableStateOf(Rect.Zero)
    var author by mutableStateOf(Rect.Zero)
    var date by mutableStateOf(Rect.Zero)
    var duration by mutableStateOf(Rect.Zero)
}

@Stable class ArticleExpansionState(private val scope: CoroutineScope) {
    var source by mutableStateOf<ArticleSource?>(null);private set
    var progress by mutableFloatStateOf(0f);private set
    var settling by mutableStateOf(false);private set
    var returning by mutableStateOf(false);private set
    private var sourceMeasured by mutableStateOf(false)
    var anchors by mutableStateOf(ReaderAnchors());private set
    private val origins=mutableStateListOf<Pair<ArticleSource,ReaderAnchors>>()
    var motionEnabled=true
    var onStart: ()->Unit={}
    private var navigate: ()->Unit={}
    private var job: Job?=null
    private var committed=false
    private var velocity=0f
    val active get()=source!=null
    fun begin(origin: ArticleSource,onRead: ()->Unit): Boolean {
        if(active)return false
        if(!motionEnabled||origin.card==Rect.Zero){onRead();return false}
        job?.cancel();onStart();navigate=onRead;committed=false;settling=false;returning=false;progress=0f;velocity=0f;anchors=ReaderAnchors();source=origin
        return true
    }
    fun open(origin: ArticleSource,onRead: ()->Unit) {if(begin(origin,onRead))finish(true)}
    fun drag(value: Float) {if(active&&!settling)progress=SceneMotion.readingDrag(value)}
    fun release(velocityDp: Float) {if(active)finish(
        SceneMotion.commits(progress/SceneMotion.READING_DRAG_STAGE,velocityDp),SceneMotion.readingReleaseVelocity(velocityDp))}
    fun cancel() {if(active&&!committed&&!returning)finish(false)}
    private fun finish(commit: Boolean,initialVelocity: Float=velocity) {
        job?.cancel();settling=true
        job=scope.launch {
            val target=if(commit)1f else 0f
            animate(progress,target,initialVelocity=initialVelocity,
                animationSpec=when {
                    !motionEnabled->snap()
                    commit->SceneMotion.readingOpenSpring()
                    else->SceneMotion.readingSpring()
                }) {value,speed->progress=value;velocity=speed}
            progress=target;velocity=0f;settling=false
            if(commit){committed=true;navigate()}else source=null
        }
    }
    /** The measured reader has entered composition; no duplicate route animation. */
    fun readerReady(id: String) {
        if(committed&&source?.article?.id==id) {
            origins.add(source!! to anchors);source=null;progress=0f;committed=false
        }
    }
    fun close(id: String,onBack: ()->Unit) {
        if(active)return
        val origin=origins.lastOrNull()?.takeIf {it.first.article.id==id}
        if(origin==null){onBack();return}
        origins.removeAt(origins.lastIndex)
        if(!motionEnabled){onBack();return}
        job?.cancel();onStart();returning=true;settling=true;sourceMeasured=false;progress=1f;velocity=0f;anchors=origin.second;source=origin.first
        onBack()
        job=scope.launch {
            // Reverse only once the real source is laid out again, not against an estimated card.
            snapshotFlow {sourceMeasured}.first {it}
            animate(1f,0f,animationSpec=SceneMotion.readingCloseSpec()){value,speed->progress=value;velocity=speed}
            progress=0f;velocity=0f;source=null;returning=false;settling=false
        }
    }
    fun sourceReady(origin: ArticleSource) {
        if(returning&&!sourceMeasured&&source?.article?.id==origin.article.id&&
            listOf(origin.card,origin.title,origin.author,origin.date,origin.duration).none {it==Rect.Zero}) {
            source=origin;sourceMeasured=true
        }
    }
    /** A reader popped by its author link no longer owns a reverse card transition. */
    fun forgetLastOrigin(id: String) {
        if(origins.lastOrNull()?.first?.article?.id==id)origins.removeAt(origins.lastIndex)
    }
    fun forgetOrigins() {origins.clear()}
}
val LocalArticleExpansion=staticCompositionLocalOf<ArticleExpansionState?> {null}
/** Source-page chrome follows the drag itself, not the final navigation callback. */
@Composable
fun Modifier.readingChromeFade(): Modifier {
    val expansion=LocalArticleExpansion.current
    return graphicsLayer {alpha=if(expansion?.active==true)1f-SceneMotion.readingChrome(expansion.progress) else 1f}
}
val LocalFlowPhase=compositionLocalOf {FlowPhase.IDLE}
val LocalHomeEntry=compositionLocalOf {0}
val LocalProfileEntryProgress=compositionLocalOf {1f}
