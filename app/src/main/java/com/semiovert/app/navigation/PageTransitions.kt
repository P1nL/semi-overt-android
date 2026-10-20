package com.semiovert.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import com.semiovert.app.core.designsystem.*

/** One handoff, two independent scene clocks. Scene-authored durations stay unchanged. */
internal data class PageHandoff(val from: Destination, val to: Destination) {
    val exitMillis = exitMillis(from)
    val enterMillis = enterMillis(to)
    // Start before the departing cards have cleared the viewport, not after an empty beat.
    val overlapMillis = minOf(exitMillis / 2, enterMillis)
    val enterDelayMillis = exitMillis - overlapMillis
    val totalMillis = enterDelayMillis + enterMillis

    companion object {
        fun between(from: String?, to: String?): PageHandoff? {
            val source = Destination.entries.find { it.route == from } ?: return null
            val target = Destination.entries.find { it.route == to } ?: return null
            return if (source == target) null else PageHandoff(source, target)
        }

        private fun exitMillis(page: Destination) = when (page) {
            Destination.HOME -> SceneMotion.HOME_EXIT_MS
            Destination.CATEGORY -> SceneMotion.CATEGORY_EXIT_MS
            Destination.SEARCH -> SceneMotion.SEARCH_EXIT_MS
            Destination.PROFILE -> SceneMotion.PROFILE_EXIT_MS
        }

        private fun enterMillis(page: Destination) = when (page) {
            Destination.HOME -> SceneMotion.HOME_ENTER_MS
            Destination.CATEGORY -> SceneMotion.CATEGORY_ENTER_MS
            Destination.SEARCH -> SceneMotion.SEARCH_ENTER_MS
            Destination.PROFILE -> SceneMotion.PROFILE_ENTER_MS
        }
    }
}

/** Registered on NavHost's own transition, so entries live until their scene motion finishes. */
@Composable
internal fun AnimatedContentScope.SceneMotionContent(
    page: Destination,
    handoff: PageHandoff?,
    content: @Composable () -> Unit,
) {
    val enabled = LocalMotionEnabled.current && handoff != null &&
        (page == handoff.from || page == handoff.to)
    val hidden by transition.animateFloat(
        transitionSpec = {
            when {
                !enabled -> snap()
                targetState == EnterExitState.PostExit -> tween(handoff!!.exitMillis, easing = LinearEasing)
                initialState == EnterExitState.PreEnter -> tween(
                    handoff!!.enterMillis, delayMillis = handoff.enterDelayMillis, easing = LinearEasing,
                )
                else -> snap()
            }
        }, label = "scene-${page.route}",
    ) { state -> if (enabled && state != EnterExitState.Visible) 1f else 0f }
    val phase = when {
        !enabled -> FlowPhase.IDLE
        transition.targetState == EnterExitState.PostExit -> FlowPhase.EXIT
        transition.currentState == EnterExitState.PreEnter -> FlowPhase.ENTER
        else -> FlowPhase.IDLE
    }
    // Keep the source registered at rest, so its bounds exist before the target is composed.
    IdentityTransitionScene(this,if(handoff!=null)SceneMotion.PRIMARY_ELEMENT_MS else 0,enabled&&phase!=FlowPhase.IDLE) {
    CompositionLocalProvider(
        LocalFlowRoll provides hidden,
        LocalFlowPhase provides phase,
        LocalProfileEntryProgress provides (1f - hidden),
        content = content,
    )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(motion: Boolean, pop: Boolean = false): EnterTransition {
    if (!motion || targetState.destination.route == "reader/{articleId}" ||
        (pop && initialState.destination.route == "reader/{articleId}")) return EnterTransition.None
    val handoff = PageHandoff.between(initialState.destination.route, targetState.destination.route)
    if (handoff != null) return fadeIn(tween(handoff.overlapMillis, handoff.enterDelayMillis, LinearEasing))
    return if (pop) slideInHorizontally(tween(320, easing = SoEase)) { -it / 5 } + fadeIn(tween(320), initialAlpha = .9f)
    else slideInHorizontally(tween(320, easing = SoEase)) { it }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(motion: Boolean, pop: Boolean = false): ExitTransition {
    if (!motion || initialState.destination.route == "reader/{articleId}" ||
        (!pop && targetState.destination.route == "reader/{articleId}")) return ExitTransition.None
    val handoff = PageHandoff.between(initialState.destination.route, targetState.destination.route)
    if (handoff != null) return fadeOut(tween(handoff.overlapMillis, handoff.enterDelayMillis, LinearEasing))
    return if (pop) slideOutHorizontally(tween(320, easing = SoEase)) { it }
    else slideOutHorizontally(tween(320, easing = SoEase)) { -it / 5 } + fadeOut(tween(320), targetAlpha = .9f)
}
