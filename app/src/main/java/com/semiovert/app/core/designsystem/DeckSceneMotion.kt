package com.semiovert.app.core.designsystem


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
    const val READING_MS = 600
    const val LIFT_DISTANCE_DP = 160f
    const val LIFT_THRESHOLD = .45f
    fun segment(value: Float,start: Float,end: Float)=((value-start)/(end-start)).coerceIn(0f,1f)
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
        val delay=(rank*.045f).coerceAtMost(.18f)
        return (height+24f-top)*SoEase.transform(segment(progress,delay,.82f+delay))
    }
}
