package com.semiovert.app.core.designsystem.wave

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

private val LocalWaveDeckOverlay=staticCompositionLocalOf<SharedTransitionScope?> {null}

/** Above route content and chrome, but below the separate reading expansion overlay. */
@Composable
fun WaveDeckOverlayHost(content: @Composable ()->Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWaveDeckOverlay provides this) {content()}
    }
}

/** Only drawing moves to the root overlay; layout, semantics and gestures stay at the source. */
@Composable
internal fun Modifier.waveDeckOverlay(active: ()->Boolean): Modifier {
    val overlay=LocalWaveDeckOverlay.current ?: return this
    return with(overlay) {this@waveDeckOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay=1f,renderInOverlay=active)}
}

/** The primary navigation circle alone stays above moving decks, not its whole menu. */
@Composable
internal fun Modifier.mainCircleOverlay(): Modifier {
    val overlay=LocalWaveDeckOverlay.current ?: return this
    return with(overlay) {this@mainCircleOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay=2f,renderInOverlay={true})}
}
