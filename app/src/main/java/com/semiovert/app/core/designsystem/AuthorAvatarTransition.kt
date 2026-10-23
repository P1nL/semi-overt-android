package com.semiovert.app.core.designsystem

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing

data class AuthorAvatarHandoff(val authorId: String,val sourceEntry: String,val targetEntry: String,val durationMillis: Int,
    val changesIdentity: Boolean=false,val sourceIsUser: Boolean=false) {
    fun contains(entry: String)=entry==sourceEntry||entry==targetEntry
    fun isUser(entry: String)=changesIdentity&&entry==if(sourceIsUser)sourceEntry else targetEntry
}
private val LocalAuthorSharedScope=staticCompositionLocalOf<SharedTransitionScope?> {null}
private val LocalAuthorVisibility=staticCompositionLocalOf<AnimatedVisibilityScope?> {null}
private val LocalAuthorHandoff=compositionLocalOf<AuthorAvatarHandoff?> {null}
private val LocalAuthorEntry=compositionLocalOf {""}
private val LocalAvatarPixels=staticCompositionLocalOf<MutableMap<String,AvatarPixelFrames>?> {null}
private val LocalAvatarMorph=compositionLocalOf {1f}

@Composable
private fun avatarPixelKey(authorId: String?): String {
    val identity=LocalIdentity.current
    val colors=MaterialTheme.colorScheme
    val person=authorId?:"user-${identity.signedIn}-${identity.avatar}"
    return "$person-${colors.secondary}-${colors.onSecondary}-${colors.background}"
}

/** Above the reader's opaque exit layer, separate from the user's own navigation identity. */
@Composable
fun AuthorAvatarTransitionHost(handoff: AuthorAvatarHandoff?,content: @Composable ()->Unit) {
    val pixels=remember {mutableStateMapOf<String,AvatarPixelFrames>()}
    val enabled=LocalMotionEnabled.current
    val morph=remember(handoff,enabled) {Animatable(if(handoff?.changesIdentity==true&&enabled)0f else 1f)}
    LaunchedEffect(morph) {
        if(handoff?.changesIdentity==true&&enabled)morph.animateTo(1f,tween(handoff.durationMillis,easing=LinearEasing))
    }
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAuthorSharedScope provides this,LocalAuthorHandoff provides handoff,
            LocalAvatarPixels provides pixels,LocalAvatarMorph provides morph.value,content=content)
    }
}

@Composable
fun AuthorAvatarTransitionScene(scope: AnimatedVisibilityScope,entryId: String,content: @Composable ()->Unit) {
    val handoff=LocalAuthorHandoff.current
    val active=handoff!=null&&(entryId==handoff.sourceEntry||entryId==handoff.targetEntry)
    val duration=if(active&&LocalMotionEnabled.current)handoff!!.durationMillis else 0
    // Keep both real endpoints mounted even when their route has no ordinary fade/slide.
    scope.transition.animateFloat(transitionSpec={tween(duration,easing=SoSceneEase)},label="author-avatar-lifetime") {
        if(it==EnterExitState.Visible)1f else 0f
    }
    CompositionLocalProvider(LocalAuthorVisibility provides scope,LocalAuthorEntry provides entryId,content=content)
}

@Composable
private fun authorHandoffRunning(): Boolean {
    val scope=LocalAuthorVisibility.current
    return LocalAvatarMorph.current<1f||scope?.transition?.let {
        it.currentState!=EnterExitState.Visible||it.targetState!=EnterExitState.Visible
    }==true
}

@Composable
internal fun authorAvatarInHandoff(authorId: String): Boolean {
    val handoff=LocalAuthorHandoff.current?:return false
    val entry=LocalAuthorEntry.current
    return handoff.authorId==authorId&&handoff.contains(entry)&&!handoff.isUser(entry)&&authorHandoffRunning()
}

@Composable
internal fun userAvatarInAuthorHandoff(): Boolean=LocalAuthorHandoff.current?.isUser(LocalAuthorEntry.current)==true&&authorHandoffRunning()

@Composable
internal fun Modifier.authorAvatarSharedElement(authorId: String?): Modifier {
    val host=LocalAuthorSharedScope.current
    val visibility=LocalAuthorVisibility.current
    val handoff=LocalAuthorHandoff.current
    val active=if(authorId==null)userAvatarInAuthorHandoff() else authorAvatarInHandoff(authorId)
    val duration=if(active&&LocalMotionEnabled.current)handoff!!.durationMillis else 0
    return if(host!=null&&visibility!=null)with(host) {
        val key=if(active&&handoff?.changesIdentity==true)"author-user-morph" else "author-avatar-$authorId"
        val state=rememberSharedContentState(key)
        this@authorAvatarSharedElement.sharedBounds(state,visibility,enter=EnterTransition.None,exit=ExitTransition.None,
            boundsTransform={_,_->tween(duration,easing=SoSceneEase)},
            resizeMode=SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds))
            .drawWithContent {
                if(!state.isMatchFound||visibility.transition.targetState==EnterExitState.Visible)drawContent()
            }
    } else this
}

/** Capture the real artwork once and reuse the opening animation's cached pixel levels. */
@Composable
internal fun Modifier.avatarIdentityPixels(authorId: String?): Modifier {
    val cache=LocalAvatarPixels.current?:return this
    val key=avatarPixelKey(authorId)
    val handoff=LocalAuthorHandoff.current
    val userKey=avatarPixelKey(null)
    val authorKey=avatarPixelKey(handoff?.authorId)
    val morph=LocalAvatarMorph.current
    val enabled=LocalMotionEnabled.current
    val active=handoff?.changesIdentity==true&&enabled&&
        (if(authorId==null)userAvatarInAuthorHandoff() else authorAvatarInHandoff(authorId))
    val layer=rememberGraphicsLayer()
    var recorded by remember(key) {mutableStateOf(false)}
    LaunchedEffect(recorded,key) {
        if(recorded&&cache[key]==null) {
            val image=layer.toImageBitmap()
            cache[key]=withContext(Dispatchers.Default) {AvatarPixelFrames(image)}
        }
    }
    return drawWithContent {
        layer.record {this@drawWithContent.drawContent()}
        if(!recorded)recorded=true
        val source=cache[if(handoff?.sourceIsUser==true)userKey else authorKey]
        val target=cache[if(handoff?.sourceIsUser==true)authorKey else userKey]
        if(active&&morph<1f&&source!=null) {
            val mix=if(target!=null)AvatarPixelMotion.identityMix(morph) else 0f
            val bounds=Rect(Offset.Zero,size)
            clipPath(Path().apply {addOval(bounds)}) {
                fun paint(frames: AvatarPixelFrames,clarity: Float,alpha: Float) {
                    if(alpha>0f)drawImage(frames.frame(clarity),dstOffset=IntOffset.Zero,
                        dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),alpha=alpha,
                        filterQuality=if(clarity>=1f)FilterQuality.High else FilterQuality.None)
                }
                paint(source,AvatarPixelMotion.sourceClarity(morph),1f-mix)
                if(target!=null)paint(target,AvatarPixelMotion.targetClarity(morph),mix)
            }
        } else drawLayer(layer)
    }
}

@Composable
fun AuthorAvatar(authorId: String,modifier: Modifier=Modifier,size: Int=48,shared: Boolean=true) {
    val transition=if(shared)Modifier.authorAvatarSharedElement(authorId) else Modifier
    val pixels=if(shared)Modifier.avatarIdentityPixels(authorId) else Modifier
    Box(modifier.then(transition).size(size.dp).then(pixels).testTag("author-avatar-$authorId")) {ProfileAvatar(size=size)}
}
