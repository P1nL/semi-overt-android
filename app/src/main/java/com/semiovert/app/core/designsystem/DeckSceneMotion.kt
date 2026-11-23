package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

enum class FlowPhase { IDLE, ENTER, EXIT }
val LocalBrowseAnnotationAlpha = compositionLocalOf {1f}

/** Durations belong to the scene, never to a generic route fade. */
object SceneMotion {
    const val PRIMARY_ELEMENT_MS = 1000
    const val DECK_STAGGER_MS = 180
    const val HOME_ENTER_MS = 900
    const val HOME_EXIT_MS = 900
    const val HOME_EXIT_CARD_MS = 425
    const val HOME_EXIT_STAGGER_MS = HOME_EXIT_MS - HOME_EXIT_CARD_MS
    private const val CATEGORY_SETTLE_EXTRA_MS = 200
    const val CATEGORY_ENTER_MS = 900
    const val CATEGORY_EXIT_MS = 900
    private const val PROFILE_TEXT_EXIT_REFERENCE_MS = 1000
    private const val PROFILE_TEXT_ENTER_REDUCTION_MS = 200
    const val PROFILE_ENTER_MS = 700
    const val PROFILE_AVATAR_LEAD_REDUCTION_MS = 200
    const val PROFILE_AVATAR_ENTER_MS = 600
    private const val PROFILE_SETTINGS_FADE_MS = 120
    const val PROFILE_COVER_ENTER_MS = PROFILE_ENTER_MS - PROFILE_SETTINGS_FADE_MS
    const val PROFILE_EXIT_MS = 700
    const val PROFILE_GUEST_ENTER_MS = 400
    const val PROFILE_GUEST_EXIT_MS = 400
    const val SEARCH_PRIMARY_MS = 750
    const val SEARCH_STAGGER_MS = 150
    const val SEARCH_ENTER_MS = SEARCH_PRIMARY_MS + SEARCH_STAGGER_MS
    const val SEARCH_EXIT_MS = SEARCH_ENTER_MS
    const val CATEGORY_SWAP_MS = 550
    const val CATEGORY_SWAP_ENTER_MS = CATEGORY_SWAP_MS + CATEGORY_SETTLE_EXTRA_MS
    const val CATEGORY_SWAP_STAGGER_MS = 84
    const val BROWSE_ANNOTATION_MS = 600
    const val DRAWER_MS = 400
    const val READER_PAGE_EXIT_MS = 400
    const val STATE_FADE_MS = 300
    const val MAIN_ICON_MORPH_MS = 400
    fun navigationSpring()=spring<Float>(dampingRatio=.78f,stiffness=160f,visibilityThreshold=.001f)
    const val LIFT_DISTANCE_DP = 160f
    const val LIFT_THRESHOLD = .45f
    const val READING_DRAG_STAGE = .55f
    const val READING_OPEN_MS = 1200
    const val HOME_READING_TEXT_MS = 300
    const val HOME_READING_STAGGER_MS = 150
    private const val HOME_READING_AVATAR_MS = 180
    const val READING_BODY_MS = 480
    const val HOME_READING_OPEN_MS = HOME_READING_STAGGER_MS + READING_BODY_MS
    private const val READING_RETURN_EXTRA_MS = 100
    private val HOME_READING_CLOSE_BASE_MS = maxOf(READING_BODY_MS,HOME_READING_STAGGER_MS + HOME_READING_TEXT_MS)
    val HOME_READING_CLOSE_MS = HOME_READING_CLOSE_BASE_MS + READING_RETURN_EXTRA_MS
    const val STACK_READING_TEXT_END = .80f
    const val READING_BODY_START = .60f
    private const val READING_EXIT_BASE_MS = 1100
    const val READING_EXIT_MS = READING_EXIT_BASE_MS + READING_RETURN_EXTRA_MS
    const val READER_CIRCLE_DOCK_MS = 320
    private const val READING_EXIT_REFERENCE_MS = 1300
    private const val READING_EXIT_CONTENT_MS = 560
    fun segment(value: Float,start: Float,end: Float)=((value-start)/(end-start)).coerceIn(0f,1f)
    fun readingDrag(raw: Float)=READING_DRAG_STAGE*raw.coerceIn(0f,1f)
    fun readingReleaseVelocity(velocityDp: Float)=
        (velocityDp/LIFT_DISTANCE_DP*READING_DRAG_STAGE).coerceIn(-4f,4f)
    fun readingSpring()=spring<Float>(dampingRatio=.84f,stiffness=105f,visibilityThreshold=.001f)
    // Click, drag and fling share the same remaining clock after handoff.
    fun readingOpenMillis(origin: ArticleOrigin)=if(origin==ArticleOrigin.HOME)HOME_READING_OPEN_MS else READING_OPEN_MS
    fun readingOpenSpec(startProgress: Float=0f,origin: ArticleOrigin=ArticleOrigin.STACK,openWithLift: Boolean=false)=tween<Float>(
        (readingOpenMillis(origin)*(1f-readingContentProgress(startProgress,openWithLift))).roundToInt(),easing=LinearEasing)
    fun readingContentProgress(progress: Float,openWithLift: Boolean)=
        if(openWithLift)lerp(READING_DRAG_STAGE,1f,progress.coerceIn(0f,1f)) else progress.coerceIn(0f,1f)
    // Stretch the whole return without changing the relative timing of the body and card.
    fun readingCloseSpec(origin: ArticleOrigin=ArticleOrigin.STACK)=if(origin==ArticleOrigin.HOME)
        tween<Float>(HOME_READING_CLOSE_MS,easing=LinearEasing)
    else tween<Float>(READING_EXIT_MS,easing=Easing {fraction->
        val elapsed=fraction*READING_EXIT_BASE_MS
        val landing=segment(elapsed,READING_EXIT_CONTENT_MS.toFloat(),READING_EXIT_BASE_MS.toFloat())
        val originalTime=elapsed+(READING_EXIT_REFERENCE_MS-READING_EXIT_BASE_MS)*landing*landing
        SoSceneEase.transform(originalTime/READING_EXIT_REFERENCE_MS)
    })
    fun readingFollowerSpring(rank: Int)=spring<Float>(dampingRatio=.74f,stiffness=170f/(1f+.15f*rank),visibilityThreshold=.1f)
    fun readingOpeningLift(progress: Float,origin: ArticleOrigin)=
        if(origin==ArticleOrigin.HOME)0f else (progress/READING_DRAG_STAGE).coerceIn(0f,1f)
    private fun homeReturnElapsed(progress: Float)=(1f-progress)*HOME_READING_CLOSE_BASE_MS
    fun readingOpeningText(progress: Float,origin: ArticleOrigin,returning: Boolean=false): Float {
        if(origin==ArticleOrigin.HOME)return if(returning)
            1f-segment(homeReturnElapsed(progress),HOME_READING_STAGGER_MS.toFloat(),
                (HOME_READING_STAGGER_MS+HOME_READING_TEXT_MS).toFloat())
        else segment(progress,0f,HOME_READING_TEXT_MS.toFloat()/HOME_READING_OPEN_MS)
        val t=segment(progress,READING_DRAG_STAGE,STACK_READING_TEXT_END)
        return t*(2f-t)
    }
    fun readingOpeningCardAlpha(progress: Float,origin: ArticleOrigin,returning: Boolean=false): Float {
        if(origin==ArticleOrigin.HOME&&returning)return SoEase.transform(segment(homeReturnElapsed(progress),
            HOME_READING_STAGGER_MS.toFloat(),minOf(HOME_READING_STAGGER_MS+420f,HOME_READING_CLOSE_BASE_MS.toFloat())))
        return 1f-SoEase.transform(if(origin==ArticleOrigin.HOME)segment(progress,0f,420f/HOME_READING_OPEN_MS)
            else segment(progress,READING_DRAG_STAGE,STACK_READING_TEXT_END))
    }
    fun readingOpeningAvatar(progress: Float,origin: ArticleOrigin,returning: Boolean=false): Float {
        if(origin==ArticleOrigin.HOME&&returning)return 1f-SoEase.transform(segment(homeReturnElapsed(progress),
            0f,HOME_READING_AVATAR_MS.toFloat()))
        return SoEase.transform(if(origin==ArticleOrigin.HOME)
            segment(progress,HOME_READING_TEXT_MS.toFloat()/HOME_READING_OPEN_MS,
                (HOME_READING_TEXT_MS+HOME_READING_AVATAR_MS).toFloat()/HOME_READING_OPEN_MS)
            else segment(progress,STACK_READING_TEXT_END,.90f))
    }
    // Home starts at 150ms; both entrances give the body 480ms.
    fun readingBody(progress: Float,returning: Boolean=false,origin: ArticleOrigin=ArticleOrigin.STACK)=
        if(returning&&origin==ArticleOrigin.HOME)1f-SoSceneEase.transform(segment(homeReturnElapsed(progress),0f,READING_BODY_MS.toFloat()))
        else if(returning)SoEase.transform(segment(progress,.80f,1f))
        else if(origin==ArticleOrigin.HOME)SoSceneEase.transform(segment(progress,HOME_READING_STAGGER_MS.toFloat()/HOME_READING_OPEN_MS,1f))
        else SoSceneEase.transform(segment(progress,READING_BODY_START,1f))
    // Return has its own fade-in, starting at p=.52, rather than reversing the late exit fade.
    fun readingChrome(progress: Float,returning: Boolean=false)=if(returning)
        1f-SoEase.transform(segment(1f-progress,.48f,1f)) else SoEase.transform(segment(progress,0f,.36f))
    fun profileSegment(progress: Float,start: Float,end: Float,exiting: Boolean)=if(exiting)
        segment(progress*PROFILE_TEXT_EXIT_REFERENCE_MS,start,end)
        else segment(progress*PROFILE_ENTER_MS,start,end-PROFILE_TEXT_ENTER_REDUCTION_MS)
    fun profileCoverProgress(progress: Float,exiting: Boolean)=if(exiting)
        1f-SoSceneEase.transform(1f-progress)
        else SoSceneEase.transform(segment(progress*PROFILE_ENTER_MS,0f,PROFILE_COVER_ENTER_MS.toFloat()))
    fun profileSettingsAlpha(progress: Float)=
        segment(progress*PROFILE_ENTER_MS,PROFILE_COVER_ENTER_MS.toFloat(),PROFILE_ENTER_MS.toFloat())
    fun profileGuestSettingsAlpha(avatarArrival: Float)=
        segment(avatarArrival,SoSceneEase.transform(
            (PROFILE_GUEST_ENTER_MS-PROFILE_SETTINGS_FADE_MS).toFloat()/PROFILE_GUEST_ENTER_MS),1f)
    fun searchHeader(progress: Float)=SoSceneEase.transform(segment(progress*SEARCH_ENTER_MS,0f,SEARCH_PRIMARY_MS.toFloat()))
    // Use the route's linear clock, not a card stagger or an in-page result swap.
    fun browseAnnotationAlpha(hidden: Float,phase: FlowPhase,durationMillis: Int): Float {
        if(phase==FlowPhase.IDLE)return 1f
        val elapsed=(if(phase==FlowPhase.ENTER)1f-hidden else hidden)*durationMillis
        val amount=SoSceneEase.transform((elapsed/BROWSE_ANNOTATION_MS).coerceIn(0f,1f))
        return if(phase==FlowPhase.ENTER)amount else 1f-amount
    }
    fun commits(progress: Float,upwardVelocityDp: Float)=progress>=LIFT_THRESHOLD||(progress>.08f&&upwardVelocityDp>=900f)

    private const val CATEGORY_APPROACH_AT = .32f
    private const val CATEGORY_UPPER_TURN_AT = .64f
    private val categoryApproachProgress=SoSceneEase.transform(CATEGORY_APPROACH_AT)
    private val categoryApproachEase=CubicBezierEasing(.30f,.152f,.62f,1f)
    private val categoryEntranceCurve=Easing { progress ->
        val bottomTurnAt=.84f
        val topTurn=1.062f
        val bottomTurn=.988f
        when {
            progress<CATEGORY_APPROACH_AT -> SoSceneEase.transform(progress)
            progress<CATEGORY_UPPER_TURN_AT -> lerp(categoryApproachProgress,topTurn,
                categoryApproachEase.transform(segment(progress,CATEGORY_APPROACH_AT,CATEGORY_UPPER_TURN_AT)))
            progress<bottomTurnAt -> lerp(topTurn,bottomTurn,
                SoSceneEase.transform(segment(progress,CATEGORY_UPPER_TURN_AT,bottomTurnAt)))
            else -> lerp(bottomTurn,1f,SoSceneEase.transform(segment(progress,bottomTurnAt,1f)))
        }
    }
    private fun categoryEntranceWithLongerSettle(durationMillis: Int,staggerMillis: Int): Easing {
        val cardMillis=(durationMillis-staggerMillis).toFloat()
        val liftEnd=CATEGORY_UPPER_TURN_AT*(cardMillis-CATEGORY_SETTLE_EXTRA_MS)/cardMillis
        return Easing { progress ->
            val curveProgress=if(progress<liftEnd)
                CATEGORY_UPPER_TURN_AT*segment(progress,0f,liftEnd)
            else lerp(CATEGORY_UPPER_TURN_AT,1f,segment(progress,liftEnd,1f))
            categoryEntranceCurve.transform(curveProgress)
        }
    }
    val categoryRouteEntranceEase=categoryEntranceWithLongerSettle(CATEGORY_ENTER_MS,DECK_STAGGER_MS)
    val categorySwapEntranceEase=categoryEntranceWithLongerSettle(CATEGORY_SWAP_ENTER_MS,CATEGORY_SWAP_STAGGER_MS)
    val searchSwapEntranceEase=Easing { progress ->
        val tail=sin(PI.toFloat()*segment(progress,.55f,1f))
        SoSceneEase.transform(progress)+.070f*tail*tail
    }

    /** rank zero is the foreground; entrance deliberately reverses that ordering. */
    fun waveOffset(hidden: Float,phase: FlowPhase,rank: Int,count: Int,top: Float,height: Float,
        durationMillis: Int=if(phase==FlowPhase.EXIT)CATEGORY_EXIT_MS else CATEGORY_ENTER_MS,
        staggerMillis: Int=DECK_STAGGER_MS,entranceEase: Easing=SoSceneEase): Float {
        if(phase==FlowPhase.IDLE||hidden<=0f)return 0f
        val duration=durationMillis
        val span=staggerMillis.toFloat()
        val order=if(phase==FlowPhase.EXIT)rank else count-1-rank
        val delay=if(count<=1)0f else order.toFloat()/(count-1)*span
        val elapsed=(if(phase==FlowPhase.EXIT)hidden else 1f-hidden)*duration
        // Each card's full travel must fit before the scene's final stagger finishes.
        val window=(duration-staggerMillis).toFloat()
        val travel=height+24f-top
        val progress=segment(elapsed-delay,0f,window)
        return if(phase==FlowPhase.EXIT)travel*SoSceneEase.transform(progress)
        else travel*(1f-entranceEase.transform(progress))
    }
    fun readingOffset(progress: Float,rank: Int,top: Float,height: Float): Float {
        val delay=(rank*.055f).coerceAtMost(.28f)
        // This is a target, not a spring-shaped time curve. Each card owns a real spring.
        return (height+24f-top)*segment(progress,delay,.55f+delay)
    }
}
