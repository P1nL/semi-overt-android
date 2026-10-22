package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Immutable
internal data class CircleColors(val background: Color,val foreground: Color) {
    fun towards(target: CircleColors,progress: Float): CircleColors = when {
        progress<=0f -> this
        progress>=1f -> target
        else -> CircleColors(lerp(background,target.background,progress),lerp(foreground,target.foreground,progress))
    }
}

/** The visible color survives route replacement, just like the main circle's inner glyph. */
@Stable
internal class MainCircleColorState(private val scope: CoroutineScope) {
    var colors by mutableStateOf<CircleColors?>(null);private set
    private var requested: CircleColors?=null
    private var mergeOwner: Any?=null
    private var mergeFrom: CircleColors?=null
    private var job: Job?=null

    fun showPage(ownerKey: Any,target: CircleColors,motion: Boolean) {
        // The departing route must not overwrite a color already committed by its radial menu.
        if(mergeOwner===ownerKey&&requested!=target)return
        mergeOwner=null;mergeFrom=null
        if(requested==target&&colors!=null) {
            if(!motion){job?.cancel();colors=target}
            return
        }
        val from=colors?:target
        job?.cancel();requested=target
        if(!motion||from==target){colors=target;return}
        job=scope.launch {
            animate(0f,1f,animationSpec=tween(SceneMotion.MAIN_ICON_MORPH_MS,easing=SoSceneEase)) {value,_->
                colors=from.towards(target,value)
            }
            colors=target
        }
    }

    fun beginMerge(ownerKey: Any,target: CircleColors) {
        job?.cancel()
        mergeOwner=ownerKey;mergeFrom=colors?:target;requested=target
    }

    /** Drive this from the selected small circle's actual return, not a second timer. */
    fun merge(progress: Float) {
        val from=mergeFrom?:return
        val target=requested?:return
        colors=from.towards(target,progress.coerceIn(0f,1f))
    }
}
