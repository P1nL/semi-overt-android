package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.compose.material3.MaterialTheme
import kotlin.math.*

@Stable
class IdentityAnchor(val signedIn: Boolean,val avatar: Int) {
    var bounds by mutableStateOf(Rect.Zero)
    var image by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
}
private val LocalIdentity=staticCompositionLocalOf {IdentityAnchor(false,0)}
val LocalIntroReady=compositionLocalOf {true}

private fun DrawScope.drawIdentityFrame(atlases: List<ImageBitmap>,frame: Int,rect: Rect,tint: Color,opacity: Float=1f,mode: BlendMode=BlendMode.SrcOver) {
    val local=frame%30
    drawImage(atlases[frame/30],srcOffset=IntOffset(local%6*480,local/6*480),srcSize=IntSize(480,480),
        dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(rect.width.roundToInt(),rect.height.roundToInt()),
        alpha=opacity,colorFilter=ColorFilter.tint(tint),filterQuality=FilterQuality.High,blendMode=mode)
}

@Composable
fun IdentityAvatar(modifier: Modifier=Modifier) {
    val anchor=LocalIdentity.current
    val context=LocalContext.current
    val still=remember {context.assets.open("identity/guest-still.png").use {BitmapFactory.decodeStream(it).asImageBitmap()}}
    val tint=MaterialTheme.colorScheme.onSecondary
    val background=MaterialTheme.colorScheme.secondary
    val layer=rememberGraphicsLayer()
    var recorded by remember {mutableStateOf(false)}
    LaunchedEffect(recorded,anchor.signedIn,anchor.avatar,tint) {
        if(recorded) {withFrameNanos { };anchor.image=layer.toImageBitmap()}
    }
    Box(modifier.size(40.dp).onGloballyPositioned {anchor.bounds=it.boundsInRoot()}.drawWithContent {
        layer.record {this@drawWithContent.drawContent()};if(!anchor.playing)drawLayer(layer)
        if(!recorded)recorded=true
    }) {
        if(anchor.signedIn)ProfileAvatar(anchor.avatar,40)
        else Canvas(Modifier.fillMaxSize().clip(CircleShape)) {drawCircle(background);drawImage(still,dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),colorFilter=ColorFilter.tint(tint))}
    }
}

@Composable
fun IdentityIntroHost(signedIn: Boolean,avatar: Int,content: @Composable ()->Unit) {
    val anchor=remember(signedIn,avatar) {IdentityAnchor(signedIn,avatar)}
    var played by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val context=LocalContext.current
    val background=MaterialTheme.colorScheme.background
    val ink=MaterialTheme.colorScheme.primary
    val avatarTint=MaterialTheme.colorScheme.onSecondary
    val avatarBackground=MaterialTheme.colorScheme.secondary
    val ring=remember {Animatable(0f)}
    val pixels=remember {Animatable(0f)}
    val playback=remember {Animatable(0f)}
    val morph=remember {Animatable(0f)}
    val framePaint=remember {Paint()}
    CompositionLocalProvider(LocalIdentity provides anchor,LocalIntroReady provides (played||!motion||morph.value>.15f)) {
        Box(Modifier.fillMaxSize()) {
            content()
            if(!played&&motion) {
                // Source: 90 original 60-fps frames, blended at the display cadence without respeeding.
                val atlases=remember {if(signedIn)emptyList() else (0..2).map {index ->context.assets.open("identity/guest-frames-$index.png").use {BitmapFactory.decodeStream(it).asImageBitmap()}}}
                var coarse by remember {mutableStateOf<List<ImageBitmap>>(emptyList())}
                LaunchedEffect(Unit) {
                    anchor.playing=true
                    ring.animateTo(1f,tween(500,easing=SoEase))
                    val source=anchor.image?.asAndroidBitmap()?.let {if(it.config==Bitmap.Config.HARDWARE)it.copy(Bitmap.Config.ARGB_8888,false) else it}
                    if(source!=null)coarse=listOf(8,12,20,32,64,128).map {Bitmap.createScaledBitmap(source,it,it,false).asImageBitmap()}
                    pixels.animateTo(1f,tween(560,easing=LinearEasing))
                    if(!signedIn)playback.animateTo(IdentityPlayback.FRAME_COUNT-.001f,tween(IdentityPlayback.DURATION_MILLIS,easing=LinearEasing))
                    morph.animateTo(1f,tween(580,easing=SoEase))
                    played=true;anchor.playing=false
                }
                Canvas(Modifier.fillMaxSize().testTag("identity-intro").pointerInput(Unit) {awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
                    val start=Offset(size.width*.5f,size.height*.42f)
                    val destination=anchor.bounds.takeUnless {it==Rect.Zero}?:Rect(size.width-72.dp.toPx(),52.dp.toPx(),size.width-32.dp.toPx(),92.dp.toPx())
                    val p=morph.value
                    val center=lerp(start,destination.center,p)
                    val diameter=lerp(116.dp.toPx(),destination.width,p)
                    if(p<.001f)drawRect(background)
                    else {
                        val revealRadius=hypot(size.width,size.height)*p
                        clipPath(Path().apply {addOval(Rect(destination.center,revealRadius))},ClipOp.Difference) {drawRect(background)}
                    }
                    val rect=Rect(center,diameter/2f)
                    if(ring.value<1f)drawArc(ink,-90f,360f*ring.value,false,rect.topLeft,rect.size,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    else {
                        drawCircle(avatarBackground,diameter/2f,center)
                        val clip=Path().apply {addOval(rect)}
                        clipPath(clip) {
                            if(pixels.value<1f) {
                                drawRect(background,rect.topLeft,rect.size)
                                val source=coarse.getOrNull((pixels.value*coarse.size).toInt().coerceAtMost(coarse.lastIndex.coerceAtLeast(0)))?:anchor.image
                                if(source!=null) {
                                    val tiles=Path()
                                    for(row in 0..7)for(column in 0..7)if(((row*8+column)*37%64)/64f<=pixels.value)tiles.addRect(Rect(rect.left+rect.width*column/8,rect.top+rect.height*row/8,rect.left+rect.width*(column+1)/8,rect.top+rect.height*(row+1)/8))
                                    clipPath(tiles) {drawImage(source,dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(diameter.roundToInt(),diameter.roundToInt()),filterQuality=FilterQuality.None)}
                                }
                            } else if(!signedIn&&atlases.isNotEmpty()) {
                                val sample=IdentityPlayback.sample(playback.value)
                                if(sample.mix==0f)drawIdentityFrame(atlases,sample.first,rect,avatarTint)
                                else {
                                    // Add premultiplied masks in an isolated layer, so blending does not dim the icon.
                                    drawContext.canvas.saveLayer(rect,framePaint)
                                    try {
                                        drawIdentityFrame(atlases,sample.first,rect,avatarTint,1f-sample.mix)
                                        drawIdentityFrame(atlases,sample.second,rect,avatarTint,sample.mix,BlendMode.Plus)
                                    } finally {drawContext.canvas.restore()}
                                }
                            } else anchor.image?.let {drawImage(it,dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(diameter.roundToInt(),diameter.roundToInt()),filterQuality=FilterQuality.High)}
                        }
                    }
                }
            } else {SideEffect {anchor.playing=false;played=true}}
        }
    }
}
