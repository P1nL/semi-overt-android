package com.semiovert.app.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale

private val LocalIdentitySharedScope=staticCompositionLocalOf<SharedTransitionScope?> {null}
private val LocalIdentityVisibilityScope=staticCompositionLocalOf<AnimatedVisibilityScope?> {null}
private val LocalIdentityTransitionMillis=compositionLocalOf {0}
internal val LocalIdentityHandoffActive=compositionLocalOf {false}

/** One shared avatar above the fading routes, below the independent reading overlay. */
@Composable
fun IdentityTransitionHost(content: @Composable ()->Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalIdentitySharedScope provides this,content=content)
    }
}

@Composable
internal fun IdentityTransitionScene(
    scope: AnimatedVisibilityScope,durationMillis: Int,active: Boolean,content: @Composable ()->Unit,
) {
    CompositionLocalProvider(
        LocalIdentityVisibilityScope provides scope,
        LocalIdentityTransitionMillis provides durationMillis,
        LocalIdentityHandoffActive provides active,
        content=content,
    )
}

/** Navigation supplies both real endpoints; no global last-writer bounds or reversed old origin. */
@Composable
internal fun Modifier.identitySharedElement(): Modifier {
    val shared=LocalIdentitySharedScope.current ?: return this
    val visibility=LocalIdentityVisibilityScope.current ?: return this
    val duration=if(LocalMotionEnabled.current)LocalIdentityTransitionMillis.current else 0
    return with(shared) {
        val state=rememberSharedContentState("navigation-identity")
        // Scale the whole, stable avatar drawing. Remeasuring into the incoming small
        // header can otherwise display its final 40dp contents before the bounds arrive.
        this@identitySharedElement.sharedBounds(
            state,visibility,enter=EnterTransition.None,exit=ExitTransition.None,
            boundsTransform={_,_->tween(duration,easing=SoSceneEase)},
            resizeMode=SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds),
        ).drawWithContent {
            // sharedBounds supports different contents; identity still draws only one copy.
            if(!state.isMatchFound||visibility.transition.targetState==EnterExitState.Visible)drawContent()
        }
    }
}
