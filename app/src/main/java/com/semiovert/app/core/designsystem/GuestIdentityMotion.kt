package com.semiovert.app.core.designsystem

import androidx.compose.ui.util.lerp

/** Only the head keeps the supplied 600ms bounce; the shoulders rest after rising. */
internal object GuestIdentityMotion {
    const val RISE_MILLIS=560
    const val BOUNCE_MILLIS=600
    const val ICON_FRACTION=.56f
    const val RING_STROKE_FRACTION=.04f

    fun riseOffset(progress: Float): Float=1f-progress.coerceIn(0f,1f)
    fun headOffset(progress: Float): Float=bounce(progress,4f)

    private fun bounce(progress: Float,down: Float): Float {
        val p=progress.coerceIn(0f,1f)
        return when {
            p<=.33f ->lerp(0f,down,SoSceneEase.transform(p/.33f))
            p<=.66f ->lerp(down,-2f,SoSceneEase.transform((p-.33f)/.33f))
            else ->lerp(-2f,0f,SoSceneEase.transform((p-.66f)/.34f))
        }
    }
}
