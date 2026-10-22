package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
val LocalIdentity=staticCompositionLocalOf {IdentityAnchor(false,0)}
val LocalIntroReady=compositionLocalOf {true}

@Composable
fun IdentityAvatar(modifier: Modifier=Modifier,size: Int=40,registerAnchor: Boolean=true) {
    val anchor=LocalIdentity.current
    val guest=remember {GuestIdentityPainter()}
    val tint=guestIdentityInk(MaterialTheme.colorScheme.background)
    if(!registerAnchor) {
        Box(modifier.size(size.dp)) {
            if(anchor.signedIn)ProfileAvatar(anchor.avatar,size)
            else Canvas(Modifier.fillMaxSize()){guest.draw(this,Rect(Offset.Zero,this.size),tint)}
        }
        return
    }
    val layer=rememberGraphicsLayer()
    var recorded by remember {mutableStateOf(false)}
    LaunchedEffect(recorded,anchor.signedIn,anchor.avatar,tint) {
        if(recorded) {withFrameNanos { };anchor.image=layer.toImageBitmap()}
    }
    Box(modifier.identitySharedElement().size(size.dp).onGloballyPositioned {anchor.bounds=it.boundsInRoot()}.drawWithContent {
        layer.record {this@drawWithContent.drawContent()};if(!anchor.playing)drawLayer(layer)
        if(!recorded)recorded=true
    }) {
        if(anchor.signedIn)ProfileAvatar(anchor.avatar,size)
        else Canvas(Modifier.fillMaxSize()) {guest.draw(this,Rect(Offset.Zero,this.size),tint)}
    }
}

@Composable
fun IdentityIntroHost(signedIn: Boolean,avatar: Int,canStart: Boolean=true,content: @Composable ()->Unit) {
    val anchor=remember(signedIn,avatar) {IdentityAnchor(signedIn,avatar)}
    var played by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val background=MaterialTheme.colorScheme.background
    val ink=MaterialTheme.colorScheme.primary
    val avatarBackground=MaterialTheme.colorScheme.secondary
    val guestInk=guestIdentityInk(background)
    val guest=remember {GuestIdentityPainter()}
    val ring=remember {Animatable(0f)}
    val pixels=remember {Animatable(0f)}
    val rise=remember {Animatable(0f)}
    val bounce=remember {Animatable(0f)}
    val morph=remember {Animatable(0f)}
    CompositionLocalProvider(LocalIdentity provides anchor,LocalIntroReady provides (played||!motion||morph.value>.15f)) {
        Box(Modifier.fillMaxSize()) {
            content()
            if(!played&&motion) {
                var coarse by remember {mutableStateOf<List<ImageBitmap>>(emptyList())}
                LaunchedEffect(canStart) {
                    anchor.playing=true
                    if(!canStart)return@LaunchedEffect
                    // Start the clock on a visible app frame, not beneath the system splash.
                    withFrameNanos { }
                    ring.animateTo(1f,tween(500,easing=SoEase))
                    if(signedIn) {
                        val source=anchor.image?.asAndroidBitmap()?.let {if(it.config==Bitmap.Config.HARDWARE)it.copy(Bitmap.Config.ARGB_8888,false) else it}
                        if(source!=null)coarse=listOf(8,12,20,32,64,128).map {Bitmap.createScaledBitmap(source,it,it,false).asImageBitmap()}
                        pixels.animateTo(1f,tween(560,easing=LinearEasing))
                    } else {
                        rise.animateTo(1f,tween(GuestIdentityMotion.RISE_MILLIS,easing=SoSceneEase))
                        bounce.animateTo(1f,tween(GuestIdentityMotion.BOUNCE_MILLIS,easing=LinearEasing))
                    }
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
                    if(!signedIn)guest.draw(this,rect,guestInk,ring.value,rise.value,bounce.value)
                    else if(ring.value<1f)drawArc(ink,-90f,360f*ring.value,false,rect.topLeft,rect.size,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    else {
                        drawCircle(avatarBackground,diameter/2f,center)
                        val clip=Path().apply {addOval(rect)}
                        clipPath(clip) {
                            if(pixels.value<1f) {
                                drawRect(background,rect.topLeft,rect.size)
                                val source=coarse.getOrNull(PixelReveal.sample(pixels.value,coarse.size))?:anchor.image
                                if(source!=null) {
                                    val tiles=Path()
                                    for(row in 0..7)for(column in 0..7)if(PixelReveal.tileVisible(row*8+column,pixels.value))tiles.addRect(Rect(rect.left+rect.width*column/8,rect.top+rect.height*row/8,rect.left+rect.width*(column+1)/8,rect.top+rect.height*(row+1)/8))
                                    clipPath(tiles) {drawImage(source,dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(diameter.roundToInt(),diameter.roundToInt()),filterQuality=FilterQuality.None)}
                                }
                            } else anchor.image?.let {drawImage(it,dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),dstSize=IntSize(diameter.roundToInt(),diameter.roundToInt()),filterQuality=FilterQuality.High)}
                        }
                    }
                }
            } else {SideEffect {anchor.playing=false;played=true}}
        }
    }
}
