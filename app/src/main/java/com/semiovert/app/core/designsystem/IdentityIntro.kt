package com.semiovert.app.core.designsystem

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.*

@Stable
class IdentityAnchor(val signedIn: Boolean,val avatar: Int) {
    var bounds by mutableStateOf(Rect.Zero)
    var image by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
}
val LocalIdentity=staticCompositionLocalOf {IdentityAnchor(false,0)}
val LocalIntroReady=compositionLocalOf {true}

/** Only the cold-start home scene registers work here; ordinary routes keep their current behavior. */
@Stable
internal class IntroPreparation(val homeCovers: Deferred<Map<Int,ImageBitmap>>?=null) {
    var ready by mutableStateOf(true)
    var covers by mutableStateOf<Map<Int,ImageBitmap>>(emptyMap())
}
internal val LocalIntroPreparation=staticCompositionLocalOf<IntroPreparation?> {null}

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
    val shared=if(userAvatarInAuthorHandoff())Modifier.authorAvatarSharedElement(null) else Modifier.identitySharedElement()
    Box(modifier.then(shared).size(size.dp).onGloballyPositioned {anchor.bounds=it.boundsInRoot()}.drawWithContent {
        layer.record {this@drawWithContent.drawContent()};if(!anchor.playing)drawLayer(layer)
        if(!recorded)recorded=true
    }.avatarIdentityPixels(null)) {
        if(anchor.signedIn)ProfileAvatar(anchor.avatar,size)
        else Canvas(Modifier.fillMaxSize()) {guest.draw(this,Rect(Offset.Zero,this.size),tint)}
    }
}

@Composable
fun IdentityIntroHost(signedIn: Boolean,avatar: Int,canStart: Boolean=true,
    homeCovers: Deferred<Map<Int,ImageBitmap>>?=null,
    avatarSource: @Composable ()->Unit={IdentityAvatar()},content: @Composable ()->Unit) {
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
    val preparation=remember(homeCovers) {IntroPreparation(homeCovers)}
    var ringDrawn by remember {mutableStateOf(false)}
    var identityDrawn by remember {mutableStateOf(false)}
    var mountContent by remember {mutableStateOf(false)}
    var contentLaidOut by remember {mutableStateOf(false)}
    val bypass=played||!motion
    LaunchedEffect(identityDrawn) {
        if(identityDrawn) {
            // Show the completed identity before route construction can occupy the UI thread.
            withFrameNanos { }
            mountContent=true
        }
    }
    CompositionLocalProvider(LocalIdentity provides anchor,LocalIntroReady provides (bypass||morph.value>.15f),
        LocalIntroPreparation provides if(bypass)null else preparation,
        LocalPreparedArticleCovers provides preparation.covers) {
        Box(Modifier.fillMaxSize()) {
            if(bypass||mountContent)Box(Modifier.fillMaxSize().onGloballyPositioned {
                if(!contentLaidOut)contentLaidOut=true
            }) {content()}
            // Capture the same small avatar as the header without constructing navigation.
            else if(signedIn&&ringDrawn)avatarSource()
            if(!played&&motion) {
                var avatarFrames by remember {mutableStateOf<AvatarPixelFrames?>(null)}
                LaunchedEffect(canStart) {
                    anchor.playing=true
                    if(!canStart)return@LaunchedEffect
                    // Start the clock on a visible app frame, not beneath the system splash.
                    coroutineScope {
                        // Only the lightweight avatar source is needed for the pixel phase.
                        val preparedAvatar=if(signedIn)async {
                            val source=snapshotFlow {anchor.image}.filterNotNull().first()
                            withContext(Dispatchers.Default) {AvatarPixelFrames(source)}
                        } else null
                        withFrameNanos { }
                        ring.animateTo(1f,tween(500,easing=SoEase))
                        if(signedIn) {
                            avatarFrames=preparedAvatar!!.await()
                            pixels.animateTo(1f,tween(AvatarPixelMotion.DURATION_MILLIS,easing=LinearEasing))
                        } else {
                            rise.animateTo(1f,tween(GuestIdentityMotion.RISE_MILLIS,easing=SoSceneEase))
                            bounce.animateTo(1f,tween(GuestIdentityMotion.BOUNCE_MILLIS,easing=LinearEasing))
                        }
                        // Navigation must not delay the icon, but the scene must still be ready
                        // before its background is revealed and the real avatar anchor is used.
                        snapshotFlow {contentLaidOut&&preparation.ready}.first {it}
                        morph.animateTo(1f,tween(580,easing=SoEase))
                    }
                    played=true;anchor.playing=false
                }
                Canvas(Modifier.fillMaxSize().testTag("identity-intro").pointerInput(Unit) {awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
                    val start=Offset(size.width*.5f,size.height*.42f)
                    val destination=anchor.bounds.takeUnless {it==Rect.Zero}?:Rect(size.width-72.dp.toPx(),52.dp.toPx(),size.width-32.dp.toPx(),92.dp.toPx())
                    val p=morph.value
                    val center=lerp(start,destination.center,p)
                    val diameter=lerp(AvatarPixelMotion.OPENING_SIZE.dp.toPx(),destination.width,p)
                    if(p<.001f)drawRect(background)
                    else {
                        val revealRadius=hypot(size.width,size.height)*p
                        clipPath(Path().apply {addOval(Rect(destination.center,revealRadius))},ClipOp.Difference) {drawRect(background)}
                    }
                    val rect=Rect(center,diameter/2f)
                    if(!signedIn)guest.draw(this,rect,guestInk,ring.value,rise.value,bounce.value)
                    else if(ring.value<1f)drawArc(ink,-90f,360f*ring.value,false,rect.topLeft,rect.size,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    else {
                        val frames=avatarFrames
                        if(frames!=null) {
                            drawCircle(avatarBackground,diameter/2f,center)
                            clipPath(Path().apply {addOval(rect)}) {
                                drawImage(frames.frame(pixels.value),
                                    dstOffset=IntOffset(rect.left.roundToInt(),rect.top.roundToInt()),
                                    dstSize=IntSize(diameter.roundToInt(),diameter.roundToInt()),
                                    alpha=AvatarPixelMotion.imageAlpha(pixels.value),
                                    filterQuality=if(pixels.value>=1f)FilterQuality.High else FilterQuality.None)
                            }
                        }
                        val outline=if(frames==null)1f else AvatarPixelMotion.outlineAlpha(pixels.value)
                        if(outline>0f)drawCircle(ink,diameter/2f,center,alpha=outline,style=Stroke(2.dp.toPx()))
                    }
                    if(!ringDrawn&&ring.value>=1f)ringDrawn=true
                    if(!identityDrawn&&(if(signedIn)pixels.value else bounce.value)>=1f)identityDrawn=true
                }
            } else {SideEffect {anchor.playing=false;played=true}}
        }
    }
}
