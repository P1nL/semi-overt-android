package com.semiovert.app.core.designsystem

import kotlin.math.pow

/** The accepted star / connected Vert sequence, in milliseconds. */
internal object BrandIntroMotion {
    const val STAR_MILLIS=700
    const val WRITING_MILLIS=2_000
    const val WRITING_END_MILLIS=STAR_MILLIS+WRITING_MILLIS
    const val HOLD_MILLIS=200
    const val RETURN_START_MILLIS=WRITING_END_MILLIS+HOLD_MILLIS
    const val RETURN_MILLIS=800
    const val PORTAL_START_MILLIS=RETURN_START_MILLIS+RETURN_MILLIS
    const val PORTAL_MILLIS=1_100
    const val TOTAL_MILLIS=PORTAL_START_MILLIS+PORTAL_MILLIS
    const val MAX_BACKGROUND_READY_WAIT_MILLIS=500L
    const val LEFT_SHIFT=265f
    const val HORIZONTAL_LENGTH=312f
    const val CENTER_Y_FRACTION=.42f

    private fun progress(time: Float,start: Int,duration: Int)=
        ((time-start)/duration).coerceIn(0f,1f)

    private fun inOut(value: Float,power: Int): Float {
        val p=value.coerceIn(0f,1f)
        return if(p<.5f)(2f*p).pow(power)/2f else 1f-(2f*(1f-p)).pow(power)/2f
    }

    fun starProgress(time: Float)=inOut(progress(time,0,STAR_MILLIS),2)

    fun wordProgress(time: Float): Float = when {
        time<STAR_MILLIS -> 0f
        time<WRITING_END_MILLIS -> handwriting(progress(time,STAR_MILLIS,WRITING_MILLIS))
        time<RETURN_START_MILLIS -> 1f
        else -> 1f-inOut(progress(time,RETURN_START_MILLIS,RETURN_MILLIS),5)
    }

    /** Horizontal ink reaches the V before the composition starts moving left. */
    fun vStartMillis(wordLength: Float): Float {
        val target=HORIZONTAL_LENGTH/wordLength
        var lower=0f
        var upper=1f
        repeat(24) {
            val middle=(lower+upper)*.5f
            if(handwriting(middle)<target)lower=middle else upper=middle
        }
        return STAR_MILLIS+WRITING_MILLIS*(lower+upper)*.5f
    }

    fun leftShift(time: Float,vStart: Float): Float =
        if(time<RETURN_START_MILLIS) {
            LEFT_SHIFT*inOut((time-vStart)/(WRITING_END_MILLIS-vStart),3)
        } else LEFT_SHIFT*(1f-inOut(progress(time,RETURN_START_MILLIS,RETURN_MILLIS),5))

    fun portalProgress(time: Float)=progress(time,PORTAL_START_MILLIS,PORTAL_MILLIS)

    fun portalZoom(time: Float,target: Float): Float {
        val p=portalProgress(time)
        return 1f+(target-1f)*(2f.pow(10f*p)-1f)/1_023f
    }

    fun homeOpacity(time: Float): Float {
        val aperture=inOut(progress(time,PORTAL_START_MILLIS,160),2)
        val home=progress(time,PORTAL_START_MILLIS,300)
        return aperture*(1f-(1f-home)*(1f-home))
    }

    fun homeScale(time: Float)=.93f+.07f*inOut(portalProgress(time),3)

    // Original GSAP Demo Hub handwriting curve; evaluate its seven cubic segments
    // directly so native drawing keeps the same pauses and accelerations.
    private val handwritingSegments=arrayOf(
        floatArrayOf(0f,0f, 0f,0f, .068f,.016f, .116f,.064f),
        floatArrayOf(.116f,.064f, .149f,.097f, .14f,.229f, .188f,.278f),
        floatArrayOf(.188f,.278f, .21f,.3f, .398f,.356f, .424f,.38f),
        floatArrayOf(.424f,.38f, .459f,.413f, .477f,.539f, .512f,.574f),
        floatArrayOf(.512f,.574f, .546f,.608f, .662f,.6f, .696f,.632f),
        floatArrayOf(.696f,.632f, .723f,.658f, .766f,.698f, .788f,.826f),
        floatArrayOf(.788f,.826f, .816f,.99f, 1f,1f, 1f,1f),
    )

    fun handwriting(fraction: Float): Float {
        if(fraction<=0f)return 0f
        if(fraction>=1f)return 1f
        val segment=handwritingSegments.first {fraction<=it[6]}
        var lower=0f
        var upper=1f
        repeat(18) {
            val t=(lower+upper)*.5f
            if(cubic(segment,0,t)<fraction)lower=t else upper=t
        }
        return cubic(segment,1,(lower+upper)*.5f)
    }

    private fun cubic(points: FloatArray,axis: Int,t: Float): Float {
        val inverse=1f-t
        return inverse*inverse*inverse*points[axis]+3f*inverse*inverse*t*points[axis+2]+
            3f*inverse*t*t*points[axis+4]+t*t*t*points[axis+6]
    }
}
