package com.semiovert.app.core.designsystem.wave

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.semiovert.app.core.designsystem.*

private val LocalWaveDeckOverlay=staticCompositionLocalOf<SharedTransitionScope?> {null}
@Stable private class MainCircleDrawOwner(val icon: MainCircleIconState) {
    val entries=mutableStateListOf<Any>()
}
private val LocalMainCircleDrawOwner=staticCompositionLocalOf<MainCircleDrawOwner?> {null}

/** Moving decks use the overlay only while dragging; the primary circle stays above reading transitions. */
@Composable
fun WaveDeckOverlayHost(content: @Composable ()->Unit) {
    val scope=rememberCoroutineScope()
    val mainCircleOwner=remember(scope) {MainCircleDrawOwner(MainCircleIconState(scope))}
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWaveDeckOverlay provides this,LocalMainCircleDrawOwner provides mainCircleOwner) {content()}
    }
}

/** Only drawing moves to the root overlay; layout, semantics and gestures stay at the source. */
@Composable
internal fun Modifier.waveDeckOverlay(active: ()->Boolean): Modifier {
    val overlay=LocalWaveDeckOverlay.current ?: return this
    return with(overlay) {this@waveDeckOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay=1f,renderInOverlay=active)}
}

/** Put every reading card (including the expanding copy) in the same ordered draw plane.
 * Moving only the selected copy into the root overlay would cover all cards in front of it.
 * Preserve deck order inside (1, 2), below the primary circle, for both lift and return.
 */
@Composable
internal fun Modifier.readingStackOverlay(layer: Float?,active: ()->Boolean): Modifier {
    if(layer==null)return this
    val overlay=LocalWaveDeckOverlay.current ?: return this
    return with(overlay) {this@readingStackOverlay.renderInSharedTransitionScopeOverlay(
        zIndexInOverlay=1f+layer/(layer+1f),renderInOverlay=active)}
}

/** The newest mounted page alone draws its main circle above moving decks.
 * Source and target coexist during route handoff; drawing both doubles their elevation shadows.
 * Gate drawing inside the recorded overlay layer, without fading either surface or its icon.
 */
@Composable
internal fun Modifier.mainCircleOverlay(ownerKey: Any?=null): Modifier {
    val overlay=LocalWaveDeckOverlay.current ?: return this
    val owner=LocalMainCircleDrawOwner.current ?: return this
    val defaultKey=remember {Any()}
    val token=ownerKey?:defaultKey
    DisposableEffect(owner,token) {
        owner.entries.add(token)
        onDispose {owner.entries.remove(token)}
    }
    return with(overlay) {
        this@mainCircleOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay=2f,renderInOverlay={true})
            .drawWithContent {if(owner.entries.lastOrNull()===token)drawContent()}
    }
}

/** Only the page that currently draws the circle may retarget the persistent inner glyph. */
@Composable
internal fun MainCircleMorphIcon(ownerKey: Any,page: SoSymbol,readingProgress: Float,controlledReading: Boolean,
    modifier: Modifier=Modifier,tint: Color=MaterialTheme.colorScheme.onPrimaryContainer) {
    val owner=LocalMainCircleDrawOwner.current
    if(owner==null) {
        ReadingMorphIcon(page,readingProgress,modifier,tint)
        return
    }
    val active=owner.entries.lastOrNull()===ownerKey
    val motion=LocalMotionEnabled.current
    SideEffect {
        if(active) {
            if(controlledReading)owner.icon.showReading(page,readingProgress)
            else owner.icon.showPage(page,motion)
        }
    }
    val morph=owner.icon.morph
    if(morph==null)ReadingMorphIcon(page,readingProgress,modifier,tint)
    else MorphIconCanvas(morph.strokes,morph.source.symbol,morph.target.symbol!!,owner.icon.progress,modifier,tint)
}

/** A navigating reader stays opaque above the destination until it has slid offscreen. */
@Composable
internal fun Modifier.readerPageOverlay(active: ()->Boolean): Modifier {
    val overlay=LocalWaveDeckOverlay.current ?: return this
    return with(overlay) {this@readerPageOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay=1.9f,renderInOverlay=active)}
}
