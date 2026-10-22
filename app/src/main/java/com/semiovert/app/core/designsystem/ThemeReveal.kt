package com.semiovert.app.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

internal const val THEME_REVEAL_MS=760

@Stable
internal class ThemeRevealState(private val scope: CoroutineScope,private val layer: GraphicsLayer) {
    var running by mutableStateOf(false);private set
    var previous by mutableStateOf<ImageBitmap?>(null);private set
    var origin by mutableStateOf(Offset.Zero);private set
    val progress=Animatable(1f)
    var motion=true

    fun switchAt(center: Offset,commit: ()->Unit) {
        if(running)return
        if(!motion){commit();return}
        running=true
        scope.launch {
            try {
                val old=layer.toImageBitmap()
                progress.snapTo(0f);origin=center;previous=old
                commit()
                // The complete target palette is painted beneath the old frame before opening the mask.
                withFrameNanos {};withFrameNanos {}
                progress.animateTo(1f,tween(THEME_REVEAL_MS,easing=CubicBezierEasing(.4f,0f,.2f,1f)))
            } finally {previous=null;running=false}
        }
    }
}

internal val LocalThemeReveal=staticCompositionLocalOf<ThemeRevealState?> {null}

/** One old-frame capture, not two independently composed apps or a per-frame screenshot. */
@Composable
fun ThemeRevealHost(content: @Composable ()->Unit) {
    val layer=rememberGraphicsLayer()
    val scope=rememberCoroutineScope()
    val reveal=remember(scope,layer) {ThemeRevealState(scope,layer)}
    val motion=LocalMotionEnabled.current
    SideEffect {reveal.motion=motion}
    BackHandler(reveal.running) {}
    CompositionLocalProvider(LocalThemeReveal provides reveal) {
        Box(Modifier.fillMaxSize().testTag("theme-reveal-host").semantics {
            stateDescription=if(reveal.running)"主题扩散中" else "主题已就位"
        }.pointerInput(reveal.running) {
            if(reveal.running)awaitPointerEventScope {while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
        }.drawWithContent {
            layer.record {this@drawWithContent.drawContent()}
            drawLayer(layer)
            reveal.previous?.let {old ->
                val center=reveal.origin
                val radius=(hypot(maxOf(center.x,size.width-center.x),maxOf(center.y,size.height-center.y))+2f)*reveal.progress.value
                val opening=Path().apply {addOval(Rect(center,radius))}
                clipPath(opening,ClipOp.Difference) {drawImage(old)}
            }
        }) {content()}
    }
}
