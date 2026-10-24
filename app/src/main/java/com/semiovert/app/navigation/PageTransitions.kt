package com.semiovert.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavBackStackEntry
import com.semiovert.app.core.designsystem.*

internal const val ACCOUNT_COMPLETED_KEY="account-completed"
internal const val ACCOUNT_SUCCESS_EXIT_MS=600
internal const val SIDE_PAGE_MS=500
internal const val READER_PAGE_NAVIGATION_KEY="reader-page-navigation"

/** One handoff, two independent scene clocks. Scene-authored durations stay unchanged. */
internal data class PageHandoff(val from: Destination?, val to: Destination,val profileGuest: Boolean=false,
    val sourceIsAuthor: Boolean=false,val targetIsAuthor: Boolean=false) {
    // A null primary source denotes a reader leaving through its navigation menu.
    val exitMillis = if(profileGuest&&!sourceIsAuthor&&from==Destination.PROFILE)SceneMotion.PROFILE_GUEST_MS
        else from?.let {exitMillis(it)} ?: SceneMotion.READER_PAGE_EXIT_MS
    val enterMillis = if(profileGuest&&!targetIsAuthor&&to==Destination.PROFILE)SceneMotion.PROFILE_GUEST_MS else enterMillis(to)
    // Start before the departing cards have cleared the viewport, not after an empty beat.
    val overlapMillis = minOf(exitMillis / 2, enterMillis)
    val enterDelayMillis = exitMillis - overlapMillis
    val totalMillis = enterDelayMillis + enterMillis
    val identityMillis = when {
        profileGuest&&((!sourceIsAuthor&&from==Destination.PROFILE)||(!targetIsAuthor&&to==Destination.PROFILE)) -> SceneMotion.PROFILE_GUEST_MS
        from==Destination.SEARCH||to==Destination.SEARCH -> SceneMotion.SEARCH_PRIMARY_MS
        else -> SceneMotion.PRIMARY_ELEMENT_MS
    }

    companion object {
        fun between(from: String?, to: String?,profileGuest: Boolean=false): PageHandoff? {
            val targetAuthor=to==ScreenRoutes.AUTHOR
            val sourceAuthor=from==ScreenRoutes.AUTHOR
            val target = if(targetAuthor)Destination.PROFILE else Destination.entries.find { it.route == to } ?: return null
            if(from=="reader/{articleId}")return PageHandoff(null,target,profileGuest,targetIsAuthor=targetAuthor)
            val source = if(sourceAuthor)Destination.PROFILE else Destination.entries.find { it.route == from } ?: return null
            return if (from == to) null else PageHandoff(source,target,profileGuest,sourceAuthor,targetAuthor)
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
    val identityDuration=handoff?.identityMillis?:0
    IdentityTransitionScene(this,identityDuration,enabled&&phase!=FlowPhase.IDLE) {
    CompositionLocalProvider(
        LocalFlowRoll provides hidden,
        LocalFlowPhase provides phase,
        LocalBrowseAnnotationAlpha provides SceneMotion.browseAnnotationAlpha(hidden,phase,
            if(phase==FlowPhase.EXIT)handoff?.exitMillis?:0 else handoff?.enterMillis?:0),
        LocalProfileEntryProgress provides (1f - hidden),
        content = content,
    )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(motion: Boolean, pop: Boolean = false,profileGuest: Boolean=false): EnterTransition {
    if(motion&&initialState.leavingReaderForPage()&&targetState.isReaderPageTarget()) {
        // The reader's temporary drawing overlay stays above this new scene. Its own
        // content clock handles entrance; the background is ready beneath the reader.
        return EnterTransition.None
    }
    // The successful account sheet reveals its caller in place, without horizontal parallax.
    if(pop&&initialState.savedStateHandle.get<Boolean>(ACCOUNT_COMPLETED_KEY)==true)return EnterTransition.None
    if (!motion || targetState.destination.route == "reader/{articleId}" ||
        (pop && initialState.destination.route == "reader/{articleId}")) return EnterTransition.None
    val handoff = PageHandoff.between(initialState.destination.route, targetState.destination.route,profileGuest)
    if (handoff != null) return fadeIn(tween(handoff.overlapMillis, handoff.enterDelayMillis, LinearEasing))
    return if (pop) slideInHorizontally(tween(SIDE_PAGE_MS, easing = SoSceneEase)) { -it / 5 } +
        fadeIn(tween(SIDE_PAGE_MS, easing = SoSceneEase), initialAlpha = .9f)
    else slideInHorizontally(tween(SIDE_PAGE_MS, easing = SoSceneEase)) { it }
}

private fun NavBackStackEntry.leavingReaderForPage()=
    destination.route=="reader/{articleId}"&&savedStateHandle.get<Boolean>(READER_PAGE_NAVIGATION_KEY)==true

private fun NavBackStackEntry.isReaderPageTarget()=
    destination.route==ScreenRoutes.AUTHOR||Destination.entries.any {it.route==destination.route}

/** Card opening / system back stay owned by ArticleExpansionHost, not this route slide. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.readerPageExit(motion: Boolean): ExitTransition =
    if(motion&&initialState.leavingReaderForPage()&&targetState.isReaderPageTarget())
        slideOutVertically(tween(SceneMotion.READER_PAGE_EXIT_MS,easing=SoSceneEase)) {it}
    else ExitTransition.None

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(motion: Boolean, pop: Boolean = false,profileGuest: Boolean=false): ExitTransition {
    if(motion&&pop&&initialState.savedStateHandle.get<Boolean>(ACCOUNT_COMPLETED_KEY)==true)
        return slideOutVertically(tween(ACCOUNT_SUCCESS_EXIT_MS,easing=SoSceneEase)) {it}
    if (!motion || initialState.destination.route == "reader/{articleId}" ||
        (!pop && targetState.destination.route == "reader/{articleId}")) return ExitTransition.None
    val handoff = PageHandoff.between(initialState.destination.route, targetState.destination.route,profileGuest)
    if (handoff != null) return fadeOut(tween(handoff.overlapMillis, handoff.enterDelayMillis, LinearEasing))
    return if (pop) slideOutHorizontally(tween(SIDE_PAGE_MS, easing = SoSceneEase)) { it }
    else slideOutHorizontally(tween(SIDE_PAGE_MS, easing = SoSceneEase)) { -it / 5 } +
        fadeOut(tween(SIDE_PAGE_MS, easing = SoSceneEase), targetAlpha = .9f)
}
