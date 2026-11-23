package com.semiovert.app.core.designsystem

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.semiovert.app.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal const val GUEST_GHOST_DRAW_FRACTION=.64f
internal const val HOME_GHOST_DRAW_FRACTION=.8f
internal val LocalGuestGhost=staticCompositionLocalOf<GuestGhostPlayback?> {null}

internal fun guestIdentityInk(background: Color)=if(background.luminance()>.5f)Color.Black else Color.White

@Stable
internal class GuestGhostPlayback {
    var loaded by mutableStateOf(false)
        private set
    private var animation by mutableStateOf<IntroGhost?>(null)
    private var playTime by mutableLongStateOf(0L)
    private var avatars by mutableIntStateOf(0)

    suspend fun load(context: Context,ink: Color) {
        animation=IntroGhost.load(context,ink)
        loaded=true
    }

    fun attachAvatar() {avatars++}
    fun detachAvatar() {avatars--}

    suspend fun play() {
        val duration=animation?.durationNanos?:return
        var previous=withFrameNanos {it}
        while(true)withFrameNanos {time ->
            playTime=(playTime+time-previous)%duration
            previous=time
        }
    }

    suspend fun playOnPage(lifecycle: Lifecycle) {
        val frameClock=checkNotNull(AndroidUiDispatcher.CurrentThread[MonotonicFrameClock])
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {avatars>0&&animation!=null}.collectLatest {visible ->
                if(visible)withContext(frameClock) {play()}
            }
        }
    }

    fun draw(scope: DrawScope,bounds: Rect,ink: Color): Boolean {
        val asset=animation?:return false
        asset.draw(scope,bounds,ink,playTime.toFloat()/asset.durationNanos)
        return true
    }
}

@Composable
internal fun rememberGuestGhost(prepare: Boolean,playOnPage: Boolean,ink: Color): GuestGhostPlayback {
    val ghost=remember {GuestGhostPlayback()}
    val context=LocalContext.current.applicationContext
    LaunchedEffect(prepare) {
        if(prepare&&!ghost.loaded)ghost.load(context,ink)
    }
    if(playOnPage) {
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(ghost,lifecycle) {ghost.playOnPage(lifecycle)}
    }
    return ghost
}

@Composable
internal fun GuestAvatar(size: Int,modifier: Modifier=Modifier,artworkFraction: Float=GUEST_GHOST_DRAW_FRACTION,
    artworkModifier: Modifier=Modifier,supersample: Boolean=false) {
    val painter=painterResource(R.drawable.ic_guest_ghost)
    val ink=guestIdentityInk(MaterialTheme.colorScheme.background)
    val tint=remember(ink) {ColorFilter.tint(ink)}
    val ghost=LocalGuestGhost.current.takeIf {LocalMotionEnabled.current}
    if(ghost!=null)DisposableEffect(ghost) {
        ghost.attachAvatar()
        onDispose {ghost.detachAvatar()}
    }
    val samplingLayer=if(supersample)rememberGraphicsLayer().also {
        it.compositingStrategy=CompositingStrategy.Offscreen
        it.pivotOffset=Offset.Zero
        it.scaleX=.5f
        it.scaleY=.5f
    } else null
    val drawArtwork: DrawScope.()->Unit={drawGuestGhost(ghost,painter,Rect(Offset.Zero,this.size),ink,tint)}
    Box(modifier.size(size.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize(artworkFraction).then(artworkModifier).graphicsLayer()) {
            if(samplingLayer==null)drawArtwork()
            else {
                samplingLayer.record(size=this.size.toIntSize()*2,block=drawArtwork)
                drawLayer(samplingLayer)
            }
        }
    }
}

internal fun DrawScope.drawGuestGhost(ghost: GuestGhostPlayback?,painter: Painter,bounds: Rect,ink: Color,tint: ColorFilter) {
    if(ghost?.draw(this,bounds,ink)==true)return
    translate(bounds.left,bounds.top) {
        with(painter) {draw(bounds.size,colorFilter=tint)}
    }
}
