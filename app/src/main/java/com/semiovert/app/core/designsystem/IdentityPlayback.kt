package com.semiovert.app.core.designsystem

internal data class IdentityFrameBlend(val first: Int,val second: Int,val mix: Float)

/** Preserve the original 90-frame/1.5-second source; interpolate, never speed it up. */
internal object IdentityPlayback {
    const val FRAME_COUNT=90
    const val DURATION_MILLIS=1500

    fun sample(position: Float): IdentityFrameBlend {
        val value=position.coerceIn(0f,(FRAME_COUNT-1).toFloat())
        val first=value.toInt()
        val second=(first+1).coerceAtMost(FRAME_COUNT-1)
        return IdentityFrameBlend(first,second,if(first==second)0f else value-first)
    }
}
