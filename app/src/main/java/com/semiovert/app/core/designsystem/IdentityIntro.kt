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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Stable
class IdentityAnchor(val signedIn: Boolean,val avatar: Int,val photo: ImageBitmap?=null,val photoKey: String?=null,
    val photoLoaded: Boolean=true) {
    var bounds by mutableStateOf(Rect.Zero)
    var image by mutableStateOf<ImageBitmap?>(null)
    var playing by mutableStateOf(false)
}
val LocalIdentity=staticCompositionLocalOf {IdentityAnchor(false,0)}
val LocalIntroReady=compositionLocalOf {true}

/** Only the cold-start home scene registers work here; ordinary routes keep their current behavior. */
@Stable
internal class IntroPreparation {
    var ready by mutableStateOf(true)
    var backgroundReady by mutableStateOf(true)
}
internal val LocalIntroPreparation=staticCompositionLocalOf<IntroPreparation?> {null}

@Composable
fun IdentityAvatar(modifier: Modifier=Modifier,size: Int=40,guestArtworkFraction: Float=GUEST_GHOST_DRAW_FRACTION,
    supersampleGuestArtwork: Boolean=false) {
    val anchor=LocalIdentity.current
    val shared=if(userAvatarInAuthorHandoff())Modifier.authorAvatarSharedElement(null) else Modifier.identitySharedElement()
    if(!anchor.signedIn) {
        GuestAvatar(size,modifier,guestArtworkFraction,artworkModifier=shared
            .onGloballyPositioned {anchor.bounds=it.boundsInRoot()}
            .drawWithContent {if(!anchor.playing)drawContent()}
            .avatarIdentityPixels(null),supersample=supersampleGuestArtwork)
        return
    }
    val layer=rememberGraphicsLayer()
    var recorded by remember(layer) {mutableStateOf(false)}
    val background=MaterialTheme.colorScheme.background
    LaunchedEffect(recorded,anchor,background) {
        if(recorded) {withFrameNanos { };anchor.image=layer.toImageBitmap()}
    }
    Box(modifier.then(shared).size(size.dp).onGloballyPositioned {anchor.bounds=it.boundsInRoot()}.drawWithContent {
        layer.record {this@drawWithContent.drawContent()}
        if(!anchor.playing)drawLayer(layer)
        if(!recorded)recorded=true
    }.avatarIdentityPixels(null)) {
        ResolvingProfileAvatar(anchor.avatar,size,anchor.photo,anchor.photoKey,anchor.photoLoaded)
    }
}

@Composable
fun IdentityIntroHost(signedIn: Boolean,avatar: Int,
    photo: ImageBitmap?=null,photoKey: String?=null,photoLoaded: Boolean=true,
    content: @Composable ()->Unit) {
    val anchor=remember(signedIn,avatar,photo,photoKey,photoLoaded) {IdentityAnchor(signedIn,avatar,photo,photoKey,photoLoaded)}
    var played by rememberSaveable {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    val background=MaterialTheme.colorScheme.background
    val ink=guestIdentityInk(background)
    val elapsed=remember {Animatable(0f)}
    val revealed by remember {derivedStateOf {elapsed.value>=BrandIntroMotion.PORTAL_START_MILLIS}}
    val preparation=remember {IntroPreparation()}
    var introDrawn by remember {mutableStateOf(false)}
    val bypass=played||!motion
    val ghost=rememberGuestGhost(prepare=!signedIn&&motion,playOnPage=(bypass||revealed)&&motion,ink=ink)
    SideEffect {
        anchor.playing=!bypass&&!revealed
        if(!motion)played=true
    }
    CompositionLocalProvider(LocalIdentity provides anchor,LocalIntroReady provides (bypass||revealed),
        LocalIntroPreparation provides if(bypass)null else preparation,LocalGuestGhost provides ghost) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val scale=if(bypass)1f else BrandIntroMotion.homeScale(elapsed.value)
                scaleX=scale
                scaleY=scale
                transformOrigin=TransformOrigin(.5f,BrandIntroMotion.CENTER_Y_FRACTION)
            }) {content()}
            if(!bypass) {
                val artwork=remember {BrandIntroArtwork()}
                LaunchedEffect(Unit) {
                    snapshotFlow {introDrawn}.first {it}
                    elapsed.animateTo(BrandIntroMotion.PORTAL_START_MILLIS.toFloat(),
                        tween(BrandIntroMotion.PORTAL_START_MILLIS,easing=LinearEasing))
                    snapshotFlow {preparation.ready}.first {it}
                    withTimeoutOrNull(BrandIntroMotion.MAX_BACKGROUND_READY_WAIT_MILLIS) {
                        snapshotFlow {preparation.backgroundReady}.first {it}
                    }
                    elapsed.animateTo(BrandIntroMotion.TOTAL_MILLIS.toFloat(),
                        tween(BrandIntroMotion.PORTAL_MILLIS,easing=LinearEasing))
                    played=true
                }
                Canvas(Modifier.fillMaxSize().testTag("identity-intro")
                    .semantics {contentDescription="正在打开 Semi-Overt"}
                    .pointerInput(Unit) {awaitPointerEventScope {while(true)awaitPointerEvent().changes.forEach {it.consume()}}}) {
                    artwork.draw(this,elapsed.value,background,ink)
                    if(!introDrawn)introDrawn=true
                }
            }
        }
    }
}
