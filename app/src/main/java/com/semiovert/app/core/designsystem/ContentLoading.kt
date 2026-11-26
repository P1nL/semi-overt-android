package com.semiovert.app.core.designsystem

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.roundToInt

internal const val CONTENT_LOADING_ASSET="motion/dashed-circle-loading.json"
internal const val CONTENT_LOADING_MARKER="default:hover-explode"
internal const val CONTENT_LOADING_TAG="content-loading"

@Stable
internal class LoadingPlayback {
    private val firstCycle=Animatable(0f)
    var artwork by mutableStateOf<LottieArtwork?>(null)
        private set
    var firstCycleFinished by mutableStateOf(false)
        private set
    val firstCycleProgress get()=firstCycle.value

    suspend fun prepare(context: Context,ink: Color) {
        if(artwork!=null)return
        artwork=LottieArtwork.load(context,CONTENT_LOADING_ASSET,ink,CONTENT_LOADING_MARKER)
        if(artwork==null)firstCycleFinished=true
    }

    suspend fun playFirstCycle(durationMillis: Int) {
        if(firstCycleFinished)return
        val remaining=(durationMillis*(1f-firstCycle.value)).roundToInt()
        firstCycle.animateTo(1f,tween(remaining,easing=LinearEasing))
        firstCycleFinished=true
    }
}

@Composable
internal fun ContentLoading(visible: Boolean,message: String,modifier: Modifier=Modifier,
    playback: LoadingPlayback?=null,initiallyVisible: Boolean=false) {
    val visibility=remember(playback) {MutableTransitionState(initiallyVisible)}
    visibility.targetState=visible
    val fade=motionMillis(180)
    AnimatedVisibility(visibility,modifier,enter=fadeIn(tween(fade)),exit=fadeOut(tween(fade))) {
        val state=playback ?: remember {LoadingPlayback()}
        val ink=MaterialTheme.colorScheme.onSurfaceVariant
        val context=LocalContext.current.applicationContext
        LaunchedEffect(state,context) {state.prepare(context,ink)}
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val animation=state.artwork
        val moving=LocalMotionEnabled.current&&lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val cycleMillis=animation?.let {(it.durationNanos/1_000_000L).toInt()} ?: 0
        if(playback!=null&&animation!=null&&moving&&visible) {
            LaunchedEffect(playback,animation) {playback.playFirstCycle(cycleMillis)}
        }
        var repeating by remember(state) {mutableStateOf(playback==null)}
        if(visible&&state.firstCycleFinished&&!repeating)SideEffect {repeating=true}
        val loop=if(animation!=null&&moving&&repeating) {
            rememberInfiniteTransition(label="content-loading").animateFloat(0f,1f,
                infiniteRepeatable(tween(cycleMillis,easing=LinearEasing)),label="content-loading-progress")
        } else null
        Box(Modifier.height(64.dp).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(40.dp).testTag(CONTENT_LOADING_TAG).semantics {
                if(visible) {
                    contentDescription=message
                    progressBarRangeInfo=ProgressBarRangeInfo.Indeterminate
                }
            }) {
                if(animation!=null&&moving) {
                    animation.draw(this,Rect(Offset.Zero,size),ink,loop?.value ?: state.firstCycleProgress)
                } else if(animation!=null) {
                    animation.draw(this,Rect(Offset.Zero,size),ink,.5f)
                } else {
                    for(segment in 0 until 8)drawArc(ink,segment*45f,25f,false,
                        topLeft=Offset(size.width*.26f,size.height*.26f),size=size*.48f,
                        style=Stroke(size.width*.045f,cap=StrokeCap.Round))
                }
            }
        }
    }
}
