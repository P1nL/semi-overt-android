package com.semiovert.app.core.designsystem.arc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.ProfileAvatar
import com.semiovert.app.core.model.Author
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** A transparent rail behind the cards, not a fixed circular image-switching viewport. */
@Composable
internal fun ArcAuthorAvatar(author: Author,focusedArticleId: String,position: Float,dragging: Boolean,settling: Boolean,enabled: Boolean,
    geometry: ArcDeckGeometry,modifier: Modifier=Modifier) {
    val latestPosition by rememberUpdatedState(position)
    val latestArticleId by rememberUpdatedState(focusedArticleId)
    val latestSettling by rememberUpdatedState(settling)
    var ready by remember {mutableStateOf(false)}
    LaunchedEffect(enabled,dragging) {
        ready=false
        if(!enabled||dragging)return@LaunchedEffect
        val gate=ArcAvatarRestGate()
        while(isActive) {
            val stable=withFrameNanos {gate.sample(latestPosition,latestArticleId,latestSettling,it/1_000_000L)}
            if(stable) {
                ready=true
                break
            }
        }
    }
    ArcAuthorAvatarRail(author,enabled&&!dragging&&ready,geometry,modifier)
}

@Composable
private fun ArcAuthorAvatarRail(author: Author,visible: Boolean,geometry: ArcDeckGeometry,modifier: Modifier=Modifier) {
    val latestAuthor by rememberUpdatedState(author)
    var shown by remember {mutableStateOf(author)}
    val hidden=remember {Animatable(if(visible)0f else 1f)}
    // Author changes during a drag do not restart the exit or mount intermediate images.
    LaunchedEffect(visible,author.id.takeIf {visible}) {
        if(!visible)hidden.animateTo(1f,tween(ArcAuthorAvatarMotion.HIDE_MILLIS,easing=FastOutSlowInEasing))
        else {
            if(shown.id!=latestAuthor.id&&hidden.value<1f) {
                hidden.animateTo(1f,tween(ArcAuthorAvatarMotion.HIDE_MILLIS,easing=FastOutSlowInEasing))
            }
            shown=latestAuthor // Swap only offscreen; the previous author must not re-enter.
            hidden.animateTo(0f,tween(ArcAuthorAvatarMotion.ENTER_MILLIS,easing=FastOutSlowInEasing))
        }
    }
    val p=ArcAuthorAvatarMotion.placement(geometry)
    val density=LocalDensity.current.density
    Box(
        modifier=modifier.absoluteOffset(p.left.dp,p.top.dp).requiredSize(p.diameter.dp)
            .graphicsLayer {translationX=hidden.value*p.hiddenOffset*density}.testTag("home-author-rail").semantics {
                stateDescription=if(visible)"已显示" else "已隐藏"
            },
        contentAlignment=Alignment.Center) {
        Box(Modifier.requiredSize(p.diameter.dp).testTag("home-browse-author-"+shown.id).clearAndSetSemantics {
            if(visible&&shown.id==author.id)contentDescription=shown.name+"的头像"
        },contentAlignment=Alignment.Center) {
            ProfileAvatar(size=p.diameter.roundToInt(),
                backgroundColor=MaterialTheme.colorScheme.background,
                contentColor=MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Keep the chosen upper/lower slot through exit, even after the card starts returning. */
@Composable
internal fun ArcExpandedAuthorAvatar(author: Author,placement: ArcAuthorAvatarPlacement?,visible: Boolean,
    tag: String,modifier: Modifier=Modifier) {
    var lastPlacement by remember {mutableStateOf<ArcAuthorAvatarPlacement?>(null)}
    val p=placement?:lastPlacement?:return
    SideEffect {if(placement!=null)lastPlacement=placement}
    val density=LocalDensity.current.density
    val offscreen=(p.hiddenOffset*density).roundToInt()
    AnimatedVisibility(visible=visible&&placement!=null,
        modifier=modifier.absoluteOffset(p.left.dp,p.top.dp),
        enter=slideInHorizontally(tween(ArcAuthorAvatarMotion.EXPANDED_ENTER_MILLIS,easing=FastOutSlowInEasing)) {offscreen},
        exit=slideOutHorizontally(tween(ArcAuthorAvatarMotion.HIDE_MILLIS,easing=FastOutSlowInEasing)) {offscreen},
        label="expanded-author-handoff") {
        Box(Modifier.requiredSize(p.diameter.dp).testTag(tag).clearAndSetSemantics {
            if(visible)contentDescription=author.name+"的头像"
        },contentAlignment=Alignment.Center) {
            ProfileAvatar(size=p.diameter.roundToInt(),backgroundColor=MaterialTheme.colorScheme.background,
                contentColor=MaterialTheme.colorScheme.onBackground)
        }
    }
}
