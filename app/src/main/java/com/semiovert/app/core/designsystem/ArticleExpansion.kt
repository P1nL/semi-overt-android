package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.Color
import com.semiovert.app.core.model.Article
import kotlinx.coroutines.*

/** All rectangles use boundsInRoot, including the measured reader destination. */
data class ArticleSource(val article: Article,val card: Rect,val title: Rect,val author: Rect,val date: Rect,val duration: Rect,val ink: Color)
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
    var motionEnabled=true
    var onStart: ()->Unit={}
    private var navigate: ()->Unit={}
    private var job: Job?=null
    private var committed=false
    val active get()=source!=null
    fun begin(origin: ArticleSource,onRead: ()->Unit): Boolean {
        if(active)return false
        if(!motionEnabled||origin.card==Rect.Zero){onRead();return false}
        job?.cancel();onStart();navigate=onRead;committed=false;settling=false;progress=0f;source=origin
        return true
    }
    fun open(origin: ArticleSource,onRead: ()->Unit) {if(begin(origin,onRead))finish(true)}
    fun drag(value: Float) {if(active&&!settling)progress=value.coerceIn(0f,1f)}
    fun release(velocityDp: Float) {if(active)finish(SceneMotion.commits(progress,velocityDp))}
    fun cancel() {if(active&&!committed)finish(false)}
    private fun finish(commit: Boolean) {
        job?.cancel();settling=true
        job=scope.launch {
            val target=if(commit)1f else 0f
            val duration=if(!motionEnabled)0 else if(commit)(SceneMotion.READING_MS*(1f-progress)).toInt().coerceAtLeast(180) else 240
            animate(progress,target,animationSpec=tween(duration,easing=SoEase)){value,_->progress=value}
            progress=target;settling=false
            if(commit){committed=true;navigate()}else source=null
        }
    }
    /** The measured reader has entered composition; no duplicate route animation. */
    fun readerReady(id: String) {if(committed&&source?.article?.id==id){source=null;progress=0f;committed=false}}
}
val LocalArticleExpansion=staticCompositionLocalOf<ArticleExpansionState?> {null}
val LocalFlowPhase=compositionLocalOf {FlowPhase.IDLE}
val LocalHomeEntry=compositionLocalOf {0}
val LocalProfileEntryProgress=compositionLocalOf {1f}
