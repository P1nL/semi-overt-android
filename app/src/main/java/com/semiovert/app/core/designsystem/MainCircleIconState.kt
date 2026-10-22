package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class IconContourFrame(val points: List<Offset>,val width: Float,val fill: Float)
internal data class IconFrame(val symbol: SoSymbol?,val contours: List<IconContourFrame>) {
    companion object {
        fun endpoint(symbol: SoSymbol): IconFrame {
            val shape=ReadingIconMorph(symbol,symbol)
            return IconFrame(symbol,shape.strokes.map {IconContourFrame(it.source,it.widthAt(0f),it.fillAt(0f))})
        }
    }
}

/** A handoff starts at the actually drawn contour, even if the previous morph was interrupted. */
internal class MainIconMorph(val source: IconFrame,val target: IconFrame) {
    val strokes=source.contours.zip(target.contours).map {(from,to)->ReadingIconMorph.MorphStroke(
        from.points,to.points,from.width,to.width,from.fill,to.fill)}

    fun frame(progress: Float): IconFrame {
        val p=progress.coerceIn(0f,1f)
        if(p==0f)return source
        if(p==1f)return target
        return IconFrame(null,strokes.map {stroke->IconContourFrame(
            stroke.source.indices.map {stroke.pointAt(it,p)},stroke.widthAt(p),stroke.fillAt(p))})
    }
}

/** Kept outside individual routes, just like the single-owner circle/shadow drawing plane. */
@Stable
internal class MainCircleIconState(private val scope: CoroutineScope) {
    var morph by mutableStateOf<MainIconMorph?>(null);private set
    var progress by mutableFloatStateOf(1f);private set
    private val endpoints=mutableMapOf<SoSymbol,IconFrame>()
    private var job: Job?=null
    private var requestedPage: SoSymbol?=null
    private var controlledPage: SoSymbol?=null
    private var mergeOwner: Any?=null

    private fun endpoint(symbol: SoSymbol)=endpoints.getOrPut(symbol) {IconFrame.endpoint(symbol)}

    fun showPage(symbol: SoSymbol,motion: Boolean,ownerKey: Any?=null) {
        if(mergeOwner!=null&&mergeOwner===ownerKey&&requestedPage!=symbol)return
        mergeOwner=null
        if(controlledPage==null&&requestedPage==symbol) {
            if(!motion){job?.cancel();progress=1f}
            return
        }
        val from=morph?.frame(progress) ?: endpoint(symbol)
        job?.cancel();controlledPage=null;requestedPage=symbol
        morph=MainIconMorph(from,endpoint(symbol))
        if(!motion||from.symbol==symbol){progress=1f;return}
        progress=0f
        job=scope.launch {
            animate(0f,1f,animationSpec=tween(SceneMotion.MAIN_ICON_MORPH_MS,easing=SoSceneEase)) {value,_->progress=value}
            progress=1f
        }
    }

    fun beginMerge(ownerKey: Any,symbol: SoSymbol) {
        val from=morph?.frame(progress) ?: endpoint(symbol)
        job?.cancel();controlledPage=null;requestedPage=symbol;mergeOwner=ownerKey
        morph=MainIconMorph(from,endpoint(symbol));progress=0f
    }

    fun merge(value: Float) {progress=value.coerceIn(0f,1f)}

    fun showReading(page: SoSymbol,value: Float,ownerKey: Any?=null) {
        if(mergeOwner!=null&&mergeOwner===ownerKey)return
        mergeOwner=null
        if(controlledPage!=page) {
            job?.cancel();requestedPage=null;controlledPage=page
            morph=MainIconMorph(endpoint(page),endpoint(SoSymbol.CONTENTS))
        }
        progress=value.coerceIn(0f,1f)
    }
}
