package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

enum class FlowPhase { IDLE, ENTER, EXIT }

/** Durations belong to the scene, never to a generic route fade. */
object SceneMotion {
    const val PRIMARY_ELEMENT_MS = 1000
    const val DECK_STAGGER_MS = 180
    const val HOME_ENTER_MS = PRIMARY_ELEMENT_MS + DECK_STAGGER_MS
    const val HOME_EXIT_MS = 900
    const val CATEGORY_EXIT_MS = PRIMARY_ELEMENT_MS + DECK_STAGGER_MS
    const val CATEGORY_ENTER_MS = PRIMARY_ELEMENT_MS + DECK_STAGGER_MS
    const val PROFILE_ENTER_MS = 1500
    const val PROFILE_EXIT_MS = 1500
    const val SEARCH_ENTER_MS = PRIMARY_ELEMENT_MS + DECK_STAGGER_MS
    const val SEARCH_EXIT_MS = PRIMARY_ELEMENT_MS + DECK_STAGGER_MS
    const val CATEGORY_SWAP_MS = 420
    const val DRAWER_MS = 400
    const val READER_PAGE_EXIT_MS = 400
    const val STATE_FADE_MS = 300
    const val MAIN_ICON_MORPH_MS = 400
    fun navigationSpring()=spring<Float>(dampingRatio=.78f,stiffness=160f,visibilityThreshold=.001f)
    const val LIFT_DISTANCE_DP = 160f
    const val LIFT_THRESHOLD = .45f
    const val READING_DRAG_STAGE = .55f
    const val READING_CONTAINER_START = .62f
    const val READING_EXIT_MS = 1600
    fun segment(value: Float,start: Float,end: Float)=((value-start)/(end-start)).coerceIn(0f,1f)
    fun readingDrag(raw: Float): Float {
        val up=raw.coerceAtLeast(0f)
        val excess=(up-1f).coerceAtLeast(0f)
        return READING_DRAG_STAGE*if(up<=1f)up else 1f+.12f*excess/(1f+excess)
    }
    fun readingReleaseVelocity(velocityDp: Float)=
        (velocityDp/LIFT_DISTANCE_DP*READING_DRAG_STAGE).coerceIn(-4f,4f)
    fun readingSpring()=spring<Float>(dampingRatio=.84f,stiffness=105f,visibilityThreshold=.001f)
    // Slow the committed opening without slowing finger tracking or a cancelled lift.
    fun readingOpenSpring()=spring<Float>(dampingRatio=.84f,stiffness=24f,visibilityThreshold=.001f)
    // A return starts at rest: give the early body/avatar stages real time before the card retracts.
    fun readingCloseSpec()=tween<Float>(READING_EXIT_MS,easing=SoSceneEase)
    fun readingFollowerSpring(rank: Int)=spring<Float>(dampingRatio=.74f,stiffness=170f/(1f+.15f*rank),visibilityThreshold=.1f)
    fun readingContainer(progress: Float)=SoEase.transform(segment(progress,READING_CONTAINER_START,1f))
    fun readingText(progress: Float)=SoEase.transform(segment(progress,.70f,.88f))
    fun readingAvatar(progress: Float)=SoEase.transform(segment(progress,.88f,.95f))
    fun readingBody(progress: Float)=SoEase.transform(segment(progress,.92f,1f))
    fun readingChrome(progress: Float)=SoEase.transform(segment(progress,0f,.36f))
    fun homeReadingFade(progress: Float)=SoEase.transform(segment(progress,0f,.60f))
    // Windows are real milliseconds, not old 1700ms proportions compressed into a 1s scene.
    fun profileSegment(progress: Float,start: Float,end: Float)=segment(progress*PROFILE_ENTER_MS,start,end)
    fun searchHeader(progress: Float)=SoSceneEase.transform(segment(progress*SEARCH_ENTER_MS,0f,PRIMARY_ELEMENT_MS.toFloat()))
    fun commits(progress: Float,upwardVelocityDp: Float)=progress>=LIFT_THRESHOLD||(progress>.08f&&upwardVelocityDp>=900f)

    /** rank zero is the foreground; entrance deliberately reverses that ordering. */
    fun waveOffset(hidden: Float,phase: FlowPhase,rank: Int,count: Int,top: Float,height: Float): Float {
        if(phase==FlowPhase.IDLE||hidden<=0f)return 0f
        val duration=if(phase==FlowPhase.EXIT)CATEGORY_EXIT_MS else CATEGORY_ENTER_MS
        val span=DECK_STAGGER_MS.toFloat()
        val order=if(phase==FlowPhase.EXIT)rank else count-1-rank
        val delay=if(count<=1)0f else order.toFloat()/(count-1)*span
        val elapsed=(if(phase==FlowPhase.EXIT)hidden else 1f-hidden)*duration
        val p=((elapsed-delay)/PRIMARY_ELEMENT_MS).coerceIn(0f,1f)
        val travel=height+24f-top
        return travel*if(phase==FlowPhase.EXIT)SoSceneEase.transform(p) else 1f-SoSceneEase.transform(p)
    }
    fun readingOffset(progress: Float,rank: Int,top: Float,height: Float): Float {
        val delay=(rank*.055f).coerceAtMost(.28f)
        // This is a target, not a spring-shaped time curve. Each card owns a real spring.
        return (height+24f-top)*segment(progress,delay,.55f+delay)
    }
}
