package com.semiovert.app.core.designsystem

import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/** Port of the web profile's pixelResolution and its image/outline fades. */
internal object AvatarPixelMotion {
    const val DURATION_MILLIS=750
    const val OPENING_SIZE=116

    fun resolution(progress: Float,size: Int): Int {
        if(progress>=1f)return size
        val p=progress.toDouble().coerceAtLeast(0.0)
        val out=1.0-(1.0-p).pow(3.0)
        return 2.0.pow(floor(2.0+(log2(size.toDouble())-2.0)*out)).toInt().coerceAtMost(size)
    }

    // Web image fade starts at 390ms; its pixel stage starts at 400ms.
    fun imageAlpha(progress: Float)=((progress*DURATION_MILLIS+10f)/50f).coerceIn(0f,1f)
    fun outlineAlpha(progress: Float)=1f-((progress*DURATION_MILLIS-500f)/250f).coerceIn(0f,1f)

    // Change identities only while both pictures are coarse; then resolve the destination.
    fun sourceClarity(progress: Float)=1f-(progress/.4f).coerceIn(0f,1f)
    fun targetClarity(progress: Float)=((progress-.6f)/.4f).coerceIn(0f,1f)
    fun identityMix(progress: Float)=SoSceneEase.transform(((progress-.4f)/.2f).coerceIn(0f,1f))
}
